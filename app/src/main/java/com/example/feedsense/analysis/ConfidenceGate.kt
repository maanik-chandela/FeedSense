package com.example.feedsense.analysis

// --------------------------------
// CONFIDENCE GATE
// --------------------------------
//
// Milestone 7A decision layer between the local
// analyzer and the cloud fallback.
//
// Rules:
//
// - No classification (confidence == null)
//   -> LOCAL_ACCEPTED. There is nothing to gate on.
//      OCR-only frames are not sent to the cloud.
//
// - Confidence above the local threshold and no
//   ambiguity -> LOCAL_ACCEPTED.
//
// - Top categories are close (ambiguityScore high)
//   -> AMBIGUOUS. Cloud reference recommended.
//
// - Confidence below the local threshold
//   -> NEEDS_CLOUD. Cloud reference required.
//

class ConfidenceGate(
    private val localConfidenceThreshold: Double =
        DEFAULT_LOCAL_CONFIDENCE,

    private val ambiguityThreshold: Double =
        DEFAULT_AMBIGUITY_THRESHOLD
) {

    fun evaluate(
        result: FrameAnalysisResult
    ): AnalysisDisposition {

        val confidence =
            result.confidence
                ?: return AnalysisDisposition.LOCAL_ACCEPTED

        val ambiguity =
            result.ambiguityScore
                ?: 0.0

        val isConfident =
            confidence >= localConfidenceThreshold

        val isAmbiguous =
            ambiguity >= ambiguityThreshold

        return when {

            isAmbiguous -> AnalysisDisposition.AMBIGUOUS

            isConfident -> AnalysisDisposition.LOCAL_ACCEPTED

            else -> AnalysisDisposition.NEEDS_CLOUD
        }
    }

    companion object {

        /*
         * Minimum local confidence required to accept
         * a classification without external help.
         */
        const val DEFAULT_LOCAL_CONFIDENCE =
            0.65

        /*
         * ambiguityScore above this means the top two
         * categories are too close to choose between.
         */
        const val DEFAULT_AMBIGUITY_THRESHOLD =
            0.7
    }
}
