package com.example.feedsense.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object FrameAnalysisScheduler {

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
            OneTimeWorkRequestBuilder<FrameAnalysisWorker>()
                .setConstraints(
                    constraints
                )
                .setInitialDelay(
                    1,
                    TimeUnit.SECONDS
                )
                .build()

        WorkManager
            .getInstance(
                context.applicationContext
            )
            .enqueueUniqueWork(
                FrameAnalysisWorker.WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
    }

    fun cancel(
        context: Context
    ) {

        WorkManager
            .getInstance(
                context.applicationContext
            )
            .cancelUniqueWork(
                FrameAnalysisWorker.WORK_NAME
            )
    }
}
