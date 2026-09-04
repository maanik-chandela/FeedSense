package com.example.feedsense

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Milestone 8A-2. Migration test for MIGRATION_24_25.
 *
 * Builds a real SQLite database at version 24 with the
 * 8A-1 evaluation layer (four tables) and an existing
 * ground_truths row, then verifies the migration:
 *
 *   - is non-destructive (the existing ground-truth row and
 *     its data survive)
 *   - adds the 7 per-signal interaction tri-state columns to
 *     ground_truths
 *   - pre-existing rows report UNKNOWN (NULL) for the new
 *     signals until re-annotated
 *   - the new columns accept tri-state values (NULL/0/1)
 */
@RunWith(AndroidJUnit4::class)
class Migration24To25Test {

    private lateinit var db: androidx.sqlite.db.SupportSQLiteDatabase

    /*
     * The ground_truths table exactly as Room created it at
     * version 24 (per schemas/24.json) - WITHOUT the 8A-2
     * tri-state interaction columns.
     */
    private val v24GroundTruths =
        """
        CREATE TABLE IF NOT EXISTS ground_truths (
            id TEXT NOT NULL PRIMARY KEY,
            evaluationItemId TEXT NOT NULL,
            annotatorId TEXT,
            recordedAt TEXT NOT NULL,
            category TEXT,
            categoryDomain TEXT,
            secondaryCategories TEXT NOT NULL,
            ambiguity TEXT NOT NULL,
            platform TEXT,
            contentType TEXT,
            durationSeconds INTEGER,
            skipped INTEGER,
            interactionSignals TEXT NOT NULL,
            topic TEXT,
            tone TEXT,
            notes TEXT
        )
        """.trimIndent()

    @Before
    fun setUp() {

        val context =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext

        val fileName =
            "migration_24_25_${System.currentTimeMillis()}.db"

        db =
            FrameworkSQLiteOpenHelperFactory()
                .create(
                    androidx.sqlite.db.SupportSQLiteOpenHelper
                        .Configuration
                        .builder(context)
                        .name(fileName)
                        .callback(
                            object :
                                androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(24) {

                                override fun onConfigure(
                                    db: androidx.sqlite.db.SupportSQLiteDatabase
                                ) {
                                }

                                override fun onCreate(
                                    db: androidx.sqlite.db.SupportSQLiteDatabase
                                ) {
                                    db.execSQL(v24GroundTruths)
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

        return application.MIGRATION_24_25
    }

    private fun columns(): Set<String> {

        return db
            .query("PRAGMA table_info(ground_truths)")
            .use { cursor ->

                buildSet {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(1))
                    }
                }
            }
    }

    @Test
    fun migrationAddsSevenInteractionTriStateColumns() {

        migration().migrate(db)

        val cols = columns()

        listOf(
            "liked", "commented", "shared", "saved",
            "followed", "paused", "playing"
        ).forEach { col ->
            assertTrue("column $col added", col in cols)
        }

        // Pre-existing 8A-1 columns must survive.
        listOf(
            "id", "evaluationItemId", "annotatorId", "category",
            "ambiguity", "platform", "contentType", "skipped",
            "interactionSignals", "topic", "tone", "notes"
        ).forEach { col ->
            assertTrue("column $col preserved", col in cols)
        }
    }

    @Test
    fun migrationPreservesExistingGroundTruthRow() {

        db.execSQL(
            """
            INSERT INTO ground_truths (
                id, evaluationItemId, annotatorId, recordedAt,
                category, categoryDomain, secondaryCategories,
                ambiguity, platform, contentType, durationSeconds,
                skipped, interactionSignals, topic, tone, notes
            ) VALUES (
                'gt-legacy', 'item-1', 'rater-a', '2026-08-17T10:00:00',
                'sports', 'sports', '[]',
                'CLEAR', 'Instagram', 'SHORT_VIDEO', 12,
                0, 'like_indicator', 'cricket', 'energetic', 'legacy note'
            )
            """.trimIndent()
        )

        migration().migrate(db)

        db.query(
            "SELECT id, category, platform, ambiguity FROM ground_truths WHERE id = 'gt-legacy'"
        ).use { cursor ->

            assertTrue("legacy ground-truth row survives", cursor.moveToFirst())
            assertEquals("gt-legacy", cursor.getString(0))
            assertEquals("category preserved", "sports", cursor.getString(1))
            assertEquals("platform preserved", "Instagram", cursor.getString(2))
            assertEquals("ambiguity preserved", "CLEAR", cursor.getString(3))
        }
    }

    @Test
    fun legacyRowsReportUnknownForNewSignalsUntilReannotated() {

        db.execSQL(
            """
            INSERT INTO ground_truths (
                id, evaluationItemId, annotatorId, recordedAt,
                category, categoryDomain, secondaryCategories,
                ambiguity, platform, contentType, durationSeconds,
                skipped, interactionSignals, topic, tone, notes
            ) VALUES (
                'gt-legacy', 'item-1', 'rater-a', '2026-08-17T10:00:00',
                'sports', 'sports', '[]',
                'CLEAR', 'Instagram', 'SHORT_VIDEO', 12,
                0, 'like_indicator', 'cricket', 'energetic', NULL
            )
            """.trimIndent()
        )

        migration().migrate(db)

        db.query(
            "SELECT liked, commented, shared, saved, followed, paused, playing " +
                "FROM ground_truths WHERE id = 'gt-legacy'"
        ).use { cursor ->

            assertTrue(cursor.moveToFirst())
            // NULL = UNKNOWN, never auto-filled with false.
            for (i in 0 until 7) {
                assertNull(
                    "new ${cursor.getColumnName(i)} must start UNKNOWN",
                    cursor.let { if (it.isNull(i)) null else it.getInt(i) }
                )
            }
        }
    }

    @Test
    fun triStateColumnsAcceptNullTrueFalse() {

        migration().migrate(db)

        db.execSQL(
            """
            INSERT INTO ground_truths (
                id, evaluationItemId, annotatorId, recordedAt,
                category, categoryDomain, secondaryCategories,
                ambiguity, platform, contentType, durationSeconds,
                skipped, interactionSignals, topic, tone, notes,
                liked, commented, shared, saved, followed,
                paused, playing
            ) VALUES (
                'gt-new', 'item-1', 'rater-a', '2026-08-17T10:01:00',
                'sports', 'sports', '[]',
                'CLEAR', 'Instagram', 'SHORT_VIDEO', 12,
                0, 'liked', 'cricket', 'energetic', NULL,
                1, 0, NULL, NULL, NULL, NULL, NULL
            )
            """.trimIndent()
        )

        db.query(
            "SELECT liked, commented, shared FROM ground_truths WHERE id = 'gt-new'"
        ).use { cursor ->

            assertTrue(cursor.moveToFirst())
            assertEquals("liked TRUE = 1", 1, cursor.getInt(0))
            assertEquals("commented FALSE = 0", 0, cursor.getInt(1))
            assertNull("shared UNKNOWN = NULL", cursor.let { if (it.isNull(2)) null else it.getInt(2) })
        }
    }
}