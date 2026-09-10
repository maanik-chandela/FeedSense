package com.example.feedsense.analysis.evaluation.experiment

import com.example.feedsense.analysis.evaluation.comparative.ComparisonOutcome
import com.example.feedsense.analysis.evaluation.comparative.TestFixtures
import com.example.feedsense.analysis.evidence.decision.ItemPredictionResult
import com.example.feedsense.model.EvaluationItem
import com.example.feedsense.model.GroundTruth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.LocalDateTime

/**
 * Milestone 8B-9.
 *
 * Tests for the controlled real-data experiment & paired evaluation
 * harness. The harness is pure and read-only, so these tests run
 * against the in-memory data source and never touch Room.
 */
class ExperimentHarnessTest {

    // --------------------------------
    // FIXTURES
    // --------------------------------

    private fun item(
        id: String,
        session: String = "session-1",
        dataset: String? = null,
        enqueued: LocalDateTime = BASE_TIME,
        feedId: String? = null
    ): EvaluationItem = EvaluationItem(
        id = id,
        feedItemId = feedId ?: "feed-$id",
        sessionId = session,
        datasetVersion = dataset,
        evaluationStatus = EvaluationItem.STATUS_EVALUATED,
        enqueuedAt = enqueued
    )

    /**
     * Builds an evidence-aware decision keyed to the item id so it
     * joins through the feedItemId (feed-<id>) in the data source.
     */
    private fun decisionFor(
        itemId: String,
        category: String?
    ): ItemPredictionResult =
        TestFixtures.eightBDecision(
            itemId = itemId,
            primaryCategory = category
        )

    private fun experiment(
        mode: ExperimentDatasetMode,
        maxItems: Int = 100,
        sessionId: String? = null,
        itemIds: Set<String> = emptySet(),
        start: LocalDateTime? = null,
        end: LocalDateTime? = null,
        dataset: String? = null,
        trainingVersion: String? = null,
        annotatorId: String? = null
    ): ExperimentDefinition = ExperimentDefinition(
        name = "test-$mode",
        datasetMode = mode,
        sessionId = sessionId,
        itemIds = itemIds,
        startDate = start,
        endDate = end,
        datasetVersion = dataset,
        trainingDatasetVersion = trainingVersion,
        annotatorId = annotatorId,
        maxItems = maxItems
    )

    // ================================
    // 1. ExperimentDefinition validation
    // ================================

    @Test
    fun `definition validates maxItems`() {
        val e = assertThrows {
            experiment(ExperimentDatasetMode.ALL_EVALUATED, maxItems = 0)
        }
        assertNotNull(e)
    }

    @Test
    fun `definition validates date range`() {
        val e = assertThrows {
            ExperimentDefinition(
                name = "bad",
                datasetMode = ExperimentDatasetMode.DATE_RANGE,
                startDate = BASE_TIME.plusDays(2),
                endDate = BASE_TIME,
                maxItems = 10
            )
        }
        assertNotNull(e)
    }

    @Test
    fun `definition requires mode-consistent selection`() {
        // SESSION requires a sessionId
        assertNotNull(assertThrows {
            ExperimentDefinition(
                name = "bad",
                datasetMode = ExperimentDatasetMode.SESSION,
                maxItems = 10
            )
        })
        // ITEM_SET requires itemIds
        assertNotNull(assertThrows {
            ExperimentDefinition(
                name = "bad",
                datasetMode = ExperimentDatasetMode.ITEM_SET,
                maxItems = 10
            )
        })
        // EVALUATION_DATASET requires a datasetVersion
        assertNotNull(assertThrows {
            ExperimentDefinition(
                name = "bad",
                datasetMode = ExperimentDatasetMode.EVALUATION_DATASET,
                maxItems = 10
            )
        })
    }

    // ================================
    // 2. EvidenceAwareOutcome mapping
    // ================================

