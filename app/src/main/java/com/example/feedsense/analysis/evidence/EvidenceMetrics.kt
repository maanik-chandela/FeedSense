package com.example.feedsense.analysis.evidence

import java.util.concurrent.atomic.AtomicLong

/*
 * Milestone 8B-5.
 *
 * Evidence pipeline metrics.
 *
 * Thread-safe atomic counters for measuring the
 * evidence pipeline. All values are read via
 * snapshot() to ensure a consistent view.
 *
 * Metrics tracked:
 *   - framesReceived: frames entering the pipeline
 *   - framesFused: frames successfully processed
 *   - framesFailed: frames where processing errored
 *   - totalEvidenceCount: total evidence items produced
 *   - totalIndependentSources: total independent sources
 *   - totalTimingNs: cumulative processing time
 *   - maxTimingNs: single-frame max
 *
 * Performance metrics:
 *   - averageProcessingTimeMs
 *   - maxProcessingTimeMs
 *
 * Do not fabricate metrics.
 */
class EvidenceMetrics {

    private val _framesReceived =
        AtomicLong(0L)

    private val _framesFused =
        AtomicLong(0L)

    private val _framesFailed =
        AtomicLong(0L)

    private val _totalEvidenceCount =
        AtomicLong(0L)

    private val _totalIndependentSources =
        AtomicLong(0L)

    private val _totalTimingNs =
        AtomicLong(0L)

    private val _maxTimingNs =
        AtomicLong(0L)

    fun recordReceived() {
        _framesReceived.incrementAndGet()
    }

    fun recordFused(
        evidenceCount: Int,
        independentSources: Int
    ) {
        _framesFused.incrementAndGet()
        _totalEvidenceCount.addAndGet(
            evidenceCount.toLong()
        )
        _totalIndependentSources.addAndGet(
            independentSources.toLong()
        )
    }

    fun recordFailed() {
        _framesFailed.incrementAndGet()
    }

    fun recordTiming(durationNs: Long) {
        _totalTimingNs.addAndGet(durationNs)
        _maxTimingNs.updateAndGet { current ->
            maxOf(current, durationNs)
        }
    }

    fun snapshot(): EvidenceMetricsSnapshot {

        val received =
            _framesReceived.get()

        val fused =
            _framesFused.get()

        val failed =
            _framesFailed.get()

        val totalNs =
            _totalTimingNs.get()

        val maxNs =
            _maxTimingNs.get()

        return EvidenceMetricsSnapshot(
            framesReceived = received,
            framesFused = fused,
            framesFailed = failed,
            totalEvidenceCount =
                _totalEvidenceCount.get(),
            totalIndependentSources =
                _totalIndependentSources.get(),
            averageProcessingTimeMs =
                if (received > 0) {
                    totalNs.toDouble() /
                            received.toDouble() /
                            1_000_000.0
                } else 0.0,
            maxProcessingTimeMs =
                maxNs.toDouble() / 1_000_000.0
        )
    }

    fun reset() {
        _framesReceived.set(0L)
        _framesFused.set(0L)
        _framesFailed.set(0L)
        _totalEvidenceCount.set(0L)
        _totalIndependentSources.set(0L)
        _totalTimingNs.set(0L)
        _maxTimingNs.set(0L)
    }
}

/*
 * Immutable snapshot of evidence metrics.
 */
data class EvidenceMetricsSnapshot(
    val framesReceived: Long,
    val framesFused: Long,
    val framesFailed: Long,
    val totalEvidenceCount: Long,
    val totalIndependentSources: Long,
    val averageProcessingTimeMs: Double,
    val maxProcessingTimeMs: Double
)
