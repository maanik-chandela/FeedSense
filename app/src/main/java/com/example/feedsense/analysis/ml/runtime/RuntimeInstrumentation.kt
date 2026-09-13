package com.example.feedsense.analysis.ml.runtime

// --------------------------------
// RUNTIME INSTRUMENTATION (8B-15-5)
// --------------------------------
//
// Lightweight timing instrumentation for the runtime adapter.
//
// Measures:
//   - model load duration
//   - initialization duration
//   - warm-up duration (if applicable)
//   - inference duration
//   - output validation duration
//
// Design rules:
//   - does NOT yet establish performance claims ("real-time",
//     "production-ready", "X FPS", "low latency")
//   - those require proper device benchmarking later
//   - timing is based on injectable clock for determinism
//   - no fabrication of performance values

/**
 * Collected timing data for a complete adapter operation.
 */
data class RuntimeTimingData(
    val loadDurationMs: Long = 0L,
    val initializationDurationMs: Long = 0L,
    val warmupDurationMs: Long = 0L,
    val inferenceDurationMs: Long = 0L,
    val outputValidationDurationMs: Long = 0L,
    val compatibilityCheckDurationMs: Long = 0L,
    val inputValidationDurationMs: Long = 0L
) {
    /**
     * Total duration from load to inference completion.
     */
    val totalDurationMs: Long
        get() = loadDurationMs + initializationDurationMs +
            warmupDurationMs + inferenceDurationMs +
            outputValidationDurationMs

    /**
     * Inference-only duration (inference + output validation).
     */
    val inferenceOnlyDurationMs: Long
        get() = inferenceDurationMs + outputValidationDurationMs
}

/**
 * Collects timing data during adapter operations. Uses an
 * injectable clock source for deterministic testing.
 *
 * The collector does NOT fabricate performance values or
 * establish claims. It only records what actually happened.
 */
class RuntimeTimingCollector(
    private val clock: com.example.feedsense.analysis.ml.InferenceClock =
        com.example.feedsense.analysis.ml.SystemInferenceClock
) {
    private var loadStart: Long = 0L
    private var initStart: Long = 0L
    private var warmupStart: Long = 0L
    private var inferenceStart: Long = 0L
    private var outputValidationStart: Long = 0L
    private var compatibilityCheckStart: Long = 0L
    private var inputValidationStart: Long = 0L

    private var lastTiming: RuntimeTimingData = RuntimeTimingData()

    /**
     * Start timing the load phase.
     */
    fun startLoad() {
        loadStart = clock.nowMs()
    }

    /**
     * End timing the load phase.
     */
    fun endLoad() {
        lastTiming = lastTiming.copy(
            loadDurationMs = clock.nowMs() - loadStart
        )
    }

    /**
     * Start timing the initialization phase.
     */
    fun startInitialization() {
        initStart = clock.nowMs()
    }

    /**
     * End timing the initialization phase.
     */
    fun endInitialization() {
        lastTiming = lastTiming.copy(
            initializationDurationMs = clock.nowMs() - initStart
        )
    }

    /**
     * Start timing the warm-up phase.
     */
    fun startWarmup() {
        warmupStart = clock.nowMs()
    }

    /**
     * End timing the warm-up phase.
     */
    fun endWarmup() {
        lastTiming = lastTiming.copy(
            warmupDurationMs = clock.nowMs() - warmupStart
        )
    }

    /**
     * Start timing the inference phase.
     */
    fun startInference() {
        inferenceStart = clock.nowMs()
    }

    /**
     * End timing the inference phase.
     */
    fun endInference() {
        lastTiming = lastTiming.copy(
            inferenceDurationMs = clock.nowMs() - inferenceStart
        )
    }

    /**
     * Start timing the output validation phase.
     */
    fun startOutputValidation() {
        outputValidationStart = clock.nowMs()
    }

    /**
     * End timing the output validation phase.
     */
    fun endOutputValidation() {
        lastTiming = lastTiming.copy(
            outputValidationDurationMs = clock.nowMs() - outputValidationStart
        )
    }

    /**
     * Start timing the compatibility check phase.
     */
    fun startCompatibilityCheck() {
        compatibilityCheckStart = clock.nowMs()
    }

    /**
     * End timing the compatibility check phase.
     */
    fun endCompatibilityCheck() {
        lastTiming = lastTiming.copy(
            compatibilityCheckDurationMs = clock.nowMs() - compatibilityCheckStart
        )
    }

    /**
     * Start timing the input validation phase.
     */
    fun startInputValidation() {
        inputValidationStart = clock.nowMs()
    }

    /**
     * End timing the input validation phase.
     */
    fun endInputValidation() {
        lastTiming = lastTiming.copy(
            inputValidationDurationMs = clock.nowMs() - inputValidationStart
        )
    }

    /**
     * Returns the accumulated timing data and resets the
     * collector.
     */
    fun collect(): RuntimeTimingData {
        val result = lastTiming
        lastTiming = RuntimeTimingData()
        return result
    }

    /**
     * Returns the current accumulated timing data without
     * resetting.
     */
    fun current(): RuntimeTimingData = lastTiming

    /**
     * Resets all accumulated timing data.
     */
    fun reset() {
        lastTiming = RuntimeTimingData()
    }
}
