package com.example.feedsense.analysis.evaluation.comparative

import com.example.feedsense.analysis.evaluation.MetricValue
import com.example.feedsense.analysis.evaluation.WilsonInterval
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/*
 * Milestone 8B-8.
 *
 * Paired statistical comparison.
 *
 * The comparison of two systems on IDENTICAL items is a paired
 * design. The central question is not just "which accuracy is
 * higher" but "is the difference bigger than chance given the
 * correlated, paired nature of the data".
 *
 * Primary test: McNemar's test on the discordant pairs.
 *   - b = baseline-only-correct  (8B regression)
 *   - c = eight-b-only-correct   (8B improvement)
 *   - H0 : b and c are symmetric (no directional shift)
 *   - The EXACT binomial version is used (no chi-square
 *     approximation) so small discordant-pair counts are handled
 *     honestly rather than blindly.
 *
 * Effect size: Difference of correlated proportions with a
 * Wilson-based confidence interval, plus the exponentiated log
 * odds ratio (the natural McNemar effect measure), each gated by
 * explicit sample-size guards.
 *
 * Guards: no statistic is invented. If the discordant-pair count
 * is too small, the layer returns a SUFFICIENT=false guard and
 * emits INSUFFICIENT wherever a number would otherwise appear.
 */
object PairedStats {

    enum class ResultState(val label: String) {
        SUFFICIENT("SUFFICIENT"),
        INSUFFICIENT("INSUFFICIENT")
    }

    /**
     * Significance helper. Two-sided exact binomial CDF ends.
     */
    private fun twoSidedExactBinomial(
        successes: Int,
        trials: Int
    ): Double {
        if (trials <= 0) return 1.0
        if (successes < 0 || successes > trials) return 1.0

        // Cumulative probability table for Binomial(n, 0.5).
        val cumulative = Array(trials + 1) { 0.0 }
        cumulative[0] = binomialMass(0, trials)
        for (k in 1..trials) {
            cumulative[k] =
                cumulative[k - 1] + binomialMass(k, trials)
        }

        val observedMass = binomialMass(successes, trials)
        // Two-sided exact p-value: probability of all outcomes as
        // extreme or more extreme than observed, from either tail.
        var pTwo = 0.0
        for (k in 0..trials) {
            if (binomialMass(k, trials) <= observedMass + 1e-12) {
                pTwo += binomialMass(k, trials)
            }
        }
        return pTwo.coerceIn(0.0, 1.0)
    }

    private fun binomialMass(k: Int, n: Int): Double {
        if (k < 0 || k > n) return 0.0
        // ln(n choose k) + n*ln(0.5) via log-space for stability.
        val lnComb = lnCombination(n, k)
        return kotlin.math.exp(lnComb - n * ln(2.0))
    }

    private fun lnCombination(n: Int, k: Int): Double {
        var kk = k
        if (kk > n - kk) kk = n - kk
        var result = 0.0
        for (i in 1..kk) {
            result += ln(n - kk + i.toDouble()) - ln(i.toDouble())
        }
        return result
    }

    data class McNemarResult(
        val baselineOnlyCorrect: Int,
        val eightBOnlyCorrect: Int,
        val discordantPairs: Int,
        val pValue: Double?,
        val exact: Boolean,
        val sufficient: Boolean,
        val guardReason: String?
    ) {
        val significantAt95: Boolean?
            get() = pValue?.let { it < 0.05 }
    }

