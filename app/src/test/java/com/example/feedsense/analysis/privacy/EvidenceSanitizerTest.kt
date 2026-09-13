package com.example.feedsense.analysis.privacy

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-10.
 *
 * EvidenceSanitizer integration tests.
 *
 * Uses an injected fake PrivacyRegionApplier so the
 * orchestrator is fully testable on the JVM without Android
 * Bitmap APIs.
 */
class EvidenceSanitizerTest {

    private class FakeApplier : PrivacyRegionApplier {

        var lastRegions: List<PrivacyRegion>? = null
        var outcome = ApplyOutcome(
            changed = true,
            truncated = false,
            regionCount = 0
        )
        var shouldThrow = false

        override fun apply(
            inputFile: File,
            regions: List<PrivacyRegion>,
            outputFile: File
        ): ApplyOutcome {
            lastRegions = regions
            if (shouldThrow) {
                throw RuntimeException("test applier crash")
            }
            if (outcome.changed) {
                outputFile.parentFile?.mkdirs()
                outputFile.writeText("sanitized-content")
            }
            return outcome
        }
    }

    private fun tempDir(name: String): File {
        return File(
            System.getProperty("java.io.tmpdir"),
            name
        ).apply { mkdirs() }
    }

    private fun createFrame(dir: File): File {
        return File(dir, "frame.jpg").apply {
            writeText("raw-frame")
        }
    }

    private fun sanitizer(
        policy: PrivacyPolicy = PrivacyPolicy.RESEARCH,
        applier: FakeApplier = FakeApplier()
    ): Pair<EvidenceSanitizer, FakeApplier> {
        return EvidenceSanitizer(
            policy = policy,
            regionApplier = applier
        ) to applier
    }

    // --------------------------------------------------
    // NO SENSITIVE CONTENT
    // --------------------------------------------------

