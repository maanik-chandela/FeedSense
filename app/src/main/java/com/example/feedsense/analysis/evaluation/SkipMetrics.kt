package com.example.feedsense.analysis.evaluation

// --------------------------------
// SKIP METRICS (Milestone 8A-3)
// --------------------------------
//
// The AI predicts `skipped` (Boolean). Ground truth is
// tri-state: true = skipped, false = watched/not skipped,
// null = UNKNOWN.
//
// Honesty rule: UNKNOWN truth is NEVER coerced to false (or
// true). Items with UNKNOWN truth are counted and reported
// but excluded from the numeric metrics, so the denominators
// reflect only items where a human actually decided.
//
// The result mirrors the metrics already stored per-item in
// EvaluationRecord.skippedAgreement, but aggregated here with
// full uncertainty.

data class SkipMetrics(
    // How many items' truth was usable (true/false) vs UNKNOWN.
    val decided: Int,
    val unknownTruth: Int,
    val total: Int,
    // Usable items reported as a binary classification.
    val binary: BinaryMetrics
) {

    companion object {

        fun compute(
            // (predictedSkipped, truthSkipped) pairs
            pairs: List<Pair<Boolean, Boolean?>>,
            minimumSample: Int = 1,
            confidenceLevel: Double = 0.95
        ): SkipMetrics {

            val usable =
                pairs.filter { it.second != null }
                    .map { (p, t) -> p to t!! }

            val unknownCount = pairs.size - usable.size

            val binary =
                BinaryMetrics.compute(
                    pairs = usable,
                    minimumSample = minimumSample,
                    confidenceLevel = confidenceLevel
                )

            return SkipMetrics(
                decided = usable.size,
                unknownTruth = unknownCount,
                total = pairs.size,
                binary = binary
            )
        }
    }
}