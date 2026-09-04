package com.example.feedsense.analysis.fusion

// --------------------------------
// INTERACTION EVIDENCE PROVIDER (Milestone 8B-1)
// --------------------------------
//
// Records visible interaction affordances (like/comment/share
// buttons and the like). The 8B-1 model distinguishes whether a
// UI affordance is merely PRESENT on screen versus whether the
// user is ACTIVELY interacting with it (8B-1 section 15).
//
// Our existing InteractionDetector can only say which affordances
// are visible on the OCR/screen text, not whether the user is
// actively using them, so this provider is honest: it labels that
// state as UI_PRESENT (or UI_UNKNOWN when nothing was detected).
// It never fabricates an ACTIVE state it cannot observe.

class InteractionEvidenceProvider(
    private val config: (FusionConfig) -> Double = { it.interactionReliability }
) : EvidenceProvider {

    override val family: EvidenceFamily = EvidenceFamily.INTERACTION

    companion object {
        const val STATE_PRESENT = "UI_PRESENT"
        const val STATE_ACTIVE = "UI_ACTIVE"
        const val STATE_UNKNOWN = "UI_UNKNOWN"
    }

    override fun provide(context: EvidenceContext): List<EvidenceRecord> {
        val reliability = config(context.config)

        val allSignals = context.frames
            .flatMap { it.interactionSignals }
            .distinct()

        if (allSignals.isEmpty()) {
            return listOf(
                EvidenceRecord(
                    id = EvidenceRecord.id(
                        EvidenceFamily.INTERACTION,
                        "INTERACTION_UNKNOWN",
                        STATE_UNKNOWN
                    ),
                    family = EvidenceFamily.INTERACTION,
                    type = "INTERACTION_UNKNOWN",
                    value = null,
                    valueSummary = STATE_UNKNOWN,
                    isRaw = true,
                    confidence = null,
                    strength = EvidenceStrength.NONE,
                    frameIds = emptyList(),
                    timestamp = "",
                    source = "InteractionEvidenceProvider",
                    reliability = reliability,
                    isVirtual = true
                )
            )
        }

        // We only observe affordances being visible; we cannot
        // assert the user is actively engaging each one. Mark the
        // state as PRESENT and note the affordances visible.
        val frames = context.frames
            .filter { it.interactionSignals.isNotEmpty() }
            .map { it.frameId }

        return listOf(
            EvidenceRecord(
                id = EvidenceRecord.id(
                    EvidenceFamily.INTERACTION,
                    "INTERACTION_PRESENT",
                    allSignals.sorted().joinToString(",")
                ),
                family = EvidenceFamily.INTERACTION,
                type = "INTERACTION_PRESENT",
                value = allSignals.sorted().joinToString(","),
                valueSummary = allSignals.sorted().take(3).joinToString(","),
                isRaw = true,
                confidence = null,
                strength =
                    if (allSignals.size >= 2) EvidenceStrength.MODERATE
                    else EvidenceStrength.WEAK,
                frameIds = frames,
                timestamp = frames.firstOrNull()?.let {
                    context.frames.first { f -> f.frameId == it }.timestamp
                } ?: "",
                source = "InteractionEvidenceProvider",
                reliability = reliability
            )
        )
    }
}
