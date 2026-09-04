package com.example.feedsense.analysis.dedup

import java.io.File

/*
 * Milestone 8B-3.
 *
 * Frame filtering engine.
 *
 * Combines perceptual hashing, configuration, force-keep
 * safety, and retention decisions into a single filtering
 * step. This is the main entry point for the
 * deduplication system.
 *
 * Design principles:
 *   - Fail-open: any error retains the frame
 *   - Force-keep: periodic retention regardless of visual
 *     similarity
 *   - Deterministic: same inputs always produce the same
 *     decision
 *   - Low-cost: perceptual hashing is inexpensive relative
 *     to neural inference
 *
 * Limitations:
 *   - Visually similar frames can differ semantically
 *   - Subtle text changes may be missed
 *   - Overlays can complicate hashing
 *   - Video can produce similar frames over time
 *   - Thresholds may depend on content and device
 *   - Perceptual similarity is NOT semantic similarity
 */
class FrameFilterEngine(
    private val hashEngine: PerceptualHashEngine =
        PerceptualHashEngine(),
    private val hashFunction: ((File) -> String?)? =
        null,
    private val config: DeduplicationConfig =
        DeduplicationConfig.DEFAULT
) {

    /*
     * Timestamp (ms) of the last retained frame.
     * Updated only when a frame is retained.
     * 0 = no frame retained yet.
     */
    private var lastRetainedTimestampMs: Long = 0L

    /*
     * Perceptual hash of the last retained frame.
     */
    private var lastRetainedHash: String? = null

    /*
     * Evaluate a new frame and decide whether it should
     * be retained for expensive downstream analysis.
     *
     * @param frameFile  The frame image file to evaluate.
     * @param currentTimestampMs  Current system time in
     *   milliseconds.
     * @return FrameFilterResult with the decision and
     *   diagnostics.
     */
    fun evaluateFrame(
        frameFile: File,
        currentTimestampMs: Long
    ): FrameFilterResult {

        // --------------------------------
        // DISABLED CHECK
        // --------------------------------

        if (!config.enabled) {
            return FrameFilterResult(
                decision =
                    RetentionDecision.DIFFERENT,
                shouldRetain = true,
                currentHash = null,
                previousHash = lastRetainedHash,
                hammingDistance = null,
                reason = "DEDUP_DISABLED"
            )
        }

        // --------------------------------
        // FIRST FRAME
        // --------------------------------

        if (lastRetainedTimestampMs == 0L) {

            val hash = computeHash(frameFile)

            updateRetainedState(
                hash,
                currentTimestampMs
            )

            return FrameFilterResult(
                decision =
                    RetentionDecision.DIFFERENT,
                shouldRetain = true,
                currentHash = hash,
                previousHash = null,
                hammingDistance = null,
                reason = "FIRST_FRAME"
            )
        }

        // --------------------------------
        // FORCE-KEEP CHECK
        // --------------------------------
        //
        // Even if the screen appears unchanged,
        // periodically retain a frame to capture subtle
        // temporal changes (video movement, captions,
        // progress bars, animations).

        val forceKeepElapsed =
            currentTimestampMs -
                    lastRetainedTimestampMs

        if (
            forceKeepElapsed >=
            config.forceKeepIntervalMs
        ) {

            val hash = computeHash(frameFile)

            updateRetainedState(
                hash,
                currentTimestampMs
            )

            return FrameFilterResult(
                decision =
                    RetentionDecision.PROBABLY_SAME,
                shouldRetain = true,
                currentHash = hash,
                previousHash = lastRetainedHash,
                hammingDistance = null,
                reason = "FORCE_KEEP_INTERVAL",
                forceKept = true
            )
        }

        // --------------------------------
        // PERCEPTUAL COMPARISON
        // --------------------------------

        val currentHash = computeHash(frameFile)

        if (currentHash == null) {

            // Fail-open: cannot hash, retain
            updateRetainedState(
                null,
                currentTimestampMs
            )

            return FrameFilterResult(
                decision =
                    RetentionDecision.UNCERTAIN,
                shouldRetain = true,
                currentHash = null,
                previousHash = lastRetainedHash,
                hammingDistance = null,
                reason = "HASH_FAILURE"
            )
        }

        val distance =
            hashEngine.computeDistance(
                currentHash,
                lastRetainedHash
            )

        if (distance == null) {

            // Cannot compare, retain
            updateRetainedState(
                currentHash,
                currentTimestampMs
            )

            return FrameFilterResult(
                decision =
                    RetentionDecision.UNCERTAIN,
                shouldRetain = true,
                currentHash = currentHash,
                previousHash = lastRetainedHash,
                hammingDistance = null,
                reason = "DISTANCE_FAILURE"
            )
        }

        // --------------------------------
        // CLASSIFY RELATIONSHIP
        // --------------------------------

        val decision =
            hashEngine.classifyRelationship(
                currentHash,
                lastRetainedHash,
                config
            )

        val shouldRetain =
            decision == RetentionDecision.UNCERTAIN ||
                    decision ==
                    RetentionDecision.DIFFERENT

        /*
         * Save the previous hash before updating state,
         * so the result accurately reflects what came
         * before this frame.
         */
        val previousHashSnapshot =
            lastRetainedHash

        if (shouldRetain) {
            updateRetainedState(
                currentHash,
                currentTimestampMs
            )
        }

        return FrameFilterResult(
            decision = decision,
            shouldRetain = shouldRetain,
            currentHash = currentHash,
            previousHash = previousHashSnapshot,
            hammingDistance = distance,
            reason = decision.name
        )
    }

    /*
     * Reset state for a new capture session.
     */
    fun reset() {
        lastRetainedTimestampMs = 0L
        lastRetainedHash = null
    }

    private fun computeHash(
        frameFile: File
    ): String? {

        return try {
            hashFunction?.invoke(frameFile)
                ?: hashEngine.computeHash(frameFile)
        } catch (_: Exception) {
            null
        }
    }

    private fun updateRetainedState(
        hash: String?,
        timestampMs: Long
    ) {
        lastRetainedHash = hash
        lastRetainedTimestampMs = timestampMs
    }
}
