package com.example.feedsense.analysis.fusion.temporal

// --------------------------------
// TEMPORAL FUSION PREDICTION (Milestone 8B-2)
// --------------------------------
//
// The structured, deterministic prediction emitted by the
// temporal fusion model. Extends the 8B-1 FusionPrediction with
// temporal-specific fields (8B-2 section 82).
//
// Every prediction carries the temporal model version so 8A can
// compare legacy, fusion-v1, and temporal-v1 predictions on
// identical items (8B-2 section 82).

data class TemporalFusionPrediction(
    // Core prediction (compatible with 8B-1 FusionPrediction)
    val primaryCategory: String?,
    val secondaryCategories: List<String>,
    val confidence: Double,
    val uncertainty: String,
    val topic: String?,
    val tone: String?,
    val contentType: String?,
    val platform: String?,
    val interactionState: String,

    // Temporal-specific fields
    val temporalConsistency: TemporalConsistency,
    val categoryStability: CategoryStability,
    val temporalConflictLevel: TemporalConflictLevel,
    val mixedContentIndicator: Boolean,
    val transitionDetected: Boolean,
    val transitionCount: Int,
    val representativeFrameOutlier: Boolean,

    // Category trajectories (compact summary)
    val categoryTrajectories: Map<String, CategoryTrajectorySummary>,

    // Trace
    val trace: TemporalFusionTrace,

    // Versioning
    val modelVersion: String,
    val configVersion: String,
    val modelState: String
) {
    val hasDecidedPrimary: Boolean
        get() = primaryCategory != null &&
            (uncertainty == "CONFIDENT" ||
                uncertainty == "LOW_CONFIDENCE")
}

/**
 * Compact summary of a category's trajectory, suitable for
 * storage and diagnostics (8B-2 section 23).
 */
data class CategoryTrajectorySummary(
    val category: String,
    val supportDurationSeconds: Double,
    val frameCount: Int,
    val persistenceLevel: String,
    val averageScore: Double,
    val stable: Boolean
)
