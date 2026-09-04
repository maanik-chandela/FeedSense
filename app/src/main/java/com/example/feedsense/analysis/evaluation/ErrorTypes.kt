package com.example.feedsense.analysis.evaluation

// --------------------------------
// ERROR ANALYSIS TAXONOMY (Milestone 8A-5)
// --------------------------------
//
// Frozen, controlled vocabularies used by the error-analysis
// layer. 8A-5 explains *when/why* the AI is wrong without
// changing the production model or retraining. Every constant
// here is a stable identifier; the human-readable mapping is
// a pure function so there is a single source of truth.
//
// The 16 capability identifiers parallel the 8A-4
// capability-eligibility vocabulary. The 19 error types are
// the controlled taxonomy the spec requires. Root-cause
// analysis is intentionally split across three evidence
// levels (OBSERVED / HYPOTHESIS / CONFIRMED) so no fabricated
// "ground truth cause" is ever written; 8A-5 itself only ever
// produces OBSERVED and HYPOTHESIS records.

object ErrorTypes {

    // --------------------------------
    // CAPABILITIES (16)
    // --------------------------------

    const val CAP_CATEGORY = "CATEGORY"
    const val CAP_PLATFORM = "PLATFORM"
    const val CAP_CONTENT_TYPE = "CONTENT_TYPE"
    const val CAP_DURATION = "DURATION"
    const val CAP_SKIP = "SKIP"
    const val CAP_LIKE = "LIKE"
    const val CAP_COMMENT = "COMMENT"
    const val CAP_SHARE = "SHARE"
    const val CAP_SAVE = "SAVE"
    const val CAP_FOLLOW = "FOLLOW"
    const val CAP_PAUSE = "PAUSE"
    const val CAP_PLAY = "PLAY"
    const val CAP_TOPIC = "TOPIC"
    const val CAP_TONE = "TONE"
    const val CAP_SEGMENTATION = "SEGMENTATION"
    const val CAP_CALIBRATION = "CALIBRATION"

    val ALL_CAPABILITIES: List<String> = listOf(
        CAP_CATEGORY,
        CAP_PLATFORM,
        CAP_CONTENT_TYPE,
        CAP_DURATION,
        CAP_SKIP,
        CAP_LIKE,
        CAP_COMMENT,
        CAP_SHARE,
        CAP_SAVE,
        CAP_FOLLOW,
        CAP_PAUSE,
        CAP_PLAY,
        CAP_TOPIC,
        CAP_TONE,
        CAP_SEGMENTATION,
        CAP_CALIBRATION
    )

    // --------------------------------
    // ERROR TYPES (19)
    // --------------------------------

    const val ERROR_WRONG_CLASS = "WRONG_CLASS"
    const val ERROR_FALSE_POSITIVE = "FALSE_POSITIVE"
    const val ERROR_FALSE_NEGATIVE = "FALSE_NEGATIVE"
    const val ERROR_MISSED_DETECTION = "MISSED_DETECTION"
    const val ERROR_OVERCONFIDENT_ERROR = "OVERCONFIDENT_ERROR"
    const val ERROR_UNDERCONFIDENT_CORRECT = "UNDERCONFIDENT_CORRECT"
    const val ERROR_AMBIGUOUS_CONTENT = "AMBIGUOUS_CONTENT"
    const val ERROR_MULTI_LABEL_MISMATCH = "MULTI_LABEL_MISMATCH"
    const val ERROR_TEMPORAL = "TEMPORAL_ERROR"
    const val ERROR_SEGMENTATION = "SEGMENTATION_ERROR"
    const val ERROR_EVIDENCE = "EVIDENCE_ERROR"
    const val ERROR_PLATFORM = "PLATFORM_ERROR"
    const val ERROR_CONTENT_TYPE = "CONTENT_TYPE_ERROR"
    const val ERROR_DURATION = "DURATION_ERROR"
    const val ERROR_INTERACTION = "INTERACTION_ERROR"
    const val ERROR_TOPIC = "TOPIC_ERROR"
    const val ERROR_TONE = "TONE_ERROR"
    const val ERROR_SKIP = "SKIP_ERROR"
    const val ERROR_UNCOMPARABLE = "UNCOMPARABLE"

