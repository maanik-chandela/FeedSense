package com.example.feedsense.analysis.evidence.decision

import com.example.feedsense.analysis.evidence.temporal.CandidateCategoryScore
import com.example.feedsense.analysis.evidence.temporal.ConflictLevel
import com.example.feedsense.analysis.evidence.temporal.CoverageState
import com.example.feedsense.analysis.evidence.temporal.ItemEvidenceSnapshot
import com.example.feedsense.model.FeedItem
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-7.
 *
 * Read-only evidence-aware adapter tests.
 *
 * Proves the adapter:
 *   - never mutates the baseline FeedItem (no-mutation)
 *   - preserves baseline category/confidence verbatim
 *   - correctly derives the experimental 8B-7 result
 *   - reports categoryChanged correctly (descriptive, not
 *     correctness)
 *   - preserves UNKNOWN / AMBIGUOUS / INSUFFICIENT_EVIDENCE
 *     decision states
 *   - preserves fusion/decision versions
 *   - is deterministic (same input -> same output)
 */
class ReadOnlyEvidenceAwareAdapterTest {

    private val adapter = ReadOnlyEvidenceAwareAdapter()

    // --------------------------------------------------
    // HELPERS
    // --------------------------------------------------

    private fun feedItem(
        category: String? = "sports",
        confidence: Double? = 0.82,
        contentType: String = FeedItem.CONTENT_SHORT_VIDEO,
        skipped: Boolean = false,
        uncertaintyLevel: String =
            FeedItem.UNCERTAINTY_LOW,
        needsReview: Boolean = false,
        modelVersion: String? = "baseline-v1",
        sessionId: String = "s1",
        id: String = "item1"
    ): FeedItem {
        return FeedItem(
            id = id,
            sessionId = sessionId,
            startTime = LocalDateTime.now(),
            endTime = LocalDateTime.now().plusSeconds(10),
            category = category,
            confidence = confidence,
            contentType = contentType,
            skipped = skipped,
            uncertaintyLevel = uncertaintyLevel,
            needsReview = needsReview,
            modelVersion = modelVersion,
            representativeFramePath = "/tmp/rep.pdf"
        )
    }

    private fun candidate(
        category: String,
        support: Double
    ): CandidateCategoryScore {
        return CandidateCategoryScore(
            category = category,
            supportScore = support,
            supportingCount = 5,
            contradictingCount = 0,
            evidenceTypes =
                listOf("OCR", "visual", "layout"),
            persistenceLevel = "SUSTAINED",
            firstSeenRelativeMs = 1000,
            lastSeenRelativeMs = 9000,
            temporalSpreadMs = 8000
        )
    }

    private fun snapshot(
        candidates: List<CandidateCategoryScore>,
        coverageRatio: Double = 0.9,
        evidenceTypes: List<String> =
            listOf("OCR", "visual", "layout"),
        uniqueIdentities: Int = 8,
        conflict: ConflictLevel = ConflictLevel.NONE
    ): ItemEvidenceSnapshot {
        return ItemEvidenceSnapshot(
            sessionId = "s1",
            feedItemId = "item1",
            snapshotVersion = "item-evidence-snapshot-v1",
            fusionVersion = "temporal-fusion-v1",
            fusionConfigVersion = "temporal-fusion-config-v1",
            totalEvidencePoints = 30,
            usableEvidencePoints = 20,
            analyzedFrameCount = 25,
            totalFrameCount = 30,
            itemDurationMs = 10000,
            coverageState = CoverageState.HIGH_COVERAGE,
            coverageRatio = coverageRatio,
            observedDurationMs = 9000,
            hasGap = false,
            gapCount = 0,
            gapTotalDurationMs = 0,
            uniqueEvidenceIdentities = uniqueIdentities,
            evidenceTypesPresent = evidenceTypes,
            extractorNames =
                listOf("ocr-extractor", "visual-extractor"),
            candidateCategories = candidates,
            supportingEvidenceByCategory = emptyMap(),
            contradictingEvidenceByCategory = emptyMap(),
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
            possibleInternalTransitions = emptyList(),
            limitations = emptyList(),
            representativeFrameAgreement = null,
            representativeFrameOutlier = null,
            createdAtMs = 0L,
            fusionDurationMs = 0L
        )
    }

