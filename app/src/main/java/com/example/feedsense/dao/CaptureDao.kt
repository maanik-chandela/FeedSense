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

    @Query(
        """
    SELECT DISTINCT sessionId
    FROM captured_frames
    WHERE analysisStatus = 'ANALYZED'
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
        analyzedAt = :analyzedAt
    WHERE id = :frameId
    """
    )
    suspend fun markAnalyzed(
        frameId: String,
        result: String,
        analyzedAt: LocalDateTime
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
        analyzedAt = :analyzedAt
    WHERE id = :frameId
    """
    )
    suspend fun markNeedsReview(
        frameId: String,
        result: String,
        analyzedAt: LocalDateTime
    )

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
}
