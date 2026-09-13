package com.example.feedsense.analysis.evidence.decision

import com.example.feedsense.analysis.evidence.temporal.CandidateCategoryScore
import com.example.feedsense.analysis.evidence.temporal.ConflictLevel
import com.example.feedsense.analysis.evidence.temporal.CoverageState
import com.example.feedsense.analysis.evidence.temporal.ItemEvidenceSnapshot
import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.FeedItem
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-8.
 *
 * Evidence-aware production adapter tests.
 *
 * Comprehensive test matrix covering:
 *   - Baseline agreement
 *   - Category disagreement
 *   - Baseline unknown
 *   - Evidence-aware unknown
 *   - Both unknown
 *   - Insufficient evidence
 *   - Review required
 *   - Representative frame conflict
 *   - OCR influence
 *   - Platform evidence
 *   - Interaction evidence
 *   - No mutation of FeedItem
 *   - Frozen AiPredictionRecord unchanged
 *   - Version propagation
 *   - Privacy (no raw evidence leaks)
 *   - Determinism
 *   - Missing evidence
 *   - Failure isolation
 *   - Batch limit
 *   - Ordering
 *   - 8A bridge compatibility
 *   - Confidence ≠ accuracy distinction
 *   - Evidence strength ≠ correctness
 */
class EvidenceAwareProductionAdapterTest {

    private val adapter =
        EvidenceAwareProductionAdapter()

    // --------------------------------------------------
    // HELPERS
    // --------------------------------------------------

    private fun feedItem(
        id: String = "item1",
        sessionId: String = "s1",
        category: String? = "sports",
        confidence: Double? = 0.82,
        contentType: String =
            FeedItem.CONTENT_SHORT_VIDEO,
        platform: String? = "instagram",
        skipped: Boolean = false,
        uncertaintyLevel: String =
            FeedItem.UNCERTAINTY_LOW,
        needsReview: Boolean = false,
        modelVersion: String? = "baseline-v1"
    ): FeedItem {
        return FeedItem(
            id = id,
            sessionId = sessionId,
            startTime = LocalDateTime.now(),
            endTime =
                LocalDateTime.now().plusSeconds(10),
            category = category,
            confidence = confidence,
            contentType = contentType,
            platform = platform,
            skipped = skipped,
            uncertaintyLevel = uncertaintyLevel,
            needsReview = needsReview,
            modelVersion = modelVersion,
            representativeFramePath = "/tmp/rep.pdf"
        )
    }

    private fun candidate(
        category: String,
        support: Double,
        evidenceTypes: List<String> =
            listOf("OCR", "visual", "layout"),
        supportingCount: Int = 5,
        contradictingCount: Int = 0,
        persistence: String = "SUSTAINED"
    ): CandidateCategoryScore {
        return CandidateCategoryScore(
            category = category,
            supportScore = support,
            supportingCount = supportingCount,
            contradictingCount = contradictingCount,
            evidenceTypes = evidenceTypes,
            persistenceLevel = persistence,
            firstSeenRelativeMs = 1000,
            lastSeenRelativeMs = 9000,
            temporalSpreadMs = 8000
        )
    }

    private fun snapshot(
        candidates: List<CandidateCategoryScore> =
            emptyList(),
        coverageRatio: Double = 0.9,
        coverageState: CoverageState =
            CoverageState.HIGH_COVERAGE,
        itemDurationMs: Long = 10000,
        observedDurationMs: Long = 9000,
        usableEvidencePoints: Int = 20,
        uniqueEvidenceIdentities: Int = 8,
        evidenceTypes: List<String> =
            listOf("OCR", "visual", "layout"),
        conflict: ConflictLevel =
            ConflictLevel.NONE,
        representativeFrameOutlier: Boolean? = null,
        internalTransitions: List<com.example.feedsense.analysis.evidence.temporal.PossibleInternalTransition> =
            emptyList()
    ): ItemEvidenceSnapshot {
        return ItemEvidenceSnapshot(
            sessionId = "s1",
            feedItemId = "item1",
            snapshotVersion =
                "item-evidence-snapshot-v1",
            fusionVersion =
                "temporal-fusion-v1",
            fusionConfigVersion =
                "temporal-fusion-config-v1",
            totalEvidencePoints = 30,
            usableEvidencePoints =
                usableEvidencePoints,
            analyzedFrameCount = 25,
            totalFrameCount = 30,
            itemDurationMs = itemDurationMs,
            coverageState = coverageState,
            coverageRatio = coverageRatio,
            observedDurationMs = observedDurationMs,
            hasGap = false,
            gapCount = 0,
            gapTotalDurationMs = 0,
            uniqueEvidenceIdentities =
                uniqueEvidenceIdentities,
            evidenceTypesPresent = evidenceTypes,
            extractorNames = listOf(
                "ocr-extractor",
                "visual-extractor"
            ),
            candidateCategories = candidates,
            supportingEvidenceByCategory =
                emptyMap(),
            contradictingEvidenceByCategory =
                emptyMap(),
            conflictLevel = conflict,
            conflictDescription = null,
            contradictingCategoryCount = 0,
            ambiguityScore = 0.0,
            isAmbiguous = false,
            ambiguityReason = null,
            isInsufficientEvidence = false,
            insufficientEvidenceReason = null,
            detectedTransitions = emptyList(),
            transitionDetected = false,
            possibleInternalTransitions =
                internalTransitions,
            limitations = emptyList(),
            representativeFrameAgreement =
                if (representativeFrameOutlier ==
                    true) false
                else null,
            representativeFrameOutlier =
                representativeFrameOutlier,
            createdAtMs = 0L,
            fusionDurationMs = 0L
        )
    }

