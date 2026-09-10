package com.example.feedsense.analysis.ml.preprocess

import com.example.feedsense.analysis.privacy.PrivacyFrame
import com.example.feedsense.analysis.privacy.PrivacyPolicyMode
import com.example.feedsense.analysis.privacy.PrivacySanitizationStatus

// --------------------------------
// PREPROCESSING EVIDENCE BOUNDARY (8B-15-3)
// --------------------------------
//
// What enters preprocessing and nothing else.
//
//   RAW CAPTURE
//     -> PRIVACY PIPELINE (8B-10 / 8B-13)
//     -> APPROVED EVIDENCE (SanitizedEvidence / safe PrivacyFrame)
//     -> ML PREPROCESSING  (this milestone)
//
// The preprocessing layer NEVER accepts raw private evidence. It
// consumes the PRIVACY-APPROVED frame and PROPAGATES the privacy
// contract (: privacy sanitization version, policy mode,
// sanitization status, and a stable evidence source identity) into
// the preprocessed input. This preserves the fact that the
// evidence crossed the privacy boundary (8B-13).
//
// Privacy rules enforced here:
//   - no restore of masked pixels (none of this layer can see the
//     raw frame)
//   - no raw OCR text, no private coordinates
//   - no bypass of DROP_FRAME / FLAG_SECURE
//   - no second unsanitized model input
//   - if evidence is not approved for research/model use, the
//     pipeline rejects it EXPLICITLY -- never silently continues.
//
// The identity chain remains conceptually:
//   session -> evaluation item -> evidence -> sanitized evidence
//     -> preprocessed input -> future ML prediction
// carried by stable identifiers only. No raw content is stored.

/**
 * Immutable privacy boundary carrier.
 */
data class PreprocessingPrivacy(
    val sanitizationVersion: String = PrivacyBoundaryVersions.PROCESSING,
    val policyMode: PrivacyPolicyMode = PrivacyPolicyMode.RESEARCH,
    val sanitizationStatus: PrivacySanitizationStatus
) {
    /*
     * A frame is usable for ML preprocessing only when the privacy
     * status explicitly confirms it was handled (SANITIZED /
     * PARTIALLY_SANITIZED / NOT_REQUIRED). FAILED / UNKNOWN are
     * never accepted.
     */
    val isSafeForResearchUse: Boolean
        get() = sanitizationStatus.isSafeForResearchUse

    /*
     * Whether sanitization transformations were actually applied
     * (false for NOT_REQUIRED, where nothing needed handling).
     */
    val sanitized: Boolean
        get() = sanitizationStatus != PrivacySanitizationStatus.NOT_REQUIRED
}

/**
 * Version references for the privacy boundary (aligned with the
 * 8B-13 PrivacySanitizationVersion constants).
 */
object PrivacyBoundaryVersions {
    const val PROCESSING = "privacy-processing-v1"
    const val POLICY = "privacy-v1"
}

/**
 * The only accepted preprocessing input: a privacy-approved frame
 * plus the privacy contract that produced it.
 *
 * Construction fails fast when the evidence is not approved:
 * ML preprocessing must never silently continue on an unsanitized
 * or failed frame.
 */
data class PreprocessingEvidence(
    val frame: PrivacyFrame,
    val privacy: PreprocessingPrivacy,
    val evidenceId: String? = null,
    val sessionId: String? = null,
    val feedItemId: String? = null
) {

    init {
        require(frame.width > 0 && frame.height > 0) {
            "privacy frame dimensions must be positive"
        }
        require(frame.pixels.size == frame.width * frame.height) {
            "privacy frame pixel buffer must match dimensions"
        }
        require(privacy.isSafeForResearchUse) {
            "preprocessing rejects evidence with privacy status " +
                "${privacy.sanitizationStatus.label}: " +
                "not approved for research/model use"
        }
        require(privacy.sanitizationVersion.isNotBlank()) {
            "sanitization version must be non-blank"
        }
    }

    /*
     * Stable, content-free identity summary for log/meta attachment.
     * No pixels, no OCR, no private coordinates.
     */
    val identitySummary: String
        get() = listOfNotNull(
            evidenceId?.let { "evidence=$it" },
            sessionId?.let { "session=$it" },
            feedItemId?.let { "item=$it" }
        ).joinToString("/")
}

/**
 * Factory for constructing evidence from an already-approved
 * frame + explicit privacy metadata. This is the ONLY approved
 * path into preprocessing.
 */
object PreprocessingEvidenceFactory {

    /**
     * Builds a PreprocessingEvidence from a safe frame.
     *
     * @throws IllegalArgumentException when the privacy status is
     *   not safe for research use -- an explicit rejection, never
     *   silent continuation.
     */
    fun approve(
        frame: PrivacyFrame,
        sanitizationStatus: PrivacySanitizationStatus,
        sanitizationVersion: String = PrivacyBoundaryVersions.PROCESSING,
        policyMode: PrivacyPolicyMode = PrivacyPolicyMode.RESEARCH,
        evidenceId: String? = null,
        sessionId: String? = null,
        feedItemId: String? = null
    ): PreprocessingEvidence {
        return PreprocessingEvidence(
            frame = frame,
            privacy = PreprocessingPrivacy(
                sanitizationVersion = sanitizationVersion,
                policyMode = policyMode,
                sanitizationStatus = sanitizationStatus
            ),
            evidenceId = evidenceId,
            sessionId = sessionId,
            feedItemId = feedItemId
        )
    }
}