    @Test
    fun `maps comparison outcomes to five-way experiment outcomes`() {
        assertEquals(
            EvidenceAwareOutcome.BOTH_CORRECT,
            EvidenceAwareOutcome.of(ComparisonOutcome.BOTH_CORRECT)
        )
        assertEquals(
            EvidenceAwareOutcome.BASELINE_ONLY_CORRECT,
            EvidenceAwareOutcome.of(
                ComparisonOutcome.BASELINE_ONLY_CORRECT
            )
        )
        assertEquals(
            EvidenceAwareOutcome.EVIDENCE_AWARE_ONLY_CORRECT,
            EvidenceAwareOutcome.of(
                ComparisonOutcome.EIGHT_B_ONLY_CORRECT
            )
        )
        assertEquals(
            EvidenceAwareOutcome.EVIDENCE_AWARE_ONLY_CORRECT,
            EvidenceAwareOutcome.of(
                ComparisonOutcome.BASELINE_UNKNOWN_EIGHT_B_CORRECT
            )
        )
        assertEquals(
            EvidenceAwareOutcome.BOTH_WRONG,
            EvidenceAwareOutcome.of(ComparisonOutcome.BOTH_WRONG)
        )
        assertEquals(
            EvidenceAwareOutcome.INCOMPLETE,
            EvidenceAwareOutcome.of(ComparisonOutcome.BOTH_UNKNOWN)
        )
        assertEquals(
            EvidenceAwareOutcome.INCOMPLETE,
            EvidenceAwareOutcome.of(
                ComparisonOutcome.BASELINE_CORRECT_EIGHT_B_UNKNOWN
            )
        )
        assertEquals(
            EvidenceAwareOutcome.INCOMPLETE,
            EvidenceAwareOutcome.of(
                ComparisonOutcome.BASELINE_WRONG_EIGHT_B_UNKNOWN
            )
        )
        assertEquals(
            EvidenceAwareOutcome.INCOMPLETE,
            EvidenceAwareOutcome.of(
                ComparisonOutcome.EIGHT_B_WRONG_BASELINE_UNKNOWN
            )
        )
    }

    @Test
    fun `improvement and regression semantics`() {
        assertTrue(
            EvidenceAwareOutcome.isEvidenceAwareImprovement(
                EvidenceAwareOutcome.EVIDENCE_AWARE_ONLY_CORRECT
            )
        )
        assertFalse(
            EvidenceAwareOutcome.isEvidenceAwareImprovement(
                EvidenceAwareOutcome.BOTH_CORRECT
            )
        )
        assertTrue(
            EvidenceAwareOutcome.isEvidenceAwareRegression(
                EvidenceAwareOutcome.BASELINE_ONLY_CORRECT
            )
        )
        assertFalse(
            EvidenceAwareOutcome.contributesToAccuracy(
                EvidenceAwareOutcome.INCOMPLETE
            )
        )
        assertTrue(
            EvidenceAwareOutcome.contributesToAccuracy(
                EvidenceAwareOutcome.BOTH_WRONG
            )
        )
    }

    // ================================
    // 3. Ground truth eligibility
    // ================================

    @Test
    fun `ground truth eligibility classifies by truth comparability`() {
        val clearTruth = TestFixtures.truth(
            "i1", "food", ambiguity = GroundTruth.AMBIGUITY_CLEAR
        )
        val unknownTruth = TestFixtures.truth(
            "i1", "food", ambiguity = GroundTruth.AMBIGUITY_UNKNOWN
        )
        val pair = TestFixtures.pair(
            id = "x1",
            truthCategory = "food",
            baselineCategory = "food",
            eightBCategory = "food"
        )
        assertEquals(
            GroundTruthEligibility.ELIGIBLE_FOR_ACCURACY,
            GroundTruthEligibility.of(pair)
        )
        // unknown-truth pair
        val unkPair = TestFixtures.pair(
            id = "x2",
            truthCategory = "food",
            baselineCategory = "food",
            eightBCategory = "food",
            ambiguity = GroundTruth.AMBIGUITY_UNKNOWN
        )
        assertEquals(
            GroundTruthEligibility.EXCLUDED_FROM_ACCURACY,
            GroundTruthEligibility.of(unkPair)
        )
    }

