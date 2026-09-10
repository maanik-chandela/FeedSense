package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-10.
 *
 * Controlled privacy-sanitization status.
 *
 * The system NEVER claims perfect privacy. Automatic
 * anonymization is a risk-reduction mechanism that can
 * fail. These statuses describe exactly what happened so
 * every artifact can be interpreted honestly:
 *
 *   SANITIZED             - sensitive regions were detected
 *                             and transformed.
 *   PARTIALLY_SANITIZED   - some sensitive regions were
 *                             transformed; others could not
 *                             be handled confidently.
 *   SANITIZATION_FAILED   - sanitization errored; the frame
 *                             must NOT be treated as safe.
 *   SANITIZATION_UNAVAILABLE - the sanitizer could not run
 *                             (policy disabled, no context,
 *                             capture blocked).
 *   NOT_REQUIRED          - no sensitive content detected;
 *                             nothing needed sanitizing.
 *   UNKNOWN               - the outcome is not known.
 *
 * Pronunciation rule: the status is metadata about the
 * EVIDENCE, not a content category.
 */
enum class PrivacySanitizationStatus(val label: String) {

    SANITIZED("SANITIZED"),

    PARTIALLY_SANITIZED("PARTIALLY_SANITIZED"),

    SANITIZATION_FAILED("SANITIZATION_FAILED"),

    SANITIZATION_UNAVAILABLE("SANITIZATION_UNAVAILABLE"),

    NOT_REQUIRED("NOT_REQUIRED"),

    UNKNOWN("UNKNOWN");

    /*
     * Whether the artifact may be consumed as research
     * evidence downstream. Only frames whose status
     * explicitly confirms they were handled (or had nothing
     * to handle) are safe. FAILED/UNKNOWN are never safe.
     */
    val isSafeForResearchUse: Boolean
        get() = when (this) {
            SANITIZED -> true
            PARTIALLY_SANITIZED -> true
            SANITIZATION_FAILED -> false
            SANITIZATION_UNAVAILABLE -> false
            NOT_REQUIRED -> true
            UNKNOWN -> false
        }

    companion object {

        private val BY_LABEL: Map<String, PrivacySanitizationStatus> =
            entries.associateBy { it.label }

        fun fromLabel(label: String?): PrivacySanitizationStatus {
            return BY_LABEL[label] ?: UNKNOWN
        }

        /*
         * Maps the low-level 8B-4 rasterizer status to the
         * controlled vocabulary.
         */
        fun fromLegacy(
            status: SanitizationStatus
        ): PrivacySanitizationStatus {
            return when (status) {
                SanitizationStatus.SANITIZED -> SANITIZED
                SanitizationStatus.UNCHANGED -> NOT_REQUIRED
                SanitizationStatus.UNCERTAIN -> UNKNOWN
                SanitizationStatus.FAILED -> SANITIZATION_FAILED
            }
        }

        /*
         * Maps back to the legacy 8B-4 rasterizer status for
         * components that still consume it.
         */
        fun toLegacy(
            status: PrivacySanitizationStatus
        ): SanitizationStatus {
            return when (status) {
                SANITIZED -> SanitizationStatus.SANITIZED
                PARTIALLY_SANITIZED -> SanitizationStatus.UNCERTAIN
                SANITIZATION_FAILED -> SanitizationStatus.FAILED
                SANITIZATION_UNAVAILABLE -> SanitizationStatus.UNCERTAIN
                NOT_REQUIRED -> SanitizationStatus.UNCHANGED
                UNKNOWN -> SanitizationStatus.UNCERTAIN
            }
        }
    }
}