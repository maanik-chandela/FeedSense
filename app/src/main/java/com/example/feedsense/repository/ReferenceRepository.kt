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

    /*
     * Milestone 7D. Validated references for a session
     * (reference-based refinement / learning data).
     */
    suspend fun getValidatedForSession(
        sessionId: String
    ): List<LabeledReference> {
        return referenceDao.getValidatedForSession(sessionId)
    }

    /*
     * Milestone 7D. Pending references for a session,
     * used to avoid duplicating review queue entries.
     */
    suspend fun getPendingForSession(
        sessionId: String
    ): List<LabeledReference> {
        return referenceDao.getPendingForSession(sessionId)
    }

    /*
     * Milestone 7K (Part 3). Frame ids the reviewer has
     * already handled (validated, rejected or skipped) in
     * this session, so already-reviewed content is not
     * queued again.
     */
    suspend fun getHandledFrameIdsForSession(
        sessionId: String
    ): List<String> {
        return referenceDao.getHandledFrameIdsForSession(
            sessionId
        )
    }

    /*
     * Milestone 7L (Part 5). True when a pending review
     * already exists for this frame, so the same content
     * is never queued twice.
     */
    suspend fun hasPendingReviewForFrame(
        sessionId: String,
        frameId: String
    ): Boolean {
        return referenceDao.getPendingCountForFrame(
            sessionId,
            frameId
        ) > 0
    }

    suspend fun getPendingReviewCount(): Int {
        return referenceDao.getPendingCount()
    }

    suspend fun deletePendingItemReferencesForSession(
        sessionId: String
    ) {
        referenceDao.deletePendingItemReferencesForSession(
            sessionId
        )
    }

    suspend fun assignFeedItemId(
        feedItemId: String,
        frameIds: List<String>
    ) {
        referenceDao.assignFeedItemId(
            feedItemId,
            frameIds
        )
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
        labelSource: String,
        feedItemId: String? = null,
        platform: String? = null,
        topic: String? = null,
        tone: String? = null,
        visibleText: String? = null,
        aiReason: String? = null,
        interactionSignals: List<String> = emptyList(),
        frameFingerprint: String? = null
    ) {

        /*
         * Milestone 7L (Part 5): a frame that already has
         * a pending review row must never be queued again.
         * The item builder also skips frames it already
         * created entries for, but the worker-level path
         * runs independently, so this is the last line of
         * defense against duplicate queue entries.
         */
        if (
            hasPendingReviewForFrame(
                sessionId = sessionId,
                frameId = frameId
            )
        ) {
            return
        }

        referenceDao.insert(
            LabeledReference(
                frameId = frameId,
                sessionId = sessionId,
                filePath = filePath,
                feedItemId = feedItemId,
                aiCategory = aiCategory,
                aiConfidence = aiConfidence,
                aiSource = aiSource,
                modelVersion = modelVersion,
                candidateCategories = candidateCategories,
                platform = platform,
                topic = topic,
                tone = tone,
                visibleText = visibleText,
                aiReason = aiReason,
                interactionSignals = interactionSignals,
                frameFingerprint = frameFingerprint,
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

        val normalizedLabel =
            com.example.feedsense.analysis.CategoryCatalog
                .normalize(label)
                ?: label

        val agreement =
            reference.aiCategory != null &&
                    com.example.feedsense.analysis.CategoryCatalog
                        .normalize(reference.aiCategory) ==
                    normalizedLabel

        referenceDao.update(
            reference.copy(
                validationStatus =
                    LabeledReference.VALIDATION_VALIDATED,
                validatedLabel = normalizedLabel,
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

    /*
     * Milestone 7K (Part 3). "Skip review": marks the
     * reference SKIPPED so it leaves the active queue
     * without becoming validated or rejected training
     * data.
     */
    suspend fun skipReference(
        referenceId: String
    ) {

        val reference =
            referenceDao.getById(referenceId)
                ?: return

        referenceDao.update(
            reference.copy(
                validationStatus =
                    LabeledReference.VALIDATION_SKIPPED,
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

    // --------------------------------
    // REFERENCE MEMORY (7F PART 8)
    // --------------------------------

    suspend fun getValidatedReferences(
        limit: Int
    ): List<LabeledReference> {
        return referenceDao.getValidatedReferences(limit)
    }

    /*
     * Milestone 7T. Unbounded read of the validated
     * ground-truth dataset for the JSON export.
     */
    suspend fun getAllValidated(): List<LabeledReference> {
        return referenceDao.getAllValidated()
    }

    suspend fun getValidatedByCategory(
        category: String,
        limit: Int
    ): List<LabeledReference> {
        return referenceDao.getValidatedByCategory(
            category,
            limit
        )
    }

    suspend fun getValidatedByCategoryAndPlatform(
        category: String,
        platform: String,
        limit: Int
    ): List<LabeledReference> {
        return referenceDao.getValidatedByCategoryAndPlatform(
            category,
            platform,
            limit
        )
    }

    suspend fun getValidatedByTopic(
        topic: String,
        limit: Int
    ): List<LabeledReference> {
        return referenceDao.getValidatedByTopic(
            topic,
            limit
        )
    }

    suspend fun getValidatedByPlatform(
        platform: String,
        limit: Int
    ): List<LabeledReference> {
        return referenceDao.getValidatedByPlatform(
            platform,
            limit
        )
    }

    suspend fun searchValidatedByText(
        query: String,
        limit: Int
    ): List<LabeledReference> {
        return referenceDao.searchValidatedByText(
            query,
            limit
        )
    }

    // --------------------------------
    // REFERENCE MEMORY (7G PART 7-8)
    // --------------------------------
    //
    // Project-scoped memory so validated labels from one
    // research project never leak into another.

    suspend fun getValidatedForProject(
        projectId: String,
        limit: Int
    ): List<LabeledReference> {
        return referenceDao.getValidatedForProject(
            projectId,
            limit
        )
    }
}