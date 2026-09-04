package com.example.feedsense

import com.example.feedsense.analysis.privacy.SanitizationMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/*
 * Milestone 8B-4.
 *
 * SanitizationMetrics: counters, derived metrics,
 * snapshots, and reset.
 */
class SanitizationMetricsTest {

    private lateinit var metrics: SanitizationMetrics

    @Before
    fun setup() {
        metrics = SanitizationMetrics()
    }

    // --------------------------------
    // INITIAL STATE
    // --------------------------------

    @Test
    fun `initial snapshot has all zeros`() {

        val snapshot = metrics.snapshot()

        assertEquals(0L, snapshot.framesReceived)
        assertEquals(0L, snapshot.framesSanitized)
        assertEquals(0L, snapshot.framesUnchanged)
        assertEquals(0L, snapshot.framesUncertain)
        assertEquals(0L, snapshot.framesFailed)
        assertEquals(0L, snapshot.regionsProtected)
    }

    @Test
    fun `initial derived metrics are zero`() {

        val snapshot = metrics.snapshot()

        assertEquals(
            0.0,
            snapshot.sanitizationRatePercent,
            1e-9
        )

        assertEquals(
            0.0,
            snapshot.privacyFailureRatePercent,
            1e-9
        )
    }

    // --------------------------------
    // COUNTER INCREMENT
    // --------------------------------

    @Test
    fun `recordReceived increments counter`() {

        metrics.recordReceived()
        metrics.recordReceived()
        metrics.recordReceived()

        val snapshot = metrics.snapshot()

        assertEquals(3L, snapshot.framesReceived)
    }

    @Test
    fun `recordSanitized increments counters`() {

        metrics.recordSanitized(2)
        metrics.recordSanitized(3)

        val snapshot = metrics.snapshot()

        assertEquals(2L, snapshot.framesSanitized)
        assertEquals(5L, snapshot.regionsProtected)
    }

    @Test
    fun `recordUnchanged increments counter`() {

        metrics.recordUnchanged()

        val snapshot = metrics.snapshot()

        assertEquals(1L, snapshot.framesUnchanged)
    }

    @Test
    fun `recordUncertain increments counter`() {

        metrics.recordUncertain()

        val snapshot = metrics.snapshot()

        assertEquals(1L, snapshot.framesUncertain)
    }

    @Test
    fun `recordFailed increments counter`() {

        metrics.recordFailed()

        val snapshot = metrics.snapshot()

        assertEquals(1L, snapshot.framesFailed)
    }

    // --------------------------------
    // DERIVED METRICS
    // --------------------------------

    @Test
    fun `sanitization rate computed correctly`() {

        metrics.recordReceived()
        metrics.recordReceived()
        metrics.recordReceived()
        metrics.recordReceived()
        metrics.recordSanitized(1)

        val snapshot = metrics.snapshot()

        assertEquals(
            25.0,
            snapshot.sanitizationRatePercent,
            1e-9
        )
    }

    @Test
    fun `failure rate computed correctly`() {

        metrics.recordReceived()
        metrics.recordReceived()
        metrics.recordFailed()

        val snapshot = metrics.snapshot()

        assertEquals(
            50.0,
            snapshot.privacyFailureRatePercent,
            1e-9
        )
    }

    @Test
    fun `uncertainty rate computed correctly`() {

        metrics.recordReceived()
        metrics.recordReceived()
        metrics.recordReceived()
        metrics.recordUncertain()

        val snapshot = metrics.snapshot()

        assertEquals(
            100.0 / 3.0,
            snapshot.uncertaintyRatePercent,
            1e-9
        )
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
            snapshot.averageSanitizationTimeMs,
            1e-6
        )

        assertEquals(
            3.0,
            snapshot.maxSanitizationTimeMs,
            1e-6
        )
    }

    @Test
    fun `max timing tracks highest value`() {

        metrics.recordReceived()
        metrics.recordReceived()
        metrics.recordReceived()
        metrics.recordTiming(1_000_000L)
        metrics.recordTiming(5_000_000L)
        metrics.recordTiming(2_000_000L)

        val snapshot = metrics.snapshot()

        assertEquals(
            5.0,
            snapshot.maxSanitizationTimeMs,
            1e-6
        )
    }

    // --------------------------------
    // RESET
    // --------------------------------

    @Test
    fun `reset clears all counters`() {

        metrics.recordReceived()
        metrics.recordSanitized(1)
        metrics.recordUnchanged()
        metrics.recordUncertain()
        metrics.recordFailed()
        metrics.recordTiming(1_000_000L)

        metrics.reset()

        val snapshot = metrics.snapshot()

        assertEquals(0L, snapshot.framesReceived)
        assertEquals(0L, snapshot.framesSanitized)
        assertEquals(0L, snapshot.framesUnchanged)
        assertEquals(0L, snapshot.framesUncertain)
        assertEquals(0L, snapshot.framesFailed)
        assertEquals(0L, snapshot.regionsProtected)
        assertEquals(
            0.0,
            snapshot.averageSanitizationTimeMs,
            1e-9
        )
        assertEquals(
            0.0,
            snapshot.maxSanitizationTimeMs,
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
