package com.example.feedsense

import com.example.feedsense.analysis.evaluation.Eligibility
import com.example.feedsense.analysis.evaluation.EvaluatorConfig
import com.example.feedsense.dao.EvaluationDao
import com.example.feedsense.dao.EvaluationRunDao
import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.EvaluationItem
import com.example.feedsense.model.EvaluationRecord
import com.example.feedsense.model.EvaluationRun
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.GroundTruth
import com.example.feedsense.repository.EvaluationRepository
import com.example.feedsense.repository.EvaluationRunRepository
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8A-3. End-to-end run against fake DAOs: enqueue +
 * annotate via the existing repository, then run the read-only
 * engine and verify a frozen EvaluationRun is persisted with
 * the correct cohort counts, provenance and report JSON.
 */
class EvaluationRunRepositoryTest {

    class FakeEvaluationRunDao : EvaluationRunDao {
        val runs = mutableMapOf<String, EvaluationRun>()
        override suspend fun insertRun(run: EvaluationRun) {
            runs[run.id] = run
        }

        override suspend fun getRecentRuns(limit: Int): List<EvaluationRun> {
            return runs.values.sortedByDescending { it.createdAt }.take(limit)
        }

        override suspend fun getRunByRunId(runId: String): EvaluationRun? =
            runs.values.firstOrNull { it.runId == runId }

        override suspend fun getRunById(id: String): EvaluationRun? = runs[id]

        override suspend fun getRunsForDataset(
            datasetVersion: String
        ): List<EvaluationRun> =
            runs.values.filter { it.datasetVersion == datasetVersion }

        override suspend fun countRuns(): Int = runs.size
    }

    // Reuse the fake EvaluationDao from the repository test by
    // extending the same behaviors inline.
    private class FakeEvaluationDao : EvaluationDao {
        val items = mutableMapOf<String, EvaluationItem>()
        val predictions = mutableMapOf<String, AiPredictionRecord>()
        val truths = mutableMapOf<String, GroundTruth>()
        val results = mutableMapOf<String, EvaluationRecord>()

        override suspend fun insertItem(item: EvaluationItem) { items[item.id] = item }
        override suspend fun getItemById(id: String): EvaluationItem? = items[id]
        override suspend fun getItemsForFeedItem(feedItemId: String): List<EvaluationItem> =
            items.values.filter { it.feedItemId == feedItemId }
        override suspend fun getItemsForSession(sessionId: String): List<EvaluationItem> =
            items.values.filter { it.sessionId == sessionId }
        override suspend fun getItemsForDataset(datasetVersion: String): List<EvaluationItem> =
            items.values.filter { it.datasetVersion == datasetVersion }
        override suspend fun getItemsByStatus(status: String): List<EvaluationItem> =
            items.values.filter { it.evaluationStatus == status }
        override suspend fun updateItemStatus(id: String, status: String, completedAt: String?) {
            val c = items[id] ?: return
            items[id] = c.copy(evaluationStatus = status, completedAt = completedAt?.let(LocalDateTime::parse))
        }
        override suspend fun assignDatasetVersion(id: String, datasetVersion: String) {
            val c = items[id] ?: return
            items[id] = c.copy(datasetVersion = datasetVersion)
        }
        override suspend fun insertPrediction(prediction: AiPredictionRecord) { predictions[prediction.id] = prediction }
        override suspend fun getPredictionForItem(itemId: String): AiPredictionRecord? =
            predictions.values.firstOrNull { it.evaluationItemId == itemId }
        override suspend fun getPredictionsForItems(itemIds: Collection<String>): List<AiPredictionRecord> =
            predictions.values.filter { it.evaluationItemId in itemIds }
        override suspend fun insertGroundTruth(truth: GroundTruth) { truths[truth.id] = truth }
        override suspend fun getGroundTruthById(id: String): GroundTruth? = truths[id]
        override suspend fun getGroundTruthsForItem(itemId: String): List<GroundTruth> =
            truths.values.filter { it.evaluationItemId == itemId }
        override suspend fun getGroundTruthsForItems(itemIds: Collection<String>): List<GroundTruth> =
            truths.values.filter { it.evaluationItemId in itemIds }
        override suspend fun updateGroundTruth(truth: GroundTruth) { truths[truth.id] = truth }
        override suspend fun getGroundTruthForItemAndAnnotator(itemId: String, annotatorId: String?): GroundTruth? =
            truths.values.firstOrNull { it.evaluationItemId == itemId && it.annotatorId == annotatorId }
        override suspend fun insertResult(result: EvaluationRecord) { results[result.id] = result }
        override suspend fun getResultById(id: String): EvaluationRecord? = results[id]
        override suspend fun updateResult(result: EvaluationRecord) { results[result.id] = result }
        override suspend fun getResultForGroundTruth(groundTruthId: String): EvaluationRecord? =
            results.values.firstOrNull { it.groundTruthId == groundTruthId }
        override suspend fun getItemsByStatusLimit(status: String, limit: Int): List<EvaluationItem> =
            items.values.filter { it.evaluationStatus == status }.take(limit)
        override suspend fun getItemsForSessionAndStatus(sessionId: String, status: String): List<EvaluationItem> =
            items.values.filter { it.sessionId == sessionId && it.evaluationStatus == status }
        override suspend fun getItemsForSessionAndStatusLimit(sessionId: String, status: String, limit: Int): List<EvaluationItem> =
            items.values.filter { it.sessionId == sessionId && it.evaluationStatus == status }.take(limit)
        override suspend fun getResultsForItem(itemId: String): List<EvaluationRecord> =
            results.values.filter { it.evaluationItemId == itemId }
        override suspend fun getResultsForItems(itemIds: Collection<String>): List<EvaluationRecord> =
            results.values.filter { it.evaluationItemId in itemIds }
        override suspend fun getResultsForDataset(datasetVersion: String): List<EvaluationRecord> =
            results.values.filter { it.datasetVersion == datasetVersion }
        override suspend fun countCorrect(): Int = results.values.count { it.verdict == EvaluationRecord.VERDICT_CORRECT }
        override suspend fun countTotal(): Int = results.size
    }

