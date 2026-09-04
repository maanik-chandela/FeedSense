package com.example.feedsense.analysis.evaluation.robustness

/*
 * Milestone 8B-9.
 *
 * Subgroup result.
 *
 * A uniform representation for per-category, per-platform,
 * per-duration, per-evidence, and other subgroup analyses.
 * Each subgroup carries its own paired test result, accuracy
 * difference, and support count.
 */
data class SubgroupResult(
    val key: String,
    val support: Int,
    val baselineAccuracy: Double?,
    val eightBAccuracy: Double?,
    val accuracyDifference: Double?,
    val netDiscordantImprovement: Int,
    val test: PairedTestResult,
    val sufficient: Boolean
) {
    companion object {
        fun fromTest(test: PairedTestResult): SubgroupResult {
            val baseAcc = if (test.n > 0) {
                test.baselineCorrect.toDouble() / test.n
            } else null
            val eightAcc = if (test.n > 0) {
                test.eightBCorrect.toDouble() / test.n
            } else null
            val diff = if (baseAcc != null && eightAcc != null) {
                eightAcc - baseAcc
            } else null
            return SubgroupResult(
                key = test.subgroupLabel ?: "UNKNOWN",
                support = test.n,
                baselineAccuracy = baseAcc,
                eightBAccuracy = eightAcc,
                accuracyDifference = diff,
                netDiscordantImprovement =
                    test.eightBOnlyCorrect - test.baselineOnlyCorrect,
                test = test,
                sufficient = test.status == StatisticalStatus.SUFFICIENT
            )
        }
    }
}
