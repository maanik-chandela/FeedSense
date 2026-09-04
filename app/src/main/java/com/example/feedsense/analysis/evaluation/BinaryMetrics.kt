package com.example.feedsense.analysis.evaluation

// --------------------------------
// BINARY CLASSIFICATION METRICS (Milestone 8A-3)
// --------------------------------
//
// Standard 2x2 contingency metrics computed over a set of
// (predicted, truth) boolean pairs.
//
// Honesty guarantees:
//   - numerator / denominator always surfaced
//   - precision is NOT_APPLICABLE when the model never
//     predicted the positive class (TP+FP == 0)
//   - recall is NOT_APPLICABLE when there is no positive
//     truth (TP+FN == 0)
//   - all estimates carry a Wilson confidence interval

data class BinaryMetrics(
    val tp: Int = 0,
    val fp: Int = 0,
    val fn: Int = 0,
    val tn: Int = 0,

    val accuracy: MetricValue? = null,
    val precision: MetricValue? = null,
    val recall: MetricValue? = null,
    val specificity: MetricValue? = null,
    val f1: MetricValue? = null,
    val mcc: Double? = null
) {

    val support: Int get() = tp + fp + fn + tn

    companion object {

        /*
         * Computes binary metrics from a list of prediction /
         * truth pairs.
         *
         * `minimumSample` is the smallest number of POSITIVE
         * truth examples that must be observed before
         * precision / recall / F1 are considered DEFINED.
         * Below it, we report INSUFFICIENT_SAMPLE_SIZE from
         * the truth-base instead of a misleading near-zero.
         */
        fun compute(
            pairs: List<Pair<Boolean, Boolean>>,
            minimumSample: Int = 1,
            confidenceLevel: Double = 0.95
        ): BinaryMetrics {

            var tp = 0
            var fp = 0
            var fn = 0
            var tn = 0

            for ((predicted, truth) in pairs) {
                when {
                    predicted && truth -> tp++
                    predicted && !truth -> fp++
                    !predicted && truth -> fn++
                    else -> tn++
                }
            }

            val total = tp + fp + fn + tn

            // An empty sample set is UNDEFINED, not a
            // NOT_APPLICABLE-able zero: there is simply no data,
            // so we expose no metric value at all.
            if (total == 0) {
                return BinaryMetrics()
            }

            fun wilson(successes: Int, denominator: Int) =
                if (denominator > 0) {
                    WilsonInterval.forProportion(
                        successes,
                        denominator,
                        confidenceLevel
                    )
                } else {
                    null
                }

            val accuracy =
                if (total > 0) {
                    MetricValue.defined(
                        value = (tp + tn).toDouble() / total,
                        numerator = tp + tn,
                        denominator = total,
                        confidenceInterval =
                            wilson(tp + tn, total)
                    )
                } else {
                    MetricValue.undefined()
                }

            val precision =
                when {
                    tp + fp == 0 ->
                        MetricValue.notApplicable(
                            numerator = tp,
                            denominator = tp + fp,
                            note = "the model never predicted this class"
                        )

                    tp + fn < minimumSample ->
                        MetricValue.insufficientSample(
                            numerator = tp,
                            denominator = tp + fp + fn,
                            note = "too few positive truth examples"
                        )

                    else ->
                        MetricValue.defined(
                            value = tp.toDouble() / (tp + fp),
                            numerator = tp,
                            denominator = tp + fp,
                            confidenceInterval =
                                wilson(tp, tp + fp)
                        )
                }

            val recall =
                when {
                    tp + fn == 0 ->
                        MetricValue.notApplicable(
                            numerator = tp,
                            denominator = tp + fn,
                            note = "no positive ground truth present"
                        )

                    tp + fp + fn < minimumSample ->
                        MetricValue.insufficientSample(
                            numerator = tp,
                            denominator = tp + fn,
                            note = "too few positive truth examples"
                        )

                    else ->
                        MetricValue.defined(
                            value = tp.toDouble() / (tp + fn),
                            numerator = tp,
                            denominator = tp + fn,
                            confidenceInterval =
                                wilson(tp, tp + fn)
                        )
                }

            val specificity =
                if (fp + tn > 0) {
                    MetricValue.defined(
                        value = tn.toDouble() / (tn + fp),
                        numerator = tn,
                        denominator = tn + fp,
                        confidenceInterval =
                            wilson(tn, tn + fp)
                    )
                } else {
                    MetricValue.notApplicable(
                        numerator = tn,
                        denominator = tn + fp,
                        note = "no negative ground truth present"
                    )
                }

            val f1 =
                if (precision?.defined == true &&
                    recall?.defined == true
                ) {
                    val p = precision.value!!
                    val r = recall.value!!
                    if (p + r > 0.0) {
                        MetricValue.defined(
                            value = 2.0 * p * r / (p + r),
                            numerator = 2 * tp,
                            denominator = 2 * tp + fp + fn
                        )
                    } else {
                        MetricValue.notApplicable(
                            numerator = 2 * tp,
                            denominator = 2 * tp + fp + fn,
                            note = "precision and recall are both zero"
                        )
                    }
                } else {
                    null
                }

            // Matthews Correlation Coefficient. Defined, with
            // a defined value, only when it is computable from
            // at least one actual example.
            val mcc =
                if (total > 0 && (tp > 0 || tn > 0 || fp > 0 || fn > 0)) {
                    val m = (tp + fp) * (tp + fn) * (tn + fp) * (tn + fn)
                    if (m == 0) {
                        // Degenerate case (a column or row is
                        // all zeros). Report the closed-form.
                        val matthews =
                            (tp * tn - fp * fn).toDouble() /
                                sqrtCase(m)
                        matthews.coerceIn(-1.0, 1.0)
                    } else {
                        val matthews =
                            (tp * tn - fp * fn).toDouble() /
                                kotlin.math.sqrt(m.toDouble())
                        matthews.coerceIn(-1.0, 1.0)
                    }
                } else {
                    null
                }

            return BinaryMetrics(
                tp = tp,
                fp = fp,
                fn = fn,
                tn = tn,
                accuracy = accuracy,
                precision = precision,
                recall = recall,
                specificity = specificity,
                f1 = f1,
                mcc = mcc
            )
        }

        private fun sqrtCase(value: Int): Double {
            return if (value <= 0) 1.0 else kotlin.math.sqrt(value.toDouble())
        }
    }
}