package com.example.feedsense.analysis.evaluation

// --------------------------------
// ROOT-CAUSE TAXONOMY (Milestone 8A-6)
// --------------------------------
//
// Frozen, controlled vocabularies for root-cause attribution.
// 8A-6 determines whether the suspected reasons behind errors
// are actually supported by evidence, separating:
//
//   OBSERVED FACT   (deterministic, from stored data)
//   HYPOTHESIS      (correlation/possible cause, not causality)
//   HUMAN-CONFIRMED ROOT CAUSE (only a human may set)
//
// This file holds the vocabularies; the logic that generates
// and classifies candidates lives in RootCauseAnalyzer.kt.

object RootCauseTypes {

    // --------------------------------
    // CANDIDATE ROOT CAUSES
    // --------------------------------
    // (8A-6 section 8: reuse the 8A-5 taxonomy plus the
    // required candidate list. An error may have several.)

    const val CAUSE_OCR_FAILURE = "OCR_FAILURE"
    const val CAUSE_VISUAL_AMBIGUITY = "VISUAL_AMBIGUITY"
    const val CAUSE_SEMANTIC_AMBIGUITY = "SEMANTIC_AMBIGUITY"
    const val CAUSE_CATEGORY_BOUNDARY = "CATEGORY_BOUNDARY"
    const val CAUSE_PLATFORM_TEXT_MISSING = "PLATFORM_TEXT_MISSING"
    const val CAUSE_PLATFORM_UI_CONFUSION = "PLATFORM_UI_CONFUSION"
    const val CAUSE_RAPID_CONTENT_CHANGE = "RAPID_CONTENT_CHANGE"
    const val CAUSE_SHORT_INTERACTION = "SHORT_INTERACTION"
    const val CAUSE_REPRESENTATIVE_FRAME_FAILURE =
        "REPRESENTATIVE_FRAME_FAILURE"
    const val CAUSE_SEGMENTATION_FAILURE = "SEGMENTATION_FAILURE"
    const val CAUSE_INTERACTION_UI_AMBIGUITY =
        "INTERACTION_UI_AMBIGUITY"
    const val CAUSE_LOW_INFORMATION_FRAME = "LOW_INFORMATION_FRAME"
    const val CAUSE_OVERLAY_OR_MODAL = "OVERLAY_OR_MODAL"
    const val CAUSE_MULTI_CONTENT_FRAME = "MULTI_CONTENT_FRAME"
    const val CAUSE_TAXONOMY_LIMITATION = "TAXONOMY_LIMITATION"
    const val CAUSE_UNKNOWN_DATA = "UNKNOWN_DATA"
    const val CAUSE_SYSTEM_PIPELINE_FAILURE =
        "SYSTEM_PIPELINE_FAILURE"
    const val CAUSE_OTHER = "OTHER"

    // Additional developer-facing candidates aligned with
    // the 8A-5 root-cause catalogue.
    const val CAUSE_ANNOTATION_DISAGREEMENT =
        "ANNOTATION_DISAGREEMENT"
    const val CAUSE_MISSING_EVIDENCE = "MISSING_EVIDENCE"
    const val CAUSE_CLASSIFIER_CALIBRATION =
        "CLASSIFIER_CALIBRATION"
    const val CAUSE_DATA_VS_PREDICTION =
        "DATA_VS_PREDICTION_MISMATCH"
    const val CAUSE_TEMPORAL = "TEMPORAL_TIMING_ERROR"

    val ALL_CANDIDATE_CAUSES: List<String> = listOf(
        CAUSE_OCR_FAILURE,
        CAUSE_VISUAL_AMBIGUITY,
        CAUSE_SEMANTIC_AMBIGUITY,
        CAUSE_CATEGORY_BOUNDARY,
        CAUSE_PLATFORM_TEXT_MISSING,
        CAUSE_PLATFORM_UI_CONFUSION,
        CAUSE_RAPID_CONTENT_CHANGE,
        CAUSE_SHORT_INTERACTION,
        CAUSE_REPRESENTATIVE_FRAME_FAILURE,
        CAUSE_SEGMENTATION_FAILURE,
        CAUSE_INTERACTION_UI_AMBIGUITY,
        CAUSE_LOW_INFORMATION_FRAME,
        CAUSE_OVERLAY_OR_MODAL,
        CAUSE_MULTI_CONTENT_FRAME,
        CAUSE_TAXONOMY_LIMITATION,
        CAUSE_UNKNOWN_DATA,
        CAUSE_SYSTEM_PIPELINE_FAILURE,
        CAUSE_ANNOTATION_DISAGREEMENT,
        CAUSE_MISSING_EVIDENCE,
        CAUSE_CLASSIFIER_CALIBRATION,
        CAUSE_DATA_VS_PREDICTION,
        CAUSE_TEMPORAL,
        CAUSE_OTHER
    )

