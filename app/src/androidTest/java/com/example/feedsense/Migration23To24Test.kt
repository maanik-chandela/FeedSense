package com.example.feedsense

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Milestone 8A-1. Migration test for MIGRATION_23_24.
 *
 * Builds a real SQLite database at version 23 with an
 * existing feed_items row and verifies the migration:
 *
 *   - is non-destructive (existing row + data survive)
 *   - adds all four new evaluation tables
 *   - the new tables start empty / correctly shaped
 */
@RunWith(AndroidJUnit4::class)
class Migration23To24Test {

    private lateinit var db: androidx.sqlite.db.SupportSQLiteDatabase

    /*
     * Minimal feed_items as it conceptually existed at
     * version 23. The migration never touches feed_items,
     * so only enough shape to hold one legacy row is needed
     * to prove preservation.
     */
    private val v23FeedItems =
        """
        CREATE TABLE IF NOT EXISTS feed_items (
            id TEXT NOT NULL PRIMARY KEY,
            sessionId TEXT NOT NULL,
            startTime TEXT NOT NULL,
            endTime TEXT,
            durationSeconds INTEGER NOT NULL,
            category TEXT,
            categoryDomain TEXT,
            confidence REAL,
            topic TEXT,
            tone TEXT,
            contentType TEXT NOT NULL,
            skipped INTEGER NOT NULL,
            representativeFramePath TEXT NOT NULL,
            frameCount INTEGER NOT NULL,
            interactionSignals TEXT NOT NULL,
            modelVersion TEXT,
            frameFingerprint TEXT,
            needsReview INTEGER NOT NULL,
            candidateCategories TEXT NOT NULL,
            classificationReason TEXT,
            updatedAt TEXT NOT NULL,
            contentTransitions TEXT NOT NULL,
            interactionEvidence TEXT NOT NULL,
            secondaryCategory TEXT,
            secondaryCategories TEXT NOT NULL,
            categoryScores TEXT NOT NULL,
            mixedContent INTEGER NOT NULL,
            pausedDurationSeconds INTEGER NOT NULL,
            activeWatchDurationSeconds INTEGER NOT NULL,
            uncertaintyLevel TEXT NOT NULL,
            source TEXT NOT NULL,
            platform TEXT,
            researcherCategory TEXT,
            researcherTopic TEXT,
            researcherNotes TEXT,
            researcherLiked INTEGER NOT NULL,
            researcherSkipped INTEGER NOT NULL,
            researcherCommented INTEGER NOT NULL,
            researcherShared INTEGER NOT NULL,
            researcherSaved INTEGER NOT NULL,
            researcherFollowed INTEGER NOT NULL,
            researcherPaused INTEGER NOT NULL,
            researcherReplayed INTEGER NOT NULL,
            appContext TEXT,
            chromeOnly INTEGER NOT NULL
        )
        """.trimIndent()

    @Before
    fun setUp() {

        val context =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext

        val fileName =
            "migration_23_24_${System.currentTimeMillis()}.db"

        db =
            FrameworkSQLiteOpenHelperFactory()
                .create(
                    androidx.sqlite.db.SupportSQLiteOpenHelper
                        .Configuration
                        .builder(context)
                        .name(fileName)
                        .callback(
                            object :
                                androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(23) {

                                override fun onConfigure(
                                    db: androidx.sqlite.db.SupportSQLiteDatabase
                                ) {
                                }

                                override fun onCreate(
                                    db: androidx.sqlite.db.SupportSQLiteDatabase
                                ) {
                                    db.execSQL(v23FeedItems)
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

        return application.MIGRATION_23_24
    }

    private fun tableNames(): Set<String> {

        return db
            .query(
                "SELECT name FROM sqlite_master WHERE type='table'"
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
    fun migrationCreatesAllFourEvaluationTables() {

        migration().migrate(db)

        val tables = tableNames()

        assertTrue(
            "evaluation_items created",
            "evaluation_items" in tables
        )
        assertTrue(
            "ai_predictions created",
            "ai_predictions" in tables
        )
        assertTrue(
            "ground_truths created",
            "ground_truths" in tables
        )
        assertTrue(
            "evaluation_results created",
            "evaluation_results" in tables
        )
    }

    @Test
    fun migrationPreservesLegacyFeedItemData() {

        db.execSQL(
            """
            INSERT INTO feed_items (
                id, sessionId, startTime, durationSeconds,
                category, categoryDomain, confidence,
                topic, tone, contentType, skipped,
                representativeFramePath, frameCount,
                interactionSignals, modelVersion,
                needsReview, candidateCategories,
                updatedAt, contentTransitions,
                interactionEvidence, secondaryCategories,
                categoryScores, mixedContent,
                pausedDurationSeconds,
                activeWatchDurationSeconds,
                uncertaintyLevel, source
            ) VALUES (
                'item-legacy', 'session-1',
                '2026-08-17T10:00:00', 12,
                'sports', 'sports', 0.85,
                'cricket', 'energetic', 'SHORT_VIDEO', 0,
                '/frames/legacy.jpg', 3,
                'like_indicator', 'local-model-v2.0',
                0, '[]',
                '2026-08-17T10:00:12',
                'CONTENT_STARTED', 'like|0.9|hit',
                'comedy', '{&quot;sports&quot;:0.85}', 1,
                0, 12, 'LOW', 'AI'
            )
            """.trimIndent()
        )

        migration().migrate(db)

        db.query(
            "SELECT category, confidence, modelVersion FROM feed_items WHERE id = 'item-legacy'"
        ).use { cursor ->

            assertTrue(
                "legacy row survives the migration",
                cursor.moveToFirst()
            )
            assertEquals("category preserved", "sports", cursor.getString(0))
            assertEquals("confidence preserved", 0.85, cursor.getDouble(1), 0.001)
            assertEquals(
                "modelVersion preserved",
                "local-model-v2.0",
                cursor.getString(2)
            )
        }
    }

    @Test
    fun newEvaluationTablesStartEmpty() {

        migration().migrate(db)

        val tables = tableNames()
        assertTrue("evaluation_results present", "evaluation_results" in tables)

        db.query("SELECT COUNT(*) FROM evaluation_items").use { c ->
            c.moveToFirst()
            assertEquals("evaluation_items starts empty", 0, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM ai_predictions").use { c ->
            c.moveToFirst()
            assertEquals("ai_predictions starts empty", 0, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM ground_truths").use { c ->
            c.moveToFirst()
            assertEquals("ground_truths starts empty", 0, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM evaluation_results").use { c ->
            c.moveToFirst()
            assertEquals("evaluation_results starts empty", 0, c.getInt(0))
        }
    }
}
