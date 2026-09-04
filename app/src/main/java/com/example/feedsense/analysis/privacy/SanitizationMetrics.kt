package com.example.feedsense.analysis.privacy

import java.util.concurrent.atomic.AtomicLong

/*
 * Milestone 8B-4.
 *
 * Privacy sanitization metrics.
 *
 * Thread-safe atomic counters for measuring the
 * sanitization pipeline. All values are read via
 * snapshot() to ensure a consistent view.
 *
 * Metrics tracked:
 *   - framesReceived: frames entering the sanitizer
 *   - framesSanitized: frames with regions transformed
 *   - framesUnchanged: frames with no regions detected
 *   - framesUncertain: frames that could not be classified
 *   - framesFailed: frames where sanitization errored
 *   - regionsProtected: total regions processed
 *
 * Derived metrics:
 *   - sanitizationRate: sanitized / received
 *   - privacyFailureRate: failed / received
 *   - uncertaintyRate: uncertain / received
 *
 * Performance metrics:
 *   - totalSanitizationTimeNs: cumulative time
 *   - maxSanitizationTimeNs: single-frame max
 *
 * Do not fabricate metrics.
 */
class SanitizationMetrics {

    private val _framesReceived =
        AtomicLong(0L)

    private val _framesSanitized =
        AtomicLong(0L)

    private val _framesUnchanged =
        AtomicLong(0L)

    private val _framesUncertain =
        AtomicLong(0L)

    private val _framesFailed =
        AtomicLong(0L)

    private val _regionsProtected =
        AtomicLong(0L)

    private val _totalSanitizationTimeNs =
        AtomicLong(0L)

    private val _maxSanitizationTimeNs =
        AtomicLong(0L)

    fun recordReceived() {
        _framesReceived.incrementAndGet()
    }

    fun recordSanitized(regionCount: Int) {
        _framesSanitized.incrementAndGet()
        _regionsProtected.addAndGet(
            regionCount.toLong()
        )
    }

    fun recordUnchanged() {
        _framesUnchanged.incrementAndGet()
    }

    fun recordUncertain() {
        _framesUncertain.incrementAndGet()
    }

    fun recordFailed() {
        _framesFailed.incrementAndGet()
    }

    fun recordTiming(durationNs: Long) {
        _totalSanitizationTimeNs.addAndGet(
            durationNs
        )

        _maxSanitizationTimeNs.updateAndGet { current ->
            maxOf(current, durationNs)
        }
    }

    fun snapshot(): SanitizationMetricsSnapshot {

        val received =
            _framesReceived.get()

        val sanitized =
            _framesSanitized.get()

        val unchanged =
            _framesUnchanged.get()

        val uncertain =
            _framesUncertain.get()

        val failed =
            _framesFailed.get()

        val totalNs =
            _totalSanitizationTimeNs.get()

        val maxNs =
            _maxSanitizationTimeNs.get()

        return SanitizationMetricsSnapshot(
            framesReceived = received,
            framesSanitized = sanitized,
            framesUnchanged = unchanged,
            framesUncertain = uncertain,
            framesFailed = failed,
            regionsProtected =
                _regionsProtected.get(),
            sanitizationRatePercent =
                if (received > 0) {
                    sanitized.toDouble() /
                            received.toDouble() *
                            100.0
                } else 0.0,
            privacyFailureRatePercent =
                if (received > 0) {
                    failed.toDouble() /
                            received.toDouble() *
                            100.0
                } else 0.0,
            uncertaintyRatePercent =
                if (received > 0) {
                    uncertain.toDouble() /
                            received.toDouble() *
                            100.0
                } else 0.0,
            averageSanitizationTimeMs =
                if (received > 0) {
                    totalNs.toDouble() /
                            received.toDouble() /
                            1_000_000.0
                } else 0.0,
            maxSanitizationTimeMs =
                maxNs.toDouble() / 1_000_000.0
        )
    }

    fun reset() {
        _framesReceived.set(0L)
        _framesSanitized.set(0L)
        _framesUnchanged.set(0L)
        _framesUncertain.set(0L)
        _framesFailed.set(0L)
        _regionsProtected.set(0L)
        _totalSanitizationTimeNs.set(0L)
        _maxSanitizationTimeNs.set(0L)
    }
}

/*
 * Immutable snapshot of sanitization metrics.
 */
data class SanitizationMetricsSnapshot(
    val framesReceived: Long,
    val framesSanitized: Long,
    val framesUnchanged: Long,
    val framesUncertain: Long,
    val framesFailed: Long,
    val regionsProtected: Long,
    val sanitizationRatePercent: Double,
    val privacyFailureRatePercent: Double,
    val uncertaintyRatePercent: Double,
    val averageSanitizationTimeMs: Double,
    val maxSanitizationTimeMs: Double
)
