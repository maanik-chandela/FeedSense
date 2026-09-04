package com.example.feedsense.analysis.evaluation

import com.example.feedsense.model.EvaluationRecord

// --------------------------------
// CONFIDENCE VS CORRECTNESS (Milestone 8A-5)
// --------------------------------
//
// Buckets predictions by the model's reported confidence and
// reports, within each bucket, how often the prediction was
// correct. This is the diagnostic view that surfaces
// overconfidence (errors at high confidence) and
// underconfidence (correctness at low confidence) without
// retraining or re-thresholding the production classifier.

object ConfidenceAnalysis {

    const val DEFAULT_BUCKET_WIDTH = 0.1

    data class Bucket(
        val lower: Double,
        val upper: Double,
        val sampleCount: Int,
        val correctCount: Int,
        val incorrectCount: Int
    ) {
        val accuracy: Double?
            get() = if (sampleCount == 0) {
                null
            } else {
                correctCount.toDouble() / sampleCount
            }
    }

    data class Result(
        val buckets: List<Bucket>,
        val confidentErrorCount: Int,
        val confidentCorrectCount: Int,
        val lowConfidenceCorrectCount: Int,
        val bucketWidth: Double
    ) {
        val confidentSampleCount: Int
            get() = confidentErrorCount + confidentCorrectCount

        /*
         * The overconfidence diagnostic: among predictions with
         * confidence >= the overconfidence threshold, what
         * fraction were wrong? Null when there is no confident
         * sample, to avoid a fabricated 0% story on no data.
         */
        val confidentErrorRate: Double?
            get() = if (confidentSampleCount == 0) {
                null
            } else {
                confidentErrorCount.toDouble() / confidentSampleCount
            }
    }

    /**
     * Buckets definitive primary-category verdicts (CORRECT /
     * INCORRECT) by prediction confidence. UNKNOWN / PARTIAL /
     * UNCOMPARABLE verdicts are excluded because they do not
     * represent a clear right-or-wrong to correlate with
     * confidence.
     */
    fun analyze(
        units: List<EvaluationUnit>,
        bucketWidth: Double = DEFAULT_BUCKET_WIDTH,
        overconfidenceThreshold: Double =
            ErrorAnalyzer.OVERCONFIDENCE_THRESHOLD,
        underConfidenceThreshold: Double =
            ErrorAnalyzer.UNDERCONFIDENCE_THRESHOLD
    ): Result {

        val buckets = mutableListOf<Bucket>()
        var confidentError = 0
        var confidentCorrect = 0
        var lowConfCorrect = 0

        val candidateUnits = units.filter { unit ->
            val v = unit.result.verdict
            (v == EvaluationRecord.VERDICT_CORRECT ||
                v == EvaluationRecord.VERDICT_INCORRECT) &&
                unit.prediction.confidence != null
        }

        // Build empty buckets over [0,1).
        val bucketCount = (1.0 / bucketWidth).toInt().coerceAtLeast(1)
        for (i in 0 until bucketCount) {
            val lower = i * bucketWidth
            val upper = ((i + 1) * bucketWidth).coerceAtMost(1.0)
            buckets += Bucket(
                lower = lower,
                upper = upper,
                sampleCount = 0,
                correctCount = 0,
                incorrectCount = 0
            )
        }
        // Edge: exactly 1.0 confidence.
        buckets[bucketCount - 1] = buckets[bucketCount - 1].copy(
            upper = 1.0
        )

        candidateUnits.forEach { unit ->
            val conf = unit.prediction.confidence!!
            val idx =
                (conf / bucketWidth).toInt().coerceAtMost(bucketCount - 1)
            val correct =
                unit.result.verdict == EvaluationRecord.VERDICT_CORRECT
            val prev = buckets[idx]
            buckets[idx] = prev.copy(
                sampleCount = prev.sampleCount + 1,
                correctCount = prev.correctCount + if (correct) 1 else 0,
                incorrectCount = prev.incorrectCount + if (correct) 0 else 1
            )

            if (conf >= overconfidenceThreshold) {
                if (correct) confidentCorrect++
                else confidentError++
            }
            if (conf <= underConfidenceThreshold && correct) {
                lowConfCorrect++
            }
        }

        return Result(
            buckets = buckets,
            confidentErrorCount = confidentError,
            confidentCorrectCount = confidentCorrect,
            lowConfidenceCorrectCount = lowConfCorrect,
            bucketWidth = bucketWidth
        )
    }
}
