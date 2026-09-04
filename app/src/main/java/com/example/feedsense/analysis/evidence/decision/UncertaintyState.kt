package com.example.feedsense.analysis.evidence.decision

/*
 * Milestone 8B-7.
 *
 * Uncertainty state.
 *
 * An interpretable uncertainty assessment for the item
 * level decision. This is separate from the decision
 * state: a DECIDED item can still have MODERATE
 * uncertainty, and an INSUFFICIENT_EVIDENCE item has
 * HIGH uncertainty.
 *
 * States:
 *
 *   LOW:
 *     Strong, diverse, well-covered evidence with clear
 *     support for the leading category.
 *
 *   MODERATE:
 *     Adequate evidence but with some coverage gaps,
 *     limited source diversity, or a modest decision
 *     margin.
 *
 *   HIGH:
 *     Poor coverage, low source diversity, conflicting
 *     evidence, or a thin decision margin. Decision carries
 *     substantial risk.
 *
 *   UNRESOLVED:
 *     Evidence is so weak or contradictory that no
 *     meaningful confidence can be assigned. The item
 *     should generally abstain.
 *
 * Design:
 *   - Interpretable: components (coverage, quality,
 *     conflict, diversity, margin) drive the state.
 *   - Not a fake numeric confidence; it is an explicit
 *     label over interpretable inputs.
 */
enum class UncertaintyState(val label: String) {

    LOW("LOW"),
    MODERATE("MODERATE"),
    HIGH("HIGH"),
    UNRESOLVED("UNRESOLVED")
}
