package com.example.feedsense.analysis.evidence.decision

/*
 * Milestone 8B-8.
 *
 * Comparison state.
 *
 * A controlled, deterministic classification of the
 * relationship between the authoritative baseline AI
 * prediction and the experimental evidence-aware
 * prediction for the same FeedItem.
 *
 * States:
 *
 *   AGREEMENT:
 *     Both systems named the same primary category.
 *     Descriptive only — does NOT mean the prediction is
 *     correct (correctness requires ground truth).
 *
 *   CATEGORY_DISAGREEMENT:
 *     Both systems decided on different primary categories.
 *
 *   BASELINE_UNKNOWN:
 *     Baseline lacks a usable category (null or empty).
 *
 *   EVIDENCE_AWARE_UNKNOWN:
 *     Evidence-aware system abstained (UNKNOWN state) or
 *     could not determine a category.
 *
 *   BOTH_UNKNOWN:
 *     Neither system has a usable primary category.
 *
 *   INSUFFICIENT_EVIDENCE:
 *     The evidence-aware system explicitly determined
 *     that evidence was insufficient for a defensible
 *     decision (INSUFFICIENT_EVIDENCE state).
 *
 *   REVIEW_REQUIRED:
 *     The evidence-aware path identifies a case that
 *     requires human review (ambiguity, high uncertainty,
 *     representative-frame outlier, internal transition).
 *
 * Design:
 *   - Controlled enum (no free-text statuses).
 *   - Deterministic: same inputs → same state.
 *   - Descriptive, not prescriptive.
 *   - Single primary state per comparison.
 */
enum class ComparisonState(val label: String) {

    /*
     * Both paths agreed on the same primary category.
     */
    AGREEMENT("AGREEMENT"),

    /*
     * Both decided different primary categories.
     */
    CATEGORY_DISAGREEMENT("CATEGORY_DISAGREEMENT"),

    /*
     * Baseline had no usable category.
     */
    BASELINE_UNKNOWN("BASELINE_UNKNOWN"),

    /*
     * Evidence-aware path produced no category.
     */
    EVIDENCE_AWARE_UNKNOWN("EVIDENCE_AWARE_UNKNOWN"),

    /*
     * Neither path had a usable category.
     */
    BOTH_UNKNOWN("BOTH_UNKNOWN"),

    /*
     * Evidence-aware system explicitly determined
     * evidence was insufficient for a defensible
     * decision.
     */
    INSUFFICIENT_EVIDENCE("INSUFFICIENT_EVIDENCE"),

    /*
     * Evidence-aware path identifies this case as
     * requiring human review.
     */
    REVIEW_REQUIRED("REVIEW_REQUIRED")
}
