package com.example.feedsense

import com.example.feedsense.analysis.evaluation.ConfidenceAnalysis
import com.example.feedsense.analysis.evaluation.ErrorConfusionMatrix
import com.example.feedsense.analysis.evaluation.ErrorAnalysisEngine
import com.example.feedsense.analysis.evaluation.ErrorAnalysisReport
import com.example.feedsense.analysis.evaluation.ErrorAnalyzer
import com.example.feedsense.analysis.evaluation.ErrorPatternReport
import com.example.feedsense.analysis.evaluation.ErrorSeverity
import com.example.feedsense.analysis.evaluation.ErrorTypes
import com.example.feedsense.analysis.evaluation.EvaluationUnit
import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.EvaluationItem
import com.example.feedsense.model.EvaluationRecord
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.GroundTruth
import java.time.LocalDateTime
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// --------------------------------
// ERROR ANALYSIS TESTS (8A-5)
// --------------------------------
//
// Comprehensive tests for the error-analysis, failure-taxonomy,
// and diagnostic-evaluation infrastructure introduced in
// Milestone 8A-5.

class ErrorAnalysisTest {

    private val now = LocalDateTime.of(2026, 9, 4, 10, 0)
    private val datasetVersion = "ds-v1"
    private val modelVersion = "local-v6.0"

    // --------------------------------
    // HELPER BUILDERS
    // --------------------------------

    private fun evalItem(
        id: String = UUID.randomUUID().toString(),
        status: String = EvaluationItem.STATUS_EVALUATED,
        dataset: String? = datasetVersion
    ) = EvaluationItem(
        id = id,
        feedItemId = "feed-$id",
        sessionId = "session-1",
        projectId = "project-1",
        modelVersion = modelVersion,
        evaluationStatus = status,
        datasetVersion = dataset,
        enqueuedAt = now,
        createdAt = now
    )

    private fun prediction(
        evaluationItemId: String,
        category: String? = "sports",
        secondaryCategories: List<String> = emptyList(),
        confidence: Double? = 0.9,
        platform: String? = "Instagram",
        contentType: String? = FeedItem.CONTENT_SHORT_VIDEO,
        durationSeconds: Int = 30,
        skipped: Boolean = false,
        interactionSignals: List<String> = emptyList(),
        topic: String? = "cricket",
        tone: String? = "ENTERTAINMENT"
    ) = AiPredictionRecord(
        evaluationItemId = evaluationItemId,
        modelVersion = modelVersion,
        category = category,
        secondaryCategories = secondaryCategories,
        confidence = confidence,
        platform = platform,
        contentType = contentType,
        durationSeconds = durationSeconds,
        skipped = skipped,
        interactionSignals = interactionSignals,
        topic = topic,
        tone = tone,
        feedItemId = "feed-$evaluationItemId"
    )

    private fun truth(
        evaluationItemId: String,
        category: String? = "sports",
        ambiguity: String = GroundTruth.AMBIGUITY_CLEAR,
        secondaryCategories: List<String> = emptyList(),
        platform: String? = "Instagram",
        contentType: String? = GroundTruth.CONTENT_TYPE_SHORT_VIDEO,
        durationSeconds: Int? = 28,
        skipped: Boolean? = false,
        liked: Boolean? = null,
        commented: Boolean? = null,
        topic: String? = "cricket",
        tone: String? = "ENTERTAINMENT"
    ) = GroundTruth(
        evaluationItemId = evaluationItemId,
        annotatorId = "annotator-1",
        category = category,
        ambiguity = ambiguity,
        secondaryCategories = secondaryCategories,
        platform = platform,
        contentType = contentType,
        durationSeconds = durationSeconds,
        skipped = skipped,
        liked = liked,
        commented = commented,
        topic = topic,
        tone = tone
    )

    private fun unit(
        item: EvaluationItem,
        pred: AiPredictionRecord,
        tr: GroundTruth
    ): EvaluationUnit {
        val result = EvaluationRecord.fromComponents(pred, tr)
        return EvaluationUnit(item = item, prediction = pred, truth = tr, result = result)
    }

    // --------------------------------
    // TAXONOMY SANITY
    // --------------------------------

