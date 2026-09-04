package com.example.feedsense.analysis.privacy

import java.io.File

/*
 * Milestone 8B-4.
 *
 * Privacy region detector interface.
 *
 * Defines the contract for automatic sensitive-region
 * detection. Future implementations can detect:
 *
 *   - SystemUIRegionDetector
 *   - SensitiveTextDetector (OCR-based)
 *   - FaceDetector
 *   - ChatRegionDetector
 *   - NotificationContentDetector
 *   - UserDefinedPrivacyRegion
 *
 * The detector returns a list of detected regions
 * which are then passed to the PrivacySanitizer
 * for transformation.
 *
 * Design principles:
 *   - Separated from sanitizer (single responsibility)
 *   - Injectable for testing
 *   - Fail-safe: empty list on error
 *   - No sensitive content in logs
 */
interface PrivacyRegionDetector {

    /*
     * Detect sensitive regions in a captured frame.
     *
     * @param frameFile The frame image file.
     * @param frameWidth Width in pixels.
     * @param frameHeight Height in pixels.
     * @return List of detected protected regions.
     *   Empty list if none detected or on error.
     */
    fun detect(
        frameFile: File,
        frameWidth: Int,
        frameHeight: Int
    ): List<ProtectedRegion>
}

/*
 * No-op detector. Always returns empty list.
 * Used when automatic detection is disabled.
 */
class NoOpDetector : PrivacyRegionDetector {
    override fun detect(
        frameFile: File,
        frameWidth: Int,
        frameHeight: Int
    ): List<ProtectedRegion> = emptyList()
}

/*
 * Composite detector. Runs multiple detectors
 * and merges results.
 */
class CompositeDetector(
    private val detectors: List<PrivacyRegionDetector>
) : PrivacyRegionDetector {

    override fun detect(
        frameFile: File,
        frameWidth: Int,
        frameHeight: Int
    ): List<ProtectedRegion> {

        return detectors.flatMap { detector ->
            try {
                detector.detect(
                    frameFile,
                    frameWidth,
                    frameHeight
                )
            } catch (_: Exception) {
                emptyList()
            }
        }
    }
}