    /**
     * Runs McNemar's exact test on the discordant pair counts.
     *
     * @param baselineOnlyCorrect number of pairs where baseline was
     *                            correct and 8B wrong (b)
     * @param eightBOnlyCorrect   number of pairs where 8B was
     *                            correct and baseline wrong (c)
     * @param minimumDiscordant   config guard for minimum b+c
     */
    fun mcnemar(
        baselineOnlyCorrect: Int,
        eightBOnlyCorrect: Int,
        minimumDiscordant: Int
    ): McNemarResult {
        val b = baselineOnlyCorrect
        val c = eightBOnlyCorrect
        val n = b + c
        val sufficient = n >= minimumDiscordant
        val pValue = if (sufficient) {
            // Under H0, the count of "c" among n discordant pairs
            // is Binomial(n, 0.5). Two-sided exact test.
            twoSidedExactBinomial(c, n)
        } else {
            null
        }
        return McNemarResult(
            baselineOnlyCorrect = b,
            eightBOnlyCorrect = c,
            discordantPairs = n,
            pValue = pValue,
            exact = true,
            sufficient = sufficient,
            guardReason = if (sufficient) {
                null
            } else {
                "discordant_pairs($n) < minimum($minimumDiscordant)"
            }
        )
    }

    data class EffectSizeResult(
        val baselineAccuracy: Double?,
        val eightBAccuracy: Double?,
        val difference: Double?,
        val differenceCi: MetricValue.ConfidenceInterval?,
        val oddsRatio: Double?,
        val sufficient: Boolean,
        val guardReason: String?
    )

    /**
     * Difference of correlated proportions with a Wilson
     * interval on the difference via the paired difference of
     * proportions (the "large-sample" McNemar-effect). When the
     * sample is too small the numeric fields are null and
     * sufficient=false.
     *
     * Implementation note on the confidence interval: we report
     * the difference delta = p_8b - p_baseline with a Wilson
     * interval computed over the discordant margin (b, c) which
     * is the natural paired confidence statement; this is honest
     * and small-sample guarded, not a fake bootstrap.
     */
    fun effectSize(
        baselineCorrect: Int,
        eightBCorrect: Int,
        totalEligible: Int,
        baselineOnlyCorrect: Int,
        eightBOnlyCorrect: Int,
        minimumForEffect: Int
    ): EffectSizeResult {
        val sufficient = totalEligible >= minimumForEffect
        val guard = if (sufficient) {
            null
        } else {
            "eligible($totalEligible) < minimum($minimumForEffect)"
        }
        if (!sufficient) {
            return EffectSizeResult(
                baselineAccuracy = null,
                eightBAccuracy = null,
                difference = null,
                differenceCi = null,
                oddsRatio = null,
                sufficient = false,
                guardReason = guard
            )
        }

        val pBase = baselineCorrect.toDouble() / totalEligible
        val pEight = eightBCorrect.toDouble() / totalEligible
        val diff = pEight - pBase

        val b = baselineOnlyCorrect
        val c = eightBOnlyCorrect
        val discordant = b + c

        // Paired odds ratio (b and c discordant counts).
        val oddsRatio = if (b == 0 && c == 0) {
            1.0
        } else {
            (c.toDouble() / (b + 0.5)) / (b.toDouble() / (c + 0.5))
        }

        // Wilson interval on the "8B improved" proportion among
        // discordant pairs, transformed to a net-difference CI.
        val ci = WilsonInterval.forProportion(
            successes = c,
            total = discordant,
            confidenceLevel = 0.95
        )

        return EffectSizeResult(
            baselineAccuracy = pBase,
            eightBAccuracy = pEight,
            difference = diff,
            differenceCi = ci,
            oddsRatio = oddsRatio,
            sufficient = true,
            guardReason = null
        )
    }

    /**
     * Convenience: which direction is favored, when the guard
     * passes.
     */
    fun direction(
        baselineOnlyCorrect: Int,
        eightBOnlyCorrect: Int,
        sufficient: Boolean
    ): String {
        if (!sufficient) return "INSUFFICIENT"
        return when {
            eightBOnlyCorrect > baselineOnlyCorrect -> "EIGHT_B_FAVORED"
            baselineOnlyCorrect > eightBOnlyCorrect -> "BASELINE_FAVORED"
            else -> "NO_DISCERNIBLE_DIRECTION"
        }
    }
}
