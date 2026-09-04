package com.example.feedsense.model

// --------------------------------
// MODEL PERFORMANCE STATS
// --------------------------------
//
// Milestone 7G.
//
// On-demand aggregation of how the local classification
// pipeline is performing. Computed from the existing
// tables (feed_items + labeled_references) when the
// evaluation screen is opened - no schema changes, no
// background work.
//
// Terminology:
//
//   totalClassifications - every built FeedItem.
//   highConfidence      - items the pipeline was sure
//                         about (uncertaintyLevel HIGH).
//   uncertain           - items flagged for review plus
//                         frames still sitting in the
//                         review queue.
//   reviewed            - references a human acted on
//                         (validated or rejected).
//   correct             - human agreed with the AI.
//   corrected           - human disagreed (the AI was
//                         wrong and the correction is
//                         the new ground truth).
//   accuracy            - correct / validated * 100.
//

data class ConfusionRow(
    val predicted: String,
    val actual: String,
    val count: Int
)

data class ModelVersionPerformance(
    val modelVersion: String,
    val reviewed: Int,
    val correct: Int,
    val accuracy: Double
)

data class CategoryPerformance(
    val category: String,
    val reviewed: Int,
    val correct: Int,
    val accuracy: Double
)

data class PlatformPerformance(
    val platform: String,
    val reviewed: Int,
    val correct: Int,
    val accuracy: Double
)

data class ConfusionPair(
    val predicted: String,
    val actual: String,
    val count: Int
)

data class ModelPerformanceStats(
    val totalClassifications: Int,
    val highConfidence: Int,
    val uncertain: Int,
    val reviewed: Int,
    val validated: Int,
    val correct: Int,
    val corrected: Int,
    val rejected: Int,
    val accuracy: Double,
    val byModelVersion: List<ModelVersionPerformance>,
    val byCategory: List<CategoryPerformance>,
    val byPlatform: List<PlatformPerformance>,
    val confusion: List<ConfusionPair>,
    val topConfusion: ConfusionPair?,

    /*
     * Milestone 7K (Part 6): observed validation
     * statistics. These measure how often a human agreed
     * with the AI on the samples that WERE reviewed - a
     * calibration signal, not a scientific accuracy
     * claim.
     */
    val topicAccuracy: Double,
    val toneAccuracy: Double,
    val uncertaintyRate: Double,
    val feedbackCount: Int,

    /*
     * Milestone 7O. Cross-session user knowledge
     * (what the user actually watches and corrects).
     * Describes future-prediction personalization only;
     * historical sessions stay immutable.
     */
    val personalization: PersonalizationStats =
        PersonalizationStats.EMPTY,

    /*
     * Milestone 7R. Cloud-teacher economics and honesty
     * metrics:
     *
     *   cloudFallbackCount  - cloud-sourced analyses
     *                         (aiSource = 'CLOUD').
     *   cloudFallbackRate   - cloudFallback /
     *                         totalClassifications.
     *   estimatedCloudCost  - allowed cloud requests x
     *                         estimated cost per request.
     *   localAcceptanceRate - share of classifications
     *                         the local pipeline accepted
     *                         at HIGH confidence.
     *   reviewRate          - share flagged for review.
     *   correctionRate      - user corrections as a share
     *                         of all user feedback.
     *   interactionAccuracy - deliberately NULL until a
     *                         real interaction dataset
     *                         exists. "Not measured" is
     *                         the honest answer.
     */
    val cloudFallbackCount: Int = 0,
    val cloudFallbackRate: Double = 0.0,
    val estimatedCloudCostRupees: Double = 0.0,
    val localAcceptanceRate: Double = 0.0,
    val reviewRate: Double = 0.0,
    val correctionRate: Double = 0.0,
    val interactionAccuracy: Double? = null
)