    private fun makeFeedItem(
        id: String,
        category: String,
        confidence: Double? = 0.9
    ) = FeedItem(
        id = id,
        sessionId = "session-1",
        startTime = LocalDateTime.now(),
        endTime = LocalDateTime.now().plusSeconds(30),
        durationSeconds = 30,
        category = category,
        confidence = confidence,
        contentType = FeedItem.CONTENT_SHORT_VIDEO,
        skipped = false,
        representativeFramePath = "/frames/$id.jpg",
        frameCount = 3,
        modelVersion = "model-v1",
        secondaryCategories = listOf("comedy"),
        categoryScores = mapOf("sports" to 0.9, "comedy" to 0.5),
        platform = "Instagram",
        topic = "cricket",
        tone = "energetic",
        interactionSignals = listOf("like_indicator")
    )

    @Test
    fun runEvaluation_persistsFrozenReportWithCorrectCounts() {
        val dao = FakeEvaluationDao()
        val runDao = FakeEvaluationRunDao()
        val evalRepo = EvaluationRepository(dao)
        val runRepo = EvaluationRunRepository(dao, runDao)

        runBlocking {
            val a = evalRepo.enqueueItem(makeFeedItem("feed-a", "sports"), "session-1")
            val b = evalRepo.enqueueItem(makeFeedItem("feed-b", "sports"), "session-1")
            val c = evalRepo.enqueueItem(makeFeedItem("feed-c", "comedy"), "session-1")

            // Annotate all three as REVIEWED.
            listOf(
                a to "sports",
                b to "sports",
                c to "sports"
            ).forEach { (item, category) ->
                evalRepo.saveAnnotation(
                    evaluationItemId = item.id,
                    draft = GroundTruth(
                        evaluationItemId = item.id,
                        category = category,
                        ambiguity = GroundTruth.AMBIGUITY_CLEAR
                    ),
                    annotatorId = "rater-a"
                )
            }

            // Assign the cohort to a frozen dataset.
            val ids = listOf(a.id, b.id, c.id)
            evalRepo.assignDatasetVersion(ids, "ds-v1")

            val run = runRepo.runEvaluation(
                datasetVersion = "ds-v1",
                config = EvaluatorConfig(),
                modelVersion = "model-v1",
                description = "first run"
            )

            assertEquals("ds-v1", run.datasetVersion)
            assertEquals("model-v1", run.modelVersion)
            assertEquals(3, run.totalItems)
            assertEquals(3, run.eligibleItems)
            assertEquals(0, run.excludedItems)
            assertTrue(run.reportJson.contains("\"categoryMetrics\""))
            assertTrue(run.reportJson.contains("ds-v1"))
            assertEquals("first run", run.description)
            assertEquals(1, runDao.runs.size)
        }
    }