    // --------------------------------
    // ATTRIBUTION STATUS (8A-6 section 6)
    // --------------------------------

    const val STATUS_UNASSESSED = "UNASSESSED"
    const val STATUS_POSSIBLE = "POSSIBLE"
    const val STATUS_SUPPORTED = "SUPPORTED"
    const val STATUS_CONFIRMED = "CONFIRMED"
    const val STATUS_REJECTED = "REJECTED"
    const val STATUS_INCONCLUSIVE = "INCONCLUSIVE"
    const val STATUS_NOT_APPLICABLE = "NOT_APPLICABLE"

    val ALL_ATTRIBUTION_STATUSES: List<String> = listOf(
        STATUS_UNASSESSED,
        STATUS_POSSIBLE,
        STATUS_SUPPORTED,
        STATUS_CONFIRMED,
        STATUS_REJECTED,
        STATUS_INCONCLUSIVE,
        STATUS_NOT_APPLICABLE
    )

    // --------------------------------
    // EVIDENCE STRENGTH (8A-6 section 7)
    // --------------------------------
    // INDEPENDENT of AI prediction confidence.

    const val EVIDENCE_NONE = "NONE"
    const val EVIDENCE_WEAK = "WEAK"
    const val EVIDENCE_MODERATE = "MODERATE"
    const val EVIDENCE_STRONG = "STRONG"

    val ALL_EVIDENCE_STRENGTHS: List<String> = listOf(
        EVIDENCE_NONE,
        EVIDENCE_WEAK,
        EVIDENCE_MODERATE,
        EVIDENCE_STRONG
    )

    // --------------------------------
    // ATTRIBUTION CLASS (8A-6 section 24)
    // --------------------------------
    // The central data-vs-model-pipeline-taxonomy-annotation
    // classification.

    const val CLASS_MODEL_RELATED = "MODEL_RELATED"
    const val CLASS_DATA_RELATED = "DATA_RELATED"
    const val CLASS_PIPELINE_RELATED = "PIPELINE_RELATED"
    const val CLASS_TAXONOMY_RELATED = "TAXONOMY_RELATED"
    const val CLASS_ANNOTATION_RELATED = "ANNOTATION_RELATED"
    const val CLASS_UNKNOWN = "UNKNOWN"

    val ALL_ATTRIBUTION_CLASSES: List<String> = listOf(
        CLASS_MODEL_RELATED,
        CLASS_DATA_RELATED,
        CLASS_PIPELINE_RELATED,
        CLASS_TAXONOMY_RELATED,
        CLASS_ANNOTATION_RELATED,
        CLASS_UNKNOWN
    )

    // --------------------------------
    // CAUSE ROLE (8A-6 section 9)
    // --------------------------------

    const val ROLE_PRIMARY = "PRIMARY_CAUSE"
    const val ROLE_CONTRIBUTING = "CONTRIBUTING_CAUSE"

    // --------------------------------
    // EVIDENCE TYPES (8A-6 section 11)
    // --------------------------------

