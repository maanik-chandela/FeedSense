package com.example.feedsense.repository

import com.example.feedsense.dao.CaptureDao
import com.example.feedsense.dao.EvaluationDao
import com.example.feedsense.dao.FeedItemDao
import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.CapturedFrame
import com.example.feedsense.model.EvaluationItem
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.GroundTruth
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

// --------------------------------
// ANNOTATION REPOSITORY (Milestone 8A-2)
// --------------------------------
//
// Coordinates the human-annotation workflow on top of
// 8A-1's evaluation layer:
//
//   - loadEvidence      assemble everything an evaluator
//                       needs to SEE before judging: the AI
//                       prediction snapshot, the
//                       representative frame, the session
//                       frames captured within the item's
//                       [startTime, endTime] window, and any
//                       existing human ground truth (for
//                       re-edit pre-fill).
//   - save              validate + write the human ground
//                       truth as the CLEAN CURRENT record
//                       (delegates to EvaluationRepository);
//                       never touches the AI snapshot.
//   - queue             bounded UNREVIEWED / REVIEWED
//                       discovery with metadata.
//
// Evidence design note (documented, not fabricated): the
// capture layer keeps an immutable feedItemId on NO captured
// frame; FeedItems are derived from a session's frames. The
// most reliable per-item evidence is therefore
// representativeFramePath plus the item metadata and the AI
// snapshot. "Session frames within the item's time window"
// is a BEST-EFFORT, deterministic approximation: the frame
// timestamps must fall within [startTime, endTime], but the
// capture pipeline is NOT altered here - any limitation is
// surfaced, not hidden.

class AnnotationRepository(
    private val evaluationRepository: EvaluationRepository,
    private val evaluationDao: EvaluationDao,
    private val feedItemDao: FeedItemDao,
    private val captureDao: CaptureDao
) {

    data class Evidence(
        val evaluationItem: EvaluationItem,
        val feedItem: FeedItem?,
        val prediction: AiPredictionRecord?,
        val currentTruth: GroundTruth?,
        val framesInWindow: Flow<List<CapturedFrame>>
    )

    /*
     * Assembles everything an evaluator needs for one item.
     * The AI prediction is read from the frozen
     * ai_predictions snapshot (never from the mutable
     * feed_items row), so evidence stays stable.
     */
    suspend fun loadEvidence(
        evaluationItemId: String,
        annotatorId: String?
    ): Evidence? {
        val item =
            evaluationDao.getItemById(
                evaluationItemId
            ) ?: return null

        val feedItem =
            item.feedItemId
                ?.let { feedItemDao.getItemById(it) }

        val prediction =
            evaluationDao.getPredictionForItem(
                evaluationItemId
            )

        val currentTruth =
            evaluationDao
                .getGroundTruthForItemAndAnnotator(
                    evaluationItemId,
                    annotatorId
                )

        val framesInWindow =
            feedItemWindowFrames(item, feedItem)

        return Evidence(
            evaluationItem = item,
            feedItem = feedItem,
            prediction = prediction,
            currentTruth = currentTruth,
            framesInWindow = framesInWindow
        )
    }

    /*
     * Best-effort frame browsing window, documented above.
     * When the item has no stored endTime the window is
     * unbounded on the upper side.
     */
    private fun feedItemWindowFrames(
        item: EvaluationItem,
        feedItem: FeedItem?
    ): Flow<List<CapturedFrame>> {
        val sessionId = item.sessionId
        if (feedItem == null) {
            return captureDao.getFramesForSession(sessionId)
        }
        return captureDao.getFramesForSessionInWindow(
            sessionId = sessionId,
            startTime = feedItem.startTime,
            endTime =
                feedItem.endTime ?: LocalDateTime.MAX
        )
    }

    /*
     * Validates + persists the human ground truth. See
     * EvaluationRepository.saveAnnotation for the upsert
     * (re-edit updates the current record, never appends a
     * competing row) and the no-overwrite guarantee on the
     * frozen AI prediction.
     */
    suspend fun save(
        evaluationItemId: String,
        draft: GroundTruth,
        annotatorId: String?
    ) {
        evaluationRepository.saveAnnotation(
            evaluationItemId = evaluationItemId,
            draft = draft,
            annotatorId = annotatorId
        )
    }

    suspend fun markDisputed(
        evaluationItemId: String
    ) {
        evaluationRepository.markDisputed(
            evaluationItemId
        )
    }

    /*
     * Bounded annotation queue with the evidence metadata
     * needed to render each row (representative frame +
     * AI category). Screenshots load lazily in the detail
     * screen only.
     */
    suspend fun queue(
        status: String,
        limit: Int = 50,
        sessionId: String? = null
    ): List<Pair<EvaluationItem, FeedItem?>> {
        return evaluationRepository
            .getQueue(status, limit, sessionId)
            .map { item ->
                item to
                    item.feedItemId
                        ?.let { feedItemDao.getItemById(it) }
            }
    }
}