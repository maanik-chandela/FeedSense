package com.example.feedsense.analysis.evidence.decision

/*
 * Milestone 8B-7.
 *
 * Category support breakdown.
 *
 * For every candidate category, this captures the
 * interpretable components that drive its decision
 * support:
 *
 *   - totalSupport      : the aggregate evidence score
 *   - sourceCount       : number of distinct evidence types
 *   - temporalCoverage  : fraction of item duration the
 *                         category was observed
 *   - persistence       : repeated-evidence persistence
 *                         (dampened, NOT inflated by
 *                         duplicate observations)
 *   - contradiction     : strength/count of contradicting
 *                         evidence
 *
 * This makes the final prediction explainable, e.g.:
 *
 *   SPORTS
 *     + IPL OCR
 *     + cricket visual evidence
 *     + ranking layout
 *     + repeated across item
 *     - advertisement overlay
 *
 * instead of an opaque "SPORTS: 0.87".
 *
 * Important:
 *   - sourceCount counts INDEPENDENT evidence types, not
 *     raw observations (per 8B-6 independence semantics).
 *   - persistence is dampened: 10 identical OCR frames
 *     do not equal 10 independent sources.
 */
data class CategorySupport(
    val category: String,

    /*
     * Aggregate evidence score. NOT a probability unless
     * explicit calibration has been performed.
     */
    val totalSupport: Double,

    /*
     * Decision margin vs the next-best candidate.
     * Interpretable diagnostic, not a calibrated certainty.
     */
    val decisionMargin: Double,

    /*
     * Number of distinct evidence types supporting this
     * category.
     */
    val sourceCount: Int,

    /*
     * Raw observation count for this category.
     */
    val observationCount: Int,

    /*
     * Fraction of the item duration over which this
     * category was observed (0..1).
     */
    val temporalCoverage: Double,

    /*
     * Dampened persistence level ("NONE"/"BRIEF"/
     * "MODERATE"/"SUSTAINED"/"DOMINANT").
     */
    val persistence: String,

    /*
     * Number of contradicting evidence observations.
     */
    val contradictionCount: Int,

    /*
     * Strength of the contradiction (0..1), where 0 = no
     * contradiction and increasing toward 1 = heavy
     * conflict.
     */
    val contradictionStrength: Double,

    /*
     * Human-readable support breakdown (supporting
     * evidence summaries).
     */
    val supportingSignals: List<String>,

    /*
     * Human-readable contradiction breakdown.
     */
    val contradictingSignals: List<String>
) {

    /*
     * Whether the category has enough independent source
     * diversity to be considered.
     */
    val hasSourceDiversity: Boolean
        get() = sourceCount >= MIN_SOURCES_FOR_DIVERSITY

    /*
     * Whether contradicting evidence is negligible.
     */
    val hasNegligibleContradiction: Boolean
        get() = contradictionStrength <
                CONTRADICTION_NEGLIGIBLE_THRESHOLD

    companion object {

        /*
         * Minimum independent source types for a category
         * to be considered diverse.
         */
        const val MIN_SOURCES_FOR_DIVERSITY = 3

        /*
         * Contradiction strength below which the
         * contradiction is considered negligible.
         */
        const val CONTRADICTION_NEGLIGIBLE_THRESHOLD = 0.15

        /*
         * Builds an empty support (no evidence) for a
         * category.
         */
        fun empty(category: String): CategorySupport {
            return CategorySupport(
                category = category,
                totalSupport = 0.0,
                decisionMargin = 0.0,
                sourceCount = 0,
                observationCount = 0,
                temporalCoverage = 0.0,
                persistence = "NONE",
                contradictionCount = 0,
                contradictionStrength = 0.0,
                supportingSignals = emptyList(),
                contradictingSignals = emptyList()
            )
        }
    }
}
