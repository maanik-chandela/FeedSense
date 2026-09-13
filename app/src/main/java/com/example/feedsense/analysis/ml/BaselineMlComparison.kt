package com.example.feedsense.analysis.ml

import com.example.feedsense.analysis.FrameAnalysisResult
import com.example.feedsense.model.AiPredictionRecord

// --------------------------------
// BASELINE vs ML COMPARISON (8B-14)
// --------------------------------
//
// A head-to-head between the EXISTING BASELINE prediction
// and an ON-DEVICE ML prediction for the same item, WITHOUT
// modifying either prediction (8B-14 section 20).
//
// IMPORTANT - descriptive, NOT correctness. The comparison
// surfaces agreement/disagreement; whether either path was
// "right" is answered by the 8A evaluation layer against the
// same frozen ground truth (8B-14 sections 20, 52).
//
//   baseline = sports   |   ml = advertisement
//   -> categoriesAgree = false, kind = DISAGREE
//
// Both sides remain immutable, independently reproducible,
// and unchanged by the comparison.

/*
 * Type of relationship between the two predictions.
 */
enum class BaselineMlComparisonKind(val label: String) {
    AGREE("AGREE"),
    DISAGREE("DISAGREE"),
    BASELINE_ONLY_DECIDED("BASELINE_ONLY_DECIDED"),
    ML_ONLY_DECIDED("ML_ONLY_DECIDED"),
    NEITHER_DECIDED("NEITHER_DECIDED"),
    ML_UNAVAILABLE("ML_UNAVAILABLE")
}

data class BaselineMlComparison(
    // --------------------------------
    // BASELINE (read verbatim, never mutated)
    // --------------------------------
    val feedItemId: String? = null,
    val baselineCategory: String?,
    val baselineConfidence: Double?,
    val baselineModelVersion: String?,

    // --------------------------------
    // ML (read verbatim, never mutated)
    // --------------------------------
    val mlStatus: InferenceStatus,
    val mlCategory: String?,
    val mlConfidence: Double?,
    val mlModelVersion: String?,
    val mlTopK: List<RankedPrediction> = emptyList(),

    // --------------------------------
    // COMPARISON (descriptive only)
    // --------------------------------
    val categoriesAgree: Boolean,
    val comparisonKind: BaselineMlComparisonKind
) {

    companion object {

        /*
         * Comparison from the frame-level baseline result.
         */
        fun of(
            baseline: FrameAnalysisResult,
            ml: ModelInferenceResult
        ): BaselineMlComparison {
            return build(
                feedItemId = null,
                baselineCategory = baseline.contentCategory,
                baselineConfidence = baseline.confidence,
                baselineModelVersion = baseline.modelVersion,
                ml = ml
            )
        }

        /*
         * Comparison from the frozen 8A baseline record.
         * The record is only read; it is never modified.
         */
        fun of(
            baseline: AiPredictionRecord,
            ml: ModelInferenceResult
        ): BaselineMlComparison {
            return build(
                feedItemId = baseline.feedItemId,
                baselineCategory = baseline.category,
                baselineConfidence = baseline.confidence,
                baselineModelVersion = baseline.modelVersion,
                ml = ml
            )
        }

        private fun build(
            feedItemId: String?,
            baselineCategory: String?,
            baselineConfidence: Double?,
            baselineModelVersion: String?,
            ml: ModelInferenceResult
        ): BaselineMlComparison {
            val mlUsable = ml.status == InferenceStatus.SUCCESS
            val mlCategory = if (mlUsable) ml.primaryCategory else null
            val mlConfidence = if (mlUsable) ml.primaryConfidence else null

            val agree = baselineCategory != null &&
                mlCategory != null &&
                baselineCategory == mlCategory

            val kind = when {
                !mlUsable -> BaselineMlComparisonKind.ML_UNAVAILABLE
                agree -> BaselineMlComparisonKind.AGREE
                baselineCategory != null && mlCategory != null ->
                    BaselineMlComparisonKind.DISAGREE
                baselineCategory != null -> BaselineMlComparisonKind.BASELINE_ONLY_DECIDED
                mlCategory != null -> BaselineMlComparisonKind.ML_ONLY_DECIDED
                else -> BaselineMlComparisonKind.NEITHER_DECIDED
            }

            return BaselineMlComparison(
                feedItemId = feedItemId,
                baselineCategory = baselineCategory,
                baselineConfidence = baselineConfidence,
                baselineModelVersion = baselineModelVersion,
                mlStatus = ml.status,
                mlCategory = mlCategory,
                mlConfidence = mlConfidence,
                mlModelVersion = ml.modelVersion,
                mlTopK = ml.rankedPredictions,
                categoriesAgree = agree,
                comparisonKind = kind
            )
        }
    }
}