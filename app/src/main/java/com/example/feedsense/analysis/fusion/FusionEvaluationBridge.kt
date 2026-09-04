package com.example.feedsense.analysis.fusion

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.FeedItem

// --------------------------------
// EVALUATION BRIDGE (Milestone 8B-1)
// --------------------------------
//
// Converts a FusionPrediction into an immutable AiPredictionRecord
// so the existing 8A evaluation engine can compare LEGACY vs
// FUSION predictions on identical items without overwriting the
// legacy prediction. The fusion record always carries:
//
//   modelVersion = local-fusion-v1
//   source       = LOCAL
//
// so it is distinguishable from the legacy model row and both are
// retained side by side (8B-1 section 4/55/56).

object FusionEvaluationBridge {

    /**
     * Materializes a comparison-ready AiPredictionRecord.
     *
     * @param prediction   the fusion output for one evaluation item
     * @param evaluationItemId the evaluation item this applies to
     * @param feedItemId   the source feed item (provenance, optional)
     */
    fun toAiPredictionRecord(
        prediction: FusionPrediction,
        evaluationItemId: String,
        feedItemId: String? = null
    ): AiPredictionRecord {
        val category = prediction.primaryCategory
            ?.let(CategoryCatalog::normalize)

        val domain = category?.let(CategoryCatalog::domainOf)

        return AiPredictionRecord(
            evaluationItemId = evaluationItemId,
            source = FeedItem.SOURCE_AI,
            modelVersion = prediction.modelVersion,
            category = category,
            categoryDomain = domain,
            confidence =
                if (prediction.hasDecidedPrimary) prediction.confidence
                else prediction.confidence,
            secondaryCategories = prediction.secondaryCategories,
            categoryScores = prediction.trace.candidates
                .associate { it.category to it.score },
            platform = prediction.platform,
            contentType = prediction.contentType,
            interactionSignals = emptyList(),
            topic = prediction.topic,
            tone = prediction.tone,
            uncertaintyLevel = uncertaintyToFeedItem(prediction.uncertainty),
            needsReview = prediction.uncertainty != Uncertainty.CONFIDENT,
            feedItemId = feedItemId
        )
    }

    private fun uncertaintyToFeedItem(u: Uncertainty): String {
        return when (u) {
            Uncertainty.CONFIDENT -> FeedItem.UNCERTAINTY_LOW
            Uncertainty.LOW_CONFIDENCE -> FeedItem.UNCERTAINTY_MEDIUM
            Uncertainty.AMBIGUOUS -> FeedItem.UNCERTAINTY_HIGH
            Uncertainty.INSUFFICIENT_EVIDENCE -> FeedItem.UNCERTAINTY_HIGH
            Uncertainty.CONFLICTING_EVIDENCE -> FeedItem.UNCERTAINTY_HIGH
        }
    }
}
