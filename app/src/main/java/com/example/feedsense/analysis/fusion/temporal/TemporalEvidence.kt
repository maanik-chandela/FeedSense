package com.example.feedsense.analysis.fusion.temporal

// --------------------------------
// TEMPORAL EVIDENCE (Milestone 8B-2)
// --------------------------------
//
// Structured representation of evidence derived from temporal
// analysis of a frame sequence. This is NOT raw frame evidence
// (which lives in 8B-1's EvidenceRecord); this is the temporal
// DIMENSION: persistence, transitions, consistency, trajectory
// and deduplication across time (8B-2 section 8).

/**
 * Temporal consistency states (8B-2 section 13).
 */
enum class TemporalConsistency(val label: String) {
    NONE("NONE"),
    WEAK("WEAK"),
    MODERATE("MODERATE"),
    STRONG("STRONG"),
    CONFLICTING("CONFLICTING")
}

/**
 * Category stability over time (8B-2 section 24).
 */
enum class CategoryStability(val label: String) {
    STABLE("STABLE"),
    MOSTLY_STABLE("MOSTLY_STABLE"),
    UNSTABLE("UNSTABLE"),
    CHANGING("CHANGING"),
    UNKNOWN("UNKNOWN")
}

/**
 * Temporal conflict level (8B-2 section 44).
 */
enum class TemporalConflictLevel(val label: String) {
    NONE("NONE"),
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH")
}

/**
 * A candidate category's trajectory across the timeline.
 */
data class CategoryTrajectory(
    val category: String,
    val supportDurationMs: Long,
    val informativeFrameCount: Int,
    val continuousSupport: Boolean,
    val maxConsecutiveFrames: Int,
    val averageScore: Double,
    val scoreTrajectory: List<Double>
) {
    val durationSeconds: Double
        get() = supportDurationMs.toDouble() / 1000.0
}

/**
 * A detected transition between content segments.
 */
data class TemporalTransition(
    val fromCategory: String?,
    val toCategory: String?,
    val atFrameIndex: Int,
    val atTimestampMs: Long,
    val isSharp: Boolean,
    val signals: List<String>
)

/**
 * OCR text aggregation across time.
 */
data class TemporalOcrSummary(
    val uniqueTexts: List<String>,
    val evolutionChains: List<List<String>>,
    val deduplicatedCount: Int,
    val totalCount: Int,
    val semanticTopics: List<String>
)

/**
 * The complete temporal evidence for a FeedItem.
 * This is the core output of the temporal analysis layer.
 */
data class TemporalEvidence(
    val timeline: FrameTimeline,

    // Per-category temporal support
    val trajectories: Map<String, CategoryTrajectory>,

    // Overall temporal state
    val temporalConsistency: TemporalConsistency,
    val categoryStability: CategoryStability,
    val temporalConflictLevel: TemporalConflictLevel,

    // Transitions
    val transitions: List<TemporalTransition>,
    val transitionDetected: Boolean,
    val segmentBoundaries: List<Int>,

    // OCR temporal aggregation
    val ocrSummary: TemporalOcrSummary,

    // Multi-content support
    val primaryCategory: String?,
    val secondaryCategories: List<String>,
    val mixedContentIndicator: Boolean,

    // Representative frame validation
    val representativeFrameAgreement: Boolean,
    val representativeFrameOutlier: Boolean,

    // Uncertainty
    val uncertaintyReason: String?,
    val informationQualityNote: String
)
