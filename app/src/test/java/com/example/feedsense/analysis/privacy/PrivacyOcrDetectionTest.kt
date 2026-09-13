package com.example.feedsense.analysis.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-13.
 *
 * OCR integration: patterns become typed regions; research
 * content is never flagged.
 */
class PrivacyOcrDetectionTest {

    private val subtitleBounds = ProtectedRegion(0.0, 0.7, 1.0, 0.2, "SUBTITLE")

    @Test
    fun `email is detected as a personal identifier`() {
        val regions = PrivacyOcrDetection.detectPrivateText(
            listOf(OcrLine("reach tech@example.com today", subtitleBounds))
        )
        assertEquals(1, regions.size)
        val region = regions.first()
        assertEquals(PrivacyRegionType.PERSONAL_IDENTIFIER, region.type)
        assertEquals(PrivacyDetectionSignal.OCR_TEXT_PATTERN, region.signals.first())
        assertEquals(subtitleBounds, region.bounds)
    }

    @Test
    fun `phone number is detected`() {
        val regions = PrivacyOcrDetection.detectPrivateText(
            listOf(OcrLine("call +1 (555) 123-4567", subtitleBounds))
        )
        assertEquals(1, regions.size)
    }

    @Test
    fun `subtitle containing one identifier yields one region`() {
        val regions = PrivacyOcrDetection.detectPrivateText(
            listOf(OcrLine("Contact: jane@mail.org - signed", subtitleBounds))
        )
        assertEquals(1, regions.size)
    }

    @Test
    fun `research-relevant content is never flagged`() {
        val clean = listOf(
            OcrLine("IPL 2026 live score", subtitleBounds),
            OcrLine("RCB innings", subtitleBounds),
            OcrLine("Netflix and YouTube recommendations", subtitleBounds)
        )
        assertEquals(0, PrivacyOcrDetection.detectPrivateText(clean).size)
    }

    @Test
    fun `plain urls are not detected unless enabled`() {
        val line = OcrLine("visit example.com for details", subtitleBounds)
        assertEquals(0, PrivacyOcrDetection.detectPrivateText(listOf(line)).size)
        assertEquals(1, PrivacyOcrDetection.detectPrivateText(listOf(line), includeUrls = true).size)
    }

    @Test
    fun `unbounded line falls back to whole-frame extent`() {
        val regions = PrivacyOcrDetection.detectPrivateText(
            listOf(OcrLine("support@acme.io"))
        )
        assertEquals(1, regions.size)
        val bounds = regions.first().bounds
        assertEquals(0.0, bounds.x, 0.0)
        assertEquals(0.0, bounds.y, 0.0)
        assertEquals(1.0, bounds.width, 0.0)
        assertEquals(1.0, bounds.height, 0.0)
    }

    @Test
    fun `detection is deterministic across runs`() {
        val lines = listOf(OcrLine("hi lee@x.com bye", subtitleBounds))
        val a = PrivacyOcrDetection.detectPrivateText(lines)
        val b = PrivacyOcrDetection.detectPrivateText(lines)
        assertEquals(a, b)
        assertTrue(a.isNotEmpty())
    }
}