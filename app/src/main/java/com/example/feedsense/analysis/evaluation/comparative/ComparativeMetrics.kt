package com.example.feedsense.analysis.evaluation.comparative

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.analysis.evaluation.MetricValue
import com.example.feedsense.analysis.evaluation.WilsonInterval

/*
 * Milestone 8B-8.
 *
 * Comparative metrics.
 *
 * Computes accuracy / coverage / abstention / selective-accuracy
 * for BOTH systems over the SAME eligible paired population,
 * plus the 8-vs-baseline outcome distribution and per-category
 * breakdowns.
 *
 * Accuracy terminology (on the eligible population, i.e. pairs
 * whose shared truth defines a definitive category):
 *   - accuracy       = correct / eligible
 *   - coverage       = decided / eligible   (decided = not unknown)
 *   - abstention     = unknown / eligible   (= 1 - coverage)
 *   - selectiveAcc   = correct / decided    (accuracy whenever the
 *                     model chose to answer). This is the metric
 *                     that rewards honest abstention, so a system
 *                     that abstains on hard items is not penalized
 *                     for its abstentions while still being judged
 *                     on the ones it attempted.
 *
 * Every proportion carries a Wilson confidence interval and an
 * explicit state; zeros and tiny samples never collapse into a
 * fake "100%".
 */
object ComparativeMetrics {

    // --------------------------------
    // OUTCOME TALLY
    // --------------------------------

    data class OutcomeTally(
        val paired: Int,
        val eligible: Int,
        val ineligible: Int,
        val bothCorrect: Int,
        val bothWrong: Int,
        val baselineOnlyCorrect: Int,
        val eightBOnlyCorrect: Int,
        val bothUnknown: Int,
        val baselineUnknownEightBCorrect: Int,
        val baselineCorrectEightBUnknown: Int,
        val baselineWrongEightBUnknown: Int,
        val eightBWrongBaselineUnknown: Int,
        val eightBImprovements: Int,
        val eightBRegressions: Int
    )

    fun tally(pairs: List<PairedPrediction>): OutcomeTally {
        val eligible = pairs.filter { it.eligibleForAccuracy }
        var bothCorrect = 0
        var bothWrong = 0
        var baselineOnly = 0
        var eightBOnly = 0
        var bothUnknown = 0
        var baseUnknownEightBCorrect = 0
        var baseCorrectEightBUnknown = 0
        var baseWrongEightBUnknown = 0
        var eightBWrongBaseUnknown = 0

        for (p in eligible) {
            when (p.outcome) {
                ComparisonOutcome.BOTH_CORRECT -> bothCorrect++
                ComparisonOutcome.BOTH_WRONG -> bothWrong++
                ComparisonOutcome.BASELINE_ONLY_CORRECT ->
                    baselineOnly++
                ComparisonOutcome.EIGHT_B_ONLY_CORRECT ->
                    eightBOnly++
                ComparisonOutcome.BOTH_UNKNOWN -> bothUnknown++
                ComparisonOutcome.BASELINE_UNKNOWN_EIGHT_B_CORRECT ->
                    baseUnknownEightBCorrect++
                ComparisonOutcome.BASELINE_CORRECT_EIGHT_B_UNKNOWN ->
                    baseCorrectEightBUnknown++
                ComparisonOutcome.BASELINE_WRONG_EIGHT_B_UNKNOWN ->
                    baseWrongEightBUnknown++
                ComparisonOutcome.EIGHT_B_WRONG_BASELINE_UNKNOWN ->
                    eightBWrongBaseUnknown++
            }
        }

        val overallEligible = eligible.size
        return OutcomeTally(
            paired = pairs.size,
            eligible = overallEligible,
            ineligible = pairs.size - overallEligible,
            bothCorrect = bothCorrect,
            bothWrong = bothWrong,
            baselineOnlyCorrect = baselineOnly,
            eightBOnlyCorrect = eightBOnly,
            bothUnknown = bothUnknown,
            baselineUnknownEightBCorrect = baseUnknownEightBCorrect,
            baselineCorrectEightBUnknown = baseCorrectEightBUnknown,
            baselineWrongEightBUnknown = baseWrongEightBUnknown,
            eightBWrongBaselineUnknown = eightBWrongBaseUnknown,
            eightBImprovements = eightBOnly + baseUnknownEightBCorrect,
            eightBRegressions = baselineOnly
        )
    }

