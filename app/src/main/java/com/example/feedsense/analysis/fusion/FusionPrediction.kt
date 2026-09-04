package com.example.feedsense.analysis.fusion

// --------------------------------
// FUSION PREDICTION (Milestone 8B-1)
// --------------------------------
//
// The structured, deterministic prediction emitted by Fusion V1.
// It keeps each dimension SEPARATE (category vs topic vs tone vs
// content type, 8B-1 section 36) and carries the model version so
// 8A can compare LEGACY vs FUSION on identical items without
// overwriting the legacy prediction (8B-1 section 4/55/56).

/**
 * Central uncertainty state (8B-1 section 28).
 */
enum class Uncertainty(
    val label: String
) {
    CONFIDENT("CONFIDENT"),
    LOW_CONFIDENCE("LOW_CONFIDENCE"),
    AMBIGUOUS("AMBIGUOUS"),
    INSUFFICIENT_EVIDENCE("INSUFFICIENT_EVIDENCE"),
    CONFLICTING_EVIDENCE("CONFLICTING_EVIDENCE")
}

/**
 * Central confidence band (8B-1 section 31).
 */
enum class ConfidenceBand(val label: String) {
    HIGH("HIGH"),
    MEDIUM("MEDIUM"),
    LOW("LOW"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromConfidence(
            confidence: Double,
            config: FusionConfig
        ): ConfidenceBand {
            return when {
                confidence >= config.bandHigh -> HIGH
                confidence >= config.bandMedium -> MEDIUM
                confidence >= config.bandLow -> LOW
                else -> UNKNOWN
            }
        }
    }
}

data class FusionPrediction(
    val primaryCategory: String?,
    val secondaryCategories: List<String>,
    val confidence: Double,
    val band: ConfidenceBand,
    val uncertainty: Uncertainty,
    val topic: String?,
    val tone: String?,
    val contentType: String?,
    val platform: String?,
    val interactionState: String,
    val modelVersion: String,
    val configVersion: String,
    val modelState: String,
    val trace: FusionTrace
) {

    val hasDecidedPrimary: Boolean
        get() = primaryCategory != null &&
            uncertainty == Uncertainty.CONFIDENT ||
            uncertainty == Uncertainty.LOW_CONFIDENCE
}
