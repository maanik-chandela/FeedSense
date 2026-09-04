package com.example.feedsense.analysis.evidence.temporal

import com.example.feedsense.analysis.evidence.Evidence
import com.example.feedsense.analysis.evidence.EvidenceFusionEngine
import com.example.feedsense.analysis.evidence.EvidenceFusionResult

/*
 * Milestone 8B-6.
 *
 * Item-level predictor.
 *
 * Provides two conceptual pathways for interpreting
 * a FeedItem's evidence:
 *
 *   BASELINE:
 *     Single-frame / current heuristic interpretation.
 *     Uses the existing 8B-5 EvidenceFusionEngine on
 *     the representative frame only.
 *
 *   TEMPORAL:
 *     Multi-frame evidence-aware interpretation.
 *     Uses 8B-6 TemporalEvidenceFusionEngine to reason
 *     across the item's full temporal window.
 *
 * Design:
 *   - Both pathways are independently accessible
 *   - Neither pathway modifies the other
 *   - Historical baseline predictions remain reproducible
 *   - The temporal pathway is an improvement candidate,
 *     not a replacement
 *
 * Important:
 *   - Do NOT claim the temporal system is more accurate
 *     until measured on a real benchmark
 *   - The existing classifier remains the production
 *     authority
 *   - This class is an adapter, not a parallel system
 *
 * Limitations:
 *   - Baseline uses only the representative frame
 *   - Temporal uses all available evidence
 *   - Neither pathway runs a neural model
 *   - Neither pathway calls cloud AI
 */
class ItemLevelPredictor(
    private val baseEngine: EvidenceFusionEngine =
        EvidenceFusionEngine(),
    private val temporalEngine:
        TemporalEvidenceFusionEngine =
        TemporalEvidenceFusionEngine()
) {

    /*
     * Produces a baseline prediction using only the
     * representative frame evidence.
     *
     * This is the existing single-frame interpretation
     * pathway. It remains available for comparison.
     *
     * @param evidence Evidence from the representative
     *   frame.
     * @return EvidenceFusionResult from the baseline
     *   pathway.
     */
    fun baselinePredict(
        evidence: List<Evidence>
    ): EvidenceFusionResult {
        return baseEngine.fuse(evidence)
    }

    /*
     * Produces a temporal prediction using evidence
     * from across the item's temporal window.
     *
     * This is the new multi-frame interpretation
     * pathway introduced in 8B-6.
     *
     * @param sessionId Research session identifier.
     * @param feedItemId Item identifier.
     * @param itemStartTimeMs Item start in epoch ms.
     * @param itemDurationMs Item duration in ms.
     * @param evidence Evidence from all available frames.
     * @param representativeFrameCategory Category from
     *   the representative frame (may be null).
     * @return ItemEvidenceSnapshot from the temporal
     *   pathway.
     */
    fun temporalPredict(
        sessionId: String,
        feedItemId: String?,
        itemStartTimeMs: Long,
        itemDurationMs: Long,
        evidence: List<Evidence>,
        representativeFrameCategory: String? = null
    ): ItemEvidenceSnapshot {
        return temporalEngine.fuse(
            sessionId = sessionId,
            feedItemId = feedItemId,
            itemStartTimeMs = itemStartTimeMs,
            itemDurationMs = itemDurationMs,
            evidence = evidence,
            representativeFrameCategory =
                representativeFrameCategory
        )
    }

    /*
     * Produces both baseline and temporal predictions
     * for comparison.
     *
     * This is useful for research evaluation where both
     * pathways need to be compared on the same data.
     *
     * @param sessionId Research session identifier.
     * @param feedItemId Item identifier.
     * @param itemStartTimeMs Item start in epoch ms.
     * @param itemDurationMs Item duration in ms.
     * @param evidence All evidence for the item.
     * @param representativeFrameEvidence Evidence from
     *   the representative frame specifically.
     * @param representativeFrameCategory Category from
     *   the representative frame.
     * @return ComparisonResult with both predictions.
     */
    fun compare(
        sessionId: String,
        feedItemId: String?,
        itemStartTimeMs: Long,
        itemDurationMs: Long,
        evidence: List<Evidence>,
        representativeFrameEvidence: List<Evidence>,
        representativeFrameCategory: String? = null
    ): ComparisonResult {

        val baselineResult =
            baselinePredict(representativeFrameEvidence)

        val temporalResult =
            temporalPredict(
                sessionId = sessionId,
                feedItemId = feedItemId,
                itemStartTimeMs = itemStartTimeMs,
                itemDurationMs = itemDurationMs,
                evidence = evidence,
                representativeFrameCategory =
                    representativeFrameCategory
            )

        // Compare primary categories.
        val categoriesAgree =
            baselineResult.topCategory ==
                    temporalResult.primaryCategory

        return ComparisonResult(
            baseline = baselineResult,
            temporal = temporalResult,
            categoriesAgree = categoriesAgree,
            baselinePrimary =
                baselineResult.topCategory,
            temporalPrimary =
                temporalResult.primaryCategory
        )
    }
}

/*
 * Comparison result from running both baseline and
 * temporal prediction pathways.
 *
 * This is for research evaluation only. Do not use for
 * production decisions without benchmark validation.
 */
data class ComparisonResult(
    val baseline: EvidenceFusionResult,
    val temporal: ItemEvidenceSnapshot,
    val categoriesAgree: Boolean,
    val baselinePrimary: String?,
    val temporalPrimary: String?
) {
    /*
     * The temporal pathway's primary category, or the
     * baseline's if temporal is null.
     */
    val recommendedCategory: String?
        get() = temporal.primaryCategory
            ?: baseline.topCategory

    /*
     * Confidence in the temporal prediction.
     */
    val temporalSupportScore: Double
        get() = temporal.primarySupportScore
}
