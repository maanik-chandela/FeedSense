package com.example.feedsense.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object FeedItemScheduler {

    private const val WORK_NAME =
        "feedsense_feed_item_builder"

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
            OneTimeWorkRequestBuilder<FeedItemBuilderWorker>()
                .setConstraints(
                    constraints
                )
                .setInitialDelay(
                    2,
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

    fun cancel(
        context: Context
    ) {

        WorkManager
            .getInstance(
                context.applicationContext
            )
            .cancelUniqueWork(
                WORK_NAME
            )
    }
}
