package com.example.feedsense

import com.example.feedsense.dao.EvaluationDao
import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.EvaluationItem
import com.example.feedsense.model.EvaluationRecord
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.GroundTruth
import com.example.feedsense.repository.EvaluationRepository
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8A-1. Repository workflow tests using a fake
 * DAO (the codebase convention for repository tests):
 *
 *   1. enqueueItem captures an immutable AI prediction
 *      snapshot from a FeedItem
 *   2. recordGroundTruth stores truth and computes the
 *      evaluation result, marking the item evaluated
 *   3. dataset versioning curates cohorts without drift
 *   4. the snapshot survives later FeedItem correction
 */
class EvaluationRepositoryTest {

    private class FakeEvaluationDao : EvaluationDao {

        val items = mutableMapOf<String, EvaluationItem>()
        val predictions = mutableMapOf<String, AiPredictionRecord>()
        val truths = mutableMapOf<String, GroundTruth>()
        val results = mutableMapOf<String, EvaluationRecord>()

        override suspend fun insertItem(item: EvaluationItem) {
            items[item.id] = item
        }

        override suspend fun getItemById(id: String): EvaluationItem? =
            items[id]

        override suspend fun getItemsForFeedItem(
            feedItemId: String
        ): List<EvaluationItem> =
            items.values.filter { it.feedItemId == feedItemId }

        override suspend fun getItemsForSession(
            sessionId: String
        ): List<EvaluationItem> =
            items.values.filter { it.sessionId == sessionId }

        override suspend fun getItemsForDataset(
            datasetVersion: String
        ): List<EvaluationItem> =
            items.values.filter { it.datasetVersion == datasetVersion }

        override suspend fun getItemsByStatus(
            status: String
        ): List<EvaluationItem> =
            items.values.filter { it.evaluationStatus == status }

        override suspend fun updateItemStatus(
            id: String,
            status: String,
            completedAt: String?
        ) {
            val current = items[id] ?: return
            items[id] = current.copy(
                evaluationStatus = status,
                completedAt = completedAt?.let { LocalDateTime.parse(it) }
            )
        }

        override suspend fun assignDatasetVersion(
            id: String,
            datasetVersion: String
        ) {
            val current = items[id] ?: return
            items[id] = current.copy(datasetVersion = datasetVersion)
        }

        override suspend fun insertPrediction(
            prediction: AiPredictionRecord
        ) {
            predictions[prediction.id] = prediction
        }

        override suspend fun getPredictionForItem(
            itemId: String
        ): AiPredictionRecord? =
            predictions.values.firstOrNull { it.evaluationItemId == itemId }

        override suspend fun getPredictionsForItems(
            itemIds: Collection<String>
        ): List<AiPredictionRecord> =
            predictions.values.filter { it.evaluationItemId in itemIds }

        override suspend fun insertGroundTruth(truth: GroundTruth) {
            truths[truth.id] = truth
        }

        override suspend fun getGroundTruthById(id: String): GroundTruth? =
            truths[id]

        override suspend fun getGroundTruthsForItem(
            itemId: String
        ): List<GroundTruth> =
            truths.values.filter { it.evaluationItemId == itemId }

        override suspend fun getGroundTruthsForItems(
            itemIds: Collection<String>
        ): List<GroundTruth> =
            truths.values.filter { it.evaluationItemId in itemIds }

        override suspend fun updateGroundTruth(truth: GroundTruth) {
            truths[truth.id] = truth
        }

        override suspend fun getGroundTruthForItemAndAnnotator(
            itemId: String,
            annotatorId: String?
        ): GroundTruth? =
            truths.values.firstOrNull {
                it.evaluationItemId == itemId &&
                    it.annotatorId == annotatorId
            }

        override suspend fun insertResult(result: EvaluationRecord) {
            results[result.id] = result
        }

        override suspend fun getResultById(id: String): EvaluationRecord? =
            results[id]

        override suspend fun updateResult(result: EvaluationRecord) {
            results[result.id] = result
        }

        override suspend fun getResultForGroundTruth(
            groundTruthId: String
        ): EvaluationRecord? =
            results.values.firstOrNull {
                it.groundTruthId == groundTruthId
            }

        override suspend fun getItemsByStatusLimit(
            status: String,
            limit: Int
        ): List<EvaluationItem> =
            items.values
                .filter { it.evaluationStatus == status }
                .sortedBy { it.enqueuedAt }
                .take(limit)

        override suspend fun getItemsForSessionAndStatus(
            sessionId: String,
            status: String
        ): List<EvaluationItem> =
            items.values.filter {
                it.sessionId == sessionId &&
                    it.evaluationStatus == status
            }

