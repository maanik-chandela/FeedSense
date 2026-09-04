package com.example.feedsense.repository

import com.example.feedsense.analysis.evaluation.Eligibility
import com.example.feedsense.analysis.evaluation.EvaluationEngine
import com.example.feedsense.analysis.evaluation.EvaluationUnit
import com.example.feedsense.analysis.evaluation.EvaluatorConfig
import com.example.feedsense.analysis.evaluation.ReportJson
import com.example.feedsense.analysis.evaluation.ConfigJson
import com.example.feedsense.dao.EvaluationDao
import com.example.feedsense.dao.EvaluationRunDao
import com.example.feedsense.model.EvaluationRun
import java.time.LocalDateTime
import java.util.UUID

// --------------------------------
// EVALUATION RUN REPOSITORY (Milestone 8A-3)
// --------------------------------
//
// Assembles a fully-loaded evaluation cohort for a dataset,
// applies eligibility/exclusion, computes the report with the
// pure engine, and persists it as an EvaluationRun. Both the
// config and the full report are frozen to JSON so a run is
// reproducible and auditable without a live dataset.
//
// This layer is read-only with respect to the AI pipeline and
// the 8A-1/8A-2 tables: it inserts a NEW row into
// evaluation_runs and never touches any other table.

class EvaluationRunRepository(
    private val evaluationDao: EvaluationDao,
    private val evaluationRunDao: EvaluationRunDao
) {

    /**
     * Runs a full evaluation over a dataset cohort and persists
     * the frozen report.
     *
     * @param datasetVersion the curated dataset to evaluate
     * @param config the frozen run config
     * @param modelVersion optional model label recorded on the run
     * @param runId optional run id (fresh UUID if omitted)
     * @return the persisted EvaluationRun
     */
    suspend fun runEvaluation(
        datasetVersion: String,
        config: EvaluatorConfig,
        modelVersion: String? = null,
        runId: String = UUID.randomUUID().toString(),
        description: String? = null
    ): EvaluationRun {

        val items =
            evaluationDao.getItemsForDataset(datasetVersion)

        // Lookups for eligibility.
        val predictionsByItem =
            evaluationDao.getPredictionsForItems(items.map { it.id })
                .associateBy { it.evaluationItemId }
        val truthsByItem =
            evaluationDao.getGroundTruthsForItems(items.map { it.id })
                // For the engine, resolve to one current truth per
                // item: prefer a non-null annotator's record, else any.
                .groupBy { it.evaluationItemId }
                .mapValues { (_, truths) ->
                    truths.firstOrNull { it.annotatorId != null }
                        ?: truths.first()
                }
        val resultsByItem =
            evaluationDao.getResultsForItems(items.map { it.id })
                .groupBy { it.evaluationItemId }
                .mapValues { (_, results) -> results.first() }

        val selection =
            Eligibility.select(
                items = items,
                config = config,
                hasPrediction = { predictionsByItem.containsKey(it) },
                hasTruth = { truthsByItem.containsKey(it) },
                hasResult = { resultsByItem.containsKey(it) }
            )

        val units = selection.eligible.mapNotNull { item ->
            val prediction = predictionsByItem[item.id] ?: return@mapNotNull null
            val truth = truthsByItem[item.id] ?: return@mapNotNull null
            val result = resultsByItem[item.id] ?: return@mapNotNull null
            EvaluationUnit(
                item = item,
                prediction = prediction,
                truth = truth,
                result = result
            )
        }

        val report =
            EvaluationEngine.evaluate(
                units = units,
                excluded = selection.excluded,
                config = config,
                runId = runId,
                datasetVersion = datasetVersion,
                modelVersion = modelVersion,
                createdAt = LocalDateTime.now()
            )

        val run =
            EvaluationRun(
                runId = runId,
                datasetVersion = datasetVersion,
                modelVersion = modelVersion,
                evaluationMethodVersion = report.evaluationMethodVersion,
                createdAt = report.createdAt,
                configJson = ConfigJson.toJson(config),
                reportJson = ReportJson.toJson(report),
                totalItems = report.totalItems,
                eligibleItems = report.eligibleItems,
                excludedItems = report.excludedItems,
                description = description
            )

        evaluationRunDao.insertRun(run)
        return run
    }

    suspend fun getRecentRuns(limit: Int = 20): List<EvaluationRun> {
        return evaluationRunDao.getRecentRuns(limit)
    }

    suspend fun getRunByRunId(runId: String): EvaluationRun? {
        return evaluationRunDao.getRunByRunId(runId)
    }
}