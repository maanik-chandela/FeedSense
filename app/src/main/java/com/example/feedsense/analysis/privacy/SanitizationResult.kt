package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-4.
 *
 * Privacy sanitization result.
 *
 * Captures what happened during sanitization,
 * which regions were processed, and whether
 * the frame is safe for downstream consumption.
 *
 * Design principles:
 *   - Explicit states (never silently pretend success)
 *   - Fail-safe: FAILED/UNCERTAIN frames are not
 *     treated as sanitized
 *   - Auditable: metadata traces which rules were applied
 *   - No sensitive content in logs or result metadata
 */
data class SanitizationResult(
    val status: SanitizationStatus,
    val regionsProcessed: List<String>,
    val regionCount: Int,
    val sanitizationVersion: String,
    val transformedFramePath: String?,
    val errorMessage: String? = null,
    /*
     * Milestone 8B-10 additions. All defaulted so existing
     * call sites (and 8B-4 tests) remain source-compatible.
     */
    val policyVersion: String =
        PrivacySanitizationVersion.POLICY,
    val privacyStatus: PrivacySanitizationStatus =
        PrivacySanitizationStatus.fromLegacy(status),
    val audit: SanitizationAudit? = null
) {
    val isSafeForDownstream: Boolean
        get() = status == SanitizationStatus.SANITIZED ||
                status == SanitizationStatus.UNCHANGED

    val wasTransformed: Boolean
        get() = status == SanitizationStatus.SANITIZED
}

/*
 * Sanitization status states.
 *
 * SANITIZED: Known sensitive regions were successfully
 *   transformed. Frame is safe for downstream.
 *
 * UNCHANGED: No configured sensitive region was detected
 *   in the frame. No transformation applied. Frame is
 *   safe for downstream.
 *
 * UNCERTAIN: System cannot confidently determine whether
 *   sensitive information exists. Frame should be
 *   handled conservatively.
 *
 * FAILED: Sanitizer encountered an error. Frame must
 *   not be treated as sanitized.
 */
enum class SanitizationStatus {
    SANITIZED,
    UNCHANGED,
    UNCERTAIN,
    FAILED
}
