package com.example.feedsense.analysis.fusion

// --------------------------------
// VISUAL EVIDENCE PROVIDER (Milestone 8B-1)
// --------------------------------
//
// This is an explicit interface for vision-based evidence. The
// offline system has NO vision model, so this provider never
// fabricates an analysis from pixels (it must not pretend OCR is
// vision, 8B-1 section 13/16). It emits a single
// VISUAL_SIGNAL_UNAVAILABLE virtual record to make the absence a
// first-class, explicit signal rather than something silently
// missing.
//
// The provider is the seam where a future real vision model would
// plug in (8B-1 section 16).

class VisualEvidenceProvider(
    private val config: (FusionConfig) -> Double = { it.visualReliability }
) : EvidenceProvider {

    override val family: EvidenceFamily = EvidenceFamily.VISUAL

    companion object {
        const val UNAVAILABLE = "VISUAL_SIGNAL_UNAVAILABLE"
    }

    override fun provide(context: EvidenceContext): List<EvidenceRecord> {
        // No vision model exists in 8B-1. Any dirt-cheap future
        // adoption goes through this single method; today it
        // always reports the explicit "unavailable" state.
        return listOf(
            EvidenceRecord(
                id = EvidenceRecord.id(
                    EvidenceFamily.VISUAL,
                    "VISUAL_UNAVAILABLE",
                    UNAVAILABLE
                ),
                family = EvidenceFamily.VISUAL,
                type = "VISUAL_UNAVAILABLE",
                value = null,
                valueSummary = UNAVAILABLE,
                isRaw = false,
                confidence = null,
                strength = EvidenceStrength.NONE,
                frameIds = emptyList(),
                timestamp = "",
                source = "VisualEvidenceProvider",
                reliability = config(context.config),
                isVirtual = true
            )
        )
    }
}
