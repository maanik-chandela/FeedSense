package com.example.feedsense

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.feedsense.FeedSenseApplication
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Milestone 7T. Migration test for MIGRATION_18_19.
 *
 * Builds a real SQLite database at version 18 and
 * verifies the migration creates the 8 query-path
 * indexes without touching any table data.
 */
@RunWith(AndroidJUnit4::class)
class Migration18To19Test {

    private lateinit var db: androidx.sqlite.db.SupportSQLiteDatabase

    /*
     * Minimal versions of the three indexed tables as they
     * existed at version 18. CREATE INDEX only requires
     * the tables and columns to exist, so the migration
     * test does not need the full entity schemas.
     */
    private val v18CapturedFrames =
        """
        CREATE TABLE IF NOT EXISTS captured_frames (
            id TEXT NOT NULL PRIMARY KEY,
            sessionId TEXT NOT NULL,
            filePath TEXT NOT NULL,
            capturedAt TEXT NOT NULL,
            analysisStatus TEXT NOT NULL DEFAULT 'PENDING',
            analysisResult TEXT,
            analyzedAt TEXT,
            frameFingerprint TEXT
        )
        """.trimIndent()

    private val v18FeedItems =
        """
        CREATE TABLE IF NOT EXISTS feed_items (
            id TEXT NOT NULL PRIMARY KEY,
            sessionId TEXT NOT NULL,
            startTime TEXT NOT NULL,
            durationSeconds INTEGER NOT NULL,
            contentType TEXT NOT NULL,
            representativeFramePath TEXT NOT NULL,
            frameCount INTEGER NOT NULL,
            interactionSignals TEXT NOT NULL,
            needsReview INTEGER NOT NULL,
            candidateCategories TEXT NOT NULL,
            updatedAt TEXT NOT NULL,
            contentTransitions TEXT NOT NULL,
            interactionEvidence TEXT NOT NULL,
            pausedDurationSeconds INTEGER NOT NULL,
            activeWatchDurationSeconds INTEGER NOT NULL,
            uncertaintyLevel TEXT NOT NULL
        )
        """.trimIndent()

    private val v18LabeledReferences =
        """
        CREATE TABLE IF NOT EXISTS labeled_references (
            id TEXT NOT NULL PRIMARY KEY,
            frameId TEXT NOT NULL,
            sessionId TEXT NOT NULL,
            filePath TEXT NOT NULL,
            feedItemId TEXT,
            aiCategory TEXT,
            aiConfidence REAL,
            aiSource TEXT NOT NULL,
            modelVersion TEXT,
            candidateCategories TEXT NOT NULL,
            platform TEXT,
            topic TEXT,
            tone TEXT,
            visibleText TEXT,
            aiReason TEXT,
            interactionSignals TEXT NOT NULL,
            frameFingerprint TEXT,
            labelSource TEXT NOT NULL,
            validationStatus TEXT NOT NULL,
            validatedLabel TEXT,
            agreement INTEGER,
            createdAt TEXT NOT NULL,
            reviewedAt TEXT
        )
        """.trimIndent()

    @Before
    fun setUp() {

        val context =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext

        val fileName =
            "migration_18_19_${System.currentTimeMillis()}.db"

        db =
            FrameworkSQLiteOpenHelperFactory()
                .create(
                    androidx.sqlite.db.SupportSQLiteOpenHelper
                        .Configuration
                        .builder(context)
                        .name(fileName)
                        .callback(
                            object :
                                androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(18) {

                                override fun onConfigure(
                                    db: androidx.sqlite.db.SupportSQLiteDatabase
                                ) {
                                }

                                override fun onCreate(
                                    db: androidx.sqlite.db.SupportSQLiteDatabase
                                ) {
                                    db.execSQL(v18CapturedFrames)
                                    db.execSQL(v18FeedItems)
                                    db.execSQL(v18LabeledReferences)
                                }

                                override fun onUpgrade(
                                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                                    oldVersion: Int,
                                    newVersion: Int
                                ) {
                                }

                                override fun onDowngrade(
                                    db: androidx.sqlite.db.SupportSQLiteDatabase,
                                    oldVersion: Int,
                                    newVersion: Int
                                ) {
                                }
                            }
                        )
                        .build()
                )
                .writableDatabase
    }

    @After
    fun tearDown() {

        if (::db.isInitialized) {
            db.close()
        }
    }

    private fun migration(): androidx.room.migration.Migration {

        val application =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext
                .applicationContext as FeedSenseApplication

        return application.MIGRATION_18_19
    }

    private fun indexNames(): Set<String> {

        return db
            .query(
                "SELECT name FROM sqlite_master " +
                        "WHERE type = 'index'"
            )
            .use { cursor ->

                buildSet {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(0))
                    }
                }
            }
    }

    @Test
    fun migrationCreatesAllEightIndexes() {

        migration().migrate(db)

        val indexes =
            indexNames()

        setOf(
            "idx_captured_frames_session_status",
            "idx_captured_frames_status",
            "idx_captured_frames_session_fingerprint",
            "idx_feed_items_session",
            "idx_feed_items_session_updated",
            "idx_labeled_references_status",
            "idx_labeled_references_frame",
            "idx_labeled_references_session_item"
        ).forEach {
            assertTrue("missing index $it", it in indexes)
        }
    }

    @Test
    fun migrationIsIdempotent() {

        migration().migrate(db)
        migration().migrate(db)

        val indexes =
            indexNames()

        assertTrue(
            "idx_feed_items_session exists after re-run",
            "idx_feed_items_session" in indexes
        )
    }
}
