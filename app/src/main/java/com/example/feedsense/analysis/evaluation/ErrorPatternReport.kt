package com.example.feedsense.analysis.evaluation

// --------------------------------
// ERROR PATTERN REPORT (Milestone 8A-5)
// --------------------------------
//
// Turns many OBSERVED errors into a small number of
// conservative HYPOTHESIS patterns, and produces quality flags
// that warn when a conclusion would be statistically unsafe.
//
// Pattern rules (anti-fabrication):
//   - A pattern is only emitted when at least MIN_PATTERN_SAMPLE
//     affected units support it.
//   - Patterns are labelled HYPOTHESIS, never CONFIRMED.
//   - On zero real data a single honest finding is emitted and
//     no other pattern is invented.

object ErrorPatternReport {

    const val MIN_PATTERN_SAMPLE = 5
    const val FINDING_INSUFFICIENT_DATA =
        ErrorAnalysisReport.FINDING_INSUFFICIENT_DATA

    /**
     * Builds conservative patterns from per-unit detected errors.
     *
     * @param detectedByUnit evaluationItemId -> detected errors
     */
    fun buildPatterns(
        detectedByUnit: Map<String, List<ErrorAnalyzer.DetectedError>>,
        realDataAvailable: Boolean
    ): List<ErrorAnalysisReport.Pattern> {

        if (!realDataAvailable || detectedByUnit.isEmpty()) {
            return emptyList()
        }

        val patterns = mutableListOf<ErrorAnalysisReport.Pattern>()

        // ----- Per-capability dominance pattern -----
        // For each capability, which error type dominates, and is
        // the affected count large enough to support a pattern?
        ErrorTypes.ALL_CAPABILITIES.forEach { capability ->
            val affectedByType = detectedByUnit.values
                .flatMap { it.filter { e -> e.capability == capability } }
                .groupingBy { it.errorType }
                .eachCount()

            if (affectedByType.isNotEmpty()) {
                val dominant = affectedByType.maxByOrNull { it.value }!!
                if (dominant.value >= MIN_PATTERN_SAMPLE) {
                    val researchImpact = detectedByUnit.values
                        .any { errors ->
                            errors.any {
                                it.capability == capability &&
                                    it.errorType == dominant.key &&
                                    it.researchImpact
                            }
                        }
                    patterns += ErrorAnalysisReport.Pattern(
                        id = "CAP_${capability}_${dominant.key}",
                        title = "Pattern in ${ErrorTypes.capabilityDisplay(capability)}",
                        description = "${dominant.value} items show " +
                            "${ErrorTypes.errorDisplay(dominant.key)} " +
                            "(${dominant.key}) for the ${capability} capability.",
                        evidenceLevel = ErrorTypes.CAUSE_HYPOTHESIS,
                        capability = capability,
                        severity = dominantSeverity(
                            errorTypes = affectedByType,
                            capability = capability
                        ),
                        sampleCount = dominant.value,
                        researchImpact = researchImpact
                    )
                }
            }
        }

        // ----- Overconfidence pattern (global) -----
        // Supplied separately via the confidence analysis; built
        // here only from observed high-confidence errors.

        return patterns
    }

    /**
     * Computes the dominant severity for a cluster of error
     * types on a capability, by taking the maximum severity rank.
     */
    fun dominantSeverity(
        errorTypes: Map<String, Int>,
        capability: String
    ): String {
        var maxRank = -1
        var dominant = ErrorSeverity.LOW
        errorTypes.forEach { (type, _) ->
            val s = ErrorSeverity.effectiveSeverity(
                error = type,
                overconfident = false,
                capability = capability
            )
            val rank = ErrorSeverity.severityRank(s)
            if (rank > maxRank) {
                maxRank = rank
                dominant = s
            }
        }
        return dominant
    }

    /**
     * Builds the honest top-level finding string.
     */
    fun buildFinding(
        realDataAvailable: Boolean,
        totalUnits: Int,
        totalErrors: Int,
        unitsWithErrors: Int
    ): String {
        if (!realDataAvailable || totalUnits == 0) {
            return FINDING_INSUFFICIENT_DATA
        }
        return String.format(
            "%d error records across %d/%d units reviewed (%d total in cohort).",
            totalErrors, unitsWithErrors, totalUnits, totalUnits
        )
    }

    /**
     * Quality flags that warn when statistical conclusions would
     * be unsafe (small cohort, too few affected units, no real
     * data).
     */
    fun buildQualityFlags(
        realDataAvailable: Boolean,
        totalUnits: Int,
        unitsWithErrors: Int
    ): List<ErrorAnalysisReport.QualityFlag> {
        val flags = mutableListOf<ErrorAnalysisReport.QualityFlag>()

        if (!realDataAvailable || totalUnits == 0) {
            flags += ErrorAnalysisReport.QualityFlag(
                flag = "NO_REAL_DATA",
                severity = ErrorAnalysisReport.FlagSeverity.CRITICAL,
                message = "No evaluated units are available; " +
                    "statistical conclusions are NOT supported."
            )
            return flags
        }

        if (totalUnits < MIN_PATTERN_SAMPLE) {
            flags += ErrorAnalysisReport.QualityFlag(
                flag = "SMALL_COHORT",
                severity = ErrorAnalysisReport.FlagSeverity.WARNING,
                message = "Only $totalUnits units; the " +
                    "recommended minimum for pattern claims is " +
                    "$MIN_PATTERN_SAMPLE."
            )
        }

        if (unitsWithErrors < MIN_PATTERN_SAMPLE) {
            flags += ErrorAnalysisReport.QualityFlag(
                flag = "FEW_ERRORS",
                severity = ErrorAnalysisReport.FlagSeverity.INFO,
                message = "Only $unitsWithErrors units have errors; " +
                    "error patterns may be anecdotal."
            )
        }

        return flags
    }
}