    // --------------------------------------------------
    // TEST 1: AGREEMENT — SAME CATEGORY
    // --------------------------------------------------

    @Test
    fun test1_agreement_sameCategory() {
        val item = feedItem(
            category = "sports",
            confidence = 0.82
        )
        val s = snapshot(
            candidates =
                listOf(candidate("sports", 0.85))
        )

        val result =
            adapter.compareFeedItem(item, s)

        assertEquals(
            ComparisonState.AGREEMENT,
            result.comparisonState
        )
        assertEquals(
            "sports",
            result.baselineCategory
        )
        assertEquals(
            "sports",
            result.evidenceAwareCategory
        )
        assertFalse(result.categoryChanged)
        assertTrue(result.decisionAgreement)
        assertTrue(result.differenceReasons.isEmpty())
    }

    // --------------------------------------------------
    // TEST 2: CATEGORY DISAGREEMENT
    // --------------------------------------------------

    @Test
    fun test2_categoryDisagreement() {
        val item = feedItem(
            category = "sports",
            confidence = 0.82
        )
        val s = snapshot(
            candidates = listOf(
                candidate("comedy", 0.85),
                candidate("sports", 0.2)
            )
        )

        val result =
            adapter.compareFeedItem(item, s)

        assertEquals(
            ComparisonState.CATEGORY_DISAGREEMENT,
            result.comparisonState
        )
        assertEquals(
            "sports",
            result.baselineCategory
        )
        assertEquals(
            "comedy",
            result.evidenceAwareCategory
        )
        assertTrue(result.categoryChanged)
        assertFalse(result.decisionAgreement)
        assertTrue(
            result.differenceReasons.isNotEmpty()
        )
        assertTrue(
            result.humanReviewRecommended
        )
    }

    // --------------------------------------------------
    // TEST 3: BASELINE UNKNOWN
    // --------------------------------------------------

    @Test
    fun test3_baselineUnknown() {
        val item = feedItem(
            category = null,
            confidence = null
        )
        val s = snapshot(
            candidates =
                listOf(candidate("sports", 0.85))
        )

        val result =
            adapter.compareFeedItem(item, s)

        assertEquals(
            ComparisonState.BASELINE_UNKNOWN,
            result.comparisonState
        )
        assertNull(result.baselineCategory)
        assertEquals(
            "sports",
            result.evidenceAwareCategory
        )
        assertTrue(result.baselineCategory == null)
    }

    // --------------------------------------------------
    // TEST 4: EVIDENCE-AWARE UNKNOWN
    // --------------------------------------------------

    @Test
    fun test4_evidenceAwareUnknown() {
        val item = feedItem(category = "sports")
        val s = snapshot(candidates = emptyList())

        val result =
            adapter.compareFeedItem(item, s)

        assertEquals(
            ComparisonState.EVIDENCE_AWARE_UNKNOWN,
            result.comparisonState
        )
        assertEquals(
            "sports",
            result.baselineCategory
        )
        assertNull(
            result.evidenceAwareCategory
        )
        assertEquals(
            DecisionState.UNKNOWN,
            result.evidenceAwareDecision
        )
    }

    // --------------------------------------------------
    // TEST 5: BOTH UNKNOWN
    // --------------------------------------------------

    @Test
    fun test5_bothUnknown() {
        val item = feedItem(
            category = null,
            confidence = null
        )
        val s = snapshot(candidates = emptyList())

        val result =
            adapter.compareFeedItem(item, s)

        assertEquals(
            ComparisonState.BOTH_UNKNOWN,
            result.comparisonState
        )
        assertNull(result.baselineCategory)
        assertNull(
            result.evidenceAwareCategory
        )
    }

    // --------------------------------------------------
    // TEST 6: INSUFFICIENT EVIDENCE
    // --------------------------------------------------