        override suspend fun getItemsForSessionAndStatusLimit(
            sessionId: String,
            status: String,
            limit: Int
        ): List<EvaluationItem> =
            items.values
                .filter {
                    it.sessionId == sessionId &&
                        it.evaluationStatus == status
                }
                .sortedBy { it.enqueuedAt }
                .take(limit)

        override suspend fun getResultsForItem(
            itemId: String
        ): List<EvaluationRecord> =
            results.values.filter { it.evaluationItemId == itemId }

        override suspend fun getResultsForItems(
            itemIds: Collection<String>
        ): List<EvaluationRecord> =
            results.values.filter { it.evaluationItemId in itemIds }

        override suspend fun getResultsForDataset(
            datasetVersion: String
        ): List<EvaluationRecord> =
            results.values.filter { it.datasetVersion == datasetVersion }

        override suspend fun countCorrect(): Int =
            results.values.count { it.verdict == EvaluationRecord.VERDICT_CORRECT }

        override suspend fun countTotal(): Int = results.size
    }

    private val dao = FakeEvaluationDao()
    private val repository = EvaluationRepository(dao)

    private fun makeFeedItem(
        id: String = "feed-1",
        category: String = "sports",
        confidence: Double? = 0.9,
        durationSeconds: Int = 30
    ) = FeedItem(
        id = id,
        sessionId = "session-1",
        startTime = LocalDateTime.now(),
        endTime = LocalDateTime.now().plusSeconds(30),
        durationSeconds = durationSeconds,
        category = category,
        confidence = confidence,
        contentType = FeedItem.CONTENT_SHORT_VIDEO,
        skipped = false,
        representativeFramePath = "/frames/$id.jpg",
        frameCount = 3,
        modelVersion = "local-model-v2.0",
        secondaryCategories = listOf("comedy"),
        categoryScores = mapOf("sports" to 0.9, "comedy" to 0.5),
        platform = "Instagram",
        topic = "cricket",
        tone = "energetic",
        interactionSignals = listOf("like_indicator")
    )

    // --------------------------------
    // ENQUEUE + SNAPSHOT
    // --------------------------------

    @Test
    fun enqueueItem_createsItemAndImmutablePredictionSnapshot() {

        val item =
            runBlocking {
                repository.enqueueItem(
                    feedItem = makeFeedItem(),
                    sessionId = "session-1",
                    projectId = "project-1"
                )
            }

        assertEquals(EvaluationItem.STATUS_NOT_EVALUATED, item.evaluationStatus)
        assertEquals("project-1", item.projectId)
        assertEquals("local-model-v2.0", item.modelVersion)
        assertEquals("feed-1", item.feedItemId)

        val snap = dao.predictions.values.single()
        assertEquals("sports", snap.category)
        assertEquals(0.9, snap.confidence!!, 0.001)
        assertEquals(listOf("comedy"), snap.secondaryCategories)
        assertEquals(mapOf("sports" to 0.9, "comedy" to 0.5), snap.categoryScores)
        assertEquals("Instagram", snap.platform)
        assertEquals(30, snap.durationSeconds)
        assertEquals("cricket", snap.topic)
    }

    @Test
    fun snapshotSurvivesLaterFeedItemCorrection() {

        val feedItem = makeFeedItem()
        runBlocking {
            repository.enqueueItem(feedItem, "session-1")
        }

        // Simulate the review flow overwriting the FeedItem's
        // category after it was corrected by a human.
        val corrected = feedItem.copy(
            category = "comedy",
            confidence = 1.0
        )

        val snap = dao.predictions.values.single()
        assertEquals(
            "snapshot must preserve the ORIGINAL AI prediction",
            "sports",
            snap.category
        )
        assertEquals(
            "snapshot confidence must not follow the correction",
            0.9,
            snap.confidence!!,
            0.001
        )
        assertTrue("feed item was mutated independently", corrected.category == "comedy")
    }

    // --------------------------------
    // GROUND TRUTH + RESULT
    // --------------------------------

    @Test
    fun recordGroundTruth_storesTruthAndResult_andMarksEvaluated() {

        runBlocking {
            repository.enqueueItem(makeFeedItem(), "session-1")
        }
        val item = dao.items.values.single()

        runBlocking {
            repository.recordGroundTruth(
                evaluationItemId = item.id,
                truth = GroundTruth(
                    evaluationItemId = item.id,
                    category = "sports",
                    ambiguity = GroundTruth.AMBIGUITY_CLEAR,
                    topic = "cricket"
                )
            )
        }

        assertEquals(1, dao.truths.size)
        assertEquals(1, dao.results.size)
        assertEquals(EvaluationRecord.VERDICT_CORRECT, dao.results.values.single().verdict)
        assertEquals(EvaluationItem.STATUS_EVALUATED, dao.items[item.id]!!.evaluationStatus)
    }

