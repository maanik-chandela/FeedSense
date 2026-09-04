package com.example.feedsense.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.feedsense.model.ModelFeedback

// --------------------------------
// MODEL FEEDBACK DAO
// --------------------------------
//
// Milestone 7K (Parts 1 and 4). The local learning /
// feedback dataset. Every confirmed correction is one
// row, preserving the original prediction alongside the
// corrected truth.
//

@Dao
interface ModelFeedbackDao {

    @Insert
    suspend fun insert(
        feedback: ModelFeedback
    )

    @Delete
    suspend fun delete(
        feedback: ModelFeedback
    )

    @Query(
        """
    SELECT *
    FROM model_feedback
    ORDER BY createdAt DESC
    """
    )
    suspend fun getAll(): List<ModelFeedback>

    @Query(
        """
    SELECT *
    FROM model_feedback
    WHERE sessionId = :sessionId
    ORDER BY createdAt DESC
    """
    )
    suspend fun getForSession(
        sessionId: String
    ): List<ModelFeedback>

    @Query(
        """
    SELECT *
    FROM model_feedback
    WHERE feedItemId = :feedItemId
    ORDER BY createdAt DESC
    """
    )
    suspend fun getForFeedItem(
        feedItemId: String
    ): List<ModelFeedback>

    @Query(
        """
    SELECT COUNT(*)
    FROM model_feedback
    """
    )
    suspend fun getCount(): Int

    @Query(
        """
    DELETE FROM model_feedback
    WHERE sessionId = :sessionId
    """
    )
    suspend fun deleteForSession(
        sessionId: String
    )
}