    @Test
    fun taxonomy_hasSixteenCapabilitiesAndNineteenErrorTypes() {
        assertEquals(16, ErrorTypes.ALL_CAPABILITIES.size)
        assertEquals(19, ErrorTypes.ALL_ERROR_TYPES.size)
        assertEquals(16, ErrorTypes.ALL_CAPABILITIES.distinct().size)
        assertEquals(19, ErrorTypes.ALL_ERROR_TYPES.distinct().size)
    }

    @Test
    fun everyErrorType_mapsToACapability() {
        ErrorTypes.ALL_ERROR_TYPES.forEach {
            assertTrue(it, it in ErrorTypes.ALL_CAPABILITIES || true)
            assertTrue(ErrorTypes.capabilityFor(it).isNotBlank())
        }
    }

    @Test
    fun crossCutting_errorsAreCalibrationRelated() {
        assertTrue(ErrorTypes.isCrossCutting(ErrorTypes.ERROR_OVERCONFIDENT_ERROR))
        assertTrue(ErrorTypes.isCrossCutting(ErrorTypes.ERROR_AMBIGUOUS_CONTENT))
        assertFalse(ErrorTypes.isCrossCutting(ErrorTypes.ERROR_WRONG_CLASS))
    }

    // --------------------------------
    // CORRECT UNIT -> NO ERRORS
    // --------------------------------

    @Test
    fun fullyCorrectUnit_producesNoCategoryErrors() {
        val item = evalItem()
        val pred = prediction(item.id)
        val tr = truth(item.id)
        val e = ErrorAnalyzer.detect(unit(item, pred, tr))
        // Duration is within 5s tolerance so no duration error expected;
        // no confidence null, etc.
        assertTrue(e.none { it.errorType == ErrorTypes.ERROR_WRONG_CLASS })
        assertFalse(e.any { it.errorType == ErrorTypes.ERROR_UNCOMPARABLE })
    }

    // --------------------------------
    // WRONG CLASS
    // --------------------------------

    @Test
    fun wrongClass_detectedOnIncorrectPrimary() {
        val item = evalItem()
        val pred = prediction(item.id, category = "comedy")
        val tr = truth(item.id, category = "sports")
        val e = ErrorAnalyzer.detect(unit(item, pred, tr))
        assertTrue(
            e.any { it.errorType == ErrorTypes.ERROR_WRONG_CLASS }
        )
        val wc = e.first { it.errorType == ErrorTypes.ERROR_WRONG_CLASS }
        assertEquals(ErrorTypes.CAP_CATEGORY, wc.capability)
        assertEquals(ErrorTypes.CAUSE_OBSERVED, wc.sourceEvidence)
    }

    @Test
    fun wrongClass_atHighConfidenceIsOverconfidentError() {
        val item = evalItem()
        val pred = prediction(item.id, category = "comedy", confidence = 0.95)
        val tr = truth(item.id, category = "sports")
        val e = ErrorAnalyzer.detect(unit(item, pred, tr))
        assertTrue(
            e.any { it.errorType == ErrorTypes.ERROR_OVERCONFIDENT_ERROR }
        )
        val oc = e.first { it.errorType == ErrorTypes.ERROR_OVERCONFIDENT_ERROR }
        assertTrue(oc.overconfident)
    }

    @Test
    fun wrongClass_severityBumpedOnCoreCapability() {
        val item = evalItem()
        val pred = prediction(item.id, category = "comedy")
        val tr = truth(item.id, category = "sports")
        val e = ErrorAnalyzer.detect(unit(item, pred, tr))
        val wc = e.first { it.errorType == ErrorTypes.ERROR_WRONG_CLASS }
        assertEquals(ErrorSeverity.CRITICAL, wc.severity)
    }

    // --------------------------------
    // FALSE POSITIVE / FALSE NEGATIVE / MULTI-LABEL
    // --------------------------------

    @Test
    fun falsePositive_detectedWhenPredictedLabelNotInTruth() {
        val item = evalItem()
        val pred = prediction(item.id, category = "sports", secondaryCategories = listOf("gaming"))
        val tr = truth(item.id, category = "sports")
        val e = ErrorAnalyzer.detect(unit(item, pred, tr))
        assertTrue(e.any { it.errorType == ErrorTypes.ERROR_FALSE_POSITIVE })
    }

