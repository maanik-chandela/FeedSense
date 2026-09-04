package com.example.feedsense.analysis.evaluation.robustness

import com.example.feedsense.analysis.evaluation.comparative.ComparisonOutcome
import com.example.feedsense.analysis.evaluation.comparative.PairedPrediction

/*
 * Milestone 8B-9.
 *
 * Abstention / UNKNOWN sensitivity analysis.
 *
 * Evaluates how conclusions change under three clearly labelled
 * policies for handling UNKNOWN predictions:
 *
 *   Policy A: UNKNOWN counts as wrong
 *     - Both systems' unknowns are counted as incorrect.
 *     - Measures raw accuracy without rewarding abstention.
 *
 *   Policy B: UNKNOWN excluded from selective accuracy
 *     - Only pairs where the system produced a definitive answer
 *       contribute to accuracy. Rewards honest abstention.
 *
 *   Policy C: Coverage + Selective Accuracy
 *     - Reports both coverage (how often the system decides) and
 *       selective accuracy (accuracy when it decides).
 *     - Reveals the accuracy/coverage trade-off.
 *
 * No policy is claimed as universally correct; the report shows
 * how the conclusion changes under each.
 */
data class AbstentionSensitivity(
    val policyA: PolicyResult,
    val policyB: PolicyResult,
    val policyC: CoverageResult
) {
    data class PolicyResult(
        val label: String,
        val baselineAccuracy: Double?,
        val eightBAccuracy: Double?,
        val accuracyDifference: Double?,
        val n: Int,
        val sufficient: Boolean
    )

    data class CoverageResult(
        val baselineCoverage: Double?,
        val eightBCoverage: Double?,
        val baselineSelectiveAccuracy: Double?,
        val eightBSelectiveAccuracy: Double?,
        val baselineAccuracy: Double?,
        val eightBAccuracy: Double?,
        val n: Int,
        val sufficient: Boolean
    )

    companion object {
        private const val POLICY_A = "UNKNOWN_AS_WRONG"
        private const val POLICY_B = "UNKNOWN_EXCLUDED"
        private const val POLICY_C = "COVERAGE_PLUS_SELECTIVE"

        fun compute(
            pairs: List<PairedPrediction>,
            minimumForEffect: Int
        ): AbstentionSensitivity {
            val eligible = pairs.filter { it.eligibleForAccuracy }
            val n = eligible.size
            val sufficient = n >= minimumForEffect

            if (!sufficient) {
                return AbstentionSensitivity(
                    policyA = PolicyResult(
                        POLICY_A, null, null, null, n, false
                    ),
                    policyB = PolicyResult(
                        POLICY_B, null, null, null, n, false
                    ),
                    policyC = CoverageResult(
                        null, null, null, null, null, null, n, false
                    )
                )
            }

            // Policy A: UNKNOWN counts as wrong
            var bCorrectA = 0
            var eCorrectA = 0
            for (p in eligible) {
                if (p.baselineCorrect) bCorrectA++
                if (p.eightBCorrect) eCorrectA++
            }
            val bAccA = bCorrectA.toDouble() / n
            val eAccA = eCorrectA.toDouble() / n

            // Policy B: UNKNOWN excluded -> only decided pairs
            var bDecidedCorrect = 0
            var bDecided = 0
            var eDecidedCorrect = 0
            var eDecided = 0
            for (p in eligible) {
                if (!p.baselineUnknown) {
                    bDecided++
                    if (p.baselineCorrect) bDecidedCorrect++
                }
                if (!p.eightBUnknown) {
                    eDecided++
                    if (p.eightBCorrect) eDecidedCorrect++
                }
            }
            val bAccB = if (bDecided > 0) {
                bDecidedCorrect.toDouble() / bDecided
            } else null
            val eAccB = if (eDecided > 0) {
                eDecidedCorrect.toDouble() / eDecided
            } else null

            // Policy C: Coverage + Selective
            val bCov = bDecided.toDouble() / n
            val eCov = eDecided.toDouble() / n
            val bSelAcc = if (bDecided > 0) {
                bDecidedCorrect.toDouble() / bDecided
            } else null
            val eSelAcc = if (eDecided > 0) {
                eDecidedCorrect.toDouble() / eDecided
            } else null

            return AbstentionSensitivity(
                policyA = PolicyResult(
                    POLICY_A, bAccA, eAccA, eAccA - bAccA, n, true
                ),
                policyB = PolicyResult(
                    POLICY_B, bAccB, eAccB,
                    if (bAccB != null && eAccB != null) eAccB - bAccB else null,
                    n, true
                ),
                policyC = CoverageResult(
                    baselineCoverage = bCov,
                    eightBCoverage = eCov,
                    baselineSelectiveAccuracy = bSelAcc,
                    eightBSelectiveAccuracy = eSelAcc,
                    baselineAccuracy = bAccA,
                    eightBAccuracy = eAccA,
                    n = n,
                    sufficient = true
                )
            )
        }
    }
}
