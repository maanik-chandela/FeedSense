package com.example.feedsense.analysis.evaluation.experiment

import com.example.feedsense.analysis.evidence.decision.ItemPredictionResult
import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.EvaluationItem
import com.example.feedsense.model.GroundTruth

/**
 * Milestone 8B-9.
 *
 * Read-only data source for a controlled experiment.
 *
 * Abstracts the retrieval of evaluation observations so the
 * selection / pairing / evaluation logic stays pure and
 * unit-testable without a database. Implementations must be
 * READ-ONLY: they never insert, update, or delete anything, and
 * they never modify the baseline prediction, ground truth, or
 * feed item.
 *
 * A default in-memory implementation ([InMemoryExperimentDataSource])
 * is provided for tests and for callers that already hold the
 * records; a Room-backed implementation reuses the existing
 * EvaluationDao without any schema change.
 */
interface ExperimentDataSource {

    /**
     * Fetches the candidate evaluation items, optionally pre-scoped
     * by the dataset mode. The returned collection is the universe
     * over which selection applies (already fetched with the DAO's
     * bounded queries where possible).
     */
    suspend fun fetchItems(definition: ExperimentDefinition): List<EvaluationItem>

    /**
     * Fetches the immutable baseline AI prediction snapshots for the
     * supplied item ids.
     */
    suspend fun fetchBaselinePredictions(itemIds: Collection<String>): List<AiPredictionRecord>

    /**
     * Fetches the ground-truth records for the supplied item ids.
     */
    suspend fun fetchGroundTruths(itemIds: Collection<String>): List<GroundTruth>

    /**
     * Fetches the evidence-aware decisions for the supplied items,
     * when they exist. An item may have no decision yet (then it is
     * excluded from the evidence-aware side and surfaced as the
     * caller's responsibility - never silently assumed correct).
     *
     * @param items the candidate evaluation items (so implementations
     *              can join to feed-item-keyed decisions)
     */
    suspend fun fetchEvidenceAwareDecisions(
        items: List<EvaluationItem>
    ): List<ItemPredictionResult>
}

/**
 * In-memory [ExperimentDataSource] over collections the caller
 * already holds. Immutable and safe: it exposes the given lists
 * read-only; callers retain ownership and nothing is mutated.
 */
class InMemoryExperimentDataSource(
    private val items: List<EvaluationItem>,
    private val baselinePredictions: List<AiPredictionRecord>,
    private val groundTruths: List<GroundTruth>,
    private val evidenceAwareDecisions: List<ItemPredictionResult>
) : ExperimentDataSource {

    override suspend fun fetchItems(
        definition: ExperimentDefinition
    ): List<EvaluationItem> = items

    override suspend fun fetchBaselinePredictions(
        itemIds: Collection<String>
    ): List<AiPredictionRecord> {
        val ids = itemIds.toSet()
        return baselinePredictions.filter { it.evaluationItemId in ids }
    }

    override suspend fun fetchGroundTruths(
        itemIds: Collection<String>
    ): List<GroundTruth> {
        val ids = itemIds.toSet()
        return groundTruths.filter { it.evaluationItemId in ids }
    }

    override suspend fun fetchEvidenceAwareDecisions(
        items: List<EvaluationItem>
    ): List<ItemPredictionResult> {
        val feedIds = items.map { it.feedItemId }.toSet()
        return evidenceAwareDecisions.filter {
            it.feedItemId in feedIds
        }
    }
}
