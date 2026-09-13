package com.example.feedsense.analysis.ml.evaluation

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.analysis.SchemaFreeze
import com.example.feedsense.analysis.ml.ModelInferenceResult
import com.example.feedsense.analysis.ml.InferenceStatus
import com.example.feedsense.analysis.ml.RankedPrediction
import com.example.feedsense.analysis.ml.taxonomy.MappingFailureCode
import com.example.feedsense.analysis.ml.taxonomy.MappingProvenance
import com.example.feedsense.analysis.ml.taxonomy.MappingRationale
import com.example.feedsense.analysis.ml.taxonomy.MappingStatus
import com.example.feedsense.analysis.ml.taxonomy.ModelTaxonomyMapping
import com.example.feedsense.analysis.ml.taxonomy.TaxonomyMappingEngine
import com.example.feedsense.analysis.ml.taxonomy.TaxonomyMappingResult
import com.example.feedsense.analysis.ml.taxonomy.TaxonomyMappingVersion
import com.example.feedsense.model.GroundTruth

// --------------------------------
// EVALUATION BOUNDARY FIXTURES (8B-15-8)
// --------------------------------
//
// Golden deterministic fixture set for evaluation boundary
// tests.
//
// Includes:
//   - Direct eligible prediction + matching ground truth
//   - Mapped eligible prediction + matching ground truth
//   - Direct/mapped prediction + different ground truth
//   - Unmapped prediction
//   - Ambiguous prediction
//   - Unsupported prediction
//   - Rejected prediction
//   - Invalid mapping
//   - Missing mapping
//   - Missing prediction
//   - Missing ground truth
//   - Taxonomy-version mismatch
//   - Invalid ground-truth state
//   - High confidence + invalid mapping
//   - Low confidence + valid mapping
//
// Fixtures are:
//   - Human-readable
//   - Versioned
//   - Deterministic
//   - Local-only
//   - Independent from production database contents

object EvaluationBoundaryFixtures {

    // --------------------------------
    // VERSIONS
    // --------------------------------

    val TAXONOMY_VERSION: String =
        TaxonomyMappingVersion.computeTaxonomyVersion(
            frozenVersion = SchemaFreeze.FREEZE_VERSION,
            sortedCategoryKeys = CategoryCatalog.keys.sorted()
        )

    const val MAPPING_TABLE_VERSION = "eval-fix-mapping-v1"
    const val MODEL_ARTIFACT_ID = "eval-fix-mobilenetv4"
    const val MODEL_ARTIFACT_VERSION = "v1-eval-fix"
    const val FIXTURE_VERSION = "8B-15-8-fixtures-v1"

    // --------------------------------
    // GROUND TRUTH FIXTURES
    // --------------------------------

    fun groundTruthSports(): GroundTruth {
        return GroundTruth(
            id = "gt-sports-001",
            evaluationItemId = "eval-item-001",
            annotatorId = "annotator-test",
            category = "sports",
            categoryDomain = "sports",
            secondaryCategories = emptyList(),
            ambiguity = GroundTruth.AMBIGUITY_CLEAR,
            platform = "TikTok"
        )
    }

    fun groundTruthEntertainment(): GroundTruth {
        return GroundTruth(
            id = "gt-entertainment-002",
            evaluationItemId = "eval-item-002",
            annotatorId = "annotator-test",
            category = "entertainment",
            categoryDomain = "entertainment",
            secondaryCategories = listOf("comedy"),
            ambiguity = GroundTruth.AMBIGUITY_CLEAR,
            platform = "YouTube"
        )
    }

    fun groundTruthEducation(): GroundTruth {
        return GroundTruth(
            id = "gt-education-003",
            evaluationItemId = "eval-item-003",
            annotatorId = "annotator-test",
            category = "education",
            categoryDomain = "education",
            secondaryCategories = emptyList(),
            ambiguity = GroundTruth.AMBIGUITY_CLEAR,
            platform = "YouTube"
        )
    }

    fun groundTruthUnknownAmbiguity(): GroundTruth {
        return GroundTruth(
            id = "gt-unknown-004",
            evaluationItemId = "eval-item-004",
            annotatorId = "annotator-test",
            category = "sports",
            ambiguity = GroundTruth.AMBIGUITY_UNKNOWN
        )
    }

