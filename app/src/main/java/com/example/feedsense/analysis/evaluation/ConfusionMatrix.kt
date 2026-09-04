package com.example.feedsense.analysis.evaluation

import com.example.feedsense.analysis.CategoryCatalog

// --------------------------------
// ERROR CONFUSION MATRIX (Milestone 8A-5)
// --------------------------------
//
// A confusion matrix built from the error-analysis perspective.
// Rows = predicted label, columns = truth label, cell = count of
// items whose predicted primary label maps to that row while the
// truth primary maps to that column. This is the classic visual
// for "which class does the model confuse with which", derived
// FROM the same stored prediction/truth pairs the 8A-3 engine
// used, so the counts are consistent with 8A-3 by construction.
//
// NOTE: named ErrorConfusionMatrix to avoid colliding with the
// 8A-3 ConfusionMatrix data type in CategoryMetrics.

object ErrorConfusionMatrix {

    data class Matrix(
        val labels: List<String>,
        val cells: Map<Pair<String, String>, Int>
    ) {
        fun count(predicted: String, truth: String): Int {
            return cells[Pair(predicted, truth)] ?: 0
        }

        fun toTable(): List<List<Int>> {
            return labels.map { row ->
                labels.map { col -> count(row, col) }
            }
        }
    }

    /**
     * Builds a confusion matrix over the given units' primary
     * labels. Only units whose truth category is normalized and
     * whose verdict is not UNKNOWN/UNCOMPARABLE contribute a
     * cell, so an unrecognizable or uncomparable item never
     * pollutes the matrix.
     *
     * @param classLabels the row/column vocabulary to display
     */
    fun build(
        units: List<EvaluationUnit>,
        classLabels: List<String> = CategoryCatalog.keys
    ): Matrix {
        val cells = mutableMapOf<Pair<String, String>, Int>()

        units.forEach { unit ->
            val truthNorm = CategoryCatalog.normalize(unit.truth.category)
            if (truthNorm == null) return@forEach
            val verdict = unit.result.verdict
            if (verdict == "UNKNOWN" || verdict == "UNCOMPARABLE") {
                return@forEach
            }
            val predictedNorm =
                CategoryCatalog.normalize(unit.prediction.category)
                    ?: "unknown"
            val key = Pair(predictedNorm, truthNorm)
            cells[key] = (cells[key] ?: 0) + 1
        }

        val labels = classLabels.ifEmpty {
            (cells.keys.map { it.first } +
                cells.keys.map { it.second }).distinct().sorted()
        }

        return Matrix(labels = labels, cells = cells)
    }
}