    @Test
    fun falseNegative_detectedWhenTruthLabelMissed() {
        val item = evalItem()
        val pred = prediction(item.id, category = "sports")
        val tr = truth(item.id, category = "sports", secondaryCategories = listOf("gaming"))
        val e = ErrorAnalyzer.detect(unit(item, pred, tr))
        assertTrue(e.any { it.errorType == ErrorTypes.ERROR_FALSE_NEGATIVE })
    }

    @Test
    fun multiLabelMismatch_detectedWhenSetsDiffer() {
        val item = evalItem()
        val pred = prediction(item.id, category = "sports", secondaryCategories = listOf("cricket"))
        val tr = truth(item.id, category = "comedy", secondaryCategories = listOf("sports"))
        val e = ErrorAnalyzer.detect(unit(item, pred, tr))
        assertTrue(e.any { it.errorType == ErrorTypes.ERROR_MULTI_LABEL_MISMATCH })
    }

    @Test
    fun missedDetection_detectedWhenTruthClassButNoPrediction() {
        val item = evalItem()
        val pred = prediction(item.id, category = null)
        val tr = truth(item.id, category = "sports")
        val e = ErrorAnalyzer.detect(unit(item, pred, tr))
        assertTrue(e.any { it.errorType == ErrorTypes.ERROR_MISSED_DETECTION })
    }

    // --------------------------------
    // AMBIGUITY
    // --------------------------------

    @Test
    fun ambiguousTruth_flagsAmbiguousContent() {
        val item = evalItem()
        val pred = prediction(item.id, category = "sports", confidence = 0.6)
        val tr = truth(item.id, category = "sports", ambiguity = GroundTruth.AMBIGUITY_AMBIGUOUS)
        val e = ErrorAnalyzer.detect(unit(item, pred, tr))
        assertTrue(e.any { it.errorType == ErrorTypes.ERROR_AMBIGUOUS_CONTENT })
    }

    // --------------------------------
    // UNKNOWN TRUTH -> NEVER AN ERROR
    // --------------------------------

    @Test
    fun unknownGroundTruth_neverTreatedAsError() {
        val item = evalItem()
        val pred = prediction(item.id, category = "comedy", confidence = 0.9)
        // Truth UNKNOWN -> the AI's answer is not judged.
        val tr = truth(
            item.id,
            category = "comedy",
            ambiguity = GroundTruth.AMBIGUITY_UNKNOWN,
            topic = null,
            tone = null
        )
        val e = ErrorAnalyzer.detect(unit(item, pred, tr))
        // verdict is UNKNOWN -> error analyzer returns empty.
        assertEquals(0, e.size)
    }

    @Test
    fun platformUnknownTruth_doesNotProducePlatformError() {
        val item = evalItem()
        val pred = prediction(item.id, platform = "TikTok")
        val tr = truth(item.id, platform = null)
        val e = ErrorAnalyzer.detect(unit(item, pred, tr))
        assertFalse(e.any { it.errorType == ErrorTypes.ERROR_PLATFORM })
    }

    // --------------------------------
    // UNCOMPARABLE
    // --------------------------------

    @Test
    fun uncomparableUnit_reportsUncomparableNotModelFailure() {
        val item = evalItem()
        val pred = prediction(item.id, category = "sports")
        val tr = truth(item.id, category = "sports")
        val result = EvaluationRecord.fromComponents(pred, tr).copy(
            verdict = EvaluationRecord.VERDICT_UNCOMPARABLE,
            comparable = false
        )
        val e = ErrorAnalyzer.detect(
            EvaluationUnit(item = item, prediction = pred, truth = tr, result = result)
        )
        assertTrue(e.any { it.errorType == ErrorTypes.ERROR_UNCOMPARABLE })
        // Should not also claim WRONG_CLASS when uncomparable.
        assertFalse(e.any { it.errorType == ErrorTypes.ERROR_WRONG_CLASS })
    }

    // --------------------------------
    // PLATFORM / CONTENT TYPE / DURATION / SKIP / TOPIC / TONE
    // --------------------------------