    @Test
    fun `no sensitive content yields NOT_REQUIRED when nothing detected`() {
        val dir = tempDir("evsan_none")
        try {
            val frame = createFrame(dir)
            val policy = PrivacyPolicy(
                sanitizeSystemUi = false,
                sanitizeNotifications = false
            )
            val (svc, applier) = sanitizer(policy)
            val out = tempDir("evsan_none_out")

            val evidence = svc.sanitize(
                rawFrame = frame,
                ocrText = "IPL 2026 purely research",
                outputDir = out
            )

            assertEquals(
                PrivacySanitizationStatus.NOT_REQUIRED,
                evidence.status
            )
            assertEquals(
                EvidenceAvailability.NO_CONTENT_DETECTED,
                evidence.availability
            )
            // No regions were handed to the applier.
            assertTrue(
                applier.lastRegions.isNullOrEmpty()
            )
            assertTrue(evidence.isSafeForResearchUse)
            assertTrue(evidence.audit.evidenceLostDueToSanitization.not())
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------------------------
    // NOTIFICATION / SYSTEM UI DETECTED
    // --------------------------------------------------

    @Test
    fun `default policy sanitizes system UI and notification regions`() {
        val dir = tempDir("evsan_sysui")
        try {
            val frame = createFrame(dir)
            val (svc, applier) = sanitizer()
            val out = tempDir("evsan_sysui_out")

            val evidence = svc.sanitize(
                rawFrame = frame,
                ocrText = "OK",
                outputDir = out
            )

            assertEquals(
                PrivacySanitizationStatus.SANITIZED,
                evidence.status
            )
            assertEquals(
                EvidenceAvailability.CONTENT_DETECTED,
                evidence.availability
            )
            assertTrue(
                evidence.audit.regionTypeCounts.keys.contains(
                    PrivacyRegionType.NOTIFICATION
                )
            )
            assertTrue(
                evidence.audit.regionTypeCounts.keys.contains(
                    PrivacyRegionType.SYSTEM_UI
                )
            )
            assertEquals(
                3,
                applier.lastRegions?.size
            )
            assertTrue(evidence.sanitizedFrameFile.exists())
            assertTrue(evidence.isSafeForResearchUse)
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------------------------
    // PERSONAL IDENTIFIER REDACTION
    // --------------------------------------------------

    @Test
    fun `personal identifier in OCR is redacted and reported`() {
        val dir = tempDir("evsan_email")
        try {
            val frame = createFrame(dir)
            val (svc, _) = sanitizer()
            val out = tempDir("evsan_email_out")

            val evidence = svc.sanitize(
                rawFrame = frame,
                ocrText = "Reach out@example.com today",
                outputDir = out
            )

            assertEquals(
                PrivacySanitizationStatus.SANITIZED,
                evidence.status
            )
            assertTrue(evidence.redactedOcrText.contains(REDACTION_TOKEN))
            assertFalse(evidence.redactedOcrText.contains("out@example.com"))
            assertEquals(1, evidence.audit.redactedTextSegments)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `phone number in OCR is redacted`() {
        val dir = tempDir("evsan_phone")
        try {
            val frame = createFrame(dir)
            val (svc, _) = sanitizer()
            val out = tempDir("evsan_phone_out")

            val evidence = svc.sanitize(
                rawFrame = frame,
                ocrText = "Call +12025550101 now",
                outputDir = out
            )

            assertFalse(evidence.redactedOcrText.contains("12025550101"))
            assertTrue(evidence.redactedOcrText.contains(REDACTION_TOKEN))
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------------------------
    // PRIVATE TEXT REGION FROM OCR SPAN
    // --------------------------------------------------

    @Test
    fun `OCR span with private pattern adds PRIVATE_TEXT region`() {
        val dir = tempDir("evsan_span")
        try {
            val frame = createFrame(dir)
            val (svc, applier) = sanitizer()
            val out = tempDir("evsan_span_out")

            val span = OcrTextSpan(
                text = "mail private@example.com",
                bounds = ProtectedRegion(
                    x = 0.1, y = 0.3,
                    width = 0.5, height = 0.1,
                    label = "PRIVATE_TEXT"
                )
            )

            val evidence = svc.sanitize(
                rawFrame = frame,
                ocrText = null,
                ocrSpans = listOf(span),
                outputDir = out
            )

            assertTrue(
                applier.lastRegions.orEmpty().any {
                    it.type == PrivacyRegionType.PRIVATE_TEXT
                }
            )
            assertEquals(
                PrivacySanitizationStatus.SANITIZED,
                evidence.status
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------------------------
    // SENSITIVE APPLICATION UI / PERSONAL IMAGE / UNKNOWN
    // --------------------------------------------------

    @Test
    fun `injected sensitive application region is processed`() {
        val dir = tempDir("evsan_app")
        try {
            val frame = createFrame(dir)
            val (svc, applier) = sanitizer()
            val out = tempDir("evsan_app_out")

            val appRegion = PrivacyRegion(
                type = PrivacyRegionType.SENSITIVE_APPLICATION_UI,
                bounds = ProtectedRegion(
                    x = 0.2, y = 0.4,
                    width = 0.6, height = 0.3,
                    label = "SENSITIVE_APPLICATION_UI"
                ),
                signals = listOf(
                    PrivacyDetectionSignal.SENSITIVE_APP_STRUCTURE
                )
            )

            val evidence = svc.sanitize(
                rawFrame = frame,
                ocrText = null,
                outputDir = out,
                additionalRegions = listOf(appRegion)
            )

            assertTrue(
                applier.lastRegions.orEmpty().any {
                    it.type == PrivacyRegionType.SENSITIVE_APPLICATION_UI
                }
            )
            assertEquals(
                1,
                evidence.audit.regionTypeCounts[
                    PrivacyRegionType.SENSITIVE_APPLICATION_UI
                ]
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `injected personal image region is processed`() {
        val dir = tempDir("evsan_img")
        try {
            val frame = createFrame(dir)
            val (svc, applier) = sanitizer()
            val out = tempDir("evsan_img_out")

            val region = PrivacyRegion(
                type = PrivacyRegionType.PERSONAL_IMAGE,
                bounds = ProtectedRegion(
                    x = 0.1, y = 0.1,
                    width = 0.3, height = 0.3,
                    label = "PERSONAL_IMAGE"
                )
            )
            svc.sanitize(
                rawFrame = frame,
                ocrText = null,
                outputDir = out,
                additionalRegions = listOf(region)
            )
            assertTrue(
                applier.lastRegions.orEmpty().any {
                    it.type == PrivacyRegionType.PERSONAL_IMAGE
                }
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------------------------
    // UNKNOWN SENSITIVE REGION (conservative)
    // --------------------------------------------------

    @Test
    fun `unknown sensitive region handled conservatively`() {
        val dir = tempDir("evsan_unknown")
        try {
            val frame = createFrame(dir)
            val (svc, applier) = sanitizer()
            val out = tempDir("evsan_unknown_out")

            val region = PrivacyRegion(
                type = PrivacyRegionType.UNKNOWN_SENSITIVE_REGION,
                bounds = ProtectedRegion(
                    x = 0.0, y = 0.5,
                    width = 1.0, height = 0.2,
                    label = "UNKNOWN_SENSITIVE_REGION"
                ),
                confidence = 0.3,
                signals = listOf(
                    PrivacyDetectionSignal.LOW_CONFIDENCE_FALLBACK
                )
            )
            svc.sanitize(
                rawFrame = frame,
                ocrText = null,
                outputDir = out,
                additionalRegions = listOf(region)
            )
            assertTrue(
                applier.lastRegions.orEmpty().any {
                    it.type == PrivacyRegionType.UNKNOWN_SENSITIVE_REGION
                }
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------------------------
    // PARTIAL SANITIZATION
    // --------------------------------------------------

    @Test
    fun `policy-disabled region yields PARTIALLY_SANITIZED`() {
        val dir = tempDir("evsan_partial")
        try {
            val frame = createFrame(dir)
            val policy = PrivacyPolicy(
                sanitizeNotifications = false
            )
            val (svc, _) = sanitizer(policy)
            val out = tempDir("evsan_partial_out")

            val evidence = svc.sanitize(
                rawFrame = frame,
                ocrText = null,
                outputDir = out
            )

            assertEquals(
                PrivacySanitizationStatus.PARTIALLY_SANITIZED,
                evidence.status
            )
            assertTrue(evidence.isSafeForResearchUse)
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------------------------
    // CAPTURE BLOCKED / FLAG-SECURE-LIKE
    // --------------------------------------------------

    @Test
    fun `truncated output maps to CAPTURE_BLOCKED availability`() {
        val dir = tempDir("evsan_blocked")
        try {
            val frame = createFrame(dir)
            val (svc, applier) = sanitizer()
            applier.outcome = ApplyOutcome(
                changed = true,
                truncated = true,
                regionCount = 0
            )
            val out = tempDir("evsan_blocked_out")

            val evidence = svc.sanitize(
                rawFrame = frame,
                ocrText = null,
                outputDir = out
            )

            assertEquals(
                EvidenceAvailability.CAPTURE_BLOCKED,
                evidence.availability
            )
            assertEquals(
                PrivacySanitizationStatus.SANITIZED,
                evidence.status
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------------------------
    // FAILURES ARE EXPLICIT, NOT THROWN
    // --------------------------------------------------

    @Test
    fun `missing raw frame yields SANITIZATION_FAILED without throwing`() {
        val dir = tempDir("evsan_missing")
        try {
            val missing = File(dir, "missing.jpg")
            val (svc, _) = sanitizer()
            val out = tempDir("evsan_missing_out")

            val evidence = svc.sanitize(
                rawFrame = missing,
                ocrText = null,
                outputDir = out
            )

            assertEquals(
                PrivacySanitizationStatus.SANITIZATION_FAILED,
                evidence.status
            )
            assertFalse(evidence.isSafeForResearchUse)
            assertEquals(
                EvidenceAvailability.UNKNOWN_AVAILABILITY,
                evidence.availability
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `applier crash yields SANITIZATION_FAILED without throwing`() {
        val dir = tempDir("evsan_crash")
        try {
            val frame = createFrame(dir)
            val (svc, applier) = sanitizer()
            applier.shouldThrow = true
            val out = tempDir("evsan_crash_out")

            val evidence = svc.sanitize(
                rawFrame = frame,
                ocrText = null,
                outputDir = out
            )

            assertEquals(
                PrivacySanitizationStatus.SANITIZATION_FAILED,
                evidence.status
            )
            assertFalse(evidence.isSafeForResearchUse)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `successful-outcome with no output file yields failure`() {
        val dir = tempDir("evsan_nofile")
        try {
            val frame = createFrame(dir)

            val lyingApplier = PrivacyRegionApplier { _, _, _ ->
                // Reports success but never writes the file.
                ApplyOutcome(
                    changed = true,
                    truncated = false,
                    regionCount = 1
                )
            }

            val svc = EvidenceSanitizer(
                policy = PrivacyPolicy.RESEARCH,
                regionApplier = lyingApplier
            )
            val out = tempDir("evsan_nofile_out")

            val evidence = svc.sanitize(
                rawFrame = frame,
                ocrText = null,
                outputDir = out
            )
            assertEquals(
                PrivacySanitizationStatus.SANITIZATION_FAILED,
                evidence.status
            )
            assertFalse(evidence.isSafeForResearchUse)
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------------------------
    // VERSIONING & DETERMINISM
    // --------------------------------------------------

    @Test
    fun `audit carries versioned sanitizer and policy`() {
        val dir = tempDir("evsan_version")
        try {
            val frame = createFrame(dir)
            val (svc, _) = sanitizer()
            val out = tempDir("evsan_version_out")

            val evidence = svc.sanitize(
                rawFrame = frame,
                ocrText = "hello",
                outputDir = out
            )

            assertEquals(
                PrivacySanitizationVersion.SANITIZER,
                evidence.audit.sanitizationVersion
            )
            assertEquals(
                PrivacySanitizationVersion.POLICY,
                evidence.audit.policyVersion
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `deterministic for identical inputs`() {
        val dir = tempDir("evsan_determ")
        try {
            val frame = createFrame(dir)

            fun run(dirName: String): SanitizedEvidence {
                val (svc, _) = sanitizer()
                val out = tempDir(dirName)
                return svc.sanitize(
                    rawFrame = frame,
                    ocrText = "mail a@b.co now",
                    outputDir = out
                )
            }

            val a = run("evsan_determ_a")
            val b = run("evsan_determ_b")

            assertEquals(a.status, b.status)
            assertEquals(a.audit.regionsDetected, b.audit.regionsDetected)
            assertEquals(a.audit.regionTypeCounts, b.audit.regionTypeCounts)
            assertEquals(a.redactedOcrText, b.redactedOcrText)
            assertEquals(a.audit.redactedTextSegments, b.audit.redactedTextSegments)
            assertEquals(a.detectedRegions, b.detectedRegions)
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------------------------
    // USEFUL CONTENT PRESERVED
    // --------------------------------------------------

    @Test
    fun `useful OCR content is preserved alongside redaction`() {
        val dir = tempDir("evsan_preserve")
        try {
            val frame = createFrame(dir)
            val (svc, _) = sanitizer()
            val out = tempDir("evsan_preserve_out")

            val evidence = svc.sanitize(
                rawFrame = frame,
                ocrText =
                    "IPL 2026 highlights · subscribe@x.com",
                outputDir = out
            )

            assertTrue(evidence.redactedOcrText.contains("IPL 2026"))
            assertTrue(evidence.redactedOcrText.contains("highlights"))
            assertFalse(
                evidence.redactedOcrText.contains("subscribe@x.com")
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------------------------
    // NO RAW CONTENT IN AUDIT
    // --------------------------------------------------

    @Test
    fun `audit never contains raw OCR content`() {
        val dir = tempDir("evsan_audit")
        try {
            val frame = createFrame(dir)
            val (svc, _) = sanitizer()
            val out = tempDir("evsan_audit_out")

            val evidence = svc.sanitize(
                rawFrame = frame,
                ocrText = "secr3t-password admin@corp.example.net",
                outputDir = out
            )

            val auditString = evidence.audit.toString() +
                evidence.audit.toPrivacyLogEntry().toString()
            assertFalse(auditString.contains("secr3t-password"))
            assertFalse(auditString.contains("admin@corp.example.net"))
        } finally {
            dir.deleteRecursively()
        }
    }

    // --------------------------------------------------
    // EVIDENCE LOSS
    // --------------------------------------------------

    @Test
    fun `evidence loss is honest for sanitized frames`() {
        val dir = tempDir("evsan_loss")
        try {
            val frame = createFrame(dir)
            val (svc, _) = sanitizer()
            val out = tempDir("evsan_loss_out")

            val evidence = svc.sanitize(
                rawFrame = frame,
                ocrText = "some content",
                outputDir = out
            )
            assertEquals(
                PrivacySanitizationStatus.SANITIZED,
                evidence.status
            )
            // Sanitization is lossy by design; the flag must
            // be honest rather than pretend no evidence was
            // affected.
            assertTrue(
                evidence.audit.evidenceLostDueToSanitization
            )
        } finally {
            dir.deleteRecursively()
        }
    }
}