package com.example.feedsense.analysis.scheduling

import com.example.feedsense.analysis.efficiency.FrameDecision
import com.example.feedsense.analysis.efficiency.FrameEvaluationReason
import com.example.feedsense.analysis.efficiency.FrameHashAlgorithm
import com.example.feedsense.analysis.efficiency.FrameSimilarityResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-12.
 *
 * SamplingStats semantics (§28): observed-only counters,
 * shared-denominator sampling ratio, and snapshot fidelity on a
 * scripted run.
 */
class SamplingStatsTest {

    private fun ctx(t: Long, d: Int) = SamplingContext(
        timestampMs = t,
        similarityResult = FrameSimilarityResult(
            sequenceIndex = 0L,
            decision = if (d >= 12) FrameDecision.UNIQUE
            else FrameDecision.SIMILAR,
            reason = FrameEvaluationReason.COMPARED,
            hash = null,
            referenceHash = null,
            hammingDistance = d,
            threshold = 10,
            algorithm = FrameHashAlgorithm.DHASH,
            algorithmVersion = "dhash-v1",
            configVersion = "dedup-v1",
            timestampMs = 0L,
            forcedForward = false
        )
    )

    @Test
    fun emptyStats_zeroEverything() {
        val s = SamplingStats()
        assertEquals(0L, s.candidateCount)
        assertEquals(0L, s.analyzedCount)
        assertEquals(0.0, s.samplingRatio, 1e-9)
    }

    @Test
    fun samplingRatio_isAnalyzedOverCandidates() {
        val stats = SamplingStats(
            candidateCount = 100,
            analyzedCount = 25,
            skippedCount = 60,
            deferredCount = 15
        )
        assertEquals(0.25, stats.samplingRatio, 1e-9)
        assertEquals(75L, stats.totalRejected)
        assertEquals(
            stats.analyzedCount.toDouble() / stats.candidateCount,
            stats.samplingRatio,
            1e-9
        )
    }

    @Test
    fun snapshotReflectsAScriptedRun() {
        val s = FrameSamplingScheduler(
            SamplingConfig(
                minimumAnalysisIntervalMs = 2_000,
                maximumAnalysisIntervalMs = 8_000,
                staticContentDistance = 4,
                highChangeDistance = 8,
                transitionDistance = 12,
                rollingWindowSize = 8
            )
        )
        // FIRST, STATIC skip, FORCE at ceiling, TRANSITION,
        // INTERACTION -> across 5 candidates with peculiar state.
        s.evaluate(ctx(0L, 0))
        s.evaluate(ctx(2_000L, 0))
        s.evaluate(ctx(4_000L, 3))   // static skip
        s.evaluate(ctx(8_000L, 5))   // FORCE (elapsed 8s)
        s.evaluate(ctx(12_000L, 40)) // TRANSITION

        val stats = s.snapshot()
        assertEquals(5L, stats.candidateCount)
        // analyses: t0 (first), t8000 (forced), t12000 (transition)
        assertEquals(3L, stats.analyzedCount)
        assertEquals(1L, stats.forcedAnalysisCount)
        assertEquals(1L, stats.transitionAnalysisCount)
        assertEquals(0L, stats.interactionAnalysisCount)
        // t2000 and t4000 fall inside the (long) static cadence
        // even though the minimum interval has elapsed.
        assertEquals(2L, stats.skippedCount)
        assertEquals(0L, stats.deferredCount)
        assertEquals(0.6, stats.samplingRatio, 1e-9)
        assertTrue(stats.totalRejected == 2L)
    }
}