package com.example.feedsense.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.feedsense.FeedSenseApplication
import com.example.feedsense.data.ExportManager

/*
 * Milestone 7T.
 *
 * One-shot JSON dataset export. Runs after a session ends
 * so the export always reflects the finished session.
 * Writes to app-private storage (filesDir/exports) - no
 * storage permission needed.
 */
class ExportWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(
    appContext,
    workerParams
) {
    private val application =
        appContext.applicationContext
                as FeedSenseApplication

    override suspend fun doWork(): Result {

        return try {

            val manager =
                ExportManager(
                    applicationContext
                )

            val file =
                manager.exportDataset(
                    sessionRepository =
                        application.sessionRepository,
                    referenceRepository =
                        application.referenceRepository,
                    feedbackRepository =
                        application.modelFeedbackRepository
                )

            if (file != null && file.exists()) {
                Result.success()
            } else {
                Result.failure()
            }

        } catch (exception: Exception) {

            exception.printStackTrace()

            Result.retry()
        }
    }
}
