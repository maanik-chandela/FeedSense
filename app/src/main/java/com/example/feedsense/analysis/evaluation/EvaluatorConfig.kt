package com.example.feedsense.analysis.evaluation

import com.example.feedsense.model.EvaluationItem

// --------------------------------
// EVALUATION CONFIG (Milestone 8A-3)
// --------------------------------
//
// The centralized, frozen configuration of a single
// evaluation run. Recorded in the run so the exact tunables
// that produced a report are reproducible, and so two runs
// are only comparable when they shared the same config.
//
// Every rule 8A-3 makes tunable lives here; none is hidden in
// a calculator.

data class EvaluatorConfig(
    /*
     * Which item statuses are ELIGIBLE for metric computation.
     * UNREVIEWED (STATUS_NOT_EVALUATED) is never eligible.
     */
    val eligibleStatuses: Set<String> = setOf(
        EvaluationItem.STATUS_EVALUATED
    ),

    /*
     * Whether DISPUTED items are included. Per the milestone,
     * DISPUTED is only ever included explicitly by config.
     * Default excludes them.
     */
    val includeDisputed: Boolean = false,

    /*
     * Percentage confidence level for Wilson intervals.
     */
    val confidenceLevel: Double = 0.95,

    /*
     * Minimum positive-truth samples a proportion metric needs
     * before it is reported as a DEFINED estimate (otherwise
     * INSUFFICIENT_SAMPLE_SIZE).
     */
    val minimumSampleClass: Int = 1,
    val minimumSampleBinary: Int = 1,
    val minimumSampleCalibration: Int = 1,

    /*
     * Width of the confidence bins for calibration.
     */
    val confidenceBucketWidth: Double = 0.1,

    /*
     * Duration tolerances (seconds) at which "within-tolerance"
     * accuracy is reported.
     */
    val durationTolerancesSeconds: List<Int> = listOf(1, 3, 5, 10),

    /*
     * The frozen duration tolerance used by EvaluationRecord.
     * Reported here so consumers can cross-reference.
     */
    val evaluationDurationToleranceSeconds: Int = 5,

    /*
     * A short human-readable tag recorded with the run (e.g.
     * "first nightly cohort").
     */
    val description: String = ""
) {

    companion object {
        const val SCHEMA_VERSION = "1.0.0"
    }
}