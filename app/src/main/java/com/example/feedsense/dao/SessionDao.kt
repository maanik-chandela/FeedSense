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

    /*
     * Milestone 7T. All currently active sessions, used
     * by the retention worker to never purge a session
     * that is still capturing.
     */
    @Query("""
        SELECT id FROM research_sessions
        WHERE active = 1
    """)
    suspend fun getActiveSessionIds(): List<String>

    /*
     * Milestone 7T. Unbounded read for the JSON dataset
     * export.
     */
    @Query("""
        SELECT * FROM research_sessions
        ORDER BY startedAt ASC
    """)
    suspend fun getAllSessions(): List<ResearchSession>

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
    SELECT * FROM research_sessions
    WHERE id = :sessionId
    LIMIT 1
""")
    suspend fun getSessionById(
        sessionId: String
    ): ResearchSession?

    /*
     * Milestone 7S. True when the session is still
     * active. The capture service checks this before
     * saving every frame so capture stops the moment a
     * session ends (battery + correctness). Returns null
     * when the session no longer exists.
     */
    @Query("""
    SELECT active
    FROM research_sessions
    WHERE id = :sessionId
    LIMIT 1
""")
    suspend fun isSessionActive(
        sessionId: String
    ): Boolean?

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