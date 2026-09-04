package com.example.feedsense.analysis.evaluation

import com.example.feedsense.model.GroundTruth

// --------------------------------
// INTERACTION METRICS (Milestone 8A-3)
// --------------------------------
//
// Each interaction signal (liked / commented / shared / saved
// / followed / paused / playing) is predicted by the AI as
// present/absent (Boolean) and recorded by the human as
// tri-state (true / false / UNKNOWN).
//
// Per-signal metrics are computed ONLY over items where the
// human decided (UNKNOWN is never inferred). UNKNOWN signals
// are counted and surfaced per signal so the reader knows how
// much of the dataset could not be meaningfully compared at
// all.

data class SignalMetrics(
    val signal: String,
    val decided: Int,
    val unknown: Int,
    val binary: BinaryMetrics
)

data class InteractionMetrics(
    val signals: List<SignalMetrics>,
    val sampleCount: Int
) {

    companion object {

        val SIGNAL_KEYS = GroundTruth.INTERACTION_SIGNAL_KEYS

        fun compute(
            // For every item: the AI's predicted signal set and
            // the tri-state truth map (signal -> Boolean?).
            items: List<Pair<Set<String>, Map<String, Boolean?>>>,
            minimumSample: Int = 1,
            confidenceLevel: Double = 0.95
        ): InteractionMetrics {

            val signalMetrics = SIGNAL_KEYS.map { signal ->
                var decided = 0
                var unknown = 0
                val pairs = mutableListOf<Pair<Boolean, Boolean>>()

                items.forEach { (predicted, truth) ->
                    val predictedPresent = signal in predicted
                    val truthValue = truth[signal]
                    when (truthValue) {
                        null -> unknown++
                        else -> {
                            decided++
                            pairs.add(predictedPresent to truthValue)
                        }
                    }
                }

                val binary =
                    BinaryMetrics.compute(
                        pairs = pairs,
                        minimumSample = minimumSample,
                        confidenceLevel = confidenceLevel
                    )

                SignalMetrics(
                    signal = signal,
                    decided = decided,
                    unknown = unknown,
                    binary = binary
                )
            }

            return InteractionMetrics(
                signals = signalMetrics,
                sampleCount = items.size
            )
        }
    }
}