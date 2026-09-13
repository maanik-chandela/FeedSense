package com.example.feedsense.analysis.evaluation.experiment

import com.example.feedsense.analysis.evaluation.comparative.ComparativeEvaluator
import com.example.feedsense.analysis.evaluation.comparative.ComparativeReport

/**
 * Milestone 8B-9.
 *
 * Experiment runner (orchestrator).
 *
 * Orchestrates a controlled real-data experiment end to end:
 *
 *   1. reads observations through a read-only [ExperimentDataSource]
 *   2. selects the bounded population (dataset mode + same-sample)
 *   3. runs the leakage guard against any nominated training cohort
 *   4. assembles the dataset quality report
 *   5. delegates the full comparative analysis to the reused 8B-8
 *      [ComparativeEvaluator]
 *   6. computes the experiment's five-way outcome, McNemar-ready
 *      contingency, and real-data status
 *   7. records an audit snapshot with an idempotency signature
 *
 * The runner is observational only and never mutates any stored
 * data. Running the same experiment over the same population is
 * idempotent: it yields the same signature and the same summary.
 */
class ExperimentRunner(
    private val dataSource: ExperimentDataSource
) {

    /**
     * Runs the experiment defined by [definition].
     *
     * @param trainingItems optional items of the nominated training /
     *                      validation cohort, used for the leakage
     *                      check; may be null when the guard should
     *                      only consult the definition's version label
     *                      (skipped when no version is set).
     */
    suspend fun run(
        definition: ExperimentDefinition,
        trainingItems: List<com.example.feedsense.model.EvaluationItem>? = null
    ): ExperimentSummary {
        val items = dataSource.fetchItems(definition)
        val itemIds = items.map { it.id }
        val baselines = dataSource.fetchBaselinePredictions(itemIds)
        val truths = dataSource.fetchGroundTruths(itemIds)
        val decisions = dataSource.fetchEvidenceAwareDecisions(items)

        val selection = DatasetSelection.select(
            items = items,
            baselinePredictions = baselines,
            groundTruths = truths,
            evidenceAwareDecisions = decisions,
            definition = definition
        )

        val leakage = LeakageGuard.check(
            experimentItems = selection.pairs.map { it.item },
            trainingVersion = definition.trainingDatasetVersion,
            trainingItems = trainingItems
        )

        val quality = DatasetQualityReport.build(
            pairs = selection.pairs,
            candidateItems = selection.candidateItems.size
        )

        val report = ComparativeEvaluator.evaluate(
            ComparativeEvaluator.Input(
                pairs = selection.pairs,
                config = definition.comparativeConfig,
                datasetVersion = definition.datasetVersion,
                baselineModelVersion = definition.baselineModelVersion,
                fusionVersion = definition.evidenceAwareModelVersion,
                decisionVersion = definition.decisionVersion
            )
        )

        val tally = ExperimentAnalyzer.tally(selection.pairs)
        val mcnemar = ExperimentAnalyzer.mcnemarReady(
            tally = tally,
            minimumDiscordant =
                definition.comparativeConfig.minimumMcNemarDiscordantPairs
        )

        val realDataSufficient = ExperimentAnalyzer.realDataSufficient(
            eligible = tally.eligible,
            minimumForEffectSize =
                definition.comparativeConfig.minimumForEffectSize
        )

        val snapshot = ExperimentSnapshot(
            experimentId = definition.experimentId,
            experimentName = definition.name,
            definition = definition,
            selectedItemIds = selection.selectedItemIds,
            pairedCount = selection.pairs.size,
            baselineModelVersion = definition.baselineModelVersion,
            evidenceAwareModelVersion = definition.evidenceAwareModelVersion,
            decisionVersion = definition.decisionVersion,
            experimentVersion = definition.experimentVersion,
            auditEvents = listOf(
                ExperimentSnapshot.AuditEvent(
                    at = definition.createdAt,
                    action = "SELECT_PAIRED",
                    detail = "paired=${selection.pairs.size} " +
                        "mode=${definition.datasetMode.label} " +
                        "max=${definition.maxItems}"
                )
            )
        )

        return ExperimentSummary(
            definition = definition,
            snapshot = snapshot,
            selection = selection,
            leakage = leakage,
            datasetQuality = quality,
            outcome = tally,
            mcnemar = mcnemar,
            realDataStatus = if (realDataSufficient) {
                ExperimentSummary.RealDataStatus.SUFFICIENT_REAL_DATA
            } else {
                ExperimentSummary.RealDataStatus.INSUFFICIENT_REAL_DATA
            },
            baselineSafety = ExperimentSummary.BaselineSafetyStatement(),
            comparativeReport = report
        )
    }
}
