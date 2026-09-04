package com.example.feedsense.analysis.evaluation.comparative

import com.example.feedsense.analysis.evaluation.ErrorAnalyzer
import com.example.feedsense.analysis.evaluation.RootCauseAnalyzer

/*
 * Milestone 8B-8.
 *
 * Error-type and root-cause comparison.
 *
 * Reuses the 8A error analyzer and (deterministic) root-cause
 * analyzer to compare WHERE each system fails, not just whether.
 * Both systems are diagnosed with the SAME analyzers on their own
 * EvaluationUnit, so the error-type vocabulary and root-cause
 * attribution vocabulary are identical across sides.
 *
 * Outputs:
 *   - errorTypeTally : per (errorType) counts, baseline vs 8B
 *   - attributionTally: per (attributionClass) counts over the
 *     pairs the root-cause analyzer could assess
 *   - calibrationFlags: overconfident-wrong / underconfident-correct
 *     per side
 *
 * These are descriptive comparisons for diagnosis, not hypothesis
 * tests; magnitudes are always reported alongside the counts.
 */
object ErrorRootCauseComparison {

    data class Comparison(
        val analyzedPairs: Int,
        val errorTypesBySystem:
            Map<String, Map<String, Int>>,      // system -> errorType -> count
        val attributionBySystem:
            Map<String, Map<String, Int>>,      // system -> attributionClass -> count
        val overconfidentWrongBySystem: Map<String, Int>,
        val underconfidentCorrectBySystem: Map<String, Int>
    )

    private const val BASELINE = "BASELINE"
    private const val EIGHT_B = "EIGHT_B"

    /**
     * Runs both analyzers over the given pairs for each system and
     * tallies the results. Deterministic ordering: keys sorted.
     *
     * @param maxRootCauseAssessments cap on how many root-cause
     *        assessments are run per side (worst pairs first), to
     *        bound cost while remaining deterministic. Set to null
     *        to assess all pairs.
     */
    fun compare(
        pairs: List<PairedPrediction>,
        maxRootCauseAssessments: Int? = null
    ): Comparison {
        val eligible = pairs.filter { it.eligibleForAccuracy }
        val eligibleSorted = eligible.sortedBy {
            it.item.id
        }

        val baselineErrorTypes = linkedMapOf<String, Int>()
        val eightBErrorTypes = linkedMapOf<String, Int>()

        val baselineAttribution = linkedMapOf<String, Int>()
        val eightBAttribution = linkedMapOf<String, Int>()

        var baselineOW = 0
        var baselineUC = 0
        var eightBOW = 0
        var eightBUC = 0

        // Error-type and calibration tallies for ALL eligible pairs.
        for (p in eligibleSorted) {
            val bErrors = ErrorAnalyzer.detect(p.baselineUnit)
            val eErrors = ErrorAnalyzer.detect(p.eightBUnit)

            tallyErrorTypes(bErrors, baselineErrorTypes)
            tallyErrorTypes(eErrors, eightBErrorTypes)

            val bOver = bErrors.any { it.overconfident }
            val bUnder = bErrors.any {
                it.capability ==
                    com.example.feedsense.analysis.evaluation.ErrorTypes.CAP_CALIBRATION
            }
            baselineOW += if (bOver) 1 else 0
            // underconfident counted via undercorrection detection flag
            baselineUC += tallyUnderconfident(bErrors)

            eightBOW += if (eErrors.any { it.overconfident }) 1 else 0
            eightBUC += tallyUnderconfident(eErrors)
        }

        // Root-cause attribution on a deterministic, bounded subset:
        // wrong pairs first (they are the diagnosable failures).
        val bWrong = eligibleSorted
            .filter { !it.baselineCorrect && !it.baselineUnknown }
            .sortedBy { it.item.id }
        val eWrong = eligibleSorted
            .filter { !it.eightBCorrect && !it.eightBUnknown }
            .sortedBy { it.item.id }

        val bTarget = bWrong.take(
            maxRootCauseAssessments ?: bWrong.size
        )
        val eTarget = eWrong.take(
            maxRootCauseAssessments ?: eWrong.size
        )

        for (p in bTarget) {
            val assess = RootCauseAnalyzer.analyze(
                unit = p.baselineUnit,
                analysisId = "cmp-b-${p.baselineUnit.item.id}",
                now = java.time.LocalDateTime.of(2000, 1, 1, 0, 0)
            )
            addCount(
                baselineAttribution, assess.attributionClass
            )
        }
        for (p in eTarget) {
            val assess = RootCauseAnalyzer.analyze(
                unit = p.eightBUnit,
                analysisId = "cmp-e-${p.eightBUnit.item.id}",
                now = java.time.LocalDateTime.of(2000, 1, 1, 0, 0)
            )
            addCount(eightBAttribution, assess.attributionClass)
        }

        return Comparison(
            analyzedPairs = eligible.size,
            errorTypesBySystem = mapOf(
                BASELINE to sorted(baselineErrorTypes),
                EIGHT_B to sorted(eightBErrorTypes)
            ),
            attributionBySystem = mapOf(
                BASELINE to sorted(baselineAttribution),
                EIGHT_B to sorted(eightBAttribution)
            ),
            overconfidentWrongBySystem = mapOf(
                BASELINE to baselineOW,
                EIGHT_B to eightBOW
            ),
            underconfidentCorrectBySystem = mapOf(
                BASELINE to baselineUC,
                EIGHT_B to eightBUC
            )
        )
    }

    private fun tallyErrorTypes(
        errors: List<ErrorAnalyzer.DetectedError>,
        into: MutableMap<String, Int>
    ) {
        for (e in errors) {
            addCount(into, e.errorType)
        }
    }

    private fun tallyUnderconfident(
        errors: List<ErrorAnalyzer.DetectedError>
    ): Int {
        // Underconfident-correct is a calibration-flavoured flag the
        // analyzer emits on correct-but-unsure units. We count a unit
        // once if it carries an underconfidence signal (already
        // derived by the analyzer's calibration capability).
        return if (errors.any { it.capability ==
                com.example.feedsense.analysis.evaluation.ErrorTypes.CAP_CALIBRATION &&
            it.errorType.contains("UNDER", ignoreCase = true)
        }) 1 else 0
    }

    private fun addCount(map: MutableMap<String, Int>, key: String) {
        map[key] = (map[key] ?: 0) + 1
    }

    private fun sorted(map: MutableMap<String, Int>): Map<String, Int> =
        map.toSortedMap()

    fun errorTypes(baseline: List<ErrorAnalyzer.DetectedError>): Map<String, Int> {
        val m = linkedMapOf<String, Int>()
        tallyErrorTypes(baseline, m)
        return m
    }
}
