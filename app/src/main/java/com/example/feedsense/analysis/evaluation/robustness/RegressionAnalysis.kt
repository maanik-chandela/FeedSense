package com.example.feedsense.analysis.evaluation.robustness

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.analysis.evaluation.comparative.PairedPrediction

/*
 * Milestone 8B-9.
 *
 * Regression analysis.
 *
 * Analyses pairs where baseline was correct but 8B was wrong
 * (8B regression). Reports:
 *   - number and percentage
 *   - categories, platforms, duration buckets
 *   - root causes (via outcome)
 *
 * A good system must not hide regressions behind an overall
 * accuracy number.
 */
data class RegressionAnalysis(
    val totalRegressions: Int,
    val regressionPercentage: Double?,
    val regressions: List<RegressionItem>,
    val byCategory: Map<String, Int>,
    val byPlatform: Map<String, Int>,
    val byDuration: Map<String, Int>,
    val sufficient: Boolean
) {
    data class RegressionItem(
        val itemId: String,
        val category: String?,
        val platform: String?,
        val durationBucket: String,
        val outcome: String
    )

    companion object {
        fun compute(
            pairs: List<PairedPrediction>,
            minimumForEffect: Int
        ): RegressionAnalysis {
            val eligible = pairs.filter { it.eligibleForAccuracy }
            val regressions = eligible.filter {
                it.baselineCorrect && !it.eightBCorrect
            }
            val n = eligible.size
            val sufficient = n >= minimumForEffect
            val pct = if (n > 0) {
                regressions.size.toDouble() / n
            } else null

            val items = regressions.map { p ->
                val cat = CategoryCatalog.normalize(p.truth.category)
                RegressionItem(
                    itemId = p.item.id,
                    category = cat,
                    platform = p.truth.platform,
                    durationBucket = com.example.feedsense.analysis.evaluation.comparative
                        .DurationBuckets.of(p.truth.durationSeconds).label,
                    outcome = p.outcome.label
                )
            }

            val byCat = linkedMapOf<String, Int>()
            val byPlat = linkedMapOf<String, Int>()
            val byDur = linkedMapOf<String, Int>()
            for (item in items) {
                val cat = item.category ?: "UNKNOWN"
                byCat[cat] = (byCat[cat] ?: 0) + 1
                val plat = item.platform ?: "UNKNOWN"
                byPlat[plat] = (byPlat[plat] ?: 0) + 1
                byDur[item.durationBucket] =
                    (byDur[item.durationBucket] ?: 0) + 1
            }

            return RegressionAnalysis(
                totalRegressions = regressions.size,
                regressionPercentage = pct,
                regressions = items,
                byCategory = byCat.toSortedMap(),
                byPlatform = byPlat.toSortedMap(),
                byDuration = byDur.toSortedMap(),
                sufficient = sufficient
            )
        }
    }
}
