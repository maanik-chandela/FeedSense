package com.example.feedsense.analysis

import java.time.LocalDateTime

/*
 * Milestone 7T.
 *
 * Pure retention decision logic. Given a snapshot of the
 * captured-frames table, which sessions are still active,
 * and a RetentionConfig, it decides:
 *
 *   1. PURGE - whole sessions (frames + feed items +
 *      observations) older than retentionDays. The raw
 *      screenshots are the bulk of storage; the research
 *      session record and the labeled references /
 *      feedback dataset are deliberately kept.
 *   2. TRIM  - sessions that exceed maxFramesPerSession
 *      keep only the newest `cap` frames. Only raw,
 *      non-review frames count towards the cap.
 *
 * The review/training dataset (NEEDS_REVIEW / REVIEWED
 * frames, labeled_references, model_feedback) is never
 * removed by retention.
 */
class RetentionPolicy {

    companion object {

        /*
         * Statuses that are bulk raw data and may be
         * deleted by retention.
         */
        val DELETABLE_STATUSES =
            listOf(
                "PENDING",
                "PROCESSING",
                "FAILED",
                "ANALYZED"
            )

        /*
         * Statuses that are the human review queue.
         */
        val REVIEW_STATUSES =
            listOf(
                "NEEDS_REVIEW",
                "REVIEWED"
            )
    }

    fun evaluate(
        sessions: List<SessionSnapshot>,
        activeSessionIds: Set<String>,
        now: LocalDateTime,
        config: RetentionConfig
    ): RetentionPlan {

        val cutoff =
            now.minusDays(
                config.retentionDays
            )

        // --------------------------------
        // 1. PURGE: old, inactive sessions
        // --------------------------------

        val purge =
            buildSet {
                for (snapshot in sessions) {

                    if (
                        snapshot.sessionId in
                        activeSessionIds
                    ) {
                        continue
                    }

                    val latest =
                        snapshot.latestCapturedAt
                            ?: continue

                    if (latest.isBefore(cutoff)) {
                        add(snapshot.sessionId)
                    }
                }
            }

        // --------------------------------
        // 2. TRIM: sessions over the cap
        // --------------------------------

        val trim =
            buildMap {
                for (snapshot in sessions) {

                    if (
                        snapshot.sessionId in purge
                    ) {
                        continue
                    }

                    if (
                        snapshot.frameCount >
                        config.maxFramesPerSession
                    ) {
                        put(
                            snapshot.sessionId,
                            snapshot.frameCount -
                                    config.maxFramesPerSession
                        )
                    }
                }
            }

        return RetentionPlan(
            purgeSessions = purge,
            trimBySession = trim,
            keepReviewFrames =
                config.keepReviewFrames
        )
    }
}

/*
 * Aggregate view of one session's captured frames,
 * produced by the DAO GROUP BY query.
 */
data class SessionSnapshot(
    val sessionId: String,
    val latestCapturedAt: LocalDateTime?,
    val frameCount: Int
)

/*
 * The worker executes exactly what the plan says.
 *
 *   purgeSessions - sessions to wipe (frames, feed items,
 *                   observations). Session record and
 *                   references/feedback survive.
 *   trimBySession - sessionId -> number of oldest raw
 *                   frames to delete.
 */
data class RetentionPlan(
    val purgeSessions: Set<String>,
    val trimBySession: Map<String, Int>,
    val keepReviewFrames: Boolean
)
