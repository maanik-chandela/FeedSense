package com.example.feedsense.analysis.fusion.temporal

// --------------------------------
// TEMPORAL PERSISTENCE (Milestone 8B-2)
// --------------------------------
//
// Measures how long and how consistently a category signal
// persists across the timeline. Persistence is measured by TIME,
// not frame count (8B-2 section 11), because capture rates can
// vary.

/**
 * Persistence levels for a category signal.
 */
enum class PersistenceLevel(val label: String) {
    NONE("NONE"),
    BRIEF("BRIEF"),
    MODERATE("MODERATE"),
    SUSTAINED("SUSTAINED"),
    DOMINANT("DOMINANT")
}

/**
 * The temporal support for a single category (8B-2 section 12).
 */
data class TemporalSupport(
    val category: String,
    val totalDurationMs: Long,
    val continuousDurationMs: Long,
    val informativeFrameCount: Int,
    val totalFrameCount: Int,
    val persistenceLevel: PersistenceLevel,
    val hasGaps: Boolean,
    val gapCount: Int
) {
    val totalDurationSeconds: Double
        get() = totalDurationMs.toDouble() / 1000.0

    val continuousDurationSeconds: Double
        get() = continuousDurationMs.toDouble() / 1000.0

    /**
     * Temporal support quality combining duration, continuity
     * and frame count (8B-2 section 47).
     *
     * Key design decision: a SINGLE isolated frame is inherently
     * weak temporal evidence regardless of how long the gap was
     * before it. The quality rewards SUSTAINED, repeated support:
     * a category present in N>=2 informative frames with
     * continuous duration is far more reliable than one observed
     * in a lone frame (8B-2 section 46).
     */
    val supportQuality: Double
        get() {
            if (informativeFrameCount == 0) return 0.0

            // Sustained multi-frame support is the cornerstone.
            // A single-frame observation is a WEAK temporal signal.
            val multiplicity =
                (informativeFrameCount - 1).toDouble()

            val durationFactor =
                (totalDurationSeconds / 15.0).coerceIn(0.0, 1.0)
            val frameFactor =
                (multiplicity / 4.0).coerceIn(0.0, 1.0)
            val continuityFactor = when {
                hasGaps -> 0.6
                informativeFrameCount >= 2 -> 1.0
                else -> 0.4
            }
            val singleFramePenalty =
                if (informativeFrameCount >= 2) 1.0 else 0.35

            return (
                0.5 * durationFactor +
                    0.3 * frameFactor +
                    0.2 * continuityFactor
                ) * singleFramePenalty
                .coerceIn(0.0, 1.0)
        }
}

object TemporalPersistence {

    /**
     * Calculates temporal support for a category given the
     * timeline and the per-frame category assignment.
     *
     * @param timeline The ordered frame timeline.
     * @param categoryFrames Map of frame index -> assigned category.
     * @param category The category to measure.
     * @param config Temporal configuration.
     */
    fun calculate(
        timeline: FrameTimeline,
        categoryFrames: Map<Int, String>,
        category: String,
        config: TemporalConfig
    ): TemporalSupport {
        val matchingEntries = timeline.entries.filter { entry ->
            categoryFrames[entry.index] == category
        }

        if (matchingEntries.isEmpty()) {
            return TemporalSupport(
                category = category,
                totalDurationMs = 0,
                continuousDurationMs = 0,
                informativeFrameCount = 0,
                totalFrameCount = 0,
                persistenceLevel = PersistenceLevel.NONE,
                hasGaps = false,
                gapCount = 0
            )
        }

        val totalDuration = if (matchingEntries.size >= 2) {
            matchingEntries.last().timestampMs -
                matchingEntries.first().timestampMs
        } else {
            // Single frame: estimate duration from delta to next
            // or from delta from previous, or 0.
            matchingEntries.first().deltaFromPreviousMs ?: 0L
        }

        // Find the longest continuous run of this category.
        val sortedIndices = matchingEntries.map { it.index }.sorted()
        var maxContinuous = 1L
        var currentRun = 1L
        var gaps = 0

        for (i in 1 until sortedIndices.size) {
            val prevEntry = timeline.entries[sortedIndices[i - 1]]
            val currEntry = timeline.entries[sortedIndices[i]]

            // Check if there's a gap in the timeline or
            // intervening frames with different categories.
            val intervening = (sortedIndices[i] - sortedIndices[i - 1]) > 1
            val gapInTimeline = currEntry.isGap

            if (intervening || gapInTimeline) {
                // Check if the intervening frames are also
                // this category.
                val allSame = (sortedIndices[i - 1] + 1 until sortedIndices[i])
                    .all { categoryFrames[it] == category }

                if (!allSame || gapInTimeline) {
                    maxContinuous = maxOf(maxContinuous, currentRun)
                    currentRun = 1L
                    gaps++
                } else {
                    currentRun++
                }
            } else {
                currentRun++
            }
        }
        maxContinuous = maxOf(maxContinuous, currentRun)

        val continuousDuration = if (maxContinuous >= 2) {
            val runEntries = matchingEntries.take(maxContinuous.toInt())
            runEntries.last().timestampMs - runEntries.first().timestampMs
        } else {
            matchingEntries.first().deltaFromPreviousMs ?: 0L
        }

        val persistenceLevel = when {
            totalDuration.toDouble() / 1000.0 >=
                config.strongPersistenceDurationSeconds * 3 ->
                PersistenceLevel.DOMINANT
            totalDuration.toDouble() / 1000.0 >=
                config.strongPersistenceDurationSeconds ->
                PersistenceLevel.SUSTAINED
            totalDuration.toDouble() / 1000.0 >=
                config.moderatePersistenceDurationSeconds ->
                PersistenceLevel.MODERATE
            matchingEntries.isNotEmpty() ->
                PersistenceLevel.BRIEF
            else ->
                PersistenceLevel.NONE
        }

        return TemporalSupport(
            category = category,
            totalDurationMs = totalDuration.coerceAtLeast(0),
            continuousDurationMs = continuousDuration.coerceAtLeast(0),
            informativeFrameCount = matchingEntries.count { it.isInformative },
            totalFrameCount = matchingEntries.size,
            persistenceLevel = persistenceLevel,
            hasGaps = gaps > 0,
            gapCount = gaps
        )
    }

    /**
     * Calculates temporal support for ALL categories found in
     * the timeline, returning them sorted by support quality.
     */
    fun calculateAll(
        timeline: FrameTimeline,
        categoryFrames: Map<Int, String>,
        config: TemporalConfig
    ): List<TemporalSupport> {
        val categories = categoryFrames.values.distinct()
        return categories.map { cat ->
            calculate(timeline, categoryFrames, cat, config)
        }.sortedByDescending { it.supportQuality }
    }
}
