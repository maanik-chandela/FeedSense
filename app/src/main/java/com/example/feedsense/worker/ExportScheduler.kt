package com.example.feedsense.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/*
 * Milestone 7T.
 *
 * Schedules a JSON dataset export. Called when a session
 * ends so the export includes the finished session.
 */
object ExportScheduler {

    private const val WORK_NAME =
        "feedsense_export"

    fun schedule(
        context: Context
    ) {

        val constraints =
            Constraints.Builder()
                .setRequiredNetworkType(
                    NetworkType.NOT_REQUIRED
                )
                .build()

        val request =
            OneTimeWorkRequestBuilder<ExportWorker>()
                .setConstraints(
                    constraints
                )
                .setInitialDelay(
                    3,
                    TimeUnit.SECONDS
                )
                .build()

        WorkManager
            .getInstance(
                context.applicationContext
            )
            .enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
    }
}
