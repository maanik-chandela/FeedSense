package com.example.feedsense.analysis.evidence.decision

import com.example.feedsense.analysis.evidence.temporal.CandidateCategoryScore
import com.example.feedsense.analysis.evidence.temporal.CoverageState
import com.example.feedsense.analysis.evidence.temporal.ItemEvidenceSnapshot
import com.example.feedsense.analysis.privacy.EvidenceAvailability
import com.example.feedsense.analysis.privacy.PrivacyEvidenceLoss
import com.example.feedsense.analysis.privacy.PrivacyEvidenceMetadata
import com.example.feedsense.analysis.privacy.PrivacyMetric
import com.example.feedsense.analysis.privacy.PrivacyMetrics
import com.example.feedsense.analysis.privacy.PrivacySanitizationStatus
import com.example.feedsense.analysis.privacy.PrivacySanitizationVersion
import com.example.feedsense.model.FeedItem
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-10.
 *
 * Integration: 8B-6/7/8 evidence snapshots + privacy
 * metadata flow through the production adapter.
 *
 * Guarantees under test:
 *   - Existing snapshots WITHOUT privacy metadata still
 *     compile and produce NONE behavior (source compat).
 *   - Privacy metadata surfaces honest status + loss on the
 *     comparison result.
 *   - Privacy reasons are appended ONLY when the metadata
 *     actually supports them.
 *   - The baseline FeedItem is read-only.
 */
class PrivacyEvidenceDecisionIntegrationTest {

    private val adapter = EvidenceAwareProductionAdapter()

    // --------------------------------------------------
    // FIXTURES (mirrors EvidenceAwareProductionAdapterTest)
    // --------------------------------------------------

    private fun feedItem(
        category: String? = "sports",
        confidence: Double? = 0.82
    ): FeedItem {
        return FeedItem(
            id = "item1",
            sessionId = "s1",
            startTime = LocalDateTime.now(),
            endTime = LocalDateTime.now().plusSeconds(10),
            category = category,
            confidence = confidence,
            contentType = FeedItem.CONTENT_SHORT_VIDEO,
            platform = "instagram",
            uncertaintyLevel = FeedItem.UNCERTAINTY_LOW,
            needsReview = false,
            modelVersion = "baseline-v1",
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
            evidenceTypes = listOf("OCR", "visual", "layout"),
            persistenceLevel = "SUSTAINED",
            firstSeenRelativeMs = 1000,
            lastSeenRelativeMs = 9000,
            temporalSpreadMs = 8000
        )
    }