    const val EVIDENCE_REPRESENTATIVE_FRAME =
        "REPRESENTATIVE_FRAME"
    const val EVIDENCE_TEMPORAL_FRAME_SEQUENCE =
        "TEMPORAL_FRAME_SEQUENCE"
    const val EVIDENCE_OCR_TEXT = "OCR_TEXT"
    const val EVIDENCE_PLATFORM_TEXT = "PLATFORM_TEXT"
    const val EVIDENCE_INTERACTION_UI = "INTERACTION_UI"
    const val EVIDENCE_TIMESTAMP_PATTERN = "TIMESTAMP_PATTERN"
    const val EVIDENCE_DURATION_PATTERN = "DURATION_PATTERN"
    const val EVIDENCE_CATEGORY_TRANSITION = "CATEGORY_TRANSITION"
    const val EVIDENCE_SEGMENT_BOUNDARY = "SEGMENT_BOUNDARY"
    const val EVIDENCE_MODEL_CONFIDENCE = "MODEL_CONFIDENCE"
    const val EVIDENCE_PREDICTION_HISTORY = "PREDICTION_HISTORY"
    const val EVIDENCE_GROUND_TRUTH = "GROUND_TRUTH"
    const val EVIDENCE_ANNOTATOR_NOTE = "ANNOTATOR_NOTE"
    const val EVIDENCE_SYSTEM_LOG = "SYSTEM_LOG"

    val ALL_EVIDENCE_TYPES: List<String> = listOf(
        EVIDENCE_REPRESENTATIVE_FRAME,
        EVIDENCE_TEMPORAL_FRAME_SEQUENCE,
        EVIDENCE_OCR_TEXT,
        EVIDENCE_PLATFORM_TEXT,
        EVIDENCE_INTERACTION_UI,
        EVIDENCE_TIMESTAMP_PATTERN,
        EVIDENCE_DURATION_PATTERN,
        EVIDENCE_CATEGORY_TRANSITION,
        EVIDENCE_SEGMENT_BOUNDARY,
        EVIDENCE_MODEL_CONFIDENCE,
        EVIDENCE_PREDICTION_HISTORY,
        EVIDENCE_GROUND_TRUTH,
        EVIDENCE_ANNOTATOR_NOTE,
        EVIDENCE_SYSTEM_LOG
    )

    // --------------------------------
    // ATTRIBUTION CONFIDENCE (8A-6 section 26)
    // --------------------------------
    // Documented meaning:
    //   HIGH   - multiple independent evidence signals and
    //            human confirmation (or very strong, unique
    //            evidence).
    //   MEDIUM - strong evidence but no independent
    //            corroboration.
    //   LOW    - weak evidence or speculative hypothesis.

    const val CONFIDENCE_LOW = "LOW"
    const val CONFIDENCE_MEDIUM = "MEDIUM"
    const val CONFIDENCE_HIGH = "HIGH"

    val ALL_ATTRIBUTION_CONFIDENCES: List<String> = listOf(
        CONFIDENCE_LOW,
        CONFIDENCE_MEDIUM,
        CONFIDENCE_HIGH
    )

    // --------------------------------
    // SEGMENTATION DIAGNOSTIC (8A-6 section 18)
    // --------------------------------

    const val SEG_UNDER = "UNDER_SEGMENTATION"
    const val SEG_OVER = "OVER_SEGMENTATION"
    const val SEG_UNCERTAIN = "BOUNDARY_UNCERTAIN"

    // --------------------------------
    // DURATION ATTRIBUTION (8A-6 section 19)
    // --------------------------------

    const val DUR_CAPTURE_TIMING = "CAPTURE_TIMING"
    const val DUR_SEGMENTATION = "SEGMENTATION"
    const val DUR_MISSING_FRAMES = "MISSING_FRAMES"
    const val DUR_DUPLICATE_FRAMES = "DUPLICATE_FRAMES"
    const val DUR_SESSION_BOUNDARY = "SESSION_BOUNDARY"
    const val DUR_ANNOTATION_ERROR = "ANNOTATION_ERROR"
    const val DUR_UNKNOWN = "UNKNOWN"

    // --------------------------------
    // SHORT-INTERACTION ATTRIBUTION (8A-6 section 20)
    // --------------------------------

    const val SHORT_TOO_SHORT = "TOO_SHORT_TO_IDENTIFY"
    const val SHORT_CAPTURE_MISSED = "CAPTURE_MISSED_FRAMES"
    const val SHORT_POOR_RFRAME = "POOR_REPRESENTATIVE_FRAME"
    const val SHORT_SEGMENTATION = "SEGMENTATION_FAILED"
    const val SHORT_CLASSIFICATION =
        "CLASSIFICATION_FAILED_DESPITE_EVIDENCE"

    // --------------------------------
    // ANNOTATION AGREEMENT (8A-6 section 22)
    // --------------------------------

