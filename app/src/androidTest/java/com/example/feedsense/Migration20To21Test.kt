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
 * Milestone 7W. Migration test for MIGRATION_20_21.
 *
 * Builds a real SQLite database at version 20 and
 * verifies the migration adds the categoryScores column
 * to feed_items, defaulting legacy rows to an empty
 * JSON object.
 */
@RunWith(AndroidJUnit4::class)
class Migration20To21Test {

    private lateinit var db: androidx.sqlite.db.SupportSQLiteDatabase

    /*
     * Minimal version of feed_items as it existed at
     * version 20. The migration only adds one NOT NULL
     * TEXT column with a '{}' default, so the table needs
     * just enough shape for an existing row to survive.
     */
    private val v20FeedItems =
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
            mixedContent INTEGER NOT NULL,
            pausedDurationSeconds INTEGER NOT NULL,
            activeWatchDurationSeconds INTEGER NOT NULL,
            uncertaintyLevel TEXT NOT NULL
        )
        """.trimIndent()

    @Before
    fun setUp() {

        val context =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext

        val fileName =
            "migration_20_21_${System.currentTimeMillis()}.db"

        db =
            FrameworkSQLiteOpenHelperFactory()
                .create(
                    androidx.sqlite.db.SupportSQLiteOpenHelper
                        .Configuration
                        .builder(context)
                        .name(fileName)
                        .callback(
                            object :
                                androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(20) {

                                override fun onConfigure(
                                    db: androidx.sqlite.db.SupportSQLiteDatabase
                                ) {
                                }

                                override fun onCreate(
                                    db: androidx.sqlite.db.SupportSQLiteDatabase
                                ) {
                                    db.execSQL(v20FeedItems)
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

        return application.MIGRATION_20_21
    }

    private fun columnNames(): Set<String> {

        return db
            .query("PRAGMA table_info(feed_items)")
            .use { cursor ->

                buildSet {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(1))
                    }
                }
            }
    }

    @Test
    fun migrationAddsCategoryScoresColumn() {

        migration().migrate(db)

        assertTrue(
            "categoryScores column exists after migration",
            "categoryScores" in columnNames()
        )
    }

    @Test
    fun migrationDefaultsLegacyRowsToEmptyObject() {

        db.execSQL(
            """
            INSERT INTO feed_items (
                id, sessionId, startTime, durationSeconds,
                category, contentType, skipped,
                representativeFramePath, frameCount,
                interactionSignals, needsReview,
                candidateCategories, updatedAt,
                contentTransitions, interactionEvidence,
                secondaryCategories, mixedContent,
                pausedDurationSeconds,
                activeWatchDurationSeconds,
                uncertaintyLevel
            ) VALUES (
                'item-1', 'session-1',
                '2026-08-17T10:00:00', 5,
                'comedy', 'SHORT_VIDEO', 0,
                '/frames/item-1.jpg', 3,
                '[]', 0, '[]',
                '2026-08-17T10:00:05',
                '[]', '[]',
                '[]', 0, 0, 5, 'LOW'
            )
            """.trimIndent()
        )

        migration().migrate(db)

        db.query(
            "SELECT category, categoryScores FROM feed_items WHERE id = 'item-1'"
        ).use { cursor ->

            assertTrue("row survives the migration", cursor.moveToFirst())
            assertEquals("category preserved", "comedy", cursor.getString(0))
            assertEquals(
                "categoryScores defaults to an empty object",
                "{}",
                cursor.getString(1)
            )
        }
    }
}
