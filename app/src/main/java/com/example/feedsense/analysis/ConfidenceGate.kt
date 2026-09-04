package com.example.feedsense.analysis

// --------------------------------
// CONFIDENCE GATE
// --------------------------------
//
// Milestone 7A decision layer between the local
// analyzer and the cloud fallback.
//
// Milestone 7D adds the medium-confidence band.
//
// Rules:
//
// - No classification (confidence == null)
//   -> LOCAL_ACCEPTED. There is nothing to gate on.
//      OCR-only frames are not sent to the cloud.
//
// - Confidence at or above the accept threshold and
//   no ambiguity -> LOCAL_ACCEPTED.
//
// - Top categories are close (ambiguityScore high)
//   -> AMBIGUOUS. Content is kept but marked
//      uncertain with candidate categories.
//
// - Confidence in the medium band (0.6 .. 0.8)
//   -> MEDIUM_CONFIDENCE. Content is kept but flagged
//      for review; no cloud call is made.
//
// - Confidence below the medium threshold
//   -> NEEDS_CLOUD. Cloud reference required.
//

class ConfidenceGate(
    private val acceptConfidence: Double =
        DEFAULT_ACCEPT_CONFIDENCE,

    private val mediumConfidenceThreshold: Double =
        DEFAULT_MEDIUM_CONFIDENCE,

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

        val isAccepted =
            confidence >= acceptConfidence

        val isMedium =
            confidence >= mediumConfidenceThreshold

        val isAmbiguous =
            ambiguity >= ambiguityThreshold

        return when {

            isAmbiguous -> AnalysisDisposition.AMBIGUOUS

            isAccepted -> AnalysisDisposition.LOCAL_ACCEPTED

            isMedium -> AnalysisDisposition.MEDIUM_CONFIDENCE

            else -> AnalysisDisposition.NEEDS_CLOUD
        }
    }

    companion object {

        /*
         * Minimum local confidence required to accept
         * a classification without any review.
         */
        const val DEFAULT_ACCEPT_CONFIDENCE =
            0.8

        /*
         * Confidence at or above this but below the
         * accept threshold is "medium": the content is
         * kept but marked uncertain for review.
         */
        const val DEFAULT_MEDIUM_CONFIDENCE =
            0.6

        /*
         * ambiguityScore above this means the top two
         * categories are too close to choose between.
         */
        const val DEFAULT_AMBIGUITY_THRESHOLD =
            0.7
    }
}