    @Test
    fun test6_insufficientEvidence() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates =
                listOf(candidate("sports", 0.9)),
            coverageRatio = 0.02,
            coverageState =
                CoverageState.LOW_COVERAGE,
            evidenceTypes = listOf("OCR"),
            uniqueEvidenceIdentities = 1
        )

        val result =
            adapter.compareFeedItem(item, s)

        assertEquals(
            ComparisonState.INSUFFICIENT_EVIDENCE,
            result.comparisonState
        )
        assertEquals(
            DecisionState.INSUFFICIENT_EVIDENCE,
            result.evidenceAwareDecision
        )
        assertNull(
            result.evidenceAwareCategory
        )
        assertEquals(
            EvidenceStrength.WEAK,
            result.evidenceStrength
        )
        assertTrue(
            result.differenceReasons.contains(
                DifferenceReason
                    .INSUFFICIENT_EVIDENCE
            )
        )
    }

    // --------------------------------------------------
    // TEST 7: REVIEW REQUIRED
    // --------------------------------------------------

    @Test
    fun test7_reviewRequired() {
        // Ambiguous evidence → review required.
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates = listOf(
                candidate("sports", 0.6),
                candidate("comedy", 0.58)
            )
        )

        val result =
            adapter.compareFeedItem(item, s)

        assertEquals(
            ComparisonState.REVIEW_REQUIRED,
            result.comparisonState
        )
        assertTrue(result.humanReviewRecommended)
    }

    // --------------------------------------------------
    // TEST 8: NO MUTATION — BASELINE PRESERVED
    // --------------------------------------------------

    @Test
    fun test8_noMutation_baselinePreserved() {
        val item = feedItem(
            category = "sports",
            confidence = 0.82,
            contentType =
                FeedItem.CONTENT_SHORT_VIDEO,
            platform = "instagram",
            uncertaintyLevel =
                FeedItem.UNCERTAINTY_LOW,
            needsReview = false,
            modelVersion = "baseline-v1"
        )
        val s = snapshot(
            candidates =
                listOf(candidate("sports", 0.85))
        )

        // Capture all baseline fields before.
        val beforeCategory = item.category
        val beforeConfidence = item.confidence
        val beforeContentType = item.contentType
        val beforePlatform = item.platform
        val beforeUncertainty =
            item.uncertaintyLevel
        val beforeNeedsReview =
            item.needsReview
        val beforeModelVersion =
            item.modelVersion
        val beforeId = item.id

        val result =
            adapter.compareFeedItem(item, s)

        // Baseline FeedItem fields are untouched.
        assertEquals(beforeCategory, item.category)
        assertEquals(
            beforeConfidence, item.confidence
        )
        assertEquals(
            beforeContentType, item.contentType
        )
        assertEquals(
            beforePlatform, item.platform
        )
        assertEquals(
            beforeUncertainty,
            item.uncertaintyLevel
        )
        assertEquals(
            beforeNeedsReview, item.needsReview
        )
        assertEquals(
            beforeModelVersion, item.modelVersion
        )
        assertEquals(beforeId, item.id)

        // Comparison result preserves baseline verbatim.
        assertEquals(
            beforeCategory,
            result.baselineCategory
        )
        assertEquals(
            beforeConfidence,
            result.baselineConfidence
        )
    }

    // --------------------------------------------------
    // TEST 9: FROZEN AiPredictionRecord UNCHANGED
    // --------------------------------------------------

    @Test
    fun test9_frozenPredictionRecordUnchanged() {
        val record = AiPredictionRecord(
            evaluationItemId = "eval-1",
            category = "sports",
            confidence = 0.82,
            modelVersion = "baseline-v1",
            source = "AI",
            feedItemId = "item1"
        )

        // Capture original fields.
        val beforeCategory = record.category
        val beforeConfidence = record.confidence
        val beforeModelVersion =
            record.modelVersion

        // Run adapter (does not touch the record).
        val item = feedItem(
            id = "item1",
            category = "sports",
            confidence = 0.82
        )
        val s = snapshot(
            candidates =
                listOf(candidate("sports", 0.85))
        )
        adapter.compareFeedItem(item, s)

        // Record is unchanged.
        assertEquals(
            beforeCategory, record.category
        )
        assertEquals(
            beforeConfidence, record.confidence
        )
        assertEquals(
            beforeModelVersion,
            record.modelVersion
        )
    }

    // --------------------------------------------------
    // TEST 10: VERSION PROPAGATION
    // --------------------------------------------------

    @Test
    fun test10_versionPropagation() {
        val item = feedItem(
            category = "sports",
            modelVersion = "baseline-v2"
        )
        val s = snapshot(
            candidates =
                listOf(candidate("sports", 0.85))
        )

        val result = adapter.compareFeedItem(
            feedItem = item,
            snapshot = s,
            datasetVersion = "evaluation-v1"
        )

        assertEquals(
            EvidenceAwareComparisonResult
                .ADAPTER_VERSION,
            result.adapterVersion
        )
        assertEquals(
            "baseline-v2",
            result.baselineModelVersion
        )
        assertEquals(
            "temporal-fusion-v1",
            result.evidenceAwareModelVersion
        )
        assertEquals(
            ItemPredictionResult.DECISION_VERSION,
            result.decisionVersion
        )
        assertEquals(
            "evaluation-v1",
            result.datasetVersion
        )
        assertEquals(
            "temporal-fusion-v1",
            result.fusionVersion
        )
        assertTrue(result.timestampMs > 0)
    }

    // --------------------------------------------------
    // TEST 11: DETERMINISM
    // --------------------------------------------------

    @Test
    fun test11_determinism() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates =
                listOf(candidate("sports", 0.85))
        )

        val a = adapter.compareFeedItem(item, s)
        val b = adapter.compareFeedItem(item, s)

        assertEquals(
            a.comparisonState,
            b.comparisonState
        )
        assertEquals(
            a.baselineCategory,
            b.baselineCategory
        )
        assertEquals(
            a.evidenceAwareCategory,
            b.evidenceAwareCategory
        )
        assertEquals(
            a.categoryChanged,
            b.categoryChanged
        )
        assertEquals(
            a.differenceReasons,
            b.differenceReasons
        )
        assertEquals(
            a.evidenceStrength,
            b.evidenceStrength
        )
    }

    // --------------------------------------------------
    // TEST 12: PRIVACY — NO RAW EVIDENCE IN RESULT
    // --------------------------------------------------

    @Test
    fun test12_privacy_noRawEvidence() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates =
                listOf(candidate("sports", 0.85))
        )

        val result =
            adapter.compareFeedItem(item, s)

        // Convert to string representation and verify
        // no raw private data is included.
        val serialized = result.toString()

        assertFalse(
            "No screenshot paths in result",
            serialized.contains("/tmp/")
        )
        assertFalse(
            "No OCR payloads in result",
            serialized.contains("private_ocr_text")
        )
        assertFalse(
            "No personal names in result",
            serialized.contains("John Doe")
        )
        assertFalse(
            "No notification text in result",
            serialized.contains("New message from")
        )
        assertFalse(
            "No private URLs in result",
            serialized.contains(
                "https://private.example.com"
            )
        )
    }

    // --------------------------------------------------
    // TEST 13: CONFIDENCE ≠ ACCURACY
    // --------------------------------------------------

    @Test
    fun test13_confidenceNotAccuracy() {
        val item = feedItem(
            category = "sports",
            confidence = 0.95
        )
        val s = snapshot(
            candidates =
                listOf(candidate("sports", 0.95))
        )

        val result =
            adapter.compareFeedItem(item, s)

        // High baseline confidence does NOT mean
        // baseline is correct.
        assertEquals(
            0.95, result.baselineConfidence!!, 0.0
        )
        // High evidence strength does NOT mean
        // prediction is correct.
        assertEquals(
            EvidenceStrength.STRONG,
            result.evidenceStrength
        )
        // Agreement does NOT mean correctness.
        assertEquals(
            ComparisonState.AGREEMENT,
            result.comparisonState
        )
        // None of these fields assert correctness.
    }

    // --------------------------------------------------
    // TEST 14: EVIDENCE STRENGTH ≠ CORRECTNESS
    // --------------------------------------------------

    @Test
    fun test14_evidenceStrengthNotCorrectness() {
        // STRONG evidence with category disagreement.
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates = listOf(
                candidate("comedy", 0.85),
                candidate("sports", 0.3)
            )
        )

        val result =
            adapter.compareFeedItem(item, s)

        assertEquals(
            EvidenceStrength.STRONG,
            result.evidenceStrength
        )
        assertEquals(
            ComparisonState.CATEGORY_DISAGREEMENT,
            result.comparisonState
        )
        // STRONG evidence + disagreement does NOT
        // mean either is correct or incorrect.
    }

    // --------------------------------------------------
    // TEST 15: REPRESENTATIVE FRAME CONFLICT
    // --------------------------------------------------

    @Test
    fun test15_representativeFrameConflict() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates = listOf(
                candidate("comedy", 0.85),
                candidate("sports", 0.3)
            ),
            representativeFrameOutlier = true
        )

        val result =
            adapter.compareFeedItem(item, s)

        assertTrue(
            result.differenceReasons.contains(
                DifferenceReason
                    .REPRESENTATIVE_FRAME_CONFLICT
            )
        )
    }

    // --------------------------------------------------
    // TEST 16: OCR INFLUENCE
    // --------------------------------------------------

    @Test
    fun test16_ocrInfluence() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates = listOf(
                candidate("comedy", 0.85),
                candidate("sports", 0.3)
            ),
            evidenceTypes =
                listOf("OCR", "visual")
        )

        val result =
            adapter.compareFeedItem(item, s)

        assertTrue(
            result.differenceReasons.contains(
                DifferenceReason
                    .OCR_CHANGED_DECISION
            )
        )
    }

    // --------------------------------------------------
    // TEST 17: PLATFORM EVIDENCE
    // --------------------------------------------------

    @Test
    fun test17_platformEvidence() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates = listOf(
                candidate("comedy", 0.85),
                candidate("sports", 0.3)
            ),
            evidenceTypes = listOf(
                "OCR", "visual", "PLATFORM"
            )
        )

        val result =
            adapter.compareFeedItem(item, s)

        assertTrue(
            result.differenceReasons.contains(
                DifferenceReason
                    .PLATFORM_EVIDENCE_CHANGED_DECISION
            )
        )
    }

    // --------------------------------------------------
    // TEST 18: INTERACTION EVIDENCE
    // --------------------------------------------------

    @Test
    fun test18_interactionEvidence() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates = listOf(
                candidate("comedy", 0.85),
                candidate("sports", 0.3)
            ),
            evidenceTypes = listOf(
                "OCR", "INTERACTION"
            )
        )

        val result =
            adapter.compareFeedItem(item, s)

        assertTrue(
            result.differenceReasons.contains(
                DifferenceReason
                    .INTERACTION_EVIDENCE_CHANGED_DECISION
            )
        )
    }

    // --------------------------------------------------
    // TEST 19: MISSING EVIDENCE HONESTLY
    // --------------------------------------------------

    @Test
    fun test19_missingEvidence_honestlyRepresented() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates =
                listOf(candidate("sports", 0.85)),
            usableEvidencePoints = 0,
            coverageRatio = 0.0,
            coverageState =
                CoverageState.NO_COVERAGE,
            evidenceTypes = emptyList(),
            uniqueEvidenceIdentities = 0
        )

        val result =
            adapter.compareFeedItem(item, s)

        assertFalse(result.evidenceAvailable)
        assertEquals(
            EvidenceStrength.NONE,
            result.evidenceStrength
        )
        assertTrue(
            result.evidenceTypesUsed.isEmpty()
        )
    }

    // --------------------------------------------------
    // TEST 20: FAILURE ISOLATION
    // --------------------------------------------------

    @Test
    fun test20_failureIsolation() {
        val item = feedItem(category = "sports")

        // FeedItem before.
        val beforeCategory = item.category

        // Use a snapshot that will cause the adapter
        // to handle edge cases safely.
        val s = ItemEvidenceSnapshot.empty(
            sessionId = "s1",
            feedItemId = "item1"
        )

        val result =
            adapter.compareFeedItem(item, s)

        // FeedItem is NOT modified even on edge case.
        assertEquals(
            beforeCategory, item.category
        )

        // Result is a safe failure state, not a crash.
        assertNotNull(result)
        assertEquals(
            ComparisonState.INSUFFICIENT_EVIDENCE,
            result.comparisonState
        )
    }

    // --------------------------------------------------
    // TEST 21: BATCH LIMIT
    // --------------------------------------------------

    @Test
    fun test21_batchLimit() {
        val items = (1..200).map { i ->
            feedItem(
                id = "item$i",
                category = "sports"
            ) to snapshot(
                candidates =
                    listOf(candidate("sports", 0.85))
            )
        }

        val results =
            adapter.compareBatch(items, limit = 50)

        assertEquals(50, results.size)
    }

    // --------------------------------------------------
    // TEST 22: BATCH ORDERING DETERMINISTIC
    // --------------------------------------------------

    @Test
    fun test22_batchOrderingDeterministic() {
        val items = listOf(
            feedItem(
                id = "a",
                category = "sports"
            ) to snapshot(
                candidates =
                    listOf(candidate("sports", 0.85))
            ),
            feedItem(
                id = "b",
                category = "comedy"
            ) to snapshot(
                candidates =
                    listOf(candidate("comedy", 0.85))
            ),
            feedItem(
                id = "c",
                category = null
            ) to snapshot(
                candidates = emptyList()
            )
        )

        val r1 =
            adapter.compareBatch(items, limit = 10)
        val r2 =
            adapter.compareBatch(items, limit = 10)

        assertEquals(r1.size, r2.size)
        for (i in r1.indices) {
            assertEquals(
                r1[i].feedItemId,
                r2[i].feedItemId
            )
            assertEquals(
                r1[i].comparisonState,
                r2[i].comparisonState
            )
        }
    }

    // --------------------------------------------------
    // TEST 23: BATCH INPUT ORDER PRESERVED
    // --------------------------------------------------

    @Test
    fun test23_batchInputOrderPreserved() {
        val items = listOf(
            feedItem(
                id = "first",
                category = "sports"
            ) to snapshot(
                candidates =
                    listOf(candidate("sports", 0.85))
            ),
            feedItem(
                id = "second",
                category = "comedy"
            ) to snapshot(
                candidates = emptyList()
            ),
            feedItem(
                id = "third",
                category = null
            ) to snapshot(
                candidates =
                    listOf(candidate("music", 0.85))
            )
        )

        val results =
            adapter.compareBatch(items, limit = 10)

        assertEquals("first", results[0].feedItemId)
        assertEquals("second", results[1].feedItemId)
        assertEquals("third", results[2].feedItemId)

        assertEquals(
            ComparisonState.AGREEMENT,
            results[0].comparisonState
        )
        assertEquals(
            ComparisonState.EVIDENCE_AWARE_UNKNOWN,
            results[1].comparisonState
        )
        assertEquals(
            ComparisonState.BASELINE_UNKNOWN,
            results[2].comparisonState
        )
    }

    // --------------------------------------------------
    // TEST 24: 8A BRIDGE COMPATIBLE
    // --------------------------------------------------

    @Test
    fun test24_8aBridgeCompatible() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates =
                listOf(candidate("sports", 0.85))
        )

        val result =
            adapter.compareFeedItem(item, s)

        // Bridge compatibility flag is set.
        assertTrue(result.bridgeCompatible)

        // Can produce an AiPredictionRecord via bridge.
        val decision = ItemDecisionEngine.decide(
            snapshot = s,
            config = ItemDecisionConfig.DEFAULT
        )
        val record =
            DecisionEvaluationBridge
                .toAiPredictionRecord(
                    decision = decision,
                    evaluationItemId = "eval-1"
                )

        assertEquals("sports", record.category)
        assertEquals(
            "AI_EVIDENCE_AWARE",
            record.source
        )
    }

    // --------------------------------------------------
    // TEST 25: EVIDENCE STRENGTH NONE
    // --------------------------------------------------

    @Test
    fun test25_evidenceStrengthNone() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates = emptyList(),
            usableEvidencePoints = 0,
            coverageRatio = 0.0,
            evidenceTypes = emptyList(),
            uniqueEvidenceIdentities = 0
        )

        val result =
            adapter.compareFeedItem(item, s)

        assertEquals(
            EvidenceStrength.NONE,
            result.evidenceStrength
        )
    }

    // --------------------------------------------------
    // TEST 26: EVIDENCE STRENGTH WEAK
    // --------------------------------------------------

    @Test
    fun test26_evidenceStrengthWeak() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates =
                listOf(candidate("sports", 0.5)),
            usableEvidencePoints = 1,
            coverageRatio = 0.02,
            evidenceTypes = listOf("OCR"),
            uniqueEvidenceIdentities = 1
        )

        val result =
            adapter.compareFeedItem(item, s)

        assertEquals(
            EvidenceStrength.WEAK,
            result.evidenceStrength
        )
    }

    // --------------------------------------------------
    // TEST 27: EVIDENCE STRENGTH MODERATE
    // --------------------------------------------------

    @Test
    fun test27_evidenceStrengthModerate() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates =
                listOf(candidate("sports", 0.7)),
            usableEvidencePoints = 5,
            coverageRatio = 0.5,
            evidenceTypes =
                listOf("OCR", "visual"),
            uniqueEvidenceIdentities = 4
        )

        val result =
            adapter.compareFeedItem(item, s)

        assertEquals(
            EvidenceStrength.MODERATE,
            result.evidenceStrength
        )
    }

    // --------------------------------------------------
    // TEST 28: EVIDENCE STRENGTH STRONG
    // --------------------------------------------------

    @Test
    fun test28_evidenceStrengthStrong() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates =
                listOf(candidate("sports", 0.9)),
            usableEvidencePoints = 20,
            coverageRatio = 0.9,
            evidenceTypes = listOf(
                "OCR", "visual", "layout",
                "interaction"
            ),
            uniqueEvidenceIdentities = 8
        )

        val result =
            adapter.compareFeedItem(item, s)

        assertEquals(
            EvidenceStrength.STRONG,
            result.evidenceStrength
        )
    }

    // --------------------------------------------------
    // TEST 29: SHORT INTERACTION REASON
    // --------------------------------------------------

    @Test
    fun test29_shortInteractionReason() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates = listOf(
                candidate("comedy", 0.85),
                candidate("sports", 0.3)
            ),
            itemDurationMs = 2000,
            evidenceTypes = listOf("OCR")
        )

        val result =
            adapter.compareFeedItem(item, s)

        assertTrue(
            result.differenceReasons.contains(
                DifferenceReason.SHORT_INTERACTION
            )
        )
    }

    // --------------------------------------------------
    // TEST 30: CATEGORY BOUNDARY REASON
    // --------------------------------------------------

    @Test
    fun test30_categoryBoundaryReason() {
        // Both in entertainment domain: comedy vs music.
        val item = feedItem(category = "comedy")
        val s = snapshot(
            candidates = listOf(
                candidate("music", 0.85),
                candidate("comedy", 0.3)
            )
        )

        val result =
            adapter.compareFeedItem(item, s)

        assertTrue(
            result.differenceReasons.contains(
                DifferenceReason.CATEGORY_BOUNDARY
            )
        )
    }

    // --------------------------------------------------
    // TEST 31: CONFIDENCE CHANGED
    // --------------------------------------------------

    @Test
    fun test31_confidenceChanged() {
        val item = feedItem(
            category = "sports",
            confidence = 0.82
        )
        val s = snapshot(
            candidates =
                listOf(candidate("sports", 0.6))
        )

        val result =
            adapter.compareFeedItem(item, s)

        assertTrue(result.confidenceChanged)
        assertEquals(
            0.82, result.baselineConfidence!!, 0.0
        )
        assertEquals(
            0.6, result.evidenceAwareConfidence!!, 0.001
        )
    }

    // --------------------------------------------------
    // TEST 32: REVIEW STATUS CHANGED
    // --------------------------------------------------

    @Test
    fun test32_reviewStatusChanged() {
        val item = feedItem(
            category = "sports",
            needsReview = false
        )
        val s = snapshot(
            candidates = listOf(
                candidate("sports", 0.6),
                candidate("comedy", 0.58)
            )
        )

        val result =
            adapter.compareFeedItem(item, s)

        assertTrue(result.reviewStatusChanged)
        assertFalse(result.baselineNeedsReview)
        assertTrue(result.evidenceAwareNeedsReview)
    }

    // --------------------------------------------------
    // TEST 33: FEED ITEM CATEGORY NEVER CHANGES
    // --------------------------------------------------

    @Test
    fun test33_feedItemCategoryNeverChanges() {
        val item = feedItem(category = "sports")

        // Run comparison with a completely different
        // evidence-aware result.
        val s = snapshot(
            candidates = listOf(
                candidate("comedy", 0.95)
            )
        )

        adapter.compareFeedItem(item, s)

        assertEquals("sports", item.category)
    }

    // --------------------------------------------------
    // TEST 34: FEED ITEM CONFIDENCE NEVER CHANGES
    // --------------------------------------------------

    @Test
    fun test34_feedItemConfidenceNeverChanges() {
        val item = feedItem(
            category = "sports",
            confidence = 0.82
        )
        val s = snapshot(
            candidates =
                listOf(candidate("comedy", 0.95))
        )

        adapter.compareFeedItem(item, s)

        assertEquals(0.82, item.confidence!!, 0.0)
    }

    // --------------------------------------------------
    // TEST 35: BATCH EMPTY LIST
    // --------------------------------------------------

    @Test
    fun test35_batchEmptyList() {
        val results =
            adapter.compareBatch(emptyList(), limit = 10)
        assertTrue(results.isEmpty())
    }

    // --------------------------------------------------
    // TEST 36: BATCH LIMIT COERCED TO MAX
    // --------------------------------------------------

    @Test
    fun test36_batchLimitCoerced() {
        val items = (1..5).map { i ->
            feedItem(
                id = "item$i",
                category = "sports"
            ) to snapshot(
                candidates =
                    listOf(candidate("sports", 0.85))
            )
        }

        // Limit above MAX_BATCH_SIZE is coerced to
        // MAX_BATCH_SIZE, but only 5 items exist.
        val results = adapter.compareBatch(
            items,
            limit = 200
        )
        assertEquals(5, results.size)
    }

    // --------------------------------------------------
    // TEST 37: EVALUATION ITEM ID PROPAGATED
    // --------------------------------------------------

    @Test
    fun test37_evaluationItemIdPropagated() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates =
                listOf(candidate("sports", 0.85))
        )

        val result = adapter.compareFeedItem(
            feedItem = item,
            snapshot = s,
            evaluationItemId = "eval-42"
        )

        assertEquals(
            "eval-42",
            result.evaluationItemId
        )
    }

    // --------------------------------------------------
    // TEST 38: DATASET VERSION PROPAGATED
    // --------------------------------------------------

    @Test
    fun test38_datasetVersionPropagated() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates =
                listOf(candidate("sports", 0.85))
        )

        val result = adapter.compareFeedItem(
            feedItem = item,
            snapshot = s,
            datasetVersion = "dataset-v3"
        )

        assertEquals(
            "dataset-v3",
            result.datasetVersion
        )
    }

    // --------------------------------------------------
    // TEST 39: MODEL DISAGREEMENT FALLBACK
    // --------------------------------------------------

    @Test
    fun test39_modelDisagreementFallback() {
        // Disagreement with only 1 evidence type
        // (no temporal, no OCR/platform/interaction,
        // no short interaction, different domains) ->
        // MODEL_DISAGREEMENT fallback.
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates = listOf(
                candidate("comedy", 0.85),
                candidate("sports", 0.3)
            ),
            evidenceTypes =
                listOf("visual", "layout"),
            itemDurationMs = 15000,
            usableEvidencePoints = 1,
            uniqueEvidenceIdentities = 3
        )

        val result =
            adapter.compareFeedItem(item, s)

        assertTrue(
            result.differenceReasons.contains(
                DifferenceReason.MODEL_DISAGREEMENT
            )
        )
    }

    // --------------------------------------------------
    // TEST 40: SEGMENTATION DIFFERENCE
    // --------------------------------------------------

    @Test
    fun test40_segmentationDifference() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates = listOf(
                candidate("comedy", 0.85),
                candidate("sports", 0.3)
            ),
            internalTransitions = listOf(
                com.example.feedsense.analysis.evidence
                    .temporal
                    .PossibleInternalTransition(
                        fromCategory = "sports",
                        toCategory = "comedy",
                        atRelativeTimeMs = 3000,
                        durationInSegmentMs = 2500,
                        evidenceCount = 4,
                        reason = "content shift"
                    )
            )
        )

        val result =
            adapter.compareFeedItem(item, s)

        assertTrue(
            result.differenceReasons.contains(
                DifferenceReason
                    .SEGMENTATION_DIFFERENCE
            )
        )
    }

    // --------------------------------------------------
    // TEST 41: REASON LIST DISTINCT
    // --------------------------------------------------

    @Test
    fun test41_reasonListDistinct() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates = listOf(
                candidate("comedy", 0.85),
                candidate("sports", 0.3)
            ),
            evidenceTypes = listOf(
                "OCR", "PLATFORM", "INTERACTION"
            ),
            itemDurationMs = 2000,
            representativeFrameOutlier = true
        )

        val result =
            adapter.compareFeedItem(item, s)

        // No duplicate reasons.
        assertEquals(
            result.differenceReasons.size,
            result.differenceReasons.distinct().size
        )
    }

    // --------------------------------------------------
    // TEST 42: TEMPORAL CONTEXT REASON
    // --------------------------------------------------

    @Test
    fun test42_temporalContextReason() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates = listOf(
                candidate("comedy", 0.85),
                candidate("sports", 0.3)
            ),
            usableEvidencePoints = 5
        )

        val result =
            adapter.compareFeedItem(item, s)

        assertTrue(
            result.differenceReasons.contains(
                DifferenceReason
                    .TEMPORAL_CONTEXT_CHANGED_DECISION
            )
        )
    }

    // --------------------------------------------------
    // TEST 43: LOW INFORMATION REASON
    // --------------------------------------------------

    @Test
    fun test43_lowInformationReason() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates = listOf(
                candidate("comedy", 0.85),
                candidate("sports", 0.3)
            ),
            evidenceTypes = listOf("OCR"),
            usableEvidencePoints = 5,
            uniqueEvidenceIdentities = 5
        )

        val result =
            adapter.compareFeedItem(item, s)

        assertTrue(
            result.differenceReasons.contains(
                DifferenceReason
                    .LOW_INFORMATION_EVIDENCE
            )
        )
    }

    // --------------------------------------------------
    // TEST 44: AGREEING CATEGORIES NO REASONS
    // --------------------------------------------------

    @Test
    fun test44_agreeing_noReasons() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates =
                listOf(candidate("sports", 0.85))
        )

        val result =
            adapter.compareFeedItem(item, s)

        assertEquals(
            ComparisonState.AGREEMENT,
            result.comparisonState
        )
        assertTrue(result.differenceReasons.isEmpty())
        assertFalse(result.categoryChanged)
    }

    // --------------------------------------------------
    // TEST 45: SESSION ID PROPAGATED
    // --------------------------------------------------

    @Test
    fun test45_sessionIdPropagated() {
        val item = feedItem(
            sessionId = "session-xyz"
        )
        val s = snapshot(
            candidates =
                listOf(candidate("sports", 0.85))
        )

        val result =
            adapter.compareFeedItem(item, s)

        assertEquals(
            "session-xyz",
            result.sessionId
        )
    }
}
