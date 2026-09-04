package com.example.feedsense.analysis.evaluation.robustness

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.analysis.evaluation.comparative.PairedPrediction

/*
 * Milestone 8B-9.
 *
 * Improvement analysis.
 *
 * Analyses pairs where 8B was correct but baseline was wrong
 * (8B improvement). Reports:
 *   - number and percentage
 *   - categories, platforms, duration buckets
 *   - evidence conditions
 *
 * The final report should compare improvement population vs
 * regression population.
 */
data class ImprovementAnalysis(
    val totalImprovements: Int,
    val improvementPercentage: Double?,
    val improvements: List<ImprovementItem>,
    val byCategory: Map<String, Int>,
    val byPlatform: Map<String, Int>,
    val byDuration: Map<String, Int>,
    val sufficient: Boolean
) {
    data class ImprovementItem(
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
        ): ImprovementAnalysis {
            val eligible = pairs.filter { it.eligibleForAccuracy }
            val improvements = eligible.filter {
                it.eightBCorrect && !it.baselineCorrect
            }
            val n = eligible.size
            val sufficient = n >= minimumForEffect
            val pct = if (n > 0) {
                improvements.size.toDouble() / n
            } else null

            val items = improvements.map { p ->
                val cat = CategoryCatalog.normalize(p.truth.category)
                ImprovementItem(
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

            return ImprovementAnalysis(
                totalImprovements = improvements.size,
                improvementPercentage = pct,
                improvements = items,
                byCategory = byCat.toSortedMap(),
                byPlatform = byPlat.toSortedMap(),
                byDuration = byDur.toSortedMap(),
                sufficient = sufficient
            )
        }
    }
}
