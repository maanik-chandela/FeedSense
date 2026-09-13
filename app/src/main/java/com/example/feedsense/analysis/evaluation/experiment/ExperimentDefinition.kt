package com.example.feedsense.analysis.evaluation.experiment

import com.example.feedsense.analysis.evaluation.comparative.ComparativeConfig
import java.time.LocalDateTime
import java.util.UUID

/**
 * Milestone 8B-9.
 *
 * Immutable definition of a controlled real-data experiment.
 *
 * An experiment runs a baseline-vs-evidence-aware paired
 * comparison over a REAL population of observations. It is purely
 * observational and evaluative: it never trains, never mutates a
 * FeedItem, session, baseline prediction, or evaluation record,
 * and never changes any production pipeline.
 *
 * Every experiment is fully described by this immutable value so
 * it can be recorded, reproduced, and audited without ambiguity.
 * The dataset selection is bounded (an explicit maximum) so no run
 * performs an unbounded database scan.
 */
data class ExperimentDefinition(
    val experimentId: String = UUID.randomUUID().toString(),

    val name: String,

    /**
     * How the observation population is chosen. See
     * [ExperimentDatasetMode].
     */
    val datasetMode: ExperimentDatasetMode,

    // --------------------------------
    // SELECTION PARAMETERS (mode-dependent)
    // --------------------------------
    val sessionId: String? = null,
    val itemIds: Set<String> = emptySet(),
    val startDate: LocalDateTime? = null,
    val endDate: LocalDateTime? = null,
    val datasetVersion: String? = null,

    /**
     * A nominated dataset version used for training / validation
     * (never the experiment's own test population). The leakage
     * guard asserts that no observation in this experiment's
     * population is also present in the training/validation
     * dataset, preventing train-on-test contamination.
     */
    val trainingDatasetVersion: String? = null,

    /**
     * Upper bound on the number of paired observations included.
     * Enforces bounded execution and a deterministic, capped
     * experiment. Always required to be >= 1.
     */
    val maxItems: Int,

    /**
     * Optional scope restriction to a single annotator / ground-truth
     * author id. When supplied, only ground-truth records authored
     * by this annotator are used as the item's truth.
     */
    val annotatorId: String? = null,

    /**
     * The comparison configuration reused from the 8B-8
     * comparative layer (sample-size guards, versions).
     */
    val comparativeConfig: ComparativeConfig = ComparativeConfig.DEFAULT,

    val createdAt: LocalDateTime = LocalDateTime.now(),

    // --------------------------------
    // VERSION PROVENANCE
    // --------------------------------
    val baselineModelVersion: String? = null,
    val evidenceAwareModelVersion: String? = null,
    val decisionVersion: String? = null,
    val experimentVersion: String = "experiment-v1"
) {
    init {
        require(maxItems >= 1) { "maxItems must be >= 1" }
        require(name.isNotBlank()) { "name must be non-blank" }
        require(dateRangeValid()) {
            "startDate must not be after endDate"
        }
        require(selectionConsistent()) {
            "selection parameters must be consistent with the dataset mode"
        }
    }

    private fun dateRangeValid(): Boolean {
        if (startDate == null || endDate == null) return true
        return !startDate.isAfter(endDate)
    }

    private fun selectionConsistent(): Boolean {
        return when (datasetMode) {
            ExperimentDatasetMode.SESSION -> sessionId != null
            ExperimentDatasetMode.ITEM_SET -> itemIds.isNotEmpty()
            ExperimentDatasetMode.DATE_RANGE ->
                startDate != null || endDate != null
            ExperimentDatasetMode.EVALUATION_DATASET ->
                datasetVersion != null
            ExperimentDatasetMode.ALL_EVALUATED -> true
        }
    }
}

/**
 * How the observation population of an experiment is selected.
 *
 *   SESSION            - all evaluated items of one research session
 *   ITEM_SET           - an explicit bounded set of item ids
 *   DATE_RANGE         - evaluated items recorded within a time window
 *   EVALUATION_DATASET - a frozen, curated dataset version cohort
 *   ALL_EVALUATED      - every item with a comparable ground truth
 */
enum class ExperimentDatasetMode(val label: String) {
    SESSION("SESSION"),
    ITEM_SET("ITEM_SET"),
    DATE_RANGE("DATE_RANGE"),
    EVALUATION_DATASET("EVALUATION_DATASET"),
    ALL_EVALUATED("ALL_EVALUATED")
}
