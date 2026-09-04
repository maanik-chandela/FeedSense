package com.example.feedsense.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.EvaluationItem
import com.example.feedsense.model.EvaluationRecord
import com.example.feedsense.model.GroundTruth

/*
 * Milestone 8A-1 (persistence) extended for 8A-2 (the
 * annotation workflow).
 *
 * 8A-1 wrote the four tables. 8A-2 adds the operations the
 * annotation workflow needs on top of the SAME tables:
 *
 *   - re-edit: find/update the current ground truth and its
 *     evaluation result instead of blindly appending a new
 *     row every save (keeps a clean current record, no
 *     duplicate contradictory annotations)
 *   - queue reads for UNREVIEWED / REVIEWED discovery
 */
@Dao
interface EvaluationDao {

    // --------------------------------
    // EVALUATION ITEM
    // --------------------------------

    @Insert
    suspend fun insertItem(item: EvaluationItem)

    @Query("SELECT * FROM evaluation_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: String): EvaluationItem?

    @Query("SELECT * FROM evaluation_items WHERE feedItemId = :feedItemId")
    suspend fun getItemsForFeedItem(feedItemId: String): List<EvaluationItem>

    @Query("SELECT * FROM evaluation_items WHERE sessionId = :sessionId")
    suspend fun getItemsForSession(sessionId: String): List<EvaluationItem>

    @Query(
        """
    SELECT *
    FROM evaluation_items
    WHERE datasetVersion = :datasetVersion
    """
    )
    suspend fun getItemsForDataset(datasetVersion: String): List<EvaluationItem>

    @Query("SELECT * FROM evaluation_items WHERE evaluationStatus = :status")
    suspend fun getItemsByStatus(status: String): List<EvaluationItem>

    @Query(
        """
    SELECT *
    FROM evaluation_items
    WHERE sessionId = :sessionId
    AND evaluationStatus = :status
    """
    )
    suspend fun getItemsForSessionAndStatus(
        sessionId: String,
        status: String
    ): List<EvaluationItem>

    /*
     * 8A-2. Bounded annotation queue read. Bounds the UI to
     * a work chunk instead of loading every evaluation item
     * into memory (thousands of items scaling).
     */
    @Query(
        """
    SELECT *
    FROM evaluation_items
    WHERE evaluationStatus = :status
    ORDER BY enqueuedAt ASC
    LIMIT :limit
    """
    )
    suspend fun getItemsByStatusLimit(
        status: String,
        limit: Int
    ): List<EvaluationItem>

    @Query(
        """
    SELECT *
    FROM evaluation_items
    WHERE sessionId = :sessionId
    AND evaluationStatus = :status
    ORDER BY enqueuedAt ASC
    LIMIT :limit
    """
    )
    suspend fun getItemsForSessionAndStatusLimit(
        sessionId: String,
        status: String,
        limit: Int
    ): List<EvaluationItem>

    @Query(
        """
    UPDATE evaluation_items
    SET evaluationStatus = :status, completedAt = :completedAt
    WHERE id = :id
    """
    )
    suspend fun updateItemStatus(
        id: String,
        status: String,
        completedAt: String?
    )

    @Query(
        """
    UPDATE evaluation_items
    SET datasetVersion = :datasetVersion
    WHERE id = :id
    """
    )
    suspend fun assignDatasetVersion(id: String, datasetVersion: String)

    // --------------------------------
    // AI PREDICTION SNAPSHOT
    // --------------------------------

    @Insert
    suspend fun insertPrediction(prediction: AiPredictionRecord)

    @Query("SELECT * FROM ai_predictions WHERE evaluationItemId = :itemId LIMIT 1")
    suspend fun getPredictionForItem(itemId: String): AiPredictionRecord?

    /*
     * 8A-3. Batched read so the engine can load all eligible
     * predictions in one query (no N+1 over the cohort).
     */
    @Query(
        """
    SELECT *
    FROM ai_predictions
    WHERE evaluationItemId IN (:itemIds)
    """
    )
    suspend fun getPredictionsForItems(
        itemIds: Collection<String>
    ): List<AiPredictionRecord>

    // --------------------------------
    // GROUND TRUTH
    // --------------------------------

    @Insert
    suspend fun insertGroundTruth(truth: GroundTruth)

    @Update
    suspend fun updateGroundTruth(truth: GroundTruth)

    @Query("SELECT * FROM ground_truths WHERE id = :id LIMIT 1")
    suspend fun getGroundTruthById(id: String): GroundTruth?

    @Query("SELECT * FROM ground_truths WHERE evaluationItemId = :itemId")
    suspend fun getGroundTruthsForItem(itemId: String): List<GroundTruth>

    /*
     * 8A-3. Batched read of the current ground-truth record for
     * each (item, annotator) pair. For the engine we take the
     * first (earliest) non-null-annotator record per item as
     * "the" current truth, mirroring the clean-current-record
     * semantics; full multi-annotator adjudication is an 8A-4
     * concern.
     */
    @Query(
        """
    SELECT *
    FROM ground_truths
    WHERE evaluationItemId IN (:itemIds)
    """
    )
    suspend fun getGroundTruthsForItems(
        itemIds: Collection<String>
    ): List<GroundTruth>

    /*
     * 8A-2. The current ground-truth record for one
     * (item, annotator) pair. Used for re-editing so the
     * workflow updates the existing record in place rather
     * than appending a duplicate.
     */
    @Query(
        """
    SELECT *
    FROM ground_truths
    WHERE evaluationItemId = :itemId
    AND annotatorId IS :annotatorId
    LIMIT 1
    """
    )
    suspend fun getGroundTruthForItemAndAnnotator(
        itemId: String,
        annotatorId: String?
    ): GroundTruth?

    // --------------------------------
    // EVALUATION RESULT
    // --------------------------------

    @Insert
    suspend fun insertResult(result: EvaluationRecord)

    @Update
    suspend fun updateResult(result: EvaluationRecord)

    @Query("SELECT * FROM evaluation_results WHERE id = :id LIMIT 1")
    suspend fun getResultById(id: String): EvaluationRecord?

    @Query("SELECT * FROM evaluation_results WHERE evaluationItemId = :itemId")
    suspend fun getResultsForItem(itemId: String): List<EvaluationRecord>

    /*
     * 8A-3. Batched read of all evaluation results for a
     * cohort of items (no N+1).
     */
    @Query(
        """
    SELECT *
    FROM evaluation_results
    WHERE evaluationItemId IN (:itemIds)
    """
    )
    suspend fun getResultsForItems(
        itemIds: Collection<String>
    ): List<EvaluationRecord>

    /*
     * 8A-2. The single evaluation result computed from one
     * ground-truth record's current value. Re-used (not
     * duplicated) when an annotation is re-edited.
     */
    @Query(
        """
    SELECT *
    FROM evaluation_results
    WHERE groundTruthId = :groundTruthId
    LIMIT 1
    """
    )
    suspend fun getResultForGroundTruth(
        groundTruthId: String
    ): EvaluationRecord?

    @Query(
        """
    SELECT *
    FROM evaluation_results
    WHERE datasetVersion = :datasetVersion
    """
    )
    suspend fun getResultsForDataset(datasetVersion: String): List<EvaluationRecord>

    // --------------------------------
    // ROLL-UPS (for the 8A-3 reporting layer)
    // --------------------------------

    @Query("SELECT COUNT(*) FROM evaluation_results WHERE verdict = 'CORRECT'")
    suspend fun countCorrect(): Int

    @Query("SELECT COUNT(*) FROM evaluation_results")
    suspend fun countTotal(): Int
}