    // --------------------------------------------------
    // TEST 1: NO-MUTATION — BASELINE PRESERVED
    // --------------------------------------------------

    @Test
    fun test1_noMutation_baselinePreserved() {
        val item = feedItem(category = "sports",
            confidence = 0.82)
        val s = snapshot(
            candidates = listOf(candidate("sports", 0.85))
        )

        val cmp = adapter.evaluate(item, s)

        // Baseline FeedItem fields are untouched.
        assertEquals("sports", item.category)
        assertEquals(0.82, item.confidence!!, 0.0)
        assertEquals(FeedItem.CONTENT_SHORT_VIDEO,
            item.contentType)
        assertFalse(item.skipped)
        assertEquals(FeedItem.UNCERTAINTY_LOW,
            item.uncertaintyLevel)
        assertFalse(item.needsReview)
        assertEquals("baseline-v1", item.modelVersion)
        assertEquals("item1", item.id)

        // Comparison-derived baseline matches verbatim.
        assertEquals("item1", cmp.feedItemId)
        assertEquals("sports", cmp.baselineCategory)
        assertEquals(0.82, cmp.baselineConfidence!!, 0.0)
    }

    // --------------------------------------------------
    // TEST 2: SAME PREDICTION -> categoryChanged = false
    // --------------------------------------------------

    @Test
    fun test2_samePrediction_noChange() {
        val item = feedItem(category = "sports",
            confidence = 0.82)
        val s = snapshot(
            candidates = listOf(candidate("sports", 0.85))
        )
        val cmp = adapter.evaluate(item, s)

        assertEquals("sports", cmp.baselineCategory)
        assertEquals("sports", cmp.evidenceAwareCategory)
        assertEquals(
            DecisionState.DECIDED, cmp.decisionState
        )
        assertFalse(cmp.categoryChanged)
        assertFalse(cmp.baselineWasUnknown)
        assertFalse(cmp.evidenceAwareWasUnknown)
    }

    // --------------------------------------------------
    // TEST 3: CHANGED PREDICTION -> categoryChanged = true
    // --------------------------------------------------

    @Test
    fun test3_changedPrediction() {
        val item = feedItem(category = "sports",
            confidence = 0.82)
        val s = snapshot(
            candidates = listOf(
                candidate("memes", 0.85),
                candidate("sports", 0.2)
            )
        )
        val cmp = adapter.evaluate(item, s)

        assertEquals("sports", cmp.baselineCategory)
        assertEquals("memes", cmp.evidenceAwareCategory)
        assertTrue(cmp.categoryChanged)
    }

    // --------------------------------------------------
    // TEST 4: BASELINE UNKNOWN
    // --------------------------------------------------

    @Test
    fun test4_baselineUnknown() {
        // Baseline has no category.
        val item = feedItem(category = null, confidence = null)
        val s = snapshot(
            candidates = listOf(candidate("sports", 0.85))
        )
        val cmp = adapter.evaluate(item, s)

        assertTrue(cmp.baselineWasUnknown)
        assertEquals("sports", cmp.evidenceAwareCategory)
        assertFalse(cmp.evidenceAwareWasUnknown)
        // Baseline never gains a category from the adapter.
        assertNull(item.category)
    }

    // --------------------------------------------------
    // TEST 5: EVIDENCE-AWARE UNKNOWN PRESERVED
    // --------------------------------------------------

    @Test
    fun test5_evidenceAwareUnknown_preserved() {
        // No candidates -> 8B-7 abstains UNKNOWN.
        val item = feedItem(category = "sports")
        val s = snapshot(candidates = emptyList())
        val cmp = adapter.evaluate(item, s)

        assertEquals(DecisionState.UNKNOWN, cmp.decisionState)
        assertNull(cmp.evidenceAwareCategory)
        assertTrue(cmp.evidenceAwareWasUnknown)
        assertNull(cmp.evidenceAwareSupport)
        // Baseline remains authoritative and unchanged.
        assertEquals("sports", item.category)
    }

