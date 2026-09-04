package com.example.feedsense.analysis.fusion.temporal

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.analysis.fusion.FusionPrediction
import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.FeedItem

// --------------------------------
// TEMPORAL EVALUATION BRIDGE (Milestone 8B-2)
// --------------------------------
//
// Converts a TemporalFusionPrediction into an immutable
// AiPredictionRecord so the existing 8A evaluation engine can
// compare LEGACY vs FUSION vs TEMPORAL predictions on identical
// items. The temporal record carries:
//
//   modelVersion = local-fusion-v1-temporal
//   source       = AI
//
// (8B-2 section 72/82).

object TemporalEvaluationBridge {

    /**
     * Materializes a comparison-ready AiPredictionRecord from
     * a temporal prediction.
     */
    fun toAiPredictionRecord(
        prediction: TemporalFusionPrediction,
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
            confidence = prediction.confidence,
            secondaryCategories = prediction.secondaryCategories,
            categoryScores = prediction.categoryTrajectories
                .map { (key, traj) -> key to traj.averageScore }
                .toMap(),
            platform = prediction.platform,
            contentType = prediction.contentType,
            interactionSignals = emptyList(),
            topic = prediction.topic,
            tone = prediction.tone,
            uncertaintyLevel = uncertaintyToLevel(prediction.uncertainty),
            needsReview = prediction.uncertainty != "CONFIDENT",
            feedItemId = feedItemId
        )
    }

    /**
     * Converts a temporal prediction to a base FusionPrediction
     * for backward compatibility with 8B-1 evaluation code.
     */
    fun toFusionPrediction(
        prediction: TemporalFusionPrediction
    ): FusionPrediction {
        return FusionPrediction(
            primaryCategory = prediction.primaryCategory,
            secondaryCategories = prediction.secondaryCategories,
            confidence = prediction.confidence,
            band = com.example.feedsense.analysis.fusion.ConfidenceBand
                .fromConfidence(
                    prediction.confidence,
                    com.example.feedsense.analysis.fusion.FusionConfig.DEFAULT
                ),
            uncertainty = com.example.feedsense.analysis.fusion.Uncertainty
                .valueOf(prediction.uncertainty),
            topic = prediction.topic,
            tone = prediction.tone,
            contentType = prediction.contentType,
            platform = prediction.platform,
            interactionState = prediction.interactionState,
            modelVersion = prediction.modelVersion,
            configVersion = prediction.configVersion,
            modelState = prediction.modelState,
            trace = com.example.feedsense.analysis.fusion.FusionTrace(
                framesConsidered = prediction.trace.framesConsidered,
                perProviderStrength = emptyList(),
                candidates = prediction.categoryTrajectories
                    .map { (cat, traj) ->
                        com.example.feedsense.analysis.fusion
                            .CandidateTraceEntry(
                                category = cat,
                                score = traj.averageScore,
                                frameAgreement = traj.frameCount
                            )
                    },
                temporalAgreement = prediction.trace.temporalConsistency,
                finalCategory = prediction.primaryCategory,
                confidenceBand = "UNKNOWN",
                uncertainty = prediction.uncertainty,
                informationQuality = prediction.trace.informationQuality,
                modelVersion = prediction.modelVersion
            )
        )
    }

    private fun uncertaintyToLevel(u: String): String {
        return when (u) {
            "CONFIDENT" -> FeedItem.UNCERTAINTY_LOW
            "LOW_CONFIDENCE" -> FeedItem.UNCERTAINTY_MEDIUM
            "AMBIGUOUS" -> FeedItem.UNCERTAINTY_HIGH
            "INSUFFICIENT_EVIDENCE" -> FeedItem.UNCERTAINTY_HIGH
            "CONFLICTING_EVIDENCE" -> FeedItem.UNCERTAINTY_HIGH
            else -> FeedItem.UNCERTAINTY_HIGH
        }
    }
}
