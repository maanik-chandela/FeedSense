package com.example.feedsense.analysis.dedup

import com.example.feedsense.analysis.PerceptualHash
import java.io.File

/*
 * Milestone 8B-3.
 *
 * Low-cost perceptual hash engine.
 *
 * Wraps the existing dHash implementation with a clean
 * abstraction for:
 *   - image normalization (via PerceptualHash)
 *   - perceptual representation (64-bit dHash)
 *   - comparison (Hamming distance)
 *   - relationship classification
 *
 * Algorithm: dHash (difference hash), 8x8 grid, 64 bits.
 * Distance: Hamming distance (number of differing bits).
 *
 * This is an efficiency optimization and does NOT
 * establish semantic equivalence between frames.
 *
 * Visually similar frames can differ semantically.
 * Two frames of a person speaking may produce nearly
 * identical hashes while meaningful temporal information
 * changes (captions, expressions, context).
 *
 * Terminology: "low-cost" / "lightweight" / "computationally
 * cheap" / "inexpensive relative to neural inference".
 * NOT "zero-compute" / "free computation".
 */
class PerceptualHashEngine(
    private val hasher: PerceptualHash = PerceptualHash()
) {

    /*
     * Compute the perceptual hash of an image file.
     *
     * Returns null when:
     *   - the file cannot be decoded
     *   - the image is too small for the hash grid
     *   - the image is corrupt
     *
     * This is a fail-open design: callers should retain
     * the frame on hash failure.
     */
    fun computeHash(
        file: File
    ): String? {

        return try {
            hasher.compute(file)
        } catch (_: Exception) {
            null
        }
    }

    /*
     * Compute hash from raw pixel data.
     *
     * Pure JVM: unit-testable without an Android device.
     */
    fun computeHashFromPixels(
        width: Int,
        height: Int,
        pixels: IntArray
    ): String? {

        return hasher.computeFromPixels(
            width,
            height,
            pixels
        )
    }

    /*
     * Compute Hamming distance between two hashes.
     *
     * Returns null when either hash is null or they
     * are not comparable (different lengths).
     */
    fun computeDistance(
        first: String?,
        second: String?
    ): Int? {

        return hasher.hammingDistance(first, second)
    }

    /*
     * Classify the visual relationship between two frames
     * based on their perceptual hashes and configuration.
     *
     * Returns UNCERTAIN when either hash is missing or
     * computation fails (fail-open).
     */
    fun classifyRelationship(
        hash1: String?,
        hash2: String?,
        config: DeduplicationConfig =
            DeduplicationConfig.DEFAULT
    ): RetentionDecision {

        if (hash1 == null || hash2 == null) {
            return RetentionDecision.UNCERTAIN
        }

        val distance =
            computeDistance(hash1, hash2)
                ?: return RetentionDecision.UNCERTAIN

        return classifyByDistance(distance, config)
    }

    /*
     * Classify a Hamming distance into a retention
     * decision using the given configuration.
     */
    fun classifyByDistance(
        distance: Int,
        config: DeduplicationConfig =
            DeduplicationConfig.DEFAULT
    ): RetentionDecision {

        return when {
            distance <= config.definitelySameThreshold ->
                RetentionDecision.DEFINITELY_SAME

            distance <= config.nearDuplicateThreshold ->
                RetentionDecision.PROBABLY_SAME

            else ->
                RetentionDecision.DIFFERENT
        }
    }
}