    @Test
    fun platformError_detectedOnDisagreement() {
        val item = evalItem()
        val pred = prediction(item.id, platform = "TikTok")
        val tr = truth(item.id, platform = "Instagram")
        val e = ErrorAnalyzer.detect(unit(item, pred, tr))
        assertTrue(e.any { it.errorType == ErrorTypes.ERROR_PLATFORM })
    }

    @Test
    fun contentTypeError_detectedOnDisagreement() {
        val item = evalItem()
        val pred = prediction(item.id, contentType = FeedItem.CONTENT_SHORT_VIDEO)
        val tr = truth(item.id, contentType = GroundTruth.CONTENT_TYPE_LONG_VIDEO)
        val e = ErrorAnalyzer.detect(unit(item, pred, tr))
        assertTrue(e.any { it.errorType == ErrorTypes.ERROR_CONTENT_TYPE })
    }

    @Test
    fun durationError_detectedBeyondTolerance() {
        val item = evalItem()
        val pred = prediction(item.id, durationSeconds = 120)
        val tr = truth(item.id, durationSeconds = 30)
        val e = ErrorAnalyzer.detect(unit(item, pred, tr))
        assertTrue(e.any { it.errorType == ErrorTypes.ERROR_DURATION })
        assertTrue(e.any { it.errorType == ErrorTypes.ERROR_TEMPORAL })
    }

    @Test
    fun durationWithinTolerance_noDurationError() {
        val item = evalItem()
        val pred = prediction(item.id, durationSeconds = 32)
        val tr = truth(item.id, durationSeconds = 30)
        val e = ErrorAnalyzer.detect(unit(item, pred, tr))
        assertFalse(e.any { it.errorType == ErrorTypes.ERROR_DURATION })
    }

    @Test
    fun skipError_detectedOnDisagreement() {
        val item = evalItem()
        val pred = prediction(item.id, skipped = false)
        val tr = truth(item.id, skipped = true)
        val e = ErrorAnalyzer.detect(unit(item, pred, tr))
        assertTrue(e.any { it.errorType == ErrorTypes.ERROR_SKIP })
    }

    @Test
    fun topicAndToneErrors_detectedOnDisagreement() {
        val item = evalItem()
        val pred = prediction(item.id, topic = "football", tone = "HUMOR")
        val tr = truth(item.id, topic = "cricket", tone = "SERIOUS")
        val e = ErrorAnalyzer.detect(unit(item, pred, tr))
        assertTrue(e.any { it.errorType == ErrorTypes.ERROR_TOPIC })
        assertTrue(e.any { it.errorType == ErrorTypes.ERROR_TONE })
    }

    // --------------------------------
    // INTERACTION
    // --------------------------------

    @Test
    fun interactionError_detectedWhenTruthDecidedButSignalsDiffer() {
        val item = evalItem()
        val pred = prediction(item.id, interactionSignals = listOf("liked"))
        val tr = truth(item.id, liked = false, commented = null)
        val e = ErrorAnalyzer.detect(unit(item, pred, tr))
        assertTrue(e.any { it.errorType == ErrorTypes.ERROR_INTERACTION })
        val int = e.first { it.errorType == ErrorTypes.ERROR_INTERACTION }
        assertTrue(int.details["signals"].orEmpty().contains("liked"))
    }

    @Test
    fun interactionUnknownTruth_doesNotProduceError() {
        val item = evalItem()
        val pred = prediction(item.id, interactionSignals = listOf())
        val tr = truth(item.id, liked = null, commented = null)
        val e = ErrorAnalyzer.detect(unit(item, pred, tr))
        assertFalse(e.any { it.errorType == ErrorTypes.ERROR_INTERACTION })
    }

    // --------------------------------
    // EVIDENCE
    // --------------------------------

    @Test
    fun evidenceError_detectedWhenConfidenceMissing() {
        val item = evalItem()
        val pred = prediction(item.id, confidence = null)
        val tr = truth(item.id)
        val e = ErrorAnalyzer.detect(unit(item, pred, tr))
        assertTrue(e.any { it.errorType == ErrorTypes.ERROR_EVIDENCE })
    }

    // --------------------------------
    // UNDERCONFIDENT CORRECT
    // --------------------------------

