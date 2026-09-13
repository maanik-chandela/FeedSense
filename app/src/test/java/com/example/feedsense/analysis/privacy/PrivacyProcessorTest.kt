package com.example.feedsense.analysis.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-13.
 *
 * Privacy processing pipeline: DECIDE -> TRANSFORM -> VALIDATE.
 *
 * All fixtures are synthetic; no real private data is used.
 */
class PrivacyProcessorTest {

    private val statusBar = PrivacyRegion(
        type = PrivacyRegionType.SYSTEM_UI,
        bounds = ProtectedRegion(0.0, 0.0, 1.0, 0.05, "STATUS"),
        signals = listOf(
            PrivacyDetectionSignal.FIXED_REGION,
            PrivacyDetectionSignal.SYSTEM_UI_STRUCTURE
        )
    )

    private val inboxCard = PrivacyRegion(
        type = PrivacyRegionType.NOTIFICATION,
        bounds = ProtectedRegion(0.0, 0.12, 1.0, 0.10, "INBOX"),
        signals = listOf(PrivacyDetectionSignal.NOTIFICATION_STYLE)
    )

    private val chatBubble = PrivacyRegion(
        type = PrivacyRegionType.PRIVATE_TEXT,
        bounds = ProtectedRegion(0.05, 0.30, 0.90, 0.20, "CHAT"),
        signals = listOf(PrivacyDetectionSignal.OCR_TEXT_PATTERN)
    )

    private fun textured() = SyntheticFrames.checkerboard(
        100, 100, 0xFF101010.toInt(), 0xFFE0E0E0.toInt()
    )

    private fun solid() = SyntheticFrames.solid(100, 100, 0xFFFFFFFF.toInt())

    @Test
    fun `research mode blurs system ui and marks frame safe`() {
        val frame = textured()
        val p = PrivacyProcessor()
        val before = frame.frameChecksum()
        val result = p.process(frame, listOf(statusBar), OcrAvailability.OCR_AVAILABLE)
        assertEquals(PrivacySanitizationStatus.SANITIZED, result.decision.status)
        assertSame(frame, result.safeFrame)
        assertTrue(result.decision.isSafeForResearchUse)
        assertFalse(result.decision.dropped)
        assertNotEquals(before, result.safeFrame!!.frameChecksum())
        val applied = result.decision.transformations.first()
        assertEquals(PrivacyRegionType.SYSTEM_UI, applied.regionType)
        assertEquals(PrivacyTransformation.BLUR, applied.transformation)
        assertEquals(PrivacyRisk.LOW, result.decision.risk)
    }

    @Test
    fun `private text is masked under research policy`() {
        val p = PrivacyProcessor()
        val frame = textured()
        val result = p.process(frame, listOf(chatBubble), OcrAvailability.OCR_AVAILABLE)
        assertEquals(PrivacySanitizationStatus.SANITIZED, result.decision.status)
        val applied = result.decision.transformations.first()
        assertEquals(PrivacyTransformation.MASK, applied.transformation)
    }

    @Test
    fun `notification is blurred and risk is medium`() {
        val result = PrivacyProcessor().process(
            textured(), listOf(inboxCard), OcrAvailability.OCR_AVAILABLE
        )
        assertEquals(PrivacyRisk.MEDIUM, result.decision.risk)
        assertEquals(
            PrivacyTransformation.BLUR,
            result.decision.transformations.first().transformation
        )
    }

    @Test
    fun `no sensitive content and ocr available is NOT_REQUIRED and safe`() {
        val frame = textured()
        val result = PrivacyProcessor().process(
            frame, emptyList(), OcrAvailability.OCR_AVAILABLE
        )
        assertEquals(PrivacySanitizationStatus.NOT_REQUIRED, result.decision.status)
        assertEquals(PrivacyRisk.NONE, result.decision.risk)
        assertTrue(result.decision.isSafeForResearchUse)
        assertSame(frame, result.safeFrame)
    }

    @Test
    fun `no content but ocr unavailable surfaces unknown risk, NOT safe`() {
        val result = PrivacyProcessor().process(
            textured(), emptyList(), OcrAvailability.OCR_UNAVAILABLE
        )
        assertEquals(PrivacyRisk.UNKNOWN, result.decision.risk)
        assertEquals(PrivacySanitizationStatus.NOT_REQUIRED, result.decision.status)
        assertFalse(result.decision.isSafeForResearchUse)
    }

