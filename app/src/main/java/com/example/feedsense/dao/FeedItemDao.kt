package com.example.feedsense.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.feedsense.model.FeedItem
import kotlinx.coroutines.flow.Flow

@Dao
interface FeedItemDao {

    @Insert
    suspend fun insert(
        item: FeedItem
    )

    @Update
    suspend fun update(
        item: FeedItem
    )

    @Delete
    suspend fun delete(
        item: FeedItem
    )

    @Query(
        """
    SELECT *
    FROM feed_items
    WHERE sessionId = :sessionId
    ORDER BY startTime ASC
    """
    )
    fun getItemsForSession(
        sessionId: String
    ): Flow<List<FeedItem>>

    @Query(
        """
    SELECT COUNT(*)
    FROM feed_items
    WHERE sessionId = :sessionId
    """
    )
    suspend fun getItemCount(
        sessionId: String
    ): Int

    @Query(
        """
    DELETE FROM feed_items
    WHERE sessionId = :sessionId
    """
    )
    suspend fun deleteForSession(
        sessionId: String
    )
}
