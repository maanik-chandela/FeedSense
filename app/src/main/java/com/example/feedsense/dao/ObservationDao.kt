package com.example.feedsense.dao
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.feedsense.model.ResearchObservation
import kotlinx.coroutines.flow.Flow

@Dao
interface ObservationDao {

    @Insert
    suspend fun insert(
        observation: ResearchObservation
    )

    @Update
    suspend fun update(
        observation: ResearchObservation
    )

    @Delete
    suspend fun delete(
        observation: ResearchObservation
    )

    @Query("""
        SELECT * FROM research_observations
        WHERE sessionId = :sessionId
        ORDER BY createdAt DESC
    """)
    fun getObservationsForSession(
        sessionId: String
    ): Flow<List<ResearchObservation>>

    @Query("""
        SELECT COUNT(*) FROM research_observations
        WHERE sessionId = :sessionId
    """)
    suspend fun getObservationCount(
        sessionId: String
    ): Int

    @Query("""
        DELETE FROM research_observations
        WHERE sessionId = :sessionId
        AND source = 'AUTO'
    """)
    suspend fun deleteAutoForSession(
        sessionId: String
    )

    /*
     * Milestone 7T. Full wipe of a session's
     * observations (manual + auto) used by the retention
     * worker when purging a whole session.
     */
    @Query("""
        DELETE FROM research_observations
        WHERE sessionId = :sessionId
    """)
    suspend fun deleteAllForSession(
        sessionId: String
    )

    /*
     * Milestone 7T. Unbounded read for the JSON dataset
     * export.
     */
    @Query("""
        SELECT *
        FROM research_observations
        ORDER BY createdAt ASC
    """)
    suspend fun getAll(): List<ResearchObservation>
}