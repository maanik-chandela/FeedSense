package com.example.feedsense.analysis.dedup

/*
 * Milestone 8B-3.
 *
 * Retention decision for a frame based on perceptual
 * comparison with the previously retained frame.
 *
 * These form a spectrum from "safe to discard" to
 * "must retain". The system is conservative: when in
 * doubt, retain.
 *
 * Research completeness is more important than maximum
 * compute savings.
 */

enum class RetentionDecision {

    /*
     * Identical perceptual hash (distance = 0).
     * May discard, subject to force-keep safety.
     */
    DEFINITELY_SAME,

    /*
     * Near-duplicate (distance within nearDuplicateThreshold).
     * May discard under safety constraints.
     */
    PROBABLY_SAME,

    /*
     * Insufficient information to classify.
     * Hash failure, unreadable image, or intermediate
     * distance. Must retain.
     */
    UNCERTAIN,

    /*
     * Visually different content.
     * Must retain for analysis.
     */
    DIFFERENT
}

/*
 * Milestone 8B-3.
 *
 * The complete result of frame filtering.
 *
 * Contains the decision plus diagnostic information for
 * structured logging and metrics. No sensitive image
 * content is included.
 */
data class FrameFilterResult(
    val decision: RetentionDecision,
    val shouldRetain: Boolean,
    val currentHash: String?,
    val previousHash: String?,
    val hammingDistance: Int?,
    val reason: String,
    val forceKept: Boolean = false
)
