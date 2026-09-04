package com.example.feedsense.worker
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.feedsense.FeedSenseApplication

// --------------------------------
// FEED ITEM BUILDER WORKER
// --------------------------------
//
// Milestone 7C.
//
// Groups analyzed frames into content items and
// generates AI auto-observations.
//
// Runs after frame analysis so it always sees the
// latest ANALYZED frames.
//

class FeedItemBuilderWorker(
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

        val sessionIds =
            sessionRepository
                .getSessionsWithAnalyzedFrames()

        var hadFailure = false

        for (sessionId in sessionIds) {

            try {

                sessionRepository
                    .rebuildFeedItemsForSession(
                        sessionId
                    )

            } catch (exception: Exception) {

                /*
                 * Isolate failures per session:
                 *
                 * - One broken session must not prevent
                 *   the others from being built.
                 * - A permanent failure is reported with
                 *   Result.failure() instead of retry() so
                 *   the unique work slot is released and
                 *   future triggers can run again.
                 */
                exception.printStackTrace()

                hadFailure = true
            }
        }

        return if (hadFailure) {
            Result.failure()
        } else {
            Result.success()
        }
    }
}
