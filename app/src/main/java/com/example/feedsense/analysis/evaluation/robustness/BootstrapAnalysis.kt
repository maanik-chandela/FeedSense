package com.example.feedsense.analysis.evaluation.robustness

import com.example.feedsense.analysis.evaluation.comparative.PairedPrediction
import java.util.Random

/*
 * Milestone 8B-9.
 *
 * Bootstrap resampling-based robustness analysis.
 *
 * The fundamental unit of resampling is the complete PairedPrediction
 * (EvaluationItem + baseline + 8B + GT), preserving the paired
 * structure. We NEVER independently resample baseline and 8B.
 *
 * The bootstrap estimates the distribution of:
 *   8B accuracy - baseline accuracy
 *
 * Deterministic seeding ensures reproducibility.
 *
 * Guards: empty/tiny datasets return INSUFFICIENT_DATA_FOR_BOOTSTRAP
 * rather than a fake confidence interval.
 */
data class BootstrapAnalysis(
    val bootstrapIterations: Int,
    val observedDifference: Double?,
    val lowerBound: Double?,
    val upperBound: Double?,
    val seed: Long,
    val statisticalVersion: String,
    val sufficient: Boolean,
    val guardReason: String? = null
) {
    companion object {
        fun compute(
            pairs: List<PairedPrediction>,
            config: RobustnessConfig
        ): BootstrapAnalysis {
            val eligible = pairs.filter { it.eligibleForAccuracy }

            if (eligible.size < config.bootstrapMinimumEligible) {
                return BootstrapAnalysis(
                    bootstrapIterations = 0,
                    observedDifference = null,
                    lowerBound = null,
                    upperBound = null,
                    seed = config.bootstrapSeed,
                    statisticalVersion = config.statisticalAnalysisVersion,
                    sufficient = false,
                    guardReason = "eligible(${eligible.size}) < " +
                        "minimum(${config.bootstrapMinimumEligible})"
                )
            }

            val n = eligible.size
            val rng = Random(config.bootstrapSeed)

            val baseCorrect = eligible.map { it.baselineCorrect }
            val eightCorrect = eligible.map { it.eightBCorrect }

            val observedBase = baseCorrect.count { it }.toDouble() / n
            val observedEight = eightCorrect.count { it }.toDouble() / n
            val observedDiff = observedEight - observedBase

            val diffs = mutableListOf<Double>()
            for (iter in 0 until config.bootstrapIterations) {
                var bCount = 0
                var eCount = 0
                for (j in 0 until n) {
                    val idx = rng.nextInt(n)
                    if (baseCorrect[idx]) bCount++
                    if (eightCorrect[idx]) eCount++
                }
                val bAcc = bCount.toDouble() / n
                val eAcc = eCount.toDouble() / n
                diffs.add(eAcc - bAcc)
            }

            val sorted = diffs.sorted()
            val lowerIdx = (0.025 * config.bootstrapIterations)
                .toInt().coerceIn(0, sorted.size - 1)
            val upperIdx = (0.975 * config.bootstrapIterations)
                .toInt().coerceIn(0, sorted.size - 1)

            return BootstrapAnalysis(
                bootstrapIterations = config.bootstrapIterations,
                observedDifference = observedDiff,
                lowerBound = sorted[lowerIdx],
                upperBound = sorted[upperIdx],
                seed = config.bootstrapSeed,
                statisticalVersion = config.statisticalAnalysisVersion,
                sufficient = true
            )
        }
    }
}
