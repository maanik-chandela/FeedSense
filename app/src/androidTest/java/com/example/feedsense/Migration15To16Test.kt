package com.example.feedsense

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.feedsense.FeedSenseApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Milestone 7P. Migration test for MIGRATION_15_16.
 *
 * Builds a real SQLite database at version 15 (the
 * exact feed_items schema that existed before this
 * milestone), seeds it with a row, applies the
 * migration and verifies:
 *
 *   - both new columns exist,
 *   - their defaults are applied to the existing row,
 *   - no existing column was dropped or changed.
 *
 * Non-destructive by construction (ALTER ... ADD only).
 */
@RunWith(AndroidJUnit4::class)
class Migration15To16Test {

    private lateinit var db: androidx.sqlite.db.SupportSQLiteDatabase

    private val v15FeedItems =
        """
        CREATE TABLE IF NOT EXISTS feed_items (
            id TEXT NOT NULL PRIMARY KEY,
            sessionId TEXT NOT NULL,
            startTime TEXT NOT NULL,
            endTime TEXT,
            durationSeconds INTEGER NOT NULL,
            category TEXT,
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
            pausedDurationSeconds INTEGER NOT NULL,
            activeWatchDurationSeconds INTEGER NOT NULL,
            uncertaintyLevel TEXT NOT NULL
        )
        """.trimIndent()

    private val seedRow =
        """
        INSERT INTO feed_items (
            id, sessionId, startTime, durationSeconds, category,
            contentType, skipped, representativeFramePath, frameCount,
            interactionSignals, needsReview, candidateCategories,
            updatedAt, contentTransitions, interactionEvidence,
            pausedDurationSeconds, activeWatchDurationSeconds,
            uncertaintyLevel
        ) VALUES (
            'item-1', 'session-1', '2026-01-01T10:00:00', 35,
            'comedy', 'SHORT_VIDEO', 0, '/tmp/frame.png', 5, 'watched', 0,
            'comedy', '2026-01-01T10:00:35', 'CONTENT_STARTED',
            '', 0, 35, 'LOW'
        )
        """.trimIndent()

    @Before
    fun setUp() {

        val context =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext

        val fileName =
            "migration_15_16_${System.currentTimeMillis()}.db"

        db =
            FrameworkSQLiteOpenHelperFactory()
                .create(
                    androidx.sqlite.db.SupportSQLiteOpenHelper
                        .Configuration
                        .builder(context)
                        .name(fileName)
                        .callback(
                            object :
                                androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(15) {

                                override fun onConfigure(
                                    db: androidx.sqlite.db.SupportSQLiteDatabase
                                ) {
                                }

                                override fun onCreate(
                                    db: androidx.sqlite.db.SupportSQLiteDatabase
                                ) {
                                    db.execSQL(v15FeedItems)
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

        db.execSQL(seedRow)
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

        return application.MIGRATION_15_16
    }

    @Test
    fun migrationAddsColumnsWithDefaultsAndKeepsData() {

        assertTrue(
            "precondition: v15 has no secondaryCategories",
            columnNames().none {
                it == "secondaryCategories"
            }
        )

        migration().migrate(db)

        val columns =
            columnNames()

        assertTrue(
            "secondaryCategories column exists",
            "secondaryCategories" in columns
        )

        assertTrue(
            "mixedContent column exists",
            "mixedContent" in columns
        )

        val row =
            db.query(
                "SELECT id, secondaryCategories, mixedContent, " +
                        "durationSeconds, category " +
                        "FROM feed_items WHERE id = 'item-1'"
            ).use { cursor ->

                assertTrue(cursor.moveToFirst())

                Triple(
                    cursor.getString(1),
                    cursor.getInt(2),
                    cursor.getString(4)
                )
            }

        assertEquals(
            "existing row keeps its data",
            "comedy",
            row.third
        )

        assertEquals(
            "secondaryCategories defaults to empty",
            "",
            row.first
        )

        assertEquals(
            "mixedContent defaults to false",
            0,
            row.second
        )
    }

    @Test
    fun migrationPreservesAllLegacyColumns() {

        val before =
            columnNames().toSet()

        migration().migrate(db)

        val after =
            columnNames().toSet()

        before.forEach {
            assertTrue(
                "column $it must survive the migration",
                it in after
            )
        }

        assertEquals(
            "exactly two columns were added",
            before.size + 2,
            after.size
        )

        assertFalse(
            "row still readable after migration",
            db.query(
                "SELECT id FROM feed_items WHERE id = 'item-1'"
            ).use {
                it.count <= 0
            }
        )
    }

    private fun columnNames(): List<String> {

        return db
            .query("PRAGMA table_info(feed_items)")
            .use { cursor ->

                buildList {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(1))
                    }
                }
            }
    }
}
