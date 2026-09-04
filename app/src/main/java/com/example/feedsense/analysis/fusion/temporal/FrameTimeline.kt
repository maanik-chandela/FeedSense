package com.example.feedsense.analysis.fusion.temporal

import com.example.feedsense.analysis.fusion.FrameSignals

// --------------------------------
// FRAME TIMELINE (Milestone 8B-2)
// --------------------------------
//
// Deterministic, ordered representation of the frames in a
// FeedItem. Frames are sorted by timestamp ASC with stable
// secondary ordering for identical timestamps (8B-2 section 9).
//
// The timeline preserves temporal relationships and derives
// frame intervals (deltaTime) where timestamps permit (8B-2
// section 10). Missing timestamps are handled gracefully.

/**
 * A single entry on the timeline with derived temporal metadata.
 */
data class TimelineEntry(
    val index: Int,
    val frame: FrameSignals,
    val timestampMs: Long,
    val deltaFromPreviousMs: Long?,
    val isGap: Boolean,
    val isInformative: Boolean
) {
    val deltaTimeSeconds: Double?
        get() = deltaFromPreviousMs?.toDouble()?.div(1000.0)

    val durationSeconds: Double
        get() = (deltaFromPreviousMs?.toDouble() ?: 0.0) / 1000.0
}

/**
 * The complete ordered timeline for a FeedItem.
 */
data class FrameTimeline(
    val entries: List<TimelineEntry>,
    val totalDurationMs: Long,
    val frameCount: Int,
    val informativeFrameCount: Int,
    val missingTimestampCount: Int,
    val gapCount: Int
) {

    val totalDurationSeconds: Double
        get() = totalDurationMs.toDouble() / 1000.0

    val informativeEntries: List<TimelineEntry>
        get() = entries.filter { it.isInformative }

    companion object {

        /**
         * Builds a deterministic timeline from a list of frame
         * signals. Handles:
         *   - missing timestamps (assigned sequential fallback)
         *   - identical timestamps (stable secondary sort by
         *     frameId)
         *   - out-of-order insertion (sorts chronologically)
         *   - gaps exceeding the max gap threshold
         *
         * @param frames The raw frame signals, potentially unsorted.
         * @param config Temporal configuration.
         * @return A fully-ordered, gap-annotated timeline.
         */
        fun build(
            frames: List<FrameSignals>,
            config: TemporalConfig
        ): FrameTimeline {
            if (frames.isEmpty()) {
                return FrameTimeline(
                    entries = emptyList(),
                    totalDurationMs = 0,
                    frameCount = 0,
                    informativeFrameCount = 0,
                    missingTimestampCount = 0,
                    gapCount = 0
                )
            }

            val parsed = frames.mapIndexed { idx, frame ->
                val ms = parseTimestampMs(frame.timestamp, idx)
                ParsedFrame(frame, ms, idx)
            }

            // Stable sort: timestamp ASC, then frameId ASC for ties.
            val sorted = parsed.sortedWith(
                compareBy<ParsedFrame> { it.timestampMs }
                    .thenBy { it.frame.frameId }
            )

            // Determine informative status per frame.
            val informativeThreshold =
                config.minInformativeDurationSeconds

            val entries = mutableListOf<TimelineEntry>()
            var prevMs: Long? = null
            var missingTs = 0
            var gaps = 0

            for ((timelineIdx, pf) in sorted.withIndex()) {
                val delta = prevMs?.let { pf.timestampMs - it }
                val isGap = delta != null &&
                    delta > config.maxFrameGapSeconds * 1000
                if (isGap) gaps++

                if (pf.originalTimestamp == null) missingTs++

                val hasOcr = pf.frame.hasOcr
                val isInformative = hasOcr

                entries += TimelineEntry(
                    index = timelineIdx,
                    frame = pf.frame,
                    timestampMs = pf.timestampMs,
                    deltaFromPreviousMs = delta,
                    isGap = isGap,
                    isInformative = isInformative
                )

                prevMs = pf.timestampMs
            }

            val totalDuration = if (entries.size >= 2) {
                entries.last().timestampMs - entries.first().timestampMs
            } else {
                0L
            }

            return FrameTimeline(
                entries = entries,
                totalDurationMs = totalDuration,
                frameCount = entries.size,
                informativeFrameCount = entries.count { it.isInformative },
                missingTimestampCount = missingTs,
                gapCount = gaps
            )
        }

        /**
         * Parses a timestamp string into milliseconds. Supports
         * ISO-8601 like formats and plain numeric strings.
         * Returns null when the original was null/blank, and
         * uses the fallback index for ordering.
         */
        fun parseTimestampMs(
            timestamp: String?,
            fallbackIndex: Int
        ): Long {
            if (timestamp.isNullOrBlank()) {
                return fallbackIndex.toLong() * 1000
            }
            // Try plain numeric (epoch millis or seconds).
            val numeric = timestamp.toLongOrNull()
            if (numeric != null) {
                return if (numeric > 1_000_000_000_000) {
                    numeric // already millis
                } else {
                    numeric * 1000 // seconds -> millis
                }
            }
            // Try ISO-8601 parse.
            return try {
                val ldt = java.time.LocalDateTime.parse(timestamp)
                ldt.toInstant(java.time.ZoneOffset.UTC)
                    .toEpochMilli()
            } catch (_: Exception) {
                // Unparseable: use fallback index.
                fallbackIndex.toLong() * 1000
            }
        }
    }

    private data class ParsedFrame(
        val frame: FrameSignals,
        val timestampMs: Long,
        val originalIndex: Int
    ) {
        val originalTimestamp: String?
            get() = frame.timestamp.takeIf { it.isNotBlank() }
    }
}