    private fun snapshot(
        candidates: List<CandidateCategoryScore> =
            emptyList(),
        privacyEvidence: PrivacyEvidenceMetadata =
            PrivacyEvidenceMetadata.NONE
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
            coverageRatio = 0.9,
            observedDurationMs = 9000,
            hasGap = false,
            gapCount = 0,
            gapTotalDurationMs = 0,
            uniqueEvidenceIdentities = 8,
            evidenceTypesPresent =
                listOf("OCR", "visual", "layout"),
            extractorNames = listOf(
                "ocr-extractor",
                "visual-extractor"
            ),
            candidateCategories = candidates,
            supportingEvidenceByCategory = emptyMap(),
            contradictingEvidenceByCategory = emptyMap(),
            conflictLevel =
                com.example.feedsense.analysis.evidence.temporal.ConflictLevel.NONE,
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
            fusionDurationMs = 0L,
            privacyEvidence = privacyEvidence
        )
    }

    // --------------------------------------------------
    // SOURCE COMPAT
    // --------------------------------------------------

    @Test
    fun `snapshot without privacy metadata still compiles to NONE`() {
        val item = feedItem()
        val s = snapshot( // no privacyEvidence passed
            candidates = listOf(
                candidate("sports", 0.85)
            )
        )

        val result = adapter.compareFeedItem(item, s)

        assertEquals(
            PrivacySanitizationStatus.UNKNOWN.label,
            result.privacyStatusLabel
        )
        assertTrue(result.privacyEvidenceLossLabels.isEmpty())
        assertFalse(
            result.differenceReasons.contains(
                DifferenceReason.EVIDENCE_LOST_DUE_TO_SANITIZATION
            )
        )
        assertFalse(
            result.differenceReasons.contains(
                DifferenceReason.PRIVACY_REDACTION_AFFECTED_DECISION
            )
        )
    }

    // --------------------------------------------------
    // STATUS SURFACING
    // --------------------------------------------------

    @Test
    fun `sanitized metadata surfaces status and version`() {
        val item = feedItem()
        val s = snapshot(
            candidates = listOf(
                candidate("sports", 0.85)
            ),
            privacyEvidence = PrivacyEvidenceMetadata(
                status = PrivacySanitizationStatus.SANITIZED,
                availability =
                    EvidenceAvailability.CONTENT_DETECTED,
                loss = PrivacyEvidenceLoss.NONE,
                decisionAffected = false
            )
        )

        val result = adapter.compareFeedItem(item, s)

        assertEquals(
            PrivacySanitizationStatus.SANITIZED.label,
            result.privacyStatusLabel
        )
        assertEquals(
            PrivacySanitizationVersion.POLICY,
            result.privacyPolicyVersion
        )
    }

    // --------------------------------------------------
    // PRIVACY REASONS ARE EVIDENCE-BACKED
    // --------------------------------------------------

    @Test
    fun `evidence loss is reported only when metadata says so`() {
        val item = feedItem()
        val s = snapshot(
            candidates = listOf(
                candidate("sports", 0.85)
            ),
            privacyEvidence = PrivacyEvidenceMetadata(
                status = PrivacySanitizationStatus.SANITIZED,
                availability =
                    EvidenceAvailability.CONTENT_DETECTED,
                loss =
                    PrivacyEvidenceLoss.EVIDENCE_LOST_DUE_TO_SANITIZATION,
                decisionAffected = false
            )
        )

        val result = adapter.compareFeedItem(item, s)

        assertTrue(
            result.differenceReasons.contains(
                DifferenceReason.EVIDENCE_LOST_DUE_TO_SANITIZATION
            )
        )
        assertEquals(
            listOf(
                PrivacyEvidenceLoss
                    .EVIDENCE_LOST_DUE_TO_SANITIZATION.label
            ),
            result.privacyEvidenceLossLabels
        )
        // No decisionAffected → no redaction reason.
        assertFalse(
            result.differenceReasons.contains(
                DifferenceReason.PRIVACY_REDACTION_AFFECTED_DECISION
            )
        )
    }

    @Test
    fun `redaction-affecting decision is reported on disagreement`() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates = listOf(
                candidate("comedy", 0.90)
            ),
            privacyEvidence = PrivacyEvidenceMetadata(
                status = PrivacySanitizationStatus.SANITIZED,
                availability =
                    EvidenceAvailability.CONTENT_DETECTED,
                loss = PrivacyEvidenceLoss.OCR_TEXT_REDACTED,
                decisionAffected = true
            )
        )

        val result = adapter.compareFeedItem(item, s)

        assertTrue(
            result.differenceReasons.contains(
                DifferenceReason.PRIVACY_REDACTION_AFFECTED_DECISION
            )
        )
        assertTrue(
            result.differenceReasons.contains(
                DifferenceReason.EVIDENCE_LOST_DUE_TO_SANITIZATION
            )
        )
        assertEquals(true, result.categoryChanged)
    }

    @Test
    fun `redaction reason never appears without disagreement`() {
        val item = feedItem(category = "sports")
        val s = snapshot(
            candidates = listOf(
                candidate("sports", 0.85)
            ),
            privacyEvidence = PrivacyEvidenceMetadata(
                decisionAffected = true
            )
        )

        val result = adapter.compareFeedItem(item, s)

        assertFalse(result.categoryChanged)
        assertFalse(
            result.differenceReasons.contains(
                DifferenceReason.PRIVACY_REDACTION_AFFECTED_DECISION
            )
        )
    }

    // --------------------------------------------------
    // BASELINE READ-ONLY
    // --------------------------------------------------

    @Test
    fun `adapter never modifies the baseline FeedItem`() {
        val before = feedItem(
            category = "sports",
            confidence = 0.82
        )
        val s = snapshot(
            candidates = listOf(
                candidate("comedy", 0.90)
            ),
            privacyEvidence = PrivacyEvidenceMetadata(
                decisionAffected = true
            )
        )

        adapter.compareFeedItem(before, s)

        assertEquals("sports", before.category)
        assertEquals(0.82, before.confidence)
        assertEquals("baseline-v1", before.modelVersion)
        assertEquals(false, before.needsReview)
    }

    // --------------------------------------------------
    // METRICS & VERSIONING (satellite contracts)
    // --------------------------------------------------

    @Test
    fun `privacy metrics carry the policy version`() {
        assertEquals(
            PrivacySanitizationVersion.POLICY,
            PrivacyMetrics.METRICS_VERSION
        )
        assertEquals(
            "privacy.frames_sanitized",
            PrivacyMetric.FRAMES_SANITIZED
        )
    }
}