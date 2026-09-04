package com.example.feedsense.analysis.fusion.temporal

// --------------------------------
// TEMPORAL CONSISTENCY (Milestone 8B-2)
// --------------------------------
//
// Deterministic assessment of whether the evidence across the
// timeline is internally consistent. Missing frames are NOT
// treated as contradictory evidence (8B-2 section 14).

object TemporalConsistencyAssessor {

    /**
     * Assesses temporal consistency for a category given the
     * timeline and per-frame category assignments.
     *
     * @param timeline The ordered frame timeline.
     * @param categoryFrames Map of frame index -> assigned category.
     * @param leadingCategory The dominant category to assess.
     * @param config Temporal configuration.
     * @return Temporal consistency level.
     */
    fun assess(
        timeline: FrameTimeline,
        categoryFrames: Map<Int, String>,
        leadingCategory: String,
        config: TemporalConfig
    ): TemporalConsistency {
        val informativeEntries = timeline.informativeEntries
        if (informativeEntries.isEmpty()) {
            return TemporalConsistency.NONE
        }

        val informativeIndices = informativeEntries.map { it.index }
        val totalInformative = informativeIndices.size

        val supportingCount = informativeIndices.count { idx ->
            categoryFrames[idx] == leadingCategory
        }

        val absentCount = informativeIndices.count { idx ->
            categoryFrames[idx] == null
        }

        val contradictingCount = informativeIndices.count { idx ->
            val cat = categoryFrames[idx]
            cat != null && cat != leadingCategory
        }

        // Missing frames do not count as contradictions.
        val effectiveTotal = totalInformative - absentCount
        if (effectiveTotal == 0) {
            return TemporalConsistency.NONE
        }

        val supportRatio = supportingCount.toDouble() / effectiveTotal

        // Check for contradictions from different content domains.
        val contradictingCategories = informativeIndices
            .mapNotNull { categoryFrames[it] }
            .filter { it != leadingCategory }
            .distinct()

        val hasSharpContradiction = contradictingCount > 0 &&
            contradictingCount.toDouble() / effectiveTotal >
            config.transitionDisagreementRatio

        return when {
            hasSharpContradiction ->
                TemporalConsistency.CONFLICTING
            supportRatio >= config.strongConsistencyRatio ->
                TemporalConsistency.STRONG
            supportRatio >= config.moderateConsistencyRatio ->
                TemporalConsistency.MODERATE
            supportRatio > 0.0 ->
                TemporalConsistency.WEAK
            else ->
                TemporalConsistency.NONE
        }
    }

    /**
     * Determines category stability from the trajectory (8B-2
     * section 24).
     */
    fun assessStability(
        trajectory: CategoryTrajectory,
        totalTimelineDurationMs: Long,
        config: TemporalConfig
    ): CategoryStability {
        if (trajectory.informativeFrameCount < 2) {
            return CategoryStability.UNKNOWN
        }

        val durationRatio = if (totalTimelineDurationMs > 0) {
            trajectory.supportDurationMs.toDouble() /
                totalTimelineDurationMs
        } else {
            0.0
        }

        val scoreVariance = if (trajectory.scoreTrajectory.size >= 2) {
            val mean = trajectory.scoreTrajectory.average()
            trajectory.scoreTrajectory
                .map { (it - mean) * (it - mean) }
                .average()
        } else {
            0.0
        }

        return when {
            durationRatio >= 0.9 && scoreVariance < 0.01 ->
                CategoryStability.STABLE
            durationRatio >= 0.7 && scoreVariance < 0.05 ->
                CategoryStability.MOSTLY_STABLE
            durationRatio < 0.5 || scoreVariance > 0.1 ->
                CategoryStability.UNSTABLE
            trajectory.continuousSupport &&
                durationRatio >= 0.5 ->
                CategoryStability.MOSTLY_STABLE
            else ->
                CategoryStability.CHANGING
        }
    }

    /**
     * Calculates temporal conflict level (8B-2 section 44).
     */
    fun assessConflictLevel(
        timeline: FrameTimeline,
        categoryFrames: Map<Int, String>,
        config: TemporalConfig
    ): TemporalConflictLevel {
        val informativeIndices = timeline.informativeEntries
            .map { it.index }
        if (informativeIndices.size < 2) {
            return TemporalConflictLevel.NONE
        }

        val categories = informativeIndices
            .mapNotNull { categoryFrames[it] }
            .distinct()

        if (categories.size <= 1) {
            return TemporalConflictLevel.NONE
        }

        // Count frames per category.
        val perCategory = categories.associateWith { cat ->
            informativeIndices.count { categoryFrames[it] == cat }
        }

        val total = perCategory.values.sum().toDouble()
        val sorted = perCategory.values.sortedDescending()

        val leaderRatio = sorted[0] / total
        val runnerUpRatio = if (sorted.size >= 2) sorted[1] / total else 0.0

        return when {
            leaderRatio >= 0.85 -> TemporalConflictLevel.NONE
            leaderRatio >= 0.65 -> TemporalConflictLevel.LOW
            leaderRatio >= 0.5 -> TemporalConflictLevel.MEDIUM
            else -> TemporalConflictLevel.HIGH
        }
    }
}
