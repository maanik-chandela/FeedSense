package com.example.feedsense.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.feedsense.model.ConfusionRow
import com.example.feedsense.model.LabeledReference
import kotlinx.coroutines.flow.Flow

@Dao
interface ReferenceDao {

    @Insert
    suspend fun insert(
        reference: LabeledReference
    )

    @Update
    suspend fun update(
        reference: LabeledReference
    )

    @Query(
        """
    SELECT *
    FROM labeled_references
    WHERE validationStatus = 'PENDING'
    ORDER BY createdAt ASC
    """
    )
    fun getPendingReviews(): Flow<List<LabeledReference>>

    @Query(
        """
    SELECT *
    FROM labeled_references
    WHERE sessionId = :sessionId
    ORDER BY createdAt DESC
    """
    )
    fun getForSession(
        sessionId: String
    ): Flow<List<LabeledReference>>

    /*
     * Milestone 7D. All pending rows for a session,
     * used by the feed item builder to avoid
     * duplicating review entries.
     */
    @Query(
        """
    SELECT *
    FROM labeled_references
    WHERE sessionId = :sessionId
    AND validationStatus = 'PENDING'
    """
    )
    suspend fun getPendingForSession(
        sessionId: String
    ): List<LabeledReference>

    /*
     * Milestone 7L (Part 5). Pending row for one frame,
     * used to avoid queuing the same frame twice.
     */
    @Query(
        """
    SELECT COUNT(*)
    FROM labeled_references
    WHERE sessionId = :sessionId
    AND frameId = :frameId
    AND validationStatus = 'PENDING'
    """
    )
    suspend fun getPendingCountForFrame(
        sessionId: String,
        frameId: String
    ): Int

    /*
     * Milestone 7D. Validated references for a session,
     * used as reference-based refinement data when
     * rebuilding feed items (validated labels win over
     * raw AI predictions for the same frame).
     */
    @Query(
        """
    SELECT *
    FROM labeled_references
    WHERE sessionId = :sessionId
    AND validationStatus = 'VALIDATED'
    """
    )
    suspend fun getValidatedForSession(
        sessionId: String
    ): List<LabeledReference>

    /*
     * Milestone 7D. Item-level pending references are
     * regenerated on every feed item rebuild (item ids
     * change), so stale pending rows are removed first.
     * Validated rows are never touched.
     */
    @Query(
        """
    DELETE FROM labeled_references
    WHERE sessionId = :sessionId
    AND validationStatus = 'PENDING'
    AND feedItemId IS NOT NULL
    """
    )
    suspend fun deletePendingItemReferencesForSession(
        sessionId: String
    )

    @Query(
        """
    SELECT *
    FROM labeled_references
    WHERE id = :referenceId
    LIMIT 1
    """
    )
    suspend fun getById(
        referenceId: String
    ): LabeledReference?

    /*
     * Milestone 7D. Backfills feedItemId onto every
     * pending reference whose frame belongs to a built
     * item, so validating any reference for an item can
     * correct that FeedItem.
     */
    @Query(
        """
    UPDATE labeled_references
    SET feedItemId = :feedItemId
    WHERE frameId IN (:frameIds)
    """
    )
    suspend fun assignFeedItemId(
        feedItemId: String,
        frameIds: List<String>
    )

    @Query(
        """
    SELECT COUNT(*)
    FROM labeled_references
    WHERE validationStatus = 'PENDING'
    """
    )
    suspend fun getPendingCount(): Int

    /*
     * Milestone 7K (Part 3). Frame ids that have a
     * non-pending reference row (validated, rejected or
     * skipped) for this session. Used by the feed item
     * builder so content the reviewer already handled is
     * not surfaced again.
     */
    @Query(
        """
    SELECT frameId
    FROM labeled_references
    WHERE sessionId = :sessionId
    AND validationStatus != 'PENDING'
    """
    )
    suspend fun getHandledFrameIdsForSession(
        sessionId: String
    ): List<String>

    @Query(
        """
    SELECT COUNT(*)
    FROM labeled_references
    WHERE validationStatus = 'VALIDATED'
    """
    )
    suspend fun getValidatedCount(): Int

    @Query(
        """
    SELECT COUNT(*)
    FROM labeled_references
    WHERE validationStatus = 'VALIDATED'
    AND agreement = 1
    """
    )
    suspend fun getValidatedAgreementCount(): Int

    // --------------------------------
    // EVALUATION
    // --------------------------------
    //
    // Future local-model evaluation queries by
    // model version.

    @Query(
        """
    SELECT *
    FROM labeled_references
    WHERE validationStatus = 'VALIDATED'
    AND modelVersion = :modelVersion
    ORDER BY reviewedAt DESC
    """
    )
    fun getValidatedForModelVersion(
        modelVersion: String
    ): Flow<List<LabeledReference>>

