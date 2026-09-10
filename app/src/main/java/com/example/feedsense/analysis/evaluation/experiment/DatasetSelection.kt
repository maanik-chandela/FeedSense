package com.example.feedsense.analysis.evaluation.experiment

import com.example.feedsense.analysis.evaluation.comparative.PairedPrediction
import com.example.feedsense.analysis.evaluation.comparative.PairedPredictionBuilder
import com.example.feedsense.analysis.evidence.decision.ItemPredictionResult
import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.EvaluationItem
import com.example.feedsense.model.GroundTruth

/**
 * Milestone 8B-9.
 *
 * Dataset selection for a controlled experiment.
 *
 * Pure, deterministic selection over pre-fetched evaluation data.
 * It applies the [ExperimentDefinition]'s dataset mode, enforces the
 * bounded maximum, honours the SAME-SAMPLE guarantee (each item is
 * used exactly once with a single authoritative ground truth), and
 * assembles immutable [PairedPrediction]s for the experiment.
 *
 * The selection never mutates anything. It only reads the supplied
 * collections and returns an immutable [SelectionResult].
 */
object DatasetSelection {

    /**
     * Result of selecting the experiment population.
     *
     * @param pairs        the assembled paired observations (already
     *                     bounded and de-duplicated by item)
     * @param candidateItems the items that matched the mode BEFORE the
     *                     cap and truth-availability filters
     * @param excludedNoTruth items with no usable ground truth
     * @param excludedCapCount how many matching items were dropped because
     *                     they exceeded [ExperimentDefinition.maxItems]
     */
    data class SelectionResult(
        val pairs: List<PairedPrediction>,
        val candidateItems: List<EvaluationItem>,
        val excludedNoTruth: List<EvaluationItem>,
        val excludedNoBaseline: List<EvaluationItem>,
        val excludedNoEvidenceAware: List<EvaluationItem>,
        val excludedCapCount: Int,
        val selectedItemIds: Set<String>
    )

    /**
     * Selects and pairs the experiment population.
     *
     * @param items        all candidate evaluation items
     * @param baselinePredictions all baseline AI prediction snapshots
     * @param groundTruths  all ground-truth records
     * @param evidenceAwareDecisions all evidence-aware decisions (keyed by
     *                     feedItemId)
     * @param definition   the immutable experiment definition
     */
    fun select(
        items: List<EvaluationItem>,
        baselinePredictions: List<AiPredictionRecord>,
        groundTruths: List<GroundTruth>,
        evidenceAwareDecisions: List<ItemPredictionResult>,
        definition: ExperimentDefinition
    ): SelectionResult {
        // 1. Apply the dataset-mode filter to the item universe.
        val matched = matchByMode(items, definition)
            .sortedBy { it.id }

        // 2. Bound the population deterministically (deterministic id
        //    ordering, then the cap) - bounded execution.
        val capped = matched.take(definition.maxItems)
        val capDrop = matched.size - capped.size
        val candidateIds = capped.map { it.id }.toSet()

        val truthByItem = groupTruthsByItem(
            groundTruths, definition.annotatorId
        )
        val baselineByItem = baselinePredictions
            .associateBy { it.evaluationItemId }
        val decisionByFeed = evidenceAwareDecisions
            .associateBy { it.feedItemId }
        val feedByItem = items.associateBy { it.id }

        val pairBuf = mutableListOf<PairedPrediction>()
        val excludedNoTruth = mutableListOf<EvaluationItem>()
        val excludedNoBaseline = mutableListOf<EvaluationItem>()
        val excludedNoEvidenceAware = mutableListOf<EvaluationItem>()

        for (item in capped) {
            val truth = truthByItem[item.id]
            val baseline = baselineByItem[item.id]
            if (truth == null || truth.category == null) {
                excludedNoTruth += item
                continue
            }
            if (baseline == null) {
                excludedNoBaseline += item
                continue
            }
            val feed = feedByItem[item.id]
            val decision = feed?.let { decisionByFeed[it.feedItemId] }
            if (decision == null) {
                // No evidence-aware decision available: the item cannot
                // be paired. It is excluded (never assumed correct).
                excludedNoEvidenceAware += item
                continue
            }
            val pair = runCatching {
                PairedPredictionBuilder.build(
                    item = item,
                    truth = truth,
                    baseline = baseline,
                    eightBDecision = decision
                )
            }.getOrNull()
            if (pair != null) pairBuf += pair
        }

        return SelectionResult(
            pairs = pairBuf,
            candidateItems = capped,
            excludedNoTruth = excludedNoTruth,
            excludedNoBaseline = excludedNoBaseline,
            excludedNoEvidenceAware = excludedNoEvidenceAware,
            excludedCapCount = capDrop,
            selectedItemIds = pairBuf.map { it.item.id }.toSet()
        )
    }

    /**
     * Applies the dataset-mode filter. Only items matching the mode
     * are candidates. Exposed as internal so the read-only
     * Room-backed data source reuses the same filtering logic.
     */
    internal fun matchByMode(
        items: List<EvaluationItem>,
        definition: ExperimentDefinition
    ): List<EvaluationItem> {
        return when (definition.datasetMode) {
            ExperimentDatasetMode.SESSION ->
                items.filter { it.sessionId == definition.sessionId }
            ExperimentDatasetMode.ITEM_SET ->
                items.filter { it.id in definition.itemIds }
            ExperimentDatasetMode.DATE_RANGE -> items.filter { it ->
                val inStart = definition.startDate == null ||
                    !it.enqueuedAt.isBefore(definition.startDate)
                val inEnd = definition.endDate == null ||
                    !it.enqueuedAt.isAfter(definition.endDate)
                inStart && inEnd
            }
            ExperimentDatasetMode.EVALUATION_DATASET ->
                items.filter { it.datasetVersion == definition.datasetVersion }
            ExperimentDatasetMode.ALL_EVALUATED -> items
        }
    }

    /**
     * Groups a single authoritative ground truth per item.
     *
     * When [annotatorId] is supplied, only that annotator's current
     * record counts. Otherwise the earliest recorded CLEAR record is
     * preferred; a comparable (non-UNKNOWN) record is chosen over an
     * UNKNOWN one. This guarantees SAME-SAMPLE and single-truth
     * semantics: one item, one truth, judged once.
     */
    private fun groupTruthsByItem(
        truths: List<GroundTruth>,
        annotatorId: String?
    ): Map<String, GroundTruth> {
        val byItem = truths
            .filter { it.category != null }
            .filter { t -> annotatorId == null || t.annotatorId == annotatorId }
            .groupBy { it.evaluationItemId }

        return byItem.mapValues { (_, recs) ->
            recs.sortedWith(
                compareBy<GroundTruth> {
                    if (it.ambiguity ==
                        GroundTruth.AMBIGUITY_UNKNOWN
                    ) 1 else 0
                }.thenBy { it.recordedAt }
            ).first()
        }
    }
}
