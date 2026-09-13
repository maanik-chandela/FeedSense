package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-10.
 *
 * Export privacy policy.
 *
 * Controls what research artifacts MAY be written to export
 * files. Default mode is SANITIZED_METADATA_ONLY:
 *
 *   - metadata such as feed items, confidence, categories,
 *     timestamps: allowed (no raw screen content).
 *   - reference visibleText: NEVER exported raw.
 *   - sanitized frame paths: emitted only as relative
 *     references and only if the policy allows imagery.
 *
 * A raw-OCR export is possible ONLY under an explicit
 * DEBUG policy - never the default.
 */
enum class ExportPrivacyMode(val label: String) {

    /*
     * Default. Exports metadata only; raw OCR text and raw
     * frame references are omitted.
     */
    SANITIZED_METADATA_ONLY("SANITIZED_METADATA_ONLY"),

    /*
     * Metadata plus redacted OCR text ([REDACTED] token). No
     * raw identifiers.
     */
    METADATA_AND_REDACTED_TEXT("METADATA_AND_REDACTED_TEXT"),

    /*
     * Debug-only. Raw OCR text may be exported. NEVER the
     * default; requires an explicit opt-in.
     */
    DEBUG_RAW_TEXT("DEBUG_RAW_TEXT")
}

data class PrivacyExportPolicy(
    val mode: ExportPrivacyMode = ExportPrivacyMode.SANITIZED_METADATA_ONLY,
    val policyVersion: String = PrivacySanitizationVersion.POLICY
) {

    /*
     * Whether raw OCR text may be written to export files.
     */
    val exposesRawOcrText: Boolean
        get() = mode == ExportPrivacyMode.DEBUG_RAW_TEXT

    /*
     * Whether redacted OCR text may be written.
     */
    val exposesRedactedOcrText: Boolean
        get() = mode != ExportPrivacyMode.SANITIZED_METADATA_ONLY

    companion object {
        val SAFE_DEFAULT = PrivacyExportPolicy()
    }
}