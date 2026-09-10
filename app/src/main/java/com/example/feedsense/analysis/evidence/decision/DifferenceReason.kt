package com.example.feedsense.analysis.evidence.decision

/*
 * Milestone 8B-8.
 *
 * Difference reason.
 *
 * A controlled, deterministic taxonomy of structured
 * reasons why the evidence-aware system may differ from
 * the authoritative baseline AI prediction.
 *
 * Design:
 *   - Controlled enum (no free-text explanations).
   - One or more reasons may apply per comparison.
 *   - UNKNOWN is always preferred over inventing a
 *     fabricated explanation.
 *   - Only reasons supported by the actual evidence are
 *     included.
 *   - A comparison may have an empty reason list when
 *     there is no difference.
 *
 * Important:
 *   - These are DESCRIPTIVE, not PRESCRIPTIVE.
 *   - They do NOT imply either system is "correct".
 *   - Correctness requires ground truth (8A layer).
 */
enum class DifferenceReason(val label: String) {

    /*
     * The representative frame suggested a different
     * category than the temporal evidence context.
     */
    REPRESENTATIVE_FRAME_CONFLICT(
        "REPRESENTATIVE_FRAME_CONFLICT"
    ),

    /*
     * Temporal context (multiple frames over time) led
     * the evidence-aware system to a different decision
     * than the single-frame baseline.
     */
    TEMPORAL_CONTEXT_CHANGED_DECISION(
        "TEMPORAL_CONTEXT_CHANGED_DECISION"
    ),

    /*
     * OCR text evidence changed the decision relative
     * to the baseline (e.g. text visible across frames
     * not captured by the representative frame alone).
     */
    OCR_CHANGED_DECISION("OCR_CHANGED_DECISION"),

    /*
     * Platform detection evidence affected the decision
     * differently from the baseline.
     */
    PLATFORM_EVIDENCE_CHANGED_DECISION(
        "PLATFORM_EVIDENCE_CHANGED_DECISION"
    ),

    /*
     * Interaction evidence (likes, shares, comments,
     * etc.) affected the decision differently.
     */
    INTERACTION_EVIDENCE_CHANGED_DECISION(
        "INTERACTION_EVIDENCE_CHANGED_DECISION"
    ),

    /*
     * Differences in how content was segmented into
     * items led to different classification.
     */
    SEGMENTATION_DIFFERENCE("SEGMENTATION_DIFFERENCE"),

    /*
     * The available evidence was low-information and
     * did not support a clear category distinction.
     */
    LOW_INFORMATION_EVIDENCE("LOW_INFORMATION_EVIDENCE"),

    /*
     * The interaction was very short, providing
     * limited evidence for reliable classification.
     */
    SHORT_INTERACTION("SHORT_INTERACTION"),

    /*
     * The content legitimately falls on the boundary
     * between two categories.
     */
    CATEGORY_BOUNDARY("CATEGORY_BOUNDARY"),

    /*
     * The evidence-aware system determined evidence was
     * insufficient for a defensible comparison.
     */
    INSUFFICIENT_EVIDENCE("INSUFFICIENT_EVIDENCE"),

    /*
     * The two models (baseline classifier vs
     * evidence-aware decision) inherently disagree.
     */
    MODEL_DISAGREEMENT("MODEL_DISAGREEMENT"),

    /*
     * The 8B-10 sanitization pipeline redacted OCR text
     * that would otherwise have supported the decision.
     * The evidence-driven decision was, itself, altered by
     * privacy redaction.
     */
    PRIVACY_REDACTION_AFFECTED_DECISION(
        "PRIVACY_REDACTION_AFFECTED_DECISION"
    ),

    /*
     * Evidence that would have aided the decision was lost
     * because sanitization transformed or removed the frame
     * content.
     */
    EVIDENCE_LOST_DUE_TO_SANITIZATION(
        "EVIDENCE_LOST_DUE_TO_SANITIZATION"
    ),

    /*
     * The cause of the difference is uncertain. Always
     * preferred over inventing an explanation.
     */
    UNKNOWN("UNKNOWN")
}
