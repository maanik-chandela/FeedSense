package com.example.feedsense.analysis.evidence.decision

/*
 * Milestone 8B-7.
 *
 * Item decision state.
 *
 * An explicit state for the item-level prediction rather
 * than forcing every item into a category. This state is
 * what distinguishes "we decided" from "we abstain"
 * honestly.
 *
 * States:
 *
 *   DECIDED:
 *     Evidence is sufficient and one candidate clearly
 *     dominates. Example: SPORTS with strong support,
 *     all other categories weak.
 *
 *   AMBIGUOUS:
 *     Two or more semantic interpretations remain
 *     plausible with substantial support for both.
 *     Example: SPORTS vs ADVERTISEMENT. We do NOT
 *     arbitrarily select one because it has a slightly
 *     larger score.
 *
 *   INSUFFICIENT_EVIDENCE:
 *     Evidence coverage or quality is too low to support
 *     a defensible semantic decision. Examples: extremely
 *     short exposure, very few usable frames, privacy
 *     masking, no useful OCR, excessive temporal gaps.
 *
 *   UNKNOWN:
 *     Available evidence cannot be mapped to a supported
 *     category. Distinct from INSUFFICIENT_EVIDENCE:
 *     here there may be plenty of evidence, but it does
 *     not support any canonical category.
 *
 * Important:
 *   - UNKNOWN ≠ INSUFFICIENT_EVIDENCE (different failure
 *     modes). UNKNOWN = evidence present but unmappable;
 *     INSUFFICIENT_EVIDENCE = not enough reliable
 *     evidence to decide at all.
 *   - UNKNOWN must NOT be used to hide errors or inflate
 *     reported accuracy.
 */
enum class DecisionState(val label: String) {

    /*
     * One candidate clearly dominates with sufficient
     * evidence.
     */
    DECIDED("DECIDED"),

    /*
     * Two or more interpretations remain plausible.
     */
    AMBIGUOUS("AMBIGUOUS"),

    /*
     * Not enough reliable evidence to make a decision.
     */
    INSUFFICIENT_EVIDENCE("INSUFFICIENT_EVIDENCE"),

    /*
     * Evidence present but cannot be mapped to a
     * supported category.
     */
    UNKNOWN("UNKNOWN")
}
