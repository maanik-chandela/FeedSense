package com.example.feedsense.analysis.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-13.
 *
 * Benchmark structure and determinism. Timing values are only
 * verified for derivation correctness via an injected clock;
 * real timings are machine-dependent and never asserted as
 * absolute numbers.
 */
class PrivacyBenchmarkTest {

    private class StepClock(
        var value: Long,
        val stepMs: Double
    ) : () -> Long {
        override fun invoke(): Long {
            val v = value
            value += (stepMs * 1_000_000).toLong()
            return v
        }
    }

    @Test
    fun `median and derived statistics follow the injected clock`() {
        val clock = StepClock(0L, stepMs = 2.0)
        val benchmark = PrivacyBenchmark(clockNs = clock)

        val run = benchmark.run(
            PrivacyBenchmarkScenarios.scenarios(100, 100)[1], // research
            frames = 5
        )

        assertEquals(5, run.processingMillis.size)
        assertEquals(2.0, run.medianProcessingMs, 0.001)
        assertEquals(2.0, run.meanProcessingMs, 0.001)
        assertEquals(1000.0 / 2.0, run.framesPerSecond, 0.001)
        assertEquals(0, run.droppedFrames)
        assertTrue(run.transformationsApplied.isNotEmpty())
    }

    @Test
    fun `benchmark scenarios cover the four documented modes`() {
        val labels = PrivacyBenchmarkScenarios.scenarios().map { it.label }
        assertEquals(
            listOf("disabled", "research", "strict", "heavy"),
            labels
        )
    }

    @Test
    fun `disabled scenario is the no-op control`() {
        val bench = PrivacyBenchmark(clockNs = StepClock(0L, stepMs = 1.0))
        val run = bench.run(
            PrivacyBenchmarkScenarios.scenarios(64, 64)[0], // disabled
            frames = 3
        )
        assertTrue(run.transformationsApplied.isEmpty())
        assertEquals(0, run.droppedFrames)
        assertEquals(3, run.frames)
    }

    @Test
    fun `frame pixel byte estimate is intrinsic`() {
        val bench = PrivacyBenchmark(clockNs = StepClock(0L, stepMs = 1.0))
        val run = bench.run(
            PrivacyBenchmarkScenarios.scenarios(64, 64)[1],
            frames = 2
        )
        assertEquals(64L * 64L * 4L, run.framePixelBytes)
    }

    @Test
    fun `real-clock smoke run produces sane non-negative stats`() {
        val benchmark = PrivacyBenchmark()
        for (scenario in PrivacyBenchmarkScenarios.scenarios(64, 64).take(2)) {
            val run = benchmark.run(scenario, frames = 10)
            assertTrue(run.medianProcessingMs >= 0.0)
            assertTrue(run.framesPerSecond > 0.0)
            assertEquals(0, run.droppedFrames)
        }
    }

    @Test
    fun `benchmark run is deterministic under a fixed clock`() {
        val bench = PrivacyBenchmark(clockNs = StepClock(0L, stepMs = 1.0))
        val scenario = PrivacyBenchmarkScenarios.scenarios(64, 64)[2] // strict
        val a = bench.run(scenario, frames = 4)
        val b = bench.run(scenario, frames = 4)
        assertEquals(a.processingMillis, b.processingMillis)
        assertEquals(
            a.transformationsApplied.values.toSet(),
            b.transformationsApplied.values.toSet()
        )
    }
}