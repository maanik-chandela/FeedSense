package com.example.feedsense

import com.example.feedsense.analysis.evaluation.AnnotationAgreement
import com.example.feedsense.analysis.evaluation.AttributionDecisionTree
import com.example.feedsense.analysis.evaluation.Evidence
import com.example.feedsense.analysis.evaluation.ErrorAnalyzer
import com.example.feedsense.analysis.evaluation.ErrorSeverity
import com.example.feedsense.analysis.evaluation.ErrorTypes
import com.example.feedsense.analysis.evaluation.EvaluationUnit
import com.example.feedsense.analysis.evaluation.RootCauseAnalyzer
import com.example.feedsense.analysis.evaluation.RootCauseAssessment
import com.example.feedsense.analysis.evaluation.RootCauseReport
import com.example.feedsense.analysis.evaluation.RootCauseTypes
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
// ROOT-CAUSE ANALYSIS TESTS (8A-6)
// --------------------------------
//
// Tests for the root-cause validation / failure-attribution /
// error-analysis quality-control framework. These verify that
// suspected causes behind errors are only ever POSSIBLE /
// SUPPORTED (never auto-CONFIRMED), that evidence strength is
// independent of AI confidence, that the decision tree is
// deterministic, and that exports reference existing objects.

class RootCauseAnalysisTest {

    private val now = LocalDateTime.of(2026, 9, 4, 12, 0)
    private val datasetVersion = "ds-v1"
    private val modelVersion = "local-v6.0"

    // --------------------------------
    // HELPER BUILDERS
    // --------------------------------

