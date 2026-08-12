package com.example.feedsense.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
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
}