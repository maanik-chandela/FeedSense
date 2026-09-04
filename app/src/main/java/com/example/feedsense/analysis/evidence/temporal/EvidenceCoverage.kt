package com.example.feedsense.analysis.evidence.temporal

/*
 * Milestone 8B-6.
 *
 * Evidence coverage model.
 *
 * Represents how much of a FeedItem's temporal extent
 * was actually observed through evidence. Coverage is
 * a critical factor in determining whether a prediction
 * is reliable.
 *
 * Key insight:
 *   item = 30 sec, evidence = 2 sec
 *   is NOT equivalent to
 *   item = 30 sec, evidence = 27 sec
 *
 * Coverage states are explicit and deterministic.
 * The system never pretends missing frames contained
 * the same content (no semantic interpolation).
 *
 * Design:
 *   - Deterministic: same timestamps → same coverage
 *   - Honest: gaps remain gaps
 *   - No invented precision: coverage reflects actual
 *     observed extent
 *   - Serializable: for audit and reproducibility
 *
 * Important:
 *   - Coverage ≠ confidence
 *   - HIGH_COVERAGE does NOT guarantee correct prediction
 *   - LOW_COVERAGE does NOT guarantee incorrect prediction
 *   - Coverage is a factual observation about data extent
 */
data class EvidenceCoverage(
    val itemDurationMs: Long,
    val observedDurationMs: Long,
    val coverageState: CoverageState,
    val coverageRatio: Double,
    val observedRangeStartMs: Long,
    val observedRangeEndMs: Long,
    val hasGap: Boolean,
    val gapCount: Int,
    val gapTotalDurationMs: Long,
    val largestGapMs: Long,
    val firstEvidenceRelativeMs: Long,
    val lastEvidenceRelativeMs: Long,
    val evidencePointCount: Int,
    val uniqueEvidenceIdentities: Int
) {
    /*
     * Temporal extent of the observation window as a
     * fraction of the item duration.
     */
    val temporalReach: Double
        get() = if (itemDurationMs > 0) {
            observedDurationMs.toDouble() /
                    itemDurationMs.toDouble()
        } else {
            0.0
        }

    /*
     * Density: evidence points per second of item
     * duration.
     */
    val evidenceDensity: Double
        get() = if (itemDurationMs > 0) {
            evidencePointCount.toDouble() /
                    (itemDurationMs.toDouble() / 1000.0)
        } else {
            0.0
        }

    /*
     * Effective coverage: ratio of observed duration to
     * item duration, capped at 1.0.
     */
    val effectiveCoverage: Double
        get() = coverageRatio.coerceIn(0.0, 1.0)

    companion object {

        const val VERSION = "evidence-coverage-v1"

        /*
         * Thresholds for coverage state classification.
         *
         * These are explicit and documented. Any change
         * must bump VERSION.
         */
        const val HIGH_COVERAGE_THRESHOLD = 0.7
        const val PARTIAL_COVERAGE_THRESHOLD = 0.3
        const val LOW_COVERAGE_THRESHOLD = 0.05

        /*
         * Computes coverage from evidence points and item
         * duration.
         *
         * @param points The ordered evidence points.
         * @param itemDurationMs Total item duration in ms.
         * @return Deterministic coverage assessment.
         */
        fun compute(
            points: List<TemporalEvidencePoint>,
            itemDurationMs: Long
        ): EvidenceCoverage {

            if (points.isEmpty() || itemDurationMs <= 0) {
                return noCoverage(
                    itemDurationMs,
                    points.size
                )
            }

            val timestamps =
                points.map { it.relativeTimeMs }.sorted()

            val firstMs = timestamps.first()
            val lastMs = timestamps.last()
            val observedDuration =
                (lastMs - firstMs).coerceAtLeast(0)

            // Detect gaps between consecutive points.
            val sortedUnique = timestamps.distinct().sorted()
            var gapCount = 0
            var gapTotalMs = 0L
            var largestGapMs = 0L

            for (i in 1 until sortedUnique.size) {
                val gap =
                    sortedUnique[i] - sortedUnique[i - 1]
                if (gap > GAP_THRESHOLD_MS) {
                    gapCount++
                    gapTotalMs += gap
                    largestGapMs =
                        maxOf(largestGapMs, gap)
                }
            }

            val uniqueIdentities =
                points.map { it.evidenceIdentityHash }
                    .distinct()
                    .size

            val coverageRatio = if (itemDurationMs > 0) {
                observedDuration.toDouble() /
                        itemDurationMs.toDouble()
            } else {
                0.0
            }

            val state = classifyCoverage(coverageRatio)

            return EvidenceCoverage(
                itemDurationMs = itemDurationMs,
                observedDurationMs = observedDuration,
                coverageState = state,
                coverageRatio = coverageRatio,
                observedRangeStartMs = firstMs,
                observedRangeEndMs = lastMs,
                hasGap = gapCount > 0,
                gapCount = gapCount,
                gapTotalDurationMs = gapTotalMs,
                largestGapMs = largestGapMs,
                firstEvidenceRelativeMs = firstMs,
                lastEvidenceRelativeMs = lastMs,
                evidencePointCount = points.size,
                uniqueEvidenceIdentities = uniqueIdentities
            )
        }

        private fun noCoverage(
            itemDurationMs: Long,
            pointCount: Int
        ): EvidenceCoverage {
            return EvidenceCoverage(
                itemDurationMs = itemDurationMs,
                observedDurationMs = 0,
                coverageState = CoverageState.NO_COVERAGE,
                coverageRatio = 0.0,
                observedRangeStartMs = 0,
                observedRangeEndMs = 0,
                hasGap = false,
                gapCount = 0,
                gapTotalDurationMs = 0,
                largestGapMs = 0,
                firstEvidenceRelativeMs = 0,
                lastEvidenceRelativeMs = 0,
                evidencePointCount = pointCount,
                uniqueEvidenceIdentities = 0
            )
        }

        private fun classifyCoverage(
            ratio: Double
        ): CoverageState {
            return when {
                ratio <= 0.0 ->
                    CoverageState.NO_COVERAGE
                ratio < LOW_COVERAGE_THRESHOLD ->
                    CoverageState.LOW_COVERAGE
                ratio < PARTIAL_COVERAGE_THRESHOLD ->
                    CoverageState.PARTIAL_COVERAGE
                else ->
                    CoverageState.HIGH_COVERAGE
            }
        }

        /*
         * Gap threshold: 5 seconds between evidence
         * points is considered a temporal gap. This is
         * separate from the frame gap detection in
         * TemporalConfig (which is for frame-level
         * segmentation).
         */
        const val GAP_THRESHOLD_MS = 5000L
    }
}

/*
 * Coverage states.
 *
 * Explicit, documented, deterministic.
 * Do not use arbitrary labels without rationale.
 */
enum class CoverageState(val label: String) {
    /*
     * No evidence observed at all.
     */
    NO_COVERAGE("NO_COVERAGE"),

    /*
     * Evidence covers < 5% of the item.
     * Very short interaction or skipped content.
     */
    LOW_COVERAGE("LOW_COVERAGE"),

    /*
     * Evidence covers 5-70% of the item.
     * Partial observation, some temporal reasoning
     * possible but incomplete.
     */
    PARTIAL_COVERAGE("PARTIAL_COVERAGE"),

    /*
     * Evidence covers >= 70% of the item.
     * Substantial temporal reasoning possible.
     */
    HIGH_COVERAGE("HIGH_COVERAGE")
}
