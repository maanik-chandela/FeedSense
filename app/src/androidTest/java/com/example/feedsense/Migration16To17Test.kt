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
 * Milestone 7Q. Migration test for MIGRATION_16_17.
 *
 * Builds a real SQLite database at version 16 and
 * verifies the migration creates the two cloud-usage
 * audit tables with the exact columns the Room entity
 * expects, without touching anything else.
 */
@RunWith(AndroidJUnit4::class)
class Migration16To17Test {

    private lateinit var db: androidx.sqlite.db.SupportSQLiteDatabase

    @Before
    fun setUp() {

        val context =
            InstrumentationRegistry
                .getInstrumentation()
                .targetContext

        val fileName =
            "migration_16_17_${System.currentTimeMillis()}.db"

        db =
            FrameworkSQLiteOpenHelperFactory()
                .create(
                    androidx.sqlite.db.SupportSQLiteOpenHelper
                        .Configuration
                        .builder(context)
                        .name(fileName)
                        .callback(
                            object :
                                androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(16) {

                                override fun onConfigure(
                                    db: androidx.sqlite.db.SupportSQLiteDatabase
                                ) {
                                }

                                override fun onCreate(
                                    db: androidx.sqlite.db.SupportSQLiteDatabase
                                ) {
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

        return application.MIGRATION_16_17
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
    fun migrationCreatesCloudUsageTables() {

        assertFalse(
            "precondition: v16 has no cloud tables",
            tableNames().any {
                it.startsWith("cloud_usage")
            }
        )

        migration().migrate(db)

        val tables =
            tableNames()

        assertTrue(
            "cloud_usage_records created",
            "cloud_usage_records" in tables
        )

        assertTrue(
            "cloud_usage_state created",
            "cloud_usage_state" in tables
        )
    }

    @Test
    fun recordsTableHasExpectedColumns() {

        migration().migrate(db)

        val columns =
            columnsOf("cloud_usage_records")

        setOf(
            "id",
            "sessionId",
            "frameId",
            "feedItemId",
            "filePath",
            "requestedAt",
            "requestType",
            "estimatedCostRupees",
            "allowed",
            "reason",
            "modelVersion"
        ).forEach {
            assertTrue("missing column $it", it in columns)
        }
    }

    @Test
    fun stateTableHasExpectedColumns() {

        migration().migrate(db)

        val columns =
            columnsOf("cloud_usage_state")

        setOf(
            "id",
            "monthKey",
            "dayKey",
            "monthlyEstimatedRupees",
            "dailyEstimatedRupees",
            "requestCount",
            "lastUpdated"
        ).forEach {
            assertTrue("missing column $it", it in columns)
        }
    }
}
