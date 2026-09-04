package com.example.feedsense.analysis.evaluation.comparative

import com.example.feedsense.analysis.CategoryCatalog

/*
 * Milestone 8B-8.
 *
 * Transition matrices.
 *
 * Two complementary views:
 *
 * 1) CLASS CONFUSION (ground-truth x predicted primary), computed
 *    separately for baseline and 8B over the same eligible pairs.
 *    Palpable, comparable "who confused what" across systems.
 *
 * 2) COVERAGE-OUTCOME TRANSITION (baseline judgment x 8B
 *    judgment). Each cell is the count of eligible pairs at that
 *    (baseline, 8B) junction, where each system is
 *    CORRECT / WRONG / UNKNOWN. The leading diagonal is "no
 *    change"; off-diagonal "wrong -> right" cells are 8B
 *    improvements and "right -> wrong" cells are regressions.
 *
 * Both are pure, deterministic, and only include eligible pairs.
 */
object TransitionMatrices {

    /**
     * Class confusion matrix (truth category x predicted
     * category) for the given system. Only pairs where both the
     * truth and the prediction normalize to a known category are
     * included; otherwise they appear in neither (they are
     * counted in abstention, not in the confusion matrix).
     */
    fun classConfusion(
        pairs: List<PairedPrediction>,
        isBaseline: Boolean
    ): ClassConfusion {
        val eligible = pairs.filter { it.eligibleForAccuracy }
        val categories = eligible
            .map {
                CategoryCatalog.normalize(it.truth.category)
            }
            .filterNotNull()
            .toSortedSet()

        val predicted = eligible
            .map { p ->
                val c = if (isBaseline) {
                    p.baselineRecord.category
                } else {
                    p.eightBRecord.category
                }
                CategoryCatalog.normalize(c)
            }
            .filterNotNull()
        categories.addAll(predicted)

        val sorted = categories.sorted()
        val matrix = linkedMapOf<String, MutableMap<String, Int>>()
        val rowTotals = linkedMapOf<String, Int>()
        val colTotals = linkedMapOf<String, Int>()
        for (t in sorted) {
            matrix[t] = linkedMapOf<String, Int>()
            rowTotals[t] = 0
            colTotals[t] = 0
        }
        sorted.forEach { t ->
            sorted.forEach { p -> matrix[t]!![p] = 0 }
        }

        for (p in eligible) {
            val truth = CategoryCatalog.normalize(p.truth.category)
            val pred = if (isBaseline) {
                p.baselineRecord.category
            } else {
                p.eightBRecord.category
            }
            val predNorm = CategoryCatalog.normalize(pred)
            if (truth != null && predNorm != null) {
                matrix[truth]?.let { row ->
                    row[predNorm] = row[predNorm]!! + 1
                    rowTotals[truth] =
                        (rowTotals[truth] ?: 0) + 1
                    colTotals[predNorm] =
                        (colTotals[predNorm] ?: 0) + 1
                }
            }
        }

        return ClassConfusion(
            categories = sorted,
            counts = matrix.mapValues { it.value.toMap() },
            rowTotals = rowTotals.mapValues { it.value },
            colTotals = colTotals.mapValues { it.value }
        )
    }

    data class ClassConfusion(
        val categories: List<String>,
        val counts: Map<String, Map<String, Int>>,
        val rowTotals: Map<String, Int>,
        val colTotals: Map<String, Int>
    ) {
        fun count(truth: String, predicted: String): Int =
            counts[truth]?.get(predicted) ?: 0

        fun total(): Int = rowTotals.values.sum()
    }

    // --------------------------------
    // COVERAGE-OUTCOME TRANSITION
    // --------------------------------

    enum class Judgment(val label: String) {
        CORRECT("CORRECT"),
        WRONG("WRONG"),
        UNKNOWN("UNKNOWN");

        companion object {
            fun of(correct: Boolean, unknown: Boolean): Judgment =
                when {
                    unknown -> UNKNOWN
                    correct -> CORRECT
                    else -> WRONG
                }
        }
    }

    data class CoverageTransition(
        val eligible: Int,
        val matrix: Map<String, Map<String, Int>>
    ) {
        fun count(baseline: Judgment, eightB: Judgment): Int =
            matrix[baseline.label]?.get(eightB.label) ?: 0
    }

    /**
     * 3x3 transition matrix using the CoverageOutcome.Judgment
     * classification for each system on eligible pairs only.
     */
    fun coverageTransition(
        pairs: List<PairedPrediction>
    ): CoverageTransition {
        val eligible = pairs.filter { it.eligibleForAccuracy }
        val labels = listOf("CORRECT", "WRONG", "UNKNOWN")
        val matrix = linkedMapOf<String, MutableMap<String, Int>>()
        for (b in labels) {
            matrix[b] = linkedMapOf<String, Int>()
            for (e in labels) matrix[b]!![e] = 0
        }
        for (p in eligible) {
            val bJ = Judgment.of(p.baselineCorrect, p.baselineUnknown)
            val eJ = Judgment.of(p.eightBCorrect, p.eightBUnknown)
            matrix[bJ.label]!![eJ.label] =
                matrix[bJ.label]!![eJ.label]!! + 1
        }
        return CoverageTransition(
            eligible = eligible.size,
            matrix = matrix.mapValues { it.value.toMap() }
        )
    }
}
