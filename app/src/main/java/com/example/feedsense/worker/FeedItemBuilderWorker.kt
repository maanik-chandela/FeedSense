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

        return try {

            val sessionIds =
                sessionRepository
                    .getSessionsWithAnalyzedFrames()

            for (sessionId in sessionIds) {

                sessionRepository
                    .rebuildFeedItemsForSession(
                        sessionId
                    )
            }

            Result.success()

        } catch (exception: Exception) {

            exception.printStackTrace()

            Result.retry()
        }
    }
}
