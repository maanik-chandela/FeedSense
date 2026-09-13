package com.example.feedsense.analysis.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-10.
 *
 * Sensitive pattern detection.
 *
 * Conservative: only explicit identifier forms are matched.
 * RESEARCH-relevant content (IPL, RCB, YouTube, Netflix,
 * sports scores, hashtags) is NEVER treated as sensitive.
 */
class SensitivePatternDetectorTest {

    private val detector = SensitivePatternDetector()

    @Test
    fun `detects email addresses`() {
        val text = "Contact john.doe@example.com for details"
        val matches = detector.findMatches(text)
        assertTrue(
            "expected an EMAIL_ADDRESS match in: $matches",
            matches.any {
                it.kind == SensitivePatternKind.EMAIL_ADDRESS
            }
        )
        val email = matches.first {
            it.kind == SensitivePatternKind.EMAIL_ADDRESS
        }
        assertEquals(
            "john.doe@example.com",
            text.substring(email.start, email.end)
        )
    }

    @Test
    fun `detects phone-number-like spans`() {
        val matches = detector.findMatches("Call +44 7911 123456 now")
        assertTrue(
            matches.any {
                it.kind == SensitivePatternKind.PHONE_NUMBER
            }
        )
    }

    @Test
    fun `detects payment-card-like spans`() {
        val matches = detector.findMatches(
            "Card 4111 1111 1111 1111 expires soon"
        )
        assertTrue(
            matches.any {
                it.kind == SensitivePatternKind.PAYMENT_CARD
            }
        )
    }

    @Test
    fun `does NOT flag research content`() {
        listOf(
            "IPL 2026 final score 224/4",
            "RCB vs CSK highlights",
            "YouTube reels and Netflix memes",
            "sports", "cricket", "standup comedy",
            "Meme Monday on Instagram"
        ).forEach { text ->
            val matches = detector.findMatches(text)
            assertTrue(
                "research text was flagged sensitive: $text",
                matches.all {
                    it.kind != SensitivePatternKind.EMAIL_ADDRESS &&
                        it.kind != SensitivePatternKind.PHONE_NUMBER &&
                        it.kind != SensitivePatternKind.PAYMENT_CARD &&
                        it.kind != SensitivePatternKind.ACCOUNT_ID
                }
            )
        }
    }

    @Test
    fun `year-only strings are NOT phone numbers`() {
        val matches = detector.findMatches("Highlights 2026")
        assertFalse(
            matches.any {
                it.kind == SensitivePatternKind.PHONE_NUMBER
            }
        )
    }

    @Test
    fun `matches are returned in deterministic order`() {
        val text = "reach out@example.com or +12025550101 today"
        val a = detector.findMatches(text)
        val b = detector.findMatches(text)
        assertEquals(a, b)
        // Sorted by start index.
        assertEquals(
            a.sortedBy { it.start },
            a
        )
    }

    @Test
    fun `firstMatchIndex returns -1 when no private pattern`() {
        assertEquals(-1, detector.firstMatchIndex("IPL memes"))
    }

    @Test
    fun `firstMatchIndex finds email`() {
        assertTrue(detector.firstMatchIndex("mail a@b.co") >= 0)
    }
}