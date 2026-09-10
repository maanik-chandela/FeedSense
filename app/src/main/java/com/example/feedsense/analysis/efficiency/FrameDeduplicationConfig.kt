package com.example.feedsense.analysis.efficiency

/*
 * Milestone 8B-11.
 *
 * Versioned configuration for the frame-deduplication layer.
 *
 * All tunables for this component live HERE - a single
 * controlled configuration point. Thresholds are never
 * scattered through the application.
 */

/*
 * Algorithm/behaviour version of the deduplication layer.
 *
 * Bump this constant whenever the decision semantics or the
 * configuration contract changes, so experiments produced by
 * different versions remain attributable.
 */
object FrameDedupVersion {
    const val DEDUP_VERSION = "dedup-v1"
}

/*
 * Immutable, deterministic configuration.
 *
 * Same config + same frames => same decisions (see determinism
 * tests).
 */
data class FrameDeduplicationConfig(
    /*
     * Master switch. When false every frame is forwarded
     * UNIQUE (pass-through) and no hashing is performed.
     */
    val enabled: Boolean = true,

    /*
     * Perceptual hash algorithm (dHash default).
     */
    val hashAlgorithm: FrameHashAlgorithm =
        FrameHashAlgorithm.DHASH,

    /*
     * Hash grid side length. Total hash length is
     * hashSize^2 bits (64 for the default 8x8).
     */
    val hashSize: Int = PerceptualHasher.DEFAULT_HASH_SIZE,

    /*
     * Maximum Hamming distance below which a frame is
     * considered NOT meaningfully changed.
     *
     * Boundary semantics (INCLUSIVE reject):
     *   distance 0           -> DUPLICATE  (reject)
     *   0 < distance <= max  -> SIMILAR    (reject)
     *   distance > max       -> UNIQUE     (forward)
     */
    val maxHammingDistance: Int = DEFAULT_MAX_HAMMING_DISTANCE,

    /*
     * Minimum elapsed time (ms) since the last accepted frame
     * before a new frame is eligible. Frames arriving sooner
     * are rejected cheaply WITHOUT hashing (TIME_WINDOW_GATED).
     *
     * Default 0 => pure visual-similarity behaviour.
     */
    val minimumFrameIntervalMs: Long = 0L,

    /*
     * Safety ceiling: maximum elapsed time (ms) since the last
     * accepted frame before the next frame is FORCED through
     * (UNIQUE, forcedForward = true) even if visually
     * identical. Guarantees a long static session still
     * refreshes its evidence (see §16 of the 8B-11 spec).
     */
    val maximumForwardIntervalMs: Long = DEFAULT_MAX_FORWARD_INTERVAL_MS,

    /*
     * Version of this config contract.
     */
    val configVersion: String = FrameDedupVersion.DEDUP_VERSION
) {

    init {
        require(hashSize in PerceptualHasher.MIN_HASH_SIZE..PerceptualHasher.MAX_HASH_SIZE) {
            "hashSize must be in [${PerceptualHasher.MIN_HASH_SIZE}, ${PerceptualHasher.MAX_HASH_SIZE}], got $hashSize"
        }
        require(maxHammingDistance >= 0) {
            "maxHammingDistance must be >= 0, got $maxHammingDistance"
        }
        require(minimumFrameIntervalMs >= 0) {
            "minimumFrameIntervalMs must be >= 0, got $minimumFrameIntervalMs"
        }
        require(maximumForwardIntervalMs >= minimumFrameIntervalMs) {
            "maximumForwardIntervalMs must be >= minimumFrameIntervalMs"
        }
    }

    companion object {

        /*
         * Research default. 10 differing bits of 64 is a
         * moderate, defensible default for dHash(8): small UI
         * motion and caption text usually stays at or below
         * this, and a real content transition crosses it.
         */
        const val DEFAULT_MAX_HAMMING_DISTANCE = 10

        /*
         * Safety refresh ceiling (8 seconds) for static
         * screens.
         */
        const val DEFAULT_MAX_FORWARD_INTERVAL_MS = 8000L

        /*
         * Production/research default configuration.
         */
        val DEFAULT = FrameDeduplicationConfig()

        /*
         * Pure similarity behaviour: no time-window gating and
         * no forced-forward ceiling.
         */
        val VISUAL_ONLY = FrameDeduplicationConfig(
            minimumFrameIntervalMs = 0L,
            maximumForwardIntervalMs = Long.MAX_VALUE
        )

        /*
         * Disabled pass-through.
         */
        val DISABLED = FrameDeduplicationConfig(enabled = false)

        /*
         * Strict configuration for evidence-preservation
         * experiments: only exact or near-exact duplicates are
         * suppressed.
         */
        val STRICT = FrameDeduplicationConfig(
            maxHammingDistance = 2
        )
    }
}