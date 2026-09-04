package com.example.feedsense.analysis.evaluation

import com.example.feedsense.model.EvaluationItem

// --------------------------------
// ELIGIBILITY (Milestone 8A-3)
// --------------------------------
//
// Defines — explicitly — which items are eligible to enter a
// metric computation, and records every exclusion with a
// reason. This is the enforcement point for 8A-3's rule that
// UNREVIEWED items are never counted, DISPUTED only via
// config, and that nothing is excluded silently.

object Eligibility {

    sealed class Reason {
        // The item has no human ground truth at all
        // (e.g. still UNREVIEWED / in-progress).
        object UNREVIEWED : Reason()

        // Marked DISPUTED and the run config excludes disputed.
        object DISPUTED_EXCLUDED : Reason()

        // No AI prediction snapshot is available.
        object MISSING_PREDICTION : Reason()

        // No ground truth is available.
        object MISSING_TRUTH : Reason()

        // No evaluation result row is available.
        object MISSING_RESULT : Reason()

        data class Other(val detail: String) : Reason()
    }

    data class Outcome(
        val eligible: List<EvaluationItem>,
        val excluded: List<ExcludedItem>
    )

    data class ExcludedItem(
        val item: EvaluationItem,
        val reason: Reason
    )

    /**
     * Applies eligibility + exclusion to a collection of items.
     *
     * An item is eligible ONLY when all of:
     *   1. its status is in the configured eligible set
     *   2. it is not DISPUTED unless the config includes it
     *   3. it has a prediction, a truth, and a result (checked
     *      by the caller's lookup and signaled via the optional
     *      predicates)
     *
     * @param hasPrediction whether the item has an AI snapshot
     * @param hasTruth      whether the item has ground truth
     * @param hasResult     whether the item has an evaluation result
     */
    fun select(
        items: List<EvaluationItem>,
        config: EvaluatorConfig,
        hasPrediction: (String) -> Boolean,
        hasTruth: (String) -> Boolean,
        hasResult: (String) -> Boolean
    ): Outcome {

        val eligible = mutableListOf<EvaluationItem>()
        val excluded = mutableListOf<ExcludedItem>()

        items.forEach { item ->
            val id = item.id

            when {
                item.evaluationStatus ==
                    EvaluationItem.STATUS_NOT_EVALUATED -> {
                    excluded.add(
                        ExcludedItem(item, Reason.UNREVIEWED)
                    )
                }

                item.evaluationStatus ==
                    EvaluationItem.STATUS_DISPUTED &&
                    !config.includeDisputed -> {
                    excluded.add(
                        ExcludedItem(item, Reason.DISPUTED_EXCLUDED)
                    )
                }

                !hasPrediction(id) -> {
                    excluded.add(
                        ExcludedItem(item, Reason.MISSING_PREDICTION)
                    )
                }

                !hasTruth(id) -> {
                    excluded.add(
                        ExcludedItem(item, Reason.MISSING_TRUTH)
                    )
                }

                !hasResult(id) -> {
                    excluded.add(
                        ExcludedItem(item, Reason.MISSING_RESULT)
                    )
                }

                else -> {
                    eligible.add(item)
                }
            }
        }

        return Outcome(
            eligible = eligible,
            excluded = excluded
        )
    }
}