    // --------------------------------------------------
    // TEST 6: AMBIGUOUS PRESERVED
    // --------------------------------------------------

    @Test
    fun test6_ambiguousPreserved_noForcedCategory() {
        val item = feedItem(category = "ads")
        // Tight margin -> not arbitrarily resolved.
        val s = snapshot(
            candidates = listOf(
                candidate("sports", 0.6),
                candidate("ads", 0.58)
            )
        )
        val cmp = adapter.evaluate(item, s)

        assertEquals(DecisionState.AMBIGUOUS, cmp.decisionState)
        // Leadership is preserved but not forced to a
        // single confident label.
        assertEquals("sports", cmp.evidenceAwareCategory)
        assertEquals(UncertaintyState.HIGH, cmp.uncertainty)
    }

    // --------------------------------------------------
    // TEST 7: INSUFFICIENT_EVIDENCE PRESERVED
    // --------------------------------------------------

    @Test
    fun test7_insufficientEvidencePreserved() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates = listOf(candidate("sports", 0.9)),
            coverageRatio = 0.02,
            evidenceTypes =
                listOf("OCR"),
            uniqueIdentities = 1
        )
        val cmp = adapter.evaluate(item, s)

        assertEquals(
            DecisionState.INSUFFICIENT_EVIDENCE,
            cmp.decisionState
        )
        assertNull(cmp.evidenceAwareCategory)
        assertTrue(cmp.evidenceAwareWasUnknown)
        // evidenceAwareSupport is null when abstaining.
        assertNull(cmp.evidenceAwareSupport)
    }

    // --------------------------------------------------
    // TEST 8: VERSIONING PRESERVED
    // --------------------------------------------------

    @Test
    fun test8_versioning() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates = listOf(candidate("sports", 0.85))
        )
        val cmp = adapter.evaluate(item, s)

        assertEquals("temporal-fusion-v1", cmp.fusionVersion)
        assertEquals(
            ItemPredictionResult.DECISION_VERSION,
            cmp.decisionVersion
        )
    }

    // --------------------------------------------------
    // TEST 9: DETERMINISM
    // --------------------------------------------------

    @Test
    fun test9_determinism() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates = listOf(candidate("sports", 0.85))
        )
        val a = adapter.evaluate(item, s)
        val b = adapter.evaluate(item, s)
        assertEquals(a, b)
        assertFalse(a.categoryChanged)
    }

    // --------------------------------------------------
    // TEST 10: BASELINE CONFIDENCE IS NOT REINTERPRETED
    // --------------------------------------------------

    @Test
    fun test10_baselineConfidenceDistinctFromSupport() {
        val item = feedItem(category = "sports",
            confidence = 0.82)
        val s = snapshot(
            candidates = listOf(candidate("sports", 0.6))
        )
        val cmp = adapter.evaluate(item, s)

        // Baseline confidence preserved verbatim.
        assertEquals(0.82, cmp.baselineConfidence!!, 0.0)
        // Evidence support is a separate value.
        assertEquals(0.6, cmp.evidenceAwareSupport!!, 0.001)
        // They must stay independent.
        assertTrue(cmp.baselineConfidence !=
            cmp.evidenceAwareSupport)
    }

    // --------------------------------------------------
    // TEST 11: SKIPPED BASELINE NOT ALTERED
    // --------------------------------------------------

    @Test
    fun test11_skippedBaselineNotAltered() {
        val item = feedItem(category = "sports", skipped = true)
        val s = snapshot(
            candidates = listOf(candidate("sports", 0.85))
        )
        val cmp = adapter.evaluate(item, s)

        assertTrue(cmp.baselineSkipped)
        assertTrue(item.skipped)
    }
}
