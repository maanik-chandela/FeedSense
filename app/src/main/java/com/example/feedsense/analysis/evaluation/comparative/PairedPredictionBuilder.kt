package com.example.feedsense.analysis.evaluation.comparative

import com.example.feedsense.analysis.evaluation.EvaluationUnit
import com.example.feedsense.analysis.evidence.decision.DecisionEvaluationBridge
import com.example.feedsense.analysis.evidence.decision.ItemPredictionResult
import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.EvaluationItem
import com.example.feedsense.model.EvaluationRecord
import com.example.feedsense.model.GroundTruth

/*
 * Milestone 8B-8.
 *
 * Paired prediction builder.
 *
 * Assembles a PairedPrediction from its parts. It is the ONLY
 * place where a baseline prediction and an 8B decision become a
 * comparable pair for the SAME item and SAME ground truth.
 *
 * Design:
 *   - The BASELINE AiPredictionRecord is consumed as-is: it is
 *     NEVER modified. This enforces the "baseline immutable"
 *     directive.
 *   - The 8B side goes through DecisionEvaluationBridge to
 *     materialize an immutable AiPredictionRecord from the
 *     evidence-aware decision.
 *   - Both verdicts are computed with the shared
 *     EvaluationRecord.fromComponents(prediction, truth), i.e.
 *     the SAME 8A comparison rules, so no double standard exists
 *     between the two sides.
 *
 * The builder itself is pure (no Room); callers supply the
 * already-fetched records.
 */
object PairedPredictionBuilder {

    /**
     * Builds a single paired comparison unit.
     *
     * @param item         the immutable evaluation item
     * @param truth        the single authoritative ground truth
     * @param baseline     the stored baseline AiPredictionRecord
     *                     (fetched by evaluationItemId); passed
     *                     unchanged into the pair
     * @param eightBDecision the 8B evidence-aware decision, if
     *                     available; may be null if only an
     *                     already-materialized 8B record exists.
     * @param eightBRecordOverride optional pre-built 8B record to
     *                     use instead of re-bridging the decision
     *                     (useful when only the record is at hand).
     */
    fun build(
        item: EvaluationItem,
        truth: GroundTruth,
        baseline: AiPredictionRecord,
        eightBDecision: ItemPredictionResult?,
        eightBRecordOverride: AiPredictionRecord? = null
    ): PairedPrediction {
        require(baseline.evaluationItemId == item.id) {
            "baseline record must reference the evaluation item"
        }

        val eightBRecord = eightBRecordOverride
            ?: eightBDecision?.let {
                DecisionEvaluationBridge.toAiPredictionRecord(
                    decision = it,
                    evaluationItemId = item.id
                )
            }
        require(eightBRecord != null) {
            "an 8B prediction record (or decision) must be supplied"
        }

        val baselineUnit = EvaluationUnit(
            item = item,
            prediction = baseline,
            truth = truth,
            result = EvaluationRecord.fromComponents(baseline, truth)
        )
        val eightBUnit = EvaluationUnit(
            item = item,
            prediction = eightBRecord,
            truth = truth,
            result = EvaluationRecord.fromComponents(eightBRecord, truth)
        )

        return PairedPrediction(
            item = item,
            truth = truth,
            baselineRecord = baseline,
            eightBRecord = eightBRecord,
            baselineUnit = baselineUnit,
            eightBUnit = eightBUnit,
            eightBDecision = eightBDecision
        )
    }
}
