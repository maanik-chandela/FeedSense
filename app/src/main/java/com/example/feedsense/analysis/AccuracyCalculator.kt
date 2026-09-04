package com.example.feedsense.analysis

import com.example.feedsense.model.ModelFeedback

// --------------------------------
// ACCURACY CALCULATOR
// --------------------------------
//
// Milestone 7K (Part 6).
//
// Pure functions for the "Observed validation accuracy"
// statistics. The numbers are computed from confirmed
// corrections only - they measure how often a human
// agreed with the AI, NOT a scientifically validated
// accuracy claim.
//
// Everything here is deterministic and unit-testable.
//

object AccuracyCalculator {

    /*
     * fraction(agreement=true) among feedback rows that
     * had comparable data. Nullable agreement rows
     * (no topic/tone present) are excluded from that
     * dimension.
     */
    fun topicAccuracy(
        feedback: List<ModelFeedback>
    ): Double {

        val comparable =
            feedback.filter {
                it.topicAgreement != null
            }

        if (comparable.isEmpty()) {
            return 0.0
        }

        val agreed =
            comparable.count {
                it.topicAgreement == true
            }

        return agreed.toDouble() * 100 / comparable.size
    }

    fun toneAccuracy(
        feedback: List<ModelFeedback>
    ): Double {

        val comparable =
            feedback.filter {
                it.toneAgreement != null
            }

        if (comparable.isEmpty()) {
            return 0.0
        }

        val agreed =
            comparable.count {
                it.toneAgreement == true
            }

        return agreed.toDouble() * 100 / comparable.size
    }

    /*
     * Share of all classifications that ended up in the
     * review queue (uncertain items + frames still
     * waiting for review).
     */
    fun uncertaintyRate(
        uncertain: Int,
        total: Int
    ): Double {

        if (total <= 0) {
            return 0.0
        }

        return uncertain.toDouble() * 100 / total
    }
}
