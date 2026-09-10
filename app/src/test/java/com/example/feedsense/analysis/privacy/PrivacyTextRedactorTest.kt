package com.example.feedsense.analysis.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-10.
 *
 * OCR text redactor.
 *
 * Replaces private identifier spans with [REDACTED].
 * Deterministic, versioned, and preserves research-relevant
 * content.
 */
class PrivacyTextRedactorTest {

    private val redactor = PrivacyTextRedactor()

    @Test
    fun `email is redacted`() {
        val result = redactor.redact(
            "Message from john.doe@example.com incoming"
        )
        assertEquals(1, result.segmentsRedacted)
        assertTrue(result.redacted.contains(REDACTION_TOKEN))
        assertFalse(result.redacted.contains("john.doe@example.com"))
        assertTrue(result.redacted.startsWith("Message from "))
        assertTrue(result.redacted.endsWith(" incoming"))
    }

    @Test
    fun `phone number is redacted`() {
        val result = redactor.redact("Call +12025550101 now")
        assertEquals(1, result.segmentsRedacted)
        assertFalse(result.redacted.contains("12025550101"))
    }

    @Test
    fun `research content passes through untouched`() {
        val content = "IPL 2026, RCB, YouTube, Netflix, #Meme"
        val result = redactor.redact(content)
        assertEquals(0, result.segmentsRedacted)
        assertEquals(content, result.redacted)
    }

    @Test
    fun `redaction is deterministic for same input and policy`() {
        val text = "Email a@b.co here and +12025550101 there"
        val a = redactor.redact(text)
        val b = redactor.redact(text)
        assertEquals(a, b)
        assertEquals(a.redacted, b.redacted)
        assertEquals(a.segmentsRedacted, b.segmentsRedacted)
    }

    @Test
    fun `redaction version is propagated`() {
        val result = redactor.redact("mail a@b.co")
        assertEquals(
            PrivacySanitizationVersion.REDACTION,
            result.redactionVersion
        )
    }

    @Test
    fun `disabled identifier policy returns text unchanged`() {
        val policy = PrivacyPolicy(
            sanitizePersonalIdentifiers = false
        )
        val result = redactor.redact(
            "mail a@b.co",
            policy
        )
        assertEquals("mail a@b.co", result.redacted)
        assertEquals(0, result.segmentsRedacted)
    }

    @Test
    fun `countSensitiveSegments is raw-content-free statistic`() {
        val count = redactor.countSensitiveSegments(
            "a@b.co and c@d.co here"
        )
        assertEquals(2, count)
    }

    @Test
    fun `redacted output never contains original identifier`() {
        val text = "Private pager +18005550123 or eve@hushmail.com"
        val result = redactor.redact(text)
        listOf("+18005550123", "eve@hushmail.com").forEach {
            assertFalse(result.redacted.contains(it))
        }
        assertNotEquals(text, result.redacted)
    }

    @Test
    fun `empty and whitespace input redacts to zero segments`() {
        assertEquals(
            0,
            redactor.redact("").segmentsRedacted
        )
        assertEquals(
            0,
            redactor.redact("   ").segmentsRedacted
        )
    }
}