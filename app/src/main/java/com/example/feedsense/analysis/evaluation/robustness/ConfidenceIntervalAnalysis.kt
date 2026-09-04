package com.example.feedsense.analysis.evaluation.robustness

import com.example.feedsense.analysis.evaluation.MetricValue
import com.example.feedsense.analysis.evaluation.WilsonInterval

/*
 * Milestone 8B-9.
 *
 * Confidence intervals for comparative estimates.
 *
 * Provides Wilson score intervals for:
 *   - baseline accuracy
 *   - 8B accuracy
 *   - accuracy difference (via Wilson interval on the discordant
 *     proportion, following the paired-difference approach)
 *
 * Method documented: Wilson score interval (Acklam inverse-normal
 * approximation for the z-score), same as 8A-3. No normal
 * approximation for tiny samples; the Wilson method is specifically
 * designed to handle edge proportions (0%, 100%) without collapsing.
 *
 * Minimum-sample guards are applied; when insufficient data exists,
 * fields are null with an explicit guard reason.
 */
data class ConfidenceIntervalAnalysis(
    val baselineAccuracy: MetricValue?,
    val eightBAccuracy: MetricValue?,
    val accuracyDifference: MetricValue?,
    val sufficient: Boolean,
    val guardReason: String? = null
) {
    companion object {
        fun compute(
            baselineCorrect: Int,
            eightBCorrect: Int,
            totalEligible: Int,
            baselineOnlyCorrect: Int,
            eightBOnlyCorrect: Int,
            minimumForCI: Int
        ): ConfidenceIntervalAnalysis {
            if (totalEligible < minimumForCI) {
                return ConfidenceIntervalAnalysis(
                    baselineAccuracy = null,
                    eightBAccuracy = null,
                    accuracyDifference = null,
                    sufficient = false,
                    guardReason = "eligible($totalEligible) < minimum($minimumForCI)"
                )
            }

            val baseAccValue = baselineCorrect.toDouble() / totalEligible
            val eightAccValue = eightBCorrect.toDouble() / totalEligible
            val diff = eightAccValue - baseAccValue

            val baseCI = WilsonInterval.forProportion(
                baselineCorrect, totalEligible, 0.95
            )
            val eightCI = WilsonInterval.forProportion(
                eightBCorrect, totalEligible, 0.95
            )

            val baselineMV = MetricValue(
                state = MetricValue.State.DEFINED,
                value = baseAccValue,
                numerator = baselineCorrect,
                denominator = totalEligible,
                confidenceInterval = baseCI
            )
            val eightBMV = MetricValue(
                state = MetricValue.State.DEFINED,
                value = eightAccValue,
                numerator = eightBCorrect,
                denominator = totalEligible,
                confidenceInterval = eightCI
            )

            val discordant = baselineOnlyCorrect + eightBOnlyCorrect
            val diffCI = if (discordant > 0) {
                val improvedProp = eightBOnlyCorrect.toDouble() /
                    discordant
                WilsonInterval.forProportion(
                    eightBOnlyCorrect, discordant, 0.95
                )
            } else null

            val diffMV = MetricValue(
                state = MetricValue.State.DEFINED,
                value = diff,
                numerator = eightBCorrect - baselineCorrect,
                denominator = totalEligible,
                confidenceInterval = diffCI,
                note = "paired_difference_wilson"
            )

            return ConfidenceIntervalAnalysis(
                baselineAccuracy = baselineMV,
                eightBAccuracy = eightBMV,
                accuracyDifference = diffMV,
                sufficient = true
            )
        }
    }
}
