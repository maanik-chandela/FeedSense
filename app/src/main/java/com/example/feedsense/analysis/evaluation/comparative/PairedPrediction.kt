package com.example.feedsense.analysis.evaluation.comparative

import com.example.feedsense.analysis.evaluation.EvaluationUnit
import com.example.feedsense.model.EvaluationItem
import com.example.feedsense.model.GroundTruth

/*
 * Milestone 8B-8.
 *
 * Paired comparison unit.
 *
 * Binds, for ONE eligible EvaluationItem, the SAME ground truth
 * to BOTH the baseline prediction and the 8B evidence-aware
 * prediction. This is the unit the comparative evaluator sums
 * over.
 *
 * Imutability guarantees:
 *   - baselineRecord / eightBRecord are immutable
 *     AiPredictionRecord snapshots (the baseline is never
 *     modified; the 8B record is produced by the read-only
 *     DecisionEvaluationBridge).
 *   - truth is the single authoritative GroundTruth used to
 *     judge both sides, so the comparison is apples-to-apples.
 *
 * The pair resolves to a ComparisonOutcome once both sides have
 * been judged against the same truth. Only pairs whose truth is
 * non-unknown (a definitive category exists) are eligible for
 * accuracy/outcome comparisons. Pairs whose truth is UNKNOWN are
 * still carried so abstention behaviour on unjudgeable items is
 * observable, but they are excluded from accuracy denominators.
 */
data class PairedPrediction(

    val item: EvaluationItem,

    val truth: GroundTruth,

    val baselineRecord: com.example.feedsense.model.AiPredictionRecord,

    val eightBRecord: com.example.feedsense.model.AiPredictionRecord,

    val baselineUnit: EvaluationUnit,

    val eightBUnit: EvaluationUnit,

    /*
     * The underlying 8B decision (when available) so the
     * evidence stratification (coverage, temporal continuity,
     * representative-frame outlier) is reproducible. May be
     * null when only the AiPredictionRecord was materialized.
     */
    val eightBDecision:
        com.example.feedsense.analysis.evidence.decision.ItemPredictionResult? = null
) {

    /**
     * Whether the shared ground truth is comparable (ambiguity is
     * not UNKNOWN, so a definitive category exists).
     */
    val truthComparable: Boolean
        get() = truth.ambiguity !=
            GroundTruth.AMBIGUITY_UNKNOWN

    /**
     * Strict primary-category correctness of the baseline against
     * THIS truth (derived deterministically, recomputable).
     */
    val baselineCorrect: Boolean
        get() = baselineUnit.result.verdict ==
            com.example.feedsense.model.EvaluationRecord.VERDICT_CORRECT

    /**
     * Whether the baseline abstained (no usable primary category):
     * a COOLING variant: the baseline never abstains in the frozen
     * 8A pipeline, so find the "named wrong vs cannot judge"
     * distinction via the prediction having no normalized category.
     */
    val baselineUnknown: Boolean
        get() = baselineUnit.prediction.category == null

    /**
     * Strict primary-category correctness of 8B against THIS
     * truth.
     */
    val eightBCorrect: Boolean
        get() = eightBUnit.result.verdict ==
            com.example.feedsense.model.EvaluationRecord.VERDICT_CORRECT

    /**
     * Whether 8B abstained (no usable primary category).
     */
    val eightBUnknown: Boolean
        get() = eightBUnit.prediction.category == null

    /**
     * The resolved comparison outcome for this pair.
     */
    val outcome: ComparisonOutcome
        get() = ComparisonOutcome.of(
            baselineCorrect = baselineCorrect,
            baselineUnknown = baselineUnknown,
            eightBCorrect = eightBCorrect,
            eightBUnknown = eightBUnknown
        )

    /**
     * True when this pair is eligible for accuracy/outcome
     * comparison (truth comparable).
     */
    val eligibleForAccuracy: Boolean
        get() = truthComparable
}
