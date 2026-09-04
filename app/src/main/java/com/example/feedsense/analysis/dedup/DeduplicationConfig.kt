package com.example.feedsense.analysis.dedup

/*
 * Milestone 8B-3.
 *
 * Configuration for frame deduplication and adaptive sampling.
 *
 * Every configuration value has:
 *   - documented meaning
 *   - safe default
 *   - test coverage
 *
 * This class is immutable and deterministic: the same
 * parameters always produce the same behavior.
 */
data class DeduplicationConfig(

    /*
     * Master switch. When false, all frames pass through
     * without deduplication. Useful for debugging or when
     * deduplication is not desired.
     */
    val enabled: Boolean = true,

    /*
     * Maximum Hamming distance (in bits) for a frame to be
     * classified as DEFINITELY_SAME.
     *
     * Distance 0 means the perceptual hashes are identical.
     * This is the strictest threshold.
     *
     * Safe default: 0 (only exact perceptual matches).
     */
    val definitelySameThreshold: Int = 0,

    /*
     * Maximum Hamming distance for PROBABLY_SAME.
     *
     * Frames within this range may be discarded under safety
     * constraints (force-keep, etc.). Frames beyond this
     * range are UNCERTAIN or DIFFERENT and always retained.
     *
     * Must be >= definitelySameThreshold.
     *
     * Safe default: 5 (out of 64 bits for dHash 8x8).
     */
    val nearDuplicateThreshold: Int = 5,

    /*
     * Minimum interval between frame evaluations in
     * milliseconds.
     *
     * The system will not evaluate frames more frequently
     * than this, even under high visual change.
     *
     * Safe default: 1000 (1 frame/sec).
     */
    val minimumSamplingIntervalMs: Long = 1000L,

    /*
     * Maximum interval between frame evaluations in
     * milliseconds.
     *
     * The system will not wait longer than this between
     * evaluations, even when the screen is stable.
     *
     * Safe default: 10000 (10 seconds).
     */
    val maximumSamplingIntervalMs: Long = 10000L,

    /*
     * Force-keep interval in milliseconds.
     *
     * Even if the screen appears unchanged, a frame is
     * retained at least this often to capture subtle
     * temporal changes:
     *   - video movement
     *   - captions / subtitles
     *   - progress bars
     *   - counters
     *   - animations
     *   - small UI changes
     *   - interaction indicators
     *
     * Safe default: 15000 (15 seconds).
     */
    val forceKeepIntervalMs: Long = 15000L
) {

    init {
        require(
            definitelySameThreshold >= 0
        ) {
            "definitelySameThreshold must be >= 0"
        }

        require(
            nearDuplicateThreshold >= definitelySameThreshold
        ) {
            "nearDuplicateThreshold must be >= definitelySameThreshold"
        }

        require(
            minimumSamplingIntervalMs > 0
        ) {
            "minimumSamplingIntervalMs must be > 0"
        }

        require(
            maximumSamplingIntervalMs >= minimumSamplingIntervalMs
        ) {
            "maximumSamplingIntervalMs must be >= minimumSamplingIntervalMs"
        }

        require(
            forceKeepIntervalMs > 0
        ) {
            "forceKeepIntervalMs must be > 0"
        }
    }

    companion object {

        /*
         * Disabled configuration: all frames pass through
         * without deduplication.
         */
        val DISABLED =
            DeduplicationConfig(
                enabled = false
            )

        /*
         * Production default configuration.
         */
        val DEFAULT =
            DeduplicationConfig()

        /*
         * Conservative configuration: wider thresholds,
         * more retention. Safer for research completeness
         * at the cost of fewer deduplication savings.
         */
        val CONSERVATIVE =
            DeduplicationConfig(
                nearDuplicateThreshold = 8,
                forceKeepIntervalMs = 10000L
            )
    }
}
