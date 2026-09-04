package com.example.feedsense

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.feedsense.database.FeedSenseDatabase
import com.example.feedsense.model.EvaluationItem
import com.example.feedsense.model.EvaluationRecord
import com.example.feedsense.model.GroundTruth
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/*
 * Milestone 8A-2. DAO-level test against a REAL Room
 * in-memory database at version 25.
 *
 * Verifies the primitives the annotation workflow builds on:
 *   - insert + retrieve evaluation items / ground truth /
 *     results
 *   - re-edit UPDATE keeps a single current ground-truth row
 *   - queue reads by status and by session+status
 */
@RunWith(AndroidJUnit4::class)
class EvaluationDaoTest {

    private lateinit var db: FeedSenseDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            FeedSenseDatabase::class.java
        )
            .allowMainThreadQueries()
            .fallbackToDestructiveMigration()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private val dao = db.evaluationDao()

    private fun newItem(
        id: String,
        sessionId: String = "s1",
        status: String = EvaluationItem.STATUS_NOT_EVALUATED
    ) = EvaluationItem(
        id = id,
        feedItemId = "feed-$id",
        sessionId = sessionId,
        evaluationStatus = status
    )

    @Test
    fun insertAndRetrieveItem_byIdAndByStatus() = runBlocking {

        dao.insertItem(newItem("a", sessionId = "s1"))
        dao.insertItem(newItem("b", sessionId = "s1"))
        dao.insertItem(newItem("c", sessionId = "s2"))

        assertNotNull(dao.getItemById("a"))

        val unreviewed = dao.getItemsByStatus(
            EvaluationItem.STATUS_NOT_EVALUATED
        )
        assertEquals(3, unreviewed.size)

        val s1Items = dao.getItemsForSession("s1")
        assertEquals(2, s1Items.size)
    }

    @Test
    fun groundTruth_updateKeepsSingleCurrentRow() = runBlocking {

        val item = newItem("a")
        dao.insertItem(item)

        val first = GroundTruth(
            evaluationItemId = "a",
            category = "sports",
            ambiguity = GroundTruth.AMBIGUITY_CLEAR
        )
        dao.insertGroundTruth(first)

        // Re-edit updates the SAME row.
        val edited = first.copy(
            category = "comedy",
            liked = true
        )
        dao.updateGroundTruth(edited)

        val rows = dao.getGroundTruthsForItem("a")
        assertEquals(
            "re-edited ground truth stays a single row",
            1,
            rows.size
        )
        val row = rows.single()
        assertEquals("comedy", row.category)
        assertEquals(true, row.liked)
    }

    @Test
    fun groundTruth_isScopedToItemAndAnnotator() = runBlocking {

        dao.insertItem(newItem("a"))

        val rowA = GroundTruth(
            evaluationItemId = "a",
            category = "sports",
            ambiguity = GroundTruth.AMBIGUITY_CLEAR
        ).copy(annotatorId = "rater-a")
        val rowB = rowA.copy(
            annotatorId = "rater-b",
            category = "comedy"
        )
        dao.insertGroundTruth(rowA)
        dao.insertGroundTruth(rowB)

        val forA = dao.getGroundTruthForItemAndAnnotator("a", "rater-a")
        assertNotNull(forA)
        assertEquals("sports", forA!!.category)

        val forB = dao.getGroundTruthForItemAndAnnotator("a", "rater-b")
        assertEquals("comedy", forB!!.category)

        // No row for a never-used annotator.
        assertNull(dao.getGroundTruthForItemAndAnnotator("a", "rater-c"))
    }

    @Test
    fun result_isReplaced_notAppended_forGroundTruth() = runBlocking {

        dao.insertItem(newItem("a"))

        val truth = GroundTruth(
            evaluationItemId = "a",
            category = "sports",
            ambiguity = GroundTruth.AMBIGUITY_CLEAR
        )
        dao.insertGroundTruth(truth)

        val result = EvaluationRecord(
            evaluationItemId = "a",
            groundTruthId = truth.id,
            aiPredictionId = "pred-1",
            verdict = EvaluationRecord.VERDICT_CORRECT
        )
        dao.insertResult(result)

        val replacement = result.copy(
            verdict = EvaluationRecord.VERDICT_INCORRECT
        )
        dao.updateResult(replacement)

        assertEquals(1, dao.getResultsForItem("a").size)
        assertEquals(
            EvaluationRecord.VERDICT_INCORRECT,
            dao.getResultForGroundTruth(truth.id)!!.verdict
        )
    }

    @Test
    fun queueReads_respectStatusAndSessionLimit() = runBlocking {

        // 6 unreviewed in s1 (enqueued in order), 1 reviewed, 2 unreviewed in s2.
        for (i in 1..6) {
            dao.insertItem(newItem("u$i", sessionId = "s1"))
        }
        dao.insertItem(
            newItem("r1", sessionId = "s1", status = EvaluationItem.STATUS_EVALUATED)
        )
        dao.insertItem(newItem("v1", sessionId = "s2"))
        dao.insertItem(newItem("v2", sessionId = "s2"))

        val global = dao.getItemsByStatusLimit(
            EvaluationItem.STATUS_NOT_EVALUATED,
            limit = 3
        )
        assertEquals(
            "bounded global queue respects the limit",
            3,
            global.size
        )

        val perSession = dao.getItemsForSessionAndStatusLimit(
            "s1",
            EvaluationItem.STATUS_NOT_EVALUATED,
            limit = 2
        )
        assertEquals(2, perSession.size)
    }
}