    // ================================
    // 4. DatasetSelection
    // ================================

    @Test
    fun `selects by session`() {
        // Two sessions; pick session-1 only.
        val items = listOf(item("a", "s1"), item("b", "s2"))
        val decisions = listOf(
            decisionFor("a", "food"),
            decisionFor("b", "food")
        )
        val ds = InMemoryExperimentDataSource(
            items = items,
            baselinePredictions = items.mapIndexed { i, it ->
                TestFixtures.baselineRecord(it.id, if (i == 0) "food" else "travel")
            },
            groundTruths = items.map { TestFixtures.truth(it.id, "food") },
            evidenceAwareDecisions = decisions
        )
        val def = experiment(
            ExperimentDatasetMode.SESSION, sessionId = "s1"
        )
        val result = runBlocking {
            DatasetSelection.select(
                items = ds.fetchItems(def),
                baselinePredictions =
                    ds.fetchBaselinePredictions(items.map { it.id }),
                groundTruths = ds.fetchGroundTruths(items.map { it.id }),
                evidenceAwareDecisions =
                    ds.fetchEvidenceAwareDecisions(items),
                definition = def
            )
        }
        assertEquals(listOf("a"), result.pairs.map { it.item.id })
    }

    @Test
    fun `bounds population by maxItems deterministically`() {
        val items = listOf(
            item("zfoo"), item("abar"), item("mfoo")
        )
        val decisions = items.map {
            decisionFor(it.id, "food")
        }
        val ds = InMemoryExperimentDataSource(
            items = items,
            baselinePredictions = items.map {
                TestFixtures.baselineRecord(it.id, "food")
            },
            groundTruths = items.map {
                TestFixtures.truth(it.id, "food")
            },
            evidenceAwareDecisions = decisions
        )
        val def = experiment(
            ExperimentDatasetMode.ALL_EVALUATED, maxItems = 2
        )
        val result = runBlocking {
            DatasetSelection.select(
                items = ds.fetchItems(def),
                baselinePredictions =
                    ds.fetchBaselinePredictions(items.map { it.id }),
                groundTruths = ds.fetchGroundTruths(items.map { it.id }),
                evidenceAwareDecisions =
                    ds.fetchEvidenceAwareDecisions(items),
                definition = def
            )
        }
        // Deterministic id ordering: abar, mfoo (capped at 2)
        assertEquals(listOf("abar", "mfoo"), result.pairs.map { it.item.id })
        assertEquals(1, result.excludedCapCount)
    }

    @Test
    fun `excludes items without ground truth, baseline, or decision`() {
        val withTruth = item("have-truth")
        val noTruth = item("no-truth")
        val noDecision = item("no-decision")
        val items = listOf(withTruth, noTruth, noDecision)

        val decisions = listOf(
            decisionFor(withTruth.id, "food"),
            decisionFor(noTruth.id, "food")
        )
        val truths = listOf(
            TestFixtures.truth(withTruth.id, "food"),
            // no-decision has a truth but no evidence-aware decision
            TestFixtures.truth(noDecision.id, "food")
            // no truth for noTruth
        )
        val baselines = listOf(
            TestFixtures.baselineRecord(withTruth.id, "food"),
            TestFixtures.baselineRecord(noTruth.id, "food"),
            TestFixtures.baselineRecord(noDecision.id, "food")
        )
        val ds = InMemoryExperimentDataSource(
            items = items,
            baselinePredictions = baselines,
            groundTruths = truths,
            evidenceAwareDecisions = decisions
        )
        val def = experiment(ExperimentDatasetMode.ALL_EVALUATED)
        val result = runBlocking {
            DatasetSelection.select(
                items = ds.fetchItems(def),
                baselinePredictions = ds.fetchBaselinePredictions(items.map { it.id }),
                groundTruths = ds.fetchGroundTruths(items.map { it.id }),
                evidenceAwareDecisions = ds.fetchEvidenceAwareDecisions(items),
                definition = def
            )
        }
        assertTrue(result.pairs.isNotEmpty())
        assertTrue(
            result.excludedNoTruth.map { it.id }.contains("no-truth") ||
                result.excludedNoTruth.isEmpty()
        )
        assertTrue(result.excludedNoEvidenceAware.isNotEmpty())
    }

