package com.example.feedsense.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.feedsense.model.FeedItem
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow

data class CategoryCount(
    val category: String,
    val count: Int
)

data class PlatformCount(
    val platform: String,
    val count: Int
)

data class ContentTypeCount(
    val contentType: String,
    val count: Int
)

data class UncertaintyCount(
    val uncertaintyLevel: String,
    val count: Int
)

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
    SELECT *
    FROM feed_items
    WHERE id = :itemId
    LIMIT 1
    """
    )
    suspend fun getItemById(
        itemId: String
    ): FeedItem?

    @Query(
        """
    DELETE FROM feed_items
    WHERE sessionId = :sessionId
    """
    )
    suspend fun deleteForSession(
        sessionId: String
    )

    @Query(
        """
    SELECT MAX(updatedAt)
    FROM feed_items
    WHERE sessionId = :sessionId
    """
    )
    suspend fun getLatestItemUpdatedAt(
        sessionId: String
    ): LocalDateTime?

    // --------------------------------
    // SESSION ANALYTICS
    // --------------------------------

    @Query("SELECT COUNT(*) FROM feed_items WHERE sessionId = :sessionId AND source = 'AI'")
    suspend fun getAICountForSession(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM feed_items WHERE sessionId = :sessionId AND source = 'MANUAL'")
    suspend fun getManualCountForSession(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM feed_items WHERE sessionId = :sessionId AND skipped = 1")
    suspend fun getSkippedCountForSession(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM feed_items WHERE sessionId = :sessionId AND researcherLiked = 1")
    suspend fun getLikedCountForSession(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM feed_items WHERE sessionId = :sessionId AND researcherCommented = 1")
    suspend fun getCommentedCountForSession(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM feed_items WHERE sessionId = :sessionId AND researcherShared = 1")
    suspend fun getSharedCountForSession(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM feed_items WHERE sessionId = :sessionId AND researcherSaved = 1")
    suspend fun getSavedCountForSession(sessionId: String): Int

    @Query("SELECT SUM(durationSeconds) FROM feed_items WHERE sessionId = :sessionId")
    suspend fun getTotalWatchTimeForSession(sessionId: String): Int?

    @Query("SELECT SUM(activeWatchDurationSeconds) FROM feed_items WHERE sessionId = :sessionId")
    suspend fun getActiveWatchTimeForSession(sessionId: String): Int?

    @Query("SELECT category, COUNT(*) as count FROM feed_items WHERE sessionId = :sessionId AND category IS NOT NULL GROUP BY category ORDER BY count DESC")
    suspend fun getCategoryBreakdownForSession(sessionId: String): List<CategoryCount>

    @Query("SELECT platform, COUNT(*) as count FROM feed_items WHERE sessionId = :sessionId AND platform IS NOT NULL GROUP BY platform ORDER BY count DESC")
    suspend fun getPlatformBreakdownForSession(sessionId: String): List<PlatformCount>

    @Query("SELECT contentType, COUNT(*) as count FROM feed_items WHERE sessionId = :sessionId GROUP BY contentType ORDER BY count DESC")
    suspend fun getContentTypeBreakdownForSession(sessionId: String): List<ContentTypeCount>

    @Query("SELECT uncertaintyLevel, COUNT(*) as count FROM feed_items WHERE sessionId = :sessionId GROUP BY uncertaintyLevel")
    suspend fun getUncertaintyBreakdownForSession(sessionId: String): List<UncertaintyCount>

    @Query("SELECT COUNT(*) FROM feed_items WHERE sessionId = :sessionId AND needsReview = 1")
    suspend fun getNeedsReviewCountForSession(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM feed_items WHERE sessionId = :sessionId AND source = 'MANUAL' AND researcherCategory IS NOT NULL")
    suspend fun getManualCorrectionsForSession(sessionId: String): Int

    // --------------------------------
    // GLOBAL MODEL PERFORMANCE
    // --------------------------------

    @Query("SELECT COUNT(*) FROM feed_items")
    suspend fun getTotalItemCount(): Int

    @Query("SELECT COUNT(*) FROM feed_items WHERE uncertaintyLevel = 'HIGH'")
    suspend fun getHighConfidenceItemCount(): Int

    @Query("SELECT COUNT(*) FROM feed_items WHERE needsReview = 1")
    suspend fun getNeedsReviewItemCount(): Int

    @Query(
        """
    SELECT *
    FROM feed_items
    ORDER BY startTime ASC
    """
    )
    suspend fun getAllItems(): List<FeedItem>
}
