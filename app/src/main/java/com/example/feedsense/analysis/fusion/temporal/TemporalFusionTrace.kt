package com.example.feedsense.analysis.fusion.temporal

// --------------------------------
// TEMPORAL FUSION TRACE (Milestone 8B-2)
// --------------------------------
//
// Extended diagnostic trace for the temporal prediction. Exposes
// compact structured information (8B-2 section 76) without raw
// screenshots or full OCR transcripts (8B-2 section 77).
//
// Extends the 8B-1 FusionTrace concept with temporal-specific
// dimensions.

data class TemporalFusionTrace(
    // Basic frame info
    val framesConsidered: Int,
    val informativeFrames: Int,
    val temporalDurationSeconds: Double,

    // Temporal state
    val dominantCategory: String?,
    val temporalConsistency: String,
    val categoryStability: String,
    val categoryConflict: Boolean,
    val temporalConflictLevel: String,

    // Transitions
    val transitionDetected: Boolean,
    val transitionCount: Int,
    val segmentBoundaries: List<Int>,

    // Representative frame
    val representativeFrameAgreement: Boolean,
    val representativeFrameOutlier: Boolean,

    // OCR temporal
    val ocrConsistency: String,
    val ocrDeduplicatedCount: Int,
    val ocrTotalCount: Int,
    val ocrEvolutionChains: Int,

    // Visual
    val visualConsistency: String,

    // Uncertainty
    val uncertainty: String,
    val uncertaintyReason: String?,

    // Model version
    val modelVersion: String,
    val informationQuality: String,

    // Multi-content
    val mixedContent: Boolean,
    val secondaryCategories: List<String>
) {
    companion object {
        const val TEMPORAL_CONSISTENT = "CONSISTENT"
        const val TEMPORAL_INSUFFICIENT = "INSUFFICIENT"
        const val TEMPORAL_CONFLICT = "CONFLICT"
        const val VISUAL_UNAVAILABLE = "UNAVAILABLE"
    }
}
