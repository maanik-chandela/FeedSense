package com.example.feedsense.analysis.evaluation.experiment

import com.example.feedsense.analysis.evaluation.comparative.ComparisonOutcome

/**
 * Milestone 8B-9.
 *
 * The frozen five-way outcome of ONE eligible paired observation in
 * a controlled experiment. Orthogonal to and derived from the more
 * fine-grained 8B-8 [ComparisonOutcome] (which additionally splits
 * abstention variants):
 *
 *   BOTH_CORRECT               both systems named the right category
 *   BASELINE_ONLY_CORRECT      baseline right, evidence-aware wrong
 *                              (a strict evidence-aware regression)
 *   EVIDENCE_AWARE_ONLY_CORRECT evidence-aware right, baseline wrong
 *                              (a strict evidence-aware improvement)
 *   BOTH_WRONG                 both named a wrong category
 *   INCOMPLETE                 at least one side abstained / could not
 *                              be judged on an eligible item
 *
 * INCOMPLETE is deliberately separate from accuracy outcomes so a
 * system that abstains is never mistaken for one that is accurate.
 * Only BOTH_CORRECT / BASELINE_ONLY_CORRECT /
 * EVIDENCE_AWARE_ONLY_CORRECT / BOTH_WRONG contribute to the
 * accuracy contingency; INCOMPLETE is reported alongside.
 */
enum class EvidenceAwareOutcome(val label: String) {
    BOTH_CORRECT("BOTH_CORRECT"),
    BASELINE_ONLY_CORRECT("BASELINE_ONLY_CORRECT"),
    EVIDENCE_AWARE_ONLY_CORRECT("EVIDENCE_AWARE_ONLY_CORRECT"),
    BOTH_WRONG("BOTH_WRONG"),
    INCOMPLETE("INCOMPLETE");

    companion object {

        /**
         * Maps the fine-grained 8B-8 comparison outcome into the
         * experiment's five-way outcome. The abstention-oriented
         * variants of [ComparisonOutcome] all collapse into
         * [INCOMPLETE] because at least one side could not be judged.
         */
        fun of(comparison: ComparisonOutcome): EvidenceAwareOutcome {
            return when (comparison) {
                ComparisonOutcome.BOTH_CORRECT -> BOTH_CORRECT
                ComparisonOutcome.BASELINE_ONLY_CORRECT ->
                    BASELINE_ONLY_CORRECT
                ComparisonOutcome.EIGHT_B_ONLY_CORRECT,
                ComparisonOutcome.BASELINE_UNKNOWN_EIGHT_B_CORRECT ->
                    EVIDENCE_AWARE_ONLY_CORRECT
                ComparisonOutcome.BOTH_WRONG -> BOTH_WRONG
                ComparisonOutcome.BOTH_UNKNOWN,
                ComparisonOutcome.BASELINE_CORRECT_EIGHT_B_UNKNOWN,
                ComparisonOutcome.BASELINE_WRONG_EIGHT_B_UNKNOWN,
                ComparisonOutcome.EIGHT_B_WRONG_BASELINE_UNKNOWN ->
                    INCOMPLETE
            }
        }

        /**
         * True when the outcome counts as a strict evidence-aware
         * improvement in primary-category accuracy.
         */
        fun isEvidenceAwareImprovement(
            o: EvidenceAwareOutcome
        ): Boolean = o == EVIDENCE_AWARE_ONLY_CORRECT

        /**
         * True when the outcome counts as a strict evidence-aware
         * regression in primary-category accuracy.
         */
        fun isEvidenceAwareRegression(
            o: EvidenceAwareOutcome
        ): Boolean = o == BASELINE_ONLY_CORRECT

        /**
         * True when the outcome contributes to the accuracy
         * contingency (i.e. is not INCOMPLETE).
         */
        fun contributesToAccuracy(o: EvidenceAwareOutcome): Boolean =
            o != INCOMPLETE
    }
}
