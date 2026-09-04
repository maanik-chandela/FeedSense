package com.example.feedsense.analysis.evaluation

// --------------------------------
// MULTI-LABEL METRICS (Milestone 8A-3)
// --------------------------------
//
// Content can carry multiple labels (primary + secondaries on
// both the AI prediction and the ground truth). To measure
// multi-label performance WITHOUT throwing the extra labels
// away, this reports:
//
//   - exact-match accuracy: fraction of items where the AI's
//     label SET equals the truth SET exactly
//   - example-based micro precision / recall / F1: averaged
//     per-item, then micro-aggregated across items (the
//     standard approach for multi-label classification)
//   - example-based macro precision / recall / F1: the mean
//     of per-item scores (each item weighted equally)
//
// The engine feeds each item's set of correct labels
// (primary + secondaries, normalized & fusion with the truth).

data class MultiLabelSample(
    // Set of labels the model predicted (already canonicalized).
    val predicted: Set<String>,
    // Set of labels the ground truth considers correct.
    val truth: Set<String>
)

data class MultiLabelMetrics(
    val sampleCount: Int,
    val exactMatch: MetricValue,
    val hammingLoss: MetricValue,
    val microPrecision: MetricValue,
    val microRecall: MetricValue,
    val microF1: MetricValue,
    val macroPrecision: MetricValue,
    val macroRecall: MetricValue,
    val macroF1: MetricValue
) {

    companion object {

        const val DEFAULT_MINIMUM_SAMPLE = 1

        fun compute(
            samples: List<MultiLabelSample>,
            minimumSample: Int = DEFAULT_MINIMUM_SAMPLE,
            confidenceLevel: Double = 0.95
        ): MultiLabelMetrics {

            val n = samples.size

            fun wilson(succ: Int, den: Int) =
                WilsonInterval.forProportion(succ, den, confidenceLevel)

            val exactMatches =
                samples.count {
                    it.predicted == it.truth
                }

            val exactMatch =
                if (n == 0) {
                    MetricValue.undefined()
                } else {
                    MetricValue.defined(
                        value = exactMatches.toDouble() / n,
                        numerator = exactMatches,
                        denominator = n,
                        confidenceInterval = wilson(exactMatches, n)
                    )
                }

            // Hamming loss: fraction of (item, label-slot)
            // disagreements across the union of all labels.
            val allLabels =
                samples.flatMap { it.predicted + it.truth }.toSet()
            val unionSize = allLabels.size
            var hammingDisagreements = 0
            samples.forEach { sample ->
                allLabels.forEach { label ->
                    if (label in sample.predicted != label in sample.truth) {
                        hammingDisagreements++
                    }
                }
            }
            val hammingDenominator = n * unionSize
            val hammingLoss =
                if (hammingDenominator == 0) {
                    MetricValue.notApplicable(
                        note = "no labels present to compare"
                    )
                } else {
                    MetricValue.defined(
                        value = hammingDisagreements.toDouble() / hammingDenominator,
                        numerator = hammingDisagreements,
                        denominator = hammingDenominator
                    )
                }

            // Per-item micro aggregation.
            var microTP = 0
            var microFP = 0
            var microFN = 0
            var totalLabels = 0

            var macroPSum = 0.0
            var macroRSum = 0.0
            var macroFSum = 0.0

            samples.forEach { sample ->
                val tp = (sample.predicted intersect sample.truth).size
                val fp = (sample.predicted - sample.truth).size
                val fn = (sample.truth - sample.predicted).size

                microTP += tp
                microFP += fp
                microFN += fn
                totalLabels += sample.truth.size

                val p = if (tp + fp > 0) tp.toDouble() / (tp + fp) else 0.0
                val r = if (tp + fn > 0) tp.toDouble() / (tp + fn) else 0.0
                macroPSum += p
                macroRSum += r
                if (p + r > 0) {
                    macroFSum += 2.0 * p * r / (p + r)
                }
            }

            fun proportionPer(succ: Int, den: Int, stateIfZeroDen: MetricValue) =
                if (den == 0) stateIfZeroDen
                else MetricValue.defined(
                    value = succ.toDouble() / den,
                    numerator = succ,
                    denominator = den,
                    confidenceInterval = wilson(succ, den)
                )

            val microPrecision =
                proportionPer(
                    microTP,
                    microTP + microFP,
                    MetricValue.notApplicable(
                        numerator = microTP,
                        denominator = microTP + microFP,
                        note = "no positive predictions across the sample"
                    )
                )

            val microRecall =
                proportionPer(
                    microTP,
                    microTP + microFN,
                    MetricValue.undefined(
                        denominator = microTP + microFN,
                        note = "no positive ground-truth labels across the sample"
                    )
                )

            val microF1 =
                if (microPrecision.defined && microRecall.defined) {
                    val p = microPrecision.value!!
                    val r = microRecall.value!!
                    if (p + r > 0) {
                        MetricValue.defined(
                            value = 2.0 * p * r / (p + r),
                            numerator = 2 * microTP,
                            denominator = 2 * microTP + microFP + microFN
                        )
                    } else {
                        MetricValue.notApplicable(
                            note = "micro precision and recall both zero"
                        )
                    }
                } else {
                    MetricValue.notApplicable(
                        note = "micro precision or recall not defined"
                    )
                }

            val hasTruth = n > 0 && totalLabels > 0
            val hasPredicted =
                n > 0 && samples.any { it.predicted.isNotEmpty() }

            val macroPrecision =
                if (hasTruth && hasPredicted) {
                    MetricValue.defined(
                        value = macroPSum / n,
                        numerator = microTP,
                        denominator = microTP + microFP,
                        note = "per-item mean of precision"
                    )
                } else {
                    MetricValue.notApplicable(
                        note = "no predicted labels among samples"
                    )
                }

            val macroRecall =
                if (hasTruth) {
                    MetricValue.defined(
                        value = macroRSum / n,
                        numerator = microTP,
                        denominator = microTP + microFN,
                        note = "per-item mean of recall"
                    )
                } else {
                    MetricValue.undefined(
                        note = "no ground-truth labels among samples"
                    )
                }

            val macroF1 =
                if (hasTruth) {
                    MetricValue.defined(
                        value = macroFSum / n,
                        numerator = 2 * microTP,
                        denominator = 2 * microTP + microFP + microFN,
                        note = "per-item mean of F1"
                    )
                } else {
                    MetricValue.notApplicable(
                        note = "no ground-truth labels among samples"
                    )
                }

            return MultiLabelMetrics(
                sampleCount = n,
                exactMatch = exactMatch,
                hammingLoss = hammingLoss,
                microPrecision = microPrecision,
                microRecall = microRecall,
                microF1 = microF1,
                macroPrecision = macroPrecision,
                macroRecall = macroRecall,
                macroF1 = macroF1
            )
        }
    }
}