package com.example.feedsense.analysis.evaluation

// --------------------------------
// DATASET BALANCE REPORT (Milestone 8A-3)
// --------------------------------
//
// Describes the composition of the evaluated cohort so the
// reader can judge how representative each metric's
// denominator is, and can spot imbalances that would bias
// estimates. This is a DESCRIPTION (counts + proportions +
// an entropy-based measure of imbalance), never a metric that
// penalizes a class.

data class DatasetBalanceReport(
    val byCategory: List<Pair<String, Int>>,
    val byPlatform: List<Pair<String, Int>>,
    val byContentType: List<Pair<String, Int>>,
    val bySource: List<Pair<String, Int>>,
    val byModelVersion: List<Pair<String, Int>>,
    val byDurationBucket: List<Pair<String, Int>>,
    val totalItems: Int,
    val notes: List<String>
) {

    companion object {

        fun balance(values: List<String>): List<Pair<String, Int>> {
            return values.groupingBy { it }.eachCount()
                .toList()
                .sortedWith(
                    compareByDescending<Pair<String, Int>> { it.second }
                        .thenBy { it.first }
                )
        }

        fun normalizedEntropy(values: List<String>): Double? {
            if (values.isEmpty()) return null
            val counts = values.groupingBy { it }.eachCount().values
            val total = values.size
            val maxEntropy = kotlin.math.ln(counts.size.toDouble())
            if (maxEntropy == 0.0) return null
            val entropy = counts.sumOf { c ->
                val p = c / total.toDouble()
                if (p == 0.0) 0.0 else -p * kotlin.math.ln(p)
            }
            return entropy / maxEntropy
        }

        fun imbalanceRatio(values: List<String>): Double? {
            if (values.isEmpty()) return null
            val counts = values.groupingBy { it }.eachCount().values
            if (counts.size < 2) return null
            val max = counts.maxOrNull()!!.toDouble()
            val min = counts.minOrNull()!!.toDouble()
            if (min == 0.0) return null
            return max / min
        }
    }
}