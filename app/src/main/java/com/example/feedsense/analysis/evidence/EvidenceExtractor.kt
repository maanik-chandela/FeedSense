package com.example.feedsense.analysis.evidence

import java.io.File

/*
 * Milestone 8B-5.
 *
 * Evidence extractor interface.
 *
 * Defines the contract for all evidence extractors.
 * Each extractor produces Evidence items from a
 * captured frame.
 *
 * Design principles:
 *   - Single responsibility per extractor
 *   - Injectable for testing
 *   - Fail-safe: empty list on error
 *   - Deterministic: same input → same output
 *   - No sensitive data in logs
 *
 * Future extractors:
 *   - SensitiveTextDetector
 *   - FaceDetector
 *   - ChatRegionDetector
 *   - NotificationContentDetector
 */
interface EvidenceExtractor {

    /*
     * Extract evidence from a frame.
     *
     * @param frameFile The captured frame image.
     * @param frameWidth Width in pixels (0 if unknown).
     * @param frameHeight Height in pixels (0 if unknown).
     * @param existingText OCR text if already extracted.
     *   Allows extractors to reuse OCR without re-running.
     * @param existingPlatform Platform if already detected.
     * @return List of extracted evidence items.
     *   Empty list if none extracted or on error.
     */
    fun extract(
        frameFile: File,
        frameWidth: Int = 0,
        frameHeight: Int = 0,
        existingText: String? = null,
        existingPlatform: String? = null
    ): List<Evidence>
}

/*
 * No-op extractor. Returns empty list.
 * Used when a specific extraction is disabled.
 */
class NoOpEvidenceExtractor : EvidenceExtractor {
    override fun extract(
        frameFile: File,
        frameWidth: Int,
        frameHeight: Int,
        existingText: String?,
        existingPlatform: String?
    ): List<Evidence> = emptyList()
}

/*
 * Composite extractor. Runs multiple extractors
 * and merges results. Failures in individual
 * extractors are swallowed (fail-safe).
 */
class CompositeEvidenceExtractor(
    private val extractors: List<EvidenceExtractor>
) : EvidenceExtractor {

    override fun extract(
        frameFile: File,
        frameWidth: Int,
        frameHeight: Int,
        existingText: String?,
        existingPlatform: String?
    ): List<Evidence> {

        return extractors.flatMap { extractor ->
            try {
                extractor.extract(
                    frameFile,
                    frameWidth,
                    frameHeight,
                    existingText,
                    existingPlatform
                )
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
