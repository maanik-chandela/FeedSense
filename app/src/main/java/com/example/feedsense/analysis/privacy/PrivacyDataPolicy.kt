package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-10.
 *
 * Frame storage states.
 *
 * Distinguishes where evidence lives so retention can be
 * reasoned about explicitly:
 *
 *   RAW              - original capture, unmodified.
 *   SANITIZED        - sanitized frame, analysis-safe.
 *   RAW_RESTRICTED   - raw frame exists but is policy-restricted
 *                      (never exported, treated as sensitive).
 *   SANITIZED_ONLY   - only the sanitized copy remains.
 */
enum class FrameStorageState(val label: String) {
    RAW("RAW"),
    SANITIZED("SANITIZED"),
    RAW_RESTRICTED("RAW_RESTRICTED"),
    SANITIZED_ONLY("SANITIZED_ONLY")
}

/*
 * Milestone 8B-10.
 *
 * Data retention classes.
 *
 * The retention model is policy-abstraction: it records the
 * CLASS of an artifact and its intended lifetime. Actual
 * deletion is executed by the existing RetentionWorker
 * (Milestone 7); the privacy layer adds classification and
 * privacy-safe sizing/timing metadata.
 */
enum class DataRetentionClass(val label: String) {

    /*
     * Raw captures. Sensitive. Longest retention today is
     * bounded by the existing retention worker.
     */
    RAW_CAPTURE("RAW_CAPTURE"),

    /*
     * Sanitized frames used for analysis. Lower sensitivity.
     */
    SANITIZED_ANALYSIS("SANITIZED_ANALYSIS"),

    /*
     * Aggregated model-evaluation data (FeedItem rows,
     * ground truth outcomes).
     */
    AGGREGATED_MODEL_DATA("AGGREGATED_MODEL_DATA"),

    /*
     * User-visible review labels.
     */
    USER_VISIBLE_LABEL_DATA("USER_VISIBLE_LABEL_DATA"),

    /*
     * Audit trails and metrics (privacy-safe by design).
     */
    AUDIT_AND_METRICS("AUDIT_AND_METRICS")
}

/*
 * Retention settings for a data class.
 */
data class RetentionPolicySettings(
    val retentionClass: DataRetentionClass,
    val retainDays: Long? = null,
    val note: String = ""
) {
    val isRetainedIndefinitely: Boolean get() = retainDays == null
}

/*
 * Milestone 8B-10.
 *
 * User consent flags.
 *
 * Consent is binary metadata: whether the user opted into
 * research-adjacent capture. It does NOT alter capture
 * behavior; it only tags artifacts for research eligibility.
 * Consent is stored privacy-safely and never contains screen
 * content.
 */
data class ConsentFlags(
    val researchCaptureConsentGranted: Boolean = false,
    val exportConsentGranted: Boolean = false,
    val consentVersion: String = "consent-v1"
) {
    val isResearchEligible: Boolean
        get() = researchCaptureConsentGranted
}