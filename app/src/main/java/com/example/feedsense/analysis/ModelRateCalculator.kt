package com.example.feedsense.analysis

// --------------------------------
// MODEL RATE CALCULATOR
// --------------------------------
//
// Milestone 7R.
//
// Pure percentage computations for the evaluation
// screen. Kept outside the repository so every formula
// is unit-testable without Room.
//
// All rates are honest-to-zero: an empty dataset
// yields 0.0%, never a divide-by-zero or a misleading
// "100%".
//

object ModelRateCalculator {

    /*
     * Cloud-sourced analyses as a share of all
     * classifications.
     */
    fun cloudFallbackRate(
        cloudFallback: Int,
        totalClassifications: Int
    ): Double {

        return percent(
            part = cloudFallback,
            total = totalClassifications
        )
    }

    /*
     * HIGH-confidence local acceptances as a share of
     * all classifications.
     */
    fun localAcceptanceRate(
        highConfidence: Int,
        totalClassifications: Int
    ): Double {

        return percent(
            part = highConfidence,
            total = totalClassifications
        )
    }

    /*
     * Items flagged for review as a share of all
     * classifications.
     */
    fun reviewRate(
        needsReview: Int,
        totalClassifications: Int
    ): Double {

        return percent(
            part = needsReview,
            total = totalClassifications
        )
    }

    /*
     * User corrections as a share of all user feedback
     * (corrections + confirmations).
     */
    fun correctionRate(
        corrections: Int,
        confirmations: Int
    ): Double {

        return percent(
            part = corrections,
            total = corrections + confirmations
        )
    }

    private fun percent(
        part: Int,
        total: Int
    ): Double {

        if (total <= 0 || part <= 0) {
            return 0.0
        }

        return part.toDouble() * 100 / total
    }
}
