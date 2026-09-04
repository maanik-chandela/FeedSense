package com.example.feedsense.analysis.fusion.temporal

// --------------------------------
// VISUAL FINGERPRINT (Milestone 8B-2)
// --------------------------------
//
// Interface for visual fingerprinting of frames. Supports
// fingerprint generation, similarity comparison and unavailable
// state (8B-2 section 18).
//
// No raw images are stored through this layer (8B-2 section 19).
// Fingerprints are compact derived representations.

/**
 * A compact visual fingerprint for a frame.
 */
data class VisualFingerprint(
    val hash: String,
    val width: Int = 0,
    val height: Int = 0,
    val algorithm: String = "simple-dct"
) {
    val isEmpty: Boolean get() = hash.isBlank()
}

/**
 * Result of comparing two fingerprints.
 */
data class FingerprintSimilarity(
    val similarity: Double,
    val isIdentical: Boolean,
    val algorithm: String
)

/**
 * Provider interface for visual fingerprinting. Implementations
 * should be lightweight and local (8B-2 section 19).
 */
interface VisualFingerprintProvider {

    /**
     * Generates a fingerprint for a frame's visual content.
     * Returns null when the fingerprint cannot be computed.
     */
    fun fingerprint(
        filePath: String,
        width: Int,
        height: Int
    ): VisualFingerprint?

    /**
     * Compares two fingerprints for similarity.
     */
    fun compare(
        a: VisualFingerprint,
        b: VisualFingerprint
    ): FingerprintSimilarity
}

/**
 * Default no-op implementation when no fingerprint library is
 * available. Reports everything as unavailable so the temporal
 * engine operates without visual fingerprinting (8B-2 section 67).
 */
class UnavailableFingerprintProvider : VisualFingerprintProvider {

    override fun fingerprint(
        filePath: String,
        width: Int,
        height: Int
    ): VisualFingerprint? = null

    override fun compare(
        a: VisualFingerprint,
        b: VisualFingerprint
    ): FingerprintSimilarity = FingerprintSimilarity(
        similarity = 0.0,
        isIdentical = false,
        algorithm = "unavailable"
    )
}

/**
 * Simple hash-based fingerprint provider. Uses a basic
 * representation of frame dimensions and a simple content
 * hash for lightweight comparison without external
 * dependencies (8B-2 section 18).
 *
 * This is NOT a perceptual hash; it is a minimal interface
 * that allows the architecture to plug in a real
 * perceptual-hash library later.
 */
class SimpleFingerprintProvider : VisualFingerprintProvider {

    override fun fingerprint(
        filePath: String,
        width: Int,
        height: Int
    ): VisualFingerprint? {
        if (width <= 0 || height <= 0) return null
        // Simple composite fingerprint from dimensions + path
        // hash. A real implementation would use DCT/pHash.
        val pathHash = filePath.hashCode().toUInt()
        val dimHash = ((width.toLong() shl 32) or height.toLong())
            .toULong()
        val composite = (pathHash.toULong() xor (dimHash shr 16))
        return VisualFingerprint(
            hash = "%016x".format(composite),
            width = width,
            height = height,
            algorithm = "simple-dim-hash"
        )
    }

    override fun compare(
        a: VisualFingerprint,
        b: VisualFingerprint
    ): FingerprintSimilarity {
        if (a.algorithm != b.algorithm) {
            return FingerprintSimilarity(
                similarity = 0.0,
                isIdentical = false,
                algorithm = "mismatched"
            )
        }
        val identical = a.hash == b.hash
        val similarity = if (identical) 1.0 else {
            // Simple Hamming-like ratio for hex strings.
            val matching = a.hash.zip(b.hash)
                .count { it.first == it.second }
            matching.toDouble() / maxOf(a.hash.length, b.hash.length)
        }
        return FingerprintSimilarity(
            similarity = similarity,
            isIdentical = identical,
            algorithm = a.algorithm
        )
    }
}
