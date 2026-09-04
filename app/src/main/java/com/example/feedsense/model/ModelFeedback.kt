package com.example.feedsense.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.feedsense.analysis.CategoryCatalog
import java.time.LocalDateTime
import java.util.UUID

// --------------------------------
// MODEL FEEDBACK
// --------------------------------
//
// Milestone 7K (Parts 1 and 4).
//
// One row per user/system confirmation or correction of
// an AI classification. This is the local "training
// example" dataset: every row keeps BOTH the original
// prediction and the corrected/validated truth so model
// performance can be measured later.
//
// The original prediction is NEVER destroyed. What the
// AI said (originalCategory/Confidence/Topic/Tone and
// modelVersion) and what the human confirmed
// (corrected*) are stored side by side.
//
// Safety (Part 15): this dataset only receives rows that
// a human confirmed (SOURCE_USER). Cloud output alone
// never becomes a trusted example through this path.
//
// correctionSource values:
//
//   USER           human correction in the review screen
//   CLOUD_REFERENCE teacher model agreed/confirmed
//   LOCAL_MODEL    the local model re-confirmed its own
//                  prediction (self-evaluation)
//   SYSTEM         an internal rule/system correction
//

@Entity(tableName = "model_feedback")
data class ModelFeedback(

    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val frameId: String?,

    val feedItemId: String?,

    val sessionId: String,

    // --------------------------------
    // LEARNING EXAMPLE CONTEXT
    // --------------------------------

    val platform: String? = null,

    val visibleText: String? = null,

    val interactionSignals: List<String> = emptyList(),

    // --------------------------------
    // ORIGINAL AI PREDICTION (PRESERVED)
    // --------------------------------

    val originalCategory: String? = null,

    val originalConfidence: Double? = null,

    val originalTopic: String? = null,

    val originalTone: String? = null,

    val modelVersion: String? = null,

    // --------------------------------
    // CORRECTED / VALIDATED TRUTH
    // --------------------------------

    val correctedCategory: String? = null,

    val correctedTopic: String? = null,

    val correctedTone: String? = null,

    val correctionSource: String,

    val confidenceAfterCorrection: Double? = null,

    // --------------------------------
    // AGREEMENT (FOR CALIBRATION)
    // --------------------------------
    //
    // Whether the confirmed value matched the original
    // prediction. topicAgreement / toneAgreement are
    // null when there was no topic/tone to compare.

    val categoryAgreement: Boolean,

    val topicAgreement: Boolean? = null,

    val toneAgreement: Boolean? = null,

    val createdAt: LocalDateTime = LocalDateTime.now()
) {

    companion object {

        const val SOURCE_USER =
            "USER"

        const val SOURCE_CLOUD_REFERENCE =
            "CLOUD_REFERENCE"

        const val SOURCE_LOCAL_MODEL =
            "LOCAL_MODEL"

        const val SOURCE_SYSTEM =
            "SYSTEM"

        /*
         * Pure builder used by the repository. Kept as a
         * companion function so the agreement logic is
         * unit-testable without Room.
         */
        fun fromCorrection(
            reference: LabeledReference,
            correctedCategory: String,
            correctedTopic: String?,
            correctedTone: String?,
            correctionSource: String
        ): ModelFeedback {

            return ModelFeedback(
                frameId = reference.frameId,
                feedItemId = reference.feedItemId,
                sessionId = reference.sessionId,
                platform = reference.platform,
                visibleText = reference.visibleText,
                interactionSignals =
                    reference.interactionSignals,
                originalCategory = reference.aiCategory,
                originalConfidence = reference.aiConfidence,
                originalTopic = reference.topic,
                originalTone = reference.tone,
                modelVersion = reference.modelVersion,
                correctedCategory = correctedCategory,
                correctedTopic = clean(correctedTopic),
                correctedTone = clean(correctedTone),
                correctionSource = correctionSource,
                confidenceAfterCorrection = 1.0,
                categoryAgreement = categoryAgreementOf(
                    reference.aiCategory,
                    correctedCategory
                ),
                topicAgreement = textAgreementOf(
                    reference.topic,
                    correctedTopic
                ),
                toneAgreement = textAgreementOf(
                    reference.tone,
                    correctedTone
                )
            )
        }

        private fun clean(
            value: String?
        ): String? {
            return value
                ?.trim()
                ?.takeIf {
                    it.isNotEmpty()
                }
        }

        private fun categoryAgreementOf(
            original: String?,
            corrected: String
        ): Boolean {

            if (original == null) {
                return false
            }

            return CategoryCatalog
                .normalize(original) ==
                    CategoryCatalog
                        .normalize(corrected)
        }

        /*
         * Topic/tone agreement:
         *
         * - both blank   -> null (nothing to compare)
         * - AI had one and the user kept it -> true
         * - AI had none, user added one    -> false
         * - both present, differ           -> false
         */
        private fun textAgreementOf(
            original: String?,
            corrected: String?
        ): Boolean? {

            val originalClean = clean(original)
            val correctedClean = clean(corrected)

            return when {
                correctedClean == null ->
                    if (originalClean == null) {
                        null
                    } else {
                        true
                    }

                originalClean == null -> false

                else ->
                    originalClean.equals(
                        correctedClean,
                        ignoreCase = true
                    )
            }
        }
    }
}
