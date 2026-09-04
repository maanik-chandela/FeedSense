package com.example.feedsense.analysis.evidence

/*
 * Milestone 8B-5.
 *
 * Evidence source provenance.
 *
 * Tracks which extractor produced an evidence item
 * and which version of that extractor was used.
 *
 * This is critical for research reproducibility:
 * a prediction must be traceable to the evidence
 * source and version that produced it.
 *
 * Example:
 *   EvidenceSource(
 *       extractorName = "OcrEvidenceExtractor",
 *       extractorVersion = "evidence-v1"
 *   )
 */
data class EvidenceSource(
    val extractorName: String,
    val extractorVersion: String
) {
    companion object {

        const val VERSION = "evidence-v1"

        val OCR = EvidenceSource(
            extractorName = "OcrEvidenceExtractor",
            extractorVersion = VERSION
        )

        val PLATFORM = EvidenceSource(
            extractorName =
                "PlatformEvidenceExtractor",
            extractorVersion = VERSION
        )

        val VISUAL = EvidenceSource(
            extractorName =
                "VisualEvidenceExtractor",
            extractorVersion = VERSION
        )

        val LAYOUT = EvidenceSource(
            extractorName =
                "LayoutEvidenceExtractor",
            extractorVersion = VERSION
        )

        val INTERACTION = EvidenceSource(
            extractorName =
                "InteractionEvidenceExtractor",
            extractorVersion = VERSION
        )

        val TEMPORAL = EvidenceSource(
            extractorName =
                "TemporalEvidenceExtractor",
            extractorVersion = VERSION
        )
    }
}
