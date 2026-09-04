package com.example.feedsense.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.feedsense.FeedSenseApplication
import com.example.feedsense.analysis.RetentionConfig
import com.example.feedsense.analysis.RetentionPolicy
import com.example.feedsense.analysis.SessionSnapshot
import java.io.File
import java.time.LocalDateTime

/*
 * Milestone 7T.
 *
 * Periodic data-retention worker.
 *
 * Applies the pure RetentionPolicy to the captured-frames
 * table:
 *
 *   - PURGE whole inactive sessions whose latest frame is
 *     older than retentionDays (frames + their files +
 *     feed items + observations).
 *   - TRIM sessions over maxFramesPerSession by deleting
 *     the oldest raw frames (never review-queue frames).
 *
 * The research session record, the labeled references and
 * the model feedback dataset are never touched - they are
 * the learning/evaluation record. Exported JSON files
 * older than retentionDays are also pruned.
 */
class RetentionWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(
    appContext,
    workerParams
) {
    private val application =
        appContext.applicationContext
                as FeedSenseApplication

    private val sessionRepository =
        application.sessionRepository

    override suspend fun doWork(): Result {

        return try {

            val config =
                RetentionConfig()

            val snapshots =
                sessionRepository
                    .getSessionFrameStats()
                    .map {
                        SessionSnapshot(
                            sessionId = it.sessionId,
                            latestCapturedAt =
                                it.latestCapturedAt,
                            frameCount = it.frameCount
                        )
                    }

            val activeIds =
                sessionRepository
                    .getActiveSessionIds()
                    .toSet()

            val plan =
                RetentionPolicy().evaluate(
                    sessions = snapshots,
                    activeSessionIds = activeIds,
                    now = LocalDateTime.now(),
                    config = config
                )

            // --------------------------------
            // PURGE OLD SESSIONS
            // --------------------------------

            for (sessionId in plan.purgeSessions) {

                deleteFiles(
                    sessionRepository
                        .getFramePathsForSession(
                            sessionId
                        )
                )

                sessionRepository
                    .deleteFramesForSession(
                        sessionId
                    )

                sessionRepository
                    .deleteFeedItemsForSession(
                        sessionId
                    )

                sessionRepository
                    .deleteObservationsForSession(
                        sessionId
                    )
            }

            // --------------------------------
            // TRIM OVER-CAP SESSIONS
            // --------------------------------

            for ((sessionId, excess) in plan.trimBySession) {

                if (excess <= 0) {
                    continue
                }

                deleteFiles(
                    sessionRepository
                        .getDeletableFramePathsForSession(
                            sessionId = sessionId,
                            limit = excess
                        )
                )

                sessionRepository
                    .deleteDeletableFramesForSession(
                        sessionId = sessionId,
                        limit = excess
                    )
            }

            // --------------------------------
            // PRUNE STALE EXPORTS
            // --------------------------------

            pruneStaleExports(config)

            Result.success()

        } catch (exception: Exception) {

            exception.printStackTrace()

            Result.retry()
        }
    }

    private fun deleteFiles(
        paths: List<String>
    ) {
        for (path in paths) {

            try {

                val file =
                    File(path)

                if (file.exists()) {
                    file.delete()
                }

            } catch (exception: Exception) {
                exception.printStackTrace()
            }
        }
    }

    private fun pruneStaleExports(
        config: RetentionConfig
    ) {

        val directory =
            File(
                applicationContext.filesDir,
                "exports"
            )

        if (!directory.exists()) {
            return
        }

        val cutoff =
            LocalDateTime.now()
                .minusDays(
                    config.retentionDays
                )

        directory
            .listFiles { file ->
                file.isFile &&
                        file.name.startsWith(
                            "feedsense_export_"
                        )
            }
            ?.forEach { file ->

                try {

                    val lastModified =
                        java.time.Instant
                            .ofEpochMilli(
                                file.lastModified()
                            )
                            .atZone(
                                java.time.ZoneId.systemDefault()
                            )
                            .toLocalDateTime()

                    if (lastModified.isBefore(cutoff)) {
                        file.delete()
                    }

                } catch (exception: Exception) {
                    exception.printStackTrace()
                }
            }
    }
}
