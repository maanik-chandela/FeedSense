package com.example.feedsense.analysis.efficiency

/*
 * Milestone 8B-11.
 *
 * Result model for the visual-similarity check (§8 of the
 * 8B-11 spec).
 *
 * A result is a structured, research-friendly record of what
 * happened: the decision, the reason, the hashes, the distance,
 * the threshold and the algorithm version. It exposes NO raw
 * pixel data.
 */

/*
 * The three-way visual-similarity classification.
 *
 *   DUPLICATE - identical perceptual signature (distance 0).
 *   SIMILAR   - small perceptual distance
 *               (0 < distance <= configured threshold).
 *   UNIQUE    - meaningfully changed (distance > threshold).
 *
 * Only UNIQUE is forwarded to downstream analysis.
 */
enum class FrameDecision(val label: String) {
    DUPLICATE("DUPLICATE"),
    SIMILAR("SIMILAR"),
    UNIQUE("UNIQUE");

    /*
     * Whether this decision lets the frame through to
     * downstream analysis.
     */
    val shouldForward: Boolean
        get() = this == UNIQUE
}

/*
 * WHY a decision was produced. Enables research diagnostics
 * without any raw content.
 */
enum class FrameEvaluationReason(val label: String) {
    /* First frame seen; nothing to compare against. */
    FIRST_FRAME("FIRST_FRAME"),

    /* Hashes were computed and compared normally. */
    COMPARED("COMPARED"),

    /* Frame arrived inside the minimum frame interval; it was
       rejected cheaply without hashing. */
    TIME_WINDOW_GATED("TIME_WINDOW_GATED"),

    /* Safety ceiling reached; the frame was forced through. */
    FORCED_FORWARD("FORCED_FORWARD"),

    /* Deduplication disabled; pass-through. */
    DISABLED_PASSTHROUGH("DISABLED_PASSTHROUGH")
}

/*
 * Immutable, fully attributable result of one evaluation.
 *
 * `hash` is null only when the frame was not hashed
 * (TIME_WINDOW_GATED or DISABLED_PASSTHROUGH).
 */
data class FrameSimilarityResult(
    val sequenceIndex: Long,
    val frameId: String? = null,
    val decision: FrameDecision,
    val reason: FrameEvaluationReason,
    val hash: FrameHash?,
    val referenceHash: FrameHash?,
    val hammingDistance: Int?,
    val threshold: Int?,
    val algorithm: FrameHashAlgorithm,
    val algorithmVersion: String,
    val configVersion: String,
    val timestampMs: Long,
    val forcedForward: Boolean
) {

    /*
     * Convenience mirror of decision.shouldForward for the
     * future scheduling contract (FORWARD vs SKIP, §30).
     */
    val forwards: Boolean get() = decision.shouldForward
}