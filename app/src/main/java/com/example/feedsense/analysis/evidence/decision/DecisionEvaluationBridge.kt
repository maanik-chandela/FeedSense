package com.example.feedsense.analysis.evidence.decision

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.FeedItem

/*
 * Milestone 8B-7.
 *
 * Decision evaluation bridge.
 *
 * Converts an evidence-aware ItemPredictionResult into an
 * immutable AiPredictionRecord so the existing 8A
 * evaluation engine can compare the EVIDENCE-AWARE
 * decision on identical items.
 *
 * The record:
 *   - modelVersion = the 8B-6 fusion version (evidence
 *     model provenance)
 *   - source       = AI
 *   - uncertaintyLevel = mapped from UncertaintyState
 *   - needsReview  = derived from decision state (any
 *     non-DECIDED-LOW state implies review)
 *
 * Important:
 *   - supportScore is mapped to confidence ONLY as a
 *     comparison carrier for the evaluation layer. It is
 *     NOT declared a calibrated probability. Downstream
 *     evaluation must treat it as evidence support.
 *   - The baseline AiPredictionRecord (existing
 *     classifier) is untouched; this bridge only ever
 *     creates NEW records for the evidence-aware path.
 */
object DecisionEvaluationBridge {

    private const val SOURCE_EVIDENCE_AWARE =
        "AI_EVIDENCE_AWARE"

    /**
     * Materializes a comparison-ready AiPredictionRecord
     * from an evidence-aware decision.
     */
    fun toAiPredictionRecord(
        decision: ItemPredictionResult,
        evaluationItemId: String
    ): AiPredictionRecord {
        val category = decision.primaryCategory
            ?.let(CategoryCatalog::normalize)

        val domain = category
            ?.let(CategoryCatalog::domainOf)

        return AiPredictionRecord(
            evaluationItemId = evaluationItemId,
            source = SOURCE_EVIDENCE_AWARE,
            modelVersion = modelVersionOf(decision),
            category = category,
            categoryDomain = domain,
            confidence = decision.supportScore,
            secondaryCategories =
                listOfNotNull(decision.secondaryCategory),
            categoryScores = decision
                .candidateCategories
                .associate {
                    it.category to it.totalSupport
                },
            platform = null,
            contentType = null,
            interactionSignals = decision.uncertaintyReasons,
            topic = null,
            tone = null,
            uncertaintyLevel = uncertaintyToLevel(
                decision.uncertainty
            ),
            needsReview = needsReviewOf(decision),
            feedItemId = decision.feedItemId
        )
    }

    private fun modelVersionOf(
        decision: ItemPredictionResult
    ): String {
        return listOf(
            decision.modelVersion,
            decision.decisionVersion
        )
            .filter { it.isNotBlank() }
            .joinToString("/")
    }

    /*
     * Maps the evidence-aware decision state to the
     * FeedItem uncertainty level vocabulary used by 8A.
     */
    private fun needsReviewOf(
        decision: ItemPredictionResult
    ): Boolean {
        return when (decision.decisionState) {
            DecisionState.DECIDED ->
                decision.uncertainty !=
                    UncertaintyState.LOW
            else -> true
        }
    }

    private fun uncertaintyToLevel(
        u: UncertaintyState
    ): String {
        return when (u) {
            UncertaintyState.LOW ->
                FeedItem.UNCERTAINTY_LOW
            UncertaintyState.MODERATE ->
                FeedItem.UNCERTAINTY_MEDIUM
            UncertaintyState.HIGH ->
                FeedItem.UNCERTAINTY_HIGH
            UncertaintyState.UNRESOLVED ->
                FeedItem.UNCERTAINTY_HIGH
        }
    }
}
