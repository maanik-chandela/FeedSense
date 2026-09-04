package com.example.feedsense.analysis.dedup

/*
 * Milestone 8B-3.
 *
 * Bounded adaptive frame sampler.
 *
 * Adjusts the sampling interval based on visual change
 * magnitude:
 *   - High visual change → shorter interval (sample more
 *     frequently)
 *   - Stable visual state → longer interval (sample less
 *     frequently)
 *
 * The interval is always bounded:
 *   - minimumSamplingIntervalMs (fastest allowed)
 *   - maximumSamplingIntervalMs (slowest allowed)
 *
 * Deterministic: the same inputs produce the same interval.
 * No randomness, no unbounded feedback loops.
 *
 * The adaptive behavior is conservative: changes are
 * incremental (20% steps) to prevent oscillation.
 */
class AdaptiveFrameSampler(
    private val config: DeduplicationConfig =
        DeduplicationConfig.DEFAULT
) {

    private var currentIntervalMs: Long =
        config.minimumSamplingIntervalMs

    private var lastSampleTimestampMs: Long = 0L

    /*
     * Current sampling interval in milliseconds.
     */
    val samplingIntervalMs: Long
        get() = currentIntervalMs

    /*
     * Evaluate whether enough time has elapsed since the
     * last sample to warrant a new evaluation.
     *
     * @param currentTimestampMs  Current system time.
     * @param visualChangeMagnitude  Normalized change
     *   magnitude from the previous frame.
     *   0.0 = no change, 1.0 = maximum change.
     * @return true if a new frame should be evaluated.
     */
    fun shouldSample(
        currentTimestampMs: Long,
        visualChangeMagnitude: Float = 0f
    ): Boolean {

        // --------------------------------
        // FIRST CALL
        // --------------------------------

        if (lastSampleTimestampMs == 0L) {
            lastSampleTimestampMs =
                currentTimestampMs
            return true
        }

        // --------------------------------
        // INTERVAL CHECK
        // --------------------------------

        val elapsed =
            currentTimestampMs -
                    lastSampleTimestampMs

        if (elapsed < currentIntervalMs) {
            return false
        }

        // --------------------------------
        // ADAPT INTERVAL
        // --------------------------------
        //
        // After each accepted sample, adjust the interval
        // based on visual change magnitude. Changes are
        // incremental (20% steps) for smooth convergence.

        adaptInterval(visualChangeMagnitude)

        lastSampleTimestampMs =
            currentTimestampMs

        return true
    }

    /*
     * Force the next evaluation regardless of interval.
     * Used for force-keep scenarios.
     */
    fun forceNext() {
        lastSampleTimestampMs = 0L
    }

    /*
     * Compute a normalized visual change magnitude between
     * two bitmaps by sampling pixels.
     *
     * Returns 0.0 for identical images, up to 1.0 for
     * completely different images.
     *
     * Uses the same pixel-sampling approach as the
     * ScreenCaptureService for consistency.
     */
    fun computeChangeMagnitude(
        previousPixels: IntArray,
        currentPixels: IntArray,
        width: Int,
        height: Int,
        sampleStep: Int = SAMPLE_STEP
    ): Float {

        if (
            previousPixels.size !=
            currentPixels.size ||
            previousPixels.size < width * height
        ) {
            return 1f
        }

        var sampledPixels = 0
        var differentPixels = 0

        var y = 0

        while (y < height) {

            var x = 0

            while (x < width) {

                val prev =
                    previousPixels[y * width + x]

                val curr =
                    currentPixels[y * width + x]

                val rDiff = kotlin.math.abs(
                    ((curr shr 16) and 0xFF) -
                            ((prev shr 16) and 0xFF)
                )

                val gDiff = kotlin.math.abs(
                    ((curr shr 8) and 0xFF) -
                            ((prev shr 8) and 0xFF)
                )

                val bDiff = kotlin.math.abs(
                    (curr and 0xFF) -
                            (prev and 0xFF)
                )

                sampledPixels++

                if (
                    rDiff >
                    PIXEL_DIFFERENCE_THRESHOLD ||
                    gDiff >
                    PIXEL_DIFFERENCE_THRESHOLD ||
                    bDiff >
                    PIXEL_DIFFERENCE_THRESHOLD
                ) {
                    differentPixels++
                }

                x += sampleStep
            }

            y += sampleStep
        }

        if (sampledPixels == 0) {
            return 0f
        }

        return differentPixels.toFloat() /
                sampledPixels.toFloat()
    }

    /*
     * Reset state for a new session.
     */
    fun reset() {
        currentIntervalMs =
            config.minimumSamplingIntervalMs
        lastSampleTimestampMs = 0L
    }

    private fun adaptInterval(
        visualChangeMagnitude: Float
    ) {

        val clamped =
            visualChangeMagnitude.coerceIn(0f, 1f)

        when {

            // High change: decrease interval
            clamped > HIGH_CHANGE_THRESHOLD -> {
                val reduction =
                    (currentIntervalMs *
                            ADAPTIVE_STEP_FACTOR)
                        .toLong()

                currentIntervalMs =
                    (currentIntervalMs - reduction)
                        .coerceAtLeast(
                            config
                                .minimumSamplingIntervalMs
                        )
            }

            // Low change: increase interval
            clamped < LOW_CHANGE_THRESHOLD -> {
                val increase =
                    (currentIntervalMs *
                            ADAPTIVE_STEP_FACTOR)
                        .toLong()

                currentIntervalMs =
                    (currentIntervalMs + increase)
                        .coerceAtMost(
                            config
                                .maximumSamplingIntervalMs
                        )
            }

            // Medium change: keep current interval
        }
    }

    companion object {

        /*
         * Fraction of current interval used as adjustment
         * step. 0.2 = 20% step, providing smooth
         * convergence without oscillation.
         */
        private const val ADAPTIVE_STEP_FACTOR =
            0.2f

        /*
         * Change magnitude above which the sampling
         * interval is decreased.
         */
        private const val HIGH_CHANGE_THRESHOLD =
            0.3f

        /*
         * Change magnitude below which the sampling
         * interval is increased.
         */
        private const val LOW_CHANGE_THRESHOLD =
            0.05f

        /*
         * Pixel sampling stride for change magnitude
         * computation.
         */
        private const val SAMPLE_STEP = 20

        /*
         * Per-channel RGB difference threshold for a
         * pixel to count as "different".
         */
        private const val PIXEL_DIFFERENCE_THRESHOLD =
            25
    }
}
