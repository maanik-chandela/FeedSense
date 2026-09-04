package com.example.feedsense.analysis.evaluation

// --------------------------------
// DURATION (REGRESSION) METRICS (Milestone 8A-3)
// --------------------------------
//
// Compares the AI-estimated duration (seconds) against the
// human ground-truth duration. Reported at whole-second
// precision throughout.
//
//   - MAE               mean absolute error
//   - medianAbsError    median absolute error (robust to outliers)
//   - RMSE              root mean squared error (penalizes large errors)
//   - bias              mean signed error (negative = underestimates,
//                       positive = overestimates)
//   - withinTolerances  fraction of items within each tolerance
//                       (e.g. <=1s, <=3s, <=5s, <=10s)
//   - errorDistribution sorted percentiles of |error|
//
// Only items whose ground truth provided a durationSeconds are
// included; UNKNOWN truth durations are excluded and reported
// separately by the engine.

data class DurationMetrics(
    val sampleCount: Int,
    val meanAbsoluteError: MetricValue,
    val medianAbsError: MetricValue,
    val rmse: MetricValue,
    val bias: MetricValue,
    val withinTolerances: Map<Int, MetricValue>,
    val absoluteErrorPercentiles: List<Pair<Int, Double>>,
    // Signed errors per item, for downstream drill-down.
    val signedErrorsSeconds: List<Int>
) {

    companion object {

        const val DEFAULT_MINIMUM_SAMPLE = 1

        fun compute(
            // (predictedSeconds, truthSeconds) pairs
            pairs: List<Pair<Int, Int>>,
            tolerances: List<Int> = listOf(1, 3, 5, 10),
            minimumSample: Int = DEFAULT_MINIMUM_SAMPLE,
            percentiles: List<Int> = listOf(50, 75, 90, 95, 99)
        ): DurationMetrics {

            val n = pairs.size

            fun wilson(succ: Int, den: Int) =
                if (den > 0) {
                    WilsonInterval.forProportion(succ, den, 0.95)
                } else {
                    null
                }

            if (n == 0) {
                val none = MetricValue.undefined(
                    note = "no ground-truth durations to evaluate"
                )
                return DurationMetrics(
                    sampleCount = 0,
                    meanAbsoluteError = none,
                    medianAbsError = none,
                    rmse = none,
                    bias = none,
                    withinTolerances = tolerances.associateWith { none },
                    absoluteErrorPercentiles = emptyList(),
                    signedErrorsSeconds = emptyList()
                )
            }

            val signed = pairs.map { (p, t) -> p - t }
            val abs = signed.map { kotlin.math.abs(it) }

            val mae = abs.sum().toDouble() / n
            val medianAbs = percentile(abs.sorted(), 50).toDouble()
            val mse = signed.sumOf { it.toLong() * it.toLong() } / n.toDouble()
            val rmse = kotlin.math.sqrt(mse)
            val biasMean = signed.sum().toDouble() / n

            val maeValue =
                if (n >= minimumSample) {
                    MetricValue.defined(mae, numerator = abs.sum(), denominator = n)
                } else {
                    MetricValue.insufficientSample(numerator = abs.sum(), denominator = n)
                }

            val medianValue =
                if (n >= minimumSample) {
                    MetricValue.defined(medianAbs, numerator = abs.sum(), denominator = n)
                } else {
                    MetricValue.insufficientSample(numerator = abs.sum(), denominator = n)
                }

            val rmseValue =
                if (n >= minimumSample) {
                    MetricValue.defined(rmse, numerator = n, denominator = n)
                } else {
                    MetricValue.insufficientSample(numerator = n, denominator = n)
                }

            val biasValue =
                if (n >= minimumSample) {
                    MetricValue.defined(biasMean, numerator = signed.sum(), denominator = n)
                } else {
                    MetricValue.insufficientSample(numerator = signed.sum(), denominator = n)
                }

            val within = tolerances.associateWith { tolerance ->
                val hit = abs.count { it <= tolerance }
                if (n >= minimumSample) {
                    MetricValue.defined(
                        value = hit.toDouble() / n,
                        numerator = hit,
                        denominator = n,
                        confidenceInterval = wilson(hit, n)
                    )
                } else {
                    MetricValue.insufficientSample(numerator = hit, denominator = n)
                }
            }

            val sortedAbs = abs.sorted()
            val percentileVals = percentiles.map {
                it to percentile(sortedAbs, it)
            }

            return DurationMetrics(
                sampleCount = n,
                meanAbsoluteError = maeValue,
                medianAbsError = medianValue,
                rmse = rmseValue,
                bias = biasValue,
                withinTolerances = within,
                absoluteErrorPercentiles = percentileVals,
                signedErrorsSeconds = signed
            )
        }

        private fun percentile(sorted: List<Int>, p: Int): Double {
            if (sorted.isEmpty()) return 0.0
            val idx = ((sorted.size - 1) * p / 100.0)
            val lower = sorted[kotlin.math.floor(idx).toInt()]
            val upper = sorted[kotlin.math.ceil(idx).toInt().coerceAtMost(sorted.size - 1)]
            val frac = idx - kotlin.math.floor(idx)
            return lower + (upper - lower) * frac
        }
    }
}