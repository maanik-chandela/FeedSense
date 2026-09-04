package com.example.feedsense.analysis.evaluation.robustness

import com.example.feedsense.analysis.evaluation.comparative.PairedPrediction

/*
 * Milestone 8B-9.
 *
 * Paired contingency counts.
 *
 * Extracts the 2x2 paired contingency (a/b/c/d) from a set of
 * PAIRED observations (the same EvaluationItem judged by both
 * systems against the same ground truth). Respecting pairing is
 * essential: these are not two independent populations.
 *
 *   a = both correct
 *   b = baseline correct, 8B wrong   <- discordant (baseline only)
 *   c = baseline wrong, 8B correct   <- discordant (8B only)
 *   d = both wrong
 *
 * Eligibility: only pairs with a comparable (definitive) ground
 * truth are included in the correctness contingency, consistent
 * with 8B-8. UNKNOWN-truth pairs are excluded from the accuracy
 * contingency but reported separately.
 */
data class PairedCounts(
    val n: Int,
    val a: Int,
    val b: Int,
    val c: Int,
    val d: Int,
    val baselineCorrect: Int,
    val baselineWrong: Int,
    val eightBCorrect: Int,
    val eightBWrong: Int
) {
    val bothCorrect: Int get() = a
    val baselineOnlyCorrect: Int get() = b
    val eightBOnlyCorrect: Int get() = c
    val bothWrong: Int get() = d
    val discordantPairs: Int get() = b + c

    companion object {
        fun of(pairs: List<PairedPrediction>): PairedCounts {
            val eligible = pairs.filter { it.eligibleForAccuracy }
            var a = 0
            var b = 0
            var c = 0
            var d = 0
            for (p in eligible) {
                when {
                    p.baselineCorrect && p.eightBCorrect -> a++
                    p.baselineCorrect && !p.eightBCorrect -> b++
                    !p.baselineCorrect && p.eightBCorrect -> c++
                    else -> d++
                }
            }
            return PairedCounts(
                n = eligible.size,
                a = a,
                b = b,
                c = c,
                d = d,
                baselineCorrect = a + b,
                baselineWrong = c + d,
                eightBCorrect = a + c,
                eightBWrong = b + d
            )
        }

        fun of(
            a: Int,
            b: Int,
            c: Int,
            d: Int
        ): PairedCounts = PairedCounts(
            n = a + b + c + d,
            a = a,
            b = b,
            c = c,
            d = d,
            baselineCorrect = a + b,
            baselineWrong = c + d,
            eightBCorrect = a + c,
            eightBWrong = b + d
        )
    }
}
