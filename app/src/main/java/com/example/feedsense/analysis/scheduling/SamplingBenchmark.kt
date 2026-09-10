package com.example.feedsense.analysis.scheduling

import com.example.feedsense.analysis.efficiency.FrameDecision

/*
 * Milestone 8B-12.
 *
 * Controlled scheduling benchmark (spec §38).
 *
 * Synthesizes deterministic candidate streams (timestamps +
 * compact visual-change magnitudes) for eleven documented
 * scenarios and reports, per scenario, how many candidates were
 * analyzed / skipped / forced. This exercises the scheduler on
 * the *shape* of the problem (static vs moving vs bursting vs
 * transitioning) and is self-checking infrastructure.
 *
 * EXPLICITLY NOT a real-world accuracy benchmark: it uses no
 * annotated data and claims no accuracy or battery numbers.
 */
object SamplingBenchmark {

    data class ScenarioOutcome(
        val name: String,
        val candidateCount: Int,
        val analyzedCount: Int,
        val skippedCount: Int,
        val deferredCount: Int,
        val forcedAnalysisCount: Int,
        val transitionAnalysisCount: Int,
        val samplingRatio: Double
    )

    data class BenchmarkReport(
        val scenarios: List<ScenarioOutcome>,
        val cadenceMs: Long,
        val configVersion: String,
        val algorithmVersion: String
    ) {
        fun scenario(name: String): ScenarioOutcome =
            scenarios.first { it.name == name }
    }

    fun run(
        config: SamplingConfig = SamplingConfig.DEFAULT,
        cadenceMs: Long = DEFAULT_CADENCE_MS,
        durationMs: Long = DEFAULT_DURATION_MS
    ): BenchmarkReport {
        val count = (durationMs / cadenceMs).toInt().coerceAtLeast(1)

        val scenarios = listOf(
            "static-screen" to { _: Int -> 0 },
            "static-reel" to { i: Int ->
                if (i % 97 == 0) 2 else 0
            },
            "moving-video" to { i: Int -> 6 + (i % 5) },
            "rapid-scrolling" to { i: Int -> 20 + (i % 9) },
            "new-reel" to { i: Int ->
                // Deliberately NOT on the 8 s ceiling grid so the
                // transition fires through the transition path
                // rather than always crossing MAX_INTERVAL first.
                if (i % 25 == 7) 40 else 0
            },
            "advertisement" to { i: Int ->
                if (i % 20 in 0..3) 64 else 0
            },
            "comments" to { i: Int -> 8 + (i % 7) },
            "modal" to { i: Int ->
                if (i % 25 == 12) 45 else 2
            },
            "overlay" to { i: Int ->
                if (i % 17 == 5) 10 else 1
            },
            "slow-transition" to { i: Int ->
                (i % 40) * 2
            },
            "rapid-transition" to { i: Int -> 64 }
        )

        val outcomes = scenarios.map { (name, distanceFn) ->
            val sampler = FrameSamplingScheduler(config)
            var analyzed = 0
            var skipped = 0
            var deferred = 0
            var forced = 0
            var transitioned = 0
            for (i in 0 until count) {
                // Deterministic stream of purely synthetic
                // magnitude evidence - no real frames, no pixels.
                val distance =
                    distanceFn(i).coerceIn(0, hashSpaceMax)
                val decisionResult = SamplingDecisionStub(
                    distance, distance > config.transitionDistance
                )
                val context = SamplingContext(
                    timestampMs = i.toLong() * cadenceMs,
                    similarityResult = decisionResult.asFrameSimilarity()
                )
                val d = sampler.evaluate(context)
                when (d.action) {
                    SamplingAction.ANALYZE -> analyzed++
                    SamplingAction.FORCE_ANALYZE -> {
                        analyzed++
                        forced++
                    }
                    SamplingAction.SKIP -> {
                        when (d.reason) {
                            SamplingReason.MIN_INTERVAL -> deferred++
                            else -> skipped++
                        }
                    }
                }
                if (d.reason == SamplingReason.TRANSITION_SIGNAL) {
                    transitioned++
                }
            }
            ScenarioOutcome(
                name = name,
                candidateCount = count,
                analyzedCount = analyzed,
                skippedCount = skipped,
                deferredCount = deferred,
                forcedAnalysisCount = forced,
                transitionAnalysisCount = transitioned,
                samplingRatio = analyzed.toDouble() / count
            )
        }

        return BenchmarkReport(
            scenarios = outcomes,
            cadenceMs = cadenceMs,
            configVersion = config.configVersion,
            algorithmVersion = SamplingVersion.SAMPLING_VERSION
        )
    }

    /** 8B-11 dHash default hash space: hashSize^2 = 64 bits. */
    private const val hashSpaceMax = 64

    private const val DEFAULT_CADENCE_MS = 400L
    private const val DEFAULT_DURATION_MS = 30_000L
}

/*
 * Minimal deterministic stand-in for a structured 8B-11 result
 * so the benchmark can drive the scheduler from pure synthetic
 * evidence without running any hashing.
 */
internal class SamplingDecisionStub(
    private val distance: Int,
    private val unique: Boolean
) {
    fun asFrameSimilarity(): com.example.feedsense.analysis.efficiency.FrameSimilarityResult {
        val decision =
            if (unique) FrameDecision.UNIQUE
            else if (distance == 0) FrameDecision.DUPLICATE
            else FrameDecision.SIMILAR
        return com.example.feedsense.analysis.efficiency.FrameSimilarityResult(
            sequenceIndex = 0L,
            decision = decision,
            reason = com.example.feedsense.analysis.efficiency.FrameEvaluationReason.COMPARED,
            hash = null,
            referenceHash = null,
            hammingDistance = distance,
            threshold = 10,
            algorithm = com.example.feedsense.analysis.efficiency.FrameHashAlgorithm.DHASH,
            algorithmVersion = "dhash-v1",
            configVersion = "dedup-v1",
            timestampMs = 0L,
            forcedForward = false
        )
    }
}