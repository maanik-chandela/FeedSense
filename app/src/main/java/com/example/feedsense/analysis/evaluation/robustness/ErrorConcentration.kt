package com.example.feedsense.analysis.evaluation.robustness

import com.example.feedsense.analysis.evaluation.comparative.PairedPrediction

/*
 * Milestone 8B-9.
 *
 * Error concentration analysis.
 *
 * Determines whether the total improvement or regression is
 * concentrated in:
 *   - one category
 *   - one platform
 *   - one duration bucket
 *   - one evidence condition
 *
 * Produces a ranked contribution report. Does NOT equate
 * contribution with causality; uses wording such as
 * "associated with" rather than "caused by".
 */
data class ErrorConcentration(
    val improvements: ConcentrationReport,
    val regressions: ConcentrationReport
) {
    data class ConcentrationReport(
        val total: Int,
        val topCategory: RankedContribution?,
        val topPlatform: RankedContribution?,
        val topDuration: RankedContribution?,
        val topEvidenceCondition: RankedContribution?,
        val categoryDistribution: Map<String, Int>,
        val platformDistribution: Map<String, Int>,
        val durationDistribution: Map<String, Int>,
        val evidenceConditionDistribution: Map<String, Int>
    )

    data class RankedContribution(
        val key: String,
        val count: Int,
        val percentage: Double,
        val description: String
    )

    companion object {
        fun compute(
            pairs: List<PairedPrediction>,
            minimumForEffect: Int
        ): ErrorConcentration {
            val eligible = pairs.filter { it.eligibleForAccuracy }
            val sufficient = eligible.size >= minimumForEffect

            val imp = eligible.filter {
                it.eightBCorrect && !it.baselineCorrect
            }
            val reg = eligible.filter {
                it.baselineCorrect && !it.eightBCorrect
            }

            return ErrorConcentration(
                improvements = buildReport(imp, "improvement"),
                regressions = buildReport(reg, "regression")
            )
        }

        private fun buildReport(
            items: List<PairedPrediction>,
            label: String
        ): ConcentrationReport {
            val catDist = linkedMapOf<String, Int>()
            val platDist = linkedMapOf<String, Int>()
            val durDist = linkedMapOf<String, Int>()
            val evDist = linkedMapOf<String, Int>()

            for (p in items) {
                val cat = com.example.feedsense.analysis.CategoryCatalog
                    .normalize(p.truth.category) ?: "UNKNOWN"
                catDist[cat] = (catDist[cat] ?: 0) + 1

                val plat = p.truth.platform ?: "UNKNOWN"
                platDist[plat] = (platDist[plat] ?: 0) + 1

                val dur = com.example.feedsense.analysis.evaluation.comparative
                    .DurationBuckets.of(p.truth.durationSeconds).label
                durDist[dur] = (durDist[dur] ?: 0) + 1

                val ev = p.eightBDecision?.evidenceCoverage?.name ?: "UNKNOWN"
                evDist[ev] = (evDist[ev] ?: 0) + 1
            }

            fun topContribution(
                dist: Map<String, Int>,
                dimension: String
            ): RankedContribution? {
                if (dist.isEmpty()) return null
                val total = dist.values.sum()
                if (total == 0) return null
                val top = dist.maxByOrNull { it.value } ?: return null
                return RankedContribution(
                    key = top.key,
                    count = top.value,
                    percentage = top.value.toDouble() / total,
                    description = "${top.key} is associated with " +
                        "${top.value} of $total $label events " +
                        "(${dimension})"
                )
            }

            return ConcentrationReport(
                total = items.size,
                topCategory = topContribution(catDist, "category"),
                topPlatform = topContribution(platDist, "platform"),
                topDuration = topContribution(durDist, "duration"),
                topEvidenceCondition = topContribution(evDist, "evidence"),
                categoryDistribution = catDist.toSortedMap(),
                platformDistribution = platDist.toSortedMap(),
                durationDistribution = durDist.toSortedMap(),
                evidenceConditionDistribution = evDist.toSortedMap()
            )
        }
    }
}
