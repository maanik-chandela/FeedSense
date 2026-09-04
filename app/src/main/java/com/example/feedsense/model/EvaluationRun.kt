package com.example.feedsense.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.util.UUID

// --------------------------------
// EVALUATION RUN (Milestone 8A-3)
// --------------------------------
//
// A frozen, reproducible snapshot of one EvaluationReport.
// One row per run: it records the provenance (dataset +
// model + method version), the config that produced it, the
// cohort sizes, when it ran, and the full serialized report
// (all metric sections, denominators, eligibility trace).
//
// This table is write-once from the engine's perspective; a
// report is never mutated after it is persisted. It lets
// model/dataset comparison be a matter of reading two runs
// with the same config, rather than recomputing on-demand
// against a moving dataset.

@Entity(
    tableName = "evaluation_runs",
    indices = [
        Index(
            name = "idx_evaluation_runs_dataset",
            value = ["datasetVersion"]
        ),
        Index(
            name = "idx_evaluation_runs_model",
            value = ["modelVersion"]
        ),
        Index(
            name = "idx_evaluation_runs_created",
            value = ["createdAt"]
        )
    ]
)
data class EvaluationRun(

    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val runId: String,

    val datasetVersion: String? = null,

    val modelVersion: String? = null,

    val evaluationMethodVersion: String = "1.0.0",

    val createdAt: LocalDateTime = LocalDateTime.now(),

    // Serialized config (JSON) + full report (JSON).
    val configJson: String = "{}",

    val reportJson: String = "{}",

    // Cohort sizes for cheap inspection without parsing JSON.
    val totalItems: Int = 0,

    val eligibleItems: Int = 0,

    val excludedItems: Int = 0,

    val description: String? = null
)