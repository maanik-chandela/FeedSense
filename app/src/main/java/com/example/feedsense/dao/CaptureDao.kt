package com.example.feedsense.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.feedsense.model.CapturedFrame
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface CaptureDao {
// --------------------------------
// BASIC CRUD
// --------------------------------

    @Insert
    suspend fun insert(
        frame: CapturedFrame
    )

    @Update
    suspend fun update(
        frame: CapturedFrame
    )

    @Delete
    suspend fun delete(
        frame: CapturedFrame
    )

// --------------------------------
// SESSION FRAMES
// --------------------------------

    @Query(
        """
    SELECT *
    FROM captured_frames
    WHERE sessionId = :sessionId
    ORDER BY capturedAt DESC
    """
    )
    fun getFramesForSession(
        sessionId: String
    ): Flow<List<CapturedFrame>>

// --------------------------------
// ANALYZED FRAMES (FEED ITEM BUILDING)
// --------------------------------

    @Query(
        """
    SELECT *
    FROM captured_frames
    WHERE sessionId = :sessionId
    AND analysisStatus = 'ANALYZED'
    ORDER BY capturedAt ASC
    """
    )
    suspend fun getAnalyzedFramesForSession(
        sessionId: String
    ): List<CapturedFrame>

    /*
     * Milestone 7D.
     *
     * Frames that participate in FeedItem building.
     *
     * Previously only ANALYZED frames were used, which
     * silently dropped NEEDS_REVIEW / REVIEWED content
     * - a very short Reel with a weak classification
     * would never become a FeedItem.
     *
     * Uncertain frames are now included so nothing
     * disappears; they simply produce items flagged for
     * review.
     */
    @Query(
        """
    SELECT *
    FROM captured_frames
    WHERE sessionId = :sessionId
    AND analysisStatus IN ('ANALYZED', 'NEEDS_REVIEW', 'REVIEWED')
    ORDER BY capturedAt ASC
    """
    )
    suspend fun getFramesForItemBuilding(
        sessionId: String
    ): List<CapturedFrame>

    @Query(
        """
    SELECT DISTINCT sessionId
    FROM captured_frames
    WHERE analysisStatus IN ('ANALYZED', 'NEEDS_REVIEW', 'REVIEWED')
    """
    )
    suspend fun getSessionsWithAnalyzedFrames(): List<String>

// --------------------------------
// FRAME COUNT
// --------------------------------

    @Query(
        """
    SELECT COUNT(*)
    FROM captured_frames
    WHERE sessionId = :sessionId
    """
    )
    suspend fun getFrameCount(
        sessionId: String
    ): Int

// --------------------------------
// ANALYSIS QUEUE
// --------------------------------

    @Query(
        """
    SELECT *
    FROM captured_frames
    WHERE analysisStatus = 'PENDING'
    ORDER BY capturedAt ASC
    LIMIT :limit
    """
    )
    suspend fun getPendingFrames(
        limit: Int = 10
    ): List<CapturedFrame>

// --------------------------------
// MARK PROCESSING
// --------------------------------

    @Query(
        """
    UPDATE captured_frames
    SET analysisStatus = 'PROCESSING'
    WHERE id = :frameId
    """
    )
    suspend fun markProcessing(
        frameId: String
    )

// --------------------------------
// MARK ANALYZED
// --------------------------------

    @Query(
        """
    UPDATE captured_frames
    SET
        analysisStatus = 'ANALYZED',
        analysisResult = :result,
        analyzedAt = :analyzedAt,
        frameFingerprint = :fingerprint
    WHERE id = :frameId
    """
    )
    suspend fun markAnalyzed(
        frameId: String,
        result: String,
        analyzedAt: LocalDateTime,
        fingerprint: String? = null
    )

// --------------------------------
// MARK FAILED
// --------------------------------

    @Query(
        """
    UPDATE captured_frames
    SET
        analysisStatus = 'FAILED'
    WHERE id = :frameId
    """
    )
    suspend fun markFailed(
        frameId: String
    )

// --------------------------------
// MARK NEEDS REVIEW
// --------------------------------
//
// Local analysis finished but the result was not
// confident or was ambiguous, and no cloud reference
// was available. The frame is queued for review.
//

    @Query(
        """
    UPDATE captured_frames
    SET
        analysisStatus = 'NEEDS_REVIEW',
        analysisResult = :result,
        analyzedAt = :analyzedAt,
        frameFingerprint = :fingerprint
    WHERE id = :frameId
    """
    )
    suspend fun markNeedsReview(
        frameId: String,
        result: String,
        analyzedAt: LocalDateTime,
        fingerprint: String? = null
    )

// --------------------------------
// MILESTONE 8B-3: FRAME FILTERED
// --------------------------------
//
// A frame that was perceptually similar to a recently
// retained frame and was not force-kept. The frame file
// is preserved for research completeness but the
// expensive analysis pipeline is skipped.
//

    @Query(
        """
    UPDATE captured_frames
    SET
        analysisStatus = 'FILTERED',
        analysisResult = :result,
        analyzedAt = :analyzedAt,
        frameFingerprint = :fingerprint
    WHERE id = :frameId
    """
    )
    suspend fun markFiltered(
        frameId: String,
        result: String,
        analyzedAt: LocalDateTime,
        fingerprint: String? = null
    )

// --------------------------------
// MILESTONE 7S: FRAME DEDUP
// --------------------------------
//
// Same content, same session -> analyzed once. Returns
// an already-analyzed frame with the same perceptual
// fingerprint so the worker can reuse its classification
// instead of running the whole pipeline again.
//

    @Query(
        """
    SELECT *
    FROM captured_frames
    WHERE sessionId = :sessionId
    AND analysisStatus IN ('ANALYZED', 'NEEDS_REVIEW')
    AND frameFingerprint = :fingerprint
    LIMIT 1
    """
    )
    suspend fun getAnalyzedFrameByFingerprint(
        sessionId: String,
        fingerprint: String
    ): CapturedFrame?

// --------------------------------
// RESET PROCESSING FRAMES
// --------------------------------
//
// If the app is killed while analysis
// is running, PROCESSING frames can be
// returned to PENDING.
//

    @Query(
        """
    UPDATE captured_frames
    SET analysisStatus = 'PENDING'
    WHERE analysisStatus = 'PROCESSING'
    """
    )
    suspend fun resetProcessingFrames()

// --------------------------------
// UPDATE ANALYSIS STATUS
// --------------------------------

    @Query(
        """
    UPDATE captured_frames
    SET analysisStatus = :status
    WHERE id = :frameId
    """
    )
    suspend fun updateAnalysisStatus(
        frameId: String,
        status: String
    )

// --------------------------------
// DELETE ANALYZED FRAMES
// --------------------------------

    @Query(
        """
    DELETE FROM captured_frames
    WHERE analysisStatus = 'ANALYZED'
    AND sessionId = :sessionId
    """
    )
    suspend fun deleteAnalyzedFramesForSession(
        sessionId: String
    )

// --------------------------------
// REVIEW QUEUE COUNT (7G PART 5)
// --------------------------------

    @Query(
        """
    SELECT COUNT(*)
    FROM captured_frames
    WHERE analysisStatus = 'NEEDS_REVIEW'
    """
    )
    suspend fun getNeedsReviewFrameCount(): Int

// --------------------------------
// MILESTONE 7T: RETENTION
// --------------------------------
//
// The retention worker operates on these queries. Only
// raw frame statuses (PENDING/PROCESSING/FAILED/ANALYZED)
// are ever bulk-deleted; the review queue and the
// labeled reference / feedback dataset survive.

    @Query(
        """
    SELECT
        sessionId,
        MAX(capturedAt) AS latestCapturedAt,
        COUNT(*) AS frameCount
    FROM captured_frames
    GROUP BY sessionId
    """
    )
    suspend fun getSessionFrameStats():
            List<com.example.feedsense.model.SessionFrameStats>

    @Query(
        """
    SELECT filePath
    FROM captured_frames
    WHERE sessionId = :sessionId
    AND analysisStatus IN ('PENDING', 'PROCESSING', 'FAILED', 'ANALYZED', 'FILTERED')
    ORDER BY capturedAt ASC
    LIMIT :limit
    """
    )
    suspend fun getDeletableFramePathsForSession(
        sessionId: String,
        limit: Int
    ): List<String>

    @Query(
        """
    SELECT filePath
    FROM captured_frames
    WHERE sessionId = :sessionId
    """
    )
    suspend fun getFramePathsForSession(
        sessionId: String
    ): List<String>

    @Query(
        """
    DELETE FROM captured_frames
    WHERE sessionId = :sessionId
    AND id IN (
        SELECT id
        FROM captured_frames
        WHERE sessionId = :sessionId
        AND analysisStatus IN ('PENDING', 'PROCESSING', 'FAILED', 'ANALYZED', 'FILTERED')
        ORDER BY capturedAt ASC
        LIMIT :limit
    )
    """
    )
    suspend fun deleteDeletableFramesForSession(
        sessionId: String,
        limit: Int
    )

    @Query(
        """
    SELECT *
    FROM captured_frames
    WHERE sessionId = :sessionId
    AND capturedAt BETWEEN :startTime AND :endTime
    ORDER BY capturedAt ASC
    """
    )
    fun getFramesForSessionInWindow(
        sessionId: String,
        startTime: LocalDateTime,
        endTime: LocalDateTime
    ): Flow<List<CapturedFrame>>

    @Query(
        """
    DELETE FROM captured_frames
    WHERE sessionId = :sessionId
    """
    )
    suspend fun deleteFramesForSession(
        sessionId: String
    )
}