    fun groundTruthNullCategory(): GroundTruth {
        return GroundTruth(
            id = "gt-null-cat-005",
            evaluationItemId = "eval-item-005",
            annotatorId = "annotator-test",
            category = null,
            ambiguity = GroundTruth.AMBIGUITY_CLEAR
        )
    }

    // --------------------------------
    // MODEL INFERENCE RESULT FIXTURES
    // --------------------------------

    fun inferenceResultSports(): ModelInferenceResult {
        return ModelInferenceResult(
            modelId = MODEL_ARTIFACT_ID,
            modelVersion = MODEL_ARTIFACT_VERSION,
            status = InferenceStatus.SUCCESS,
            rankedPredictions = listOf(
                RankedPrediction("sports", 0.92),
                RankedPrediction("entertainment", 0.05)
            ),
            preprocessVersion = "preprocess-v1",
            evidenceId = "evidence-001",
            timestampMs = 1000L
        )
    }

    fun inferenceResultMappedLabel(): ModelInferenceResult {
        return ModelInferenceResult(
            modelId = MODEL_ARTIFACT_ID,
            modelVersion = MODEL_ARTIFACT_VERSION,
            status = InferenceStatus.SUCCESS,
            rankedPredictions = listOf(
                RankedPrediction("model_athletics", 0.85)
            ),
            preprocessVersion = "preprocess-v1",
            evidenceId = "evidence-002",
            timestampMs = 2000L
        )
    }

    fun inferenceResultUnmappedLabel(): ModelInferenceResult {
        return ModelInferenceResult(
            modelId = MODEL_ARTIFACT_ID,
            modelVersion = MODEL_ARTIFACT_VERSION,
            status = InferenceStatus.SUCCESS,
            rankedPredictions = listOf(
                RankedPrediction("weather_forecast", 0.78)
            ),
            preprocessVersion = "preprocess-v1",
            evidenceId = "evidence-003",
            timestampMs = 3000L
        )
    }

    fun inferenceResultAmbiguousLabel(): ModelInferenceResult {
        return ModelInferenceResult(
            modelId = MODEL_ARTIFACT_ID,
            modelVersion = MODEL_ARTIFACT_VERSION,
            status = InferenceStatus.SUCCESS,
            rankedPredictions = listOf(
                RankedPrediction("daily_vlog", 0.71)
            ),
            preprocessVersion = "preprocess-v1",
            evidenceId = "evidence-004",
            timestampMs = 4000L
        )
    }

    fun inferenceResultUnsupportedLabel(): ModelInferenceResult {
        return ModelInferenceResult(
            modelId = MODEL_ARTIFACT_ID,
            modelVersion = MODEL_ARTIFACT_VERSION,
            status = InferenceStatus.SUCCESS,
            rankedPredictions = listOf(
                RankedPrediction("unknown_model_class", 0.65)
            ),
            preprocessVersion = "preprocess-v1",
            evidenceId = "evidence-005",
            timestampMs = 5000L
        )
    }

    fun inferenceResultRejectedLabel(): ModelInferenceResult {
        return ModelInferenceResult(
            modelId = MODEL_ARTIFACT_ID,
            modelVersion = MODEL_ARTIFACT_VERSION,
            status = InferenceStatus.SUCCESS,
            rankedPredictions = listOf(
                RankedPrediction("model_political_commentary", 0.88)
            ),
            preprocessVersion = "preprocess-v1",
            evidenceId = "evidence-006",
            timestampMs = 6000L
        )
    }

    fun inferenceResultFailedStatus(): ModelInferenceResult {
        return ModelInferenceResult(
            modelId = MODEL_ARTIFACT_ID,
            modelVersion = MODEL_ARTIFACT_VERSION,
            status = InferenceStatus.INFERENCE_FAILURE,
            rankedPredictions = emptyList(),
            preprocessVersion = "preprocess-v1",
            evidenceId = "evidence-007",
            timestampMs = 7000L
        )
    }

