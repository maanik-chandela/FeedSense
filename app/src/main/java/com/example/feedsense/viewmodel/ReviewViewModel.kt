package com.example.feedsense.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.LabeledReference
import com.example.feedsense.model.ModelFeedback
import com.example.feedsense.repository.ModelFeedbackRepository
import com.example.feedsense.repository.ReferenceRepository
import com.example.feedsense.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

// --------------------------------
// REVIEW VIEW MODEL
// --------------------------------
//
// Milestone 7B.
//
// Exposes the review queue and turns human labels
// into validated reference data.
//
// Milestone 7K (Part 3):
//
// - submitLabel now accepts corrected topic/tone and
//   records the correction in the model_feedback
//   learning dataset (SOURCE_USER).
// - confirmAi: "AI was correct" - the prediction is
//   confirmed and becomes a positive learning example.
// - skip: "Skip review" - the reference leaves the queue
//   (SKIPPED) without becoming validated training data.
// - getItemById exposes duration/platform/interactions
//   so the review card can show context.
//

class ReviewViewModel(
    private val referenceRepository: ReferenceRepository,
    private val sessionRepository: SessionRepository,
    private val feedbackRepository: ModelFeedbackRepository
) : ViewModel() {

    fun getPendingReviews(): Flow<List<LabeledReference>> {
        return referenceRepository.getPendingReviews()
    }

    fun getValidatedCount(
        onResult: (Int) -> Unit
    ) {

        viewModelScope.launch {

            onResult(
                referenceRepository
                    .getValidatedCount()
            )
        }
    }

    fun getValidatedAgreementCount(
        onResult: (Int) -> Unit
    ) {

        viewModelScope.launch {

            onResult(
                referenceRepository
                    .getValidatedAgreementCount()
            )
        }
    }

    fun getItemById(
        feedItemId: String,
        onResult: (FeedItem?) -> Unit
    ) {

        viewModelScope.launch {

            onResult(
                sessionRepository
                    .getFeedItemById(feedItemId)
            )
        }
    }

    /*
     * Milestone 7K (Part 3). "AI was correct": confirm
     * the prediction without editing it. The reference is
     * validated with the AI's own category and a positive
     * learning example is recorded.
     */
    fun confirmAi(
        referenceId: String
    ) {

        viewModelScope.launch {

            val reference =
                referenceRepository
                    .getById(referenceId)
                    ?: return@launch

            val confirmedCategory =
                reference.aiCategory ?: return@launch

            submitLabel(
                referenceId = referenceId,
                label = confirmedCategory,
                correctedTopic = reference.topic,
                correctedTone = reference.tone
            )
        }
    }

    /*
     * Milestone 7K (Part 3). "Skip review": the reference
     * leaves the queue without becoming validated or
     * rejected training data.
     */
    fun skip(
        referenceId: String
    ) {

        viewModelScope.launch {

            referenceRepository.skipReference(
                referenceId
            )
        }
    }

    fun submitLabel(
        referenceId: String,
        label: String,
        correctedTopic: String? = null,
        correctedTone: String? = null
    ) {

        viewModelScope.launch {

            val reference =
                referenceRepository
                    .getById(referenceId)
                    ?: return@launch

            // Human label becomes validated data.
            referenceRepository.submitHumanLabel(
                referenceId = referenceId,
                label = label
            )

            /*
             * Milestone 7D. Item-level references (and
             * backfilled frame-level references) are
             * linked to a FeedItem: the human correction
             * is applied to the item and clears its
             * review flag, so it no longer counts as
             * uncertain.
             */
            reference.feedItemId?.let { feedItemId ->
                sessionRepository.submitFeedItemCorrection(
                    feedItemId = feedItemId,
                    label = label,
                    correctedTopic = correctedTopic,
                    correctedTone = correctedTone
                )
            }

            /*
             * Milestone 7K (Parts 1 and 4). Every human
             * correction becomes a learning example: the
             * original AI prediction is preserved next to
             * the confirmed truth so performance can be
             * measured later.
             */
            feedbackRepository.submitCorrection(
                reference = reference,
                correctedCategory = label,
                correctedTopic = correctedTopic,
                correctedTone = correctedTone,
                correctionSource =
                    ModelFeedback.SOURCE_USER
            )

            // Frame leaves the review queue.
            sessionRepository.updateFrameAnalysisStatus(
                frameId = reference.frameId,
                status = STATUS_REVIEWED
            )
        }
    }

    fun reject(
        referenceId: String
    ) {

        viewModelScope.launch {

            referenceRepository.rejectReference(
                referenceId
            )
        }
    }

    companion object {

        const val STATUS_REVIEWED =
            "REVIEWED"
    }
}
