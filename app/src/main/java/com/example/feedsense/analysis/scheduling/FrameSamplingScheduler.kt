package com.example.feedsense.analysis.scheduling

/*
 * Milestone 8B-12.
 *
 * Adaptive frame sampling & inference scheduling.
 *
 * ANSWERS ONE QUESTION (spec §4):
 *   Given the current candidate frame and everything known about
 *   recent analysis, should FeedSense spend expensive computation
 *   on this frame?
 *
 * DECISION ORDER (deterministic, documented in
 * docs/scheduling.md §7):
 *   1. disabled            -> ANALYZE  (DISABLED_PASSTHROUGH)
 *   2. invalid context     -> ANALYZE  (UNAVAILABLE_FALLBACK, forced)
 *   3. first frame         -> ANALYZE  (FIRST_FRAME)
 *   4. elapsed >= max      -> FORCE_ANALYZE (MAX_INTERVAL, forced)
 *   5. elapsed <  min      -> SKIP     (MIN_INTERVAL) [deferred]
 *   6. transition signal   -> ANALYZE  (TRANSITION_SIGNAL)
 *   7. interaction signal  -> ANALYZE  (INTERACTION_SIGNAL)
 *   8. elapsed >= adaptive -> ANALYZE  (VISUAL_CHANGE | SCHEDULED_SAMPLE)
 *   9. otherwise           -> SKIP     (STATIC_CONTENT)
 *
 * MEMORY (spec §27): the scheduler retains only the last
 * analysis timestamp plus a bounded rolling window of recent
 * 8B-11 distances (config.rollingWindowSize) and counters -
 * never the frame history, never any pixels.
 *
 * This class NEVER hashes and NEVER inspects content. 8B-11 owns
 * perceptual similarity; 8B-12 only consumes its structured
 * result (§9, §40).
 */
