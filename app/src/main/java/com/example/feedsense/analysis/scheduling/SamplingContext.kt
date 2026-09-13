package com.example.feedsense.analysis.scheduling

import com.example.feedsense.analysis.efficiency.FrameDecision
import com.example.feedsense.analysis.efficiency.FrameSimilarityResult

/*
 * Milestone 8B-12.
 *
 * Per-candidate scheduling evidence.
 *
 * This is the ONLY input the scheduler needs. It deliberately
 * contains COMPACT, metadata-first evidence only:
 *   - the timestamp
 *   - a reference to the structured 8B-11 result (distance and
 *     decision are extracted; the scheduler never re-hashes and
 *     never touches pixels)
 *   - two compact booleans (interaction / transition suspicion)
 *   - optional informational labels for the screen/session.
 *
 * NO raw screenshots, OCR payloads, captions or messages are
 * passed here.
 */
data class SamplingContext(
    /*
     * Monotonic candidate timestamp in milliseconds. Must be
     * >= 0; negative timestamps trigger the documented safe
     * fallback (see FrameSamplingScheduler).
     */
    val timestampMs: Long,

    /*
     * Structured 8B-11 similarity result if one was produced
     * (null when 8B-11 was disabled, time-window-gated, or
     * otherwise withheld). The scheduler reads only
     * `hammingDistance`, `decision`, and the version fields.
     */
    val similarityResult: FrameSimilarityResult? = null,

    /*
     * Compact scheduling signal: an interaction event (like /
     * comment / share / save / follow / skip / pause) occurred
     * around this candidate. It is a SCHEDULING SIGNAL ONLY -
     * it never modifies any AI prediction.
     */
    val interactionActive: Boolean = false,

    /*
     * Compact scheduling signal that a content transition is
     * suspected (e.g. upstream feed boundary signal). Optional -
     * the scheduler also infers transitions from the 8B-11
     * distance.
     */
    val contentTransitionSuspected: Boolean = false,

    /*
     * Optional informational, non-sensitive sector or session
     * label (e.g. "REEL", "FEED", "SHORTS"). Never contains
     * content or identity data. Not part of the decision logic.
     */
    val sectorState: String? = null
) {

    /*
     * Visual-change magnitude (0..hashSize^2 for dHash). Null
     * when 8B-11 produced no comparable distance.
     */
    val visualChangeDistance: Int?
        get() = similarityResult?.hammingDistance

    val similarityDecision: FrameDecision?
        get() = similarityResult?.decision

    override fun toString(): String =
        "SamplingContext(t=$timestampMs," +
            "dist=${visualChangeDistance},interaction=$interactionActive," +
            "transitionSuspected=$contentTransitionSuspected)"
}