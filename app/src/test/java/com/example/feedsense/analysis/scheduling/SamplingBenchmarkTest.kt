package com.example.feedsense.analysis.scheduling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-12.
 *
 * Controlled benchmark tests (§38). These assert the SHAPE of
 * the problem is correctly handled (static screens analyze
 * rarely, rapid scrolling mobilizes the cadence, coverage forces
 * run on static content) - not real-world accuracy, which this
 * synthetic corpus explicitly cannot claim.
 */
class SamplingBenchmarkTest {

    @Test
    fun report_isDeterministic() {
        assertEquals(SamplingBenchmark.run(), SamplingBenchmark.run())
    }

    @Test
    fun report_coversAllScenariosWithValidRatio() {
        val report = SamplingBenchmark.run()
        assertEquals(11, report.scenarios.size)
        assertEquals(SamplingVersion.SAMPLING_VERSION, report.algorithmVersion)
        assertEquals(SamplingVersion.SAMPLING_VERSION, report.configVersion)
        report.scenarios.forEach { o ->
            assertTrue(
                "${o.name} ratio",
                o.samplingRatio in 0.0..1.0
            )
            assertTrue(
                "${o.name} counts",
                o.analyzedCount + o.skippedCount + o.deferredCount ==
                    o.candidateCount
            )
        }
    }

    @Test
    fun staticScreen_analyzesLessThanMovingContent() {
        val report = SamplingBenchmark.run()
        val staticRatio = report.scenario("static-screen").samplingRatio
        val movingRatio = report.scenario("moving-video").samplingRatio
        val rapidRatio = report.scenario("rapid-scrolling").samplingRatio

        assertTrue(
            "static $staticRatio should be below moving $movingRatio",
            staticRatio < movingRatio
        )
        assertTrue(
            "moving $movingRatio should be below rapid $rapidRatio",
            movingRatio < rapidRatio
        )
    }

    @Test
    fun staticScreen_usesForcedCoverage() {
        val report = SamplingBenchmark.run()
        val staticScreen = report.scenario("static-screen")
        assertTrue(
            "static screen coverage: forced=${staticScreen.forcedAnalysisCount}",
            staticScreen.forcedAnalysisCount > 0
        )
        // The ceiling guarantee: even a stone-static screen is
        // not entirely starved.
        assertTrue(staticScreen.analyzedCount > 0)
    }

    @Test
    fun rapidScrolling_neverNeedsForcing() {
        val report = SamplingBenchmark.run()
        val rapid = report.scenario("rapid-scrolling")
        // Change pressure saturates at min interval, so the 8s
        // ceiling is never approached.
        assertEquals(0, rapid.forcedAnalysisCount)
    }

    @Test
    fun transitioningScenarios_triggerTransitionAnalyses() {
        val report = SamplingBenchmark.run()
        assertTrue(
            report.scenario("new-reel").transitionAnalysisCount > 0
        )
        assertTrue(
            report.scenario("advertisement").transitionAnalysisCount > 0
        )
        assertTrue(
            report.scenario("rapid-transition")
                .transitionAnalysisCount > 0
        )
    }

    @Test
    fun fixedMode_ratioIsIntervalspaced() {
        // 400 ms cadence / 2000 ms interval -> exactly 1 in 5
        // candidates is analyzed (0.2).
        val report = SamplingBenchmark.run(SamplingConfig.fixed(2_000))
        val o = report.scenario("static-screen")
        assertEquals(0.2, o.samplingRatio, 0.01)
        // Every interval grid hit coincides with the safety
        // ceiling, so every sampled candidate is (correctly)
        // forced.
        assertTrue(o.forcedAnalysisCount > 0)
    }
}