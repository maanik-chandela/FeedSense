package com.example.feedsense.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.util.UUID

// --------------------------------
// EVALUATION ITEM (Milestone 8A-1)
// --------------------------------
//
// The unit of research evaluation. One row per quantity
// that has been (or will be) rigorously evaluated across
// the three-part separation:
//
//   A. AI prediction  (see AiPredictionRecord)
//   B. Human ground truth (see GroundTruth)
//   C. Evaluation result (see EvaluationRecord)
//
// This table is the join point between the existing data
// model (FeedItem) and the new, dedicated evaluation
// layer. It never mutates the FeedItem; it only links to
// it so the evaluation can be reproduced later.
//
// datasetVersion:
//   When a set of evaluated items is curated into a frozen
//   training/evaluation cohort, that cohort is assigned a
//   dataset version here (and on the resulting
//   EvaluationRecord). Null while an item is still being
//   evaluated and has not been snapshotted into a dataset.
//   This is what prevents silent dataset drift between
//   model generations.
//
// dataChart / dataProvenance:
//   - AI_PREDICTED for the prediction snapshot
//   - USER_CONFIRMED for human ground truth

@Entity(
    tableName = "evaluation_items",
    indices = [
        Index(
            name = "idx_evaluation_items_feed_item",
            value = ["feedItemId"]
        ),
        Index(
            name = "idx_evaluation_items_session",
            value = ["sessionId"]
        ),
        Index(
            name = "idx_evaluation_items_dataset",
            value = ["datasetVersion"]
        ),
        Index(
            name = "idx_evaluation_items_status",
            value = ["evaluationStatus"]
        )
    ]
)
data class EvaluationItem(

    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val feedItemId: String,

    val sessionId: String,

    val projectId: String? = null,

    /*
     * The AI model version that produced the prediction
     * being evaluated. Preserved from the FeedItem at
     * enqueue time so the evaluation stays reproducible
     * even if the FeedItem later changes.
     */
    val modelVersion: String? = null,

    /*
     * Curated dataset version this item belongs to, or
     * null while it has not yet been snapshotted into a
     * cohort. Set as part of dataset curation (8A-2).
     */
    val datasetVersion: String? = null,

    /*
     * Annotation status (8A-1 field reused as the annotation
     * status to avoid a duplicate status field - Step 17):
     *
     *   NOT_EVALUATED       -> UNREVIEWED
     *   PARTIALLY_EVALUATED -> IN_PROGRESS
     *   EVALUATED           -> REVIEWED
     *   DISPUTED            -> DISPUTED
     *
     * The UI maps these to the 8A-2 vocabulary.
     */
    val evaluationStatus: String = STATUS_NOT_EVALUATED,

    val createdAt: LocalDateTime = LocalDateTime.now(),

    val enqueuedAt: LocalDateTime = LocalDateTime.now(),

    val completedAt: LocalDateTime? = null
) {

    companion object {

        const val STATUS_NOT_EVALUATED =
            "NOT_EVALUATED"

        const val STATUS_PARTIALLY_EVALUATED =
            "PARTIALLY_EVALUATED"

        const val STATUS_EVALUATED =
            "EVALUATED"

        /*
         * Human review could not reach a single conclusion
         * (e.g. a genuine disagreement between annotators, or
         * the evaluator flagged the item for later
         * adjudication). Stored as a plain string value on
         * the existing TEXT column - no schema change.
         */
        const val STATUS_DISPUTED =
            "DISPUTED"

        val VALID_STATUSES = setOf(
            STATUS_NOT_EVALUATED,
            STATUS_PARTIALLY_EVALUATED,
            STATUS_EVALUATED,
            STATUS_DISPUTED
        )
    }
}
