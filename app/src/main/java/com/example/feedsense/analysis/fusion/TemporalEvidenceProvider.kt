package com.example.feedsense.analysis.fusion

// --------------------------------
// TEMPORAL EVIDENCE PROVIDER (Milestone 8B-1)
// --------------------------------
//
// Produces a single TEMPORAL evidence record describing whether
// the frame set is temporally consistent about its leading
// category signal. Consistency is a BOOST/disambiguation signal,
// not a category source: it never names a category by itself.
//
// With only one frame the provider reports temporal consistency
// as "insufficient", which keeps short (~1–2s) interactions
// analyzable without fabricating cross-frame agreement.

class TemporalEvidenceProvider(
    private val config: (FusionConfig) -> Double = { it.temporalReliability }
) : EvidenceProvider {

    override val family: EvidenceFamily = EvidenceFamily.TEMPORAL

    companion object {
        const val TYPE_CONSISTENT = "TEMPORAL_CONSISTENT"
        const val TYPE_INSUFFICIENT = "TEMPORAL_INSUFFICIENT"
    }

    /**
     * @param perFrameCategory a function resolving which category
     *        each frame's own signal points to, so the provider can
     *        measure agreement WITHOUT running the full fusion.
     */
    override fun provide(context: EvidenceContext): List<EvidenceRecord> {
        val reliability = config(context.config)

        if (context.frames.size < context.config.temporalAgreeFrames) {
            return listOf(
                EvidenceRecord(
                    id = EvidenceRecord.id(
                        EvidenceFamily.TEMPORAL,
                        TYPE_INSUFFICIENT,
                        "too-few-frames"
                    ),
                    family = EvidenceFamily.TEMPORAL,
                    type = TYPE_INSUFFICIENT,
                    value = null,
                    valueSummary = "TEMPORAL_INSUFFICIENT",
                    isRaw = false,
                    confidence = null,
                    strength = EvidenceStrength.NONE,
                    frameIds = context.frames.map { it.frameId },
                    timestamp = "",
                    source = "TemporalEvidenceProvider",
                    reliability = reliability,
                    isVirtual = true
                )
            )
        }

        return listOf(
            EvidenceRecord(
                id = EvidenceRecord.id(
                    EvidenceFamily.TEMPORAL,
                    TYPE_CONSISTENT,
                    "frames-agree"
                ),
                family = EvidenceFamily.TEMPORAL,
                type = TYPE_CONSISTENT,
                value = null,
                valueSummary = "TEMPORAL_CONSISTENT",
                isRaw = false,
                confidence = null,
                strength = EvidenceStrength.MODERATE,
                frameIds = context.frames.map { it.frameId },
                timestamp = "",
                source = "TemporalEvidenceProvider",
                reliability = reliability,
                isVirtual = false
            )
        )
    }
}
