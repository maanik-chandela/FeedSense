package com.example.feedsense.analysis.evidence.decision

/*
 * Milestone 8B-7.
 *
 * Prediction comparison.
 *
 * A reproducible head-to-head between the BASELINE
 * (existing classifier) and the EVIDENCE-AWARE (8B-7)
 * decision for the same item.
 *
 * This is a diagnostic / research hook. It does NOT
 * alter either path and does NOT claim one is "better" -
 * it only surfaces the agreement/disagreement so a later
 * benchmark can test which pathway aligns with ground
 * truth (Milestone 4, calibration).
 *
 * Design:
 *   - Both sides kept separate and immutable.
 *   - Agreement computed over primary category.
 *   - When baseline decided a category but evidence-aware
 *     abstained, or vice versa, the discrepancy is
 *     explicit.
 *   - Always reproducible: same inputs -> same comparison.
 */
data class PredictionComparison(
    val baseline: BaselinePrediction,
    val evidenceAware: ItemPredictionResult,

    /*
     * Whether both paths named the same primary category.
     * TRUE only when both are non-null and equal.
     */
    val categoriesAgree: Boolean,

    /*
     * Which path is more conservative (more likely to
     * abstain or flag uncertainty).
     */
    val comparisonKind: ComparisonKind
) {

    /*
     * The baseline's decided category.
     */
    val baselinePrimary: String?
        get() = baseline.primaryCategory

    /*
     * The evidence-aware decided category (only when not
     * abstaining).
     */
    val evidenceAwarePrimary: String?
        get() = evidenceAware.primaryCategory

    /*
     * TRUE when the evidence-aware decision abstained
     * (did not decide) while the baseline decided.
     */
    val baselineDecidedButEvidenceAwereAbstained: Boolean
        get() = baseline.primaryCategory != null &&
                evidenceAware.primaryCategory == null

    /*
     * TRUE when the evidence-aware decided while the
     * baseline did not.
     */
    val evidenceAwareDecidedButBaselineAbstained: Boolean
        get() = baseline.primaryCategory == null &&
                evidenceAware.primaryCategory != null

    /**
     * Builds the comparison from two independent
     * predictions.
     */
    companion object {

        fun of(
            baseline: BaselinePrediction,
            evidenceAware: ItemPredictionResult
        ): PredictionComparison {
            val agree =
                baseline.primaryCategory != null &&
                        baseline.primaryCategory ==
                            evidenceAware.primaryCategory

            val kind = when {
                agree ->
                    ComparisonKind.AGREE
                evidenceAware.abstained &&
                    baseline.primaryCategory != null ->
                    ComparisonKind.BASELINE_ONLY_DECIDED
                evidenceAware.madeCategoryDecision &&
                    baseline.primaryCategory == null ->
                    ComparisonKind.EVIDENCE_AWARE_ONLY_DECIDED
                else ->
                    ComparisonKind.DISAGREE
            }

            return PredictionComparison(
                baseline = baseline,
                evidenceAware = evidenceAware,
                categoriesAgree = agree,
                comparisonKind = kind
            )
        }
    }
}

/*
 * The type of agreement/disagreement between the two
 * pathways.
 */
enum class ComparisonKind {
    /*
     * Both decided the same category.
     */
    AGREE,

    /*
     * Both decided but to different categories.
     */
    DISAGREE,

    /*
     * Baseline decided; evidence-aware abstained.
     */
    BASELINE_ONLY_DECIDED,

    /*
     * Evidence-aware decided; baseline abstained.
     */
    EVIDENCE_AWARE_ONLY_DECIDED
}
