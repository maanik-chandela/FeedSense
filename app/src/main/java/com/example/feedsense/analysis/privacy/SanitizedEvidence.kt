package com.example.feedsense.analysis.privacy

import java.io.File

/*
 * Milestone 8B-10.
 *
 * An OCR line with its normalized bounding box. Lets the
 * sanitizer map a detected sensitive pattern to an on-screen
 * region when geometry is available.
 */
data class OcrTextSpan(
    val text: String,
    val bounds: ProtectedRegion
)

/*
 * Output of a sanitization pipeline run. Immutable.
 *
 * NOTE: the sanitized file is the ONLY artifact intended for
 * downstream analysis. rawFrame remains on device and is
 * never exported under the safe policy.
 */
data class SanitizedEvidence(
    val frameId: String? = null,
    val sanitizedFrameFile: File,
    val rawFrameFile: File,
    val redactedOcrText: String,
    val status: PrivacySanitizationStatus,
    val availability: EvidenceAvailability,
    val audit: SanitizationAudit,
    val detectedRegions: List<PrivacyRegion> = emptyList()
) {

    /*
     * Whether this sanitized evidence is safe to hand to the
     * analysis pipeline. Failed/unknown outcomes are not.
     */
    val isSafeForResearchUse: Boolean
        get() = status.isSafeForResearchUse
}