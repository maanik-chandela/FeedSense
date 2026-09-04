package com.example.feedsense

import com.example.feedsense.analysis.dedup.DeduplicationMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-3.
 *
 * DeduplicationMetrics: counters, derived metrics,
 * snapshots, and reset.
 */
class DeduplicationMetricsTest {

    // --------------------------------
    // INITIAL STATE
    // --------------------------------

    @Test
    fun `initial counters are zero`() {

        val metrics =
            DeduplicationMetrics()

        assertEquals(0L, metrics.framesCaptured)
        assertEquals(0L, metrics.framesSampled)
        assertEquals(0L, metrics.framesCompared)
        assertEquals(0L, metrics.framesRetained)
        assertEquals(0L, metrics.framesDeduplicated)
        assertEquals(0L, metrics.framesForceKept)
        assertEquals(0L, metrics.framesUncertain)
    }

    @Test
    fun `initial derived metrics are zero`() {

        val metrics =
            DeduplicationMetrics()

        assertEquals(
            0.0,
            metrics.deduplicationRate,
            0.001
        )

        assertEquals(
            0.0,
            metrics.retentionRate,
            0.001
        )

        assertEquals(
            0.0,
            metrics.averageHashTimeMs,
            0.001
        )

        assertEquals(
            0.0,
            metrics.maxHashTimeMs,
            0.001
        )
    }

    // --------------------------------
    // RECORDING
    // --------------------------------

    @Test
    fun `recordCaptured increments counter`() {

        val metrics =
            DeduplicationMetrics()

        metrics.recordCaptured()
        metrics.recordCaptured()
        metrics.recordCaptured()

        assertEquals(3L, metrics.framesCaptured)
    }

    @Test
    fun `recordSampled increments counter`() {

        val metrics =
            DeduplicationMetrics()

        metrics.recordSampled()
        metrics.recordSampled()

        assertEquals(2L, metrics.framesSampled)
    }

    @Test
    fun `recordCompared increments counter and time`() {

        val metrics =
            DeduplicationMetrics()

        metrics.recordCompared(1_000_000L)
        metrics.recordCompared(2_000_000L)

        assertEquals(2L, metrics.framesCompared)

        assertEquals(
            1_500_000.0,
            metrics.averageHashTimeMs * 1_000_000,
            1.0
        )
    }

    @Test
    fun `recordRetained increments counter`() {

        val metrics =
            DeduplicationMetrics()

        metrics.recordRetained()

        assertEquals(1L, metrics.framesRetained)
    }

    @Test
    fun `recordDeduplicated increments counter`() {

        val metrics =
            DeduplicationMetrics()

        metrics.recordDeduplicated()
        metrics.recordDeduplicated()

        assertEquals(
            2L,
            metrics.framesDeduplicated
        )
    }

    @Test
    fun `recordForceKept increments counter`() {

        val metrics =
            DeduplicationMetrics()

        metrics.recordForceKept()

        assertEquals(1L, metrics.framesForceKept)
    }

    @Test
    fun `recordUncertain increments counter`() {

        val metrics =
            DeduplicationMetrics()

        metrics.recordUncertain()
        metrics.recordUncertain()
        metrics.recordUncertain()

        assertEquals(
            3L,
            metrics.framesUncertain
        )
    }

    // --------------------------------
    // DERIVED METRICS
    // --------------------------------

    @Test
    fun `deduplicationRate computes correctly`() {

        val metrics =
            DeduplicationMetrics()

        metrics.recordCompared(100L)
        metrics.recordCompared(100L)
        metrics.recordCompared(100L)
        metrics.recordDeduplicated()

        assertEquals(
            1.0 / 3.0,
            metrics.deduplicationRate,
            0.001
        )
    }

    @Test
    fun `retentionRate computes correctly`() {

        val metrics =
            DeduplicationMetrics()

        metrics.recordSampled()
        metrics.recordSampled()
        metrics.recordSampled()
        metrics.recordSampled()
        metrics.recordRetained()
        metrics.recordRetained()

        assertEquals(
            0.5,
            metrics.retentionRate,
            0.001
        )
    }

    @Test
    fun `averageHashTimeMs computes correctly`() {

        val metrics =
            DeduplicationMetrics()

        metrics.recordCompared(1_000_000L)
        metrics.recordCompared(3_000_000L)

        assertEquals(
            2.0,
            metrics.averageHashTimeMs,
            0.001
        )
    }

    @Test
    fun `maxHashTimeMs tracks maximum`() {

        val metrics =
            DeduplicationMetrics()

        metrics.recordCompared(1_000_000L)
        metrics.recordCompared(5_000_000L)
        metrics.recordCompared(3_000_000L)

        assertEquals(
            5.0,
            metrics.maxHashTimeMs,
            0.001
        )
    }

    // --------------------------------
    // SNAPSHOT
    // --------------------------------

    @Test
    fun `snapshot captures all values`() {

        val metrics =
            DeduplicationMetrics()

        metrics.recordCaptured()
        metrics.recordCaptured()
        metrics.recordSampled()
        metrics.recordCompared(1_000_000L)
        metrics.recordRetained()
        metrics.recordDeduplicated()
        metrics.recordForceKept()
        metrics.recordUncertain()

        val snapshot =
            metrics.snapshot()

        assertEquals(2L, snapshot.framesCaptured)
        assertEquals(1L, snapshot.framesSampled)
        assertEquals(1L, snapshot.framesCompared)
        assertEquals(1L, snapshot.framesRetained)
        assertEquals(
            1L,
            snapshot.framesDeduplicated
        )
        assertEquals(1L, snapshot.framesForceKept)
        assertEquals(1L, snapshot.framesUncertain)
        assertEquals(
            1.0,
            snapshot.deduplicationRate,
            0.001
        )
        assertEquals(
            1.0,
            snapshot.averageHashTimeMs,
            0.001
        )
    }

    @Test
    fun `snapshot is independent copy`() {

        val metrics =
            DeduplicationMetrics()

        metrics.recordCaptured()

        val snapshot1 =
            metrics.snapshot()

        metrics.recordCaptured()

        val snapshot2 =
            metrics.snapshot()

        assertEquals(1L, snapshot1.framesCaptured)
        assertEquals(2L, snapshot2.framesCaptured)
    }

    // --------------------------------
    // RESET
    // --------------------------------

    @Test
    fun `reset clears all counters`() {

        val metrics =
            DeduplicationMetrics()

        metrics.recordCaptured()
        metrics.recordSampled()
        metrics.recordCompared(1_000_000L)
        metrics.recordRetained()
        metrics.recordDeduplicated()
        metrics.recordForceKept()
        metrics.recordUncertain()

        metrics.reset()

        assertEquals(0L, metrics.framesCaptured)
        assertEquals(0L, metrics.framesSampled)
        assertEquals(0L, metrics.framesCompared)
        assertEquals(0L, metrics.framesRetained)
        assertEquals(
            0L,
            metrics.framesDeduplicated
        )
        assertEquals(0L, metrics.framesForceKept)
        assertEquals(0L, metrics.framesUncertain)
        assertEquals(
            0.0,
            metrics.maxHashTimeMs,
            0.001
        )
    }

    // --------------------------------
    // EDGE CASES
    // --------------------------------

    @Test
    fun `zero compared frames gives zero rates`() {

        val metrics =
            DeduplicationMetrics()

        assertEquals(
            0.0,
            metrics.deduplicationRate,
            0.001
        )

        assertEquals(
            0.0,
            metrics.retentionRate,
            0.001
        )

        assertEquals(
            0.0,
            metrics.averageHashTimeMs,
            0.001
        )
    }
}
