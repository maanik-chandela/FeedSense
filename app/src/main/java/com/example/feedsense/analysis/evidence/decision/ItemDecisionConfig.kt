package com.example.feedsense.analysis.evidence.decision

/*
 * Milestone 8B-7.
 *
 * Item decision configuration.
 *
 * Frozen, explicit, documented and test-covered thresholds
 * for the evidence-aware decision layer. Every threshold
 * has:
 *   - a name
 *   - a documented rationale
 *   - determinism
 *   - test coverage
 *
 * IMPORTANT (8B-7 section 24):
 *   Thresholds here are NOT optimized against the
 *   evaluation dataset. That would contaminate future
 *   experiments. They are fixed research priors. Any
 *   future change must bump CONFIG_VERSION and preserve
 *   historical reproducibility.
 *
 * Each threshold is chosen to be conservative: when in
 * doubt, the system abstains (INSUFFICIENT_EVIDENCE /
 * AMBIGUOUS) rather than fabricating confidence.
 */
data class ItemDecisionConfig(
    val decisionVersion: String = DECISION_VERSION,
    val configVersion: String = CONFIG_VERSION,

    // --------------------------------
    // DECISION TECHNIQUE THRESHOLDS
    // --------------------------------

    /*
     * Absolute support below which no category is
     * considered even when it leads. Guards against
     * deciding from almost-no evidence.
     */
    val minSupportForDecided: Double = 0.35,

    /*
     * Decision margin ratio (top - second) / top above
     * which the leading category is considered to clearly
     * dominate. Below this, the item is AMBIGUOUS.
     */
    val minMarginRatioForDecided: Double = 0.25,

    /*
     * Minimum number of independent evidence source types
     * required for a category to be DECIDED confidently.
     */
    val minSourcesForDecided: Int = 1,

    /*
     * Minimum unique evidence identities for a confident
     * DECIDED state.
     */
    val minUniqueIdentitiesForDecided: Int = 2,

    // --------------------------------
    // COVERAGE THRESHOLDS
    // --------------------------------

    /*
     * Minimum coverage ratio (observed/item duration)
     * for a confident decision.
     */
    val minCoverageForDecided: Double = 0.1,

    /*
     * Minimum coverage ratio below which the item is
     * INSUFFICIENT_EVIDENCE regardless of candidate
     * scores.
     */
    val minCoverageForAnyDecision: Double = 0.05,

    // --------------------------------
    // CONTRADICTION THRESHOLDS
    // --------------------------------

    /*
     * Contradiction strength at or above which the item
     * is flagged as conflicting (raises uncertainty;
     * may force AMBIGUOUS).
     */
    val conflictThreshold: Double = 0.5,

    /*
     * Contradiction strength at or above which the item
     * becomes AMBIGUOUS if two candidates are close.
     */
    val ambiguityConflictThreshold: Double = 0.35,

    // --------------------------------
    // UNCERTAINTY THRESHOLDS
    // --------------------------------

    /*
     * Evidence quality weight below which the item is
     * considered low-quality (raises uncertainty).
     */
    val minQualityForLowUncertainty: Double = 0.6,

    /*
     * Coverage ratio below which uncertainty rises to
     * HIGH.
     */
    val coverageForHighUncertainty: Double = 0.2,

    /*
     * Source diversity below which uncertainty rises.
     */
    val sourcesForModerateUncertainty: Int = 1
) {
    companion object {

        const val DECISION_VERSION = "item-decision-v1"
        const val CONFIG_VERSION = "item-decision-config-v1"

        /*
         * The frozen default configuration for 8B-7.
         */
        val DEFAULT = ItemDecisionConfig()

        /*
         * A more conservative configuration that abstains
         * more readily. Useful for experiments prioritizing
         * precision over coverage.
         */
        val CONSERVATIVE = ItemDecisionConfig(
            minSupportForDecided = 0.5,
            minMarginRatioForDecided = 0.35,
            minSourcesForDecided = 2,
            minUniqueIdentitiesForDecided = 3,
            minCoverageForDecided = 0.2,
            minCoverageForAnyDecision = 0.1,
            conflictThreshold = 0.4,
            ambiguityConflictThreshold = 0.3,
            minQualityForLowUncertainty = 0.7,
            coverageForHighUncertainty = 0.3,
            sourcesForModerateUncertainty = 2
        )
    }

    init {
        require(minSupportForDecided in 0.0..1.0) {
            "minSupportForDecided must be in [0, 1]"
        }
        require(minMarginRatioForDecided in 0.0..1.0) {
            "minMarginRatioForDecided must be in [0, 1]"
        }
        require(minCoverageForDecided in 0.0..1.0) {
            "minCoverageForDecided must be in [0, 1]"
        }
        require(minCoverageForAnyDecision in 0.0..1.0) {
            "minCoverageForAnyDecision must be in [0, 1]"
        }
    }
}