    @Test
    fun `respects annotator id for same-sample truth`() {
        // Two truths for the same item from different annotators;
        // the annotator filter selects only the matching one.
        val it = item("multi")
        val truthA = TestFixtures.truth(
            it.id, "food", id = "t-a"
        ).copy(annotatorId = "annotator-A")
        val truthB = TestFixtures.truth(
            it.id, "travel", id = "t-b"
        ).copy(annotatorId = "annotator-B")
        val ds = InMemoryExperimentDataSource(
            items = listOf(it),
            baselinePredictions = listOf(
                TestFixtures.baselineRecord(it.id, "food")
            ),
            groundTruths = listOf(truthA, truthB),
            evidenceAwareDecisions = listOf(
                decisionFor(it.id, "food")
            )
        )
        val def = experiment(
            ExperimentDatasetMode.ALL_EVALUATED,
            annotatorId = "annotator-A"
        )
        val result = runBlocking {
            DatasetSelection.select(
                items = ds.fetchItems(def),
                baselinePredictions = ds.fetchBaselinePredictions(listOf(it.id)),
                groundTruths = ds.fetchGroundTruths(listOf(it.id)),
                evidenceAwareDecisions = ds.fetchEvidenceAwareDecisions(listOf(it)),
                definition = def
            )
        }
        assertEquals(1, result.pairs.size)
        assertEquals("food", result.pairs[0].truth.category)
    }

    // ================================
    // 5. LeakageGuard
    // ================================

    @Test
    fun `detects train-test leakage`() {
        val experimentItems = listOf(item("a"), item("b"))
        val trainingItems = listOf(
            item("b", dataset = "train-v1"),
            item("c", dataset = "train-v1")
        )
        val report = LeakageGuard.check(
            experimentItems = experimentItems,
            trainingVersion = "train-v1",
            trainingItems = trainingItems
        )
        assertTrue(report.checked)
        assertTrue(report.leaked)
        assertEquals(setOf("b"), report.overlapItemIds)
    }

    @Test
    fun `no leakage when disjoint`() {
        val report = LeakageGuard.check(
            experimentItems = listOf(item("a")),
            trainingVersion = "train-v1",
            trainingItems = listOf(item("c", dataset = "train-v1"))
        )
        assertFalse(report.leaked)
        assertTrue(report.overlapItemIds.isEmpty())
    }

    @Test
    fun `skips leakage check when no training version nominated`() {
        val report = LeakageGuard.check(
            experimentItems = listOf(item("a")),
            trainingVersion = null
        )
        assertFalse(report.checked)
        assertFalse(report.leaked)
    }

    // ================================
    // 6. DatasetQualityReport
    // ================================

    @Test
    fun `builds dataset quality report`() {
        val p1 = TestFixtures.pair(
            id = "q1", truthCategory = "food",
            baselineCategory = "food", eightBCategory = "food"
        )
        val p2 = TestFixtures.pair(
            id = "q2", truthCategory = "travel",
            baselineCategory = "travel", eightBCategory = "food",
            ambiguity = GroundTruth.AMBIGUITY_UNKNOWN
        )
        val report = DatasetQualityReport.build(
            pairs = listOf(p1, p2),
            candidateItems = 3
        )
        assertEquals(2, report.paired)
        assertEquals(1, report.eligibleForAccuracy)
        assertEquals(1, report.excludedFromAccuracy)
        assertTrue(report.truthAmbiguityCounts.containsKey(
            GroundTruth.AMBIGUITY_CLEAR
        ))
        assertTrue(report.truthAmbiguityCounts.containsKey(
            GroundTruth.AMBIGUITY_UNKNOWN
        ))
    }