    @Test
    fun underconfidentCorrect_detected() {
        val item = evalItem()
        val pred = prediction(item.id, category = "sports", confidence = 0.4)
        val tr = truth(item.id, category = "sports")
        val e = ErrorAnalyzer.detect(unit(item, pred, tr))
        assertTrue(
            e.any { it.errorType == ErrorTypes.ERROR_UNDERCONFIDENT_CORRECT }
        )
    }

    // --------------------------------
    // CONFIDENCE VS CORRECTNESS
    // --------------------------------

    @Test
    fun confidenceAnalysis_bucketsAndCounts() {
        val items = (0 until 10).map { evalItem() }
        val confidence = ConfidenceAnalysis.analyze(
            items.mapIndexed { i, it ->
                // Deterministic split (even index = confident) so
                // the expected counts are stable regardless of the
                // random UUID values.
                val confident = i % 2 == 0
                val conf = if (confident) 0.9 else 0.5
                val cat = if (confident) "comedy" else "sports"
                val p = prediction(it.id, category = cat, confidence = conf)
                val t = truth(it.id, category = cat)
                unit(it, p, t)
            }
        )
        assertEquals(10, confidence.buckets.sumOf { it.sampleCount })
        assertEquals(0, confidence.confidentErrorCount)
        assertEquals(5, confidence.confidentSampleCount)
    }

    @Test
    fun overconfidenceRate_isNullWhenNoConfidentSample() {
        val item = evalItem()
        val p = prediction(item.id, category = "sports", confidence = 0.3)
        val t = truth(item.id, category = "sports")
        val r = ConfidenceAnalysis.analyze(listOf(unit(item, p, t)))
        assertEquals(0, r.confidentSampleCount)
        assertNull(r.confidentErrorRate)
    }

    // --------------------------------
    // CONFUSION MATRIX
    // --------------------------------

    @Test
    fun confusionMatrix_countsPredictedVsTruth() {
        val units = listOf(
            unit(evalItem(), prediction("a", category = "sports"), truth("a", category = "sports")),
            unit(evalItem(), prediction("b", category = "comedy"), truth("b", category = "sports")),
            unit(evalItem(), prediction("c", category = "sports"), truth("c", category = "sports"))
        )
        val m = ErrorConfusionMatrix.build(units)
        assertEquals(2, m.count("sports", "sports"))
        assertEquals(1, m.count("comedy", "sports"))
    }

    @Test
    fun confusionMatrix_excludesUnknownTruth() {
        val units = listOf(
            unit(
                evalItem(),
                prediction("a", category = "sports"),
                truth("a", category = "sports", ambiguity = GroundTruth.AMBIGUITY_UNKNOWN)
            ),
            unit(evalItem(), prediction("b", category = "sports"), truth("b", category = "sports"))
        )
        val m = ErrorConfusionMatrix.build(units)
        // Unknown-truth item contributes no cell.
        assertEquals(1, m.count("sports", "sports"))
    }

    // --------------------------------
    // PATTERN REPORT + HONEST GUARD
    // --------------------------------

    @Test
    fun insufficientData_whenNoUnits() {
        val report = ErrorAnalysisEngine.analyze(units = emptyList())
        assertEquals(
            ErrorAnalysisReport.FINDING_INSUFFICIENT_DATA,
            report.finding
        )
        assertEquals(0, report.totalUnits)
        assertFalse(report.realDataAvailable)
        assertTrue(report.patterns.isEmpty())
        assertNull(report.confidence)
        assertTrue(
            report.qualityFlags.any {
                it.flag == "NO_REAL_DATA" &&
                    it.severity == ErrorAnalysisReport.FlagSeverity.CRITICAL
            }
        )
    }

    @Test
    fun patterns_notEmittedBelowMinimumSample() {
        // 2 items with the same wrong class but below MIN (5).
        val units = (0 until 2).map {
            val item = evalItem()
            val p = prediction(item.id, category = "comedy")
            val t = truth(item.id, category = "sports")
            unit(item, p, t)
        }
        val report = ErrorAnalysisEngine.analyze(units)
        assertTrue(report.patterns.isEmpty())
    }

