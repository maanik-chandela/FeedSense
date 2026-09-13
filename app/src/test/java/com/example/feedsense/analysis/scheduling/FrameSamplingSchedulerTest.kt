package com.example.feedsense.analysis.scheduling

import com.example.feedsense.analysis.efficiency.FrameDecision
import com.example.feedsense.analysis.efficiency.FrameEvaluationReason
import com.example.feedsense.analysis.efficiency.FrameHashAlgorithm
import com.example.feedsense.analysis.efficiency.FrameSimilarityResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-12.
 *
 * FrameSamplingScheduler behaviour tests: startup, minimum /
 * maximum intervals, adaptive (static / high-change) behaviour,
 * transitions, interactions, state management, session
 * isolation, determinism, boundedness, fallback, and the
 * property-style invariants of spec §37.
 *
 * Timestamps are injected through SamplingContext, so the
 * scheduler needs no real clock and every sequence is fully
 * deterministic.
 */
class FrameSamplingSchedulerTest {

    private fun scheduler(
        enabled: Boolean = true,
        minMs: Long = 1_000L,
        maxMs: Long = 8_000L,
        staticDist: Int = 4,
        highDist: Int = 8,
        transitionDist: Int = 12,
        pressure: Double = 0.5
    ) = FrameSamplingScheduler(
        SamplingConfig(
            enabled = enabled,
            minimumAnalysisIntervalMs = minMs,
            maximumAnalysisIntervalMs = maxMs,
            staticContentDistance = staticDist,
            highChangeDistance = highDist,
            transitionDistance = transitionDist,
            highChangePressureThreshold = pressure,
            rollingWindowSize = 8
        )
    )

    private fun ctx(
        t: Long,
        d: Int? = null,
        interaction: Boolean = false,
        transitionSuspected: Boolean = false
    ) = SamplingContext(
        timestampMs = t,
        similarityResult = d?.let { sim(it) },
        interactionActive = interaction,
        contentTransitionSuspected = transitionSuspected
    )

