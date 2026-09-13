package com.example.feedsense.analysis.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-10.
 *
 * Privacy policy behavior.
 *
 * Policies are conservative by default, versioned, and
 * deterministic. The policy NEVER claims the output is
 * fully anonymized.
 */
class PrivacyPolicyTest {

    @Test
    fun `default policy is research mode with version privacy-v1`() {
        val policy = PrivacyPolicy.RESEARCH
        assertEquals(PrivacySanitizationVersion.POLICY, policy.policyVersion)
        assertSame(PrivacyMode.RESEARCH_MODE, policy.mode)
    }

    @Test
    fun `default policy sanitizes all sensitive type classes`() {
        val policy = PrivacyPolicy.RESEARCH
        assertTrue(policy.sanitizeNotifications)
        assertTrue(policy.sanitizeSystemUi)
        assertTrue(policy.sanitizePersonalIdentifiers)
        assertTrue(policy.sanitizePrivateText)
        assertTrue(policy.sanitizePersonalImages)
        assertTrue(policy.sanitizeSensitiveAppUi)
        assertTrue(policy.sanitizeLocation)
    }

    @Test
    fun `default policy does NOT block capture on sanitization failure`() {
        // Capture continues even if sanitization fails.
        assertFalse(PrivacyPolicy.RESEARCH.blockOnSanitizationFailure)
    }

    @Test
    fun `default policy retains raw frames and disables raw export`() {
        val policy = PrivacyPolicy.RESEARCH
        assertTrue(policy.retainRawFrames)
        assertTrue(policy.retainSanitizedFrames)
        assertFalse(policy.allowRawResearchExport)
    }

    @Test
    fun `isSanitizationEnabled respects toggles`() {
        val disabled = PrivacyPolicy(
            sanitizeNotifications = false,
            sanitizeSystemUi = false,
            sanitizePersonalIdentifiers = false,
            sanitizePrivateText = false,
            sanitizePersonalImages = false,
            sanitizeSensitiveAppUi = false,
            sanitizeLocation = false
        )
        PrivacyRegionType.ALL.forEach { type ->
            assertFalse(disabled.isSanitizationEnabled(type))
        }

        val enabled = PrivacyPolicy.RESEARCH
        PrivacyRegionType.ALL.forEach { type ->
            assertTrue(enabled.isSanitizationEnabled(type))
        }
    }

    @Test
    fun `unknown sensitive regions handled when text sanitization active`() {
        val policy = PrivacyPolicy.RESEARCH
        assertTrue(
            policy.isSanitizationEnabled(
                PrivacyRegionType.UNKNOWN_SENSITIVE_REGION
            )
        )
    }

    @Test
    fun `policy is versioned and deterministic`() {
        val a = PrivacyPolicy.RESEARCH
        val b = PrivacyPolicy(policyVersion = "privacy-v1")
        assertEquals(a, b)
        assertEquals(
            a.isSanitizationEnabled(PrivacyRegionType.NOTIFICATION),
            b.isSanitizationEnabled(PrivacyRegionType.NOTIFICATION)
        )
    }

    @Test
    fun `validation rejects invalid blur strength`() {
        try {
            PrivacyPolicy(blurStrength = 101)
            throw AssertionError("should have thrown")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `limitations are honest`() {
        // The system is explicit that it does NOT guarantee
        // perfect anonymization.
        assertTrue(PrivacyPolicyLimitations.isLossy)
        assertTrue(PrivacyPolicyLimitations.adversarialContentPossible)
        assertTrue(PrivacyPolicyLimitations.ocrCoverageIsPartial)
        assertTrue(PrivacyPolicyLimitations.doesNotPreventLegalOscaptures)
    }

    @Test
    fun `production default is the safe research preset`() {
        assertEquals(
            PrivacyPolicy.RESEARCH,
            defaultPrivacyPolicy()
        )
    }
}