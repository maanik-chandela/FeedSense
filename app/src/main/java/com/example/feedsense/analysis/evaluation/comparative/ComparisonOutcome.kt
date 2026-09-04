package com.example.feedsense.analysis.evaluation.comparative

/*
 * Milestone 8B-8.
 *
 * Controlled comparison outcome.
 *
 * Each eligible comparison unit (one EvaluationItem with both a
 * baseline and an 8B prediction, judged against the SAME ground
 * truth) resolves to exactly one outcome. Outcomes keep
 * correctness and abstention separate so a system that merely
 * abstains a lot is never mistaken for one that is more accurate.
 *
 * "Correct" here is a strict primary-category match against the
 * definitive ground truth (verdict CORRECT). "Unknown/abstained"
 * means the model produced no usable primary category, or the
 * truth was not comparable (handled separately).
 *
 * Outcome semantics (explicit, non-collapsing):
 *   BOTH_CORRECT                   : both named the right category
 *   BOTH_WRONG                     : both named a wrong category
 *   BASELINE_ONLY_CORRECT          : baseline right, 8B wrong
 *                                     -> 8B REGRESSION
 *   EIGHT_B_ONLY_CORRECT           : 8B right, baseline wrong
 *                                     -> 8B IMPROVEMENT
 *   BOTH_UNKNOWN                   : both abstained (no category)
 *   BASELINE_UNKNOWN_EIGHT_B_CORRECT : baseline abstained,
 *                                     8B correct -> IMPROVEMENT
 *   BASELINE_CORRECT_EIGHT_B_UNKNOWN : baseline correct, 8B
 *                                      abstained -> abstention,
 *                                     policy-defined regression
 *   BASELINE_WRONG_EIGHT_B_UNKNOWN   : baseline wrong, 8B
 *                                      abstained (no claim)
 *   EIGHT_B_WRONG_BASELINE_UNKNOWN   : 8B wrong, baseline
 *                                      abstained (no claim)
 *
 * IMPORTANT: Only EIGHT_B_ONLY_CORRECT and
 * BASELINE_UNKNOWN_EIGHT_B_CORRECT are counted as "8B
 * improvements". Only BASELINE_ONLY_CORRECT is a strict "8B
 * regression" for primary-category accuracy. The *_UNKNOWN
 * outcomes are recorded but NOT counted as accuracy for either
 * side unless selective (coverage-limited) metrics are used.
 */
enum class ComparisonOutcome(val label: String) {
    BOTH_CORRECT("BOTH_CORRECT"),
    BOTH_WRONG("BOTH_WRONG"),
    BASELINE_ONLY_CORRECT("BASELINE_ONLY_CORRECT"),
    EIGHT_B_ONLY_CORRECT("EIGHT_B_ONLY_CORRECT"),
    BOTH_UNKNOWN("BOTH_UNKNOWN"),
    BASELINE_UNKNOWN_EIGHT_B_CORRECT(
        "BASELINE_UNKNOWN_EIGHT_B_CORRECT"
    ),
    BASELINE_CORRECT_EIGHT_B_UNKNOWN(
        "BASELINE_CORRECT_EIGHT_B_UNKNOWN"
    ),
    BASELINE_WRONG_EIGHT_B_UNKNOWN(
        "BASELINE_WRONG_EIGHT_B_UNKNOWN"
    ),
    EIGHT_B_WRONG_BASELINE_UNKNOWN(
        "EIGHT_B_WRONG_BASELINE_UNKNOWN"
    );

    companion object {

        /**
         * Classifies an outcome from the strict correctness /
         * abstention of each system.
         *
         * @param baselineCorrect   true when the baseline named the
         *                          correct definitive category
         * @param baselineUnknown   true when the baseline abstained
         *                          (no usable primary category / not
         *                          judged)
         * @param eightBCorrect     true when 8B named the correct
         *                          definitive category
         * @param eightBUnknown     true when 8B abstained or could not
         *                          be judged
         */
        fun of(
            baselineCorrect: Boolean,
            baselineUnknown: Boolean,
            eightBCorrect: Boolean,
            eightBUnknown: Boolean
        ): ComparisonOutcome {
            require(!(baselineCorrect && baselineUnknown)) {
                "baseline cannot be both correct and unknown"
            }
            require(!(eightBCorrect && eightBUnknown)) {
                "eightB cannot be both correct and unknown"
            }

            return when {
                // All four abstention / correctness combos.
                baselineUnknown && eightBUnknown ->
                    BOTH_UNKNOWN
                baselineUnknown && eightBCorrect ->
                    BASELINE_UNKNOWN_EIGHT_B_CORRECT
                baselineUnknown && !eightBCorrect ->
                    EIGHT_B_WRONG_BASELINE_UNKNOWN

                eightBUnknown && baselineCorrect ->
                    BASELINE_CORRECT_EIGHT_B_UNKNOWN
                eightBUnknown && !baselineCorrect ->
                    BASELINE_WRONG_EIGHT_B_UNKNOWN

                baselineCorrect && eightBCorrect ->
                    BOTH_CORRECT
                baselineCorrect && !eightBCorrect ->
                    BASELINE_ONLY_CORRECT
                !baselineCorrect && eightBCorrect ->
                    EIGHT_B_ONLY_CORRECT
                else ->
                    BOTH_WRONG
            }
        }

        /**
         * True when this outcome is a strict 8B improvement for
         * primary-category accuracy (8B correct where baseline
         * was not).
         */
        fun isEightBImprovement(o: ComparisonOutcome): Boolean {
            return o == EIGHT_B_ONLY_CORRECT ||
                o == BASELINE_UNKNOWN_EIGHT_B_CORRECT
        }

        /**
         * True when this outcome is a strict 8B regression for
         * primary-category accuracy (baseline correct where 8B
         * was not).
         */
        fun isEightBRegression(o: ComparisonOutcome): Boolean {
            return o == BASELINE_ONLY_CORRECT
        }
    }
}
