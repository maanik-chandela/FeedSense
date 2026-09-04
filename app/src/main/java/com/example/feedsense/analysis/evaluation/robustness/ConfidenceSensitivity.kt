package com.example.feedsense.analysis.evaluation.robustness

import com.example.feedsense.analysis.evaluation.comparative.PairedPrediction
import com.example.feedsense.analysis.evaluation.comparative.PairedStats

/*
 * Milestone 8B-9.
 *
 * Confidence sensitivity analysis.
 *
 * Stratifies outcomes by the 8B prediction's confidence bands.
 * Uses the existing confidence semantics from the evidence-aware
 * decision system. Reveals whether 8B's improvement is concentrated
 * in high-confidence or low-confidence predictions.
 *
 * The confidence bands are defined in RobustnessConfig.
 */
data class ConfidenceSensitivity(
    val bands: List<ConfidenceBand>,
    val sufficient: Boolean
) {
    data class ConfidenceBand(
        val lowerBound: Double,
        val upperBound: Double,
        val support: Int,
        val baselineCorrect: Int,
        val eightBCorrect: Int,
        val baselineAccuracy: Double?,
        val eightBAccuracy: Double?,
        val accuracyDifference: Double?,
        val mcnemarSufficient: Boolean,
        val mcnemarP: Double?
    )

    companion object {
        fun compute(
            pairs: List<PairedPrediction>,
            config: RobustnessConfig
        ): ConfidenceSensitivity {
            val eligible = pairs.filter { it.eligibleForAccuracy }
            if (eligible.isEmpty()) {
                return ConfidenceSensitivity(
                    bands = emptyList(), sufficient = false
                )
            }

            val bands = mutableListOf<ConfidenceBand>()
            var prev = 0.0
            for (upper in config.confidenceBands) {
                val inBand = eligible.filter { p ->
                    val conf = p.eightBRecord.confidence ?: 0.0
                    conf >= prev && conf < upper
                }
                val n = inBand.size
                if (n < config.minimumSubgroupSupport) {
                    bands.add(
                        ConfidenceBand(
                            lowerBound = prev,
                            upperBound = upper,
                            support = n,
                            baselineCorrect = 0,
                            eightBCorrect = 0,
                            baselineAccuracy = null,
                            eightBAccuracy = null,
                            accuracyDifference = null,
                            mcnemarSufficient = false,
                            mcnemarP = null
                        )
                    )
                    prev = upper
                    continue
                }

                val bCorr = inBand.count { it.baselineCorrect }
                val eCorr = inBand.count { it.eightBCorrect }
                val bAcc = bCorr.toDouble() / n
                val eAcc = eCorr.toDouble() / n
                val diff = eAcc - bAcc

                val bOnly = inBand.count {
                    it.baselineCorrect && !it.eightBCorrect
                }
                val eOnly = inBand.count {
                    !it.baselineCorrect && it.eightBCorrect
                }
                val mc = PairedStats.mcnemar(
                    bOnly, eOnly, config.minimumMcNemarDiscordantPairs
                )

                bands.add(
                    ConfidenceBand(
                        lowerBound = prev,
                        upperBound = upper,
                        support = n,
                        baselineCorrect = bCorr,
                        eightBCorrect = eCorr,
                        baselineAccuracy = bAcc,
                        eightBAccuracy = eAcc,
                        accuracyDifference = diff,
                        mcnemarSufficient = mc.sufficient,
                        mcnemarP = mc.pValue
                    )
                )
                prev = upper
            }
            return ConfidenceSensitivity(
                bands = bands, sufficient = true
            )
        }
    }
}