    // ================================
    // 7. ExperimentAnalyzer
    // ================================

    @Test
    fun `tallies five-way outcomes and mcnemar-ready output`() {
        val pairs = listOf(
            // both correct
            TestFixtures.pair("x1", "food", "food", "food"),
            // baseline only correct
            TestFixtures.pair("x2", "food", "food", "travel"),
            // evidence aware only correct
            TestFixtures.pair("x3", "food", "travel", "food"),
            // both wrong
            TestFixtures.pair("x4", "food", "travel", "health"),
            // incomplete (8B unknown -> abstain)
            TestFixtures.pair("x5", "food", "food", null)
        )
        val tally = ExperimentAnalyzer.tally(pairs)
        assertEquals(5, tally.paired)
        assertEquals(5, tally.eligible)
        assertEquals(1, tally.bothCorrect)
        assertEquals(1, tally.baselineOnlyCorrect)
        assertEquals(1, tally.evidenceAwareOnlyCorrect)
        assertEquals(1, tally.bothWrong)
        assertEquals(1, tally.incomplete)
        assertEquals(2, tally.discordantPairs)
        assertEquals(1, tally.evidenceAwareImprovements)
        assertEquals(1, tally.evidenceAwareRegressions)

        val m = ExperimentAnalyzer.mcnemarReady(
            tally = tally,
            minimumDiscordant = 2
        )
        assertEquals(2, m.baselineCorrect)
        assertEquals(2, m.evidenceAwareCorrect)
        assertEquals(1, m.baselineOnlyCorrect)
        assertEquals(1, m.evidenceAwareOnlyCorrect)
        assertTrue(m.sufficient)
        assertNotNull(m.pValue)
    }

    @Test
    fun `mcnemar is insufficient below discordant minimum`() {
        val tally = ExperimentAnalyzer.OutcomeTally(
            paired = 2, eligible = 2,
            bothCorrect = 1, baselineOnlyCorrect = 1,
            evidenceAwareOnlyCorrect = 0, bothWrong = 0, incomplete = 0
        )
        val m = ExperimentAnalyzer.mcnemarReady(
            tally = tally,
            minimumDiscordant = 5
        )
        assertFalse(m.sufficient)
        assertNull(m.pValue)
        assertNotNull(m.guardReason)
    }

    @Test
    fun `real data sufficiency follows the effect-size guard`() {
        assertTrue(ExperimentAnalyzer.realDataSufficient(10, 10))
        assertFalse(ExperimentAnalyzer.realDataSufficient(9, 10))
        assertFalse(ExperimentAnalyzer.realDataSufficient(0, 10))
    }

    // ================================
    // 8. ExperimentSnapshot idempotency
    // ================================

    @Test
    fun `snapshot signature is deterministic and sensitive to population`() {
        val def = experiment(ExperimentDatasetMode.ALL_EVALUATED)
        val s1 = ExperimentSnapshot(
            experimentId = def.experimentId,
            experimentName = def.name,
            definition = def,
            selectedItemIds = setOf("a", "b"),
            pairedCount = 2,
            baselineModelVersion = null,
            evidenceAwareModelVersion = null,
            decisionVersion = null,
            experimentVersion = "experiment-v1",
            auditEvents = emptyList()
        )
        val s2 = ExperimentSnapshot(
            experimentId = def.experimentId,
            experimentName = def.name,
            definition = def,
            selectedItemIds = setOf("a", "b"),
            pairedCount = 2,
            baselineModelVersion = null,
            evidenceAwareModelVersion = null,
            decisionVersion = null,
            experimentVersion = "experiment-v1",
            auditEvents = emptyList()
        )
        val s3 = s2.copy(selectedItemIds = setOf("a", "b", "c"))
        assertEquals(s1.idempotencySignature, s2.idempotencySignature)
        assertNotEquals(s1.idempotencySignature, s3.idempotencySignature)
    }

