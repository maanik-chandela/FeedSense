package com.example.feedsense.analysis.ml.repro

// --------------------------------
// PRIVACY IDENTITY (8B-15-2)
// --------------------------------
//
// The privacy/sanitization contract that a future inference
// result depends on. Records VERSIONS only -- NEVER raw private
// content, OCR text, screenshots, or coordinates. This is
// metadata about the privacy boundary, not an evidence dump.
//
// Reuses the 8B-13 PrivacyPolicyMode vocabulary so the research
// contract aligns with the production privacy layer.

/*
 * The evidence source type the privacy boundary operates on.
 */
enum class EvidenceSourceType(override val label: String) : ReprLabeled {
    SAFE_FRAME("SAFE_FRAME"),
    OCR_TEXT("OCR_TEXT"),
    OTHER("OTHER"),
    UNKNOWN("UNKNOWN")
}

/*
 * Immutable privacy identity: identifies which privacy-processing
 * version and policy mode a result depended on.
 */
data class ReproPrivacyIdentity(
    val privacySanitizationVersion: String,
    val policyMode: PolicyModeRef,
    val evidenceSourceType: EvidenceSourceType = EvidenceSourceType.SAFE_FRAME
) {

    init {
        require(privacySanitizationVersion.isNotBlank()) {
            "privacySanitizationVersion must be non-blank"
        }
    }

    val key: String
        get() = "$privacySanitizationVersion:${policyMode.label}:${evidenceSourceType.label}"
}

/*
 * A stable reference to the FeedSense privacy policy mode,
 * avoiding a direct dependency on the 8B-13 runtime enum so this
 * reproducibility layer stays standalone and serializable.
 */
enum class PolicyModeRef(override val label: String) : ReprLabeled {
    RESEARCH("RESEARCH"),
    BALANCED("BALANCED"),
    STRICT("STRICT"),
    UNKNOWN("UNKNOWN")
}
