package com.example.feedsense

import com.example.feedsense.analysis.evaluation.Eligibility
import com.example.feedsense.analysis.evaluation.EvaluationEngine
import com.example.feedsense.analysis.evaluation.EvaluationUnit
import com.example.feedsense.analysis.evaluation.EvaluatorConfig
import com.example.feedsense.analysis.evaluation.MetricValue
import com.example.feedsense.analysis.evaluation.ReportJson
import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.EvaluationItem
import com.example.feedsense.model.EvaluationRecord
import com.example.feedsense.model.GroundTruth
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8A-3. Engine-level integration tests over pure
 * EvaluationUnit inputs: cross-section metrics, exclusion
 * handling, verdict distribution, error records, and report
 * serialization.
 */
class EvaluationEngineTest {

    private fun item(
        id: String,
        status: String = EvaluationItem.STATUS_EVALUATED,
        datasetVersion: String? = "ds-v1",
        modelVersion: String? = "model-v1"
    ) = EvaluationItem(
        id = id,
        feedItemId = "feed-$id",
        sessionId = "s1",
        datasetVersion = datasetVersion,
        modelVersion = modelVersion,
        evaluationStatus = status
    )

    private fun prediction(
        itemId: String,
        category: String? = "sports",
        confidence: Double? = 0.9,
        durationSeconds: Int = 30,
        platform: String? = "Instagram",
        contentType: String? = "SHORT_VIDEO",
        modelVersion: String? = "model-v1",
        source: String? = "AI"
    ) = AiPredictionRecord(
        evaluationItemId = itemId,
        category = category,
        confidence = confidence,
        durationSeconds = durationSeconds,
        platform = platform,
        contentType = contentType,
        modelVersion = modelVersion,
        source = source,
        skipped = false,
        interactionSignals = listOf("liked"),
        topic = "cricket",
        tone = "energetic"
    )

    private fun truth(
        itemId: String,
        category: String? = "sports",
        ambiguity: String = GroundTruth.AMBIGUITY_CLEAR,
        durationSeconds: Int? = 30,
        platform: String? = "Instagram",
        contentType: String? = "SHORT_VIDEO",
        annotatorId: String? = "rater-a"
    ) = GroundTruth(
        evaluationItemId = itemId,
        annotatorId = annotatorId,
        category = category,
        ambiguity = ambiguity,
        durationSeconds = durationSeconds,
        platform = platform,
        contentType = contentType,
        liked = true,
        skipped = false,
        topic = "cricket",
        tone = "energetic"
    )

    private fun unit(
        evaluationItem: EvaluationItem,
        pred: AiPredictionRecord,
        truth: GroundTruth
    ): EvaluationUnit {
        val result = EvaluationRecord.fromComponents(pred, truth)
        return EvaluationUnit(
            item = evaluationItem,
            prediction = pred,
            truth = truth,
            result = result
        )
    }

    @Test
    fun perfectCategory_allCorrectMetrics() {
        val i = item("i1")
        val u = unit(i, prediction("i1", category = "sports"), truth("i1", category = "sports"))

        val report = EvaluationEngine.evaluate(
            units = listOf(u),
            excluded = emptyList(),
            config = EvaluatorConfig()
        )

        assertTrue(report.categoryMetrics!!.aggregates.microAccuracy.defined)
        assertEquals(1.0, report.categoryMetrics.aggregates.microAccuracy.value!!, 1e-9)
        assertEquals(1, report.sampleSummary.corrected)
        assertEquals(1, report.eligibleItems)
        assertEquals(1, report.verdictDistribution[EvaluationRecord.VERDICT_CORRECT])
    }

    @Test
    fun mixedItemReportedAsPartialNotDropped() {
        val i = item("i1")
        // AI primary=sports with secondary=comedy; truth primary=comedy,
        // ambiguity MIXED, secondary=sports => EvaluationRecord sees
        // category mismatch but secondaryMatch true => PARTIAL.
        val pred = prediction("i1", category = "sports").copy(
            secondaryCategories = listOf("comedy")
        )
        val truth = truth(
            "i1",
            category = "comedy",
            ambiguity = GroundTruth.AMBIGUITY_MIXED
        ).copy(secondaryCategories = listOf("sports"))

        val report = EvaluationEngine.evaluate(
            units = listOf(unit(i, pred, truth)),
            excluded = emptyList(),
            config = EvaluatorConfig()
        )
        assertEquals(1, report.sampleSummary.partial)
        assertTrue(report.verdictDistribution.containsKey(EvaluationRecord.VERDICT_PARTIAL))

        // PARTIAL is not a definitive verdict, so it must not inflate
        // strict category accuracy denominators.
        assertEquals(MetricValue.State.UNDEFINED, report.categoryMetrics!!.aggregates.microAccuracy.state)
        assertEquals(0, report.categoryMetrics.sampleCount)
    }

