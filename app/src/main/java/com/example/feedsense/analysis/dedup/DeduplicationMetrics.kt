package com.example.feedsense.analysis.dedup

import java.util.concurrent.atomic.AtomicLong

/*
 * Milestone 8B-3.
 *
 * Instrumentation for frame deduplication and filtering.
 *
 * All counters are thread-safe (atomic longs) and
 * non-blocking. The metrics themselves are lightweight.
 *
 * Tracked metrics:
 *   - framesCaptured: total frames from capture service
 *   - framesSampled: frames that passed sampling filter
 *   - framesCompared: frames that reached hash comparison
 *   - framesRetained: frames kept for analysis
 *   - framesDeduplicated: frames skipped as duplicates
 *   - framesForceKept: frames retained by force-keep
 *   - framesUncertain: frames retained due to uncertainty
 *
 * Derived metrics:
 *   - deduplicationRate: framesDeduplicated / framesCompared
 *   - retentionRate: framesRetained / framesSampled
 *   - averageHashTimeMs: mean hash computation time
 *   - maxHashTimeMs: worst-case hash computation time
 */
class DeduplicationMetrics {

    private val _framesCaptured =
        AtomicLong(0L)

    private val _framesSampled =
        AtomicLong(0L)

    private val _framesCompared =
        AtomicLong(0L)

    private val _framesRetained =
        AtomicLong(0L)

    private val _framesDeduplicated =
        AtomicLong(0L)

    private val _framesForceKept =
        AtomicLong(0L)

    private val _framesUncertain =
        AtomicLong(0L)

    private val _totalHashTimeNs =
        AtomicLong(0L)

    private val _maxHashTimeNs =
        AtomicLong(0L)

    val framesCaptured: Long
        get() = _framesCaptured.get()

    val framesSampled: Long
        get() = _framesSampled.get()

    val framesCompared: Long
        get() = _framesCompared.get()

    val framesRetained: Long
        get() = _framesRetained.get()

    val framesDeduplicated: Long
        get() = _framesDeduplicated.get()

    val framesForceKept: Long
        get() = _framesForceKept.get()

    val framesUncertain: Long
        get() = _framesUncertain.get()

    val averageHashTimeMs: Double
        get() {
            val count = _framesCompared.get()
            if (count == 0L) return 0.0
            return _totalHashTimeNs.get()
                .toDouble() / count /
                    1_000_000.0
        }

    val maxHashTimeMs: Double
        get() =
            _maxHashTimeNs.get().toDouble() /
                    1_000_000.0

    val deduplicationRate: Double
        get() {
            val compared =
                _framesCompared.get()
            if (compared == 0L) return 0.0
            return _framesDeduplicated.get()
                .toDouble() /
                    compared.toDouble()
        }

    val retentionRate: Double
        get() {
            val sampled =
                _framesSampled.get()
            if (sampled == 0L) return 0.0
            return _framesRetained.get()
                .toDouble() /
                    sampled.toDouble()
        }

    fun recordCaptured() {
        _framesCaptured.incrementAndGet()
    }

    fun recordSampled() {
        _framesSampled.incrementAndGet()
    }

    fun recordCompared(hashTimeNs: Long) {
        _framesCompared.incrementAndGet()
        _totalHashTimeNs.addAndGet(hashTimeNs)
        updateMaxHashTime(hashTimeNs)
    }

    fun recordRetained() {
        _framesRetained.incrementAndGet()
    }

    fun recordDeduplicated() {
        _framesDeduplicated.incrementAndGet()
    }

    fun recordForceKept() {
        _framesForceKept.incrementAndGet()
    }

    fun recordUncertain() {
        _framesUncertain.incrementAndGet()
    }

    /*
     * Take an immutable snapshot for logging or reporting.
     */
    fun snapshot(): MetricsSnapshot {
        return MetricsSnapshot(
            framesCaptured = framesCaptured,
            framesSampled = framesSampled,
            framesCompared = framesCompared,
            framesRetained = framesRetained,
            framesDeduplicated = framesDeduplicated,
            framesForceKept = framesForceKept,
            framesUncertain = framesUncertain,
            deduplicationRate = deduplicationRate,
            retentionRate = retentionRate,
            averageHashTimeMs = averageHashTimeMs,
            maxHashTimeMs = maxHashTimeMs
        )
    }

    fun reset() {
        _framesCaptured.set(0L)
        _framesSampled.set(0L)
        _framesCompared.set(0L)
        _framesRetained.set(0L)
        _framesDeduplicated.set(0L)
        _framesForceKept.set(0L)
        _framesUncertain.set(0L)
        _totalHashTimeNs.set(0L)
        _maxHashTimeNs.set(0L)
    }

    private fun updateMaxHashTime(
        hashTimeNs: Long
    ) {
        while (true) {
            val current =
                _maxHashTimeNs.get()

            if (hashTimeNs <= current) return

            if (
                _maxHashTimeNs
                    .compareAndSet(
                        current,
                        hashTimeNs
                    )
            ) {
                return
            }
        }
    }
}

/*
 * Milestone 8B-3.
 *
 * Immutable snapshot of deduplication metrics.
 * Used for structured logging and reporting without
 * thread-safety concerns.
 *
 * No sensitive image content is included.
 */
data class MetricsSnapshot(
    val framesCaptured: Long,
    val framesSampled: Long,
    val framesCompared: Long,
    val framesRetained: Long,
    val framesDeduplicated: Long,
    val framesForceKept: Long,
    val framesUncertain: Long,
    val deduplicationRate: Double,
    val retentionRate: Double,
    val averageHashTimeMs: Double,
    val maxHashTimeMs: Double
)
