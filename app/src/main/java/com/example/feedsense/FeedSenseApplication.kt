package com.example.feedsense

import android.app.Application
import androidx.room.Room
import com.example.feedsense.analysis.FrameAnalysisPipeline
import com.example.feedsense.analysis.LocalFrameAnalyzer
import com.example.feedsense.database.FeedSenseDatabase
import com.example.feedsense.repository.ProjectRepository
import com.example.feedsense.repository.ReferenceRepository
import com.example.feedsense.repository.SessionRepository
import com.example.feedsense.worker.FrameAnalysisScheduler
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

class FeedSenseApplication : Application() {

    private val database by lazy {
        Room.databaseBuilder(
            applicationContext,
            FeedSenseDatabase::class.java,
            "feedsense_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }
    val MIGRATION_8_9 = object : Migration(8, 9) {

        override fun migrate(
            database: SupportSQLiteDatabase
        ) {
            database.execSQL(
                """
            UPDATE feed_items
            SET interactionSignals = '[]'
            WHERE interactionSignals IS NULL
            """.trimIndent()
            )
        }
    }
    val repository by lazy {
        ProjectRepository(
            database.projectDao()
        )
    }

    val sessionRepository by lazy {
        SessionRepository(
            sessionDao = database.sessionDao(),
            observationDao = database.observationDao(),
            captureDao = database.captureDao(),
            feedItemDao = database.feedItemDao(),
            applicationContext = applicationContext
        )
    }

    /*
     * Labeled reference / training dataset.
     */
    val referenceRepository by lazy {
        ReferenceRepository(
            database.referenceDao()
        )
    }

    /*
     * The hybrid analysis pipeline.
     *
     * Local analysis first. Cloud fallback only when
     * the local result is not confident or is ambiguous.
     */
    val frameAnalyzer by lazy {
        FrameAnalysisPipeline(
            localAnalyzer =
                LocalFrameAnalyzer(
                    applicationContext
                )
        )
    }

    override fun onCreate() {
        super.onCreate()

        FrameAnalysisScheduler.schedule(this)
    }
}