    // --------------------------------
    // PER-SYSTEM PROPORTIONS
    // --------------------------------

    /**
     * Computes the proportion metrics for ONE system given its
     * correct / not-correct / unknown counts over the eligible
     * population.
     */
    fun systemMetrics(
        correct: Int,
        notCorrect: Int,
        unknown: Int,
        minConfidence: Int
    ): SystemMetrics {
        val eligible = correct + notCorrect + unknown
        val decided = correct + notCorrect

        val accuracy = proportion(
            correct, eligible, minConfidence
        )
        val coverage = proportion(
            decided, eligible, minConfidence
        )
        val abstention = proportion(
            unknown, eligible, minConfidence
        )
        val selective = proportion(
            correct, decided, minConfidence
        )

        return SystemMetrics(
            eligible = eligible,
            correct = correct,
            notCorrect = notCorrect,
            unknown = unknown,
            accuracy = accuracy,
            coverage = coverage,
            abstention = abstention,
            selectiveAccuracy = selective
        )
    }

    data class SystemMetrics(
        val eligible: Int,
        val correct: Int,
        val notCorrect: Int,
        val unknown: Int,
        val accuracy: MetricValue,
        val coverage: MetricValue,
        val abstention: MetricValue,
        val selectiveAccuracy: MetricValue
    )

    /**
     * Computes both systems' metrics over the SAME eligible
     * population in one pass.
     */
    fun bothSystems(
        pairs: List<PairedPrediction>,
        minConfidence: Int
    ): BothSystems {
        val eligible = pairs.filter { it.eligibleForAccuracy }
        var bCorrect = 0
        var bNotCorrect = 0
        var bUnknown = 0
        var eCorrect = 0
        var eNotCorrect = 0
        var eUnknown = 0
        for (p in eligible) {
            when {
                p.baselineCorrect -> bCorrect++
                p.baselineUnknown -> bUnknown++
                else -> bNotCorrect++
            }
            when {
                p.eightBCorrect -> eCorrect++
                p.eightBUnknown -> eUnknown++
                else -> eNotCorrect++
            }
        }
        return BothSystems(
            eligible = eligible.size,
            baseline = systemMetrics(
                bCorrect, bNotCorrect, bUnknown, minConfidence
            ),
            eightB = systemMetrics(
                eCorrect, eNotCorrect, eUnknown, minConfidence
            )
        )
    }

    data class BothSystems(
        val eligible: Int,
        val baseline: SystemMetrics,
        val eightB: SystemMetrics
    )

    // --------------------------------
    // PER-CATEGORY
    // --------------------------------

    sealed class PerCategoryEntry {
        data class Defined(
            val category: String,
            val truthSupport: Int,
            val tp: Int,
            val fp: Int,
            val fn: Int,
            val precision: MetricValue,
            val recall: MetricValue,
            val f1: MetricValue
        ) : PerCategoryEntry()

        data class Insufficient(
            val category: String,
            val truthSupport: Int,
            val tp: Int,
            val fp: Int,
            val fn: Int
        ) : PerCategoryEntry()
    }

