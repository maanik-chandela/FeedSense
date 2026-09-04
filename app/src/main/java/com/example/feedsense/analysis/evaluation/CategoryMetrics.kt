package com.example.feedsense.analysis.evaluation

// --------------------------------
// CATEGORY (MULTI-CLASS) METRICS (Milestone 8A-3)
// --------------------------------
//
// Primary-category classification metrics, computed from
// (predictedCategory, truthCategory) pairs after normalizing
// through the shared CategoryCatalog so synonyms collapse.
//
// Reports BOTH:
//   - per-category precision / recall / F1 (one-vs-rest)
//   - macro / micro / weighted aggregates
//
// A confusion matrix is produced from the union of all
// observed (predicted or truth) class labels.
//
// Honesty rules:
//   - UNKNOWN truth (ambiguity = AMBIGUITY_UNKNOWN) is never
//     counted as FALSE here; such items are filtered by the
//     engine into their own dedicated section, not mixed in.
//   - any category observed with no valid samples reports
//     per-class metrics as INSUFFICIENT_SAMPLE_SIZE /
//     NOT_APPLICABLE rather than a made-up number.

data class PerClassCategoryMetrics(
    val label: String,
    val tp: Int,
    val fp: Int,
    val fn: Int,
    val support: Int,
    val precision: MetricValue,
    val recall: MetricValue,
    val f1: MetricValue?
)

data class AggregateMetrics(
    val macroPrecision: MetricValue,
    val macroRecall: MetricValue,
    val macroF1: MetricValue,
    val microAccuracy: MetricValue,
    val weightedPrecision: MetricValue,
    val weightedRecall: MetricValue,
    val weightedF1: MetricValue
)

data class ConfusionMatrix(
    // Rows = predicted label, columns = truth label.
    // Labels are the union of all observed (or configured)
    // classes, sorted for reproducibility.
    val labels: List<String>,
    val counts: Map<Pair<String, String>, Int>
) {

    fun countOf(predicted: String, truth: String): Int {
        return counts[predicted to truth] ?: 0
    }
}