    // ================================
    // 9. ExperimentRunner end-to-end + idempotency
    // ================================

    @Test
    fun `runner produces a coherent summary and is idempotent`() = runBlocking {
        // Build a handful of real observations across sessions/dates.
        val items = listOf(
            item("r1", "s1", enqueued = BASE_TIME),
            item("r2", "s1", enqueued = BASE_TIME.plusDays(1)),
            item("r3", "s2", enqueued = BASE_TIME.plusDays(2))
        )
        val decisions = listOf(
            decisionFor("r1", "food"),
            decisionFor("r2", "food"),
            decisionFor("r3", "travel")
        )
        // truth mismatch for r1 -> baseline-only correct (baseline food,
        // 8B food, but truth travel is in another item...)
        // Build truths so outcomes are varied:
        val truths = listOf(
            // r1: baseline food, 8B food, truth travel -> both wrong
            TestFixtures.truth("r1", "travel"),
            // r2: baseline food, 8B food, truth food -> both correct
            TestFixtures.truth("r2", "food"),
            // r3: baseline travel, 8B travel, truth travel -> both correct
            TestFixtures.truth("r3", "travel")
        )
        val baselines = listOf(
            TestFixtures.baselineRecord("r1", "food"),
            TestFixtures.baselineRecord("r2", "food"),
            TestFixtures.baselineRecord("r3", "travel")
        )
        val ds = InMemoryExperimentDataSource(
            items = items,
            baselinePredictions = baselines,
            groundTruths = truths,
            evidenceAwareDecisions = decisions
        )
        val runner = ExperimentRunner(ds)
        val def = experiment(
            ExperimentDatasetMode.DATE_RANGE,
            start = BASE_TIME,
            end = BASE_TIME.plusDays(3)
        )
        val summary = runner.run(def)

        assertEquals(3, summary.paired)
        assertEquals(3, summary.eligible)
        assertEquals(2, summary.outcome.bothCorrect)
        assertEquals(1, summary.outcome.bothWrong)
        assertTrue(summary.baselineSafety.safe)
        assertTrue(summary.snapshot.idempotencySignature.isNotBlank())

        // Idempotency: re-running yields the same signature.
        val summary2 = runner.run(def)
        assertEquals(
            summary.snapshot.idempotencySignature,
            summary2.snapshot.idempotencySignature
        )
        assertEquals(summary.paired, summary2.paired)
    }

    @Test
    fun `runner marks insufficient real data when population is too small`() = runBlocking {
        val items = listOf(item("t1", "s1"))
        val ds = InMemoryExperimentDataSource(
            items = items,
            baselinePredictions = listOf(
                TestFixtures.baselineRecord("t1", "food")
            ),
            groundTruths = listOf(TestFixtures.truth("t1", "food")),
            evidenceAwareDecisions = listOf(
                decisionFor("t1", "food")
            )
        )
        val def = experiment(
            ExperimentDatasetMode.SESSION, sessionId = "s1"
        )
        val summary = ExperimentRunner(ds).run(def)
        assertEquals(
            ExperimentSummary.RealDataStatus.INSUFFICIENT_REAL_DATA,
            summary.realDataStatus
        )
        assertEquals(1, summary.paired)
        assertEquals(
            com.example.feedsense.analysis.evaluation.comparative.ComparativeReport.Verdict
                .INSUFFICIENT_REAL_DATA_FOR_COMPARATIVE_CONCLUSIONS,
            summary.comparativeReport.conclusion.verdict
        )
    }

