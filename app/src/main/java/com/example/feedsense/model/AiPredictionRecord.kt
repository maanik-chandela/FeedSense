package com.example.feedsense.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.util.UUID

// --------------------------------
// AI PREDICTION RECORD (Milestone 8A-1)
// --------------------------------
//
// An IMMUTABLE snapshot of what the AI predicted for an
// evaluated item. Captured from the FeedItem at the moment
// the item is enqueued for evaluation.
//
// Why an immutable snapshot and not a reference to
// feed_items?
//
//   The existing review flow (submitFeedItemCorrection)
//   overwrites feed_items.category/topic/tone when a human
//   corrects an uncertain item. The feed_items row is
//   therefore NOT a reliable record of the original AI
//   prediction. To answer "what did the AI predict, and
//   was it right?" we must freeze the prediction at the
//   moment evaluation begins, independent of later edits
//   or deletions.
//
//   The frame-level JSON in captured_frames.analysisResult
//   is preserved by the pipeline, but it is not ergonomic
//   to query per feed item. This table materializes the
//   predicted dimensions in normalized columns so the
//   evaluation layer can compare them directly against
//   human ground truth.
//
// Multi-label content is supported two ways:
//   - secondaryCategories : ranked secondary labels
//   - categoryScores      : per-category confidence map
//
// Only ever inserted; never updated once written.

@Entity(
    tableName = "ai_predictions",
    indices = [
        Index(
            name = "idx_ai_predictions_evaluation_item",
            value = ["evaluationItemId"]
        ),
        Index(
            name = "idx_ai_predictions_model_version",
            value = ["modelVersion"]
        )
    ]
)
data class AiPredictionRecord(

    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val evaluationItemId: String,

    val predictedAt: LocalDateTime = LocalDateTime.now(),

    /*
     * Provenance: where this prediction came from.
     * Matches the frozen AI result `source` /
     * AnalysisSource (LOCAL / CLOUD).
     */
    val source: String? = null,

    val modelVersion: String? = null,

    // --------------------------------
    // PRIMARY LABEL
    // --------------------------------

    val category: String? = null,

    val categoryDomain: String? = null,

    val confidence: Double? = null,

    // --------------------------------
    // MULTI-LABEL
    // --------------------------------

    val secondaryCategories: List<String> = emptyList(),

    val categoryScores: Map<String, Double> = emptyMap(),

    // --------------------------------
    // OTHER PREDICTED DIMENSIONS
    // --------------------------------

    val platform: String? = null,

    val contentType: String? = null,

    val durationSeconds: Int = 0,

    val skipped: Boolean = false,

    val interactionSignals: List<String> = emptyList(),

    val topic: String? = null,

    val tone: String? = null,

    val uncertaintyLevel: String = FeedItem.UNCERTAINTY_LOW,

    val needsReview: Boolean = false,

    /*
     * Audit chain: which FeedItem and evaluation records
     * this snapshot was derived from, for reproducibility.
     */
    val feedItemId: String? = null
)
