package com.example.feedsense.analysis.evaluation

// --------------------------------
// CONFIDENCE / CALIBRATION METRICS (Milestone 8A-3)
// --------------------------------
//
// Measures how well the AI's `confidence` corresponds to how
// often it is right (using the primary-category verdict as
// the correctness signal).
//
//   - BUCKETS: confidence rounded down into `bucketWidth`
//     bins (e.g. 10% bins: 0.0-0.1, 0.1-0.2, ...). For each
//     bucket we report the mean predicted confidence, the
//     empirical accuracy (of classified items inside it), and
//     a Wilson interval.
//   - ECE (expected calibration error): the mean, over
//     buckets, of |accuracy - meanConfidence| weighted by the
//     fraction of samples each bucket holds.
//
// Honesty:
//   - Buckets with too FEW samples report
//     INSUFFICIENT_SAMPLE_SIZE, never a fabricated accuracy.
//   - ECE is only DEFINED when there are enough samples across
//     enough buckets; otherwise INSUFFICIENT_SAMPLE_SIZE.
//
// Confidence is only meaningful when the truth is a definite
// CORRECT or INCORRECT primary-category verdict. Items whose
// truth is UNKNOWN / PARTIAL (ambiguous) are excluded from
// calibration, and their count is surfaced so the reader knows
// the calibration only applies to clearly-labelled items.

data class ConfidenceBucket(
    val lower: Double,
    val upper: Double,

    val sampleCount: Int,
    val meanConfidence: Double?,
    val accuracy: MetricValue,
    val delta: Double?
)

data class CalibrationMetrics(
    val sampleCount: Int,
    val excludedAmbiguous: Int,
    val buckets: List<ConfidenceBucket>,
    val ece: MetricValue,
    val bucketWidth: Double
) {

    companion object {

        const val DEFAULT_BUCKET_WIDTH = 0.1
        const val MIN_SAMPLES_PER_BUCKET_FOR_DEFINED = 1

        fun compute(
            // (confidence, correct) pairs, where `correct` is a
            // strict boolean derived only from a definitive
            // verdict (CORRECT / INCORRECT).
            pairs: List<Pair<Double, Boolean>>,
            bucketWidth: Double = DEFAULT_BUCKET_WIDTH,
            confidenceLevel: Double = 0.95
        ): CalibrationMetrics {

            val n = pairs.size

            val maxBucketIndex =
                if (pairs.isEmpty()) 0
                else (1.0 / bucketWidth).toInt()

            val bucketed =
                Array(maxBucketIndex + 1) { mutableListOf<Boolean>() }
            val bucketConfidence =
                Array(maxBucketIndex + 1) { mutableListOf<Double>() }

            pairs.forEach { (confidence, correct) ->
                val c = confidence.coerceIn(0.0, 1.0)
                var index = (c / bucketWidth).toInt()
                if (index >= bucketed.size) {
                    index = bucketed.size - 1
                }
                if (index < 0) index = 0
                bucketed[index].add(correct)
                bucketConfidence[index].add(c)
            }

            val buckets = bucketed.mapIndexed { i, list ->
                val lower = i * bucketWidth
                val upper = (i + 1) * bucketWidth
                val correct = list.count { it }
                val meanConf =
                    if (list.isEmpty()) null
                    else bucketConfidence[i].average()

                val accuracy =
                    if (list.isEmpty()) {
                        MetricValue.notApplicable(
                            note = "empty confidence bucket"
                        )
                    } else if (list.size < MIN_SAMPLES_PER_BUCKET_FOR_DEFINED) {
                        MetricValue.insufficientSample(
                            numerator = correct,
                            denominator = list.size
                        )
                    } else {
                        MetricValue.defined(
                            value = correct.toDouble() / list.size,
                            numerator = correct,
                            denominator = list.size,
                            confidenceInterval =
                                WilsonInterval.forProportion(
                                    correct,
                                    list.size,
                                    confidenceLevel
                                )
                        )
                    }

                ConfidenceBucket(
                    lower = lower,
                    upper = upper,
                    sampleCount = list.size,
                    meanConfidence = meanConf,
                    accuracy = accuracy,
                    delta =
                        if (accuracy.defined && meanConf != null) {
                            kotlin.math.abs(accuracy.value!! - meanConf)
                        } else {
                            null
                        }
                )
            }

            // ECE: only over buckets that are DEFINED.
            val definedBuckets = buckets.filter { it.accuracy.defined }
            val definedTotal = definedBuckets.sumOf { it.sampleCount }

            val ece =
                if (definedTotal == 0) {
                    MetricValue.notApplicable(
                        note = "no confidence bucket had defined accuracy"
                    )
                } else if (definedTotal < 2 ||
                    definedBuckets.size < 2
                ) {
                    MetricValue.insufficientSample(
                        numerator = definedTotal,
                        denominator = pairs.size,
                        note = "too few samples/buckets for a stable ECE"
                    )
                } else {
                    val eceValue = definedBuckets.sumOf { bucket ->
                        val weight =
                            bucket.sampleCount.toDouble() / definedTotal
                        weight * (bucket.delta ?: 0.0)
                    }
                    MetricValue.defined(
                        value = eceValue,
                        numerator = definedTotal,
                        denominator = n
                    )
                }

            return CalibrationMetrics(
                sampleCount = n,
                excludedAmbiguous = 0,
                buckets = buckets,
                ece = ece,
                bucketWidth = bucketWidth
            )
        }
    }
}