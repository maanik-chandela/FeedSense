package com.example.feedsense.analysis.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-10.
 *
 * Data policy: storage states, retention classes, consent
 * flags, and representative-frame resolution.
 */
class PrivacyDataPolicyTest {

    // --------------------------------------------------
    // STORAGE STATES
    // --------------------------------------------------

    @Test
    fun `storage states are explicit and controlled`() {
        assertEquals(4, FrameStorageState.entries.size)
        assertEquals("RAW", FrameStorageState.RAW.label)
        assertEquals(
            "SANITIZED_ONLY",
            FrameStorageState.SANITIZED_ONLY.label
        )
    }

    // --------------------------------------------------
    // RETENTION CLASSES
    // --------------------------------------------------

    @Test
    fun `retention classes cover the artifact types`() {
        assertEquals(5, DataRetentionClass.entries.size)
        assertTrue(
            DataRetentionClass.entries.containsAll(
                listOf(
                    DataRetentionClass.RAW_CAPTURE,
                    DataRetentionClass.SANITIZED_ANALYSIS,
                    DataRetentionClass.AGGREGATED_MODEL_DATA,
                    DataRetentionClass.USER_VISIBLE_LABEL_DATA,
                    DataRetentionClass.AUDIT_AND_METRICS
                )
            )
        )
    }

    @Test
    fun `retention settings distinguish finite and indefinite`() {
        val finite = RetentionPolicySettings(
            retentionClass = DataRetentionClass.RAW_CAPTURE,
            retainDays = 7L
        )
        val indefinite = RetentionPolicySettings(
            retentionClass = DataRetentionClass.AUDIT_AND_METRICS
        )
        assertFalse(finite.isRetainedIndefinitely)
        assertTrue(indefinite.isRetainedIndefinitely)
    }

    // --------------------------------------------------
    // CONSENT
    // --------------------------------------------------

    @Test
    fun `consent defaults are opt-out safety`() {
        val consent = ConsentFlags()
        assertFalse(consent.researchCaptureConsentGranted)
        assertFalse(consent.exportConsentGranted)
        assertFalse(consent.isResearchEligible)
    }

    @Test
    fun `consent is binary and versioned`() {
        val consent = ConsentFlags(
            researchCaptureConsentGranted = true
        )
        assertTrue(consent.isResearchEligible)
        assertEquals("consent-v1", consent.consentVersion)
    }

    // --------------------------------------------------
    // REPRESENTATIVE FRAME RESOLUTION
    // --------------------------------------------------

    @Test
    fun `analysis view prefers sanitized frame`() {
        val frame = RepresentativeFrame(
            frameId = "f1",
            rawFilePath = "/raw/f1.jpg",
            sanitizedFilePath = "/sanitized/f1.jpg",
            storageState = FrameStorageState.SANITIZED
        )
        assertEquals(
            "/sanitized/f1.jpg",
            frame.analysisViewPath()
        )
    }

    @Test
    fun `review view prefers raw (lossless) frame`() {
        val frame = RepresentativeFrame(
            frameId = "f1",
            rawFilePath = "/raw/f1.jpg",
            sanitizedFilePath = "/sanitized/f1.jpg",
            storageState = FrameStorageState.SANITIZED
        )
        assertEquals(
            "/raw/f1.jpg",
            frame.reviewViewPath()
        )
    }

    @Test
    fun `resolver falls back to raw only when raw is retained`() {
        val withRaw = RepresentativeFrameResolver.forAnalysis(
            rawPath = "/raw/f1.jpg",
            sanitizedPath = null
        )
        assertEquals(
            "/raw/f1.jpg",
            withRaw.analysisViewPath()
        )

        val noRaw = RepresentativeFrameResolver.forAnalysis(
            rawPath = "/raw/f1.jpg",
            sanitizedPath = null,
            policy = PrivacyPolicy(
                retainRawFrames = false
            )
        )
        assertEquals(
            FrameStorageState.SANITIZED_ONLY,
            noRaw.storageState
        )
        assertNull(noRaw.analysisViewPath())
    }
}