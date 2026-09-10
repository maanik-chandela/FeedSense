package com.example.feedsense.analysis.privacy

import com.example.feedsense.analysis.privacy.PrivacySanitizationStatus.PARTIALLY_SANITIZED
import com.example.feedsense.analysis.privacy.PrivacySanitizationStatus.SANITIZATION_FAILED
import com.example.feedsense.analysis.privacy.PrivacySanitizationStatus.SANITIZATION_UNAVAILABLE
import com.example.feedsense.analysis.privacy.PrivacySanitizationStatus.NOT_REQUIRED
import com.example.feedsense.analysis.privacy.PrivacySanitizationStatus.SANITIZED
import com.example.feedsense.analysis.privacy.PrivacySanitizationStatus.UNKNOWN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-10.
 *
 * Controlled sanitization status semantics.
 *
 * The system NEVER claims perfect privacy. These statuses
 * state exactly what happened so every artifact can be
 * interpreted honestly.
 */
class PrivacySanitizationStatusTest {

    @Test
    fun `six controlled statuses exist`() {
        assertEquals(
            6,
            PrivacySanitizationStatus.entries.size
        )
    }

    @Test
    fun `statuses round-trip through labels`() {
        PrivacySanitizationStatus.entries.forEach { status ->
            assertEquals(
                status,
                PrivacySanitizationStatus.fromLabel(status.label)
            )
        }
    }

    @Test
    fun `unknown label maps to UNKNOWN`() {
        assertEquals(
            UNKNOWN,
            PrivacySanitizationStatus.fromLabel("BOGUS")
        )
        assertEquals(
            UNKNOWN,
            PrivacySanitizationStatus.fromLabel(null)
        )
    }

    @Test
    fun `safe statuses are flagged for research use`() {
        assertTrue(SANITIZED.isSafeForResearchUse)
        assertTrue(PARTIALLY_SANITIZED.isSafeForResearchUse)
        assertTrue(NOT_REQUIRED.isSafeForResearchUse)
    }

    @Test
    fun `unsafe statuses are flagged explicitly`() {
        assertFalse(SANITIZATION_FAILED.isSafeForResearchUse)
        assertFalse(SANITIZATION_UNAVAILABLE.isSafeForResearchUse)
        assertFalse(UNKNOWN.isSafeForResearchUse)
    }

    @Test
    fun `legacy mapping preserves meaning`() {
        assertEquals(
            SANITIZED,
            PrivacySanitizationStatus.fromLegacy(
                SanitizationStatus.SANITIZED
            )
        )
        assertEquals(
            NOT_REQUIRED,
            PrivacySanitizationStatus.fromLegacy(
                SanitizationStatus.UNCHANGED
            )
        )
        assertEquals(
            SANITIZATION_FAILED,
            PrivacySanitizationStatus.fromLegacy(
                SanitizationStatus.FAILED
            )
        )
        assertEquals(
            UNKNOWN,
            PrivacySanitizationStatus.fromLegacy(
                SanitizationStatus.UNCERTAIN
            )
        )
    }

    @Test
    fun `round-trip through legacy keeps safe flag where representable`() {
        // The legacy 8B-4 vocabulary is exactly
        // {SANITIZED, UNCHANGED, UNCERTAIN, FAILED}; only the
        // four statuses it can represent round-trip losslessly.
        val legacyRepresentable = listOf(
            SANITIZED,
            NOT_REQUIRED,
            SANITIZATION_FAILED,
            UNKNOWN
        )
        legacyRepresentable.forEach { status ->
            val legacy = PrivacySanitizationStatus.toLegacy(status)
            val mapped = PrivacySanitizationStatus.fromLegacy(legacy)
            assertEquals(
                "mismatch for $status",
                status,
                mapped
            )
            assertTrue(
                "safe-flag mismatch for $status",
                status.isSafeForResearchUse ==
                    mapped.isSafeForResearchUse
            )
        }
    }

    @Test
    fun `legacy mapping is honest about lossy statuses`() {
        // PARTIALLY_SANITIZED and SANITIZATION_UNAVAILABLE
        // are NOT representable in the legacy vocabulary and
        // deliberately degrade to UNCERTAIN. The safe flag
        // therefore changes: legacy consumers never see a
        // false-safe artifact.
        val lossy = listOf(
            PARTIALLY_SANITIZED,
            SANITIZATION_UNAVAILABLE
        )
        lossy.forEach { status ->
            assertEquals(
                SanitizationStatus.UNCERTAIN,
                PrivacySanitizationStatus.toLegacy(status)
            )
            assertFalse(
                PrivacySanitizationStatus
                    .fromLegacy(
                        PrivacySanitizationStatus.toLegacy(status)
                    )
                    .isSafeForResearchUse
            )
        }
    }
}