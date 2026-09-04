package com.example.feedsense.analysis.evaluation.robustness

/*
 * Milestone 8B-9.
 *
 * Accuracy difference and directional improvement metrics.
 *
 * The primary effect-size representation for paired binary outcomes:
 *   - accuracyDifference = 8B accuracy - baseline accuracy
 *   - netDiscordantImprovement = eightBOnlyCorrect - baselineOnlyCorrect
 *
 * These are signed quantities: positive means 8B is better.
 */
data class AccuracyDifference(
    val accuracyDifference: Double?,
    val netDiscordantImprovement: Int,
    val baselineAccuracy: Double?,
    val eightBAccuracy: Double?,
    val sufficient: Boolean,
    val guardReason: String? = null
) {
    companion object {
        fun fromCounts(
            counts: PairedCounts,
            minimumForEffect: Int
        ): AccuracyDifference {
            val sufficient = counts.n >= minimumForEffect
            if (!sufficient) {
                return AccuracyDifference(
                    accuracyDifference = null,
                    netDiscordantImprovement =
                        counts.eightBOnlyCorrect - counts.baselineOnlyCorrect,
                    baselineAccuracy = null,
                    eightBAccuracy = null,
                    sufficient = false,
                    guardReason = "eligible(${counts.n}) < minimum($minimumForEffect)"
                )
            }
            val baseAcc = counts.baselineCorrect.toDouble() / counts.n
            val eightAcc = counts.eightBCorrect.toDouble() / counts.n
            return AccuracyDifference(
                accuracyDifference = eightAcc - baseAcc,
                netDiscordantImprovement =
                    counts.eightBOnlyCorrect - counts.baselineOnlyCorrect,
                baselineAccuracy = baseAcc,
                eightBAccuracy = eightAcc,
                sufficient = true
            )
        }
    }
}
