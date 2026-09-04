package com.example.feedsense.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.feedsense.model.EvaluationRun

/*
 * Milestone 8A-3. Persists and reads frozen evaluation-run
 * reports. Runs are write-once: insertRun stores a completed
 * snapshot that is never mutated.
 */
@Dao
interface EvaluationRunDao {

    @Insert
    suspend fun insertRun(run: EvaluationRun)

    @Query("SELECT * FROM evaluation_runs ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecentRuns(limit: Int): List<EvaluationRun>

    @Query("SELECT * FROM evaluation_runs WHERE runId = :runId LIMIT 1")
    suspend fun getRunByRunId(runId: String): EvaluationRun?

    @Query("SELECT * FROM evaluation_runs WHERE id = :id LIMIT 1")
    suspend fun getRunById(id: String): EvaluationRun?

    @Query("SELECT * FROM evaluation_runs WHERE datasetVersion = :datasetVersion")
    suspend fun getRunsForDataset(datasetVersion: String): List<EvaluationRun>

    @Query("SELECT COUNT(*) FROM evaluation_runs")
    suspend fun countRuns(): Int
}