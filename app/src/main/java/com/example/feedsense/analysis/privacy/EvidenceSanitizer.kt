package com.example.feedsense.analysis.privacy

import java.io.File

/*
 * Outcome of applying geometric transformations to a frame.
 */
data class ApplyOutcome(
    val changed: Boolean,
    val truncated: Boolean,
    val regionCount: Int
)

/*
 * Milestone 8B-10.
 *
 * Applies a set of privacy regions to a frame file and
 * writes the result. Implementations are platform-specific:
 * the default Android implementation reuses the 8B-4
 * rasterizer (PrivacySanitizer); tests inject a fake here.
 *
 * Implementations MUST be deterministic for a given input
 * frame, region list, and sanitizer version.
 */
fun interface PrivacyRegionApplier {

    fun apply(
        inputFile: File,
        regions: List<PrivacyRegion>,
        outputFile: File
    ): ApplyOutcome
}

/*
 * Milestone 8B-10.
 *
 * Orchestrates evidence sanitization.
 *
 * Pipeline (all deterministic):
 *   1. DETECT   - fixed system regions + OCR pattern regions.
 *   2. FILTER   - drop regions the policy disables.
 *   3. RASTERIZE- transform geometry via PrivacyRegionApplier.
 *   4. REDACT   - redact private patterns in OCR text.
 *   5. AUDIT    - record counts, status, versions only.
 *
 * Failure is never thrown to the caller; a SANITIZED_FAILED
 * outcome is returned so capture can continue (policy may
 * then choose to block downstream use).
 *
 * The sanitizer does NOT claim perfect privacy and never
 * deletes raw frames by policy (retention is a separate
 * stage; see PrivacyDataPolicy).
 */
