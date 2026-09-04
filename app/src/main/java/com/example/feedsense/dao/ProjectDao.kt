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

    /*
     * Live counts instead of the entity defaults.
     *
     * ResearchProject.sessionCount and feedItemCount are
     * stored columns that are never written, so the cards
     * in the "Open Existing Projects" screen always showed
     * zero. Computing the counts here keeps them accurate
     * as sessions and feed items are created.
     */
    @Query(
        """
        SELECT
            p.id AS id,
            p.title AS title,
            p.description AS description,
            p.researchQuestion AS researchQuestion,
            p.hypothesis AS hypothesis,
            p.platform AS platform,
            p.owner AS owner,
            p.createdAt AS createdAt,
            p.updatedAt AS updatedAt,
            p.archived AS archived,
            (
                SELECT COUNT(*)
                FROM research_sessions s
                WHERE s.projectId = p.id
            ) AS sessionCount,
            (
                SELECT COUNT(*)
                FROM feed_items fi
                WHERE fi.sessionId IN (
                    SELECT s.id
                    FROM research_sessions s
                    WHERE s.projectId = p.id
                )
            ) AS feedItemCount
        FROM research_projects p
        ORDER BY p.createdAt DESC
        """
    )
    fun getAllProjectsWithCounts(): Flow<List<ResearchProject>>
}