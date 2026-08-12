package com.example.feedsense.repository

import com.example.feedsense.dao.ReferenceDao
import com.example.feedsense.model.LabeledReference
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow

// --------------------------------
// REFERENCE REPOSITORY
// --------------------------------
//
// Milestone 7B.
//
// Manages the labeled reference / training dataset.
//
// - Pending rows form the review queue.
// - Human labels become VALIDATED reference data.
// - Cloud results are stored PENDING and only become
//   training data after human validation.
//

class ReferenceRepository(
    private val referenceDao: ReferenceDao
) {

    fun getPendingReviews(): Flow<List<LabeledReference>> {
        return referenceDao.getPendingReviews()
    }

    fun getReferencesForSession(
        sessionId: String
    ): Flow<List<LabeledReference>> {
        return referenceDao.getForSession(sessionId)
    }

    suspend fun getById(
        referenceId: String
    ): LabeledReference? {
        return referenceDao.getById(referenceId)
    }

    suspend fun addPendingReview(
        frameId: String,
        sessionId: String,
        filePath: String,
        aiCategory: String?,
        aiConfidence: Double?,
        aiSource: String,
        modelVersion: String?,
        candidateCategories: List<String>,
        labelSource: String
    ) {

        referenceDao.insert(
            LabeledReference(
                frameId = frameId,
                sessionId = sessionId,
                filePath = filePath,
                aiCategory = aiCategory,
                aiConfidence = aiConfidence,
                aiSource = aiSource,
                modelVersion = modelVersion,
                candidateCategories = candidateCategories,
                labelSource = labelSource,
                validationStatus =
                    LabeledReference.VALIDATION_PENDING
            )
        )
    }

    suspend fun submitHumanLabel(
        referenceId: String,
        label: String
    ) {

        val reference =
            referenceDao.getById(referenceId)
                ?: return

        val agreement =
            reference.aiCategory != null &&
                    reference.aiCategory == label

        referenceDao.update(
            reference.copy(
                validationStatus =
                    LabeledReference.VALIDATION_VALIDATED,
                validatedLabel = label,
                agreement = agreement,
                labelSource =
                    LabeledReference.LABEL_SOURCE_HUMAN,
                reviewedAt = LocalDateTime.now()
            )
        )
    }

    suspend fun rejectReference(
        referenceId: String
    ) {

        val reference =
            referenceDao.getById(referenceId)
                ?: return

        referenceDao.update(
            reference.copy(
                validationStatus =
                    LabeledReference.VALIDATION_REJECTED,
                reviewedAt = LocalDateTime.now()
            )
        )
    }

    suspend fun getValidatedCount(): Int {
        return referenceDao.getValidatedCount()
    }

    suspend fun getValidatedAgreementCount(): Int {
        return referenceDao.getValidatedAgreementCount()
    }
}