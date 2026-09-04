package com.example.feedsense.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.util.UUID

// --------------------------------
// LABELED REFERENCE
// --------------------------------
//
// Milestone 7B.
//
// One row per uncertain frame that needs (or has
// received) a validated label.
//
// Purpose:
//
// - Review queue for frames the local pipeline could
//   not confidently classify.
// - Safe storage for cloud reference results.
// - Training/evaluation dataset for the local model.
//
// Poisoning protection:
//
// Cloud results are stored with labelSource = CLOUD
// and validationStatus = PENDING. They only become
// usable training data once a human validates them
// (labelSource becomes HUMAN).
//
// Each row keeps modelVersion and aiSource so the
// dataset can be evaluated per model over time.
//

/*
 * Milestone 7T. Indexes cover the hot paths: the review
 * queue (validationStatus), per-frame pending checks
 * (frameId) and item-level reference assignment
 * (sessionId + feedItemId).
 */
@Entity(
    tableName = "labeled_references",
    indices = [
        Index(
            name = "idx_labeled_references_status",
            value = ["validationStatus"]
        ),
        Index(
            name = "idx_labeled_references_frame",
            value = ["frameId"]
        ),
        Index(
            name = "idx_labeled_references_session_item",
            value = ["sessionId", "feedItemId"]
        )
    ]
)
data class LabeledReference(

    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val frameId: String,

    val sessionId: String,

    val filePath: String,

    /*
     * Milestone 7D.
     *
     * When set, this reference was created at the
     * FeedItem level (an uncertain content piece)
     * rather than a single frame. Validating it
     * corrects the FeedItem itself.
     */
    val feedItemId: String? = null,

    // --------------------------------
    // AI PREDICTION
    // --------------------------------

    val aiCategory: String?,

    val aiConfidence: Double?,

    val aiSource: String,

    val modelVersion: String?,

    // --------------------------------
    // CANDIDATE CATEGORIES
    // --------------------------------
    //
    // Shown to the reviewer so they do not have to
    // type a category from scratch.
    //

    val candidateCategories: List<String> = emptyList(),

    // --------------------------------
    // MILESTONE 7F (PARTS 7-8)
    // --------------------------------
    //
    // Rich context preserved with every reference so
    // the local dataset can later be searched by
    // category, topic, platform, text or model version
    // without re-analyzing the original frames.

    val platform: String? = null,

    val topic: String? = null,

    val tone: String? = null,

    val visibleText: String? = null,

    val aiReason: String? = null,

    val interactionSignals: List<String> = emptyList(),

    /*
     * Milestone 7G.
     *
     * The perceptual fingerprint of the frame (or the
     * representative frame of the feed item) this
     * reference came from. Lets the local retriever
     * find visually similar previous examples without
     * re-analyzing the original images.
     */
    val frameFingerprint: String? = null,

    // --------------------------------
    // LABEL / VALIDATION
    // --------------------------------

    val labelSource: String,

    val validationStatus: String,

    val validatedLabel: String? = null,

    val agreement: Boolean? = null,

    val createdAt: LocalDateTime = LocalDateTime.now(),

    val reviewedAt: LocalDateTime? = null
) {

    companion object {

        const val VALIDATION_PENDING =
            "PENDING"

        const val VALIDATION_VALIDATED =
            "VALIDATED"

        const val VALIDATION_REJECTED =
            "REJECTED"

        /*
         * Milestone 7K (Part 3). The reviewer chose
         * "Skip review": the reference is removed from the
         * active queue without becoming validated or
         * rejected training data. TEXT constant only -
         * no schema change.
         */
        const val VALIDATION_SKIPPED =
            "SKIPPED"

        const val LABEL_SOURCE_HUMAN =
            "HUMAN"

        const val LABEL_SOURCE_CLOUD =
            "CLOUD"
    }
}
