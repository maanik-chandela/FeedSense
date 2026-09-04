package com.example.feedsense.analysis.evaluation

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.model.EvaluationItem
import com.example.feedsense.model.EvaluationRecord
import java.time.LocalDateTime
import java.util.UUID

// --------------------------------
// EVALUATION ENGINE (Milestone 8A-3)
// --------------------------------
//
// Pure, deterministic orchestrator. Consumes fully-assembled
// EvaluationUnits (of which there are only eligible ones --
// the caller is responsible for data assembly) and:
//
//   1. records eligibility/exclusion on the FEW basis records
//      still needed at this layer
//   2. computes each independent metric section
//   3. assembles the frozen EvaluationReport
//
// It never mutates any stored data. To keep it unit-testable
// without a database, data assembly (querying evaluation_items
// + ai_predictions + ground_truths + evaluation_results) lives
// in EvaluationRepository; this class is pure math over the
// already-loaded models.
//
// Excluded items are passed in by the caller (already filtered
// by Eligibility.select using the repository's lookups).

class EvaluationEngine private constructor() {

    companion object {

        const val METHOD_VERSION = EvaluatorConfig.SCHEMA_VERSION

        /**
         * Assembles the report.
         *
         * @param units        eligible evaluation units
         * @param excluded      excluded items (with reasons), from
         *                      Eligibility.select
         * @param config        the frozen run config
         * @param runId         run id (defaults to a fresh UUID)
         * @param datasetVersion dataset version label for the run
         * @param modelVersion   model version label for the run
         * @param feedItemIds    resolved feed item id per eval item
         * @param createdAt      run timestamp
         */
        fun evaluate(
            units: List<EvaluationUnit>,
            excluded: List<Eligibility.ExcludedItem>,
            config: EvaluatorConfig,
            runId: String = UUID.randomUUID().toString(),
            datasetVersion: String? = null,
            modelVersion: String? = null,
            feedItemIds: Map<String, String> = emptyMap(),
            createdAt: LocalDateTime = LocalDateTime.now()
        ): EvaluationReport {

            val eligibleIds = units.map { it.item.id }.toSet()

            val exclusions = excluded.map {
                EvaluationReport.ExclusionRecord(
                    evaluationItemId = it.item.id,
                    reason = reasonLabel(it.reason)
                )
            }

            // --------------------------------
            // 1. CATEGORY (multiclass, primary label)
            // --------------------------------
            //
            // Primary-category comparison is meaningful only on
            // items whose truth is a definitive CLEAR/AMBIGUOUS/
            // MIXED verdict (not AMBIGUITY_UNKNOWN). We include
            // PARTIAL (mixed where the AI's secondary matched)
            // in the denominator but resolve its predicted/truth
            // primary labels the same way the stored result did.
            val definitiveUnits =
                units.filter { it.hasDefinitiveVerdict }

            val categoryPairs =
                definitiveUnits.map {
                    CategoryCatalog.normalize(it.prediction.category) to
                        CategoryCatalog.normalize(it.truth.category)
                }

            val categoryMetrics =
                CategoryMetrics.compute(
                    pairs = categoryPairs,
                    classLabels = CategoryCatalog.keys,
                    minimumSample = config.minimumSampleClass,
                    confidenceLevel = config.confidenceLevel
                )

            // --------------------------------
            // 2. MULTI-LABEL
            // --------------------------------
            //
            // Set-of-labels comparison using primary + secondaries.
            // Only definitive-verdict items with a non-UNKNOWN
            // ambiguity are used (UNKNOWN truth means the AI's
            // label set cannot be judged).
            val multiLabelSamples =
                definitiveUnits.map { unit ->
                    MultiLabelSample(
                        predicted =
                            buildSet {
                                CategoryCatalog
                                    .normalize(unit.prediction.category)
                                    ?.let { add(it) }
                                unit.prediction.secondaryCategories.forEach {
                                    CategoryCatalog
                                        .normalize(it)
                                        ?.let { label -> add(label) }
                                }
                            },
                        truth =
                            buildSet {
                                CategoryCatalog
                                    .normalize(unit.truth.category)
                                    ?.let { add(it) }
                                unit.truth.secondaryCategories.forEach {
                                    CategoryCatalog
                                        .normalize(it)
                                        ?.let { label -> add(label) }
                                }
                            }
                    )
                }

            val multiLabelMetrics =
                MultiLabelMetrics.compute(
                    samples = multiLabelSamples,
                    minimumSample = config.minimumSampleBinary,
                    confidenceLevel = config.confidenceLevel
                )

            // --------------------------------
            // 3. PLATFORM (multiclass agreement)
            // --------------------------------
            val platformPairs =
                definitiveUnits
                    .mapNotNull { unit ->
                        val truthPlatform = optional(unit.truth.platform)
                        if (truthPlatform == null) {
                            null
                        } else {
                            optional(unit.prediction.platform) to truthPlatform
                        }
                    }

            val platformMetrics =
                CategoryMetrics.compute(
                    pairs = platformPairs,
                    classLabels = emptyList(),
                    minimumSample = config.minimumSampleClass,
                    confidenceLevel = config.confidenceLevel
                )

            // --------------------------------
            // 4. CONTENT TYPE (multiclass agreement)
            // --------------------------------
            val contentTypePairs =
                definitiveUnits
                    .mapNotNull { unit ->
                        val t = unit.truth.contentType
                        if (t.isNullOrBlank()) {
                            null
                        } else {
                            optional(unit.prediction.contentType) to t
                        }
                    }

            val contentTypeMetrics =
                CategoryMetrics.compute(
                    pairs = contentTypePairs,
                    classLabels = possibleContentTypes(),
                    minimumSample = config.minimumSampleClass,
                    confidenceLevel = config.confidenceLevel
                )

            // --------------------------------
            // 5. DURATION (regression)
            // --------------------------------
            // Only items whose truth supplied a duration.
            val durationPairs =
                units.mapNotNull { unit ->
                    val t = unit.truth.durationSeconds
                    if (t != null) {
                        unit.prediction.durationSeconds to t
                    } else {
                        null
                    }
                }

            val durationMetrics =
                DurationMetrics.compute(
                    pairs = durationPairs,
                    tolerances = config.durationTolerancesSeconds,
                    minimumSample = 1
                )

            // --------------------------------
            // 6. SKIP (binary, tri-state truth)
            // --------------------------------
            val skipPairs =
                units.map {
                    it.prediction.skipped to it.truth.skipped
                }

            val skipMetrics =
                SkipMetrics.compute(
                    pairs = skipPairs,
                    minimumSample = config.minimumSampleBinary,
                    confidenceLevel = config.confidenceLevel
                )

            // --------------------------------
            // 7. INTERACTIONS (per-signal binary)
            // --------------------------------
            val interactionItems =
                units.map { unit ->
                    unit.prediction.interactionSignals.toSet() to
                        interactionTruthMap(unit.truth)
                }

            val interactionMetrics =
                InteractionMetrics.compute(
                    items = interactionItems,
                    minimumSample = config.minimumSampleBinary,
                    confidenceLevel = config.confidenceLevel
                )

            // --------------------------------
            // 8. TOPIC + TONE (exact-match agreement)
            // --------------------------------
            val topicPairs =
                units.map { unit ->
                    (unit.result.topicAgreement ?: false) to
                        (!unit.truth.topic.isNullOrBlank())
                }
            val tonePairs =
                units.map { unit ->
                    (unit.result.toneAgreement ?: false) to
                        (!unit.truth.tone.isNullOrBlank())
                }

            val topicMetrics =
                TopicToneMetrics.compute(
                    pairs = topicPairs,
                    dimension = "topic"
                )
            val toneMetrics =
                TopicToneMetrics.compute(
                    pairs = tonePairs,
                    dimension = "tone"
                )

            // --------------------------------
            // 9. CALIBRATION (confidence vs correctness)
            // --------------------------------
            val calibrationPairs =
                definitiveUnits.mapNotNull { unit ->
                    unit.prediction.confidence
                        ?.let { conf ->
                            conf to
                                (unit.result.verdict ==
                                    EvaluationRecord.VERDICT_CORRECT)
                        }
                }

            val calibrationMetrics =
                CalibrationMetrics.compute(
                    pairs = calibrationPairs,
                    bucketWidth = config.confidenceBucketWidth,
                    confidenceLevel = config.confidenceLevel
                )

            // --------------------------------
            // 10. DATASET BALANCE + VERDICT DISTRIBUTION
            // --------------------------------
            val inchStats = units
                .groupBy { it.item.evaluationStatus }
                .mapValues { it.value.size }

            val verdictDist = units
                .groupBy { it.result.verdict }
                .mapValues { it.value.size }
                .toSortedMap()

            val balance =
                buildBalanceReport(units)

            val sampleSummary = EvaluationReport.SampleSummary(
                corrected =
                    verdictDist[EvaluationRecord.VERDICT_CORRECT] ?: 0,
                incorrect =
                    verdictDist[EvaluationRecord.VERDICT_INCORRECT] ?: 0,
                partial =
                    verdictDist[EvaluationRecord.VERDICT_PARTIAL] ?: 0,
                unknown =
                    verdictDist[EvaluationRecord.VERDICT_UNKNOWN] ?: 0,
                uncomparable =
                    verdictDist[EvaluationRecord.VERDICT_UNCOMPARABLE] ?: 0,
                reviewed =
                    inchStats[EvaluationItem.STATUS_EVALUATED] ?: 0,
                disputed =
                    inchStats[EvaluationItem.STATUS_DISPUTED] ?: 0
            )

            // --------------------------------
            // 11. ERROR RECORDS (drill-down)
            // --------------------------------
            val errorRecords =
                units.filter {
                    it.result.verdict == EvaluationRecord.VERDICT_INCORRECT
                }.map { unit ->
                    EvaluationReport.ErrorRecord(
                        evaluationItemId = unit.item.id,
                        feedItemId =
                            feedItemIds[unit.item.id]
                                ?: unit.prediction.feedItemId,
                        predictedCategory =
                            unit.prediction.category,
                        truthCategory = unit.truth.category,
                        confidence = unit.prediction.confidence,
                        modelVersion = unit.prediction.modelVersion,
                        datasetVersion = unit.item.datasetVersion,
                        verdict = unit.result.verdict,
                        durationErrorSeconds =
                            unit.result.durationErrorSeconds,
                        skippedAgreement = unit.result.skippedAgreement,
                        topicAgreement = unit.result.topicAgreement,
                        toneAgreement = unit.result.toneAgreement,
                        platformAgreement = unit.result.platformAgreement,
                        contentTypeAgreement =
                            unit.result.contentTypeAgreement,
                        interactionSignalsDisagreement =
                            unit.result.interactionSignalsDisagreement
                    )
                }

            return EvaluationReport(
                runId = runId,
                datasetVersion = datasetVersion,
                modelVersion = modelVersion,
                evaluationMethodVersion = METHOD_VERSION,
                createdAt = createdAt,
                config = config,
                totalItems = units.size + excluded.size,
                eligibleItems = units.size,
                excludedItems = excluded.size,
                exclusions = exclusions,
                categoryMetrics = categoryMetrics,
                multiLabelMetrics = multiLabelMetrics,
                platformMetrics = platformMetrics,
                contentTypeMetrics = contentTypeMetrics,
                durationMetrics = durationMetrics,
                skipMetrics = skipMetrics,
                interactionMetrics = interactionMetrics,
                topicMetrics = topicMetrics,
                toneMetrics = toneMetrics,
                calibrationMetrics = calibrationMetrics,
                datasetBalance = balance,
                verdictDistribution = verdictDist,
                sampleSummary = sampleSummary,
                errorRecords = errorRecords
            )
        }

        private fun reasonLabel(reason: Eligibility.Reason): String {
            return when (reason) {
                Eligibility.Reason.UNREVIEWED -> "UNREVIEWED"
                Eligibility.Reason.DISPUTED_EXCLUDED -> "DISPUTED"
                Eligibility.Reason.MISSING_PREDICTION -> "NO_PREDICTION"
                Eligibility.Reason.MISSING_TRUTH -> "NO_TRUTH"
                Eligibility.Reason.MISSING_RESULT -> "NO_RESULT"
                is Eligibility.Reason.Other -> reason.detail
            }
        }

        private fun optional(value: String?): String? {
            return value?.trim()?.takeIf { it.isNotEmpty() }
        }

        private fun interactionTruthMap(
            truth: com.example.feedsense.model.GroundTruth
        ): Map<String, Boolean?> {
            return buildMap {
                com.example.feedsense.model.GroundTruth
                    .INTERACTION_SIGNAL_KEYS.forEach { key ->
                        put(
                            key,
                            when (key) {
                                com.example.feedsense.model.GroundTruth
                                    .INTERACTION_LIKED -> truth.liked
                                com.example.feedsense.model.GroundTruth
                                    .INTERACTION_COMMENTED -> truth.commented
                                com.example.feedsense.model.GroundTruth
                                    .INTERACTION_SHARED -> truth.shared
                                com.example.feedsense.model.GroundTruth
                                    .INTERACTION_SAVED -> truth.saved
                                com.example.feedsense.model.GroundTruth
                                    .INTERACTION_FOLLOWED -> truth.followed
                                com.example.feedsense.model.GroundTruth
                                    .INTERACTION_PAUSED -> truth.paused
                                com.example.feedsense.model.GroundTruth
                                    .INTERACTION_PLAYING -> truth.playing
                                else -> null
                            }
                        )
                    }
            }
        }

        private fun possibleContentTypes(): List<String> {
            return com.example.feedsense.model.GroundTruth.VALID_CONTENT_TYPES
        }

        private fun buildBalanceReport(
            units: List<EvaluationUnit>
        ): DatasetBalanceReport {

            val categories =
                units.mapNotNull { CategoryCatalog.normalize(it.truth.category) }
            val platforms = units.mapNotNull { optional(it.truth.platform) }
            val contentTypes = units.mapNotNull { optional(it.truth.contentType) }
            val sources = units.mapNotNull { optional(it.prediction.source) }
            val models = units.mapNotNull { it.prediction.modelVersion }
            val durations = units.mapNotNull { it.truth.durationSeconds }
                .map { durationBucket(it) }

            return DatasetBalanceReport(
                byCategory = DatasetBalanceReport.balance(categories),
                byPlatform = DatasetBalanceReport.balance(platforms),
                byContentType = DatasetBalanceReport.balance(contentTypes),
                bySource = DatasetBalanceReport.balance(sources),
                byModelVersion = DatasetBalanceReport.balance(models),
                byDurationBucket = DatasetBalanceReport.balance(durations),
                totalItems = units.size,
                notes = buildList {
                    DatasetBalanceReport.normalizedEntropy(categories)
                        ?.let { add("category entropy=${String.format("%.3f", it)}") }
                    DatasetBalanceReport.imbalanceRatio(categories)
                        ?.let { add("category imbalance ratio=${String.format("%.2f", it)}") }
                }
            )
        }

        private fun durationBucket(seconds: Int): String {
            return when {
                seconds < 15 -> "0-14s"
                seconds < 30 -> "15-29s"
                seconds < 60 -> "30-59s"
                seconds < 180 -> "60-179s"
                else -> "180s+"
            }
        }
    }
}