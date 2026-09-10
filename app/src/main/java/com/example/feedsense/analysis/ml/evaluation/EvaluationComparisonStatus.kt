package com.example.feedsense.analysis.ml.evaluation

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.model.GroundTruth

// --------------------------------
// EVALUATION COMPARISON STATUS (8B-15-8)
// --------------------------------
//
// A deterministic structural comparison classification.
//
// This is NOT a metric. It does NOT calculate accuracy,
// precision, recall, F1, confusion matrices, or Cohen's
// kappa. Those belong to a later milestone.
//
// The comparison produces a structural classification:
//   - MATCH: same taxonomy identity/version and same ID
//   - MISMATCH: both valid, IDs differ
//   - UNCOMPARABLE: structural incompatibility prevents
//     legitimate comparison
//   - PENDING_REVIEW: requires human adjudication
//
// Important distinctions:
//   - UNCOMPARABLE is NOT equivalent to MISMATCH.
//     An uncomparable prediction is structurally different
//     from a valid mismatch.
//   - MATCH does NOT mean the prediction is correct.
//     It means both sides refer to the same taxonomy
//     identity/version and the same taxonomy ID.
//   - MISMATCH does NOT mean the prediction is wrong.
//     It means both sides are structurally valid but
//     refer to different taxonomy IDs.
//
// Ground truth remains independent of model output.
// Model confidence does not determine comparison status.

/*
 * Structural comparison status classification.
 */
enum class ComparisonStatus(val label: String) {
    MATCH("MATCH"),
    MISMATCH("MISMATCH"),
    UNCOMPARABLE("UNCOMPARABLE"),
    PENDING_REVIEW("PENDING_REVIEW")
}

/*
 * The result of a deterministic structural comparison
 * between a prediction and ground truth.
 *
 * This is NOT an evaluation metric. It is a structural
 * classification that can be used as input to future
 * metric computation.
 */
data class EvaluationComparisonResult(
    val status: ComparisonStatus,
    val reasons: List<EvaluationDecisionReason>,
    val predictionTaxonomyId: String?,
    val groundTruthTaxonomyId: String?,
    val taxonomyIdentityCompatible: Boolean,
    val taxonomyVersionCompatible: Boolean,
    val groundTruthAnnotationValid: Boolean,
    val comparisonHash: String
)

/*
 * Companion factory for deterministic comparison results.
 */
object EvaluationComparison {

    // --------------------------------
    // PUBLIC API
    // --------------------------------

