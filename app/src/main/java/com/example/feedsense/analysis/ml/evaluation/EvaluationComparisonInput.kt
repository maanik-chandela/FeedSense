package com.example.feedsense.analysis.ml.evaluation

import com.example.feedsense.model.GroundTruth

// --------------------------------
// EVALUATION COMPARISON INPUT (8B-15-8)
// --------------------------------
//
// A standalone comparison contract that clearly identifies
// what is being compared from the prediction side and the
// ground-truth side.
//
// The comparison layer makes clear that ground truth is
// independently authored. Ground truth is never derived
// from, modified by, or influenced by the model prediction.
//
// This contract does NOT calculate metrics. It produces
// a structural comparison classification only.

// --------------------------------
// PREDICTION SIDE
// --------------------------------

/*
 * Read-only representation of the prediction side of a
 * comparison. Contains only the taxonomy-mapped prediction
 * state required for structural comparison.
 */
data class PredictionComparisonSide(
    val feedSenseTaxonomyId: String,
    val taxonomyVersion: String,
    val mappedModelLabel: String,
    val modelArtifactId: String,
    val modelArtifactVersion: String,
    val mappingId: String,
    val mappingVersion: String,
    val mappingStatus: String,
    val mappingStrength: Double? = null,
    val predictionScore: Double? = null,
    val predictionInterpretationVersion: String? = null,
    val eligibility: EvaluationEligibility
) {
    val isEligible: Boolean
        get() = eligibility.isEligible
}

// --------------------------------
// GROUND TRUTH SIDE (READ-ONLY REFERENCE)
// --------------------------------

/*
 * Read-only snapshot of ground truth for comparison purposes.
 *
 * This is NOT a copy of the GroundTruth entity. It is a
 * minimal read-only reference containing only what is
 * required for structural comparison.
 *
 * Ground truth remains independently authored and is never
 * modified by the evaluation boundary.
 */
data class GroundTruthComparisonSnapshot(
    val groundTruthId: String,
    val evaluationItemId: String,
    val annotatorId: String? = null,
    val taxonomyVersion: String? = null,
    val category: String? = null,
    val categoryDomain: String? = null,
    val secondaryCategories: List<String> = emptyList(),
    val ambiguity: String = GroundTruth.AMBIGUITY_CLEAR,
    val annotationStatus: String = "RECORDED",
    val annotationVersion: String? = null,
    val hasInteractionSignals: Boolean = false,
    val hasNotes: Boolean = false
) {
    companion object {

        /*
         * Create a read-only snapshot from a GroundTruth
         * entity. The snapshot captures only the fields
         * needed for comparison; it does NOT store the
         * full entity.
         *
         * This method does NOT modify the GroundTruth.
         */
        fun fromGroundTruth(
            truth: GroundTruth,
            taxonomyVersion: String? = null,
            annotationVersion: String? = null
        ): GroundTruthComparisonSnapshot {
            return GroundTruthComparisonSnapshot(
                groundTruthId = truth.id,
                evaluationItemId = truth.evaluationItemId,
                annotatorId = truth.annotatorId,
                taxonomyVersion = taxonomyVersion,
                category = truth.category,
                categoryDomain = truth.categoryDomain,
                secondaryCategories = truth.secondaryCategories,
                ambiguity = truth.ambiguity,
                annotationStatus = resolveAnnotationStatus(truth),
                annotationVersion = annotationVersion,
                hasInteractionSignals =
                    truth.interactionSignals.isNotEmpty(),
                hasNotes = truth.notes != null
            )
        }

        private fun resolveAnnotationStatus(
            truth: GroundTruth
        ): String {
            return when (truth.ambiguity) {
                GroundTruth.AMBIGUITY_UNKNOWN -> "INCOMPLETE"
                GroundTruth.AMBIGUITY_CLEAR -> "RECORDED"
                GroundTruth.AMBIGUITY_AMBIGUOUS -> "RECORDED"
                GroundTruth.AMBIGUITY_MIXED -> "RECORDED"
                else -> "RECORDED"
            }
        }
    }
}

// --------------------------------
// COMPARISON INPUT
// --------------------------------

/*
 * The complete comparison input, pairing a prediction side
 * with a ground-truth side.
 *
 * Both sides are independently authored and immutable.
 * The comparison input does not modify either side.
 */
data class EvaluationComparisonInput(
    val predictionSide: PredictionComparisonSide,
    val groundTruthSide: GroundTruthComparisonSnapshot,
    val candidateSnapshotId: String? = null,
    val expectedTaxonomyVersion: String? = null
) {
    companion object {

        /*
         * Create a comparison input from a candidate
         * snapshot and a ground truth entity.
         *
         * The comparison input does NOT store or modify
         * the original GroundTruth entity.
         */
        fun fromCandidateAndTruth(
            snapshot: EvaluationCandidateSnapshot,
            truth: GroundTruth,
            groundTruthTaxonomyVersion: String? = null,
            annotationVersion: String? = null
        ): EvaluationComparisonInput {

            val predictionSide = PredictionComparisonSide(
                feedSenseTaxonomyId =
                    snapshot.mappedTaxonomyKey ?: "",
                taxonomyVersion = snapshot.taxonomyVersion,
                mappedModelLabel = snapshot.modelNativeLabel,
                modelArtifactId = snapshot.modelId,
                modelArtifactVersion = snapshot.modelVersion,
                mappingId = snapshot.mappingId,
                mappingVersion = snapshot.mappingTableVersion,
                mappingStatus = snapshot.mappingStatus.label,
                mappingStrength = snapshot.mappingStrength,
                predictionScore = snapshot.predictionScore,
                predictionInterpretationVersion =
                    snapshot.outputInterpretationVersion,
                eligibility = snapshot.eligibility
            )

            val truthSide =
                GroundTruthComparisonSnapshot.fromGroundTruth(
                    truth = truth,
                    taxonomyVersion = groundTruthTaxonomyVersion,
                    annotationVersion = annotationVersion
                )

            return EvaluationComparisonInput(
                predictionSide = predictionSide,
                groundTruthSide = truthSide,
                candidateSnapshotId = snapshot.candidateId,
                expectedTaxonomyVersion =
                    groundTruthTaxonomyVersion
            )
        }
    }
}
