package com.example.feedsense.analysis.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-13.
 *
 * AnonymizationResult contract: serializable metadata is safe,
 * raw content never leaves the frame buffer.
 */
class AnonymizationResultTest {

    private val statusBar = PrivacyRegion(
        type = PrivacyRegionType.SYSTEM_UI,
        bounds = ProtectedRegion(0.0, 0.0, 1.0, 0.05, "STATUS"),
        signals = listOf(
            PrivacyDetectionSignal.FIXED_REGION,
            PrivacyDetectionSignal.SYSTEM_UI_STRUCTURE
        )
    )

    private val chatBubble = PrivacyRegion(
        type = PrivacyRegionType.PRIVATE_TEXT,
        bounds = ProtectedRegion(0.05, 0.30, 0.90, 0.20, "CHAT"),
        signals = listOf(PrivacyDetectionSignal.OCR_TEXT_PATTERN)
    )

    private fun result(): AnonymizationResult {
        val frame = SyntheticFrames.checkerboard(
            100, 100, 0xFF101010.toInt(), 0xFFE0E0E0.toInt()
        )
        return PrivacyProcessor().process(
            frame, listOf(statusBar, chatBubble), OcrAvailability.OCR_AVAILABLE
        )
    }

    @Test
    fun `safe metadata map carries only allow-listed keys`() {
        val metadata = result().toSafeMetadataMap()
        val allowList = setOf(
            "frameId", "policyVersion", "processingVersion",
            "rulesVersion", "ocrAvailability", "regionsDetected",
            "regionsProcessed", "risk", "confidence", "status",
            "dropped", "evidenceLoss", "transformations"
        )
        assertEquals(allowList, metadata.keys)
    }

    @Test
    fun `safe metadata never contains raw content`() {
        val metadata = result().toSafeMetadataMap()
        val joined = metadata.values.joinToString(" ")
        assertFalse(joined.contains("CHAT"))
        assertFalse(joined.contains("0.05"))
        assertFalse(joined.contains("OCR_TEXT"))
        assertFalse(joined.contains("pixel"))
        assertFalse(joined.contains("STATUS_BAR"))
    }

    @Test
    fun `log line is single-line and key-value`() {
        val line = result().toSafeLogLine()
        assertTrue(line.startsWith("privacy_processing "))
        assertTrue(line.contains("frameId="))
        assertTrue(line.contains("status=SANITIZED"))
        assertFalse(line.contains("\n"))
    }

    @Test
    fun `transformations log preserves type-to-strongest summary`() {
        val line = result().toSafeLogLine()
        assertTrue(line.contains("PRIVATE_TEXT:MASK"))
        assertTrue(line.contains("SYSTEM_UI:BLUR"))
    }

    @Test
    fun `audit mirrors the decision without raw content`() {
        val audit = result().toSanitizationAudit()
        assertEquals(PrivacySanitizationStatus.SANITIZED, audit.status)
        assertEquals(2, audit.regionsDetected)
        assertEquals(2, audit.processedRegionCount)
        assertTrue(audit.evidenceLostDueToSanitization)
        assertEquals("frame", audit.frameId!!.substring(0, 5))
    }

    @Test
    fun `dropped result reports dropped and has no safe frame`() {
        val fullScreen = PrivacyRegion(
            type = PrivacyRegionType.PRIVATE_TEXT,
            bounds = ProtectedRegion(0.0, 0.0, 1.0, 1.0, "FULLSCREEN"),
            signals = listOf(PrivacyDetectionSignal.OCR_TEXT_PATTERN)
        )
        val frame = SyntheticFrames.checkerboard(
            100, 100, 0xFF101010.toInt(), 0xFFE0E0E0.toInt()
        )
        val dropped = PrivacyProcessor(PrivacyPolicy.STRICT).process(
            frame, listOf(fullScreen), OcrAvailability.OCR_AVAILABLE
        )
        assertEquals(null, dropped.safeFrame)
        assertEquals("true", dropped.toSafeMetadataMap()["dropped"])
        assertTrue(dropped.decision.evidenceLoss != PrivacyEvidenceLoss.NONE)
    }

    @Test
    fun `decision is sealed against post-hoc content`() {
        // The transformation record never carries bounds, so no
        // coordinate pair can leak into the metadata.
        val decision = result().decision
        decision.transformations.forEach {
            assertNotNull(it.regionType)
            assertNotNull(it.transformation)
            assertFalse(it.toString().contains("bounds"))
        }
    }
}