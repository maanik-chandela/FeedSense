package com.example.feedsense.analysis.evaluation.experiment

import com.example.feedsense.dao.EvaluationDao
import com.example.feedsense.model.EvaluationItem

/**
 * Milestone 8B-9.
 *
 * Room-backed [ExperimentDataSource] that reuses the EXISTING
 * [EvaluationDao] (no DAO / schema change). It reads evaluation
 * items, immutable baseline prediction snapshots, and ground-truth
 * records through the existing batched queries, then applies the
 * experiment dataset-mode selection in-memory.
 *
 * Evidence-aware decisions are NOT persisted (they are derived
 * on-demand by the 8B-7 decision layer from the live evidence), so
 * [provideEvidenceAwareDecisions] supplies them per experiment.
 *
 * This class is strictly READ-ONLY - it performs no insert, update,
 * or delete, and never modifies the baseline prediction, ground
 * truth, or feed item.
 */
class RoomExperimentDataSource(
    private val dao: EvaluationDao,
    private val provideEvidenceAwareDecisions:
        suspend (items: List<EvaluationItem>) ->
            List<com.example.feedsense.analysis.evidence.decision.ItemPredictionResult>
) : ExperimentDataSource {

    override suspend fun fetchItems(
        definition: ExperimentDefinition
    ): List<EvaluationItem> {
        val roomItems = when (definition.datasetMode) {
            ExperimentDatasetMode.SESSION ->
                dao.getItemsForSession(definition.sessionId ?: "")
            ExperimentDatasetMode.EVALUATION_DATASET ->
                dao.getItemsForDataset(definition.datasetVersion ?: "")
            else ->
                // ITEM_SET / DATE_RANGE / ALL_EVALUATED have no
                // single bounded DAO query (by design, no new query is
                // added). Enumerate by a bounded status read.
                dao.getItemsByStatusLimit(
                    status = com.example.feedsense.model.EvaluationItem
                        .STATUS_EVALUATED,
                    limit = definition.maxItems
                )
        }
        // Re-apply the exact mode + bounds deterministically.
        return DatasetSelection.matchByMode(
            items = roomItems,
            definition = definition
        ).sortedBy { it.id }.take(definition.maxItems)
    }

    override suspend fun fetchBaselinePredictions(
        itemIds: Collection<String>
    ): List<com.example.feedsense.model.AiPredictionRecord> =
        dao.getPredictionsForItems(itemIds)

    override suspend fun fetchGroundTruths(
        itemIds: Collection<String>
    ): List<com.example.feedsense.model.GroundTruth> =
        dao.getGroundTruthsForItems(itemIds)

    override suspend fun fetchEvidenceAwareDecisions(
        items: List<EvaluationItem>
    ): List<com.example.feedsense.analysis.evidence.decision.ItemPredictionResult> {
        return provideEvidenceAwareDecisions(items)
    }
}
