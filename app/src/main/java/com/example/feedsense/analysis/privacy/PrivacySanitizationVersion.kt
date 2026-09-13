package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-10.
 *
 * Versioned sanitization pipeline.
 *
 * Every sanitization artifact carries the exact versions of
 * the components that produced it, so reproducibility and
 * auditability are preserved:
 *
 *   - POLICY    : the privacy policy that drove the run.
 *   - SANITIZER : the rasterizer that applied transformations.
 *   - DETECTION : the sensitive-content detection logic.
 *   - REDACTION : the OCR text redactor.
 *
 * Rule: bump the relevant constant whenever the behavior of
 * that component changes. Two runs with identical inputs,
 * versions, and policy MUST produce identical sanitization
 * output (determinism requirement).
 */
object PrivacySanitizationVersion {

    const val POLICY = "privacy-v1"
    const val SANITIZER = "sanitizer-v1"
    const val DETECTION = "pattern-detection-v1"
    const val REDACTION = "text-redaction-v1"

    /*
     * 8B-13 additions: the deterministic in-memory privacy
     * processing pipeline (PrivacyProcessor + transformation
     * primitives) and its policy-rule table.
     */
    const val PROCESSING = "privacy-processing-v1"
    const val RULES = "privacy-rules-v1"
    const val COVERAGE = "privacy-coverage-v1"
    const val BENCHMARK = "privacy-benchmark-v1"
}

/*
 * Controlled flag schema for privacy-safe structured logs.
 *
 * Global logging rule:
 *   NEVER log raw OCR text.
 *   NEVER log screenshots or screenshot paths.
 *   NEVER log audit data containing raw content.
 *
 * Only SUMMARY metadata may be logged:
 *   frameId, status, region counts, versions, and booleans.
 */
enum class PrivacyLogField(val key: String) {
    FRAME_ID("frameId"),
    STATUS("status"),
    REGIONS_DETECTED("regionsDetected"),
    REGION_TYPES("regionTypes"),
    POLICY_VERSION("policyVersion"),
    SANITIZER_VERSION("sanitizerVersion"),
    REDACTION_VERSION("redactionVersion"),
    DETECTION_VERSION("detectionVersion"),
    TEXT_SEGMENTS_REDACTED("textSegmentsRedacted"),
    DECISION_AFFECTED("decisionAffected"),
    EVIDENCE_LOST("evidenceLost"),
    AVAILABILITY("availability")
}

/*
 * Privacy-safe structured logging payload. Constructed only
 * from an audit trail; never from raw content.
 */
data class PrivacyLogEntry(
    val timestampMs: Long,
    val fields: Map<String, String>
) {

    /*
     * A single-line, privacy-safe representation suitable for
     * Log.d / streettern. Guaranteed free of raw content.
     */
    fun toLogLine(): String {
        val body = fields.entries
            .joinToString(", ") { "${it.key}=${it.value}" }
        return "privacy_sanitization $timestampMs {$body}"
    }
}