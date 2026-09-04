package com.example.feedsense.analysis.fusion

// --------------------------------
// TEXT EVIDENCE PROVIDER (Milestone 8B-1)
// --------------------------------
//
// Turns OCR text into RAW text evidence. The provider does NOT
// decide a category: it records that the frame showed text and
// lets CandidateGenerator read category/topic/tone candidates
// from it. This keeps RAW distinct from INTERPRETATION.
//
// When a frame has no OCR text the provider emits an explicit
// OCR_UNAVAILABLE virtual record so downstream layers can tell
// "no text was observed" apart from "content is unknown".

class TextEvidenceProvider(
    private val config: (FusionConfig) -> Double = { it.textReliability }
) : EvidenceProvider {

    override val family: EvidenceFamily = EvidenceFamily.TEXT

    override fun provide(context: EvidenceContext): List<EvidenceRecord> {
        val reliability = config(context.config)
        val records = mutableListOf<EvidenceRecord>()

        val ocrFrames = context.frames.filter { it.hasOcr }
        if (ocrFrames.isEmpty()) {
            records += unavailable(reliability)
            return records
        }

        records += ocrFrames.mapNotNull { frame ->
            val text = frame.ocrText?.trim().orEmpty()
            if (text.isEmpty()) {
                null
            } else {
                EvidenceRecord(
                    id = EvidenceRecord.id(
                        EvidenceFamily.TEXT,
                        "OCR_TEXT",
                        summaryOf(text)
                    ),
                    family = EvidenceFamily.TEXT,
                    type = "OCR_TEXT",
                    value = text,
                    valueSummary = summaryOf(text),
                    isRaw = true,
                    confidence = null,
                    strength = textStrength(text, context.config),
                    frameIds = listOf(frame.frameId),
                    timestamp = frame.timestamp,
                    source = "TextEvidenceProvider",
                    reliability = reliability
                )
            }
        }

        return records
    }

    private fun unavailable(reliability: Double): EvidenceRecord =
        EvidenceRecord(
            id = EvidenceRecord.id(
                EvidenceFamily.TEXT,
                "OCR_UNAVAILABLE",
                "no-ocr-text"
            ),
            family = EvidenceFamily.TEXT,
            type = "OCR_UNAVAILABLE",
            value = null,
            valueSummary = "OCR_UNAVAILABLE",
            isRaw = true,
            confidence = null,
            strength = EvidenceStrength.NONE,
            frameIds = emptyList(),
            timestamp = "",
            source = "TextEvidenceProvider",
            reliability = reliability,
            isVirtual = true
        )

    /**
     * Longer, keyword-rich text is stronger OCR evidence.
     * STRONG only for text with enough real subject-matter words.
     */
    private fun textStrength(
        text: String,
        c: FusionConfig
    ): EvidenceStrength {
        if (text.length < c.minInformativeTextChars) {
            return EvidenceStrength.WEAK
        }
        val informative = text
            .split(Regex("\\s+"))
            .count { it.length >= 3 }
        return when {
            informative >= 8 -> EvidenceStrength.STRONG
            informative >= 3 -> EvidenceStrength.MODERATE
            else -> EvidenceStrength.WEAK
        }
    }

    companion object {
        /**
         * Compact content summary for provenance/logging only.
         */
        fun summaryOf(text: String): String {
            val t = text.trim()
            return if (t.length <= 24) t else t.take(21) + "..."
        }
    }
}
