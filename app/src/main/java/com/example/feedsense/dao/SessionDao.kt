package com.example.feedsense.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.feedsense.model.ResearchSession
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface SessionDao {

    @Insert
    suspend fun insert(
        session: ResearchSession
    )

    @Update
    suspend fun update(
        session: ResearchSession
    )

    @Delete
    suspend fun delete(
        session: ResearchSession
    )

    @Query("""
        SELECT * FROM research_sessions
        WHERE projectId = :projectId
        ORDER BY startedAt DESC
    """)
    fun getSessionsForProject(
        projectId: String
    ): Flow<List<ResearchSession>>

    @Query("""
        SELECT * FROM research_sessions
        WHERE projectId = :projectId
        ORDER BY startedAt DESC
        LIMIT 1
    """)
    suspend fun getLatestSession(
        projectId: String
    ): ResearchSession?

    @Query("""
        SELECT * FROM research_sessions
        WHERE projectId = :projectId
        AND active = 1
        LIMIT 1
    """)
    suspend fun getActiveSessionForProject(
        projectId: String
    ): ResearchSession?

    @Query("""
        UPDATE research_sessions
        SET active = 0,
            endedAt = :endedAt
        WHERE id = :sessionId
    """)
    suspend fun endSession(
        sessionId: String,
        endedAt: LocalDateTime
    )

    @Query("""
        UPDATE research_sessions
        SET active = 1,
            startedAt = :startedAt,
            endedAt = NULL
        WHERE id = :sessionId
    """)
    suspend fun reopenSession(
        sessionId: String,
        startedAt: LocalDateTime
    )
    @Query("""
    UPDATE research_sessions
    SET observationCount = observationCount + 1
    WHERE id = :sessionId
""")
    suspend fun incrementObservationCount(
        sessionId: String
    )

    @Query("""
    UPDATE research_sessions
    SET observationCount = :count
    WHERE id = :sessionId
""")
    suspend fun setObservationCount(
        sessionId: String,
        count: Int
    )
}