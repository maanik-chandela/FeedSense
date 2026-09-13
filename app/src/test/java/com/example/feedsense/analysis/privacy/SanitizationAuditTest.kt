package com.example.feedsense.analysis.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-10.
 *
 * Sanitization audit trail.
 *
 * Audits record WHAT happened and WHY using ONLY counts,
 * statuses, and versions - never raw content.
 */
class SanitizationAuditTest {

    @Test
    fun `audit carries controlled metadata only`() {
        val audit = SanitizationAudit(
            frameId = "frame-1",
            sanitizationVersion =
                PrivacySanitizationVersion.SANITIZER,
            policyVersion = PrivacySanitizationVersion.POLICY,
            status = PrivacySanitizationStatus.SANITIZED,
            regionsDetected = 4,
            processedRegionCount = 4,
            regionTypeCounts = mapOf(
                PrivacyRegionType.SYSTEM_UI to 2,
                PrivacyRegionType.NOTIFICATION to 1,
                PrivacyRegionType.PRIVATE_TEXT to 1
            ),
            redactedTextSegments = 1,
            processingTimestampMs = 1234L
        )

        assertEquals(
            "sanitizer-v1",
            audit.sanitizationVersion
        )
        assertEquals(4, audit.regionsDetected)
        assertEquals(1, audit.redactedTextSegments)
        assertEquals(
            mapOf(
                "NOTIFICATION" to 1,
                "PRIVATE_TEXT" to 1,
                "SYSTEM_UI" to 2
            ),
            audit.regionTypeLog
        )
    }

    @Test
    fun `log entry contains only privacy-logged fields`() {
        val audit = SanitizationAudit(
            frameId = "frame-x",
            status = PrivacySanitizationStatus.PARTIALLY_SANITIZED,
            regionsDetected = 3,
            regionTypeCounts = mapOf(
                PrivacyRegionType.SYSTEM_UI to 3
            ),
            evidenceLostDueToSanitization = true,
            processingTimestampMs = 42L
        )

        val line = audit.toPrivacyLogEntry().toLogLine()

        assertTrue(line.contains("frameId=frame-x"))
        assertTrue(line.contains("status=PARTIALLY_SANITIZED"))
        assertTrue(line.contains("regionsDetected=3"))
        assertTrue(line.contains("SYSTEM_UI:3"))
        assertTrue(line.contains("policyVersion=privacy-v1"))
        assertTrue(line.contains("evidenceLost=true"))
    }

    @Test
    fun `EMPTY audit is a safe sentinel`() {
        val empty = SanitizationAudit.EMPTY
        assertEquals(0, empty.regionsDetected)
        assertEquals(
            PrivacySanitizationStatus.UNKNOWN,
            empty.status
        )
        assertTrue(empty.regionTypeCounts.isEmpty())
    }

    @Test
    fun `audit cannot be coerced into raw content`() {
        // Even a string dump of a fully-populated audit must
        // not contain the raw text it was built from.
        val audit = SanitizationAudit(
            status = PrivacySanitizationStatus.SANITIZED,
            regionsDetected = 5,
            redactedTextSegments = 3,
            availability = EvidenceAvailability.CONTENT_DETECTED
        )
        val dump = audit.toString() + audit.toPrivacyLogEntry()
        assertFalse(dump.contains("private-text"))
        assertFalse(dump.contains("@"))
        assertFalse(dump.contains("password"))
    }
}