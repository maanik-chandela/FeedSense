package com.example.feedsense.analysis.fusion.temporal

// --------------------------------
// TEMPORAL TRANSITION (Milestone 8B-2)
// --------------------------------
//
// Detects when content itself changes category over time (true
// transition) versus when evidence merely conflicts about the
// same content (conflict). These are fundamentally different
// concepts (8B-2 section 52).

object TemporalTransitionDetector {

    /**
     * Detects category transitions and segment boundaries in the
     * timeline.
     *
     * @param timeline The ordered frame timeline.
     * @param categoryFrames Map of frame index -> assigned category.
     * @param config Temporal configuration.
     * @return List of detected transitions and segment boundaries.
     */
    fun detect(
        timeline: FrameTimeline,
        categoryFrames: Map<Int, String>,
        config: TemporalConfig
    ): TransitionResult {
        if (timeline.entries.size < 2) {
            return TransitionResult(
                transitions = emptyList(),
                segmentBoundaries = emptyList(),
                transitionDetected = false
            )
        }

        val transitions = mutableListOf<TemporalTransition>()
        val segmentBoundaries = mutableListOf<Int>()

        var prevCategory: String? = null
        var consecutiveNewCategory = 0
        var newCategoryStart: Int? = null
        var newCategoryName: String? = null

        for (entry in timeline.entries) {
            val currentCategory = categoryFrames[entry.index]

            if (currentCategory != null && currentCategory != prevCategory) {
                if (prevCategory != null &&
                    currentCategory != newCategoryName
                ) {
                    // Category changed.
                    consecutiveNewCategory++
                    if (consecutiveNewCategory == 1) {
                        newCategoryStart = entry.index
                        newCategoryName = currentCategory
                    }

                    // Check if this meets the transition threshold.
                    if (consecutiveNewCategory >=
                        config.transitionMinConsecutiveFrames
                    ) {
                        val signals = buildTransitionSignals(
                            timeline, entry, categoryFrames
                        )

                        transitions += TemporalTransition(
                            fromCategory = prevCategory,
                            toCategory = currentCategory,
                            atFrameIndex = entry.index,
                            atTimestampMs = entry.timestampMs,
                            isSharp = entry.isGap ||
                                (entry.deltaTimeSeconds
                                    ?: Double.MAX_VALUE) >
                                config.maxFrameGapSeconds * 0.5,
                            signals = signals
                        )

                        segmentBoundaries += entry.index
                    }
                } else if (currentCategory == newCategoryName) {
                    consecutiveNewCategory++
                }
            } else if (currentCategory == null) {
                // Missing category: reset the consecutive counter
                // but don't count as a transition.
                consecutiveNewCategory = 0
                newCategoryStart = null
                newCategoryName = null
            } else if (currentCategory == prevCategory) {
                // Same category: reset consecutive counter for new
                // category tracking.
                consecutiveNewCategory = 0
                newCategoryStart = null
                newCategoryName = null
            }

            if (currentCategory != null) {
                prevCategory = currentCategory
            }
        }

        return TransitionResult(
            transitions = transitions,
            segmentBoundaries = segmentBoundaries.distinct().sorted(),
            transitionDetected = transitions.isNotEmpty()
        )
    }

    /**
     * Identifies possible transition frames (8B-2 section 16).
     * A transition frame is one where evidence changes abruptly.
     */
    fun identifyTransitionFrames(
        timeline: FrameTimeline,
        categoryFrames: Map<Int, String>,
        config: TemporalConfig
    ): Set<Int> {
        val transitionFrames = mutableSetOf<Int>()

        for (entry in timeline.entries) {
            val currentCat = categoryFrames[entry.index]
            val prevCat = timeline.entries
                .firstOrNull { it.index == entry.index - 1 }
                ?.let { categoryFrames[it.index] }

            val nextCat = timeline.entries
                .firstOrNull { it.index == entry.index + 1 }
                ?.let { categoryFrames[it.index] }

            // Frame is a transition candidate if:
            // 1. It has no category (blank/loading/overlay)
            val noCategory = currentCat == null
            // 2. It differs from both neighbors
            val differsFromNeighbors = prevCat != null &&
                nextCat != null &&
                currentCat != prevCat &&
                currentCat != nextCat &&
                prevCat == nextCat
            // 3. It's a gap frame
            val isGap = entry.isGap

            if (noCategory || differsFromNeighbors || isGap) {
                transitionFrames += entry.index
            }
        }

        return transitionFrames
    }

    private fun buildTransitionSignals(
        timeline: FrameTimeline,
        entry: TimelineEntry,
        categoryFrames: Map<Int, String>
    ): List<String> {
        val signals = mutableListOf<String>()

        if (entry.isGap) {
            signals += "TIMELINE_GAP"
        }

        val prevEntry = timeline.entries
            .firstOrNull { it.index == entry.index - 1 }
        if (prevEntry != null) {
            val prevOcr = prevEntry.frame.ocrText
            val currOcr = entry.frame.ocrText
            if (!prevOcr.isNullOrBlank() && currOcr.isNullOrBlank()) {
                signals += "OCR_DISAPPEARANCE"
            }
            if (prevOcr.isNullOrBlank() && !currOcr.isNullOrBlank()) {
                signals += "OCR_APPEARANCE"
            }
        }

        val deltaSec = entry.deltaTimeSeconds
        if (deltaSec != null && deltaSec > 5.0) {
            signals += "LARGE_TIME_GAP"
        }

        val prevPlatform = prevEntry?.frame?.platform
        val currPlatform = entry.frame.platform
        if (prevPlatform != null && currPlatform != null &&
            prevPlatform != currPlatform
        ) {
            signals += "PLATFORM_CHANGE"
        }

        return signals
    }
}

data class TransitionResult(
    val transitions: List<TemporalTransition>,
    val segmentBoundaries: List<Int>,
    val transitionDetected: Boolean
)
