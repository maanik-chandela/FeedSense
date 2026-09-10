package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-10.
 *
 * Privacy-safe sanitization audit.
 *
 * An audit trail records WHAT sanitization decided and WHY,
 * WITHOUT ever storing the raw content that triggered it.
 *
 * Allowed in an audit:
 *   - versions and policy references
 *   - status
 *   - counts (regions, types, redacted segments)
 *   - frame id
 *   - booleans
 *
 * FORBIDDEN in an audit:
 *   - raw OCR text
 *   - raw screenshot data or screenshot paths
 *   - coordinates that could be paired with source content
 */
data class SanitizationAudit(
    val frameId: String? = null,
    val sanitizationVersion: String = PrivacySanitizationVersion.SANITIZER,
    val policyVersion: String = PrivacySanitizationVersion.POLICY,
    val status: PrivacySanitizationStatus = PrivacySanitizationStatus.UNKNOWN,
    val regionsDetected: Int = 0,
    val processedRegionCount: Int = 0,
    val regionTypeCounts: Map<PrivacyRegionType, Int> = emptyMap(),
    val redactedTextSegments: Int = 0,
    val redactionAffectedDecision: Boolean = false,
    val evidenceLostDueToSanitization: Boolean = false,
    val availability: EvidenceAvailability = EvidenceAvailability.NO_CONTENT_DETECTED,
    val processingTimestampMs: Long = 0L
) {

    /*
     * Counts of detected regions by type, stable ordering
     * for deterministic output.
     */
    val regionTypeLog: Map<String, Int>
        get() = regionTypeCounts.entries
            .sortedBy { it.key.label }
            .associate { it.key.label to it.value }

    /*
     * Privacy-safe structured log entry. Compose this from
     * an audit so raw content can never leak into logs.
     */
    fun toPrivacyLogEntry(): PrivacyLogEntry {
        val fields = linkedMapOf<String, String>().apply {
            frameId?.let { put(PrivacyLogField.FRAME_ID.key, it) }
            put(PrivacyLogField.STATUS.key, status.label)
            put(PrivacyLogField.REGIONS_DETECTED.key, regionsDetected.toString())
            put(PrivacyLogField.REGION_TYPES.key, regionTypeLog.entries.joinToString("|") { "${it.key}:${it.value}" })
            put(PrivacyLogField.POLICY_VERSION.key, policyVersion)
            put(PrivacyLogField.SANITIZER_VERSION.key, sanitizationVersion)
            put(PrivacyLogField.TEXT_SEGMENTS_REDACTED.key, redactedTextSegments.toString())
            put(PrivacyLogField.DECISION_AFFECTED.key, redactionAffectedDecision.toString())
            put(PrivacyLogField.EVIDENCE_LOST.key, evidenceLostDueToSanitization.toString())
            put(PrivacyLogField.AVAILABILITY.key, availability.label)
        }
        return PrivacyLogEntry(processingTimestampMs, fields)
    }

    companion object {
        val EMPTY = SanitizationAudit()
    }
}