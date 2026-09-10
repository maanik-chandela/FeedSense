package com.example.feedsense.analysis.evaluation.experiment

import com.example.feedsense.analysis.evaluation.comparative.ComparativeReport

/**
 * Milestone 8B-9.
 *
 * Summary of a controlled real-data experiment.
 *
 * A frozen, immutable bundle of everything a reader needs to
 * interpret the run honestly:
 *
 *   - the definition and snapshot (audit / idempotency)
 *   - the selection accounting (eligible vs every exclusion)
 *   - leakage protection outcome
 *   - dataset quality report
 *   - the experiment's five-way outcome tally and McNemar-ready
 *     contingency
 *   - the real-data status flag
 *   - the baseline safety statement
 *   - the full reused 8B-8 comparative report (metrics, matrices,
 *     per-category, stratification, root cause, conclusion)
 *
 * No accuracy claim is asserted here. The presence of a number does
 * not imply it is meaningful; only the real-data status and the
 * comparative conclusion decide that.
 */
data class ExperimentSummary(
    val definition: ExperimentDefinition,
    val snapshot: ExperimentSnapshot,
    val selection: DatasetSelection.SelectionResult,
    val leakage: LeakageGuard.LeakageReport,
    val datasetQuality: DatasetQualityReport,
    val outcome: ExperimentAnalyzer.OutcomeTally,
    val mcnemar: ExperimentAnalyzer.McNemarReady,
    val realDataStatus: RealDataStatus,
    val baselineSafety: BaselineSafetyStatement,
    val comparativeReport: ComparativeReport
) {

    /**
     * Real-data status of the experiment. Strict: with insufficient
     * real paired data we do NOT claim a comparative conclusion.
     */
    enum class RealDataStatus(val label: String) {
        INSUFFICIENT_REAL_DATA("INSUFFICIENT_REAL_DATA"),
        SUFFICIENT_REAL_DATA("SUFFICIENT_REAL_DATA")
    }

    /**
     * Baseline safety statement. Documents that this run did NOT
     * mutate the authoritative baseline, any session, any
     * prediction, or any evaluation record, and did not change the
     * production pipeline.
     */
    data class BaselineSafetyStatement(
        val baselineUnmodified: Boolean = true,
        val feedItemsUnmodified: Boolean = true,
        val sessionsUnmodified: Boolean = true,
        val predictionsUnmodified: Boolean = true,
        val evaluationRecordsUnmodified: Boolean = true,
        val noPipelineChanges: Boolean = true,
        val observationalOnly: Boolean = true,
        val note: String = "Observational/evaluative experiment only; " +
            "the authoritative baseline AI result is never modified."
    ) {
        val safe: Boolean
            get() = baselineUnmodified && feedItemsUnmodified &&
                sessionsUnmodified && predictionsUnmodified &&
                evaluationRecordsUnmodified && noPipelineChanges &&
                observationalOnly
    }

    /** Convenience: eligible paired observations in this experiment. */
    val eligible: Int
        get() = outcome.eligible

    /** Total paired observations. */
    val paired: Int
        get() = outcome.paired
}