    const val AGREEMENT_FULL = "FULL_AGREEMENT"
    const val AGREEMENT_PARTIAL = "PARTIAL_AGREEMENT"
    const val AGREEMENT_DISAGREEMENT = "DISAGREEMENT"
    const val AGREEMENT_AMBIGUOUS = "AMBIGUOUS"

    // --------------------------------
    // REVIEW ACTIONS (8A-6 section 28)
    // --------------------------------

    const val ACTION_CONFIRM_CAUSE = "CONFIRM_CAUSE"
    const val ACTION_REJECT_CAUSE = "REJECT_CAUSE"
    const val ACTION_MARK_POSSIBLE = "MARK_POSSIBLE"
    const val ACTION_MARK_INCONCLUSIVE = "MARK_INCONCLUSIVE"
    const val ACTION_MARK_DATA_ISSUE = "MARK_DATA_ISSUE"
    const val ACTION_MARK_TAXONOMY_ISSUE = "MARK_TAXONOMY_ISSUE"
    const val ACTION_MARK_ANNOTATION_ISSUE =
        "MARK_ANNOTATION_ISSUE"
    const val ACTION_MARK_PIPELINE_ISSUE = "MARK_PIPELINE_ISSUE"
    const val ACTION_MARK_MODEL_ISSUE = "MARK_MODEL_ISSUE"

    // --------------------------------
    // EVIDENCE LEVELS (aligned with 8A-5)
    // --------------------------------

    const val LEVEL_OBSERVED = "OBSERVED"
    const val LEVEL_HYPOTHESIS = "HYPOTHESIS"
    const val LEVEL_CONFIRMED = "CONFIRMED"

    // --------------------------------
    // HUMAN-READABLE MAPPINGS
    // --------------------------------

    fun causeDisplay(cause: String): String {
        return when (cause) {
            CAUSE_OCR_FAILURE -> "OCR failed to read on-screen text"
            CAUSE_VISUAL_AMBIGUITY -> "Visual content is ambiguous"
            CAUSE_SEMANTIC_AMBIGUITY -> "Semantic content is ambiguous"
            CAUSE_CATEGORY_BOUNDARY -> "Content sits on a category boundary"
            CAUSE_PLATFORM_TEXT_MISSING -> "Platform text missing from screen"
            CAUSE_PLATFORM_UI_CONFUSION -> "Platform UI confused the detector"
            CAUSE_RAPID_CONTENT_CHANGE -> "Content changed rapidly"
            CAUSE_SHORT_INTERACTION -> "Interaction was too short"
            CAUSE_REPRESENTATIVE_FRAME_FAILURE ->
                "Representative frame was misleading"
            CAUSE_SEGMENTATION_FAILURE -> "Segmentation boundary was wrong"
            CAUSE_INTERACTION_UI_AMBIGUITY ->
                "Interaction UI was ambiguous"
            CAUSE_LOW_INFORMATION_FRAME ->
                "Frame carried little identifying information"
            CAUSE_OVERLAY_OR_MODAL -> "Overlay/modal obscured content"
            CAUSE_MULTI_CONTENT_FRAME -> "Frame contains multiple content pieces"
            CAUSE_TAXONOMY_LIMITATION -> "Category taxonomy lacks coverage"
            CAUSE_UNKNOWN_DATA -> "Underlying data status unknown"
            CAUSE_SYSTEM_PIPELINE_FAILURE -> "Capture/analysis pipeline failed"
            CAUSE_ANNOTATION_DISAGREEMENT -> "Human annotations disagreed"
            CAUSE_MISSING_EVIDENCE -> "Insufficient evidence was available"
            CAUSE_CLASSIFIER_CALIBRATION -> "Classifier confidence mis-calibrated"
            CAUSE_DATA_VS_PREDICTION -> "Prediction diverged from ground truth"
            CAUSE_TEMPORAL -> "Timing discrepancy"
            else -> cause
        }
    }

    fun classDisplay(clazz: String): String {
        return when (clazz) {
            CLASS_MODEL_RELATED -> "Model related"
            CLASS_DATA_RELATED -> "Data related"
            CLASS_PIPELINE_RELATED -> "Pipeline related"
            CLASS_TAXONOMY_RELATED -> "Taxonomy related"
            CLASS_ANNOTATION_RELATED -> "Annotation related"
            else -> "Unknown"
        }
    }
}