    private fun evalItem(
        id: String = UUID.randomUUID().toString(),
        dataset: String? = datasetVersion
    ) = EvaluationItem(
        id = id,
        feedItemId = "feed-$id",
        sessionId = "session-1",
        projectId = "project-1",
        modelVersion = modelVersion,
        evaluationStatus = EvaluationItem.STATUS_EVALUATED,
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

    private fun frame(
        id: String,
        ts: LocalDateTime,
        category: String? = "sports",
        confidence: Double? = 0.9,
        ocr: String? = "some text",
        platform: String? = "Instagram"
    ) = Evidence.FrameView(
        frameId = id,
        capturedAt = ts,
        category = category,
        confidence = confidence,
        ocrText = ocr,
        platformText = platform
    )

    // --------------------------------
    // TAXONOMY SANITY
    // --------------------------------

    @Test
    fun candidateCauses_areControlledAndDistinct() {
        assertEquals(
            RootCauseTypes.ALL_CANDIDATE_CAUSES.size,
            RootCauseTypes.ALL_CANDIDATE_CAUSES.distinct().size
        )
        assertTrue(
            RootCauseTypes.CAUSE_OCR_FAILURE in
                RootCauseTypes.ALL_CANDIDATE_CAUSES
        )
        assertTrue(
            RootCauseTypes.CAUSE_VISUAL_AMBIGUITY in
                RootCauseTypes.ALL_CANDIDATE_CAUSES
        )
        assertTrue(
            RootCauseTypes.CAUSE_OVERLAY_OR_MODAL in
                RootCauseTypes.ALL_CANDIDATE_CAUSES
        )
        assertTrue(
            RootCauseTypes.ALL_ATTRIBUTION_STATUSES.contains(
                RootCauseTypes.STATUS_CONFIRMED
            )
        )
        assertEquals(4, RootCauseTypes.ALL_EVIDENCE_STRENGTHS.size)
        assertEquals(6, RootCauseTypes.ALL_ATTRIBUTION_CLASSES.size)
    }

    // --------------------------------
    // NEVER AUTO-CONFIRM
    // --------------------------------

    @Test
    fun analyzer_neverAutoConfirmsAnyCause() {
        // A clear wrong-class error with OCR and 3 frames must
        // NOT be auto-CONFIRMED; it stays POSSIBLE.
        val item = evalItem()
        val pred = prediction(item.id, category = "comedy")
        val tr = truth(item.id, category = "sports")
        val u = unit(item, pred, tr)
        val frames = listOf(
            frame("f1", now.minusSeconds(2), "sports"),
            frame("f2", now.minusSeconds(1), "sports", ocr = "match"),
            frame("f3", now, "sports")
        )
        val a = RootCauseAnalyzer.analyze(
            unit = u,
            frameViews = frames,
            representativeFrameId = "f2",
            analysisId = "rc-1",
            now = now
        )
        assertTrue(a.causes.isNotEmpty())
        a.causes.forEach {
            assertFalse(
                "machine must never auto-confirm: ${it.cause}",
                it.attributionStatus == RootCauseTypes.STATUS_CONFIRMED
            )
        }
    }

    // --------------------------------
    // ATTRIBUTION CLASSIFICATION
    // --------------------------------

    @Test
    fun wrongClass_withAdequateEvidence_isModelRelated() {
        val item = evalItem()
        val pred = prediction(item.id, category = "comedy", confidence = 0.95)
        val tr = truth(item.id, category = "sports")
        val u = unit(item, pred, tr)
        val frames = listOf(
            frame("f1", now.minusSeconds(2), "sports", ocr = "cricket"),
            frame("f2", now.minusSeconds(1), "sports"),
            frame("f3", now, "sports")
        )
        val a = RootCauseAnalyzer.analyze(
            unit = u, frameViews = frames,
            representativeFrameId = "f2", analysisId = "rc-m1", now = now
        )
        assertEquals(RootCauseTypes.CLASS_MODEL_RELATED, a.attributionClass)
    }

    @Test
    fun uncomparableUnit_isPipelineRelated() {
        val item = evalItem()
        val pred = prediction(item.id, category = "sports")
        val tr = truth(item.id, category = "sports")
        val result = EvaluationRecord.fromComponents(pred, tr).copy(
            verdict = EvaluationRecord.VERDICT_UNCOMPARABLE,
            comparable = false
        )
        val u = EvaluationUnit(
            item = item, prediction = pred, truth = tr, result = result
        )
        val a = RootCauseAnalyzer.analyze(
            unit = u, analysisId = "rc-p1", now = now
        )
        assertEquals(RootCauseTypes.CLASS_PIPELINE_RELATED, a.attributionClass)
        assertTrue(
            a.causes.any {
                it.cause == RootCauseTypes.CAUSE_SEGMENTATION_FAILURE
            }
        )
    }

    @Test
    fun ambiguousTruth_isTaxonomyRelated() {
        val item = evalItem()
        val pred = prediction(item.id, category = "sports", confidence = 0.6)
        val tr = truth(
            item.id,
            category = "sports",
            ambiguity = GroundTruth.AMBIGUITY_AMBIGUOUS
        )
        val u = unit(item, pred, tr)
        val frames = listOf(
            frame("f1", now.minusSeconds(2), "sports"),
            frame("f2", now.minusSeconds(1), "sports"),
            frame("f3", now, "sports")
        )
        val a = RootCauseAnalyzer.analyze(
            unit = u, frameViews = frames,
            representativeFrameId = "f2", analysisId = "rc-t1", now = now
        )
        assertEquals(RootCauseTypes.CLASS_TAXONOMY_RELATED, a.attributionClass)
        assertTrue(
            a.causes.any { it.cause == RootCauseTypes.CAUSE_VISUAL_AMBIGUITY }
        )
    }

    // --------------------------------
    // INTER-ANNOTATOR AGREEMENT
    // --------------------------------

    @Test
    fun agreement_singleRater_returnsInsufficientDataGuard() {
        val item = evalItem()
        val only = truth(item.id, category = "sports")
        val a = AnnotationAgreement.ofPrimaryCategory(listOf(only))
        assertEquals(1, a.annotatorCount)
        assertNull(a.percentAgreement)
        assertNull(a.kappa)
        assertEquals(
            AnnotationAgreement.INSUFFICIENT_DATA_FOR_INTER_ANNOTATOR_STATISTICS,
            a.level
        )
    }

    @Test
    fun agreement_twoRaters_agree() {
        val item = evalItem()
        val t1 = truth(item.id, category = "sports")
        val t2 = truth(item.id, category = "sports")
        val a = AnnotationAgreement.ofPrimaryCategory(listOf(t1, t2))
        assertEquals(2, a.annotatorCount)
        assertEquals(1.0, a.percentAgreement!!, 1e-9)
        assertEquals(RootCauseTypes.AGREEMENT_FULL, a.level)
    }

    @Test
    fun agreement_twoRaters_disagree() {
        val item = evalItem()
        val t1 = truth(item.id, category = "sports")
        val t2 = truth(item.id, category = "comedy")
        val a = AnnotationAgreement.ofPrimaryCategory(listOf(t1, t2))
        assertEquals(0.0, a.percentAgreement!!, 1e-9)
        assertEquals(RootCauseTypes.AGREEMENT_DISAGREEMENT, a.level)
    }

    @Test
    fun disagreementSurface_isSupportedNotConfirmed() {
        val item = evalItem()
        val t1 = truth(item.id, category = "sports")
        val t2 = truth(item.id, category = "comedy")
        val pred = prediction(item.id, category = "sports", confidence = 0.8)
        val u = unit(item, pred, t1)
        val a = RootCauseAnalyzer.analyze(
            unit = u,
            multipleTruths = listOf(t1, t2),
            analysisId = "rc-iaa",
            now = now
        )
        assertTrue(
            a.causes.any {
                it.cause == RootCauseTypes.CAUSE_ANNOTATION_DISAGREEMENT &&
                    it.attributionStatus == RootCauseTypes.STATUS_SUPPORTED
            }
        )
        // Supported is fine, but never CONFIRMED.
        a.causes.forEach {
            assertFalse(
                it.attributionStatus == RootCauseTypes.STATUS_CONFIRMED
            )
        }
    }

    // --------------------------------
    // HUMAN REVIEW / AUDIT / VERSIONING
    // --------------------------------

    @Test
    fun assessment_carriesVersionAndProvenance() {
        val item = evalItem()
        val pred = prediction(item.id, category = "comedy")
        val tr = truth(item.id, category = "sports")
        val u = unit(item, pred, tr)
        val a = RootCauseAnalyzer.analyze(u, analysisId = "rc-v", now = now)
        assertEquals(RootCauseAssessment.DIAGNOSTIC_VERSION, a.diagnosticVersion)
        assertEquals(datasetVersion, a.datasetVersion)
        assertEquals(modelVersion, a.modelVersion)
        assertEquals(item.id, a.evaluationItemId)
        assertEquals(RootCauseAssessment.REVIEW_UNREVIEWED, a.humanReviewStatus)
        assertNull(a.reviewerId)
        assertEquals(now, a.createdAt)
        assertEquals(now, a.updatedAt)
    }

    @Test
    fun withAudit_appendsEntryAndBumpsUpdatedAt() {
        val item = evalItem()
        val pred = prediction(item.id, category = "comedy")
        val tr = truth(item.id, category = "sports")
        val a = RootCauseAnalyzer.analyze(unit(item, pred, tr), analysisId = "rc-au", now = now)
        val later = now.plusHours(1)
        val updated = a.withAudit(
            RootCauseAssessment.AuditEntry(
                at = later,
                reviewerId = "analyst-9",
                previousStatus = RootCauseTypes.STATUS_POSSIBLE,
                newStatus = RootCauseTypes.STATUS_CONFIRMED,
                note = "human confirmed"
            ),
            now = later
        )
        assertEquals(1, updated.auditTrail.size)
        assertEquals("analyst-9", updated.auditTrail[0].reviewerId)
        assertEquals(later, updated.updatedAt)
        // Original assessment is unchanged (immutability).
        assertEquals(now, a.updatedAt)
        assertEquals(0, a.auditTrail.size)
    }

    // --------------------------------
    // REPRESENTATIVE-FRAME FAILURE (POSSIBLE ONLY)
    // --------------------------------

    @Test
    fun representativeFrameDisagreeingWithMajority_isPossibleNotConfirmed() {
        val item = evalItem()
        val pred = prediction(item.id, category = "comedy")
        val tr = truth(item.id, category = "sports")
        val u = unit(item, pred, tr)
        val frames = listOf(
            frame("f1", now.minusSeconds(2), "sports"),
            frame("f2", now.minusSeconds(1), "comedy"),
            frame("f3", now, "sports")
        )
        val a = RootCauseAnalyzer.analyze(
            unit = u, frameViews = frames,
            representativeFrameId = "f2", analysisId = "rc-rf",
            now = now
        )
        val contributing = a.causes.firstOrNull {
            it.cause == RootCauseTypes.CAUSE_REPRESENTATIVE_FRAME_FAILURE
        }
        assertNotNull(contributing)
        assertEquals(RootCauseTypes.STATUS_POSSIBLE, contributing!!.attributionStatus)
        assertEquals(RootCauseTypes.ROLE_CONTRIBUTING, contributing.role)
    }

    // --------------------------------
    // EVIDENCE STRENGTH INDEPENDENT OF AI CONFIDENCE
    // --------------------------------

    @Test
    fun evidenceStrength_isDerivedFromSignalsNotConfidence() {
        val item = evalItem()
        val pred = prediction(item.id, category = "comedy", confidence = 0.5)
        val tr = truth(item.id, category = "sports")
        val u = unit(item, pred, tr)
        // 3 frames + OCR -> sufficient signals.
        val frames = listOf(
            frame("f1", now.minusSeconds(2), "sports", ocr = "a"),
            frame("f2", now.minusSeconds(1), "sports", ocr = "b"),
            frame("f3", now, "sports", ocr = "c")
        )
        val a = RootCauseAnalyzer.analyze(
            unit = u, frameViews = frames,
            representativeFrameId = "f2",
            // second agreeing annotator -> 3rd evidence signal
            multipleTruths = listOf(tr, truth(item.id, category = "sports")),
            analysisId = "rc-ev",
            now = now
        )
        assertEquals(RootCauseTypes.EVIDENCE_STRONG, a.evidenceStrength)
        // Attribution confidence must be independent: even with
        // moderate prediction confidence, evidence is strong.
        assertEquals(RootCauseTypes.CONFIDENCE_MEDIUM, a.attributionConfidence)
    }

    // --------------------------------
    // DECISION TREE DETERMINISM
    // --------------------------------

    @Test
    fun decisionTree_resolvesToSameClassForSameInput() {
        val input = AttributionDecisionTree.Input(
            truthValid = true,
            truthAmbiguous = false,
            evidenceSufficient = true,
            segmentationCorrect = true,
            capabilityRepresentationError = false,
            aiDisagreesDespiteEvidence = true,
            categoryAmbiguous = false
        )
        val r1 = AttributionDecisionTree.classify(input)
        val r2 = AttributionDecisionTree.classify(input)
        assertEquals(r1.attributionClass, r2.attributionClass)
        assertEquals(r1.step, r2.step)
        assertEquals(RootCauseTypes.CLASS_MODEL_RELATED, r1.attributionClass)
        assertEquals(5, r1.step)
    }

    @Test
    fun decisionTree_decisiveTruthFirst() {
        // TRUE even if EVERYTHING else would point at model: truth
        // validity must be decided first.
        val r = AttributionDecisionTree.classify(
            AttributionDecisionTree.Input(
                truthValid = false,
                truthAmbiguous = true,
                evidenceSufficient = true,
                segmentationCorrect = true,
                capabilityRepresentationError = false,
                aiDisagreesDespiteEvidence = true,
                categoryAmbiguous = false
            )
        )
        assertEquals(RootCauseTypes.CLASS_ANNOTATION_RELATED, r.attributionClass)
        assertEquals(1, r.step)
    }

    // --------------------------------
    // DETERMINISM OF ANALYZER
    // --------------------------------

    @Test
    fun analyzer_isDeterministicForSameInputs() {
        val item = evalItem()
        val pred = prediction(item.id, category = "comedy")
        val tr = truth(item.id, category = "sports")
        val u = unit(item, pred, tr)
        val frames = listOf(
            frame("f1", now.minusSeconds(2), "sports"),
            frame("f2", now.minusSeconds(1), "sports"),
            frame("f3", now, "sports")
        )
        val a1 = RootCauseAnalyzer.analyze(
            unit = u, frameViews = frames, representativeFrameId = "f2",
            analysisId = "rc-det", now = now
        )
        val a2 = RootCauseAnalyzer.analyze(
            unit = u, frameViews = frames, representativeFrameId = "f2",
            analysisId = "rc-det", now = now
        )
        assertEquals(a1.causes.map { it.cause to it.role }, a2.causes.map { it.cause to it.role })
        assertEquals(a1.attributionClass, a2.attributionClass)
        assertEquals(a1.evidenceStrength, a2.evidenceStrength)
        assertEquals(a1.attributionConfidence, a2.attributionConfidence)
        assertEquals(a1.toJson(), a2.toJson())
    }

    // --------------------------------
    // REPORT AGGREGATION
    // --------------------------------

    @Test
    fun report_aggregatesClassCounts() {
        val assessments = (0 until 4).map { i ->
            val item = evalItem()
            val pred = prediction(item.id, category = "comedy")
            val tr = truth(item.id, category = "sports")
            RootCauseAnalyzer.analyze(
                unit(item, pred, tr),
                frameViews = listOf(
                    frame("f1", now.minusSeconds(2), "sports"),
                    frame("f2", now.minusSeconds(1), "sports"),
                    frame("f3", now, "sports")
                ),
                representativeFrameId = "f2",
                analysisId = "rc-rep-$i",
                now = now
            )
        }
        val report = RootCauseReport.build(assessments)
        assertEquals(4, report.aggregated.totalAssessed)
        val modelCount = report.aggregated.classCounts.first {
            it.attributionClass == RootCauseTypes.CLASS_MODEL_RELATED
        }.count
        assertEquals(4, modelCount)
        assertEquals(4, report.aggregated.dataVsModel.modelRelated)
        assertFalse(report.aggregated.dataVsModel.insufficientRealData)
    }

    @Test
    fun report_emptyInput_marksInsufficientData() {
        val report = RootCauseReport.build(emptyList())
        assertEquals(0, report.aggregated.totalAssessed)
        assertTrue(report.aggregated.dataVsModel.insufficientRealData)
        assertEquals(
            AnnotationAgreement.INSUFFICIENT_DATA_FOR_INTER_ANNOTATOR_STATISTICS,
            report.aggregated.overallAttributionConfidence
        )
        assertTrue(report.aggregated.reviewQueue.isEmpty())
    }

    // --------------------------------
    // PRIORITY / HARD-CASE QUEUE
    // --------------------------------

    @Test
    fun priorityScore_isHigherForInconclusiveUnknown() {
        val item = evalItem()
        val pred = prediction(item.id, category = "comedy")
        val tr = truth(item.id, category = "sports")
        val base = RootCauseAnalyzer.analyze(
            unit(item, pred, tr), analysisId = "rc-pq1", now = now
        )
        // A genuinely inconclusive + unknown-class assessment.
        val inconclusive = base.copy(
            attributionStatus = RootCauseTypes.STATUS_INCONCLUSIVE,
            attributionClass = RootCauseTypes.CLASS_UNKNOWN
        )
        val model = base.copy(
            attributionStatus = RootCauseTypes.STATUS_POSSIBLE,
            attributionClass = RootCauseTypes.CLASS_MODEL_RELATED
        )
        assertTrue(
            RootCauseReport.priorityScore(inconclusive) >
                RootCauseReport.priorityScore(model)
        )
    }

    @Test
    fun hardCaseQueue_excludesConfirmedAndRejected() {
        val item = evalItem()
        val pred = prediction(item.id, category = "comedy")
        val tr = truth(item.id, category = "sports")
        val a = RootCauseAnalyzer.analyze(
            unit(item, pred, tr), analysisId = "rc-hq", now = now
        )
        val confirmed = a.copy(
            attributionStatus = RootCauseTypes.STATUS_CONFIRMED
        )
        val rejected = a.copy(
            attributionStatus = RootCauseTypes.STATUS_REJECTED
        )
        val report = RootCauseReport.build(
            listOf(confirmed, rejected, a, a.copy(analysisId = "rc-hq2"))
        )
        // confirmed + rejected excluded; two possible remain.
        assertEquals(2, report.aggregated.reviewQueue.size)
        val queueIds = report.aggregated.reviewQueue.map { it.analysisId }.toSet()
        assertFalse(queueIds.contains("rc-hq-confirmed"))
        assertFalse(queueIds.contains("rc-hq-rejected"))
    }

    // --------------------------------
    // TRAINING-CANDIDATE MARKING (no training)
    // --------------------------------

    @Test
    fun trainingCandidates_onlyMarkPossibleOrInconclusive() {
        val item = evalItem()
        val pred = prediction(item.id, category = "comedy")
        val tr = truth(item.id, category = "sports")
        val a = RootCauseAnalyzer.analyze(
            unit(item, pred, tr),
            analysisId = "rc-tc", now = now
        )
        val confirmed = a.copy(
            attributionStatus = RootCauseTypes.STATUS_CONFIRMED
        )
        val report = RootCauseReport.build(listOf(a, confirmed))
        val candidates = RootCauseReport.trainingCandidates(
            report.assessments
        )
        // only the non-confirmed candidate may appear
        assertTrue(candidates.none { it.attributionStatus == RootCauseTypes.STATUS_CONFIRMED })
    }

    // --------------------------------
    // CSV / JSON EXPORT (references only)
    // --------------------------------

    @Test
    fun csv_exportListsOneRowPerCause() {
        val item = evalItem()
        val pred = prediction(item.id, category = "comedy")
        val tr = truth(item.id, category = "sports")
        val a = RootCauseAnalyzer.analyze(
            unit(item, pred, tr),
            frameViews = listOf(
                frame("f1", now.minusSeconds(2), "sports"),
                frame("f2", now.minusSeconds(1), "sports")
            ),
            representativeFrameId = "f1",
            analysisId = "rc-csv", now = now
        )
        val report = RootCauseReport.build(listOf(a))
        val csv = RootCauseReport.toCsv(report.assessments)
        val lines = csv.trim().split("\n")
        // header + one line per candidate cause
        assertEquals(1 + a.causes.size, lines.size)
        assertTrue(lines[0].contains("analysisId"))
        assertTrue(csv.contains("rc-csv"))
    }

    @Test
    fun json_export_containsMarkersAndReferences() {
        val item = evalItem()
        val pred = prediction(item.id, category = "comedy")
        val tr = truth(item.id, category = "sports")
        val a = RootCauseAnalyzer.analyze(
            unit(item, pred, tr),
            frameViews = listOf(frame("f1", now.minusSeconds(2), "sports")),
            representativeFrameId = "f1",
            analysisId = "rc-json", now = now
        )
        val report = RootCauseReport.build(listOf(a))
        val root = JSONObject(report.toJson())
        assertEquals(1, root.getInt("totalAssessed"))
        assertTrue(root.has("attributionClasses"))
        assertTrue(root.has("dataVsModel"))
        assertTrue(root.has("reviewQueue"))
        assertTrue(root.has("trainingCandidates"))
        val details = root.getJSONArray("assessments")
        assertEquals(1, details.length())
        val first = details.getJSONObject(0)
        // Evidence references reference frame ids, never content.
        val refs = first.getJSONArray("evidenceReferences")
        assertTrue(refs.length() > 0)
        assertTrue(
            refs.toString().contains("f1") ||
                refs.toString().contains("prediction")
        )
    }

    @Test
    fun assessment_jsonRoundTripsFieldValues() {
        val item = evalItem()
        val pred = prediction(item.id, category = "comedy", confidence = 0.9)
        val tr = truth(item.id, category = "sports")
        val a = RootCauseAnalyzer.analyze(
            unit(item, pred, tr), analysisId = "rc-rt", now = now
        )
        val o = JSONObject(a.toJson())
        assertEquals("rc-rt", o.getString("analysisId"))
        assertEquals(a.errorType, o.getString("errorType"))
        assertEquals(a.attributionClass, o.getString("attributionClass"))
        assertEquals(a.diagnosticVersion, o.getString("diagnosticVersion"))
        assertEquals(a.evidenceStrength, o.getString("evidenceStrength"))
    }

    // --------------------------------
    // PRIVACY: NO RAW CONTENT IN EXPORTS
    // --------------------------------

    @Test
    fun evidenceReferences_neverContainRawOcrPayload() {
        val item = evalItem()
        val pred = prediction(item.id, category = "comedy")
        val tr = truth(item.id, category = "sports")
        val frames = listOf(
            frame("f1", now.minusSeconds(2), "sports",
                ocr = "sensitive-private-caption")
        )
        val a = RootCauseAnalyzer.analyze(
            unit(item, pred, tr), frameViews = frames,
            representativeFrameId = "f1", analysisId = "rc-priv", now = now
        )
        // The OCR payload string must NOT appear in serialized
        // evidence refs or report JSON.
        assertFalse(a.toJson().contains("sensitive-private-caption"))
        val report = RootCauseReport.build(listOf(a))
        assertFalse(report.toJson().contains("sensitive-private-caption"))
    }

    // --------------------------------
    // ERROR TYPE / CAPABILITY MAPPING
    // --------------------------------

    @Test
    fun errorType_mappedFromDetectedErrors() {
        val item = evalItem()
        val pred = prediction(item.id, category = "comedy")
        val tr = truth(item.id, category = "sports")
        val u = unit(item, pred, tr)
        val detected = ErrorAnalyzer.detect(u)
        assertTrue(
            detected.any { it.errorType == ErrorTypes.ERROR_WRONG_CLASS }
        )
        val a = RootCauseAnalyzer.analyze(u, analysisId = "rc-et", now = now)
        // capability field must be a valid capability for the
        // derived error type.
        assertTrue(a.errorType in ErrorTypes.ALL_ERROR_TYPES)
        assertTrue(a.capability in ErrorTypes.ALL_CAPABILITIES)
        assertEquals(
            ErrorTypes.capabilityFor(a.errorType),
            a.capability
        )
    }

    // --------------------------------
    // CROSS-CUTTING CONSISTENCY
    // --------------------------------

    @Test
    fun allCandidateCauses_haveDisplayText() {
        RootCauseTypes.ALL_CANDIDATE_CAUSES.forEach {
            assertTrue(it, RootCauseTypes.causeDisplay(it).isNotBlank())
        }
    }
}
