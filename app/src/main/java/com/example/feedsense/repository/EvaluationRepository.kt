package com.example.feedsense.repository

import com.example.feedsense.analysis.AnnotationValidator
import com.example.feedsense.dao.EvaluationDao
import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.EvaluationItem
import com.example.feedsense.model.EvaluationRecord
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.GroundTruth

// --------------------------------
// EVALUATION REPOSITORY (Milestone 8A-1)
// --------------------------------
//
// Owns the research evaluation workflow's persistence:
//
//   1. enqueueItem   - create the evaluation item and an
//                      IMMUTABLE snapshot of the AI
//                      prediction derived from a FeedItem.
//                      The snapshot makes the evaluation
//                      immune to later correction/deletion
//                      of the feed_items row.
//   2. recordGroundTruth - store the human truth and
//                      compute + store the EvaluationRecord
//                      comparing prediction vs truth.
//   3. assignDatasetVersion - curate a cohort of evaluated
//                      items into a frozen dataset version.
//
// Pure comparison logic lives in EvaluationRecord.fromComponents
// so it is unit-testable without a database.

class EvaluationRepository(
    private val evaluationDao: EvaluationDao
) {

    /*
     * Snapshots a FeedItem's AI prediction into the
     * evaluation layer and marks the item as not yet
     * evaluated. Returns the newly created evaluation item.
     */
    suspend fun enqueueItem(
        feedItem: FeedItem,
        sessionId: String,
        projectId: String? = null
    ): EvaluationItem {

        val item = EvaluationItem(
            feedItemId = feedItem.id,
            sessionId = sessionId,
            projectId = projectId,
            modelVersion = feedItem.modelVersion,
            evaluationStatus =
                EvaluationItem.STATUS_NOT_EVALUATED
        )

        val prediction = AiPredictionRecord(
            evaluationItemId = item.id,
            source = feedItem.source,
            modelVersion = feedItem.modelVersion,
            category = feedItem.category,
            categoryDomain = feedItem.categoryDomain,
            confidence = feedItem.confidence,
            secondaryCategories =
                feedItem.secondaryCategories,
            categoryScores = feedItem.categoryScores,
            platform = feedItem.platform,
            contentType = feedItem.contentType,
            durationSeconds = feedItem.durationSeconds,
            skipped = feedItem.skipped,
            interactionSignals =
                feedItem.interactionSignals,
            topic = feedItem.topic,
            tone = feedItem.tone,
            uncertaintyLevel =
                feedItem.uncertaintyLevel,
            needsReview = feedItem.needsReview,
            feedItemId = feedItem.id
        )

        /*
         * Inserting the immutable prediction ends the
         * "snapshot" stage; from here the AI prediction is
         * frozen for this evaluation.
         */
        evaluationDao.insertItem(item)
        evaluationDao.insertPrediction(prediction)

        return item
    }

    /*
     * Records human ground truth and computes/stores the
     * evaluation result against the item's frozen AI
     * prediction snapshot.
     *
     * Milestone 8A-2 corrected re-edit semantics: the
     * workflow maintains a CLEAN CURRENT record. Saving an
     * annotation for the same (item, annotator) pair UPDATES
     * the existing ground-truth row in place and REPLACES its
     * evaluation result - it never appends a second,
     * contradictory row. The frozen AI prediction snapshot is
     * never touched.
     *
     * Multiple annotators remain independent: a different
     * annotatorId produces its own (item, annotator) current
     * record.
     */
    suspend fun recordGroundTruth(
        evaluationItemId: String,
        truth: GroundTruth,
        annotatorId: String? = null
    ) {
        val prediction =
            evaluationDao.getPredictionForItem(
                evaluationItemId
            ) ?: return

        val requestedAnnotator =
            annotatorId ?: truth.annotatorId

        val existing =
            evaluationDao
                .getGroundTruthForItemAndAnnotator(
                    evaluationItemId,
                    requestedAnnotator
                )

        // Clean-current-record upsert for ground truth.
        //
        // On RE-EDIT the existing row's PRIMARY KEY (id) and
        // original recordedAt are preserved so @Update hits
        // the real row - the fresh UUID generated inside the
        // draft must not replace the key. This keeps a
        // single current record per (item, annotator).
        val groundTruth = truth.copy(
            id = existing?.id ?: truth.id,
            evaluationItemId = evaluationItemId,
            annotatorId = requestedAnnotator,
            recordedAt =
                existing?.recordedAt ?: truth.recordedAt
        )

        if (existing != null) {
            evaluationDao.updateGroundTruth(groundTruth)
        } else {
            evaluationDao.insertGroundTruth(groundTruth)
        }

        val result = EvaluationRecord.fromComponents(
            prediction = prediction,
            truth = groundTruth
        )

        // Replace (not append) the result for this record,
        // preserving its PRIMARY KEY so @Update targets the
        // real row.
        val existingResult =
            evaluationDao.getResultForGroundTruth(
                groundTruth.id
            )
        if (existingResult != null) {
            evaluationDao.updateResult(
                result.copy(id = existingResult.id)
            )
        } else {
            evaluationDao.insertResult(result)
        }

        evaluationDao.updateItemStatus(
            id = evaluationItemId,
            status =
                EvaluationItem.STATUS_EVALUATED,
            completedAt =
                groundTruth.recordedAt.toString()
        )
    }

    /*
     * 8A-2 entry point used by the annotation screen.
     *
     * Validates + normalizes the human draft against the
     * single source-of-truth taxonomies, derives the legacy
     * interaction-signal list from the tri-state fields, then
     * commits it as the clean current record (upsert, never
     * a duplicate). Throws ValidationException when the draft
     * is incomplete/inconsistent.
     */
    suspend fun saveAnnotation(
        evaluationItemId: String,
        draft: GroundTruth,
        annotatorId: String?
    ) {
        val validated = AnnotationValidator.validate(draft)

        if (!validated.isValid) {
            throw AnnotationValidator.ValidationException(
                validated.errors
            )
        }

        recordGroundTruth(
            evaluationItemId = evaluationItemId,
            truth = validated.normalized!!,
            annotatorId = annotatorId
        )
    }

    /*
     * Marks an item DISPUTED (adjudication needed). Stores
     * the plain string value on the existing status column -
     * no schema change.
     */
    suspend fun markDisputed(
        evaluationItemId: String
    ) {
        evaluationDao.updateItemStatus(
            id = evaluationItemId,
            status = EvaluationItem.STATUS_DISPUTED,
            completedAt = null
        )
    }

    /*
     * 8A-2. Reads the current ground-truth record for one
     * (item, annotator) pair so the UI can pre-fill an
     * existing annotation on re-edit.
     */
    suspend fun getCurrentGroundTruthForAnnotator(
        evaluationItemId: String,
        annotatorId: String?
    ): GroundTruth? {
        return evaluationDao
            .getGroundTruthForItemAndAnnotator(
                evaluationItemId,
                annotatorId
            )
    }

    /*
     * Annotator identity. Kept pseudonymous: the caller
     * supplies an opaque label; no profile data is ever
     * stored, so a human's ground truth cannot be tied back
     * to a real person (local-only privacy).
     */
    data class Annotator(
        val id: String,
        val label: String
    )

    /*
     * Curates a set of evaluated items into a named
     * dataset version. Assigning the same version to a
     * set of items freezes that cohort so later
     * comparisons can reference it without drift.
     */
    suspend fun assignDatasetVersion(
        evaluationItemIds: List<String>,
        datasetVersion: String
    ) {
        evaluationItemIds.forEach { id ->
            evaluationDao.assignDatasetVersion(
                id = id,
                datasetVersion = datasetVersion
            )
        }
    }

    suspend fun getItemsForSession(
        sessionId: String
    ): List<EvaluationItem> {
        return evaluationDao.getItemsForSession(sessionId)
    }

    suspend fun getItemsForDataset(
        datasetVersion: String
    ): List<EvaluationItem> {
        return evaluationDao.getItemsForDataset(datasetVersion)
    }

    suspend fun getResultsForDataset(
        datasetVersion: String
    ): List<EvaluationRecord> {
        return evaluationDao.getResultsForDataset(datasetVersion)
    }

    // --------------------------------
    // ANNOTATION QUEUE (8A-2)
    // --------------------------------

    /*
     * Reads a bounded annotation-queue chunk by status,
     * optionally scoped to one session.
     */
    suspend fun getQueue(
        status: String,
        limit: Int = 50,
        sessionId: String? = null
    ): List<EvaluationItem> {
        return if (sessionId != null) {
            evaluationDao.getItemsForSessionAndStatusLimit(
                sessionId = sessionId,
                status = status,
                limit = limit
            )
        } else {
            evaluationDao.getItemsByStatusLimit(
                status = status,
                limit = limit
            )
        }
    }

    /*
     * Maps the stored evaluation status to the 8A-2
     * annotation-status vocabulary.
     */
    fun annotationStatusLabel(
        status: String?
    ): String {
        return when (status) {
            EvaluationItem.STATUS_NOT_EVALUATED -> "UNREVIEWED"
            EvaluationItem.STATUS_PARTIALLY_EVALUATED -> "IN_PROGRESS"
            EvaluationItem.STATUS_EVALUATED -> "REVIEWED"
            EvaluationItem.STATUS_DISPUTED -> "DISPUTED"
            else -> "UNREVIEWED"
        }
    }

    companion object {
        val QUEUE_UNREVIEWED =
            EvaluationItem.STATUS_NOT_EVALUATED
        val QUEUE_IN_PROGRESS =
            EvaluationItem.STATUS_PARTIALLY_EVALUATED
        val QUEUE_REVIEWED =
            EvaluationItem.STATUS_EVALUATED
        val QUEUE_DISPUTED =
            EvaluationItem.STATUS_DISPUTED
    }
}
