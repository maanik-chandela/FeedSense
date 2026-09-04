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
 * Milestone 8A-3. Migration test for MIGRATION_25_26.
 *
 * Builds a real SQLite database at version 25 with the 8A-1/8A-2
 * evaluation layer + an existing ground_truth row, then verifies
 * the migration:
 *   - is non-destructive (existing evaluation rows survive)
 *   - creates evaluation_runs with the expected schema
 *   - the new table accepts a run insert with the JSON payloads
 */
@RunWith(AndroidJUnit4::class)
class Migration25To26Test {

    private lateinit var db: androidx.sqlite.db.SupportSQLiteDatabase

    @Before
    fun setUp() {

        val context =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext

        val fileName =
            "migration_25_26_${System.currentTimeMillis()}.db"

        db =
            FrameworkSQLiteOpenHelperFactory()
                .create(
                    androidx.sqlite.db.SupportSQLiteOpenHelper
                        .Configuration
                        .builder(context)
                        .name(fileName)
                        .callback(
                            object :
                                androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(25) {

                                override fun onConfigure(
                                    db: androidx.sqlite.db.SupportSQLiteDatabase
                                ) {
                                }

                                override fun onCreate(
                                    db: androidx.sqlite.db.SupportSQLiteDatabase
                                ) {
                                    db.execSQL(
                                        """
                                        CREATE TABLE IF NOT EXISTS evaluation_items (
                                            id TEXT NOT NULL PRIMARY KEY,
                                            feedItemId TEXT NOT NULL,
                                            sessionId TEXT NOT NULL,
                                            projectId TEXT,
                                            modelVersion TEXT,
                                            datasetVersion TEXT,
                                            evaluationStatus TEXT NOT NULL,
                                            createdAt TEXT NOT NULL,
                                            enqueuedAt TEXT NOT NULL,
                                            completedAt TEXT
                                        )
                                        """.trimIndent()
                                    )
                                    db.execSQL(
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
                                            liked INTEGER, commented INTEGER,
                                            shared INTEGER, saved INTEGER,
                                            followed INTEGER, paused INTEGER, playing INTEGER,
                                            interactionSignals TEXT NOT NULL,
                                            topic TEXT, tone TEXT, notes TEXT
                                        )
                                        """.trimIndent()
                                    )
                                    db.execSQL(
                                        """
                                        INSERT INTO ground_truths (
                                            id, evaluationItemId, annotatorId, recordedAt,
                                            category, secondaryCategories, ambiguity,
                                            platform, contentType, interactionSignals
                                        ) VALUES (
                                            'gt-legacy', 'item-1', 'rater-a', '2026-08-17T10:00:00',
                                            'sports', '[]', 'CLEAR',
                                            'Instagram', 'SHORT_VIDEO', ''
                                        )
                                        """.trimIndent()
                                    )
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
        return application.MIGRATION_25_26
    }

    private fun columns(): Set<String> {
        return db
            .query("PRAGMA table_info(evaluation_runs)")
            .use { cursor ->
                buildSet {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(1))
                    }
                }
            }
    }

    @Test
    fun migrationCreatesEvaluationRunsTable() {
        migration().migrate(db)
        val cols = columns()
        val expected = setOf(
            "id", "runId", "datasetVersion", "modelVersion",
            "evaluationMethodVersion", "createdAt",
            "configJson", "reportJson",
            "totalItems", "eligibleItems", "excludedItems", "description"
        )
        assertTrue("all expected columns present", cols.containsAll(expected))
    }

    @Test
    fun migrationIsNonDestructiveToExistingTables() {
        migration().migrate(db)
        db.query(
            "SELECT category, platform FROM ground_truths WHERE id = 'gt-legacy'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("sports", cursor.getString(0))
            assertEquals("Instagram", cursor.getString(1))
        }
    }

    @Test
    fun evaluationRunsAcceptsReportInsert() {
        migration().migrate(db)
        db.execSQL(
            """
            INSERT INTO evaluation_runs (
                id, runId, datasetVersion, modelVersion,
                evaluationMethodVersion, createdAt,
                configJson, reportJson,
                totalItems, eligibleItems, excludedItems, description
            ) VALUES (
                'run-1', 'RUN-ABC', 'ds-v1', 'model-v1',
                '1.0.0', '2026-09-02T10:00:00',
                '{"eligibleStatuses":["EVALUATED"]}',
                '{"runId":"RUN-ABC","categoryMetrics":{}}',
                10, 8, 2, 'first run'
            )
            """.trimIndent()
        )
        db.query(
            "SELECT reportJson, totalItems, eligibleItems FROM evaluation_runs WHERE runId = 'RUN-ABC'"
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.getString(0).contains("RUN-ABC"))
            assertEquals(10, cursor.getInt(1))
            assertEquals(8, cursor.getInt(2))
        }
    }
}