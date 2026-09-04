package com.example.feedsense.analysis.fusion

// --------------------------------
// EVIDENCE CONTEXT (Milestone 8B-1)
// --------------------------------
//
// The pure, immutable input to evidence providers and to the
// fusion engine. It bundles the few, already-available signals a
// frame (or a short clip of frames) carries WITHOUT invoking any
// cloud AI or repeating OCR.
//
// 8B-1 never re-runs OCR: it consumes whatever localStorage the
// pipeline already wrote. If OCR text is absent the caller
// members are left empty and the system records OCR_UNAVAILABLE,
// NOT CONTENT_IS_UNKNOWN (8B-1 section 13).

/**
 * One frame's already-collected signals.
 */
data class FrameSignals(
    val frameId: String,
    val timestamp: String,
    val ocrText: String? = null,
    val platform: String? = null,
    val interactionSignals: Set<String> = emptySet(),
    val existingCategory: String? = null,
    val existingConfidence: Double? = null,
    val contentType: String? = null,
    val topic: String? = null,
    val tone: String? = null,
    val visualSignals: Map<String, String> = emptyMap()
) {
    val hasOcr: Boolean get() = !ocrText.isNullOrBlank()
}

/**
 * The bundle consumed by providers and the engine.
 */
data class EvidenceContext(
    val frames: List<FrameSignals>,
    val config: FusionConfig = FusionConfig.DEFAULT
) {
    fun frame(frameId: String): FrameSignals? =
        frames.firstOrNull { it.frameId == frameId }

    companion object {
        fun single(frame: FrameSignals): EvidenceContext =
            EvidenceContext(listOf(frame))
    }
}

// Evidence provider contract (8B-1 section 8) ------------------

/**
 * A provider turns already-available signals into structured
 * EvidenceRecords for one evidence family. Providers are pure and
 * deterministic: the same context always yields the same records.
 */
interface EvidenceProvider {
    val family: EvidenceFamily

    /**
     * Produces evidence for the given context. Returns an empty
     * list when this family has no observation; a provider MUST
     * still emit an explicit "unavailable" virtual record when
     * the signal genuinely cannot be observed (see the visual
     * provider), so absence is never confused with a real answer.
     */
    fun provide(context: EvidenceContext): List<EvidenceRecord>
}