    fun inferenceResultHighConfidence(): ModelInferenceResult {
        return ModelInferenceResult(
            modelId = MODEL_ARTIFACT_ID,
            modelVersion = MODEL_ARTIFACT_VERSION,
            status = InferenceStatus.SUCCESS,
            rankedPredictions = listOf(
                RankedPrediction("sports", 0.99)
            ),
            preprocessVersion = "preprocess-v1",
            evidenceId = "evidence-008",
            timestampMs = 8000L
        )
    }

    fun inferenceResultLowConfidence(): ModelInferenceResult {
        return ModelInferenceResult(
            modelId = MODEL_ARTIFACT_ID,
            modelVersion = MODEL_ARTIFACT_VERSION,
            status = InferenceStatus.SUCCESS,
            rankedPredictions = listOf(
                RankedPrediction("sports", 0.35)
            ),
            preprocessVersion = "preprocess-v1",
            evidenceId = "evidence-009",
            timestampMs = 9000L
        )
    }

    // --------------------------------
    // TAXONOMY MAPPING TABLE
    // --------------------------------

    val ALL_MAPPINGS: List<ModelTaxonomyMapping> = listOf(
        ModelTaxonomyMapping(
            mappingId = "eval-direct-sports",
            modelArtifactId = MODEL_ARTIFACT_ID,
            modelArtifactVersion = MODEL_ARTIFACT_VERSION,
            modelLabelIndex = 0,
            modelLabel = "sports",
            feedSenseTaxonomyKey = "sports",
            status = MappingStatus.DIRECT,
            rationale = MappingRationale.SEMANTIC_EQUIVALENCE,
            provenance = MappingProvenance.PROJECT_DEFINED,
            mappingTableVersion = MAPPING_TABLE_VERSION,
            taxonomyVersion = TAXONOMY_VERSION
        ),
        ModelTaxonomyMapping(
            mappingId = "eval-direct-entertainment",
            modelArtifactId = MODEL_ARTIFACT_ID,
            modelArtifactVersion = MODEL_ARTIFACT_VERSION,
            modelLabelIndex = 1,
            modelLabel = "entertainment",
            feedSenseTaxonomyKey = "entertainment",
            status = MappingStatus.DIRECT,
            rationale = MappingRationale.SEMANTIC_EQUIVALENCE,
            provenance = MappingProvenance.PROJECT_DEFINED,
            mappingTableVersion = MAPPING_TABLE_VERSION,
            taxonomyVersion = TAXONOMY_VERSION
        ),
        ModelTaxonomyMapping(
            mappingId = "eval-direct-education",
            modelArtifactId = MODEL_ARTIFACT_ID,
            modelArtifactVersion = MODEL_ARTIFACT_VERSION,
            modelLabelIndex = 2,
            modelLabel = "education",
            feedSenseTaxonomyKey = "education",
            status = MappingStatus.DIRECT,
            rationale = MappingRationale.SEMANTIC_EQUIVALENCE,
            provenance = MappingProvenance.PROJECT_DEFINED,
            mappingTableVersion = MAPPING_TABLE_VERSION,
            taxonomyVersion = TAXONOMY_VERSION
        ),
        ModelTaxonomyMapping(
            mappingId = "eval-mapped-athletics",
            modelArtifactId = MODEL_ARTIFACT_ID,
            modelArtifactVersion = MODEL_ARTIFACT_VERSION,
            modelLabelIndex = 3,
            modelLabel = "model_athletics",
            feedSenseTaxonomyKey = "sports",
            status = MappingStatus.MAPPED,
            rationale = MappingRationale.BROADER_FEEDSENSE_CATEGORY,
            provenance = MappingProvenance.PROJECT_DEFINED,
            mappingTableVersion = MAPPING_TABLE_VERSION,
            taxonomyVersion = TAXONOMY_VERSION,
            rationaleNotes = "Athletics maps to sports domain"
        ),
        ModelTaxonomyMapping(
            mappingId = "eval-unmapped-weather",
            modelArtifactId = MODEL_ARTIFACT_ID,
            modelArtifactVersion = MODEL_ARTIFACT_VERSION,
            modelLabelIndex = 4,
            modelLabel = "weather_forecast",
            status = MappingStatus.UNMAPPED,
            provenance = MappingProvenance.PROJECT_DEFINED,
            mappingTableVersion = MAPPING_TABLE_VERSION,
            taxonomyVersion = TAXONOMY_VERSION,
            rationaleNotes = "No FeedSense equivalent"
        ),
        ModelTaxonomyMapping(
            mappingId = "eval-ambiguous-vlog",
            modelArtifactId = MODEL_ARTIFACT_ID,
            modelArtifactVersion = MODEL_ARTIFACT_VERSION,
            modelLabelIndex = 5,
            modelLabel = "daily_vlog",
            status = MappingStatus.AMBIGUOUS,
            provenance = MappingProvenance.PROVISIONAL,
            mappingTableVersion = MAPPING_TABLE_VERSION,
            taxonomyVersion = TAXONOMY_VERSION,
            rationaleNotes = "Could be lifestyle or entertainment"
        ),
        ModelTaxonomyMapping(
            mappingId = "eval-unsupported-unknown",
            modelArtifactId = MODEL_ARTIFACT_ID,
            modelArtifactVersion = MODEL_ARTIFACT_VERSION,
            modelLabelIndex = 6,
            modelLabel = "unknown_model_class",
            status = MappingStatus.UNSUPPORTED,
            provenance = MappingProvenance.PROJECT_DEFINED,
            mappingTableVersion = MAPPING_TABLE_VERSION,
            taxonomyVersion = TAXONOMY_VERSION,
            rationaleNotes = "Not in model label set"
        ),
        ModelTaxonomyMapping(
            mappingId = "eval-rejected-politics",
            modelArtifactId = MODEL_ARTIFACT_ID,
            modelArtifactVersion = MODEL_ARTIFACT_VERSION,
            modelLabelIndex = 7,
            modelLabel = "model_political_commentary",
            status = MappingStatus.REJECTED,
            provenance = MappingProvenance.HUMAN_REVIEWED,
            mappingTableVersion = MAPPING_TABLE_VERSION,
            taxonomyVersion = TAXONOMY_VERSION,
            rationaleNotes = "Rejected: semantic mismatch"
        )
    )

