package com.example.feedsense.analysis.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-13 (acceptance: edge cases).
 *
 * Degenerate / malformed / empty inputs must never crash the
 * processor or the OCR bridge, and a valid region must still be
 * processed correctly alongside a malformed one.
 */
class PrivacyEdgeCaseTest {

    private fun textured() = SyntheticFrames.checkerboard(
        100, 100, 0xFF101010.toInt(), 0xFFE0E0E0.toInt()
    )

    private fun emptyRegion() = PrivacyRegion(
        type = PrivacyRegionType.PRIVATE_TEXT,
        bounds = ProtectedRegion(0.5, 0.5, 0.0, 0.0, "EMPTY"),
        signals = listOf(PrivacyDetectionSignal.OCR_TEXT_PATTERN)
    )

    @Test
    fun `zero-area region does not crash the processor`() {
        val frame = textured()
        val result = PrivacyProcessor().process(
            frame, listOf(emptyRegion()), OcrAvailability.OCR_AVAILABLE
        )
        assertFalse(result.decision.dropped)
        assertSame(frame, result.safeFrame)
        assertEquals(1, result.decision.regionsDetected)
    }

    @Test
    fun `zero-area region does not silence a valid sibling`() {
        val valid = PrivacyRegion(
            type = PrivacyRegionType.PRIVATE_TEXT,
            bounds = ProtectedRegion(0.0, 0.3, 1.0, 0.2, "CHAT"),
            signals = listOf(PrivacyDetectionSignal.OCR_TEXT_PATTERN)
        )
        val frame = textured()
        val before = frame.frameChecksum()
        val result = PrivacyProcessor().process(
            frame, listOf(emptyRegion(), valid), OcrAvailability.OCR_AVAILABLE
        )
        assertEquals(PrivacySanitizationStatus.PARTIALLY_SANITIZED, result.decision.status)
        assertEquals(2, result.decision.transformations.size)
        // The valid region was still masked: the frame changed.
        assertNotEquals(before, result.safeFrame!!.frameChecksum())
    }

    @Test
    fun `sub-pixel region on a tiny frame is safely clamped`() {
        val hairline = PrivacyRegion(
            type = PrivacyRegionType.SYSTEM_UI,
            bounds = ProtectedRegion(0.0, 0.0, 0.001, 1.0, "HAIRLINE"),
            signals = listOf(PrivacyDetectionSignal.FIXED_REGION)
        )
        val frame = SyntheticFrames.checkerboard(
            10, 10, 0xFF101010.toInt(), 0xFFE0E0E0.toInt()
        )
        val result = PrivacyProcessor().process(
            frame, listOf(hairline), OcrAvailability.OCR_AVAILABLE
        )
        assertNotNull(result.safeFrame)
        assertFalse(result.decision.dropped)
    }

    @Test
    fun `out-of-range normalized bounds are rejected at construction`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProtectedRegion(-0.1, 0.0, 0.5, 0.5, "BAD")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProtectedRegion(0.0, 0.0, 1.2, 0.5, "BAD")
        }
        // Zero-size is VALID construction; the processor handles it.
        ProtectedRegion(0.5, 0.5, 0.0, 0.0, "EMPTY")
    }

    @Test
    fun `empty ocr input produces no regions`() {
        assertEquals(0, PrivacyOcrDetection.detectPrivateText(emptyList()).size)
        assertEquals(
            0,
            PrivacyOcrDetection.detectPrivateText(
                listOf(OcrLine(""))
            ).size
        )
    }

    @Test
    fun `three non-overlapping regions are all processed deterministically`() {
        val status = PrivacyRegion(
            type = PrivacyRegionType.SYSTEM_UI,
            bounds = ProtectedRegion(0.0, 0.0, 1.0, 0.05, "STATUS"),
            signals = listOf(PrivacyDetectionSignal.FIXED_REGION)
        )
        val chat = PrivacyRegion(
            type = PrivacyRegionType.PRIVATE_TEXT,
            bounds = ProtectedRegion(0.05, 0.3, 0.9, 0.1, "CHAT"),
            signals = listOf(PrivacyDetectionSignal.OCR_TEXT_PATTERN)
        )
        val map = PrivacyRegion(
            type = PrivacyRegionType.LOCATION_INFORMATION,
            bounds = ProtectedRegion(0.4, 0.55, 0.2, 0.1, "MAP"),
            signals = listOf(PrivacyDetectionSignal.OCR_TEXT_PATTERN)
        )
        val regions = listOf(status, chat, map)

        val a = PrivacyProcessor().process(
            textured(), regions, OcrAvailability.OCR_AVAILABLE
        )
        val b = PrivacyProcessor().process(
            textured(), regions, OcrAvailability.OCR_AVAILABLE
        )

        assertEquals(a.toSafeMetadataMap(), b.toSafeMetadataMap())
        assertEquals(a.safeFrame!!.frameChecksum(), b.safeFrame!!.frameChecksum())
        assertEquals(PrivacyRisk.HIGH, a.decision.risk)
        assertEquals(PrivacySanitizationStatus.SANITIZED, a.decision.status)
        assertEquals(3, a.decision.transformations.size)
        assertTrue(
            a.decision.transformations.any { it.transformation == PrivacyTransformation.MASK }
        )
    }
}