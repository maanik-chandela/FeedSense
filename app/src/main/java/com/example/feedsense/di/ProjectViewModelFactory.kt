package com.example.feedsense.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.feedsense.repository.ProjectRepository
import com.example.feedsense.repository.SessionRepository
import com.example.feedsense.viewmodel.ProjectViewModel
import com.example.feedsense.viewmodel.SessionViewModel

class ProjectViewModelFactory(
    private val projectRepository: ProjectRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (modelClass.isAssignableFrom(ProjectViewModel::class.java)) {
            return ProjectViewModel(
                projectRepository
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class"
        )
    }
}

class SessionViewModelFactory(
    private val sessionRepository: SessionRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (modelClass.isAssignableFrom(SessionViewModel::class.java)) {
            return SessionViewModel(
                sessionRepository
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel class"
        )
    }
}