package com.example.feedsense.analysis.evidence.decision

import com.example.feedsense.analysis.evidence.temporal.ItemEvidenceSnapshot
import com.example.feedsense.model.FeedItem

/*
 * Milestone 8B-7.
 *
 * Read-only evidence-aware adapter.
 *
 * Bridges a production experience into the experimental
 * 8B evidence-aware decision path WITHOUT mutating the
 * production baseline.
 *
 * Inputs:
 *   - an existing FeedItem (authoritative baseline)
 *   - an 8B-6 ItemEvidenceSnapshot for that item
 *
 * Output:
 *   - an immutable EvidenceAwareComparison
 *
 * Guarantees (read-only):
 *   - The FeedItem is never modified.
 *   - No Room entity is written.
 *   - No baseline prediction, observation, session state,
 *     or historical record is changed.
 *   - No stored frame/screen content (screenshots, OCR,
 *     captions, messages, notifications) is exported or
 *     persisted. Only evidence IDs / category labels are
 *     referenced.
 *
 * The adapter intentionally takes an already-computed
 * 8B-6 snapshot so it does NOT re-run fusion or couple to
 * the capture pipeline. It only runs the 8B-7 decision.
 *
 * Architectural role:
 *
 *   FeedItem baseline
 *        │
 *        ├──────────────► baseline evaluation
 *        │
 *        └──► ReadOnlyEvidenceAwareAdapter
 *                  ↓
 *             8B-7 decision
 *                  ↓
 *         EvidenceAwareComparison
 *                  ↓
 *         DecisionEvaluationBridge
 *                  ↓
 *             8A evaluation
 *
 * Future extensibility:
 *   The evaluate() signature stays stable so additional
 *   experimental predictors (temporal fusion, mobile ML,
 *   quantized multimodal, etc.) can be introduced as new
 *   result types without reshaping the comparison surface.
 */
class ReadOnlyEvidenceAwareAdapter(
    private val decisionEngine: ItemDecisionEngine =
        ItemDecisionEngine,
    private val config: ItemDecisionConfig =
        ItemDecisionConfig.DEFAULT
) {

    /**
     * Derives an evidence-aware comparison for an item,
     * read-only.
     *
     * @param feedItem The authoritative baseline FeedItem.
     *   It is used only as an immutable input and is never
     *   modified.
     * @param snapshot The 8B-6 ItemEvidenceSnapshot for the
     *   same item.
     * @return An immutable [EvidenceAwareComparison].
     */
    fun evaluate(
        feedItem: FeedItem,
        snapshot: ItemEvidenceSnapshot
    ): EvidenceAwareComparison {

        // --------------------------------
        // BASELINE (read verbatim)
        // --------------------------------

        val baselineCategory = feedItem.category
        val baselineConfidence = feedItem.confidence
        val baselineWasUnknown =
            baselineCategory == null

        // --------------------------------
        // EXPERIMENTAL 8B-7 DECISION
        // --------------------------------

        val decision = decisionEngine.decide(
            snapshot = snapshot,
            config = config
        )

        val evidenceAwareCategory =
            decision.primaryCategory
        val evidenceAwareWasUnknown =
            evidenceAwareCategory == null

        // --------------------------------
        // COMPARISON (descriptive, not correctness)
        // --------------------------------

        val categoryChanged =
            baselineCategory != null &&
                evidenceAwareCategory != null &&
                baselineCategory != evidenceAwareCategory

        return EvidenceAwareComparison(
            feedItemId = feedItem.id,
            sessionId = feedItem.sessionId,
            baselineCategory = baselineCategory,
            baselineConfidence = baselineConfidence,
            baselineContentType = feedItem.contentType,
            baselineSkipped = feedItem.skipped,
            baselineUncertaintyLevel =
                feedItem.uncertaintyLevel,
            baselineNeedsReview = feedItem.needsReview,
            baselineModelVersion = feedItem.modelVersion,
            evidenceAwareCategory = evidenceAwareCategory,
            evidenceAwareSupport =
                if (decision.abstained) {
                    null
                } else {
                    decision.supportScore
                },
            decisionState = decision.decisionState,
            uncertainty = decision.uncertainty,
            evidenceAwareSecondaryCategory =
                decision.secondaryCategory,
            evidenceAwareSourceDiversity =
                decision.sourceDiversity,
            categoryChanged = categoryChanged,
            baselineWasUnknown = baselineWasUnknown,
            evidenceAwareWasUnknown = evidenceAwareWasUnknown,
            fusionVersion = decision.fusionVersion,
            decisionVersion = decision.decisionVersion
        )
    }
}
