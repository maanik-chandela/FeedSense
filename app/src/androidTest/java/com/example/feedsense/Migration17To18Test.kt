package com.example.feedsense

import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.feedsense.FeedSenseApplication
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/*
 * Milestone 7S. Migration test for MIGRATION_17_18.
 *
 * Builds a real SQLite database at version 17 and
 * verifies the migration adds the frameFingerprint
 * column to captured_frames (nullable TEXT, existing
 * rows preserved) without touching anything else.
 */
@RunWith(AndroidJUnit4::class)
class Migration17To18Test {

    private lateinit var db: androidx.sqlite.db.SupportSQLiteDatabase

    /*
     * The exact captured_frames schema that existed at
     * version 17 (BEFORE MIGRATION_17_18 added the
     * frameFingerprint column).
     */
    private val v17CapturedFrames =
        """
        CREATE TABLE IF NOT EXISTS captured_frames (
            id TEXT NOT NULL PRIMARY KEY,
            sessionId TEXT NOT NULL,
            filePath TEXT NOT NULL,
            capturedAt TEXT NOT NULL,
            analysisStatus TEXT NOT NULL DEFAULT 'PENDING',
            analysisResult TEXT,
            analyzedAt TEXT
        )
        """.trimIndent()

    @Before
    fun setUp() {

        val context =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext

        val fileName =
            "migration_17_18_${System.currentTimeMillis()}.db"

        db =
            FrameworkSQLiteOpenHelperFactory()
                .create(
                    androidx.sqlite.db.SupportSQLiteOpenHelper
                        .Configuration
                        .builder(context)
                        .name(fileName)
                        .callback(
                            object :
                                androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(17) {

                                override fun onConfigure(
                                    db: androidx.sqlite.db.SupportSQLiteDatabase
                                ) {
                                }

                                override fun onCreate(
                                    db: androidx.sqlite.db.SupportSQLiteDatabase
                                ) {
                                    db.execSQL(v17CapturedFrames)
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

        return application.MIGRATION_17_18
    }

    private fun tableNames(): Set<String> {

        return db
            .query(
                "SELECT name FROM sqlite_master " +
                        "WHERE type = 'table'"
            )
            .use { cursor ->

                buildSet {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(0))
                    }
                }
            }
    }

    private fun columnsOf(table: String): Set<String> {

        return db
            .query("PRAGMA table_info($table)")
            .use { cursor ->

                buildSet {
                    while (cursor.moveToNext()) {
                        add(cursor.getString(1))
                    }
                }
            }
    }

    @Test
    fun migrationAddsFingerprintColumnToCapturedFrames() {

        migration().migrate(db)

        val columns =
            columnsOf("captured_frames")

        assertTrue(
            "frameFingerprint column added",
            "frameFingerprint" in columns
        )
    }

    @Test
    fun migrationIsNonDestructive() {

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS captured_frames (
                id TEXT NOT NULL PRIMARY KEY,
                sessionId TEXT NOT NULL,
                filePath TEXT NOT NULL,
                capturedAt TEXT NOT NULL,
                analysisStatus TEXT NOT NULL DEFAULT 'PENDING',
                analysisResult TEXT,
                analyzedAt TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO captured_frames (
                id, sessionId, filePath, capturedAt, analysisStatus
            ) VALUES (
                'frame-1', 'session-1', '/tmp/a.jpg', '2026-01-01T00:00:00', 'ANALYZED'
            )
            """.trimIndent()
        )

        migration().migrate(db)

        db.query(
            "SELECT frameFingerprint FROM captured_frames WHERE id = 'frame-1'"
        ).use { cursor ->

            assertTrue(cursor.moveToFirst())
            assertTrue(
                "existing row preserved with NULL fingerprint",
                cursor.isNull(0)
            )
        }

        db.query(
            "SELECT COUNT(*) FROM captured_frames WHERE id = 'frame-1'"
        ).use { cursor ->

            assertTrue(cursor.moveToFirst())
            assertTrue(
                "row still present after migration",
                cursor.getInt(0) == 1
            )
        }
    }

    @Test
    fun migrationDoesNotTouchOtherTables() {

        val before =
            tableNames()

        migration().migrate(db)

        val after =
            tableNames()

        assertFalse(
            "no tables created by MIGRATION_17_18",
            after.size > before.size
        )
    }
}
