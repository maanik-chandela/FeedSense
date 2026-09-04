package com.example.feedsense.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/*
 * Milestone 7T.
 *
 * Schedules the periodic retention worker. Runs roughly
 * daily, no network required.
 */
object RetentionScheduler {

    private const val WORK_NAME =
        "feedsense_retention"

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
            PeriodicWorkRequestBuilder<RetentionWorker>(
                1,
                TimeUnit.DAYS
            )
                .setConstraints(
                    constraints
                )
                .build()

        WorkManager
            .getInstance(
                context.applicationContext
            )
            .enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
    }
}