    // --------------------------------
    // MAPPING VERSION
    // --------------------------------

    val MAPPING_VERSION = TaxonomyMappingVersion(
        mappingTableVersion = MAPPING_TABLE_VERSION,
        taxonomyVersion = TAXONOMY_VERSION,
        modelArtifactId = MODEL_ARTIFACT_ID,
        modelArtifactVersion = MODEL_ARTIFACT_VERSION,
        labelCount = ALL_MAPPINGS.size,
        taxonomyKeyCount = CategoryCatalog.keys.size
    )

    // --------------------------------
    // TAXONOMY VERSION MISMATCH FIXTURE
    // --------------------------------

    const val DIFFERENT_TAXONOMY_VERSION =
        "taxonomy:different:0:abcdef"

    // --------------------------------
    // HELPER: MAP PREDICTIONS
    // --------------------------------

    fun mapInferenceResult(
        inferenceResult: ModelInferenceResult
    ): List<TaxonomyMappingResult> {
        return TaxonomyMappingEngine.mapPredictions(
            inferenceResult = inferenceResult,
            mappings = ALL_MAPPINGS,
            taxonomyVersion = TAXONOMY_VERSION
        )
    }

    // --------------------------------
    // HELPER: CREATE SNAPSHOT FROM
    //         MAPPING RESULT
    // --------------------------------

    fun createSnapshot(
        mappingResult: TaxonomyMappingResult,
        inferenceResult: ModelInferenceResult,
        candidateId: String,
        eligibility: EvaluationEligibility
    ): EvaluationCandidateSnapshot {
        return EvaluationCandidateSnapshot.fromMappingResult(
            candidateId = candidateId,
            modelId = inferenceResult.modelId,
            modelVersion = inferenceResult.modelVersion,
            modelChecksum = inferenceResult.modelChecksum,
            runtimeVersion = null,
            preprocessingVersion =
                inferenceResult.preprocessVersion,
            outputInterpretationVersion = null,
            mappingResult = mappingResult,
            predictionScore = inferenceResult
                .rankedPredictions
                .firstOrNull {
                    it.category == mappingResult.modelLabel
                }
                ?.confidence,
            inferenceStatus =
                inferenceResult.status.label,
            evidenceId = inferenceResult.evidenceId,
            eligibility = eligibility
        )
    }
}
