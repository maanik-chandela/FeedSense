package com.example.feedsense.analysis.evaluation.experiment

import com.example.feedsense.analysis.evaluation.comparative.PairedPrediction

/**
 * Milestone 8B-9.
 *
 * Ground-truth eligibility of a paired observation for ACCURACY
 * comparison.
 *
 * An observation is only eligible for accuracy comparison when its
 * shared ground truth defines a definitive category (it is not
 * AMBIGUITY_UNKNOWN). Excluded observations are still carried and
 * reported (they inform dataset quality and abstention behaviour)
 * but never enter any accuracy denominator.
 *
 * This mirrors the 8B-8 comparable-truth rule so the experiment and
 * the comparative layer use an identical eligibility definition.
 */
enum class GroundTruthEligibility(val label: String) {
    ELIGIBLE_FOR_ACCURACY("ELIGIBLE_FOR_ACCURACY"),
    EXCLUDED_FROM_ACCURACY("EXCLUDED_FROM_ACCURACY");

    companion object {

        fun of(pair: PairedPrediction): GroundTruthEligibility =
            if (pair.eligibleForAccuracy) {
                ELIGIBLE_FOR_ACCURACY
            } else {
                EXCLUDED_FROM_ACCURACY
            }
    }
}
