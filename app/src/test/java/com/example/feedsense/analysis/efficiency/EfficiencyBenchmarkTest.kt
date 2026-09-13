package com.example.feedsense.analysis.efficiency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-11.
 *
 * EfficiencyBenchmark PLUMBING tests. No wall-clock assertions
 * here: benchmark numbers are methodology, not claims, and
 * hard timing thresholds would be flaky across machines.
 */
class EfficiencyBenchmarkTest {

    private val frame: RawPixelFrame =
        TestFrames.seededGrid(32, 32, seed = 99L)
            .let {
                RawPixelFrame(64, 32, IntArray(64 * 32) { i ->
                    (it[(i / 64) % 32] * (i % 64) + i) % 256
                })
            }

    @Test
    fun run_reportsRequestedIterationsAndFields() {
        val result = EfficiencyBenchmark.run(
            frame,
            iterations = 50,
            warmup = 5
        )
        assertEquals(50, result.iterations)
        assertEquals(5, result.warmupIterations)
        assertEquals(FrameHashAlgorithm.DHASH, result.algorithm)
        assertEquals(64, result.hashSizeBits)
        assertEquals(frame.width, result.width)
        assertEquals(frame.height, result.height)
        assertEquals(
            PerceptualHashVersion.DHASH_VERSION,
            result.version
        )
    }

    @Test
    fun medianLiesWithinMinAndMax() {
        val result = EfficiencyBenchmark.run(
            frame,
            iterations = 30,
            warmup = 3
        )
        assertTrue(result.medianMs >= result.minMs)
        assertTrue(result.medianMs <= result.maxMs)
        assertTrue(result.averageMs >= result.minMs)
        assertTrue(result.averageMs <= result.maxMs)
        assertTrue(result.minMs >= 0.0)
    }

    @Test
    fun phashBenchmarkRunsToo() {
        // pHash path must not crash and stays deterministic in
        // structure (only plumbing is asserted here).
        val result = EfficiencyBenchmark.run(
            frame,
            iterations = 10,
            warmup = 2,
            algorithm = FrameHashAlgorithm.PHASH,
            hashSize = 8
        )
        assertEquals(FrameHashAlgorithm.PHASH, result.algorithm)
        assertEquals(64, result.hashSizeBits)
        assertEquals(
            PerceptualHashVersion.PHASH_VERSION,
            result.version
        )
    }

    @Test
    fun invalidIterations_rejected() {
        try {
            EfficiencyBenchmark.run(frame, iterations = 0)
            org.junit.Assert.fail("expected IAE for iterations=0")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}