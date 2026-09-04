package com.example.feedsense.analysis.evaluation.robustness

import com.example.feedsense.analysis.evaluation.comparative.PairedStats

/*
 * Milestone 8B-9.
 *
 * Paired test result (the §7 statistical output).
 *
 * Exposes the full contingency plus the test name, statistic,
 * p-value, threshold and significance — or, when the guard is not
 * satisfied, pValue=null / significant=null / status=INSUFFICIENT_DATA
 * (never a fabricated zero or a plausible-looking number).
 *
 * Implementation: McNemar's test computed EXACTLY (two-sided
 * binomial) by reusing the 8B-8 PairedStats exact formulation,
 * which is appropriate for small discordant-pair counts and does
 * not blindly apply an asymptotic chi-square approximation.
 */
data class PairedTestResult(
    val role: AnalysisRole,
    val subgroupLabel: String? = null,
    val n: Int,
    val baselineCorrect: Int,
    val baselineWrong: Int,
    val eightBCorrect: Int,
    val eightBWrong: Int,
    val baselineOnlyCorrect: Int,
    val eightBOnlyCorrect: Int,
    val testName: String,
    val testStatistic: Double?,
    val pValue: Double?,
    val significanceThreshold: Double,
    val significant: Boolean?,
    val status: StatisticalStatus,
    val guardReason: String? = null
) {
    val discordantPairs: Int
        get() = baselineOnlyCorrect + eightBOnlyCorrect

    companion object {
        const val TEST_NAME = "McNemar (exact two-sided binomial)"

        /**
         * Runs McNemar from precomputed PairedCounts.
         */
        fun fromCounts(
            counts: PairedCounts,
            role: AnalysisRole,
            subgroupLabel: String?,
            alpha: Double,
            minimumDiscordant: Int,
            minimumForEffect: Int
        ): PairedTestResult {
            // Guard: below the effect-size minimum we cannot claim a
            // meaningful estimate; below the discordant minimum we
            // cannot even compute a reliable p-value.
            val dataEnough = counts.n >= minimumForEffect
                && counts.discordantPairs >= minimumDiscordant

            val mcnemar = PairedStats.mcnemar(
                baselineOnlyCorrect = counts.baselineOnlyCorrect,
                eightBOnlyCorrect = counts.eightBOnlyCorrect,
                minimumDiscordant = minimumDiscordant
            )

            return if (!dataEnough || !mcnemar.sufficient) {
                PairedTestResult(
                    role = role,
                    subgroupLabel = subgroupLabel,
                    n = counts.n,
                    baselineCorrect = counts.baselineCorrect,
                    baselineWrong = counts.baselineWrong,
                    eightBCorrect = counts.eightBCorrect,
                    eightBWrong = counts.eightBWrong,
                    baselineOnlyCorrect = counts.baselineOnlyCorrect,
                    eightBOnlyCorrect = counts.eightBOnlyCorrect,
                    testName = TEST_NAME,
                    testStatistic = null,
                    pValue = null,
                    significanceThreshold = alpha,
                    significant = null,
                    status = StatisticalStatus.INSUFFICIENT_DATA,
                    guardReason = mcnemar.guardReason
                        ?: "eligible_${counts.n}<${minimumForEffect}"
                )
            } else {
                val p = mcnemar.pValue!!
                PairedTestResult(
                    role = role,
                    subgroupLabel = subgroupLabel,
                    n = counts.n,
                    baselineCorrect = counts.baselineCorrect,
                    baselineWrong = counts.baselineWrong,
                    eightBCorrect = counts.eightBCorrect,
                    eightBWrong = counts.eightBWrong,
                    baselineOnlyCorrect = counts.baselineOnlyCorrect,
                    eightBOnlyCorrect = counts.eightBOnlyCorrect,
                    testName = TEST_NAME,
                    testStatistic = mcnemar.discordantPairs.toDouble(),
                    pValue = p,
                    significanceThreshold = alpha,
                    significant = p < alpha,
                    status = StatisticalStatus.SUFFICIENT
                )
            }
        }
    }
}
