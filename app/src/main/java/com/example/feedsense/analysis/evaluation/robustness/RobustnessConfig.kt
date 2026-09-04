package com.example.feedsense.analysis.evaluation.robustness

/*
 * Milestone 8B-9.
 *
 * Statistical robustness / sensitivity configuration.
 *
 * Every stochastic procedure must be deterministically seeded; the
 * alpha threshold and all statistical versions are frozen here and
 * recorded in every report so nothing can be silently changed after
 * seeing results.
 *
 * Design rules:
 *   - alpha is part of the configuration, never a hard-coded 0.05
 *     hidden in a test.
 *   - No procedure here tunes thresholds against evaluation data.
 *   - Sample-size guards are explicit and surfaced (INSUFFICIENT_DATA
 *     rather than a manufactured p-value).
 *   - All random sampling is seeded (java.util.Random with
 *     FixedRandomSeed) for reproducibility.
 */
data class RobustnessConfig(
    // --------------------------------
    // VERSIONING
    // --------------------------------
    val statisticalAnalysisVersion: String =
        "statistical-analysis-v1",
    val evaluationVersion: String = "comparative-eval-v1",

    // --------------------------------
    // SIGNIFICANCE
    // --------------------------------
    val alpha: Double = 0.05,

    // --------------------------------
    // RANDOM SEED CONTROL
    // --------------------------------
    val analysisSeed: Long = 20240818L,

    // --------------------------------
    // BOOTSTRAP
    // --------------------------------
    val bootstrapIterations: Int = 2000,
    val bootstrapMinimumEligible: Int = 10,
    val bootstrapSeed: Long = 19700101L,

    // --------------------------------
    // SAMPLE-SIZE GUARDS
    // --------------------------------
    /*
     * Minimum discordant pairs (b+c) for a McNemar p-value. Reuse
     * the 8B-8 guard semantics; the primary test refuses to emit a
     * p-value below this.
     */
    val minimumMcNemarDiscordantPairs: Int = 5,

    /*
     * Minimum eligible paired items for an effect-size estimate.
     */
    val minimumForEffectSize: Int = 10,

    /*
     * Minimum eligible paired items for a confidence interval.
     */
    val minimumForConfidenceInterval: Int = 1,

    /*
     * Minimum legitimate observation count in any subgroup before
     * its metrics are DEFINED (below -> INSUFFICIENT).
     */
    val minimumSubgroupSupport: Int = 1,

    /*
     * Minimum support for a category to be included in
     * leave-one-category-out / per-category analysis.
     */
    val minimumCategorySupport: Int = 1,

    /*
     * Minimum support for a class-balanced resample cell.
     */
    val minimumBalancedSupport: Int = 1,

    // --------------------------------
    // CONFIDENCE BANDS
    // --------------------------------
    /*
     * Confidence bands for confidence-sensitivity analysis.
     * Values are the UPPER bounds (exclusive) of each band.
     */
    val confidenceBands: List<Double> = listOf(
        0.25, 0.50, 0.75, 1.01
    ),

    /*
     * Confidence above which a prediction is labelled
     * "high-confidence" for the high-confidence-error analysis.
     */
    val highConfidenceThreshold: Double = 0.75,

    // --------------------------------
    // MULTIPLE COMPARISON
    // --------------------------------
    /*
     * When true, subgroup p-values are Bonferroni-adjusted over the
     * family of tests in the same subgroup category; controlled
     * wording and metadata always accompany the adjustment.
     */
    val multipleComparisonBonferroni: Boolean = true
) {
    companion object {
        const val SCHEMA_VERSION = "1.0.0"
        val DEFAULT = RobustnessConfig()
    }

    init {
        require(alpha > 0.0 && alpha < 1.0)
        require(bootstrapIterations > 0)
        require(bootstrapMinimumEligible >= 0)
        require(minimumMcNemarDiscordantPairs >= 0)
        require(minimumForEffectSize >= 0)
        require(minimumForConfidenceInterval >= 0)
        require(minimumSubgroupSupport >= 0)
        require(minimumCategorySupport >= 0)
        require(minimumBalancedSupport >= 0)
        require(highConfidenceThreshold in 0.0..1.01)
    }
}
