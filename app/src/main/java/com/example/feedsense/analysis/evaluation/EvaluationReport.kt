package com.example.feedsense.analysis.evaluation

import java.time.LocalDateTime

// --------------------------------
// EVALUATION REPORT (Milestone 8A-3)
// --------------------------------
//
// The frozen output of one evaluation run. Carries run
// identity + provenance, the exact config, the eligibility /
// exclusion trace, dataset balance, and every independent
// metric section (each computed and storable independently so
// a partial or degenerate dataset does not corrupt the rest).
//
// This object is the unit of reproducibility: serializing it
// to JSON (one row in evaluation_runs) lets any later consumer
// re-inspect the exact numbers, denominators, and rules.

data class EvaluationReport(

    // --------------------------------
    // RUN IDENTITY / PROVENANCE
    // --------------------------------

    val runId: String,

    val datasetVersion: String?,

    val modelVersion: String?,

    val evaluationMethodVersion: String,

    val createdAt: LocalDateTime,

    val config: EvaluatorConfig,

    // --------------------------------
    // COHORT / ELIGIBILITY TRACE
    // --------------------------------

    val totalItems: Int,

    val eligibleItems: Int,

    val excludedItems: Int,

    val exclusions: List<ExclusionRecord>,

    // --------------------------------
    // INDEPENDENT METRIC SECTIONS
    // --------------------------------

    val categoryMetrics: CategoryMetrics?,

    val multiLabelMetrics: MultiLabelMetrics?,

    val platformMetrics: CategoryMetrics?,

    val contentTypeMetrics: CategoryMetrics?,

    val durationMetrics: DurationMetrics?,

    val skipMetrics: SkipMetrics?,

    val interactionMetrics: InteractionMetrics?,

    val topicMetrics: TopicToneMetrics?,

    val toneMetrics: TopicToneMetrics?,

    val calibrationMetrics: CalibrationMetrics?,

    val datasetBalance: DatasetBalanceReport,

    // --------------------------------
    // SAMPLE COUNTS + VARIANTS
    // --------------------------------

    val verdictDistribution: Map<String, Int>,

    val sampleSummary: SampleSummary,

    val errorRecords: List<ErrorRecord>
) {

    // A tiny record so exclusions are visible with reasons.
    data class ExclusionRecord(
        val evaluationItemId: String,
        val reason: String
    )

    // How many items produced each verdict and status.
    data class SampleSummary(
        val corrected: Int,
        val incorrect: Int,
        val partial: Int,
        val unknown: Int,
        val uncomparable: Int,
        val reviewed: Int,
        val disputed: Int
    )

    // 8A-3: error records must be retrievable with the
    // FeedItem + prediction + truth + model/dataset versions.
    data class ErrorRecord(
        val evaluationItemId: String,
        val feedItemId: String?,
        val predictedCategory: String?,
        val truthCategory: String?,
        val confidence: Double?,
        val modelVersion: String?,
        val datasetVersion: String?,
        val verdict: String,
        val durationErrorSeconds: Int?,
        val skippedAgreement: Boolean?,
        val topicAgreement: Boolean?,
        val toneAgreement: Boolean?,
        val platformAgreement: Boolean?,
        val contentTypeAgreement: Boolean?,
        val interactionSignalsDisagreement: Int?
    )
}