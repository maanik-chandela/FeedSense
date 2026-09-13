package com.example.feedsense.analysis.efficiency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-11.
 *
 * FrameDedupStats semantics: the two rates share one
 * denominator and therefore sum to 1.0; averages are 0 when
 * nothing was compared or seen.
 */
class FrameDedupStatsTest {

    @Test
    fun emptyStats_reportZeroRatesAndAverages() {
        val stats = FrameDedupStats()
        assertEquals(0, stats.framesSeen)
        assertEquals(0L, stats.hashComputations)
        assertEquals(0.0, stats.averageHashDistance, 1e-9)
        assertEquals(0.0, stats.deduplicationRate, 1e-9)
        assertEquals(0.0, stats.forwardRate, 1e-9)
    }

    @Test
    fun ratesShareOneDenominator_andSumToOne() {
        val stats = FrameDedupStats(
            framesSeen = 100,
            framesAccepted = 3,
            framesRejected = 97,
            hashComputations = 99,
            hashDistanceSum = 99 * 5
        )
        assertEquals(0.97, stats.deduplicationRate, 1e-9)
        assertEquals(0.03, stats.forwardRate, 1e-9)
        assertEquals(
            stats.deduplicationRate + stats.forwardRate,
            1.0,
            1e-9
        )
        assertEquals(5.0, stats.averageHashDistance, 1e-9)
    }

    @Test
    fun snapshotMirrorsAScriptedRunWithForcedForward() {
        var now = 0L
        val frame =
            TestFrames.solid(64, 64, 128)
        val dedup = FrameDeduplicator(
            FrameDeduplicationConfig(
                maximumForwardIntervalMs = 10_000,
                maxHammingDistance = 10
            ),
            nowMs = { now }
        )

        // 4 identical frames then 6 more separated by 10s each,
        // every 10s forcing a refresh.
        now = 0L
        repeat(4) { dedup.evaluate(frame) }          // +1 accepted, +3 rejected
        now = 10_000L
        repeat(3) { dedup.evaluate(frame) }          // +1 forced, +2 rejected
        now = 20_000L
        repeat(3) { dedup.evaluate(frame) }          // +1 forced, +2 rejected

        val stats = dedup.snapshot()
        assertEquals(10L, stats.framesSeen)
        assertEquals(10L, stats.hashComputations)
        assertEquals(3L, stats.framesAccepted)
        assertEquals(2L, stats.forcedForwardCount)
        assertEquals(7L, stats.framesRejected)
        assertEquals(
            stats.framesAccepted.toDouble() / stats.framesSeen,
            stats.forwardRate,
            1e-9
        )
        assertTrue(stats.averageHashDistance == 0.0)
    }
}