data class CategoryMetrics(
    val perClass: List<PerClassCategoryMetrics>,
    val aggregates: AggregateMetrics,
    val confusionMatrix: ConfusionMatrix,
    val sampleCount: Int
) {

    companion object {

        /*
         * Computes multiclass category metrics.
         *
         * @param pairs          normalized (predicted, truth) pairs
         * @param classLabels    the universe of classes to report
         *                       (union of observed + configured so
         *                       absent classes still appear with
         *                       NOT_APPLICABLE, not silently)
         * @param minimumSample  per-class minimum positive truth to
         *                       call recall/precision DEFINED
         */
        fun compute(
            pairs: List<Pair<String?, String?>>,
            classLabels: List<String>,
            minimumSample: Int = 1,
            confidenceLevel: Double = 0.95
        ): CategoryMetrics {

            // Build the union of labels to include: the caller's
            // configured universe plus anything observed.
            val observed = buildSet {
                pairs.forEach { (p, t) ->
                    p?.let { add(it) }
                    t?.let { add(it) }
                }
            }
            val labels =
                (classLabels + observed)
                    .distinct()
                    .sorted()

            // Confusion matrix counts.
            val confusion = mutableMapOf<Pair<String, String>, Int>().apply {
                pairs.forEach { (p, t) ->
                    if (p != null && t != null) {
                        merge(p to t, 1, Int::plus)
                    }
                }
            }

            val truthSupport = mutableMapOf<String, Int>()
            val predictedCount = mutableMapOf<String, Int>()
            val tpByClass = mutableMapOf<String, Int>()
            val fnByClass = mutableMapOf<String, Int>()

            pairs.forEach { (p, t) ->
                if (t != null) {
                    truthSupport.merge(t, 1, Int::plus)
                    if (p != null && p == t) {
                        tpByClass.merge(t, 1, Int::plus)
                    } else {
                        fnByClass.merge(t, 1, Int::plus)
                    }
                }
                if (p != null) {
                    predictedCount.merge(p, 1, Int::plus)
                }
            }

            fun wilson(succ: Int, den: Int) =
                if (den > 0) {
                    WilsonInterval.forProportion(succ, den, confidenceLevel)
                } else {
                    null
                }

            val perClass = labels.map { label ->
                val tp = tpByClass[label] ?: 0
                val fn = fnByClass[label] ?: 0
                val support = truthSupport[label] ?: 0
                val fp = (predictedCount[label] ?: 0) - tp

                val precision =
                    when {
                        predictedCount[label] == null || predictedCount[label] == 0 ->
                            MetricValue.notApplicable(
                                numerator = tp,
                                denominator = tp + fp,
                                note = "model never predicted '$label'"
                            )

                        support < minimumSample ->
                            MetricValue.insufficientSample(
                                numerator = tp,
                                denominator = tp + fp + fn,
                                note = "too few '$label' truth examples"
                            )

                        else ->
                            MetricValue.defined(
                                value = tp.toDouble() / (predictedCount[label]!!),
                                numerator = tp,
                                denominator = predictedCount[label]!!,
                                confidenceInterval =
                                    wilson(tp, predictedCount[label]!!)
                            )
                    }

                val recall =
                    when {
                        support == 0 ->
                            MetricValue.notApplicable(
                                numerator = tp,
                                denominator = tp + fn,
                                note = "no '$label' ground truth present"
                            )

                        support < minimumSample ->
                            MetricValue.insufficientSample(
                                numerator = tp,
                                denominator = tp + fn,
                                note = "too few '$label' truth examples"
                            )

                        else ->
                            MetricValue.defined(
                                value = tp.toDouble() / support,
                                numerator = tp,
                                denominator = support,
                                confidenceInterval = wilson(tp, support)
                            )
                    }

                val f1 =
                    if (precision.defined && recall.defined) {
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
                                denominator = 2 * tp + fp + fn
                            )
                        }
                    } else {
                        null
                    }

                PerClassCategoryMetrics(
                    label = label,
                    tp = tp,
                    fp = fp,
                    fn = fn,
                    support = support,
                    precision = precision,
                    recall = recall,
                    f1 = f1
                )
            }

            // --------------------------------
            // AGGREGATES
            // --------------------------------

            val totalTruth = truthSupport.values.sum()
            val totalPredicted = predictedCount.values.sum()
            val totalCorrect = tpByClass.values.sum()

            // Micro accuracy = total correct / total samples.
            val microAccuracy =
                if (totalTruth > 0) {
                    val ci = wilson(totalCorrect, totalTruth)
                    MetricValue.defined(
                        value = totalCorrect.toDouble() / totalTruth,
                        numerator = totalCorrect,
                        denominator = totalTruth,
                        confidenceInterval = ci
                    )
                } else {
                    MetricValue.undefined()
                }

            // Macro = unweighted mean over classes that are
            // DEFINED. Classes that are NOT_APPLICABLE or
            // INSUFFICIENT are surfaced separately (see note)
            // but excluded from the numeric macro to avoid
            // feeding a fake 0.
            val definedPrec =
                perClass.filter { it.precision.defined }
            val definedRec =
                perClass.filter { it.recall.defined }

            val macroPrecision =
                if (definedPrec.isNotEmpty()) {
                    val mean =
                        definedPrec.map { it.precision.value!! }
                            .average()
                    MetricValue.defined(
                        value = mean,
                        numerator = definedPrec.sumOf { it.tp },
                        denominator =
                            definedPrec.sumOf { it.tp + it.fp },
                        note = "macro over ${definedPrec.size} defined classes"
                    )
                } else {
                    MetricValue.notApplicable(
                        note = "no class had defined precision"
                    )
                }

            val macroRecall =
                if (definedRec.isNotEmpty()) {
                    val mean =
                        definedRec.map { it.recall.value!! }
                            .average()
                    MetricValue.defined(
                        value = mean,
                        numerator = definedRec.sumOf { it.tp },
                        denominator =
                            definedRec.sumOf { it.tp + it.fn },
                        note = "macro over ${definedRec.size} defined classes"
                    )
                } else {
                    MetricValue.notApplicable(
                        note = "no class had defined recall"
                    )
                }

            val macroF1 =
                if (macroPrecision.defined &&
                    macroRecall.defined
                ) {
                    val p = macroPrecision.value!!
                    val r = macroRecall.value!!
                    if (p + r > 0.0) {
                        MetricValue.defined(
                            value = 2.0 * p * r / (p + r),
                            numerator = 2 * macroPrecision.numerator,
                            denominator = macroPrecision.numerator + macroRecall.numerator
                        )
                    } else {
                        MetricValue.notApplicable()
                    }
                } else {
                    MetricValue.notApplicable(
                        note = "macro precision or recall undefined"
                    )
                }

            // Weighted aggregates = support-weighted.
            fun weighted(
                supportedMetrics: List<Triple<MetricValue, Int, Int>>
            ): MetricValue {
                val totalSupport =
                    supportedMetrics.sumOf { it.second + it.third }
                if (totalSupport == 0) {
                    return MetricValue.notApplicable(
                        note = "no supporting samples for weighting"
                    )
                }
                val weighted = supportedMetrics
                    .filter { it.first.defined }
                    .sumOf { (metric, tp, fn) ->
                        metric.value!! * (tp + fn).toDouble()
                    }
                val totalDefinedSupport =
                    supportedMetrics
                        .filter { it.first.defined }
                        .sumOf { it.second + it.third }
                if (totalDefinedSupport == 0) {
                    return MetricValue.notApplicable(
                        note = "no defined metric had samples to weight"
                    )
                }
                return MetricValue.defined(
                    value = weighted / totalDefinedSupport,
                    numerator = totalDefinedSupport,
                    denominator = totalSupport,
                    note = "support-weighted over defined classes"
                )
            }

            val weightedPrecision =
                weighted(
                    perClass.map {
                        Triple(
                            it.precision,
                            it.tp,
                            it.fn
                        )
                    }
                )

            val weightedRecall =
                weighted(
                    perClass.map {
                        Triple(
                            it.recall,
                            it.tp,
                            it.fn
                        )
                    }
                )

            val weightedF1 =
                weighted(
                    perClass.mapNotNull {
                        if (it.f1 != null) {
                            Triple(it.f1, it.tp, it.fn)
                        } else {
                            null
                        }
                    }
                )

            val aggregates =
                AggregateMetrics(
                    macroPrecision = macroPrecision,
                    macroRecall = macroRecall,
                    macroF1 = macroF1,
                    microAccuracy = microAccuracy,
                    weightedPrecision = weightedPrecision,
                    weightedRecall = weightedRecall,
                    weightedF1 = weightedF1
                )

            return CategoryMetrics(
                perClass = perClass,
                aggregates = aggregates,
                confusionMatrix = ConfusionMatrix(
                    labels = labels,
                    counts = confusion
                ),
                sampleCount = pairs.size
            )
        }
    }
}