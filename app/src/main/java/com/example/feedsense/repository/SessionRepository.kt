package com.example.feedsense.repository

import com.example.feedsense.dao.SessionDao
import com.example.feedsense.model.ResearchSession
import kotlinx.coroutines.flow.Flow

class SessionRepository(
    private val sessionDao: SessionDao
) {

    fun getSessionsForProject(
        projectId: String
    ): Flow<List<ResearchSession>> {
        return sessionDao.getSessionsForProject(projectId)
    }

    suspend fun getLatestSession(
        projectId: String
    ): ResearchSession? {
        return sessionDao.getLatestSession(projectId)
    }

    suspend fun getActiveSession(): ResearchSession? {
        return sessionDao.getActiveSession()
    }

    suspend fun insert(session: ResearchSession) {
        sessionDao.insert(session)
    }

    suspend fun update(session: ResearchSession) {
        sessionDao.update(session)
    }

    suspend fun endSession(
        sessionId: String,
        endedAt: String
    ) {
        sessionDao.endSession(sessionId, endedAt)
    }
}