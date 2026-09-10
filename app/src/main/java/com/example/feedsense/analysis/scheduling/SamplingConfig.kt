package com.example.feedsense.analysis.scheduling

/*
 * Milestone 8B-12.
 *
 * Versioned, centralized configuration for the adaptive frame
 * sampling / inference scheduling layer.
 *
 * ALL scheduling tunables live here. Constants are never
 * scattered through the scheduler or the pipeline.
 */

/*
 * Algorithm/behaviour version of the sampling layer.
 *
 * Bump whenever the decision semantics or the configuration
 * contract changes so experiments remain attributable.
 */
object SamplingVersion {
    const val SAMPLING_VERSION = "sampling-v1"
}

/*
 * Immutable, validated scheduling configuration.
 *
 * Same config + same inputs => same sampling decisions.
 */
data class SamplingConfig(
    /*
     * Master switch. When false the scheduler passes every
     * candidate through as ANALYZE (disabled pass-through,
     * compatible with the 8B-11 DISABLED behaviour) - the
     * Baseline experiment path (Experiment A).
     */
    val enabled: Boolean = true,

    /*
     * Minimum spacing between two analyses (ms).
     *
     * Boundary is INCLUSIVE for eligibility:
     *   elapsed <  min -> SKIP (MIN_INTERVAL)
     *   elapsed >= min -> eligible for analysis
     *
     * Protects against rapid capture, animations, progress-bar
     * movement, scrolling noise and repeated inference.
     */
    val minimumAnalysisIntervalMs: Long = DEFAULT_MIN_INTERVAL_MS,

    /*
     * Maximum allowed gap between analyses (ms).
     *
     * When elapsed since the last analysis reaches this value
     * the next candidate is FORCED through (FORCE_ANALYZE /
     * MAX_INTERVAL) so a visually static screen never starves
     * the evidence stream. Provides temporal coverage.
     */
    val maximumAnalysisIntervalMs: Long = DEFAULT_MAX_INTERVAL_MS,

    /*
     * Visual-change distances (from 8B-11) that anchor the
     * adaptive interval:
     *
     *   avgDistance <= staticContentDistance  -> interval -> max
     *   avgDistance >= highChangeDistance     -> interval -> min
     *   between                                 -> linear
     *
     * Values are ABSOLUTE Hamming distances from the 8B-11
     * hasher used by the caller (default dHash=64bit, so 0..64).
     */
    val staticContentDistance: Int = DEFAULT_STATIC_CONTENT_DISTANCE,

    val highChangeDistance: Int = DEFAULT_HIGH_CHANGE_DISTANCE,

    /*
     * A single-frame distance at or above this value is treated
     * as a CONTENT TRANSITION signal (Reel->Reel, video->ad,
     * Shorts->comments) and grants an early analysis opportunity
     * once the minimum interval has elapsed.
     */
    val transitionDistance: Int = DEFAULT_TRANSITION_DISTANCE,

    /*
     * Visual-change pressure (0..1, derived from recent 8B-11
     * distances) above which an analysis that becomes due is
     * labelled VISUAL_CHANGE rather than SCHEDULED_SAMPLE.
     */
    val highChangePressureThreshold: Double =
        DEFAULT_HIGH_CHANGE_PRESSURE_THRESHOLD,

    /*
     * Size of the bounded rolling window of recent visual
     * distances kept to steer static/high-change behaviour.
     * OBSERVED STATE IS O(window), never O(frames).
     */
    val rollingWindowSize: Int = DEFAULT_ROLLING_WINDOW_SIZE,

    val configVersion: String = SamplingVersion.SAMPLING_VERSION
) {

    init {
        require(minimumAnalysisIntervalMs >= 0) {
            "minimumAnalysisIntervalMs must be >= 0, " +
                "got $minimumAnalysisIntervalMs"
        }
        require(maximumAnalysisIntervalMs >= minimumAnalysisIntervalMs) {
            "maximumAnalysisIntervalMs must be >= " +
                "minimumAnalysisIntervalMs"
        }
        require(staticContentDistance >= 0) {
            "staticContentDistance must be >= 0"
        }
        require(highChangeDistance >= staticContentDistance) {
            "highChangeDistance must be >= staticContentDistance"
        }
        require(transitionDistance >= staticContentDistance) {
            "transitionDistance must be >= staticContentDistance"
        }
        require(highChangePressureThreshold in 0.0..1.0) {
            "highChangePressureThreshold must be in [0,1], " +
                "got $highChangePressureThreshold"
        }
        require(rollingWindowSize in 1..MAX_ROLLING_WINDOW_SIZE) {
            "rollingWindowSize must be in [1, $MAX_ROLLING_WINDOW_SIZE], " +
                "got $rollingWindowSize"
        }
    }

    companion object {

        /* Research defaults - documented in docs/scheduling.md. */
        const val DEFAULT_MIN_INTERVAL_MS = 1_000L
        const val DEFAULT_MAX_INTERVAL_MS = 8_000L
        const val DEFAULT_STATIC_CONTENT_DISTANCE = 4
        const val DEFAULT_HIGH_CHANGE_DISTANCE = 16
        const val DEFAULT_TRANSITION_DISTANCE = 12
        const val DEFAULT_HIGH_CHANGE_PRESSURE_THRESHOLD = 0.5
        const val DEFAULT_ROLLING_WINDOW_SIZE = 8
        const val MAX_ROLLING_WINDOW_SIZE = 64

        /*
         * Adaptive production/research default.
         */
        val DEFAULT = SamplingConfig()

        /*
         * Experiment A / Baseline: analyze every eligible frame
         * (disabled pass-through; the scheduler never blocks).
         */
        val BASELINE = SamplingConfig(enabled = false)

        /*
         * Experiment B / fixed-rate: analyze every `intervalMs`
         * milliseconds. The minimum and maximum intervals
         * coincide, so every due candidate is a cadence sample
         * that simultaneously satisfies the safety ceiling.
         */
        fun fixed(intervalMs: Long): SamplingConfig {
            require(intervalMs >= 0) {
                "fixed interval must be >= 0, got $intervalMs"
            }
            return SamplingConfig(
                minimumAnalysisIntervalMs = intervalMs,
                maximumAnalysisIntervalMs = intervalMs
            )
        }

        /*
         * Safety ceiling only: no adaptive reduction below the
         * maximum. Pure temporal coverage.
         */
        val COVERAGE_ONLY = SamplingConfig(
            minimumAnalysisIntervalMs = DEFAULT_MAX_INTERVAL_MS
        )
    }
}