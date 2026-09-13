package com.example.feedsense.analysis.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-13 (acceptance: false-positive tests).
 *
 * Content that is NOT a personal identifier must never be
 * detected as one, and ad/subtitle copy without an identifier
 * must never be destroyed.
 *
 * The detector is conservative BY DESIGN: only explicit
 * identifier forms (email / phone-like / card-like / acc-no /
 * acc#-prefixed account ids) are patterns; @-handles, public
 * creator usernames, years, times and research content are
 * never patterns. OCR-pattern matching is a best-effort signal,
 * never a claim of user attribution.
 */
class PrivacyFalsePositiveTest {

    private val detector = SensitivePatternDetector()

    private val subtitleBounds =
        ProtectedRegion(0.0, 0.7, 1.0, 0.2, "SUBTITLE")

    private fun hasPrivateMatch(text: String): Boolean {
        return detector.findMatches(text).any {
            it.kind != SensitivePatternKind.URL
        }
    }

    private fun assertNotSensitive(text: String) {
        assertTrue(
            "content was flagged sensitive: $text",
            !hasPrivateMatch(text)
        )
    }

    @Test
    fun `at-handles and public creator usernames are not identity`() {
        listOf(
            "@rcb_trends",
            "@the.sports.god",
            "@cricketfan_2027",
            "subscribe to @the.sports.god",
            "youtube.com/@highlights"
        ).forEach { text ->
            assertNotSensitive(text)
        }
    }

    @Test
    fun `movie subtitle without an identifier is not detected`() {
        val line = OcrLine(
            "He said it was always about the journey, not the destination.",
            subtitleBounds
        )
        assertEquals(0, PrivacyOcrDetection.detectPrivateText(listOf(line)).size)
    }

    @Test
    fun `subtitle times and years are not phone numbers`() {
        listOf(
            "We meet at 19:30 sharp",
            "Highlights air at 21:00",
            "Season 2 premieres in 2027"
        ).forEach { text ->
            assertNotSensitive(text)
        }
    }

    @Test
    fun `paid ads box copy without identifiers produces no region`() {
        val adLines = listOf(
            OcrLine("Big Sale - up to 50% off every item in store today!", subtitleBounds),
            OcrLine("Use code SAVE2027 at checkout", subtitleBounds),
            OcrLine("Offer ends soon. T and C apply.", subtitleBounds)
        )
        assertEquals(0, PrivacyOcrDetection.detectPrivateText(adLines).size)
    }

    @Test
    fun `email-form brand token only protects its own line, not the frame`() {
        // "kitchen@example.com" is a brand-style token. The
        // conservative detector still treats email FORM as an
        // identifier (best-effort), but the damage is bounded to
        // the single containing line: the sibling ad copy is not
        // flagged and the frame is never full-frame-masked or
        // dropped for it.
        val lines = listOf(
            OcrLine("Brand kitchen@example.com - now 20% off!", subtitleBounds),
            OcrLine("Free shipping on all orders", subtitleBounds)
        )
        val regions = PrivacyOcrDetection.detectPrivateText(lines)
        assertEquals(1, regions.size)
        assertEquals(subtitleBounds, regions.first().bounds)
    }
}