package com.example.feedsense.analysis.efficiency

/*
 * Milestone 8B-11.
 *
 * Structured, privacy-safe statistics for the deduplication
 * layer (§25, §32 of the 8B-11 spec).
 *
 * Only counts are recorded - never pixel data, OCR, or any
 * sensitive payload. Users should treat these as diagnostic
 * counters, not accuracy claims.
 */
data class FrameDedupStats(
    /* Every frame handed to the deduplicator. */
    val framesSeen: Long = 0L,

    /* Frames whose hash was actually computed (and, when a
       reference existed, compared). Equals the number of hash
       computations. */
    val hashComputations: Long = 0L,

    /* Frames forwarded (decision UNIQUE). */
    val framesAccepted: Long = 0L,

    /* Frames suppressed (DUPLICATE / SIMILAR / time-window). */
    val framesRejected: Long = 0L,

    /* Frames forced through by the safety ceiling. */
    val forcedForwardCount: Long = 0L,

    /* Sum of Hamming distances over compared frames. */
    val hashDistanceSum: Long = 0L
) {

    val framesCompared: Long
        get() = hashComputations

    /*
     * Average observed Hamming distance across compared
     * frames. 0 when nothing was compared.
     */
    val averageHashDistance: Double
        get() =
            if (hashComputations > 0) {
                hashDistanceSum.toDouble() / hashComputations
            } else {
                0.0
            }

    /*
     * DEDUPLICATION RATE = rejected / eligible.
     *
     * Denominator: framesSeen (every frame that reached the
     * layer, including the first). Precise definition, not a
     * "match rate".
     */
    val deduplicationRate: Double
        get() =
            if (framesSeen > 0) {
                framesRejected.toDouble() / framesSeen
            } else {
                0.0
            }

    /*
     * FORWARD RATE = accepted / eligible.
     *
     * Same denominator as deduplicationRate, so the two rates
     * sum to 1.0 (every seen frame is either forwarded or
     * suppressed).
     */
    val forwardRate: Double
        get() =
            if (framesSeen > 0) {
                framesAccepted.toDouble() / framesSeen
            } else {
                0.0
            }
}