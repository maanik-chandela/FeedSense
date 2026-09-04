package com.example.feedsense.analysis.fusion

// --------------------------------
// PLATFORM EVIDENCE PROVIDER (Milestone 8B-1)
// --------------------------------
//
// Records which application is on screen. This is strictly
// CONTEXT: knowing the platform (e.g. Instagram / YouTube) must
// NEVER directly imply a category (8B-1 section 14). The platform
// only modulates candidate weighting via a separate, explicit
// step; the provider itself only surfaces the observed app name.

class PlatformEvidenceProvider(
    private val config: (FusionConfig) -> Double = { it.platformReliability }
) : EvidenceProvider {

    override val family: EvidenceFamily = EvidenceFamily.PLATFORM

    override fun provide(context: EvidenceContext): List<EvidenceRecord> {
        val reliability = config(context.config)

        val platforms = context.frames
            .mapNotNull { it.platform?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()

        if (platforms.isEmpty()) {
            return listOf(
                EvidenceRecord(
                    id = EvidenceRecord.id(
                        EvidenceFamily.PLATFORM,
                        "PLATFORM_UNDETECTED",
                        "no-platform"
                    ),
                    family = EvidenceFamily.PLATFORM,
                    type = "PLATFORM_UNDETECTED",
                    value = null,
                    valueSummary = "PLATFORM_UNDETECTED",
                    isRaw = true,
                    confidence = null,
                    strength = EvidenceStrength.NONE,
                    frameIds = emptyList(),
                    timestamp = "",
                    source = "PlatformEvidenceProvider",
                    reliability = reliability,
                    isVirtual = true
                )
            )
        }

        return platforms.map { platform ->
            // One record per distinct platform so repeated frames
            // of the same app are not counted as new evidence.
            val frames = context.frames
                .filter { it.platform?.trim() == platform }
                .map { it.frameId }
            EvidenceRecord(
                id = EvidenceRecord.id(
                    EvidenceFamily.PLATFORM,
                    "PLATFORM_APP",
                    platform
                ),
                family = EvidenceFamily.PLATFORM,
                type = "PLATFORM_APP",
                value = platform,
                valueSummary = platform,
                isRaw = true,
                confidence = null,
                strength = EvidenceStrength.MODERATE,
                frameIds = frames,
                timestamp = frames.firstOrNull()?.let {
                    context.frames.first { f -> f.frameId == it }.timestamp
                } ?: "",
                source = "PlatformEvidenceProvider",
                reliability = reliability
            )
        }
    }
}