    /*
     * Produce a deterministic structural comparison from
     * a comparison input.
     *
     * The same comparison input always produces the same
     * result. This method does NOT calculate metrics.
     */
    fun compare(
        input: EvaluationComparisonInput
    ): EvaluationComparisonResult {

        // --------------------------------
        // Step 1: Check prediction eligibility
        // --------------------------------

        if (input.predictionSide.isEligible.not()) {
            return uncomparable(
                reasons = listOf(
                    EvaluationDecisionReason.STRUCTURALLY_UNCOMPARABLE
                ),
                input = input,
                taxonomyIdentityCompatible = false,
                taxonomyVersionCompatible = false,
                groundTruthValid = false
            )
        }

        // --------------------------------
        // Step 2: Check ground truth validity
        // --------------------------------

        val gtValid = validateGroundTruth(input.groundTruthSide)
        if (!gtValid) {
            return uncomparable(
                reasons = listOf(
                    EvaluationDecisionReason.INVALID_GROUND_TRUTH_STATE
                ),
                input = input,
                taxonomyIdentityCompatible = false,
                taxonomyVersionCompatible = false,
                groundTruthValid = false
            )
        }

        // --------------------------------
        // Step 3: Taxonomy identity compatibility
        // --------------------------------

        val identityCompatible =
            checkTaxonomyIdentityCompatibility(input)

        // --------------------------------
        // Step 4: Taxonomy version compatibility
        // --------------------------------

        val versionCompatible =
            checkTaxonomyVersionCompatibility(input)

        if (!identityCompatible || !versionCompatible) {
            val reasons = mutableListOf<EvaluationDecisionReason>()
            if (!identityCompatible) {
                reasons.add(
                    EvaluationDecisionReason.TAXONOMY_VERSION_MISMATCH
                )
            }
            if (!versionCompatible) {
                reasons.add(
                    EvaluationDecisionReason.TAXONOMY_VERSION_MISMATCH
                )
            }
            return uncomparable(
                reasons = reasons,
                input = input,
                taxonomyIdentityCompatible = identityCompatible,
                taxonomyVersionCompatible = versionCompatible,
                groundTruthValid = true
            )
        }

        // --------------------------------
        // Step 5: Taxonomy ID comparison
        // --------------------------------

        val predId = normalizeTaxonomyId(
            input.predictionSide.feedSenseTaxonomyId
        )
        val gtId = normalizeTaxonomyId(
            input.groundTruthSide.category
        )

        val reasons = mutableListOf<EvaluationDecisionReason>()

        return when {
            predId == null || gtId == null -> {
                reasons.add(
                    EvaluationDecisionReason.STRUCTURALLY_UNCOMPARABLE
                )
                uncomparable(
                    reasons = reasons,
                    input = input,
                    taxonomyIdentityCompatible = true,
                    taxonomyVersionCompatible = true,
                    groundTruthValid = true
                )
            }

            predId == gtId -> {
                reasons.add(
                    EvaluationDecisionReason.TAXONOMY_ID_MATCH
                )
                EvaluationComparisonResult(
                    status = ComparisonStatus.MATCH,
                    reasons = reasons,
                    predictionTaxonomyId = predId,
                    groundTruthTaxonomyId = gtId,
                    taxonomyIdentityCompatible = true,
                    taxonomyVersionCompatible = true,
                    groundTruthAnnotationValid = true,
                    comparisonHash = ""
                ).let { result ->
                    result.copy(
                        comparisonHash =
                            EvaluationBoundarySerializer
                                .computeComparisonHash(result)
                    )
                }
            }

            else -> {
                reasons.add(
                    EvaluationDecisionReason.TAXONOMY_ID_MISMATCH
                )
                EvaluationComparisonResult(
                    status = ComparisonStatus.MISMATCH,
                    reasons = reasons,
                    predictionTaxonomyId = predId,
                    groundTruthTaxonomyId = gtId,
                    taxonomyIdentityCompatible = true,
                    taxonomyVersionCompatible = true,
                    groundTruthAnnotationValid = true,
                    comparisonHash = ""
                ).let { result ->
                    result.copy(
                        comparisonHash =
                            EvaluationBoundarySerializer
                                .computeComparisonHash(result)
                    )
                }
            }
        }
    }

    // --------------------------------
    // INTERNAL
    // --------------------------------

    private fun validateGroundTruth(
        gt: GroundTruthComparisonSnapshot
    ): Boolean {
        if (gt.groundTruthId.isBlank()) return false
        if (gt.evaluationItemId.isBlank()) return false
        if (gt.ambiguity == GroundTruth.AMBIGUITY_UNKNOWN) {
            return false
        }
        return true
    }

    private fun checkTaxonomyIdentityCompatibility(
        input: EvaluationComparisonInput
    ): Boolean {
        val predVersion = input.predictionSide.taxonomyVersion
        val gtVersion = input.groundTruthSide.taxonomyVersion

        if (predVersion.isBlank()) return false
        if (gtVersion == null) return false
        if (gtVersion.isBlank()) return false

        return predVersion == gtVersion
    }

    private fun checkTaxonomyVersionCompatibility(
        input: EvaluationComparisonInput
    ): Boolean {
        return checkTaxonomyIdentityCompatibility(input)
    }

    private fun normalizeTaxonomyId(
        id: String?
    ): String? {
        if (id.isNullOrBlank()) return null
        return CategoryCatalog.normalize(id) ?: id
    }

    private fun uncomparable(
        reasons: List<EvaluationDecisionReason>,
        input: EvaluationComparisonInput,
        taxonomyIdentityCompatible: Boolean,
        taxonomyVersionCompatible: Boolean,
        groundTruthValid: Boolean
    ): EvaluationComparisonResult {
        val result = EvaluationComparisonResult(
            status = ComparisonStatus.UNCOMPARABLE,
            reasons = reasons,
            predictionTaxonomyId =
                input.predictionSide.feedSenseTaxonomyId
                    .takeIf { it.isNotBlank() },
            groundTruthTaxonomyId =
                input.groundTruthSide.category,
            taxonomyIdentityCompatible =
                taxonomyIdentityCompatible,
            taxonomyVersionCompatible =
                taxonomyVersionCompatible,
            groundTruthAnnotationValid = groundTruthValid,
            comparisonHash = ""
        )
        return result.copy(
            comparisonHash =
                EvaluationBoundarySerializer
                    .computeComparisonHash(result)
        )
    }
}
