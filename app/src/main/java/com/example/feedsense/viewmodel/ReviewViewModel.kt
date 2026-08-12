package com.example.feedsense.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feedsense.model.LabeledReference
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

class ReviewViewModel(
    private val referenceRepository: ReferenceRepository,
    private val sessionRepository: SessionRepository
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

    fun submitLabel(
        referenceId: String,
        label: String
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