    @Test
    fun unknownTruthEntity_neverCountedAsFalse() {
        val i = item("i1")
        // AI says sports with high confidence, but truth is
        // AMBIGUITY_UNKNOWN => verdict UNKNOWN, never INCORRECT.
        val truthUnknown = truth("i1", category = null, ambiguity = GroundTruth.AMBIGUITY_UNKNOWN)

        val report = EvaluationEngine.evaluate(
            units = listOf(unit(i, prediction("i1", category = "sports"), truthUnknown)),
            excluded = emptyList(),
            config = EvaluatorConfig()
        )
        assertEquals(1, report.sampleSummary.unknown)
        assertEquals(0, report.sampleSummary.incorrect)
        assertEquals(0, report.errorRecords.size)
        // Calibration needs definitive verdicts only => none.
        assertEquals(0, report.calibrationMetrics!!.sampleCount)
    }

    @Test
    fun exclusionsRecordedAndReported() {
        val reviewed = item("i1", status = EvaluationItem.STATUS_EVALUATED)
        val disputed = item("i2", status = EvaluationItem.STATUS_DISPUTED)
        val unreviewed = item("i3", status = EvaluationItem.STATUS_NOT_EVALUATED)

        val units = listOf(
            unit(reviewed, prediction("i1"), truth("i1")),
            unit(disputed, prediction("i2"), truth("i2"))
        )

        val config = EvaluatorConfig(includeDisputed = false)
        val excluded = listOf(
            Eligibility.ExcludedItem(disputed, Eligibility.Reason.DISPUTED_EXCLUDED),
            Eligibility.ExcludedItem(unreviewed, Eligibility.Reason.UNREVIEWED)
        )

        val report = EvaluationEngine.evaluate(
            units = units,
            excluded = excluded,
            config = config
        )

        assertEquals(2, report.eligibleItems)
        assertEquals(2, report.excludedItems)
        assertEquals(4, report.totalItems)
        val reasons = report.exclusions.map { it.reason }.toSet()
        assertTrue("DISPUTED" in reasons)
        assertTrue("UNREVIEWED" in reasons)
    }

    @Test
    fun errorRecordsCarryProvenanceFields() {
        val i = item("i1")
        // Wrong category + wrong duration.
        val pred = prediction("i1", category = "sports", durationSeconds = 40)
        val truthWrong = truth("i1", category = "comedy", durationSeconds = 30)

        val report = EvaluationEngine.evaluate(
            units = listOf(unit(i, pred, truthWrong)),
            excluded = emptyList(),
            config = EvaluatorConfig(),
            feedItemIds = mapOf(i.id to "feed-i1")
        )

        assertEquals(1, report.errorRecords.size)
        val err = report.errorRecords[0]
        assertEquals("i1", err.evaluationItemId)
        assertEquals("feed-i1", err.feedItemId)
        assertEquals("sports", err.predictedCategory)
        assertEquals("comedy", err.truthCategory)
        assertEquals(0.9, err.confidence!!, 1e-9)
        assertEquals(10, err.durationErrorSeconds) // 40 - 30
    }

    @Test
    fun reportSerializesWithAllSectionsAndDenominators() {
        val i1 = item("i1")
        val i2 = item("i2")
        val units = listOf(
            unit(i1, prediction("i1", category = "sports"), truth("i1", category = "sports")),
            unit(i2, prediction("i2", category = "comedy"), truth("i2", category = "sports"))
        )
        val report = EvaluationEngine.evaluate(
            units = units,
            excluded = emptyList(),
            config = EvaluatorConfig(),
            datasetVersion = "ds-v1",
            modelVersion = "model-v1"
        )

        val json = ReportJson.toJson(report)
        assertTrue(json.contains("\"runId\""))
        assertTrue(json.contains("\"categoryMetrics\""))
        assertTrue(json.contains("\"multiLabelMetrics\""))
        assertTrue(json.contains("\"calibrationMetrics\""))
        assertTrue(json.contains("\"datasetBalance\""))
        assertTrue(json.contains("\"errorRecords\""))
        assertTrue(json.contains("ds-v1"))
        assertTrue(json.contains("model-v1"))
    }

    @Test
    fun datasetBalanceReportsCompositionAndImbalance() {
        val units = listOf(
            unit(item("i1"), prediction("i1", category = "sports"), truth("i1", category = "sports")),
            unit(item("i2"), prediction("i2", category = "sports"), truth("i2", category = "sports")),
            unit(item("i3"), prediction("i3", category = "comedy"), truth("i3", category = "comedy"))
        )
        val report = EvaluationEngine.evaluate(
            units = units,
            excluded = emptyList(),
            config = EvaluatorConfig()
        )
        val balance = report.datasetBalance
        assertEquals(3, balance.totalItems)
        val categoryCounts = balance.byCategory.toMap()
        assertEquals(2, categoryCounts["sports"])
        assertEquals(1, categoryCounts["comedy"])
        assertTrue(balance.notes.any { it.startsWith("category imbalance ratio") })
    }

    @Test
    fun deterministicAcrossRuns_SameConfigSameNumbers() {
        val units = listOf(
            unit(item("i1"), prediction("i1", category = "sports"), truth("i1", category = "sports"))
        )
        val fixedAt = LocalDateTime.of(2026, 9, 2, 10, 0)
        val a = EvaluationEngine.evaluate(
            units, emptyList(), EvaluatorConfig(),
            runId = "RUN-A", createdAt = fixedAt
        )
        val b = EvaluationEngine.evaluate(
            units, emptyList(), EvaluatorConfig(),
            runId = "RUN-A", createdAt = fixedAt
        )
        assertEquals(ReportJson.toJson(a), ReportJson.toJson(b))
    }
}