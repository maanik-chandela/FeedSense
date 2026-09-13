package com.example.feedsense.analysis.scheduling

/*
 * Milestone 8B-12.
 *
 * Observed-only counters for the scheduling layer (spec §26,
 * §28). No raw frames, OCR, captions or messages. These are
 * diagnostic counters, never accuracy/battery claims.
 */
data class SamplingStats(
    /* Candidates entering the scheduler. */
    val candidateCount: Long = 0L,

    /* Candidates forwarded for analysis (ANALYZE + FORCE). */
    val analyzedCount: Long = 0L,

    /* Candidates skipped for static content (longer interval). */
    val skippedCount: Long = 0L,

    /* Candidates deferred inside the minimum interval. */
    val deferredCount: Long = 0L,

    /* Analyses triggered by the maximum-interval safety ceiling. */
    val forcedAnalysisCount: Long = 0L,

    /* Analyses triggered by a content-transition signal. */
    val transitionAnalysisCount: Long = 0L,

    /* Analyses triggered by an interaction signal. */
    val interactionAnalysisCount: Long = 0L
) {

    val totalRejected: Long get() = skippedCount + deferredCount

    /*
     * SAMPLING RATIO = analyzed / candidates.
     *
     * A diagnostic of computation allocation. NOT an accuracy
     * measure (spec §28).
     */
    val samplingRatio: Double
        get() =
            if (candidateCount > 0) {
                analyzedCount.toDouble() / candidateCount
            } else {
                0.0
            }
}