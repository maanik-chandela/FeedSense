package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-13.
 *
 * A single applied transformation, kept as a SAFE SUMMARY only
 * (spec §21): region type + transform + signal + confidence.
 *
 * FORBIDDEN here:
 *   - pixel bounds / coordinates (could be paired with source
 *     content, per SanitizationAudit rules),
 *   - any extracted text/bytes.
 */
data class AppliedTransformation(
    val regionType: PrivacyRegionType,
    val transformation: PrivacyTransformation,
    val signal: PrivacyDetectionSignal,
    val confidence: PrivacyConfidence
)

/*
 * Milestone 8B-13.
 *
 * Deterministic per-frame privacy-processing decision (spec
 * §14, §19-§22).
 *
 * Every field is derived only from the frame, the detected
 * regions, the policy, the rule table, OCR availability, and
 * the injected clock - so the same inputs always give the same
 * decision and the same safe metadata.
 */
data class PrivacyDecision(
    val frameId: String,
    val timestampMs: Long,
    val policyVersion: String,
    val processingVersion: String,
    val rulesVersion: String,
    val ocrAvailability: OcrAvailability,
    val regionsDetected: Int,
    val regionsEnabled: Int,
    val risk: PrivacyRisk,
    val confidence: PrivacyConfidence,
    val status: PrivacySanitizationStatus,
    val transformations: List<AppliedTransformation>,
    val dropped: Boolean,
    val evidenceLoss: PrivacyEvidenceLoss
) {

    init {
        require(regionsEnabled in 0..regionsDetected) {
            "regionsEnabled must be within [0, regionsDetected]"
        }
    }

    /*
     * Whether the frame may be consumed as research evidence.
     * An UNKNOWN risk (OCR unavailable, nothing to detect) is
     * deliberately NOT safe: familiarity with the content could
     * not be confirmed (spec §27).
     */
    val isSafeForResearchUse: Boolean
        get() = status.isSafeForResearchUse &&
            !dropped &&
            risk != PrivacyRisk.UNKNOWN
}