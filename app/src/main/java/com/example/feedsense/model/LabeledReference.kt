package com.example.feedsense.model

import androidx.room.Entity
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

@Entity(tableName = "labeled_references")
data class LabeledReference(

    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val frameId: String,

    val sessionId: String,

    val filePath: String,

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

        const val LABEL_SOURCE_HUMAN =
            "HUMAN"

        const val LABEL_SOURCE_CLOUD =
            "CLOUD"
    }
}