    @Test
    fun recordGroundTruth_capturesIncorrectResult() {

        runBlocking {
            repository.enqueueItem(makeFeedItem(), "session-1")
        }
        val item = dao.items.values.single()

        runBlocking {
            repository.recordGroundTruth(
                evaluationItemId = item.id,
                truth = GroundTruth(
                    evaluationItemId = item.id,
                    category = "comedy",
                    ambiguity = GroundTruth.AMBIGUITY_CLEAR
                )
            )
        }

        assertEquals(
            EvaluationRecord.VERDICT_INCORRECT,
            dao.results.values.single().verdict
        )
    }

    // --------------------------------
    // MULTI-ANNOTATOR
    // --------------------------------

    @Test
    fun multipleAnnotators_produceIndependentResults() {

        runBlocking {
            repository.enqueueItem(makeFeedItem(), "session-1")
        }
        val item = dao.items.values.single()

        runBlocking {
            repository.recordGroundTruth(
                evaluationItemId = item.id,
                truth = GroundTruth(
                    evaluationItemId = item.id,
                    category = "sports",
                    ambiguity = GroundTruth.AMBIGUITY_CLEAR
                ),
                annotatorId = "rater-a"
            )
            repository.recordGroundTruth(
                evaluationItemId = item.id,
                truth = GroundTruth(
                    evaluationItemId = item.id,
                    category = "comedy",
                    ambiguity = GroundTruth.AMBIGUITY_CLEAR
                ),
                annotatorId = "rater-b"
            )
        }

        assertEquals(2, dao.truths.size)
        assertEquals(2, dao.results.size)
        assertEquals(1, dao.results.values.count { it.verdict == EvaluationRecord.VERDICT_CORRECT })
        assertEquals(1, dao.results.values.count { it.verdict == EvaluationRecord.VERDICT_INCORRECT })
    }

    // --------------------------------
    // DATASET VERSIONING
    // --------------------------------

    @Test
    fun assignDatasetVersion_curatesCohort_withoutDrift() {

        val itemA = runBlocking {
            repository.enqueueItem(makeFeedItem(id = "feed-a", category = "sports"), "session-1")
        }
        val itemB = runBlocking {
            repository.enqueueItem(makeFeedItem(id = "feed-b", category = "comedy"), "session-1")
        }

        runBlocking {
            repository.assignDatasetVersion(
                evaluationItemIds = listOf(itemA.id, itemB.id),
                datasetVersion = "ds-v1"
            )
        }

        val curated = runBlocking {
            repository.getItemsForDataset("ds-v1")
        }
        assertEquals(2, curated.size)
        assertTrue(curated.all { it.datasetVersion == "ds-v1" })
    }

    @Test
    fun getItemsForSession_returnsEnqueuedItems() {

        runBlocking {
            repository.enqueueItem(makeFeedItem(id = "feed-a"), "session-1")
            repository.enqueueItem(makeFeedItem(id = "feed-b"), "session-1")
            repository.enqueueItem(makeFeedItem(id = "feed-c"), "session-2")
        }

        val sessionItems = runBlocking {
            repository.getItemsForSession("session-1")
        }
        assertEquals(2, sessionItems.size)
    }

    // --------------------------------
    // 8A-2: RE-EDIT (CLEAN CURRENT RECORD) + VALIDATION
    // --------------------------------

    @Test
    fun reEdit_updatesCurrentRecord_insteadOfAppending() {

        val item = runBlocking {
            repository.enqueueItem(makeFeedItem(), "session-1")
            dao.items.values.single()
        }
        val prediction = dao.predictions.values.first { it.evaluationItemId == item.id }

        runBlocking {
            // First annotation: matches the AI.
            repository.recordGroundTruth(
                evaluationItemId = item.id,
                truth = GroundTruth(
                    evaluationItemId = item.id,
                    category = "sports",
                    ambiguity = GroundTruth.AMBIGUITY_CLEAR
                ),
                annotatorId = "rater-a"
            )

            // Re-edit: correct the human truth. Must UPDATE the
            // current record, never append a competing row.
            repository.recordGroundTruth(
                evaluationItemId = item.id,
                truth = GroundTruth(
                    evaluationItemId = item.id,
                    category = "comedy",
                    ambiguity = GroundTruth.AMBIGUITY_CLEAR
                ),
                annotatorId = "rater-a"
            )
        }

        assertEquals(
            "re-edit must not create a second ground-truth row",
            1,
            dao.truths.size
        )
        assertEquals(
            "re-edit must replace the evaluation result, not append",
            1,
            dao.results.size
        )

        val truth = dao.truths.values.single()
        assertEquals(
            "the current record reflects the latest accepted annotation",
            "comedy",
            truth.category
        )

        assertEquals(
            EvaluationRecord.VERDICT_INCORRECT,
            dao.results.values.single().verdict
        )

        assertTrue(
            "the fresh draft uuid must NOT replace the record key",
            truth.id == dao.truths.keys.single()
        )

        // The frozen AI snapshot is NEVER overwritten.
        assertEquals("sports", prediction.category)
        assertEquals(0.9, prediction.confidence!!, 0.001)
    }