    @Test
    fun `ocr unavailable with unknown region still protects it`() {
        val unknown = PrivacyRegion(
            type = PrivacyRegionType.UNKNOWN_SENSITIVE_REGION,
            bounds = ProtectedRegion(0.0, 0.4, 1.0, 0.2, "UNKNOWN"),
            signals = listOf(PrivacyDetectionSignal.LOW_CONFIDENCE_FALLBACK)
        )
        val result = PrivacyProcessor().process(
            textured(), listOf(unknown), OcrAvailability.OCR_UNAVAILABLE
        )
        assertEquals(PrivacyRisk.HIGH, result.decision.risk)
        assertEquals(PrivacySanitizationStatus.SANITIZED, result.decision.status)
        // Unknown-risk rule for research mode: BLUR, never NONE.
        assertEquals(
            PrivacyTransformation.BLUR,
            result.decision.transformations.first().transformation
        )
    }

    @Test
    fun `disabled policy leaves content but is explicitly unsafe`() {
        val frame = textured()
        val result = PrivacyProcessor(PrivacyPolicy.DISABLED).process(
            frame, listOf(chatBubble), OcrAvailability.OCR_AVAILABLE
        )
        assertEquals(
            PrivacySanitizationStatus.SANITIZATION_UNAVAILABLE,
            result.decision.status
        )
        assertFalse(result.decision.isSafeForResearchUse)
        assertSame(frame, result.safeFrame)
    }

    @Test
    fun `strict policy drops a fully-covered frame`() {
        val fullScreen = PrivacyRegion(
            type = PrivacyRegionType.PRIVATE_TEXT,
            bounds = ProtectedRegion(0.0, 0.0, 1.0, 1.0, "FULLSCREEN"),
            signals = listOf(PrivacyDetectionSignal.OCR_TEXT_PATTERN)
        )
        val result = PrivacyProcessor(PrivacyPolicy.STRICT).process(
            textured(), listOf(fullScreen), OcrAvailability.OCR_AVAILABLE
        )
        assertTrue(result.decision.dropped)
        assertNull(result.safeFrame)
        assertEquals(PrivacySanitizationStatus.SANITIZED, result.decision.status)
        assertEquals(
            PrivacyEvidenceLoss.EVIDENCE_LOST_DUE_TO_SANITIZATION,
            result.decision.evidenceLoss
        )
        assertFalse(result.decision.isSafeForResearchUse)
    }

    @Test
    fun `strict policy keeps a frame with meaningful research value`() {
        val halfScreen = PrivacyRegion(
            type = PrivacyRegionType.PRIVATE_TEXT,
            bounds = ProtectedRegion(0.0, 0.0, 1.0, 0.55, "HALF"),
            signals = listOf(PrivacyDetectionSignal.OCR_TEXT_PATTERN)
        )
        val result = PrivacyProcessor(PrivacyPolicy.STRICT).process(
            textured(), listOf(halfScreen), OcrAvailability.OCR_AVAILABLE
        )
        assertFalse(result.decision.dropped)
        assertNotNull(result.safeFrame)
        assertEquals(PrivacySanitizationStatus.SANITIZED, result.decision.status)
    }

    @Test
    fun `research and balanced never drop`() {
        val fullScreen = PrivacyRegion(
            type = PrivacyRegionType.PRIVATE_TEXT,
            bounds = ProtectedRegion(0.0, 0.0, 1.0, 1.0, "FULLSCREEN"),
            signals = listOf(PrivacyDetectionSignal.OCR_TEXT_PATTERN)
        )
        for (policy in listOf(PrivacyPolicy.RESEARCH, PrivacyPolicy.BALANCED)) {
            val result = PrivacyProcessor(policy).process(
                textured(), listOf(fullScreen), OcrAvailability.OCR_AVAILABLE
            )
            assertFalse(result.decision.dropped)
            assertNotNull(result.safeFrame)
        }
    }

    @Test
    fun `processing is deterministic`() {
        val a = PrivacyProcessor().process(
            textured(), listOf(statusBar, inboxCard), OcrAvailability.OCR_AVAILABLE
        )
        val b = PrivacyProcessor().process(
            textured(), listOf(statusBar, inboxCard), OcrAvailability.OCR_AVAILABLE
        )
        assertEquals(a.toSafeMetadataMap(), b.toSafeMetadataMap())
        assertEquals(
            a.safeFrame!!.frameChecksum(),
            b.safeFrame!!.frameChecksum()
        )
    }

    @Test
    fun `uniform region blur escalates to mask`() {
        val result = PrivacyProcessor().process(
            solid(), listOf(statusBar), OcrAvailability.OCR_AVAILABLE
        )
        assertEquals(PrivacySanitizationStatus.SANITIZED, result.decision.status)
        // The no-op blur had to escalate to produce evidence.
        assertEquals(
            PrivacyTransformation.MASK,
            result.decision.transformations.first().transformation
        )
    }