    /**
     * Per-category precision/recall/F1 for a single system,
     * judged against the same eligible population. One-vs-rest
     * where a system's prediction is "correct" iff its primary
     * equals the ground-truth category.
     */
    fun perCategory(
        pairs: List<PairedPrediction>,
        isBaseline: Boolean,
        minClassSupport: Int
    ): Map<String, PerCategoryEntry> {
        val eligible = pairs.filter { it.eligibleForAccuracy }
        val allCategories = eligible
            .map { CategoryCatalog.normalize(it.truth.category) }
            .filterNotNull()
            .toSortedSet()

        val result = sortedMapOf<String, PerCategoryEntry>()
        for (cat in allCategories) {
            val truthSupport = eligible.count {
                CategoryCatalog.normalize(it.truth.category) == cat
            }
            val tp = eligible.count { p ->
                val predCat = if (isBaseline) {
                    p.baselineRecord.category
                } else {
                    p.eightBRecord.category
                }
                CategoryCatalog.normalize(predCat) == cat &&
                    CategoryCatalog.normalize(p.truth.category) == cat
            }
            val fp = eligible.count { p ->
                val predCat = if (isBaseline) {
                    p.baselineRecord.category
                } else {
                    p.eightBRecord.category
                }
                CategoryCatalog.normalize(predCat) == cat &&
                    CategoryCatalog.normalize(p.truth.category) != cat
            }
            val fn = eligible.count { p ->
                CategoryCatalog.normalize(p.truth.category) == cat &&
                    if (isBaseline) {
                        CategoryCatalog.normalize(
                            p.baselineRecord.category
                        ) != cat
                    } else {
                        CategoryCatalog.normalize(
                            p.eightBRecord.category
                        ) != cat
                    }
            }

            if (truthSupport < minClassSupport) {
                result[cat] = PerCategoryEntry.Insufficient(
                    category = cat,
                    truthSupport = truthSupport,
                    tp = tp,
                    fp = fp,
                    fn = fn
                )
                continue
            }

            val precision = ratioMetric(tp, tp + fp, minClassSupport)
            val recall = ratioMetric(tp, tp + fn, minClassSupport)
            val f1 = if (precision.defined && recall.defined) {
                val p = precision.value!!
                val r = recall.value!!
                val denom = p + r
                if (denom > 0.0) {
                    MetricValue(
                        state = MetricValue.State.DEFINED,
                        value = 2.0 * p * r / denom,
                        numerator = tp,
                        denominator = tp + fp + fn,
                        confidenceInterval =
                            WilsonInterval.forProportion(
                                tp,
                                tp + fp + fn,
                                0.95
                            ),
                        note = "harmonic_mean_of_precision_recall"
                    )
                } else {
                    MetricValue(
                        state = MetricValue.State.UNDEFINED,
                        numerator = tp,
                        denominator = tp + fp + fn
                    )
                }
            } else {
                MetricValue(
                    state = MetricValue.State.INSUFFICIENT_SAMPLE_SIZE,
                    numerator = tp,
                    denominator = tp + fp + fn
                )
            }
            result[cat] = PerCategoryEntry.Defined(
                category = cat,
                truthSupport = truthSupport,
                tp = tp,
                fp = fp,
                fn = fn,
                precision = precision,
                recall = recall,
                f1 = f1
            )
        }
        return result
    }

    // --------------------------------
    // HELPERS
    // --------------------------------

    private fun proportion(
        successes: Int,
        total: Int,
        minConfidence: Int
    ): MetricValue {
        if (total <= 0) {
            return MetricValue(
                state = MetricValue.State.UNDEFINED,
                numerator = successes,
                denominator = total
            )
        }
        val ci = WilsonInterval.forProportion(
            successes, total, 0.95
        )
        return MetricValue(
            state = MetricValue.State.DEFINED,
            value = successes.toDouble() / total,
            numerator = successes,
            denominator = total,
            confidenceInterval = ci
        )
    }

    private fun ratioMetric(
        successes: Int,
        total: Int,
        minSample: Int
    ): MetricValue {
        if (total <= 0) {
            return MetricValue(
                state = MetricValue.State.NOT_APPLICABLE,
                numerator = successes,
                denominator = total,
                note = "no_predicted_positives_for_class"
            )
        }
        val ci = WilsonInterval.forProportion(successes, total, 0.95)
        return MetricValue(
            state = MetricValue.State.DEFINED,
            value = successes.toDouble() / total,
            numerator = successes,
            denominator = total,
            confidenceInterval = ci
        )
    }
}