    @Test
    fun runEvaluation_excludesUnreviewedAndDisputed() {
        val dao = FakeEvaluationDao()
        val runDao = FakeEvaluationRunDao()
        val evalRepo = EvaluationRepository(dao)
        val runRepo = EvaluationRunRepository(dao, runDao)

        runBlocking {
            val reviewed =
                evalRepo.enqueueItem(makeFeedItem("feed-a", "sports"), "session-1")
            val unreviewed =
                evalRepo.enqueueItem(makeFeedItem("feed-b", "sports"), "session-1")

            evalRepo.saveAnnotation(
                evaluationItemId = reviewed.id,
                draft = GroundTruth(
                    evaluationItemId = reviewed.id,
                    category = "sports",
                    ambiguity = GroundTruth.AMBIGUITY_CLEAR
                ),
                annotatorId = "rater-a"
            )
            // Mark the other disputed.
            evalRepo.markDisputed(unreviewed.id)

            val ids = listOf(reviewed.id, unreviewed.id)
            evalRepo.assignDatasetVersion(ids, "ds-v1")

            val run = runRepo.runEvaluation(
                datasetVersion = "ds-v1",
                config = EvaluatorConfig(includeDisputed = false)
            )

            // Only the reviewed item is eligible; the disputed one
            // is excluded (and NOT for lack of data).
            assertEquals(1, run.eligibleItems)
            assertEquals(1, run.excludedItems)
            assertEquals(2, run.totalItems)
            assertTrue(run.reportJson.contains("DISPUTED"))
        }
    }

    @Test
    fun includeDisputedViaConfigBringThemIn() {
        val dao = FakeEvaluationDao()
        val runDao = FakeEvaluationRunDao()
        val evalRepo = EvaluationRepository(dao)
        val runRepo = EvaluationRunRepository(dao, runDao)

        runBlocking {
            val a = evalRepo.enqueueItem(makeFeedItem("feed-a", "sports"), "session-1")
            val b = evalRepo.enqueueItem(makeFeedItem("feed-b", "sports"), "session-1")
            evalRepo.saveAnnotation(
                a.id,
                GroundTruth(evaluationItemId = a.id, category = "sports", ambiguity = GroundTruth.AMBIGUITY_CLEAR),
                "rater-a"
            )
            evalRepo.saveAnnotation(
                b.id,
                GroundTruth(evaluationItemId = b.id, category = "sports", ambiguity = GroundTruth.AMBIGUITY_CLEAR),
                "rater-a"
            )
            evalRepo.markDisputed(b.id)
            evalRepo.assignDatasetVersion(listOf(a.id, b.id), "ds-v1")

            val run = runRepo.runEvaluation(
                datasetVersion = "ds-v1",
                config = EvaluatorConfig(includeDisputed = true)
            )
            assertEquals(2, run.eligibleItems)
            assertEquals(0, run.excludedItems)
        }
    }

    @Test
    fun eligibility_unreviewedIsAlwaysExcludedEvenWhenUserAddsStatus() {
        // Even if someone tried to add NOT_EVALUATED to the
        // eligible set, the engine's Eligibility defends against
        // counting unreviewed items.
        val items = listOf(
            EvaluationItem(
                feedItemId = "f1",
                sessionId = "s1",
                evaluationStatus = EvaluationItem.STATUS_NOT_EVALUATED
            )
        )
        val config = EvaluatorConfig(
            eligibleStatuses = setOf(EvaluationItem.STATUS_NOT_EVALUATED)
        )
        val outcome = Eligibility.select(
            items = items,
            config = config,
            hasPrediction = { true },
            hasTruth = { true },
            hasResult = { true }
        )
        assertTrue(outcome.eligible.isEmpty())
        assertEquals(1, outcome.excluded.size)
        assertTrue(outcome.excluded[0].reason is Eligibility.Reason.UNREVIEWED)
    }
}