package com.example.feedsense.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feedsense.model.CapturedFrame
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.ResearchObservation
import com.example.feedsense.model.ResearchSession
import com.example.feedsense.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class SessionViewModel(
    private val repository: SessionRepository
) : ViewModel() {

    // -------------------------
    // SESSIONS
    // -------------------------

    fun getSessionsForProject(
        projectId: String
    ): Flow<List<ResearchSession>> {

        return repository
            .getSessionsForProject(projectId)
    }

    fun startSession(
        projectId: String,
        title: String
    ) {

        viewModelScope.launch {

            val activeSession =
                repository
                    .getActiveSessionForProject(
                        projectId
                    )

            if (activeSession != null) {
                return@launch
            }

            val session =
                ResearchSession(
                    projectId = projectId,
                    title = title
                )

            repository.insert(
                session
            )
        }
    }

    fun endSession(
        sessionId: String
    ) {

        viewModelScope.launch {

            repository.endSession(
                sessionId
            )
        }
    }

    fun reopenSession(
        sessionId: String
    ) {

        viewModelScope.launch {

            repository.reopenSession(
                sessionId
            )
        }
    }

    // -------------------------
    // OBSERVATIONS
    // -------------------------

    fun getObservationsForSession(
        sessionId: String
    ): Flow<List<ResearchObservation>> {

        return repository
            .getObservationsForSession(
                sessionId
            )
    }

    fun addObservation(
        sessionId: String,
        text: String
    ) {

        if (text.isBlank()) {
            return
        }

        viewModelScope.launch {

            val observation =
                ResearchObservation(
                    sessionId = sessionId,
                    text = text.trim()
                )

            repository.insertObservation(
                observation
            )

            repository.incrementObservationCount(
                sessionId
            )
        }
    }

    // -------------------------
    // CAPTURED FRAMES
    // -------------------------

    fun getFramesForSession(
        sessionId: String
    ): Flow<List<CapturedFrame>> {

        return repository
            .getFramesForSession(
                sessionId
            )
    }

    fun addCapturedFrame(
        sessionId: String,
        filePath: String
    ) {

        viewModelScope.launch {

            val frame =
                CapturedFrame(
                    sessionId = sessionId,
                    filePath = filePath
                )

            repository.insertCapturedFrame(
                frame
            )
        }
    }

    /*
     * Kept for cases where we specifically need
     * the numeric count.
     *
     * Note that this function does not return the
     * count directly because database access is
     * asynchronous.
     */
    fun getFrameCount(
        sessionId: String,
        onResult: (Int) -> Unit
    ) {

        viewModelScope.launch {

            val count =
                repository.getFrameCount(
                    sessionId
                )

            onResult(count)
        }
    }

    // -------------------------
    // FEED ITEMS
    // -------------------------

    fun getFeedItemsForSession(
        sessionId: String
    ): Flow<List<FeedItem>> {

        return repository
            .getFeedItemsForSession(
                sessionId
            )
    }
}