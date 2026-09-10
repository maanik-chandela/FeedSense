package com.example.feedsense.analysis.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-10.
 *
 * Privacy metrics framework.
 *
 * Only observed counts are recorded. Derived rates and
 * fabricated percentages are never invented here (see
 * docs/privacy.md, "Real-world validation status").
 */
class PrivacyMetricsTest {

    @Test
    fun `counters record observed events`() {
        val metrics = PrivacyMetrics()
        metrics.record(PrivacyMetric.FRAMES_SANITIZED)
        metrics.record(PrivacyMetric.FRAMES_SANITIZED)
        metrics.record(PrivacyMetric.REGIONS_PROCESSED, 4)

        assertEquals(2L, metrics.value(PrivacyMetric.FRAMES_SANITIZED))
        assertEquals(4L, metrics.value(PrivacyMetric.REGIONS_PROCESSED))
        assertEquals(0L, metrics.value(PrivacyMetric.FRAMES_FAILED))
    }

    @Test
    fun `snapshot is keyed and sorted`() {
        val metrics = PrivacyMetrics()
        metrics.record(PrivacyMetric.EVIDENCE_LOST)
        metrics.record(PrivacyMetric.FRAMES_RECEIVED, 3)

        val snapshot = metrics.snapshot()
        assertEquals(3L, snapshot[PrivacyMetric.FRAMES_RECEIVED])
        assertEquals(1L, snapshot[PrivacyMetric.EVIDENCE_LOST])

        assertEquals(
            listOf(
                PrivacyMetric.EVIDENCE_LOST,
                PrivacyMetric.FRAMES_RECEIVED
            ),
            snapshot.keys.sorted()
        )
    }

    @Test
    fun `values carry metric version`() {
        val metrics = PrivacyMetrics()
        metrics.record(PrivacyMetric.FRAMES_SANITIZED)

        val values = metrics.snapshotValues()
        assertEquals(
            PrivacyMetrics.METRICS_VERSION,
            values.first().policyVersion
        )
        assertTrue(values.first().recordedAtMs > 0)
    }

    @Test
    fun `no detection-rate claims are ever recorded`() {
        // Guard against any metric that would fabricate a
        // privacy detection success rate.
        val metrics = PrivacyMetrics()
        metrics.record(PrivacyMetric.FRAMES_RECEIVED, 10)
        metrics.record(PrivacyMetric.FRAMES_SANITIZED, 8)

        val snapshot = metrics.snapshot()
        // Only raw observed counters exist; no derived
        // "detectionRate" key is invented.
        assertTrue(snapshot.keys.none { it.contains("rate", ignoreCase = true) })
        assertTrue(snapshot.keys.none { it.contains("percent", ignoreCase = true) })
    }
}