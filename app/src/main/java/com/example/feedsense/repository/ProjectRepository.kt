package com.example.feedsense.repository

import com.example.feedsense.dao.ProjectDao
import com.example.feedsense.model.ResearchProject
import kotlinx.coroutines.flow.Flow

class ProjectRepository(
    private val projectDao: ProjectDao
) {

    val allProjects: Flow<List<ResearchProject>> =
        projectDao.getAllProjects()

    suspend fun insert(project: ResearchProject) {
        projectDao.insert(project)
    }

    suspend fun update(project: ResearchProject) {
        projectDao.update(project)
    }

    suspend fun delete(project: ResearchProject) {
        projectDao.delete(project)
    }
}