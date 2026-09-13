package com.example.feedsense.analysis.efficiency

/*
 * Milestone 8B-11.
 *
 * Perceptual frame deduplicator - the standalone efficiency
 * primitive.
 *
 * STATE SEMANTICS (§11 of the 8B-11 spec):
 *   The reference is the LAST ACCEPTED frame, not the very
 *   first frame. A captured frame is compared with the current
 *   reference; only when it is meaningfully changed (or the
 *   safety ceiling is reached) does it become the new
 *   reference. This lets gradual visual transitions roll
 *   forward correctly instead of comparing everything to the
 *   session start.
 *
 * MEMORY (§19): retained reference state is O(1) with respect
 *   to the number of frames processed - a single hash plus a
 *   timestamp and counters. It never grows linearly with
 *   session length (verified by a bounded-state test).
 *
 * This class does NOT perform semantic understanding. "UNIQUE"
 * means "sufficiently different from the current visual
 * reference to justify downstream processing", NOT "this is a
 * new reel". Perceptual deduplication is an efficiency
 * heuristic, not ground truth (§15, §35).
 */
class FrameDeduplicator(
    private val config: FrameDeduplicationConfig =
        FrameDeduplicationConfig.DEFAULT,
    private val hasher: PerceptualHasher = PerceptualHasher(
        config.hashAlgorithm,
        config.hashSize
    ),
    private val nowMs: () -> Long = {
        System.currentTimeMillis()
    }
) {

    // --------------------------------
    // STATE (O(1))
    // --------------------------------

    private var reference: FrameHash? = null
    private var lastForwardMs: Long = 0L
    private var lastForwardFrameId: String? = null

    // --------------------------------
    // COUNTERS
    // --------------------------------

    private var framesSeen = 0L
    private var hashComputations = 0L
    private var framesAccepted = 0L
    private var framesRejected = 0L
    private var forcedForwardCount = 0L
    private var hashDistanceSum = 0L

    /*
     * Evaluates one captured frame.
     *
     * First frame is always UNIQUE (FIRST_FRAME) so it is
     * eligible for downstream analysis; only another
     * independent filter may reject it. Sequence index starts
     * at 1.
     */
    @Synchronized
    fun evaluate(
        frame: FrameImage,
        frameId: String? = null
    ): FrameSimilarityResult {

        framesSeen++
        val sequenceIndex = framesSeen
        val timestamp = nowMs()
        val currentReference = reference

        val base = FrameSimilarityResult(
            sequenceIndex = sequenceIndex,
            frameId = frameId,
            algorithm = config.hashAlgorithm,
            algorithmVersion = hasher.version,
            configVersion = config.configVersion,
            timestampMs = timestamp,
            decision = FrameDecision.UNIQUE,
            reason = FrameEvaluationReason.FIRST_FRAME,
            hash = null,
            referenceHash = currentReference,
            hammingDistance = null,
            threshold = config.maxHammingDistance,
            forcedForward = false
        )

        if (!config.enabled) {
            framesAccepted++
            return base.copy(
                decision = FrameDecision.UNIQUE,
                reason = FrameEvaluationReason.DISABLED_PASSTHROUGH
            )
        }

        if (currentReference == null) {
            val hash = hasher.compute(frame)
            hashComputations++
            reference = hash
            lastForwardMs = timestamp
            lastForwardFrameId = frameId
            framesAccepted++
            return base.copy(
                reason = FrameEvaluationReason.FIRST_FRAME,
                hash = hash,
                referenceHash = null,
                hammingDistance = null,
                forcedForward = false
            )
        }

        val elapsed = timestamp - lastForwardMs

        // SAFETY CEILING (§16): even visually-identical frames
        // are forced through so a static screen keeps
        // refreshing its evidence.
        if (elapsed >= config.maximumForwardIntervalMs) {
            val hash = hasher.compute(frame)
            hashComputations++
            val distance =
                HammingDistance.between(hash, currentReference)
            hashDistanceSum += distance
            reference = hash
            lastForwardMs = timestamp
            lastForwardFrameId = frameId
            framesAccepted++
            forcedForwardCount++
            return base.copy(
                decision = FrameDecision.UNIQUE,
                reason = FrameEvaluationReason.FORCED_FORWARD,
                hash = hash,
                hammingDistance = distance,
                forcedForward = true
            )
        }

        // MINIMUM FRAME INTERVAL (§7): frames inside the
        // window are rejected cheaply, WITHOUT hashing.
        if (elapsed < config.minimumFrameIntervalMs) {
            framesRejected++
            return base.copy(
                decision = FrameDecision.DUPLICATE,
                reason = FrameEvaluationReason.TIME_WINDOW_GATED,
                hash = null,
                hammingDistance = null
            )
        }

        // NORMAL SIMILARITY COMPARISON.
        hashComputations++
        val hash = hasher.compute(frame)
        val distance =
            HammingDistance.between(hash, currentReference)
        hashDistanceSum += distance

        val decision = classifyDistance(
            distance,
            config.maxHammingDistance
        )

        if (decision.shouldForward) {
            reference = hash
            lastForwardMs = timestamp
            lastForwardFrameId = frameId
            framesAccepted++
        } else {
            framesRejected++
        }

        return base.copy(
            decision = decision,
            reason = FrameEvaluationReason.COMPARED,
            hash = hash,
            hammingDistance = distance,
            forcedForward = false
        )
    }

    /*
     * Pure classification of a Hamming distance given the
     * configured threshold.
     *
     * Boundary is INCLUSIVE for rejection:
     *   distance 0            -> DUPLICATE
     *   0 < distance <= max   -> SIMILAR
     *   distance > max        -> UNIQUE
     */
    fun classifyDistance(
        distance: Int,
        threshold: Int
    ): FrameDecision {
        require(distance >= 0) {
            "distance must be >= 0, got $distance"
        }
        require(threshold >= 0) {
            "threshold must be >= 0, got $threshold"
        }
        return when {
            distance == 0 -> FrameDecision.DUPLICATE
            distance <= threshold -> FrameDecision.SIMILAR
            else -> FrameDecision.UNIQUE
        }
    }

    /*
     * Clears reference state and counters. Reusable for the
     * next session.
     */
    @Synchronized
    fun reset() {
        reference = null
        lastForwardMs = 0L
        lastForwardFrameId = null
        framesSeen = 0L
        hashComputations = 0L
        framesAccepted = 0L
        framesRejected = 0L
        forcedForwardCount = 0L
        hashDistanceSum = 0L
    }

    /*
     * Number of hashes retained as reference. Always 0 or 1 -
     * the bounded-memory invariant (§19).
     */
    fun retainedReferenceCount(): Int =
        if (reference == null) 0 else 1

    /*
     * Immutable snapshot of current counters.
     */
    @Synchronized
    fun snapshot(): FrameDedupStats {
        return FrameDedupStats(
            framesSeen = framesSeen,
            hashComputations = hashComputations,
            framesAccepted = framesAccepted,
            framesRejected = framesRejected,
            forcedForwardCount = forcedForwardCount,
            hashDistanceSum = hashDistanceSum
        )
    }

    override fun toString(): String =
        "FrameDeduplicator(${config.configVersion})"
}