package com.example.feedsense.analysis.evidence

import java.io.File

/*
 * Milestone 8B-5.
 *
 * Layout evidence extractor.
 *
 * Recognizes that UI structure itself is evidence.
 * Layout evidence is independent from platform evidence.
 *
 * Potential signals:
 *   - Vertical video layout
 *   - Full-screen content
 *   - Caption area presence
 *   - Creator/profile area
 *   - Progress indicator
 *   - Engagement controls
 *   - Comments region
 *
 * Design:
 *   - Geometry-based detection (not OCR-dependent)
 *   - Configurable region fractions
 *   - Independent from platform detection
 *
 * Limitations:
 *   - v1 uses basic heuristics
 *   - No app-specific layout detection
 *   - No floating window detection
 *   - Portrait/landscape not fully distinguished
 */
class LayoutEvidenceExtractor : EvidenceExtractor {

    override fun extract(
        frameFile: File,
        frameWidth: Int,
        frameHeight: Int,
        existingText: String?,
        existingPlatform: String?
    ): List<Evidence> {

        val evidence = mutableListOf<Evidence>()

        if (frameWidth <= 0 || frameHeight <= 0) {
            return evidence
        }

        // --------------------------------
        // ASPECT RATIO LAYOUT
        // --------------------------------

        val aspectRatio =
            frameWidth.toDouble() /
                    frameHeight.toDouble()

        val layoutType = when {
            aspectRatio < 0.6 ->
                "vertical_video"
            aspectRatio < 0.75 ->
                "tall_content"
            aspectRatio > 1.7 ->
                "wide_video"
            aspectRatio > 1.33 ->
                "landscape_content"
            else ->
                "standard_content"
        }

        evidence.add(
            Evidence(
                type = EvidenceType.LAYOUT,
                source = EvidenceSource.LAYOUT,
                quality = EvidenceQuality.MEDIUM,
                value = "layout:$layoutType",
                metadata = mapOf(
                    "layoutType" to layoutType,
                    "aspectRatio" to
                            String.format(
                                "%.2f",
                                aspectRatio
                            )
                )
            )
        )

        // --------------------------------
        // ENGAGEMENT AREA HEURISTIC
        // --------------------------------
        //
        // Vertical video layouts often have engagement
        // controls (like, comment, share) on the right
        // side. This is a heuristic based on common
        // short-form video UI patterns.

        if (aspectRatio < 0.75) {
            evidence.add(
                Evidence(
                    type = EvidenceType.LAYOUT,
                    source = EvidenceSource.LAYOUT,
                    quality = EvidenceQuality.LOW,
                    value =
                        "engagement_controls_likely",
                    metadata = mapOf(
                        "reason" to
                                "vertical_video_layout"
                    )
                )
            )
        }

        return evidence
    }
}
