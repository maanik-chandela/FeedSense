package com.example.feedsense.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feedsense.model.ResearchSession
import com.example.feedsense.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class SessionViewModel(
    private val repository: SessionRepository
) : ViewModel() {

    fun getSessionsForProject(
        projectId: String
    ): Flow<List<ResearchSession>> {
        return repository.getSessionsForProject(projectId)
    }

    fun startSession(
        projectId: String,
        title: String
    ) {

        viewModelScope.launch {

            val activeSession =
                repository.getActiveSession()

            if (activeSession != null) {
                return@launch
            }

            val session = ResearchSession(
                projectId = projectId,
                title = title
            )

            repository.insert(session)
        }
    }

    fun endSession(
        sessionId: String
    ) {

        viewModelScope.launch {
            repository.endSession(sessionId)
        }
    }
}