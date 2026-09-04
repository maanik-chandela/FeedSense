package com.example.feedsense.analysis

/*
 * Milestone 7T.
 *
 * Configurable retention knobs for the periodic
 * RetentionWorker.
 *
 *   retentionDays         - sessions whose latest frame is
 *                           older than this are purged.
 *   maxFramesPerSession   - cap on raw frames kept per
 *                           session (the review/training
 *                           frames are exempt).
 *   keepReviewFrames      - when true, NEEDS_REVIEW /
 *                           REVIEWED frames are never
 *                           deleted by retention (they are
 *                           the human review queue).
 */
data class RetentionConfig(
    val retentionDays: Long = 30,
    val maxFramesPerSession: Int = 300,
    val keepReviewFrames: Boolean = true
)