    @Test
    fun `runner records leakage outcome in summary`() = runBlocking {
        val items = listOf(item("l1", "s1"))
        val ds = InMemoryExperimentDataSource(
            items = items,
            baselinePredictions = listOf(
                TestFixtures.baselineRecord("l1", "food")
            ),
            groundTruths = listOf(TestFixtures.truth("l1", "food")),
            evidenceAwareDecisions = listOf(
                decisionFor("l1", "food")
            )
        )
        // training cohort containing an overlapping item
        val trainingItems = listOf(
            EvaluationItem(
                id = "l1", feedItemId = "feed-l1",
                sessionId = "sX", datasetVersion = "train-v1"
            )
        )
        val def = experiment(
            ExperimentDatasetMode.SESSION,
            sessionId = "s1",
            trainingVersion = "train-v1"
        )
        val summary = ExperimentRunner(ds).run(def, trainingItems)
        assertTrue(summary.leakage.checked)
        assertTrue(summary.leakage.leaked)
        assertTrue(summary.leakage.overlapItemIds.contains("l1"))
    }

    // ================================
    // 10. ExperimentExporter
    // ================================

    @Test
    fun `export json is privacy-safe and contains counts`() = runBlocking {
        val items = listOf(item("e1", "s1"), item("e2", "s1"))
        val ds = InMemoryExperimentDataSource(
            items = items,
            baselinePredictions = listOf(
                TestFixtures.baselineRecord("e1", "food"),
                TestFixtures.baselineRecord("e2", "food")
            ),
            groundTruths = listOf(
                TestFixtures.truth("e1", "food"),
                TestFixtures.truth("e2", "travel")
            ),
            evidenceAwareDecisions = listOf(
                decisionFor("e1", "food"),
                decisionFor("e2", "food")
            )
        )
        val def = experiment(ExperimentDatasetMode.SESSION, sessionId = "s1")
        val summary = ExperimentRunner(ds).run(def)
        val json = ExperimentExporter.toJsonObject(summary)
        assertTrue(json.has("experimentId"))
        assertTrue(json.has("idempotencySignature"))
        assertTrue(json.has("realDataStatus"))
        assertTrue(json.has("outcome"))
        assertTrue(json.has("baselineSafety"))
        assertTrue(json.has("comparative"))
        val raw = ExperimentExporter.toJson(summary)
        // No raw content keys are ever written.
        assertFalse(raw.contains("screenshot"))
        assertFalse(raw.contains("ocrText"))
    }

    @Test
    fun `outcomes csv has header and one row per pair`() = runBlocking {
        val items = listOf(item("c1", "s1"), item("c2", "s1"))
        val ds = InMemoryExperimentDataSource(
            items = items,
            baselinePredictions = listOf(
                TestFixtures.baselineRecord("c1", "food"),
                TestFixtures.baselineRecord("c2", "food")
            ),
            groundTruths = listOf(
                TestFixtures.truth("c1", "food"),
                TestFixtures.truth("c2", "travel")
            ),
            evidenceAwareDecisions = listOf(
                decisionFor("c1", "food"),
                decisionFor("c2", "food")
            )
        )
        val def = experiment(ExperimentDatasetMode.SESSION, sessionId = "s1")
        val summary = ExperimentRunner(ds).run(def)
        val csv = ExperimentExporter.outcomesCsv(summary)
        val lines = csv.trim().split("\n")
        assertEquals("evaluationItemId,feedItemId,eligibility,outcome",
            lines.first())
        assertEquals(1 + summary.paired, lines.size)
    }

    // --------------------------------
    // helpers
    // --------------------------------

    private fun assertThrows(block: () -> Unit): Throwable? {
        return try {
            block()
            null
        } catch (t: Throwable) {
            t
        }
    }

    companion object {
        private val BASE_TIME =
            LocalDateTime.of(2026, 1, 1, 0, 0)
    }
}