class EvidenceSanitizer(
    private val policy: PrivacyPolicy = defaultPrivacyPolicy(),
    private val redactor: PrivacyTextRedactor = PrivacyTextRedactor(),
    private val detector: SensitivePatternDetector = SensitivePatternDetector(),
    private val regionApplier: PrivacyRegionApplier
) {

    fun sanitize(
        rawFrame: File,
        ocrText: String?,
        ocrSpans: List<OcrTextSpan> = emptyList(),
        frameId: String? = null,
        outputDir: File,
        /*
         * Extra typed regions injected by detectors. In
         * production this stays empty (the worker relies on
         * fixed regions + OCR patterns for now); detectors
         * that run later can supply typed regions here.
         */
        additionalRegions: List<PrivacyRegion> = emptyList()
    ): SanitizedEvidence {

        if (!rawFrame.exists()) {
            return failure(
                frameId = frameId,
                reason = "raw frame file does not exist"
            )
        }

        return try {
            runSanitize(
                rawFrame,
                ocrText,
                ocrSpans,
                frameId,
                outputDir,
                additionalRegions
            )
        } catch (exception: Exception) {
            failure(
                frameId = frameId,
                reason = "sanitization pipeline crashed: ${exception::class.simpleName}"
            )
        }
    }

    private fun runSanitize(
        rawFrame: File,
        ocrText: String?,
        ocrSpans: List<OcrTextSpan>,
        frameId: String?,
        outputDir: File,
        additionalRegions: List<PrivacyRegion>
    ): SanitizedEvidence {

        val timestamp = System.currentTimeMillis()

        val detected = detectRegions(
            ocrText,
            ocrSpans,
            additionalRegions
        )
        val enabled = detected.filter { region ->
            policy.isSanitizationEnabled(region.type)
        }

        // Redact OCR text AFTER detection so region detection
        // can see the raw text; only positions are recorded.
        val redaction = redactor.redact(ocrText ?: "", policy)

        if (enabled.isEmpty()) {
            val availability =
                if (detected.isEmpty()) {
                    EvidenceAvailability.NO_CONTENT_DETECTED
                } else {
                    EvidenceAvailability.CONTENT_DETECTED
                }
            val audit = SanitizationAudit(
                frameId = frameId,
                status = PrivacySanitizationStatus.NOT_REQUIRED,
                regionsDetected = detected.size,
                processedRegionCount = 0,
                regionTypeCounts = typeCounts(detected),
                redactedTextSegments = redaction.segmentsRedacted,
                availability = availability,
                processingTimestampMs = timestamp
            )
            return SanitizedEvidence(
                frameId = frameId,
                sanitizedFrameFile = rawFrame,
                rawFrameFile = rawFrame,
                redactedOcrText = redaction.redacted,
                status = PrivacySanitizationStatus.NOT_REQUIRED,
                availability = availability,
                audit = audit,
                detectedRegions = detected
            )
        }

        outputDir.mkdirs()
        val outputFile = File(outputDir, sanitizedFileName(rawFrame, frameId))

        val outcome = regionApplier.apply(
            rawFrame,
            enabled,
            outputFile
        )

        // Honesty check: a "changed" outcome with no output
        // file is a failure, not a success.
        if (outcome.changed && !outputFile.exists()) {
            return failure(
                frameId = frameId,
                reason = "applier reported success but wrote no output"
            )
        }

        val availability =
            if (outcome.truncated) {
                EvidenceAvailability.CAPTURE_BLOCKED
            } else if (outcome.changed) {
                EvidenceAvailability.CONTENT_DETECTED
            } else {
                EvidenceAvailability.NO_CONTENT_DETECTED
            }

        val partiallyHandled =
            detected.size != enabled.size

        val status =
            if (partiallyHandled) {
                PrivacySanitizationStatus.PARTIALLY_SANITIZED
            } else {
                PrivacySanitizationStatus.SANITIZED
            }

        val evidenceLost = status == PrivacySanitizationStatus.SANITIZED

        val audit = SanitizationAudit(
            frameId = frameId,
            status = status,
            regionsDetected = detected.size,
            processedRegionCount = enabled.size,
            regionTypeCounts = typeCounts(detected),
            redactedTextSegments = redaction.segmentsRedacted,
            redactionAffectedDecision = redaction.segmentsRedacted > 0,
            evidenceLostDueToSanitization = evidenceLost,
            availability = availability,
            processingTimestampMs = timestamp
        )

        return SanitizedEvidence(
            frameId = frameId,
            sanitizedFrameFile = outputFile,
            rawFrameFile = rawFrame,
            redactedOcrText = redaction.redacted,
            status = status,
            availability = availability,
            audit = audit,
            detectedRegions = detected
        )
    }

    /*
     * Detection stage. Deterministic ordering invariant:
     * regions are sorted by type label then geometry before
     * being handed to the rasterizer.
     */
    private fun detectRegions(
        ocrText: String?,
        ocrSpans: List<OcrTextSpan>,
        additionalRegions: List<PrivacyRegion>
    ): List<PrivacyRegion> {

        val regions = mutableListOf<PrivacyRegion>()
        regions += additionalRegions

        if (policy.sanitizeSystemUi) {
            regions += PRIVACY_SYSTEM_UI_REGIONS
        }

        if (policy.sanitizeNotifications) {
            regions += PRIVACY_SYSTEM_UI_REGIONS.filter {
                it.type == PrivacyRegionType.NOTIFICATION
            }
        }

        // OCR pattern regions: any span whose text holds a
        // sensitive pattern maps to a PRIVATE_TEXT region.
        ocrSpans.forEach { span ->
            val hasPrivatePattern = detector.findMatches(span.text).any {
                !it.kind.isExcludedFromRegions
            }
            if (hasPrivatePattern) {
                regions += PrivacyRegion(
                    type = PrivacyRegionType.PRIVATE_TEXT,
                    bounds = span.bounds,
                    signals = listOf(
                        PrivacyDetectionSignal.OCR_TEXT_PATTERN
                    ),
                    confidence = 1.0
                )
            }
        }

        // A private pattern in OCR without geometry still
        // guarantees the redactor stage covers it. The
        // region-based pipeline only needs the spans it was
        // given plus the fixed UI regions above.
        return regions
            .distinctBy { region ->
                listOf(
                    region.type,
                    region.bounds.x,
                    region.bounds.y,
                    region.bounds.width,
                    region.bounds.height
                )
            }
            .sortedWith(
                compareBy<PrivacyRegion> { it.type.label }
                    .thenBy { it.bounds.y }
                    .thenBy { it.bounds.x }
            )
    }

    private fun typeCounts(
        regions: List<PrivacyRegion>
    ): Map<PrivacyRegionType, Int> {
        return regions.groupingBy { it.type }.eachCount()
    }

    private fun sanitizedFileName(
        rawFrame: File,
        frameId: String?
    ): String {
        val stem = frameId
            ?: rawFrame.nameWithoutExtension
        return "sanitized_${stem}.jpg"
    }

    private fun failure(
        frameId: String?,
        reason: String
    ): SanitizedEvidence {
        val timestamp = System.currentTimeMillis()
        val audit = SanitizationAudit(
            frameId = frameId,
            status = PrivacySanitizationStatus.SANITIZATION_FAILED,
            availability = EvidenceAvailability.UNKNOWN_AVAILABILITY,
            processingTimestampMs = timestamp
        )
        return SanitizedEvidence(
            frameId = frameId,
            sanitizedFrameFile = File(""),
            rawFrameFile = File(""),
            redactedOcrText = "",
            status = PrivacySanitizationStatus.SANITIZATION_FAILED,
            availability = EvidenceAvailability.UNKNOWN_AVAILABILITY,
            audit = audit,
            detectedRegions = emptyList()
        )
    }
}

private val SensitivePatternKind.isExcludedFromRegions
    get() = this == SensitivePatternKind.URL