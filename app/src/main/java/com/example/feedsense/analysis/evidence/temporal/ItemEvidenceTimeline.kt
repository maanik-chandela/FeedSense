package com.example.feedsense.analysis.evidence.temporal

import com.example.feedsense.analysis.evidence.Evidence

/*
 * Milestone 8B-6.
 *
 * Item evidence timeline.
 *
 * A deterministic, chronologically ordered representation
 * of all evidence within a FeedItem's temporal window.
 *
 * This is the core data structure for multi-frame temporal
 * reasoning. It:
 *   - orders evidence chronologically
 *   - normalizes timestamps relative to item start
 *   - detects temporal gaps
 *   - tracks evidence persistence
 *   - preserves transition sequences
 *   - prevents semantic interpolation across gaps
 *
 * Design principles:
 *   - Deterministic: same input → same timeline
 *   - Stable ordering: timestamp → evidence identity
 *   - No invented data: gaps remain gaps
 *   - Bounded memory: only structured evidence retained
 *
 * Important:
 *   - Timeline does NOT classify content
 *   - Timeline does NOT make predictions
 *   - Timeline is a data structure for temporal reasoning
 *   - FeedItem boundaries remain authoritative (no
 *     automatic re-segmentation)
 */
data class ItemEvidenceTimeline(
    val sessionId: String,
    val feedItemId: String?,
    val itemStartTimeMs: Long,
    val itemDurationMs: Long,
    val points: List<TemporalEvidencePoint>,
    val coverage: EvidenceCoverage,
    val temporalGaps: List<TemporalGap>,
    val timelineVersion: String
) {
    /*
     * Total number of evidence points.
     */
    val pointCount: Int
        get() = points.size

    /*
     * Number of unique evidence sources.
     */
    val uniqueSourceCount: Int
        get() = points.map { it.evidenceIdentityHash }
            .distinct()
            .size

    /*
     * Evidence types present in this timeline.
     */
    val evidenceTypesPresent: Set<String>
        get() = points.map { it.evidenceType.name }
            .distinct()
            .toSet()

    /*
     * Whether any temporal gap was detected.
     */
    val hasGap: Boolean
        get() = temporalGaps.isNotEmpty()

    /*
     * Ordered list of unique categories supported by
     * evidence in this timeline.
     */
    val categoriesSupported: List<String>
        get() = points.flatMap { it.supportingCategories }
            .distinct()
            .sorted()

    /*
     * Ordered list of categories contradicted by
     * evidence in this timeline.
     */
    val categoriesContradicted: List<String>
        get() = points
            .flatMap { it.contradictingCategories }
            .distinct()
            .sorted()

    companion object {

        const val VERSION = "item-evidence-timeline-v1"

        /*
         * Builds a deterministic timeline from evidence
         * and item boundaries.
         *
         * @param sessionId Research session identifier.
         * @param feedItemId Item identifier (may be null
         *   during early processing).
         * @param itemStartTimeMs Item start in epoch ms.
         * @param itemDurationMs Item duration in ms.
         * @param evidence The raw evidence list.
         * @return A deterministic, ordered timeline.
         */
        fun build(
            sessionId: String,
            feedItemId: String?,
            itemStartTimeMs: Long,
            itemDurationMs: Long,
            evidence: List<Evidence>
        ): ItemEvidenceTimeline {

            val itemEndTimeMs =
                itemStartTimeMs + itemDurationMs

            // Contextualize evidence as timeline points.
            val points = evidence
                .filter { it.isUsable }
                .mapIndexed { index, evidence ->
                    val relativeTime =
                        (evidence.timestampMs -
                                itemStartTimeMs)
                            .coerceAtLeast(0)

                    TemporalEvidencePoint.fromEvidence(
                        evidence = evidence,
                        relativeTimeMs = relativeTime,
                        frameIndex = index,
                        frameId =
                            evidence.frameId ?: "frame_$index"
                    )
                }

            // Stable sort: relative time ASC, then
            // pointId ASC for deterministic tie-breaking.
            val sorted = points.sortedWith(
                compareBy<TemporalEvidencePoint> {
                    it.relativeTimeMs
                }.thenBy {
                    it.pointId
                }
            )

            // Detect temporal gaps.
            val gaps = detectGaps(sorted)

            // Compute coverage.
            val coverage =
                EvidenceCoverage.compute(sorted, itemDurationMs)

            return ItemEvidenceTimeline(
                sessionId = sessionId,
                feedItemId = feedItemId,
                itemStartTimeMs = itemStartTimeMs,
                itemDurationMs = itemDurationMs,
                points = sorted,
                coverage = coverage,
                temporalGaps = gaps,
                timelineVersion = VERSION
            )
        }

        /*
         * Detects temporal gaps in the evidence timeline.
         * A gap occurs when consecutive evidence points
         * are separated by more than the gap threshold.
         */
        private fun detectGaps(
            points: List<TemporalEvidencePoint>
        ): List<TemporalGap> {

            if (points.size < 2) return emptyList()

            val gaps = mutableListOf<TemporalGap>()

            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]

                val gapMs =
                    curr.relativeTimeMs - prev.relativeTimeMs

                if (gapMs > EvidenceCoverage.GAP_THRESHOLD_MS) {
                    gaps += TemporalGap(
                        gapId = "gap_${i - 1}_$i",
                        startRelativeMs =
                            prev.relativeTimeMs,
                        endRelativeMs =
                            curr.relativeTimeMs,
                        durationMs = gapMs,
                        beforePointId = prev.pointId,
                        afterPointId = curr.pointId,
                        beforeFrameIndex = prev.frameIndex,
                        afterFrameIndex = curr.frameIndex
                    )
                }
            }

            return gaps
        }
    }
}

/*
 * Represents a detected temporal gap in the evidence
 * timeline.
 *
 * Gaps are factual: the system did NOT observe content
 * during this period. The system must NOT interpolate
 * semantic evidence across gaps.
 */
data class TemporalGap(
    val gapId: String,
    val startRelativeMs: Long,
    val endRelativeMs: Long,
    val durationMs: Long,
    val beforePointId: String,
    val afterPointId: String,
    val beforeFrameIndex: Int,
    val afterFrameIndex: Int
) {
    val durationSeconds: Double
        get() = durationMs.toDouble() / 1000.0
}
