package com.example.feedsense.analysis.evaluation

import java.time.LocalDateTime
import java.util.UUID

// --------------------------------
// ERROR ANALYSIS ENGINE (Milestone 8A-5)
// --------------------------------
//
// Pure, deterministic orchestrator that consumes an already
// assembled list of EvaluationUnits (the same units the 8A-3
// engine consumes) and produces a frozen ErrorAnalysisReport.
//
// Like EvaluationEngine, it never touches the database and never
// mutates stored data. It is the single entry point a caller
// uses to turn a cohort into the error-analysis view.

class ErrorAnalysisEngine private constructor() {

    companion object {

        fun analyze(
            units: List<EvaluationUnit>,
            runId: String = UUID.randomUUID().toString(),
            datasetVersion: String? = null,
            modelVersion: String? = null,
            createdAt: LocalDateTime = LocalDateTime.now()
        ): ErrorAnalysisReport {

            val realData = units.isNotEmpty()
            val allErrors = units.flatMap { ErrorAnalyzer.detect(it) }
            val detectedByUnit = allErrors
                .groupBy { it.evaluationItemId }
            val unitsWithErrors = detectedByUnit.size

            // --------------------------------
            // Per-capability roll-up
            // --------------------------------

            val perCapability = ErrorTypes.ALL_CAPABILITIES.mapNotNull { cap ->
                val errors = allErrors.filter { it.capability == cap }
                if (errors.isEmpty() && cap != ErrorTypes.CAP_SEGMENTATION) {
                    null
                } else {
                    ErrorAnalysisReport.CapabilitySummary(
                        capability = cap,
                        errorCount = errors.size,
                        affectedUnits = errors.map { it.evaluationItemId }
                            .distinct().size,
                        errorTypes = errors.map { it.errorType }.distinct()
                    )
                }
            }

            // --------------------------------
            // Per-error-type roll-up
            // --------------------------------

            val perErrorType = ErrorTypes.ALL_ERROR_TYPES.mapNotNull { type ->
                val errors = allErrors.filter { it.errorType == type }
                if (errors.isEmpty()) {
                    null
                } else {
                    ErrorAnalysisReport.ErrorTypeSummary(
                        errorType = type,
                        count = errors.size,
                        affectedUnits = errors.map { it.evaluationItemId }
                            .distinct().size,
                        dominantSeverity = ErrorSeverity.baseSeverity(type),
                        researchImpactCount =
                            errors.count { it.researchImpact },
                        overconfidentCount =
                            errors.count { it.overconfident }
                    )
                }
            }

            // --------------------------------
            // Error queue (severity-descending)
            // --------------------------------

            val queue = ErrorAnalyzer
                .orderedBySeverity(allErrors)
                .mapIndexed { index, error ->
                    ErrorAnalysisReport.QueueEntry(
                        sequence = index + 1,
                        evaluationItemId = error.evaluationItemId,
                        capability = error.capability,
                        errorType = error.errorType,
                        severity = error.severity,
                        sourceEvidence = error.sourceEvidence,
                        rootCause = error.rootCause,
                        researchImpact = error.researchImpact,
                        details = error.details
                    )
                }

            // --------------------------------
            // Confusion matrices (category + content type)
            // --------------------------------

            val categoryMatrix = ErrorConfusionMatrix.build(units)
            val matrices = linkedMapOf<String, ErrorConfusionMatrix.Matrix>()
            if (categoryMatrix.labels.isNotEmpty()) {
                matrices[ErrorTypes.CAP_CATEGORY] = categoryMatrix
            }
            val contentTypeRelevant = units.any {
                !it.truth.contentType.isNullOrBlank()
            }
            if (contentTypeRelevant) {
                val contentTypeMatrix = buildSimpleMatrix(
                    units = units,
                    predicted = { it.prediction.contentType ?: "UNKNOWN" },
                    truth = { it.truth.contentType },
                    noTruth = { it.truth.contentType.isNullOrBlank() }
                )
                if (contentTypeMatrix.labels.isNotEmpty()) {
                    matrices[ErrorTypes.CAP_CONTENT_TYPE] = contentTypeMatrix
                }
            }

            // --------------------------------
            // Confidence vs correctness
            // --------------------------------

            val confidence = if (realData) {
                ConfidenceAnalysis.analyze(units)
            } else {
                null
            }

            // --------------------------------
            // Patterns
            // --------------------------------

            val patterns =
                mutableListOf<ErrorAnalysisReport.Pattern>()

            patterns += ErrorPatternReport.buildPatterns(
                detectedByUnit = detectedByUnit,
                realDataAvailable = realData
            )

            // Global overconfidence pattern from the confidence
            // analysis, gated by sample size.
            val confidentErrorRate =
                confidence?.confidentErrorRate
            if (confidence != null &&
                confidence.confidentSampleCount >= ErrorPatternReport.MIN_PATTERN_SAMPLE &&
                confidentErrorRate != null
            ) {
                patterns += ErrorAnalysisReport.Pattern(
                    id = "GLOBAL_OVERCONFIDENCE",
                    title = "Overconfident errors",
                    description = String.format(
                        "%.1f%% of confident predictions (>= %.2f) " +
                            "were wrong (%d of %d).",
                        confidentErrorRate * 100.0,
                        ErrorAnalyzer.OVERCONFIDENCE_THRESHOLD,
                        confidence.confidentErrorCount,
                        confidence.confidentSampleCount
                    ),
                    evidenceLevel = ErrorTypes.CAUSE_HYPOTHESIS,
                    capability = ErrorTypes.CAP_CALIBRATION,
                    severity = ErrorSeverity.HIGH,
                    sampleCount = confidence.confidentSampleCount,
                    researchImpact = confidentErrorRate >= 0.2
                )
            }

            val severityDistribution = allErrors
                .groupBy { it.severity }
                .mapValues { it.value.size }
                .toSortedMap(compareBy { ErrorSeverity.severityRank(it) })

            val finding = ErrorPatternReport.buildFinding(
                realDataAvailable = realData,
                totalUnits = units.size,
                totalErrors = allErrors.size,
                unitsWithErrors = unitsWithErrors
            )

            val qualityFlags = ErrorPatternReport.buildQualityFlags(
                realDataAvailable = realData,
                totalUnits = units.size,
                unitsWithErrors = unitsWithErrors
            )

            return ErrorAnalysisReport(
                runId = runId,
                datasetVersion = datasetVersion,
                modelVersion = modelVersion,
                createdAt = createdAt,
                analysisMethodVersion = ErrorAnalysisReport.METHOD_VERSION,
                totalUnits = units.size,
                unitsWithErrors = unitsWithErrors,
                totalErrors = allErrors.size,
                realDataAvailable = realData,
                perCapability = perCapability,
                perErrorType = perErrorType,
                errorQueue = queue,
                confusionMatrices = matrices,
                confidence = confidence,
                patterns = patterns,
                finding = finding,
                severityDistribution = severityDistribution,
                qualityFlags = qualityFlags
            )
        }

        private fun buildSimpleMatrix(
            units: List<EvaluationUnit>,
            predicted: (EvaluationUnit) -> String,
            truth: (EvaluationUnit) -> String?,
            noTruth: (EvaluationUnit) -> Boolean
        ): ErrorConfusionMatrix.Matrix {
            val cells = mutableMapOf<Pair<String, String>, Int>()
            units.forEach { unit ->
                if (noTruth(unit)) return@forEach
                val t = truth(unit)!!
                val p = predicted(unit)
                val key = Pair(p, t)
                cells[key] = (cells[key] ?: 0) + 1
            }
            val labels = (cells.keys.map { it.first } +
                cells.keys.map { it.second }).distinct().sorted()
            return ErrorConfusionMatrix.Matrix(labels = labels, cells = cells)
        }
    }
}
