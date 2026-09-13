package com.example.feedsense.analysis.privacy

import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * Milestone 8B-13.
 *
 * Privacy confidence primitives: threshold mapping and
 * deterministic aggregation.
 */
class PrivacyConfidenceTest {

    @Test
    fun `score zero maps to NONE`() {
        assertEquals(PrivacyConfidence.NONE, PrivacyConfidence.fromScore(0.0))
    }

    @Test
    fun `score boundary mapping is exact and controlled`() {
        assertEquals(PrivacyConfidence.NONE, PrivacyConfidence.fromScore(0.0))
        assertEquals(PrivacyConfidence.LOW, PrivacyConfidence.fromScore(0.0001))
        assertEquals(PrivacyConfidence.LOW, PrivacyConfidence.fromScore(0.4))
        assertEquals(PrivacyConfidence.MEDIUM, PrivacyConfidence.fromScore(0.400001))
        assertEquals(PrivacyConfidence.MEDIUM, PrivacyConfidence.fromScore(0.7))
        assertEquals(PrivacyConfidence.HIGH, PrivacyConfidence.fromScore(0.700001))
        assertEquals(PrivacyConfidence.HIGH, PrivacyConfidence.fromScore(1.0))
    }

    @Test
    fun `out of range scores are rejected`() {
        for (bad in listOf(-0.01, 1.01, 5.0)) {
            try {
                PrivacyConfidence.fromScore(bad)
                throw AssertionError("should have thrown for $bad")
            } catch (_: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun `privacy confidence does not inherit AI confidence`() {
        // Independence requirement (spec §9): the label follows the
        // deterministic signal, never a classifier confidence.
        val blurred = PrivacyConfidence.forSignal(
            PrivacyDetectionSignal.FIXED_REGION
        )
        assertEquals(PrivacyConfidence.HIGH, blurred)
    }

    @Test
    fun `aggregation with no regions and ocr available is NONE`() {
        assertEquals(
            PrivacyConfidence.NONE,
            PrivacyConfidenceAggregator.aggregate(
                emptyList(), OcrAvailability.OCR_AVAILABLE
            )
        )
    }

    @Test
    fun `aggregation with no regions and ocr unavailable is NONE`() {
        // Note: risk is UNKNOWN here (see PrivacyRisk); confidence
        // being NONE means "no region detected", which is honest.
        assertEquals(
            PrivacyConfidence.NONE,
            PrivacyConfidenceAggregator.aggregate(
                emptyList(), OcrAvailability.OCR_UNAVAILABLE
            )
        )
    }

    @Test
    fun `aggregation uses strongest detected signal`() {
        val regions = listOf(
            PrivacyRegion(
                type = PrivacyRegionType.SYSTEM_UI,
                bounds = ProtectedRegion(0.0, 0.5, 0.2, 0.1, "A"),
                signals = listOf(PrivacyDetectionSignal.FIXED_REGION)
            ),
            PrivacyRegion(
                type = PrivacyRegionType.NOTIFICATION,
                bounds = ProtectedRegion(0.0, 0.6, 0.2, 0.1, "B"),
                signals = listOf(PrivacyDetectionSignal.OCR_TEXT_PATTERN)
            )
        )
        assertEquals(
            PrivacyConfidence.HIGH,
            PrivacyConfidenceAggregator.aggregate(regions, OcrAvailability.OCR_AVAILABLE)
        )
    }
}