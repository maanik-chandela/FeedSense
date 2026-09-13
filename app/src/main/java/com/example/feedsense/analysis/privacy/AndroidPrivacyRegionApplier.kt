package com.example.feedsense.analysis.privacy

import android.content.Context
import java.io.File

/*
 * Milestone 8B-10.
 *
 * Default Android rasterizer applier.
 *
 * Reuses the 8B-4 PrivacySanitizer (bitmap-based redact /
 * pixelation) so 8B-10 layers ON TOP of the existing stack
 * instead of duplicating it. The applier:
 *
 *   1. Builds a PrivacySanitizationConfig from the POLICY.
 *   2. Runs the existing rasterizer on the typed regions.
 *   3. Normalizes the outcome (changed / truncated).
 *
 * Decoding failures (e.g. content that cannot be decoded,
 * which strongly suggests a security-sensitive surface) are
 * surfaced as `truncated`, enabling the EvidenceSanitizer to
 * report CAPTURE_BLOCKED.
 */
class AndroidPrivacyRegionApplier(
    private val context: Context,
    private val policy: PrivacyPolicy = defaultPrivacyPolicy()
) : PrivacyRegionApplier {

    override fun apply(
        inputFile: File,
        regions: List<PrivacyRegion>,
        outputFile: File
    ): ApplyOutcome {

        if (regions.isEmpty()) {
            return ApplyOutcome(
                changed = false,
                truncated = false,
                regionCount = 0
            )
        }

        val config = PrivacySanitizationConfig(
            enabled = true,
            // Only the typed regions are rasterized; the
            // default geometric presets are already mapped
            // into the hierarchy by EvidenceSanitizer.
            protectedRegions =
                regions.map { it.toProtectedRegion() },
            redactionMode = policy.redactionMode,
            blurStrength = policy.blurStrength.coerceIn(1, 100),
            sanitizationVersion =
                PrivacySanitizationVersion.SANITIZER
        )

        val sanitizer = PrivacySanitizer(
            context = context,
            config = config
        )

        val result = sanitizer.sanitize(
            frameFile = inputFile,
            outputDir = outputFile.parentFile
        )

        return when (result.status) {
            SanitizationStatus.SANITIZED -> {
                val produced = result.transformedFramePath
                    ?.let { File(it) }
                if (produced != null && produced.exists()) {
                    produced.copyTo(
                        target = outputFile,
                        overwrite = true
                    )
                    produced.delete()
                    ApplyOutcome(
                        changed = true,
                        truncated = false,
                        regionCount = result.regionCount
                    )
                } else {
                    ApplyOutcome(
                        changed = false,
                        truncated = true,
                        regionCount = 0
                    )
                }
            }

            SanitizationStatus.UNCHANGED -> ApplyOutcome(
                changed = false,
                truncated = false,
                regionCount = 0
            )

            else -> {
                // Decode failures imply a security-sensitive
                // or otherwise unanalyzable surface.
                ApplyOutcome(
                    changed = false,
                    truncated = true,
                    regionCount = 0
                )
            }
        }
    }
}