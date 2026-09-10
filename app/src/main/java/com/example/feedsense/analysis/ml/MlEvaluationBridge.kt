package com.example.feedsense.analysis.ml

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.FeedItem

// --------------------------------
// ML EVALUATION BRIDGE (8B-14)
// --------------------------------
//
// Adapter that materializes an evaluation-compatible
// AiPredictionRecord from an on-device ML inference result.
//
// This is a NEW, separate record: it never replaces or
// mutates the baseline AiPredictionRecord (8B-14 sections
// 21, 22). It explicitly identifies:
//
//   source       = "ML_MODEL"
//   modelVersion = the EXACT model version that produced it
//
// The baseline keeps its existing source semantics; the two
// records coexist so 8A can compare them on identical items.
//
// The category taxonomy is NOT modified here: the ML output
// maps onto the existing taxonomy through CategoryCatalog
// (section 40). The model confidence is carried VERBATIM and
// is not reinterpreted as heuristic/evidence confidence
// (section 12).

object MlEvaluationBridge {

    const val SOURCE_MODEL_ML = "ML_MODEL"

    /*
     * Materializes a comparison-ready AiPredictionRecord for
     * a SUCCESSFUL ML inference. Returns null when there is
     * no usable prediction (non-SUCCESS status or no primary
     * category): the baseline record remains the only record
     * in that case, and "no ML prediction" stays explicit.
     */
    fun toAiPredictionRecord(
        result: ModelInferenceResult,
        evaluationItemId: String
    ): AiPredictionRecord? {
        if (result.status != InferenceStatus.SUCCESS) {
            return null
        }
        val category = result.primaryCategory
            ?.let(CategoryCatalog::normalize)
            ?: return null

        val domain = CategoryCatalog.domainOf(category)

        return AiPredictionRecord(
            evaluationItemId = evaluationItemId,
            source = SOURCE_MODEL_ML,
            modelVersion = result.modelVersion,
            category = category,
            categoryDomain = domain,
            confidence = result.primaryConfidence,
            secondaryCategories = result.rankedPredictions
                .drop(1)
                .map { it.category }
                .mapNotNull(CategoryCatalog::normalize),
            categoryScores = result.rankedPredictions
                .mapNotNull { entry ->
                    CategoryCatalog.normalize(entry.category)?.let { normalized ->
                        normalized to entry.confidence
                    }
                }
                .toMap(),
            uncertaintyLevel = FeedItem.UNCERTAINTY_LOW,
            needsReview = false
        )
    }
}