    val ALL_ERROR_TYPES: List<String> = listOf(
        ERROR_WRONG_CLASS,
        ERROR_FALSE_POSITIVE,
        ERROR_FALSE_NEGATIVE,
        ERROR_MISSED_DETECTION,
        ERROR_OVERCONFIDENT_ERROR,
        ERROR_UNDERCONFIDENT_CORRECT,
        ERROR_AMBIGUOUS_CONTENT,
        ERROR_MULTI_LABEL_MISMATCH,
        ERROR_TEMPORAL,
        ERROR_SEGMENTATION,
        ERROR_EVIDENCE,
        ERROR_PLATFORM,
        ERROR_CONTENT_TYPE,
        ERROR_DURATION,
        ERROR_INTERACTION,
        ERROR_TOPIC,
        ERROR_TONE,
        ERROR_SKIP,
        ERROR_UNCOMPARABLE
    )

    /*
     * The capability a given error type primarily explains.
     * Some error types are cross-cutting (over/under-confidence,
     * ambiguity, evidence) and map to CALIBRATION-related
     * capabilities.
     */
    fun capabilityFor(error: String): String {
        return when (error) {
            ERROR_WRONG_CLASS,
            ERROR_FALSE_POSITIVE,
            ERROR_FALSE_NEGATIVE,
            ERROR_MULTI_LABEL_MISMATCH -> CAP_CATEGORY
            ERROR_PLATFORM -> CAP_PLATFORM
            ERROR_CONTENT_TYPE -> CAP_CONTENT_TYPE
            ERROR_DURATION -> CAP_DURATION
            ERROR_SKIP -> CAP_SKIP
            ERROR_INTERACTION -> CAP_LIKE
            ERROR_TOPIC -> CAP_TOPIC
            ERROR_TONE -> CAP_TONE
            ERROR_SEGMENTATION -> CAP_SEGMENTATION
            ERROR_TEMPORAL -> CAP_DURATION
            ERROR_OVERCONFIDENT_ERROR,
            ERROR_UNDERCONFIDENT_CORRECT,
            ERROR_AMBIGUOUS_CONTENT,
            ERROR_EVIDENCE -> CAP_CALIBRATION
            ERROR_UNCOMPARABLE -> CAP_SEGMENTATION
            else -> CAP_CALIBRATION
        }
    }

    /*
     * Cross-cutting error types are attached to a unit but do
     * not themselves mark a specific content capability as
     * failed. This lets the pattern report separate "which
     * capability output was wrong" from "what kind of failure
     * it was".
     */
    fun isCrossCutting(error: String): Boolean {
        return error == ERROR_OVERCONFIDENT_ERROR ||
            error == ERROR_UNDERCONFIDENT_CORRECT ||
            error == ERROR_AMBIGUOUS_CONTENT ||
            error == ERROR_EVIDENCE
    }

    // --------------------------------
    // ROOT-CAUSE EVIDENCE LEVELS
    // --------------------------------
    //
    // 8A-5 never over-claims. A finding is either:
    //
    //   OBSERVED   - deterministically derived from the stored
    //                prediction + truth + result (no inference)
    //   HYPOTHESIS - an aggregate pattern inferred across many
    //                OBSERVED records (correlation, not cause)
    //   CONFIRMED  - only ever set by a human analyst; 8A-5
    //                never writes CONFIRMED on its own
    //
    const val CAUSE_OBSERVED = "OBSERVED"
    const val CAUSE_HYPOTHESIS = "HYPOTHESIS"
    const val CAUSE_CONFIRMED = "CONFIRMED"

    // --------------------------------
    // CONTROLLED ROOT-CAUSE DESCRIPTORS
    // --------------------------------
    //
    // A conservative, non-exhaustive catalogue of the *kinds*
    // of cause the analysis can attribute. These are labels a
    // human analyst may select to move a HYPOTHESIS toward
    // CONFIRMED; 8A-5 itself only ever tags OBSERVED
    // causes which map to the DATA-VS-PREDICTION family.
    //
    const val CAUSE_DATA_VS_PREDICTION =
        "DATA_VS_PREDICTION_MISMATCH"
    const val CAUSE_AMBIGUITY =
        "GENUINE_CONTENT_AMBIGUITY"
    const val CAUSE_CLASSIFIER_CALIBRATION =
        "CLASSIFIER_CALIBRATION"
    const val CAUSE_SEGMENTATION =
        "SEGMENTATION_BOUNDARY"
    const val CAUSE_TEMPORAL =
        "TEMPORAL_TIMING_ERROR"
    const val CAUSE_MISSING_EVIDENCE =
        "MISSING_FRAME_EVIDENCE"
    const val CAUSE_PLATFORM_MISDETECT =
        "PLATFORM_MISDETECTION"
    const val CAUSE_TAXONOMY_LIMIT =
        "TAXONOMY_COVERAGE_LIMIT"
    const val CAUSE_ANNOTATION =
        "ANNOTATION_DISAGREEMENT"