    private fun sim(d: Int) = FrameSimilarityResult(
        sequenceIndex = 0L,
        decision = when {
            d == 0 -> FrameDecision.DUPLICATE
            d >= 12 -> FrameDecision.UNIQUE
            else -> FrameDecision.SIMILAR
        },
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

    // ---------------------------------------------------------
    // STARTUP (§20, §36)
    // ---------------------------------------------------------

    @Test
    fun firstCandidate_isAnalyzed() {
        val s = scheduler()
        val d = s.evaluate(ctx(0L, d = 5))
        assertEquals(SamplingAction.ANALYZE, d.action)
        assertEquals(SamplingReason.FIRST_FRAME, d.reason)
        assertFalse(d.forced)
        assertEquals(1L, d.candidateIndex)
        assertEquals(1L, d.analysisIndex)
        assertEquals(0L, d.elapsedSinceLastAnalysisMs)
        assertEquals(SamplingVersion.SAMPLING_VERSION, d.algorithmVersion)
        assertEquals(SamplingVersion.SAMPLING_VERSION, d.configVersion)
        assertEquals(5, d.similarityDistance)
    }

    @Test
    fun firstFrame_alwaysWins_overTransitionSignal() {
        val s = scheduler()
        val d = s.evaluate(ctx(0L, d = 40))
        assertEquals(SamplingReason.FIRST_FRAME, d.reason)
    }

    @Test
    fun disabled_passesEveryCandidateThrough() {
        val s = FrameSamplingScheduler(SamplingConfig.BASELINE)
        repeat(5) { i ->
            val d = s.evaluate(ctx(i * 100L, d = 2))
            assertEquals(SamplingAction.ANALYZE, d.action)
            assertEquals(
                SamplingReason.DISABLED_PASSTHROUGH,
                d.reason
            )
            assertFalse(d.forced)
        }
        val stats = s.snapshot()
        assertEquals(5L, stats.candidateCount)
        assertEquals(5L, stats.analyzedCount)
        assertEquals(0L, stats.forcedAnalysisCount)
        assertEquals(1.0, stats.samplingRatio, 1e-9)
    }

    // ---------------------------------------------------------
    // MINIMUM INTERVAL (§7, §36)
    // ---------------------------------------------------------

    @Test
    fun interiorCandidates_areDeferred() {
        val s = scheduler()
        s.evaluate(ctx(0L, d = 0))
        val early = s.evaluate(ctx(300L, d = 12))
        assertEquals(SamplingAction.SKIP, early.action)
        assertEquals(SamplingReason.MIN_INTERVAL, early.reason)
        val again = s.evaluate(ctx(600L, d = 12))
        assertEquals(SamplingReason.MIN_INTERVAL, again.reason)
        // The minimum interval is a hard gate even for
        // transitions (spec §19 keeps it in force).
        assertEquals(0L, s.snapshot().transitionAnalysisCount)
    }

    @Test
    fun minimumBoundary_isInclusive() {
        val s = scheduler()
        s.evaluate(ctx(0L, d = 64))
        // One millisecond shy of the gate: deferred, even though
        // the transition distance is huge.
        assertEquals(
            SamplingReason.MIN_INTERVAL,
            s.evaluate(ctx(999L, d = 64)).reason
        )
        // Exactly at the gate: eligible, so the transition path
        // (which follows the gate) fires.
        val eligible = s.evaluate(ctx(1_999L, d = 64))
        assertTrue(eligible.analyzes)
        assertTrue(eligible.elapsedSinceLastAnalysisMs >= 1_000L)
        assertEquals(1_999L, eligible.elapsedSinceLastAnalysisMs)
    }

    @Test
    fun afterMinimumInterval_candidateBecomesEligible() {
        val s = scheduler()
        s.evaluate(ctx(0L, d = 64))
        val eligible = s.evaluate(ctx(2_500L, d = 64))
        assertTrue(eligible.analyzes)
    }

    // ---------------------------------------------------------
    // MAXIMUM INTERVAL (§8, §36)
    // ---------------------------------------------------------

    @Test
    fun maximumInterval_forcesAnalysisForTemporalCoverage() {
        val s = scheduler()
        s.evaluate(ctx(0L, d = 0))
        val skip = s.evaluate(ctx(4_000L, d = 0))
        assertEquals(SamplingAction.SKIP, skip.action)
        assertEquals(SamplingReason.STATIC_CONTENT, skip.reason)
        assertEquals(
            SamplingAction.SKIP,
            s.evaluate(ctx(7_000L, d = 0)).action
        )
        val forced = s.evaluate(ctx(8_000L, d = 0))
        assertEquals(SamplingAction.FORCE_ANALYZE, forced.action)
        assertEquals(SamplingReason.MAX_INTERVAL, forced.reason)
        assertTrue(forced.forced)
        assertEquals(1L, s.snapshot().forcedAnalysisCount)
    }

    @Test
    fun maximumInterval_preventsIndefiniteSkipping() {
        val s = scheduler()
        s.evaluate(ctx(0L, d = 0))
        var last = 0L
        repeat(20) { i ->
            val t = ((i + 1) * 5_000L)
            val d = s.evaluate(ctx(t, d = 0))
            if (d.analyzes) last = t
        }
        val stats = s.snapshot()
        assertTrue(stats.forcedAnalysisCount > 0)
        assertTrue(last >= 100_000L - 8_000L)
    }

    // ---------------------------------------------------------
    // STATIC & HIGH-CHANGE ADAPTIVE BEHAVIOUR (§12, §13, §36)
    // ---------------------------------------------------------

    @Test
    fun staticContent_lengthensInterval_towardsMaximum() {
        val s = scheduler()
        s.evaluate(ctx(0L, d = 0))
        assertEquals(
            SamplingReason.STATIC_CONTENT,
            s.evaluate(ctx(2_000L, d = 0)).reason
        )
        assertEquals(
            SamplingReason.STATIC_CONTENT,
            s.evaluate(ctx(5_000L, d = 0)).reason
        )
        val forced = s.evaluate(ctx(8_000L, d = 0))
        assertEquals(SamplingAction.FORCE_ANALYZE, forced.action)
        assertEquals(8_000L, forced.effectiveIntervalMs)
    }

    @Test
    fun highChange_shortensInterval_towardsMinimum() {
        val s = scheduler()
        s.evaluate(ctx(0L, d = 64))
        val analyzed = mutableListOf(0L)
        repeat(6) { i ->
            val t = 500L * (i + 1)
            val d = s.evaluate(ctx(t, d = 64))
            if (d.analyzes) analyzed.add(t)
        }
        val gaps = analyzed.zipWithNext { a, b -> b - a }
        assertTrue(
            "gaps $gaps should respect the minimum interval",
            gaps.all { it >= 1_000L }
        )
        val stats = s.snapshot()
        assertTrue(stats.analyzedCount >= 2L)
        assertTrue(stats.samplingRatio > 0.5)
    }

    // ---------------------------------------------------------
    // TRANSITIONS (§10, §19, §36)
    // ---------------------------------------------------------

    @Test
    fun bigDistance_grantsTransitionAnalysis() {
        val s = scheduler()
        s.evaluate(ctx(0L, d = 0))
        val transition = s.evaluate(ctx(3_000L, d = 40))
        assertEquals(SamplingAction.ANALYZE, transition.action)
        assertEquals(SamplingReason.TRANSITION_SIGNAL, transition.reason)
        assertEquals(1L, s.snapshot().transitionAnalysisCount)
    }

    @Test
    fun explicitTransitionSuspicion_alsoGrants() {
        val s = scheduler()
        s.evaluate(ctx(0L, d = 0))
        val d = s.evaluate(
            ctx(3_000L, d = 0, transitionSuspected = true)
        )
        assertEquals(SamplingReason.TRANSITION_SIGNAL, d.reason)
    }

    @Test
    fun transition_respectsMinimumInterval_onFastBursts() {
        val s = scheduler()
        s.evaluate(ctx(0L, d = 0))
        val d = s.evaluate(ctx(500L, d = 40))
        assertEquals(SamplingReason.MIN_INTERVAL, d.reason)
        assertEquals(0L, s.snapshot().transitionAnalysisCount)
    }

    // ---------------------------------------------------------
    // INTERACTION (§16, §36)
    // ---------------------------------------------------------

    @Test
    fun interactionSignal_prioritizesAnAnalysis() {
        val s = scheduler()
        s.evaluate(ctx(0L, d = 0))
        val d = s.evaluate(ctx(3_000L, d = 0, interaction = true))
        assertEquals(SamplingAction.ANALYZE, d.action)
        assertEquals(SamplingReason.INTERACTION_SIGNAL, d.reason)
        assertEquals(1L, s.snapshot().interactionAnalysisCount)
    }

    @Test
    fun interaction_isSchedulingSignalOnly() {
        val s = scheduler()
        s.evaluate(ctx(0L, d = 0))
        s.evaluate(ctx(3_000L, d = 0, interaction = true))
        val stats = s.snapshot()
        assertEquals(2L, stats.analyzedCount)
        assertEquals(1L, stats.interactionAnalysisCount)
    }

    // ---------------------------------------------------------
    // STATE (§21, §36, §37)
    // ---------------------------------------------------------

    @Test
    fun skippedCandidate_doesNotResetAnalysisTimestamp() {
        val s = scheduler()
        s.evaluate(ctx(0L, d = 0))
        s.evaluate(ctx(2_000L, d = 0))
        s.evaluate(ctx(4_000L, d = 0))
        assertEquals(0L, s.lastAnalysisTimestampMs()!!)

        val forced = s.evaluate(ctx(9_000L, d = 0))
        assertEquals(SamplingAction.FORCE_ANALYZE, forced.action)
        assertEquals(9_000L, s.lastAnalysisTimestampMs()!!)
    }

    @Test
    fun reset_clearsAllStateAndRestartsCoverage() {
        val s = scheduler()
        s.evaluate(ctx(0L, d = 40))
        s.evaluate(ctx(3_000L, d = 40))
        assertTrue(s.snapshot().analyzedCount >= 1L)
        assertEquals(2, s.retainedWindowSize())

        s.reset()
        assertEquals(0L, s.snapshot().candidateCount)
        assertEquals(0L, s.snapshot().analyzedCount)
        assertEquals(0, s.retainedWindowSize())
        assertTrue(s.lastAnalysisTimestampMs() == null)

        val first = s.evaluate(ctx(10_000L, d = 40))
        assertEquals(SamplingReason.FIRST_FRAME, first.reason)
        assertEquals(1L, first.analysisIndex)
        // Session B does NOT remember the old session's history.
        assertEquals(1L, s.snapshot().candidateCount)
    }

    @Test
    fun sessionIsolation_schedulerBDoesNotInheritHistory() {
        val a = scheduler()
        a.evaluate(ctx(0L, d = 40))
        a.evaluate(ctx(3_000L, d = 40))
        a.evaluate(ctx(6_000L, d = 40))

        val b = scheduler()
        val firstB = b.evaluate(ctx(0L, d = 40))
        assertEquals(SamplingReason.FIRST_FRAME, firstB.reason)
        assertEquals(1L, firstB.analysisIndex)
        assertEquals(1L, b.snapshot().candidateCount)
        assertEquals(1L, b.snapshot().analyzedCount)
        // A's own state is untouched.
        assertEquals(3L, a.snapshot().candidateCount)
    }

    @Test
    fun deterministic_sameInputs_sameDecisions() {
        val inputs = listOf(
            ctx(0L, d = 0),
            ctx(2_000L, d = 0),
            ctx(5_000L, d = 8),
            ctx(8_000L, d = 0),
            ctx(12_000L, d = 40),
            ctx(15_000L, d = 0, interaction = true)
        )
        fun run(): List<Triple<SamplingAction, SamplingReason, Int?>> {
            val s = scheduler()
            return inputs.map { s.evaluate(it) }
                .map { Triple(it.action, it.reason, it.similarityDistance) }
        }
        assertEquals(run(), run())
        assertEquals(run(), run())
    }

    // ---------------------------------------------------------
    // SEQUENCE TESTS (§36)
    // ---------------------------------------------------------

    @Test
    fun sequence_aaaabbcc_schedulingDecisions() {
        // A = static (d0), B = moderate (d7), C = transition (d40).
        // Cadence 2000 ms, min 2000, max 8000.
        val s = scheduler(minMs = 2_000L)
        val frames = listOf(
            0 to 0,
            2_000 to 0,
            4_000 to 0,
            6_000 to 0,
            8_000 to 7,
            10_000 to 7,
            12_000 to 40,
            14_000 to 40
        )
        val decisions = frames.map { (t, d) ->
            s.evaluate(ctx(t.toLong(), d = d))
        }

        assertEquals(
            listOf(
                SamplingAction.ANALYZE,       // A  first
                SamplingAction.SKIP,          // A  static
                SamplingAction.SKIP,          // A  static
                SamplingAction.SKIP,          // A  static
                SamplingAction.FORCE_ANALYZE, // B at ceiling
                SamplingAction.SKIP,          // B rising
                SamplingAction.ANALYZE,       // C transition
                SamplingAction.ANALYZE        // C transition
            ),
            decisions.map { it.action }
        )
        assertEquals(
            listOf(
                SamplingReason.FIRST_FRAME,
                SamplingReason.STATIC_CONTENT,
                SamplingReason.STATIC_CONTENT,
                SamplingReason.STATIC_CONTENT,
                SamplingReason.MAX_INTERVAL,
                SamplingReason.STATIC_CONTENT,
                SamplingReason.TRANSITION_SIGNAL,
                SamplingReason.TRANSITION_SIGNAL
            ),
            decisions.map { it.reason }
        )

        val stats = s.snapshot()
        assertEquals(8L, stats.candidateCount)
        assertEquals(4L, stats.analyzedCount)
        assertEquals(4L, stats.skippedCount)
        assertEquals(0L, stats.deferredCount)
        assertEquals(1L, stats.forcedAnalysisCount)
        assertEquals(2L, stats.transitionAnalysisCount)
        assertEquals(0.5, stats.samplingRatio, 1e-9)
    }

    @Test
    fun sequence_abcde_allDifferentContent() {
        // Distances 0/6/8/9/10/10 at a 2500 ms cadence (min 1000,
        // max 8000, static 4, high 8, transition 12). The rolling
        // average of 8B-11 distances (first-frame distance
        // included) climbs 0 -> 3 -> 4.67 -> 5.75 -> 6.6 -> 7.17,
        // which shortens the adaptive interval from 8000 ms down to
        // 2470 ms. D falls due (SCHEDULED_SAMPLE at p=0.44) and the
        // final frame clears the p>=0.5 threshold (VISUAL_CHANGE).
        val s = scheduler()
        val frames = listOf(
            0 to 0, 2_500 to 6, 5_000 to 8, 7_500 to 9,
            10_000 to 10, 12_500 to 10
        )
        val decisions = frames.map { (t, d) ->
            s.evaluate(ctx(t.toLong(), d = d))
        }
        assertEquals(
            listOf(
                SamplingAction.ANALYZE, // A first
                SamplingAction.SKIP,    // B below cadence
                SamplingAction.SKIP,    // C below cadence
                SamplingAction.ANALYZE, // D due, weak pressure
                SamplingAction.SKIP,    // E not yet due
                SamplingAction.ANALYZE  // F strong pressure
            ),
            decisions.map { it.action }
        )
        assertEquals(
            listOf(
                SamplingReason.FIRST_FRAME,
                SamplingReason.STATIC_CONTENT,
                SamplingReason.STATIC_CONTENT,
                SamplingReason.SCHEDULED_SAMPLE,
                SamplingReason.STATIC_CONTENT,
                SamplingReason.VISUAL_CHANGE
            ),
            decisions.map { it.reason }
        )
        assertEquals(4_938L, decisions[3].effectiveIntervalMs)
        assertTrue(decisions[3].similarityDistance == 9)
        assertTrue(decisions[5].effectiveIntervalMs < 3_000L)
    }

    @Test
    fun scrollBurst_aAprimeAprimeAprimeB_avoidsIntermediateInference() {
        // Reel A then a rapid A -> A' -> A'' -> A''' scroll burst
        // then new reel B. Only A's entry and B's transition get
        // analysed; every intermediate animation frame is saved.
        val s = scheduler()
        val frames = listOf(
            0 to 2, 300 to 2, 600 to 2, 900 to 2, 1_200 to 40
        )
        val decisions = frames.map { (t, d) ->
            s.evaluate(ctx(t.toLong(), d = d))
        }

        assertEquals(
            listOf(
                SamplingAction.ANALYZE,
                SamplingAction.SKIP,
                SamplingAction.SKIP,
                SamplingAction.SKIP,
                SamplingAction.ANALYZE
            ),
            decisions.map { it.action }
        )
        assertEquals(
            listOf(
                SamplingReason.FIRST_FRAME,
                SamplingReason.MIN_INTERVAL,
                SamplingReason.MIN_INTERVAL,
                SamplingReason.MIN_INTERVAL,
                SamplingReason.TRANSITION_SIGNAL
            ),
            decisions.map { it.reason }
        )

        val stats = s.snapshot()
        assertEquals(2L, stats.analyzedCount)
        assertEquals(3L, stats.deferredCount)
        val analyzedAt = decisions.filter { it.analyzes }.map { it.timestampMs }
        assertEquals(listOf(0L, 1_200L), analyzedAt)
        assertTrue(analyzedAt[1] - analyzedAt[0] >= 1_000L)
    }

    // ---------------------------------------------------------
    // FALLBACK (§33)
    // ---------------------------------------------------------

    @Test
    fun invalidTimestamp_usesDocumentedSafeFallback() {
        val s = scheduler()
        val d = s.evaluate(ctx(-5L, d = null))
        assertEquals(SamplingAction.ANALYZE, d.action)
        assertEquals(SamplingReason.UNAVAILABLE_FALLBACK, d.reason)
        assertTrue(d.forced)
    }

    @Test
    fun fallback_thenCoverageContinues() {
        val s = scheduler()
        s.evaluate(ctx(-1L))
        assertEquals(1L, s.snapshot().analyzedCount)
        // The stream continues with normal coverage rules.
        val forced = s.evaluate(ctx(8_000L, d = 40))
        assertEquals(SamplingAction.FORCE_ANALYZE, forced.action)
        assertEquals(SamplingReason.MAX_INTERVAL, forced.reason)
    }

    // ---------------------------------------------------------
    // BOUNDED STATE & INVARIANTS (§27, §37)
    // ---------------------------------------------------------

    @Test
    fun invariant1_noAnalysisCloserThanMinimum() {
        val s = scheduler()
        val min = 1_000L
        var previousAnalysis = -1L
        var anyAnalysis = false
        for (i in 0 until 400) {
            val t = i * 300L
            val d = s.evaluate(ctx(t, d = i % 64))
            if (d.analyzes) {
                if (anyAnalysis) {
                    val gap = t - previousAnalysis
                    assertTrue(
                        "analysis gap $gap below minimum interval",
                        gap >= min
                    )
                }
                anyAnalysis = true
                previousAnalysis = t
            }
        }
        assertTrue(anyAnalysis)
    }

    @Test
    fun invariant2_noStarvationUnderMaxInterval() {
        val s = scheduler()
        var previousAnalysis = -1L
        var anyAnalysis = false
        for (i in 0 until 60) {
            val t = i * 1_000L
            val d = s.evaluate(ctx(t, d = 0))
            if (d.analyzes) {
                if (anyAnalysis) {
                    val gap = t - previousAnalysis
                    assertTrue(
                        "coverage gap $gap exceeds maximum interval",
                        gap <= 8_000L
                    )
                }
                anyAnalysis = true
                previousAnalysis = t
            }
        }
        val stats = s.snapshot()
        assertTrue(stats.forcedAnalysisCount > 0L)
        assertTrue(previousAnalysis >= 60_000L - 8_000L)
    }

    @Test
    fun invariant5_schedulerStateRemainsBounded() {
        val s = FrameSamplingScheduler(SamplingConfig.DEFAULT)
        repeat(10_000) { i ->
            s.evaluate(ctx(i * 1L, d = i % 64))
        }
        assertEquals(8, s.retainedWindowCapacity())
        assertEquals(8, s.retainedWindowSize())
        assertEquals(10_000L, s.snapshot().candidateCount)
    }
}