package com.example.feedsense.analysis.evidence

import java.io.File

/*
 * Milestone 8B-5.
 *
 * Visual evidence extractor interface.
 *
 * Provides a clean interface for future visual evidence
 * extraction. The current implementation is lightweight
 * and does not add a heavyweight neural vision model.
 *
 * Potential future signals:
 *   - Image availability
 *   - Frame brightness
 *   - Dominant visual structure
 *   - Large text presence
 *   - Video-like layout
 *   - Face presence
 *   - Screen/UI characteristics
 *
 * Design:
 *   - Interface-first: ready for future implementations
 *   - Lightweight: no model inference in v1
 *   - Injectable: testable without Android
 *
 * Limitations:
 *   - v1 provides interface only, no visual analysis
 *   - Future versions may add lightweight heuristics
 *   - No neural vision model in this milestone
 */
class VisualEvidenceExtractor : EvidenceExtractor {

    override fun extract(
        frameFile: File,
        frameWidth: Int,
        frameHeight: Int,
        existingText: String?,
        existingPlatform: String?
    ): List<Evidence> {

        // --------------------------------
        // V1: INTERFACE ONLY
        // --------------------------------
        //
        // The visual evidence extractor provides the
        // interface for future visual analysis. In v1,
        // we record that the frame exists and its
        // dimensions, but do not perform visual analysis.

        val evidence = mutableListOf<Evidence>()

        // Frame availability
        evidence.add(
            Evidence(
                type = EvidenceType.VISUAL,
                source = EvidenceSource.VISUAL,
                quality = EvidenceQuality.LOW,
                value = "frame_available",
                metadata = mapOf(
                    "frameExists" to "true",
                    "frameWidth" to
                            frameWidth.toString(),
                    "frameHeight" to
                            frameHeight.toString()
                )
            )
        )

        // Basic brightness heuristic (very lightweight)
        if (frameWidth > 0 && frameHeight > 0) {
            val aspectRatio =
                frameWidth.toDouble() /
                        frameHeight.toDouble()

            val isVertical =
                aspectRatio < 0.75

            val isHorizontal =
                aspectRatio > 1.33

            val orientation = when {
                isVertical -> "portrait"
                isHorizontal -> "landscape"
                else -> "square"
            }

            evidence.add(
                Evidence(
                    type = EvidenceType.VISUAL,
                    source = EvidenceSource.VISUAL,
                    quality = EvidenceQuality.LOW,
                    value =
                        "orientation:$orientation",
                    metadata = mapOf(
                        "aspectRatio" to
                                String.format(
                                    "%.2f",
                                    aspectRatio
                                ),
                        "orientation" to orientation
                    )
                )
            )
        }

        return evidence
    }
}
