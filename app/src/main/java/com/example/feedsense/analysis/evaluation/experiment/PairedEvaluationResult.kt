package com.example.feedsense.analysis.evaluation.experiment

import com.example.feedsense.analysis.evaluation.comparative.PairedPrediction

/**
 * Milestone 8B-9.
 *
 * A single paired observation as seen through the experiment lens.
 *
 * Wraps the immutable 8B-8 [PairedPrediction] (the same ground
 * truth judged against both the baseline and evidence-aware
 * predictions) and materializes the experiment's own eligibility
 * and five-way outcome. It is a derived, immutable view: nothing
 * here mutates the underlying prediction, truth, or item.
 */
data class PairedEvaluationResult(
    val pair: PairedPrediction,
    val eligibility: GroundTruthEligibility,
    val outcome: EvidenceAwareOutcome
) {
    companion object {

        /**
         * Derives the experiment view of a paired observation.
         * Deterministic and pure.
         */
        fun of(pair: PairedPrediction): PairedEvaluationResult {
            return PairedEvaluationResult(
                pair = pair,
                eligibility = GroundTruthEligibility.of(pair),
                outcome = EvidenceAwareOutcome.of(pair.outcome)
            )
        }
    }

    /** True when this observation's truth can be judged for accuracy. */
    val eligibleForAccuracy: Boolean
        get() = eligibility ==
            GroundTruthEligibility.ELIGIBLE_FOR_ACCURACY
}
