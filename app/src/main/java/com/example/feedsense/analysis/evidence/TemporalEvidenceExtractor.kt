package com.example.feedsense.analysis.evidence

import java.io.File

/*
 * Milestone 8B-5.
 *
 * Temporal evidence extractor.
 *
 * Exposes temporal evidence through the evidence layer.
 * FeedSense already knows frame timestamps, item
 * duration, frame count, and category transitions.
 *
 * Evidence signals:
 *   - Duration of content
 *   - Category stability over time
 *   - Category transitions
 *   - Frame count
 *
 * Design:
 *   - Converts existing temporal data to evidence
 *   - Does not modify segmentation logic
 *   - Deterministic: same timestamps → same evidence
 *
 * Limitations:
 *   - Requires temporal context (not frame-only)
 *   - Category transitions depend on prior classifications
 *   - Short items may have unreliable temporal signals
 */
class TemporalEvidenceExtractor : EvidenceExtractor {

    override fun extract(
        frameFile: File,
        frameWidth: Int,
        frameHeight: Int,
        existingText: String?,
        existingPlatform: String?
    ): List<Evidence> {

        // Temporal evidence requires context beyond
        // a single frame. In v1, we provide the interface
        // and a basic file-timestamp signal.

        val evidence = mutableListOf<Evidence>()

        // File modification time as basic temporal signal
        val lastModified = frameFile.lastModified()

        if (lastModified > 0) {
            evidence.add(
                Evidence(
                    type = EvidenceType.TEMPORAL,
                    source = EvidenceSource.TEMPORAL,
                    quality = EvidenceQuality.LOW,
                    value = "file_timestamp",
                    metadata = mapOf(
                        "lastModified" to
                                lastModified.toString(),
                        "frameFile" to
                                frameFile.name
                    )
                )
            )
        }

        return evidence
    }

    companion object {

        /*
         * Create temporal evidence from explicit
         * temporal context. Used when temporal data
         * is available from the session/segmentation
         * layer.
         */
        fun fromTemporalContext(
            durationMs: Long,
            frameCount: Int,
            categoryTransitions: Int = 0,
            dominantCategory: String? = null
        ): List<Evidence> {

            val evidence =
                mutableListOf<Evidence>()

            // Duration evidence
            val durationCategory = when {
                durationMs < 3000 ->
                    "very_short"
                durationMs < 15000 ->
                    "short"
                durationMs < 60000 ->
                    "medium"
                else ->
                    "long"
            }

            val durationQuality = when {
                durationMs < 3000 ->
                    EvidenceQuality.LOW
                durationMs < 15000 ->
                    EvidenceQuality.MEDIUM
                else ->
                    EvidenceQuality.HIGH
            }

            evidence.add(
                Evidence(
                    type = EvidenceType.TEMPORAL,
                    source = EvidenceSource.TEMPORAL,
                    quality = durationQuality,
                    value =
                        "duration:$durationCategory",
                    metadata = mapOf(
                        "durationMs" to
                                durationMs.toString(),
                        "durationCategory" to
                                durationCategory,
                        "frameCount" to
                                frameCount.toString()
                    )
                )
            )

            // Category stability
            if (categoryTransitions == 0 &&
                dominantCategory != null
            ) {
                evidence.add(
                    Evidence(
                        type = EvidenceType.TEMPORAL,
                        source = EvidenceSource.TEMPORAL,
                        quality =
                            EvidenceQuality.MEDIUM,
                        value = "category_stable",
                        supportingCategories =
                            listOf(dominantCategory),
                        metadata = mapOf(
                            "transitions" to "0",
                            "category" to
                                    dominantCategory
                        )
                    )
                )
            }

            return evidence
        }
    }
}