    // --------------------------------
    // REFERENCE MEMORY (7F PART 8)
    // --------------------------------
    //
    // Validated references act as searchable local
    // memory: previously human-confirmed labels for the
    // same category / topic / platform / text. Results
    // are bounded so rebuilds stay cheap.

    @Query(
        """
    SELECT *
    FROM labeled_references
    WHERE validationStatus = 'VALIDATED'
    ORDER BY reviewedAt DESC
    LIMIT :limit
    """
    )
    suspend fun getValidatedReferences(
        limit: Int
    ): List<LabeledReference>

    /*
     * Milestone 7O. ALL validated references across
     * sessions - the trusted-memory dataset used to
     * build cross-session personalization statistics.
     * Unbounded on purpose: personalization is computed
     * on demand, only while the evaluation screen is
     * open.
     */
    @Query(
        """
    SELECT *
    FROM labeled_references
    WHERE validationStatus = 'VALIDATED'
    ORDER BY reviewedAt DESC
    """
    )
    suspend fun getAllValidated(): List<LabeledReference>

    @Query(
        """
    SELECT *
    FROM labeled_references
    WHERE validationStatus = 'VALIDATED'
    AND validatedLabel = :category
    ORDER BY reviewedAt DESC
    LIMIT :limit
    """
    )
    suspend fun getValidatedByCategory(
        category: String,
        limit: Int
    ): List<LabeledReference>

    @Query(
        """
    SELECT *
    FROM labeled_references
    WHERE validationStatus = 'VALIDATED'
    AND validatedLabel = :category
    AND platform = :platform
    ORDER BY reviewedAt DESC
    LIMIT :limit
    """
    )
    suspend fun getValidatedByCategoryAndPlatform(
        category: String,
        platform: String,
        limit: Int
    ): List<LabeledReference>

    @Query(
        """
    SELECT *
    FROM labeled_references
    WHERE validationStatus = 'VALIDATED'
    AND topic = :topic
    ORDER BY reviewedAt DESC
    LIMIT :limit
    """
    )
    suspend fun getValidatedByTopic(
        topic: String,
        limit: Int
    ): List<LabeledReference>

    @Query(
        """
    SELECT *
    FROM labeled_references
    WHERE validationStatus = 'VALIDATED'
    AND platform = :platform
    ORDER BY reviewedAt DESC
    LIMIT :limit
    """
    )
    suspend fun getValidatedByPlatform(
        platform: String,
        limit: Int
    ): List<LabeledReference>

    @Query(
        """
    SELECT *
    FROM labeled_references
    WHERE validationStatus = 'VALIDATED'
    AND visibleText LIKE '%' || :query || '%'
    ORDER BY reviewedAt DESC
    LIMIT :limit
    """
    )
    suspend fun searchValidatedByText(
        query: String,
        limit: Int
    ): List<LabeledReference>

    // --------------------------------
    // REFERENCE MEMORY (7G PART 7-8)
    // --------------------------------
    //
    // Project-scoped reference memory. Validated labels
    // from one research project must never leak into
    // another project, so retrieval is bounded to the
    // references whose session belongs to this project.

    @Query(
        """
    SELECT lr.*
    FROM labeled_references lr
    INNER JOIN research_sessions rs ON rs.id = lr.sessionId
    WHERE lr.validationStatus = 'VALIDATED'
    AND rs.projectId = :projectId
    ORDER BY lr.reviewedAt DESC
    LIMIT :limit
    """
    )
    suspend fun getValidatedForProject(
        projectId: String,
        limit: Int
    ): List<LabeledReference>

    // --------------------------------
    // MODEL PERFORMANCE (7G PART 5-6)
    // --------------------------------
    //
    // Human-reviewed rows (validated or rejected) are
    // the ground-truth dataset used to measure how the
    // local model is doing over time.

    @Query(
        """
    SELECT *
    FROM labeled_references
    WHERE validationStatus IN ('VALIDATED', 'REJECTED')
    ORDER BY reviewedAt DESC
    """
    )
    suspend fun getReviewedReferences(): List<LabeledReference>

    @Query(
        """
    SELECT aiCategory AS predicted,
           validatedLabel AS actual,
           COUNT(*) AS count
    FROM labeled_references
    WHERE validationStatus = 'VALIDATED'
    AND aiCategory IS NOT NULL
    AND validatedLabel IS NOT NULL
    AND aiCategory != validatedLabel
    GROUP BY aiCategory, validatedLabel
    ORDER BY count DESC
    """
    )
    suspend fun getConfusionPairs(): List<ConfusionRow>

    /*
     * Milestone 7R. How often the pipeline fell back to
     * the cloud teacher. Cloud-sourced rows keep
     * aiSource = 'CLOUD' and only become usable training
     * data after human validation.
     */
    @Query(
        """
    SELECT COUNT(*)
    FROM labeled_references
    WHERE aiSource = 'CLOUD'
    """
    )
    suspend fun getCloudSourceCount(): Int
}