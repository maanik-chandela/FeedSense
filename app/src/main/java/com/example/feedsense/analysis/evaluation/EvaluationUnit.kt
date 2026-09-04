package com.example.feedsense.analysis.evaluation

import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.EvaluationItem
import com.example.feedsense.model.EvaluationRecord
import com.example.feedsense.model.GroundTruth

// --------------------------------
// EVALUATION UNIT (Milestone 8A-3)
// --------------------------------
//
// One structurally-complete evaluation instance: the frozen AI
// prediction, the human ground truth, and the computed verdict
// that the 8A layer already derived by comparing them. The
// engine consumes a list of these and produces metrics.
//
// FeedItem lookup is deferred to the caller for error
// drill-down (8A-3 requires error records to be retrievable
// with the FeedItem, prediction, truth, and model/dataset
// versions); it is never mutated here.

data class EvaluationUnit(
    val item: EvaluationItem,
    val prediction: AiPredictionRecord,
    val truth: GroundTruth,
    val result: EvaluationRecord
) {

    // Convenience: does this unit's primary-category verdict
    // represent a strict, definite right-or-wrong (as opposed
    // to PARTIAL / UNKNOWN / UNCOMPARABLE)?
    val hasDefinitiveVerdict: Boolean
        get() =
            result.verdict == EvaluationRecord.VERDICT_CORRECT ||
                result.verdict == EvaluationRecord.VERDICT_INCORRECT
}