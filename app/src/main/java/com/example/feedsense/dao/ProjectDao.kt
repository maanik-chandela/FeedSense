package com.example.feedsense.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.feedsense.model.ResearchProject
import kotlinx.coroutines.flow.Flow

@Dao
interface ProjectDao {

    @Insert
    suspend fun insert(project: ResearchProject)

    @Update
    suspend fun update(project: ResearchProject)

    @Delete
    suspend fun delete(project: ResearchProject)

    @Query("SELECT * FROM research_projects ORDER BY createdAt DESC")
    fun getAllProjects(): Flow<List<ResearchProject>>
}