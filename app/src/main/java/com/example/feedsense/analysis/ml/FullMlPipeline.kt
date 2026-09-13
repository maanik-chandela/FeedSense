package com.example.feedsense.analysis.ml

import com.example.feedsense.analysis.ml.evaluation.EvaluationBoundaryResult
import com.example.feedsense.analysis.ml.evaluation.EvaluationBoundaryEvaluator
import com.example.feedsense.analysis.ml.evaluation.EvaluationCandidateSnapshot
import com.example.feedsense.analysis.ml.evaluation.EvaluationEligibility
import com.example.feedsense.analysis.ml.runtime.AdapterRawModelOutput
import com.example.feedsense.analysis.ml.taxonomy.MappingStatus
import com.example.feedsense.analysis.ml.taxonomy.ModelTaxonomyMapping
import com.example.feedsense.analysis.ml.taxonomy.TaxonomyMappingEngine
import com.example.feedsense.analysis.ml.taxonomy.TaxonomyMappingResult

// --------------------------------
// FULL ML PIPELINE (8B-15)
// --------------------------------
//
// Chains the complete model research pipeline:
//
//   AdapterRawModelOutput (8B-15-5)
//        ↓
//   ModelOutputDecoder (8B-15-6)
//        ↓
//   ModelInferenceResult
//        ↓
//   TaxonomyMappingEngine (8B-15-7)
//        ↓
//   List<TaxonomyMappingResult>
//        ↓
//   EvaluationBoundaryEvaluator (8B-15-8)
//        ↓
//   List<EvaluationBoundaryResult>
//
// This orchestrator is RESEARCH infrastructure. It does NOT:
//   - Modify production tables
//   - Write to the database
//   - Trigger capture or analysis
//   - Replace the existing OnDeviceInferenceEngine
//
// It DOES:
//   - Produce a complete, deterministic, auditable result
//     for a single adapter output through every pipeline stage
//   - Freeze all version identities at each boundary
//   - Preserve the distinction between model prediction,
//     taxonomy mapping, and evaluation decision

/**
 * Configuration for the full pipeline run. Freezes all
 * version identities that affect reproducibility.
 */
data class FullPipelineConfig(
    val modelId: String,
    val modelVersion: String,
    val modelChecksum: String? = null,
    val interpretationConfig: OutputInterpretationConfig,
    val taxonomyMappings: List<ModelTaxonomyMapping>,
    val taxonomyVersion: String,
    val mappingTableVersion: String
) {
    init {
        require(modelId.isNotBlank()) { "modelId must be non-blank" }
        require(modelVersion.isNotBlank()) { "modelVersion must be non-blank" }
        require(taxonomyVersion.isNotBlank()) { "taxonomyVersion must be non-blank" }
        require(mappingTableVersion.isNotBlank()) { "mappingTableVersion must be non-blank" }
    }
}

/**
 * Result of a full pipeline run for a single adapter output.
 *
 * Contains the output from every pipeline stage, independently
 * auditable and deterministic.
 */
data class FullPipelineResult(
    val adapterOutput: AdapterRawModelOutput,
    val decodedOutput: DecodedOutput,
    val inferenceResult: ModelInferenceResult?,
    val mappingResults: List<TaxonomyMappingResult>,
    val eligibilityResults: List<EvaluationEligibility>,
    val evaluationResults: List<EvaluationBoundaryResult>,
    val pipelineVersion: String = PIPELINE_VERSION
) {
    val succeeded: Boolean
        get() = decodedOutput is DecodedOutput.Success &&
            inferenceResult?.succeeded == true

    val mappedCount: Int
        get() = mappingResults.count { it is TaxonomyMappingResult.Mapped }

    val unmappedCount: Int
        get() = mappingResults.count { it is TaxonomyMappingResult.Unmapped }

    val ambiguousCount: Int
        get() = mappingResults.count { it is TaxonomyMappingResult.Ambiguous }

    val invalidCount: Int
        get() = mappingResults.count { it is TaxonomyMappingResult.Invalid }

    val eligibleCount: Int
        get() = eligibilityResults.count { it.isEligible }

    companion object {
        const val PIPELINE_VERSION = "8B-15-full-pipeline-v1"
    }
}