    val ALL_ROOT_CAUSES: List<String> = listOf(
        CAUSE_DATA_VS_PREDICTION,
        CAUSE_AMBIGUITY,
        CAUSE_CLASSIFIER_CALIBRATION,
        CAUSE_SEGMENTATION,
        CAUSE_TEMPORAL,
        CAUSE_MISSING_EVIDENCE,
        CAUSE_PLATFORM_MISDETECT,
        CAUSE_TAXONOMY_LIMIT,
        CAUSE_ANNOTATION
    )

    // --------------------------------
    // HUMAN-READABLE MAPPINGS
    // --------------------------------

    fun errorDisplay(error: String): String {
        return when (error) {
            ERROR_WRONG_CLASS -> "Wrong primary class predicted"
            ERROR_FALSE_POSITIVE -> "Predicted a label the content does not have"
            ERROR_FALSE_NEGATIVE -> "Missed a label the content has"
            ERROR_MISSED_DETECTION -> "Failed to detect content the truth marks present"
            ERROR_OVERCONFIDENT_ERROR -> "Predicted with high confidence but was wrong"
            ERROR_UNDERCONFIDENT_CORRECT -> "Predicted correctly but with low confidence"
            ERROR_AMBIGUOUS_CONTENT -> "Content is genuinely ambiguous to the human"
            ERROR_MULTI_LABEL_MISMATCH -> "Predicted label set differs from truth set"
            ERROR_TEMPORAL -> "Timing-related error (duration/temporal disagree)"
            ERROR_SEGMENTATION -> "Segmentation boundary differs from truth"
            ERROR_EVIDENCE -> "Prediction made without recorded frame evidence"
            ERROR_PLATFORM -> "Platform detected incorrectly"
            ERROR_CONTENT_TYPE -> "Content type classified incorrectly"
            ERROR_DURATION -> "Duration estimate inaccurate beyond tolerance"
            ERROR_INTERACTION -> "Interaction signal disagreement"
            ERROR_TOPIC -> "Topic predicted incorrectly"
            ERROR_TONE -> "Tone predicted incorrectly"
            ERROR_SKIP -> "Skipped/watch state predicted incorrectly"
            ERROR_UNCOMPARABLE -> "Prediction and truth not comparable"
            else -> error
        }
    }

    fun capabilityDisplay(capability: String): String {
        return when (capability) {
            CAP_CATEGORY -> "Category"
            CAP_PLATFORM -> "Platform"
            CAP_CONTENT_TYPE -> "Content Type"
            CAP_DURATION -> "Duration"
            CAP_SKIP -> "Skip"
            CAP_LIKE -> "Like"
            CAP_COMMENT -> "Comment"
            CAP_SHARE -> "Share"
            CAP_SAVE -> "Save"
            CAP_FOLLOW -> "Follow"
            CAP_PAUSE -> "Pause"
            CAP_PLAY -> "Play"
            CAP_TOPIC -> "Topic"
            CAP_TONE -> "Tone"
            CAP_SEGMENTATION -> "Segmentation"
            CAP_CALIBRATION -> "Calibration"
            else -> capability
        }
    }

    fun rootCauseDisplay(cause: String): String {
        return when (cause) {
            CAUSE_DATA_VS_PREDICTION ->
                "Prediction diverged from ground truth"
            CAUSE_AMBIGUITY ->
                "Content genuinely resists a single label"
            CAUSE_CLASSIFIER_CALIBRATION ->
                "Classifier confidence is not well calibrated"
            CAUSE_SEGMENTATION ->
                "Item segmentation boundaries differed"
            CAUSE_TEMPORAL ->
                "Predicted timing did not match observed timing"
            CAUSE_MISSING_EVIDENCE ->
                "Insufficient frame evidence for the prediction"
            CAUSE_PLATFORM_MISDETECT ->
                "Platform signal was misidentified"
            CAUSE_TAXONOMY_LIMIT ->
                "Content falls outside taxonomy coverage"
            CAUSE_ANNOTATION ->
                "Annotators disagreed on the truth"
            else -> cause
        }
    }
}
