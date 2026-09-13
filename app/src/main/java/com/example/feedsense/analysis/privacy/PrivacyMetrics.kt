package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-10.
 *
 * Privacy metrics framework.
 *
 * Only counts that are actually observed are recorded. The
 * framework NEVER fabricates detection rates or privacy
 * percentages; those numbers appear only when a real
 * evaluation run has computed them (see docs/privacy.md,
 * "Real-world validation status").
 *
 * Every counter is versioned for reproducibility.
 */

/*
 * Definitive metric names. Single source of truth so no
 * component invents its own string keys.
 */
object PrivacyMetric {

    const val FRAMES_RECEIVED = "privacy.frames_received"
    const val FRAMES_SANITIZED = "privacy.frames_sanitized"
    const val FRAMES_PARTIAL = "privacy.frames_partially_sanitized"
    const val FRAMES_NOT_REQUIRED = "privacy.frames_not_required"
    const val FRAMES_FAILED = "privacy.frames_failed"
    const val FRAMES_BLOCKED = "privacy.frames_capture_blocked"

    const val REGIONS_DETECTED = "privacy.regions_detected"
    const val REGIONS_PROCESSED = "privacy.regions_processed"

    const val TEXT_SEGMENTS_REDACTED = "privacy.text_segments_redacted"
    const val DECISIONS_AFFECTED = "privacy.decisions_affected_by_redaction"
    const val EVIDENCE_LOST = "privacy.evidence_lost_due_to_sanitization"

    const val SANITIZATION_MS = "privacy.sanitization_time_ms"
}

/*
 * A recorded privacy metric. Immutable.
 */
data class PrivacyMetricValue(
    val name: String,
    val longValue: Long,
    val recordedAtMs: Long,
    val policyVersion: String = PrivacySanitizationVersion.POLICY
)

/*
 * Milestone 8B-10.
 *
 * Thread-safe counters for privacy metrics. Only observed
 * events are recorded; no rates are derived here.
 */
class PrivacyMetrics(
    private val timestampProvider: () -> Long = {
        System.currentTimeMillis()
    }
) {

    private val counters =
        java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun record(metric: String, delta: Long = 1L) {
        counters.merge(metric, delta, Long::plus)
    }

    fun value(metric: String): Long {
        return counters[metric] ?: 0L
    }

    /*
     * Snapshot of all recorded values, keyed by metric name.
     */
    fun snapshot(): Map<String, Long> =
        counters.toMap()

    /*
     * Snapshot as values with timestamps (for export).
     */
    fun snapshotValues(): List<PrivacyMetricValue> {
        val now = timestampProvider()
        return snapshot().entries
            .sortedBy { it.key }
            .map { (name, value) ->
                PrivacyMetricValue(
                    name = name,
                    longValue = value,
                    recordedAtMs = now
                )
            }
    }

    companion object {
        const val METRICS_VERSION = PrivacySanitizationVersion.POLICY
    }
}