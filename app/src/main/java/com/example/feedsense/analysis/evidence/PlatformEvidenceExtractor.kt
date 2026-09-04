package com.example.feedsense.analysis.evidence

import com.example.feedsense.analysis.PlatformDetector
import java.io.File

/*
 * Milestone 8B-5.
 *
 * Platform evidence extractor.
 *
 * Converts PlatformDetector results into evidence.
 * Does NOT create a second platform detector.
 * Reuses the existing PlatformDetector.
 *
 * Important:
 *   - Platform evidence strengthens interpretation
 *   - Platform evidence must NOT automatically determine
 *     content category
 *   - Instagram + sports keywords = SPORTS, not INSTAGRAM
 *
 * Evidence produced:
 *   - PLATFORM with detected platform name
 *   - Quality based on detection confidence
 *   - Platform-specific category priors as metadata
 *
 * Limitations:
 *   - Platform detection depends on OCR text
 *   - Some platforms may not be detected
 *   - Platform priors are heuristic, not learned
 */
class PlatformEvidenceExtractor : EvidenceExtractor {

    private val platformDetector = PlatformDetector()

    override fun extract(
        frameFile: File,
        frameWidth: Int,
        frameHeight: Int,
        existingText: String?,
        existingPlatform: String?
    ): List<Evidence> {

        // Reuse existing platform detection
        val platform =
            existingPlatform
                ?: platformDetector.detect(
                    existingText
                )

        if (platform.isNullOrBlank()) {
            return listOf(
                Evidence(
                    type = EvidenceType.PLATFORM,
                    source = EvidenceSource.PLATFORM,
                    quality = EvidenceQuality.NONE,
                    value = "no_platform",
                    metadata = mapOf(
                        "platformDetected" to "false"
                    )
                )
            )
        }

        // --------------------------------
        // PLATFORM EVIDENCE
        // --------------------------------

        val priors =
            PLATFORM_PRIORS[platform.lowercase()]
                ?: emptyMap()

        val quality = when {
            priors.isNotEmpty() ->
                EvidenceQuality.HIGH
            else ->
                EvidenceQuality.MEDIUM
        }

        val supporting =
            priors.keys.toList()

        return listOf(
            Evidence(
                type = EvidenceType.PLATFORM,
                source = EvidenceSource.PLATFORM,
                quality = quality,
                value = platform,
                supportingCategories = supporting,
                metadata = mapOf(
                    "platformDetected" to "true",
                    "platformName" to platform,
                    "priors" to priors.toString()
                )
            )
        )
    }

    companion object {

        /*
         * Platform priors: which categories a platform
         * is known for. These are soft priors, not
         * deterministic rules.
         *
         * Platform evidence strengthens these categories
         * but does not force them.
         */
        private val PLATFORM_PRIORS =
            mapOf(
                "instagram" to mapOf(
                    "lifestyle" to 0.3,
                    "fashion" to 0.2,
                    "food" to 0.2,
                    "entertainment" to 0.2
                ),
                "youtube" to mapOf(
                    "education" to 0.3,
                    "entertainment" to 0.3,
                    "gaming" to 0.2,
                    "technology" to 0.2
                ),
                "tiktok" to mapOf(
                    "entertainment" to 0.3,
                    "comedy" to 0.2,
                    "music" to 0.2,
                    "lifestyle" to 0.2
                ),
                "facebook" to mapOf(
                    "news" to 0.3,
                    "lifestyle" to 0.2,
                    "entertainment" to 0.2
                ),
                "reddit" to mapOf(
                    "technology" to 0.3,
                    "gaming" to 0.2,
                    "news" to 0.2
                ),
                "linkedin" to mapOf(
                    "business" to 0.4,
                    "technology" to 0.3
                ),
                "pinterest" to mapOf(
                    "fashion" to 0.3,
                    "food" to 0.3,
                    "lifestyle" to 0.2
                ),
                "twitch" to mapOf(
                    "gaming" to 0.5,
                    "entertainment" to 0.3
                )
            )
    }
}
