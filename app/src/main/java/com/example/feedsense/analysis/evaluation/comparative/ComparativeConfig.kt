package com.example.feedsense.analysis.evaluation.comparative

/*
 * Milestone 8B-8.
 *
 * Comparative evaluation configuration.
 *
 * Frozen, documented thresholds and version labels for the
 * baseline-vs-8B comparative evaluation layer.
 *
 * Design principles:
 *   - Every sample-size guard is explicit and surfaced, never
 *     silently relaxed to manufacture a p-value or metric.
 *   - The version fields guarantee reproducibility: a report
 *     records which dataset, baseline model, fusion version,
 *     decision version, evaluation method and diagnostic
 *     produced it.
 *   - No threshold here is tuned against real evaluation data
 *     to inflate results.
 *
 * Sample-size guards:
 *   - minimumBinary       : minimum discordant pairs (b+c) for a
 *                           McNemar statistic to be meaningful.
 *   - minimumForEffect    : minimum paired observations for an
 *                           effect size.
 *   - minimumConfidence   : minimum observations for a Wilson
 *                           confidence interval.
 *   - minimumClassSupport : minimum ground-truth support for a
 *                           per-class metric to be DEFINED.
 *
 * When a guard is not met the layer returns
 * ComparativeSampleGuard.INSUFFICIENT rather than a number.
 */
data class ComparativeConfig(
    val evaluationVersion: String = "comparative-eval-v1",
    val diagnosticVersion: String =
        "comparative-diagnostic-v1",

    /*
     * Minimum discordant pairs (baseline-only-correct +
     * eight-b-only-correct) to run McNemar's exact binomial
     * sign test. Fewer than this -> INSUFFICIENT.
     */
    val minimumMcNemarDiscordantPairs: Int = 5,

    /*
     * Minimum paired observations for an effect-size estimate.
     */
    val minimumForEffectSize: Int = 10,

    /*
     * Minimum observations for a proportion confidence interval.
     */
    val minimumForConfidenceInterval: Int = 1,

    /*
     * Minimum ground-truth support per class for per-class
     * precision/recall/F1 to be DEFINED.
     */
    val minimumClassSupport: Int = 1,

    /*
     * Minimum observations for a stratified subgroup metric to
     * be DEFINED (vs. INSUFFICIENT_SUPPORT).
     */
    val minimumStratumSupport: Int = 1,

    /*
     * Confidence level for Wilson intervals and the McNemar
     * critical region display.
     */
    val confidenceLevel: Double = 0.95,

    /*
     * Whether a Bonferroni correction is applied when reporting
     * subgroup-level hypothesis tests. When true, per-comparison
     * alpha = confidence-alpha / number-of-comparisons; applied
     * as a documented adjustment, never silently.
     */
    val multipleComparisonsBonferroni: Boolean = true
) {
    companion object {
        const val SCHEMA_VERSION = "1.0.0"
        val DEFAULT = ComparativeConfig()
    }

    init {
        require(minimumMcNemarDiscordantPairs >= 0)
        require(minimumForEffectSize >= 0)
        require(minimumForConfidenceInterval >= 0)
        require(minimumClassSupport >= 0)
        require(minimumStratumSupport >= 0)
        require(confidenceLevel > 0.0 && confidenceLevel < 1.0)
    }
}