    @Test
    fun `uniform region pixelate escalates under balanced policy`() {
        val location = PrivacyRegion(
            type = PrivacyRegionType.LOCATION_INFORMATION,
            bounds = ProtectedRegion(0.4, 0.4, 0.2, 0.2, "LOC"),
            signals = listOf(PrivacyDetectionSignal.OCR_TEXT_PATTERN)
        )
        val result = PrivacyProcessor(PrivacyPolicy.BALANCED).process(
            solid(), listOf(location), OcrAvailability.OCR_AVAILABLE
        )
        assertEquals(PrivacySanitizationStatus.SANITIZED, result.decision.status)
        assertEquals(
            PrivacyTransformation.MASK,
            result.decision.transformations.first().transformation
        )
    }

    @Test
    fun `stronger transformation wins over an overlapping weaker one`() {
        // inbox (BLUR) contains the chat (MASK); the inner chat
        // must end up masked (severity ascending application).
        val result = PrivacyProcessor().process(
            textured(), listOf(inboxCard, chatBubble), OcrAvailability.OCR_AVAILABLE
        )
        val chat = result.decision.transformations.first {
            it.regionType == PrivacyRegionType.PRIVATE_TEXT
        }
        assertEquals(PrivacyTransformation.MASK, chat.transformation)
        assertEquals(PrivacySanitizationStatus.SANITIZED, result.decision.status)
        assertEquals(2, result.decision.transformations.size)
    }

    @Test
    fun `full-width crop produces a shorter safe frame`() {
        val table = PrivacyRuleTable.custom(
            listOf(
                PrivacyRule(
                    PrivacyRegionType.SYSTEM_UI,
                    PrivacyTransformation.CROP,
                    priority = 1
                )
            )
        )
        val frame = SyntheticFrames.checkerboard(100, 200, 0xFF101010.toInt(), 0xFFE0E0E0.toInt())
        val result = PrivacyProcessor(PrivacyPolicy.RESEARCH, table).process(
            frame, listOf(statusBar), OcrAvailability.OCR_AVAILABLE
        )
        // Status bar = rows 0..9 on a 200-height frame.
        assertNotNull(result.safeFrame)
        assertEquals(190, result.safeFrame!!.height)
        assertEquals(PrivacySanitizationStatus.SANITIZED, result.decision.status)
        assertTrue(
            result.decision.transformations.any { it.transformation == PrivacyTransformation.CROP }
        )
        // Content below the band shifted up into the top row.
        assertEquals(frame.pixels[10 * 100], result.safeFrame!!.pixels[0])
    }

    @Test
    fun `partial-width crop falls back to mask`() {
        val table = PrivacyRuleTable.custom(
            listOf(
                PrivacyRule(
                    PrivacyRegionType.SYSTEM_UI,
                    PrivacyTransformation.CROP,
                    priority = 1
                )
            )
        )
        val block = PrivacyRegion(
            type = PrivacyRegionType.SYSTEM_UI,
            bounds = ProtectedRegion(0.1, 0.1, 0.3, 0.3, "BLOCK"),
            signals = listOf(PrivacyDetectionSignal.FIXED_REGION)
        )
        val frame = textured()
        val result = PrivacyProcessor(PrivacyPolicy.RESEARCH, table).process(
            frame, listOf(block), OcrAvailability.OCR_AVAILABLE
        )
        assertSame(frame, result.safeFrame)
        assertEquals(
            PrivacyTransformation.MASK,
            result.decision.transformations.first().transformation
        )
    }

    @Test
    fun `injected clock drives the decision timestamp`() {
        var now = 5000L
        val processor = PrivacyProcessor(nowMs = { now })
        val result = processor.process(
            textured(), listOf(statusBar), OcrAvailability.OCR_AVAILABLE
        )
        assertEquals(5000L, result.decision.timestampMs)
        assertEquals("frame-5000", result.decision.frameId)
        now = 6000L
        val second = processor.process(
            textured(), emptyList(), OcrAvailability.OCR_AVAILABLE
        )
        assertEquals(6000L, second.decision.timestampMs)
    }

    @Test
    fun `decision requires regionsEnabled within detected count`() {
        val result = PrivacyProcessor().process(
            textured(), listOf(statusBar, inboxCard), OcrAvailability.OCR_AVAILABLE
        )
        assertEquals(2, result.decision.regionsDetected)
        assertEquals(2, result.decision.regionsEnabled)
    }
}