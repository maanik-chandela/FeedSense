package com.example.feedsense.analysis.evaluation.robustness

import com.example.feedsense.analysis.evaluation.comparative.ComparisonOutcome
import com.example.feedsense.analysis.evidence.decision.DecisionState
import com.example.feedsense.analysis.evidence.temporal.ConflictLevel

/*
 * Milestone 8B-9.
 *
 * High-confidence error analysis.
 *
 * Measures predictions where the system was confident but wrong.
 * A system that says "I don't know" when uncertain may be
 * preferable to one that confidently gives the wrong category.
 *
 * We do NOT automatically treat abstention as success; we report
 * both accuracy and high-confidence error rate.
 */
data class HighConfidenceError(
    val baselineHighConfidenceWrong: Int,
    val eightBHighConfidenceWrong: Int,
    val baselineHighConfidenceTotal: Int,
    val eightBHighConfidenceTotal: Int,
    val baselineHighConfidenceErrorRate: Double?,
    val eightBHighConfidenceErrorRate: Double?,
    val highConfidenceThreshold: Double,
    val sufficient: Boolean
) {
    companion object {
        fun compute(
            pairs: List<com.example.feedsense.analysis.evaluation.comparative.PairedPrediction>,
            config: RobustnessConfig
        ): HighConfidenceError {
            val eligible = pairs.filter { it.eligibleForAccuracy }
            val threshold = config.highConfidenceThreshold

            var bHCWrong = 0
            var bHCTotal = 0
            var eHCWrong = 0
            var eHCTotal = 0

            for (p in eligible) {
                val bConf = p.baselineRecord.confidence ?: 0.0
                if (bConf >= threshold) {
                    bHCTotal++
                    if (!p.baselineCorrect) bHCWrong++
                }
                val eConf = p.eightBRecord.confidence ?: 0.0
                if (eConf >= threshold) {
                    eHCTotal++
                    if (!p.eightBCorrect) eHCWrong++
                }
            }

            val bRate = if (bHCTotal > 0) {
                bHCWrong.toDouble() / bHCTotal
            } else null
            val eRate = if (eHCTotal > 0) {
                eHCWrong.toDouble() / eHCTotal
            } else null

            return HighConfidenceError(
                baselineHighConfidenceWrong = bHCWrong,
                eightBHighConfidenceWrong = eHCWrong,
                baselineHighConfidenceTotal = bHCTotal,
                eightBHighConfidenceTotal = eHCTotal,
                baselineHighConfidenceErrorRate = bRate,
                eightBHighConfidenceErrorRate = eRate,
                highConfidenceThreshold = threshold,
                sufficient = eligible.isNotEmpty()
            )
        }
    }
}
