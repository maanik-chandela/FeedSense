package com.example.feedsense.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feedsense.model.ResearchProject
import com.example.feedsense.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class ProjectViewModel(
    private val repository: ProjectRepository
) : ViewModel() {

    val allProjects: Flow<List<ResearchProject>> =
        repository.allProjects

    var currentProject by mutableStateOf<ResearchProject?>(null)
        private set

    fun selectProject(project: ResearchProject) {
        currentProject = project
    }

    fun createProject(
        title: String,
        description: String,
        researchQuestion: String,
        hypothesis: String,
        platform: String,
        owner: String
    ) {

        val project = ResearchProject(
            title = title,
            description = description,
            researchQuestion = researchQuestion,
            hypothesis = hypothesis,
            platform = platform,
            owner = owner
        )

        currentProject = project

        insertProject(project)
    }

    fun insertProject(project: ResearchProject) {
        viewModelScope.launch {
            repository.insert(project)
        }
    }

    fun updateProject(project: ResearchProject) {
        viewModelScope.launch {
            repository.update(project)
        }
    }

    fun deleteProject(project: ResearchProject) {
        viewModelScope.launch {
            repository.delete(project)
        }
    }
}