class FrameSamplingScheduler(
    private val config: SamplingConfig = SamplingConfig.DEFAULT
) {

    // --------------------------------
    // O(1) + O(window) STATE
    // --------------------------------

    private var lastAnalyzedAtMs: Long? = null
    private var candidateIndex = 0L
    private var analysisIndex = 0L

    private val window = LongArray(config.rollingWindowSize)
    private var windowFilled = 0
    private var windowHead = 0

    // --------------------------------
    // COUNTERS
    // --------------------------------

    private var analyzedCount = 0L
    private var skippedCount = 0L
    private var deferredCount = 0L
    private var forcedAnalysisCount = 0L
    private var transitionAnalysisCount = 0L
    private var interactionAnalysisCount = 0L

    /*
     * Evaluates one candidate using only compact, metadata-first
     * evidence. Returns a fully attributable SamplingDecision.
     */
    @Synchronized
    fun evaluate(context: SamplingContext): SamplingDecision {
        candidateIndex++

        // Captured BEFORE any state update so every decision
        // reports the true elapsed time since the last analysis.
        val elapsed =
            lastAnalyzedAtMs?.let {
                (context.timestampMs - it).coerceAtLeast(0L)
            } ?: 0L

        // --- 1. disabled pass-through ----------------------
        if (!config.enabled) {
            analyzedCount++
            lastAnalyzedAtMs = context.timestampMs
            return baseDecision(
                context,
                SamplingAction.ANALYZE,
                SamplingReason.DISABLED_PASSTHROUGH,
                forced = false,
                interval = config.maximumAnalysisIntervalMs,
                elapsed = elapsed
            )
        }

        // --- 2. safe fallback on invalid context -----------
        // A negative timestamp means we cannot reason about
        // intervals; the documented fallback is to ANALYZE so
        // evidence is never silently discarded (spec §33).
        if (context.timestampMs < 0) {
            analyzedCount++
            lastAnalyzedAtMs = context.timestampMs
            return baseDecision(
                context,
                SamplingAction.ANALYZE,
                SamplingReason.UNAVAILABLE_FALLBACK,
                forced = true,
                interval = config.maximumAnalysisIntervalMs,
                elapsed = elapsed
            )
        }

        val distance = context.visualChangeDistance
        if (distance != null) {
            pushDistance(distance)
        }
        val averageDistance = averageDistance()
        val pressure = changePressure(averageDistance)

        // --- 3. first frame --------------------------------
        if (lastAnalyzedAtMs == null) {
            lastAnalyzedAtMs = context.timestampMs
            analysisIndex++
            analyzedCount++
            return baseDecision(
                context,
                SamplingAction.ANALYZE,
                SamplingReason.FIRST_FRAME,
                forced = false,
                interval = adaptiveIntervalMs(pressure),
                elapsed = 0L
            )
        }

        // --- 4. maximum-interval safety ceiling -------------
        if (elapsed >= config.maximumAnalysisIntervalMs) {
            lastAnalyzedAtMs = context.timestampMs
            analysisIndex++
            analyzedCount++
            forcedAnalysisCount++
            return baseDecision(
                context,
                SamplingAction.FORCE_ANALYZE,
                SamplingReason.MAX_INTERVAL,
                forced = true,
                interval = config.maximumAnalysisIntervalMs,
                elapsed = elapsed
            )
        }

        // --- 5. minimum-interval gate -----------------------
        if (elapsed < config.minimumAnalysisIntervalMs) {
            deferredCount++
            return baseDecision(
                context,
                SamplingAction.SKIP,
                SamplingReason.MIN_INTERVAL,
                forced = false,
                interval = adaptiveIntervalMs(pressure),
                elapsed = elapsed
            )
        }

        // --- 6. content-transition signal -------------------
        val transitionSignal =
            context.contentTransitionSuspected || isTransition(distance)
        if (transitionSignal) {
            lastAnalyzedAtMs = context.timestampMs
            analysisIndex++
            analyzedCount++
            transitionAnalysisCount++
            return baseDecision(
                context,
                SamplingAction.ANALYZE,
                SamplingReason.TRANSITION_SIGNAL,
                forced = false,
                interval = adaptiveIntervalMs(pressure),
                elapsed = elapsed
            )
        }

        // --- 7. interaction signal --------------------------
        if (context.interactionActive) {
            lastAnalyzedAtMs = context.timestampMs
            analysisIndex++
            analyzedCount++
            interactionAnalysisCount++
            return baseDecision(
                context,
                SamplingAction.ANALYZE,
                SamplingReason.INTERACTION_SIGNAL,
                forced = false,
                interval = adaptiveIntervalMs(pressure),
                elapsed = elapsed
            )
        }

        // --- 8. adaptive cadence ----------------------------
        val adaptive = adaptiveIntervalMs(pressure)
        if (elapsed >= adaptive) {
            lastAnalyzedAtMs = context.timestampMs
            analysisIndex++
            analyzedCount++
            val reason =
                if (pressure >= config.highChangePressureThreshold) {
                    SamplingReason.VISUAL_CHANGE
                } else {
                    SamplingReason.SCHEDULED_SAMPLE
                }
            return baseDecision(
                context,
                SamplingAction.ANALYZE,
                reason,
                forced = false,
                interval = adaptive,
                elapsed = elapsed
            )
        }

        // --- 9. static-content skip -------------------------
        skippedCount++
        return baseDecision(
            context,
            SamplingAction.SKIP,
            SamplingReason.STATIC_CONTENT,
            forced = false,
            interval = adaptive,
            elapsed = elapsed
        )
    }

    // --------------------------------
    // ADAPTIVE INTERVAL (spec §12/§13/§15)
    // --------------------------------

    /*
     * A single-frame UNIQUE with a large 8B-11 distance counts
     * as a content transition (reel break, ad, comments modal).
     */
    private fun isTransition(distance: Int?): Boolean {
        if (distance == null) return false
        return distance >= config.transitionDistance
    }

    /*
     * Change pressure in [0,1]:
     *   0 for avgDistance <= staticContentDistance
     *   1 for avgDistance >= highChangeDistance
     *   linear between. Deterministic float arithmetic.
     */
    private fun changePressure(averageDistance: Double): Double {
        val (lower, upper) =
            config.staticContentDistance to config.highChangeDistance
        return when {
            averageDistance <= lower -> 0.0
            averageDistance >= upper -> 1.0
            upper > lower ->
                (averageDistance - lower) / (upper - lower)
            else -> 0.5
        }
    }

    /*
     * adaptive = min + (max - min) * (1 - pressure).
     * High visual change -> towards the minimum interval;
     * static content -> towards the maximum interval (never
     * beyond it). Applicable only when max > min; otherwise the
     * interval equals the fixed value.
     */
    private fun adaptiveIntervalMs(pressure: Double): Long {
        val span =
            config.maximumAnalysisIntervalMs -
                config.minimumAnalysisIntervalMs
        if (span <= 0) return config.minimumAnalysisIntervalMs
        val scaled = (span.toDouble() * (1.0 - pressure))
        return config.minimumAnalysisIntervalMs +
            kotlin.math.round(scaled).toLong()
    }

    // --------------------------------
    // BOUNDED ROLLING WINDOW
    // --------------------------------

    private fun pushDistance(distance: Int) {
        val capacity = config.rollingWindowSize
        window[windowHead] = distance.toLong()
        windowHead = (windowHead + 1) % capacity
        if (windowFilled < capacity) windowFilled++
    }

    private fun averageDistance(): Double {
        if (windowFilled == 0) return 0.0
        var sum = 0L
        for (i in 0 until windowFilled) {
            sum += window[i]
        }
        return sum.toDouble() / windowFilled
    }

    // --------------------------------
    // STATE / METRICS
    // --------------------------------

    @Synchronized
    fun reset() {
        lastAnalyzedAtMs = null
        candidateIndex = 0L
        analysisIndex = 0L
        windowFilled = 0
        windowHead = 0
        window.fill(0L)
        analyzedCount = 0L
        skippedCount = 0L
        deferredCount = 0L
        forcedAnalysisCount = 0L
        transitionAnalysisCount = 0L
        interactionAnalysisCount = 0L
    }

    /*
     * Bounded-state guarantee (§37 Invariant 5): returned size is
     * at most config.rollingWindowSize regardless of candidates.
     */
    fun retainedWindowSize(): Int = windowFilled

    fun retainedWindowCapacity(): Int = config.rollingWindowSize

    /*
     * Timestamp of the most recent analysis, or null when none.
     */
    fun lastAnalysisTimestampMs(): Long? = lastAnalyzedAtMs

    @Synchronized
    fun snapshot(): SamplingStats = SamplingStats(
        candidateCount = candidateIndex,
        analyzedCount = analyzedCount,
        skippedCount = skippedCount,
        deferredCount = deferredCount,
        forcedAnalysisCount = forcedAnalysisCount,
        transitionAnalysisCount = transitionAnalysisCount,
        interactionAnalysisCount = interactionAnalysisCount
    )

    private fun baseDecision(
        context: SamplingContext,
        action: SamplingAction,
        reason: SamplingReason,
        forced: Boolean,
        interval: Long,
        elapsed: Long
    ): SamplingDecision {
        val result = context.similarityResult
        return SamplingDecision(
            action = action,
            reason = reason,
            timestampMs = context.timestampMs,
            candidateIndex = candidateIndex,
            analysisIndex = analysisIndex,
            elapsedSinceLastAnalysisMs = elapsed,
            similarityDistance = context.visualChangeDistance,
            similarityDecision = result?.decision,
            similarityAlgorithmVersion = result?.algorithmVersion,
            effectiveIntervalMs = interval,
            forced = forced,
            algorithmVersion = SamplingVersion.SAMPLING_VERSION,
            configVersion = config.configVersion
        )
    }

    override fun toString(): String =
        "FrameSamplingScheduler(${config.configVersion})"
}