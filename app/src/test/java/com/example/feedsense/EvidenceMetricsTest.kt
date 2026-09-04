package com.example.feedsense

import com.example.feedsense.analysis.evidence.EvidenceMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/*
 * Milestone 8B-5.
 *
 * EvidenceMetrics: counters, derived metrics,
 * snapshots, and reset.
 */
class EvidenceMetricsTest {

    private lateinit var metrics: EvidenceMetrics

    @Before
    fun setup() {
        metrics = EvidenceMetrics()
    }

    // --------------------------------
    // INITIAL STATE
    // --------------------------------

    @Test
    fun `initial snapshot has all zeros`() {

        val snapshot = metrics.snapshot()

        assertEquals(0L, snapshot.framesReceived)
        assertEquals(0L, snapshot.framesFused)
        assertEquals(0L, snapshot.framesFailed)
        assertEquals(
            0L,
            snapshot.totalEvidenceCount
        )

        assertEquals(
            0L,
            snapshot.totalIndependentSources
        )
    }

    // --------------------------------
    // COUNTER INCREMENT
    // --------------------------------

    @Test
    fun `recordReceived increments counter`() {

        metrics.recordReceived()
        metrics.recordReceived()

        val snapshot = metrics.snapshot()

        assertEquals(2L, snapshot.framesReceived)
    }

    @Test
    fun `recordFused increments counters`() {

        metrics.recordFused(5, 3)

        val snapshot = metrics.snapshot()

        assertEquals(1L, snapshot.framesFused)
        assertEquals(5L, snapshot.totalEvidenceCount)
        assertEquals(
            3L,
            snapshot.totalIndependentSources
        )
    }

    @Test
    fun `recordFailed increments counter`() {

        metrics.recordFailed()

        val snapshot = metrics.snapshot()

        assertEquals(1L, snapshot.framesFailed)
    }

    // --------------------------------
    // TIMING
    // --------------------------------

    @Test
    fun `timing metrics computed correctly`() {

        metrics.recordReceived()
        metrics.recordReceived()
        metrics.recordTiming(1_000_000L)
        metrics.recordTiming(3_000_000L)

        val snapshot = metrics.snapshot()

        assertEquals(
            2.0,
            snapshot.averageProcessingTimeMs,
            1e-6
        )

        assertEquals(
            3.0,
            snapshot.maxProcessingTimeMs,
            1e-6
        )
    }

    @Test
    fun `max timing tracks highest value`() {

        metrics.recordReceived()
        metrics.recordTiming(1_000_000L)
        metrics.recordTiming(5_000_000L)
        metrics.recordTiming(2_000_000L)

        val snapshot = metrics.snapshot()

        assertEquals(
            5.0,
            snapshot.maxProcessingTimeMs,
            1e-6
        )
    }

    // --------------------------------
    // RESET
    // --------------------------------

    @Test
    fun `reset clears all counters`() {

        metrics.recordReceived()
        metrics.recordFused(5, 3)
        metrics.recordFailed()
        metrics.recordTiming(1_000_000L)

        metrics.reset()

        val snapshot = metrics.snapshot()

        assertEquals(0L, snapshot.framesReceived)
        assertEquals(0L, snapshot.framesFused)
        assertEquals(0L, snapshot.framesFailed)
        assertEquals(
            0L,
            snapshot.totalEvidenceCount
        )

        assertEquals(
            0.0,
            snapshot.averageProcessingTimeMs,
            1e-9
        )
    }

    // --------------------------------
    // THREAD SAFETY
    // --------------------------------

    @Test
    fun `concurrent increments are consistent`() {

        val threads = (1..10).map {
            Thread {
                repeat(100) {
                    metrics.recordReceived()
                }
            }
        }

        threads.forEach { it.start() }
        threads.forEach { it.join() }

        val snapshot = metrics.snapshot()

        assertEquals(
            1000L,
            snapshot.framesReceived
        )
    }
}
