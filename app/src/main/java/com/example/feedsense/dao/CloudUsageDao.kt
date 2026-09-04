package com.example.feedsense.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.feedsense.model.CloudUsageRecord
import com.example.feedsense.model.CloudUsageState
import kotlinx.coroutines.flow.Flow

@Dao
interface CloudUsageDao {

    @Insert
    suspend fun insert(
        record: CloudUsageRecord
    )

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertState(
        state: CloudUsageState
    )

    @Query(
        """
    SELECT *
    FROM cloud_usage_state
    WHERE id = 'state'
    LIMIT 1
    """
    )
    suspend fun getState(): CloudUsageState?

    @Query(
        """
    SELECT *
    FROM cloud_usage_records
    ORDER BY requestedAt DESC
    LIMIT :limit
    """
    )
    fun getRecentRecords(
        limit: Int
    ): Flow<List<CloudUsageRecord>>

    @Query(
        """
    SELECT *
    FROM cloud_usage_records
    WHERE allowed = 1
    ORDER BY requestedAt DESC
    """
    )
    suspend fun getAllowedRecords(): List<CloudUsageRecord>

    @Query(
        """
    SELECT COUNT(*)
    FROM cloud_usage_records
    WHERE allowed = 1
    """
    )
    suspend fun getAllowedRequestCount(): Int

    @Query(
        """
    SELECT COALESCE(SUM(estimatedCostRupees), 0)
    FROM cloud_usage_records
    WHERE allowed = 1
    """
    )
    suspend fun getTotalEstimatedCost(): Double
}