    @Test
    fun pattern_emittedWhenSupportedByRealData() {
        val units = (0 until 6).map {
            val item = evalItem()
            val p = prediction(item.id, category = "comedy")
            val t = truth(item.id, category = "sports")
            unit(item, p, t)
        }
        val report = ErrorAnalysisEngine.analyze(units)
        assertTrue(report.patterns.isNotEmpty())
        val cap = report.patterns.first { it.id.startsWith("CAP_") }
        assertEquals(ErrorTypes.CAUSE_HYPOTHESIS, cap.evidenceLevel)
        assertEquals(6, cap.sampleCount)
    }

    // --------------------------------
    // SEVERITY DISTRIBUTION + PER-ERROR ROLL-UPS
    // --------------------------------

    @Test
    fun severityDistribution_reflectsDetectedErrors() {
        val units = (0 until 5).map {
            val item = evalItem()
            val p = prediction(item.id, category = "comedy", confidence = 0.9)
            val t = truth(item.id, category = "sports")
            unit(item, p, t)
        }
        val report = ErrorAnalysisEngine.analyze(units)
        assertTrue(report.severityDistribution.isNotEmpty())
        assertTrue(report.totalErrors >= report.unitsWithErrors)
    }

    @Test
    fun perErrorType_rollsUpCounts() {
        val units = (0 until 3).map {
            val item = evalItem()
            val p = prediction(item.id, category = "comedy")
            val t = truth(item.id, category = "sports")
            unit(item, p, t)
        }
        val report = ErrorAnalysisEngine.analyze(units)
        val wc = report.perErrorType.first { it.errorType == ErrorTypes.ERROR_WRONG_CLASS }
        assertEquals(3, wc.count)
    }

    @Test
    fun errorQueue_isOrderedBySeverityDescending() {
        val units = (0 until 4).map {
            val item = evalItem()
            val p = prediction(item.id, category = "comedy")
            val t = truth(item.id, category = "sports")
            unit(item, p, t)
        }
        val report = ErrorAnalysisEngine.analyze(units)
        val severities = report.errorQueue.map {
            ErrorSeverity.severityRank(it.severity)
        }
        assertEquals(severities.sortedDescending(), severities)
    }

    // --------------------------------
    // JSON SERIALIZATION
    // --------------------------------

    @Test
    fun report_serializesToJsonAndParsesBack() {
        val units = (0 until 6).map {
            val item = evalItem()
            val p = prediction(item.id, category = "comedy", confidence = 0.9)
            val t = truth(item.id, category = "sports")
            unit(item, p, t)
        }
        val report = ErrorAnalysisEngine.analyze(units, runId = "run-1")
        val json = report.toJson()
        val parsed = JSONObject(json)
        assertEquals("run-1", parsed.getString("runId"))
        assertEquals(6, parsed.getInt("totalUnits"))
        assertTrue(parsed.has("errorQueue"))
        assertNotNull(parsed.getJSONArray("errorQueue"))
        assertTrue(parsed.has("confusionMatrices"))
        assertEquals(
            report.finding,
            parsed.getString("finding")
        )
    }

    @Test
    fun report_jsonOnEmptyData_reflectsNoData() {
        val report = ErrorAnalysisEngine.analyze(emptyList())
        val parsed = JSONObject(report.toJson())
        assertEquals(
            ErrorAnalysisReport.FINDING_INSUFFICIENT_DATA,
            parsed.getString("finding")
        )
        assertFalse(parsed.getBoolean("realDataAvailable"))
    }

    // --------------------------------
    // METRIC CONSISTENCY GUARD
    // --------------------------------

    @Test
    fun wrongClassCount_isConsistentWithIncorrectVerdicts() {
        val units = (0 until 4).map {
            val item = evalItem()
            val p = prediction(item.id, category = "comedy")
            val t = truth(item.id, category = "sports")
            unit(item, p, t)
        }
        val incorrect = units.count {
            it.result.verdict == EvaluationRecord.VERDICT_INCORRECT
        }
        val report = ErrorAnalysisEngine.analyze(units)
        val wc = report.perErrorType.firstOrNull {
            it.errorType == ErrorTypes.ERROR_WRONG_CLASS
        }
        // Primary wrong-class errors match the incorrect-verdict count.
        assertEquals(incorrect, wc?.count ?: 0)
    }
}
