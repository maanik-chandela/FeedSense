package com.example.feedsense.analysis.ml.repro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/*
 * Milestone 8B-15-2.
 *
 * PRIVACY identity: versioned sanitization contract. Metadata
 * only - never stores raw private content, OCR text, screenshots
 * or coordinates.
 */
class ReproPrivacyIdentityTest {

    @Test
    fun `same privacy produces equal identity`() {
        assertEquals(ReproFix.privacy, ReproFix.privacy.copy())
    }

    @Test
    fun `changed privacy version produces different identity`() {
        assertNotEquals(
            ReproFix.privacy,
            ReproFix.privacy.copy(privacySanitizationVersion = "privacy-v2")
        )
    }

    @Test
    fun `changed policy mode produces different identity`() {
        assertNotEquals(
            ReproFix.privacy,
            ReproFix.privacy.copy(policyMode = PolicyModeRef.STRICT)
        )
    }

    @Test
    fun `same privacy produces identical canonical serialization`() {
        assertEquals(
            ReproCanonicalSerializer.serializePrivacy(ReproFix.privacy),
            ReproCanonicalSerializer.serializePrivacy(ReproFix.privacy.copy())
        )
    }

    @Test
    fun `blank privacy version is rejected`() {
        try {
            ReproFix.privacy.copy(privacySanitizationVersion = "")
            throw AssertionError("blank privacy version must be rejected")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}