/**
 * Runs the full ML pipeline from adapter output through
 * evaluation boundary.
 *
 * This is the research-grade integration point. Each step
 * is a separate, testable boundary.
 */
object FullMlPipeline {

    /**
     * Run the complete pipeline from raw adapter output.
     *
     * @param adapterOutput raw output from 8B-15-5 adapter
     * @param config pipeline configuration (freezes versions)
     * @param evidenceId optional frame/evidence identifier
     * @param sessionId optional session identifier
     * @return complete pipeline result with every stage
     */
    fun run(
        adapterOutput: AdapterRawModelOutput,
        config: FullPipelineConfig,
        evidenceId: String? = null,
        sessionId: String? = null
    ): FullPipelineResult {
        // --------------------------------
        // STAGE 1: Output Interpretation (8B-15-6)
        // --------------------------------

        val decoded = ModelOutputDecoder.decode(
            rawOutput = adapterOutput,
            config = config.interpretationConfig
        )

        val inferenceResult = when (decoded) {
            is DecodedOutput.Success -> {
                ModelOutputDecoder.decodeToInferenceResult(
                    rawOutput = adapterOutput,
                    config = config.interpretationConfig,
                    modelId = config.modelId,
                    modelVersion = config.modelVersion,
                    modelChecksum = config.modelChecksum,
                    preprocessVersion = adapterOutput.preprocessingVersion,
                    evidenceId = evidenceId
                )
            }
            is DecodedOutput.Failure -> {
                ModelInferenceResult(
                    modelId = config.modelId,
                    modelVersion = config.modelVersion,
                    modelChecksum = config.modelChecksum,
                    status = decoded.status,
                    rankedPredictions = emptyList(),
                    evidenceId = evidenceId,
                    failure = com.example.feedsense.analysis.ml.InferenceFailure(
                        status = decoded.status,
                        phase = "decode",
                        message = decoded.message
                    )
                )
            }
        }

        // --------------------------------
        // STAGE 2: Taxonomy Mapping (8B-15-7)
        // --------------------------------

        val mappingResults = if (inferenceResult.rankedPredictions.isNotEmpty()) {
            TaxonomyMappingEngine.mapPredictions(
                inferenceResult = inferenceResult,
                mappings = config.taxonomyMappings,
                taxonomyVersion = config.taxonomyVersion
            )
        } else {
            emptyList()
        }

        // --------------------------------
        // STAGE 3: Evaluation Eligibility (8B-15-8)
        // --------------------------------

        val eligibilityResults = mappingResults.map { mapping ->
            EvaluationEligibility.fromMappingResult(
                mappingResult = mapping,
                expectedTaxonomyVersion = config.taxonomyVersion
            )
        }

        // --------------------------------
        // STAGE 4: Evaluation Boundary (8B-15-8)
        // --------------------------------

        val evaluationResults = mappingResults.mapIndexed { index, mapping ->
            val eligibility = eligibilityResults[index]
            val snapshot = EvaluationCandidateSnapshot.fromMappingResult(
                candidateId = "pipeline-${evidenceId ?: "unknown"}-$index",
                modelId = config.modelId,
                modelVersion = config.modelVersion,
                modelChecksum = config.modelChecksum,
                runtimeVersion = null,
                preprocessingVersion = adapterOutput.preprocessingVersion,
                outputInterpretationVersion =
                    config.interpretationConfig.interpretationVersion,
                mappingResult = mapping,
                predictionScore = inferenceResult.rankedPredictions
                    .firstOrNull {
                        it.category == mapping.modelLabel
                    }
                    ?.confidence,
                inferenceStatus = inferenceResult.status.label,
                evidenceId = evidenceId,
                sessionId = sessionId,
                eligibility = eligibility
            )

            EvaluationBoundaryEvaluator.eligibilityOnly(snapshot)
        }

        return FullPipelineResult(
            adapterOutput = adapterOutput,
            decodedOutput = decoded,
            inferenceResult = inferenceResult,
            mappingResults = mappingResults,
            eligibilityResults = eligibilityResults,
            evaluationResults = evaluationResults
        )
    }
}
