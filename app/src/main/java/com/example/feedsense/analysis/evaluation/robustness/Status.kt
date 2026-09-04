package com.example.feedsense.analysis.evaluation.robustness

/*
 * Milestone 8B-9.
 *
 * Shared statistical status vocabulary.
 *
 * A single, explicit status enum used everywhere in the robustness
 * layer so that "not enough data" is NEVER rendered as a zero or a
 * plausible-looking number. The three data states mirror the 8B-8
 * guarded style but are specific to statistical testing.
 */
enum class StatisticalStatus(val label: String) {
    SUFFICIENT("SUFFICIENT"),
    INSUFFICIENT_DATA("INSUFFICIENT_DATA"),
    NOT_APPLICABLE("NOT_APPLICABLE");

    companion object {
        const val INSUFFICIENT_DATA =
            "INSUFFICIENT_DATA"
        const val INSUFFICIENT_REAL_DATA_FOR_STATISTICAL_CONCLUSIONS =
            "INSUFFICIENT_REAL_DATA_FOR_STATISTICAL_CONCLUSIONS"
    }
}

/*
 * The scientific role of a given analysis within the report.
 *
 * PRIMARY_HYPOTHESIS : the single pre-defined main comparison.
 * SECONDARY_ANALYSIS : pre-registered supporting comparisons
 *                      (sensitivity/robustness) that inform, but do
 *                      not by themselves establish, the headline.
 * EXPLORATORY_ANALYSIS: data-driven subgroup hunting; never treated
 *                      as an independent discovery.
 */
enum class AnalysisRole(val label: String) {
    PRIMARY_HYPOTHESIS("PRIMARY_HYPOTHESIS"),
    SECONDARY_ANALYSIS("SECONDARY_ANALYSIS"),
    EXPLORATORY_ANALYSIS("EXPLORATORY_ANALYSIS")
}
