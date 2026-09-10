package com.example.feedsense.analysis.ml.evaluation

import com.example.feedsense.analysis.ml.taxonomy.MappingStatus
import com.example.feedsense.analysis.ml.taxonomy.TaxonomyMappingResult

// --------------------------------
// EVALUATION CANDIDATE SNAPSHOT (8B-15-8)
// --------------------------------
//
// An immutable historical snapshot that freezes exactly
// what prediction state enters evaluation.
//
// The snapshot captures the full pipeline state at the
// moment the candidate was created. Changing the mapping
// registry later must NOT mutate the snapshot.
//
// A future taxonomy mapping change must NOT silently change
// what this candidate meant. All version identities are
// frozen into the snapshot.
//
// Design principles (section 13):
//   - Immutable: once created, never mutated.
//   - Deterministic: same inputs produce identical snapshots.
//   - Auditable: every version identity is recorded.
//   - Standalone: does not depend on mutable database state.
//   - Non-forcing: unmapped/ambiguous/invalid predictions
//     are preserved as-is, never silently converted.

/*
 * Immutable snapshot of a prediction candidate entering
 * the evaluation boundary.
 */
data class EvaluationCandidateSnapshot(
    // --------------------------------
    // CANDIDATE IDENTITY
    // --------------------------------

    val candidateId: String,

    // --------------------------------
    // MODEL ARTIFACT IDENTITY
    // --------------------------------

    val modelId: String,
    val modelVersion: String,
    val modelChecksum: String? = null,

    // --------------------------------
    // RUNTIME IDENTITY
    // --------------------------------

    val runtimeVersion: String? = null,

    // --------------------------------
    // PREPROCESSING VERSION
    // --------------------------------

    val preprocessingVersion: String? = null,

    // --------------------------------
    // OUTPUT INTERPRETATION VERSION
    // --------------------------------
    //
    // For future use when 8B-15-6 is implemented.
    // Null means no separate interpretation step.

    val outputInterpretationVersion: String? = null,

    // --------------------------------
    // PREDICTION IDENTITY
    // --------------------------------

    val modelNativeLabel: String,
    val modelNativeLabelIndex: Int,
    val predictionRank: Int,
    val predictionScore: Double? = null,
    val inferenceStatus: String? = null,

    // --------------------------------
    // TAXONOMY MAPPING IDENTITY
    // --------------------------------

    val mappingId: String,
    val mappingTableVersion: String,
    val taxonomyVersion: String,
    val taxonomyIdentity: String,
    val mappingStatus: MappingStatus,
    val mappedTaxonomyKey: String? = null,
    val mappedTaxonomyDisplayName: String? = null,
    val mappingStrength: Double? = null,
    val mappingProvenance: String? = null,

    // --------------------------------
    // SOURCE EVIDENCE
    // --------------------------------

    val evidenceId: String? = null,
    val sessionId: String? = null,

    // --------------------------------
    // ELIGIBILITY STATE
    // --------------------------------

    val eligibility: EvaluationEligibility,

    // --------------------------------
    // DETERMINISTIC PROVENANCE
    // --------------------------------

    val deterministicCreationHash: String,

    // --------------------------------
    // CREATION METADATA
    // --------------------------------

    val creationTimestampMs: Long = 0L
) {

    companion object {

        /*
         * Create a snapshot from a taxonomy mapping result
         * and its upstream context. The snapshot freezes all
         * version identities so future changes to the mapping
         * registry do not alter the meaning of this candidate.
         */
        fun fromMappingResult(
            candidateId: String,
            modelId: String,
            modelVersion: String,
            modelChecksum: String? = null,
            runtimeVersion: String? = null,
            preprocessingVersion: String? = null,
            outputInterpretationVersion: String? = null,
            mappingResult: TaxonomyMappingResult,
            predictionScore: Double? = null,
            inferenceStatus: String? = null,
            evidenceId: String? = null,
            sessionId: String? = null,
            eligibility: EvaluationEligibility,
            creationTimestampMs: Long = 0L
        ): EvaluationCandidateSnapshot {

            val taxonomyIdentity =
                "feedsense-taxonomy:${mappingResult.taxonomyVersion}"

            val mappedKey: String? =
                (mappingResult as? TaxonomyMappingResult.Mapped)
                    ?.feedSenseTaxonomyKey
            val mappedDisplayName: String? =
                (mappingResult as? TaxonomyMappingResult.Mapped)
                    ?.feedSenseTaxonomyDisplayName
            val strength: Double? =
                (mappingResult as? TaxonomyMappingResult.Mapped)
                    ?.mappingStrength
            val provenance: String? =
                (mappingResult as? TaxonomyMappingResult.Mapped)
                    ?.provenance?.label

            val snapshot = EvaluationCandidateSnapshot(
                candidateId = candidateId,
                modelId = modelId,
                modelVersion = modelVersion,
                modelChecksum = modelChecksum,
                runtimeVersion = runtimeVersion,
                preprocessingVersion = preprocessingVersion,
                outputInterpretationVersion = outputInterpretationVersion,
                modelNativeLabel = mappingResult.modelLabel,
                modelNativeLabelIndex = mappingResult.modelLabelIndex,
                predictionRank = mappingResult.modelLabelIndex,
                predictionScore = predictionScore,
                inferenceStatus = inferenceStatus,
                mappingId = mappingResult.mappingId,
                mappingTableVersion = mappingResult.mappingTableVersion,
                taxonomyVersion = mappingResult.taxonomyVersion,
                taxonomyIdentity = taxonomyIdentity,
                mappingStatus = resolveMappingStatus(mappingResult),
                mappedTaxonomyKey = mappedKey,
                mappedTaxonomyDisplayName = mappedDisplayName,
                mappingStrength = strength,
                mappingProvenance = provenance,
                evidenceId = evidenceId,
                sessionId = sessionId,
                eligibility = eligibility,
                deterministicCreationHash = "",
                creationTimestampMs = creationTimestampMs
            )

            return snapshot.copy(
                deterministicCreationHash =
                    EvaluationBoundarySerializer
                        .computeSnapshotHash(snapshot)
            )
        }

        private fun resolveMappingStatus(
            result: TaxonomyMappingResult
        ): MappingStatus {
            return when (result) {
                is TaxonomyMappingResult.Mapped ->
                    result.status
                is TaxonomyMappingResult.Unmapped ->
                    MappingStatus.UNMAPPED
                is TaxonomyMappingResult.Ambiguous ->
                    MappingStatus.AMBIGUOUS
                is TaxonomyMappingResult.Invalid ->
                    MappingStatus.REJECTED
            }
        }
    }
}