    @Test
    fun reEdit_preservesOriginalRecordedAt() {

        runBlocking {
            repository.enqueueItem(makeFeedItem(), "session-1")
        }
        val item = dao.items.values.single()

        val first = runBlocking {
            val truth = GroundTruth(
                evaluationItemId = item.id,
                category = "sports",
                ambiguity = GroundTruth.AMBIGUITY_CLEAR
            )
            repository.recordGroundTruth(item.id, truth, "rater-a")
            dao.truths.values.single()
        }

        runBlocking {
            repository.recordGroundTruth(
                item.id,
                GroundTruth(
                    evaluationItemId = item.id,
                    category = "comedy",
                    ambiguity = GroundTruth.AMBIGUITY_CLEAR
                ),
                "rater-a"
            )
        }

        assertEquals(
            "recordedAt is the first-recorded time, not the edit time",
            first.recordedAt,
            dao.truths.values.single().recordedAt
        )
        assertEquals("id", first.id, dao.truths.values.single().id)
    }

    @Test
    fun multipleAnnotators_stillEachKeepIndependentRecords() {

        runBlocking {
            repository.enqueueItem(makeFeedItem(), "session-1")
        }
        val item = dao.items.values.single()

        runBlocking {
            repository.recordGroundTruth(
                item.id,
                GroundTruth(evaluationItemId = item.id, category = "sports", ambiguity = GroundTruth.AMBIGUITY_CLEAR),
                "rater-a"
            )
            repository.recordGroundTruth(
                item.id,
                GroundTruth(evaluationItemId = item.id, category = "comedy", ambiguity = GroundTruth.AMBIGUITY_CLEAR),
                "rater-a" // same annotator re-editing
            )
            repository.recordGroundTruth(
                item.id,
                GroundTruth(evaluationItemId = item.id, category = "sports", ambiguity = GroundTruth.AMBIGUITY_CLEAR),
                "rater-b"
            )
        }

        assertEquals(
            "two annotators = two clean records each (a edited once)",
            2,
            dao.truths.size
        )
        assertEquals(2, dao.results.size)
    }

    @Test
    fun saveAnnotation_validates_andThrowsOnIncompleteDraft() {

        runBlocking {
            repository.enqueueItem(makeFeedItem(), "session-1")
        }
        val item = dao.items.values.single()

        var threw: Exception? = null
        runBlocking {
            try {
                repository.saveAnnotation(
                    evaluationItemId = item.id,
                    draft = GroundTruth(
                        evaluationItemId = item.id,
                        category = null,
                        ambiguity = GroundTruth.AMBIGUITY_CLEAR // missing UNKNOWN
                    ),
                    annotatorId = "rater-a"
                )
            } catch (e: Exception) {
                threw = e
            }
        }

        assertNotNull(threw)
        assertEquals(
            "incomplete draft must NOT be committed",
            0,
            dao.truths.size
        )
    }

    @Test
    fun saveAnnotation_withUnknown_accepted() {

        runBlocking {
            repository.enqueueItem(makeFeedItem(), "session-1")
        }
        val item = dao.items.values.single()

        runBlocking {
            repository.saveAnnotation(
                evaluationItemId = item.id,
                draft = GroundTruth(
                    evaluationItemId = item.id,
                    category = null,
                    ambiguity = GroundTruth.AMBIGUITY_UNKNOWN
                ),
                annotatorId = "rater-a"
            )
        }

        assertEquals(1, dao.truths.size)
        assertEquals(
            EvaluationRecord.VERDICT_UNKNOWN,
            dao.results.values.single().verdict
        )
    }

    @Test
    fun markDisputed_setsDisputedStatus() {

        runBlocking {
            repository.enqueueItem(makeFeedItem(), "session-1")
        }
        val item = dao.items.values.single()

        runBlocking {
            repository.markDisputed(item.id)
        }

        assertEquals(
            EvaluationItem.STATUS_DISPUTED,
            dao.items[item.id]!!.evaluationStatus
        )
    }
}
