package com.example.feedsense.analysis.scheduling

/*
 * Milestone 8B-12.
 *
 * Controlled vocabulary of sampling actions and the explainable
 * decision records produced by the scheduler.
 */

/*
 * The three-way scheduling action.
 *
 *   ANALYZE      - spend expensive computation on this frame
 *                  (normal analysis opportunity).
 *   FORCE_ANALYZE- safety-triggered analysis (maximum interval
 *                  ceiling reached). Distinguished from a normal
 *                  analysis so research can count safety work.
 *   SKIP         - do not spend computation on this frame.
 *
 * A DEFER action is intentionally NOT introduced: a skipped
 * candidate inside the minimum interval is a deferral that will
 * naturally become eligible at a later candidate, so SKIP +
 * MIN_INTERVAL carries the deferred semantics (§4 of the spec).
 */
enum class SamplingAction(val label: String) {
    ANALYZE("ANALYZE"),
    FORCE_ANALYZE("FORCE_ANALYZE"),
    SKIP("SKIP");

    /** Whether expensive analysis is spent on this frame. */
    val analyzes: Boolean
        get() = this != SKIP
}

/*
 * Immutable, fully attributable sampling decision (§25 of the
 * spec). Contains no raw image and no content.
 */
data class SamplingDecision(
    val action: SamplingAction,
    val reason: SamplingReason,
    val timestampMs: Long,

    /* Position in the candidate stream (1-based). */
    val candidateIndex: Long,

    /* How many analyses have been spent so far (1-based). */
    val analysisIndex: Long,

    /* Elapsed ms since the previous analysis attribution.
     * 0 idempotent for the first frame. */
    val elapsedSinceLastAnalysisMs: Long,

    /* 8B-11 visual-change distance if available. */
    val similarityDistance: Int?,

    /* The 8B-11 decision if available (for diagnostics). */
    val similarityDecision: com.example.feedsense.analysis.efficiency.FrameDecision?,

    /* 8B-11 algorithm version if available. */
    val similarityAlgorithmVersion: String?,

    /* The adaptive interval (ms) this candidate was measured
     * against (equal to maximumAnalysisIntervalMs when the
     * safety ceiling applied). */
    val effectiveIntervalMs: Long,

    /* True for MAX_INTERVAL safety path or the fallback path. */
    val forced: Boolean,

    val algorithmVersion: String,
    val configVersion: String
) {

    val analyzes: Boolean
        get() = action.analyzes
}