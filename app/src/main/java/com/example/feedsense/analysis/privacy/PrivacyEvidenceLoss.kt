package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-10.
 *
 * Evidence loss caused by sanitization.
 *
 * One of the core decisions of the evidence-aware pipeline:
 * sanitization is a lossy transform, and a decision made
 * from sanitized evidence is strictly weaker than one made
 * from raw evidence. This metadata lets the 8B-6/7/8 layers
 * carry loss candidly instead of hiding it.
 */
enum class PrivacyEvidenceLoss(val label: String) {

    /* No evidence was lost; sanitization was not needed. */
    NONE("NONE"),

    /* Sanitization was applied; the decision may be weaker. */
    EVIDENCE_LOST_DUE_TO_SANITIZATION(
        "EVIDENCE_LOST_DUE_TO_SANITIZATION"
    ),

    /* OCR redaction removed one or more sensitive segments. */
    OCR_TEXT_REDACTED("OCR_TEXT_REDACTED"),

    /* Capture was blocked by the OS (FLAG_SECURE etc.). */
    CAPTURE_BLOCKED("CAPTURE_BLOCKED")
}

/*
 * Milestone 8B-10.
 *
 * Privacy metadata attached to evidence snapshots (8B-6).
 *
 * Attached as a defaulted field so all existing constructions
 * of ItemEvidenceSnapshot remain source-compatible.
 */
data class PrivacyEvidenceMetadata(
    val status: PrivacySanitizationStatus =
        PrivacySanitizationStatus.UNKNOWN,
    val availability: EvidenceAvailability =
        EvidenceAvailability.UNKNOWN_AVAILABILITY,
    val loss: PrivacyEvidenceLoss = PrivacyEvidenceLoss.NONE,
    val policyVersion: String = PrivacySanitizationVersion.POLICY,
    val sanitizerVersion: String = PrivacySanitizationVersion.SANITIZER,
    val regionTypesDetected: Set<PrivacyRegionType> = emptySet(),
    val redactedTextSegments: Int = 0,
    val decisionAffected: Boolean = false
) {

    /*
     * Whether the evidence underpinning this snapshot is
     * safe to use for a research decision.
     */
    val isSafeForResearchUse: Boolean =
        status.isSafeForResearchUse &&
            availability == EvidenceAvailability.CONTENT_DETECTED

    companion object {
        val NONE = PrivacyEvidenceMetadata()
    }
}