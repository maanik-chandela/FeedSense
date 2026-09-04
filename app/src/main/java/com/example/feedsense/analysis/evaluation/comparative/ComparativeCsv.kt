package com.example.feedsense.analysis.evaluation.comparative

/*
 * Milestone 8B-8.
 *
 * Deterministic CSV export of a ComparativeReport.
 *
 * Two CSV tables are produced:
 *   - overall: one row of version + population + outcome + overall
 *     metrics + McNemar + effect-size summary.
 *   - strata : one row per stratum cell (across all
 *     stratifications), each with support, accuracy, coverage and
 *     selective accuracy for both systems, and the McNemar guard.
 *
 * Only COUNTS and computed metrics are exported. No raw item
 * content, prediction text, or screen content is ever written
 * (privacy preserved).
 */
object ComparativeCsv {

    private fun esc(v: Any?): String {
        if (v == null) return ""
        val s = if (v is Double) fmt(v) else v.toString()
        return if (s.contains(',') || s.contains('"') ||
            s.contains('\n')) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else s
    }

    private fun fmt(d: Double): String =
        String.format("%.6f", d).replace(Regex("\\.?0+$"), "")

    private fun metricValue(
        m: com.example.feedsense.analysis.evaluation.MetricValue
    ): String {
        return when (m.state) {
            com.example.feedsense.analysis.evaluation.MetricValue.State.DEFINED ->
                if (m.value != null) fmt(m.value!!) else ""
            else -> m.state.name
        }
    }

    /**
     * Returns the overall summary as CSV text (header + one row).
     */
    fun overallCsv(report: ComparativeReport): String {
        val header = listOf(
            "reportId",
            "datasetVersion", "baselineModelVersion", "fusionVersion",
            "decisionVersion", "evaluationVersion", "diagnosticVersion",
            "schemaVersion",
            "totalPaired", "eligible", "ineligible",
            "bothCorrect", "bothWrong", "baselineOnlyCorrect",
            "eightBOnlyCorrect", "bothUnknown",
            "baselineUnknownEightBCorrect",
            "baselineCorrectEightBUnknown",
            "baselineWrongEightBUnknown",
            "eightBWrongBaselineUnknown",
            "eightBImprovements", "eightBRegressions",
            "baselineAccuracy", "eightBAccuracy",
            "baselineCoverage", "eightBCoverage",
            "baselineSelective", "eightBSelective",
            "baselineAbstention", "eightBAbstention",
            "discordantPairs",
            "mcnemarSufficient", "mcnemarExact", "pValue",
            "effectSufficient", "difference",
            "conclusionVerdict"
        )
        val row = listOf(
            report.reportId,
            report.datasetVersion ?: "", report.baselineModelVersion ?: "",
            report.fusionVersion ?: "", report.decisionVersion ?: "",
            report.evaluationVersion, report.diagnosticVersion,
            report.schemaVersion,
            report.totalPaired, report.eligible, report.ineligible,
            report.outcome.bothCorrect, report.outcome.bothWrong,
            report.outcome.baselineOnlyCorrect,
            report.outcome.eightBOnlyCorrect,
            report.outcome.bothUnknown,
            report.outcome.baselineUnknownEightBCorrect,
            report.outcome.baselineCorrectEightBUnknown,
            report.outcome.baselineWrongEightBUnknown,
            report.outcome.eightBWrongBaselineUnknown,
            report.outcome.eightBImprovements,
            report.outcome.eightBRegressions,
            metricValue(report.overall.baseline.accuracy),
            metricValue(report.overall.eightB.accuracy),
            metricValue(report.overall.baseline.coverage),
            metricValue(report.overall.eightB.coverage),
            metricValue(report.overall.baseline.selectiveAccuracy),
            metricValue(report.overall.eightB.selectiveAccuracy),
            metricValue(report.overall.baseline.abstention),
            metricValue(report.overall.eightB.abstention),
            report.mcnemar.discordantPairs,
            report.mcnemar.sufficient,
            report.mcnemar.exact,
            report.mcnemar.pValue ?: "",
            report.effect.sufficient,
            report.effect.difference ?: "",
            report.conclusion.verdict.label
        )
        return (listOf(header, row))
            .joinToString("\n") { line ->
                line.joinToString(",") { esc(it) }
            }
    }

    /**
     * Returns the stratified table as CSV text (header + rows).
     */
    fun strataCsv(report: ComparativeReport): String {
        val header = listOf(
            "stratification", "stratum", "pairs", "eligible",
            "sufficient",
            "baselineAccuracy", "eightBAccuracy",
            "baselineCoverage", "eightBCoverage",
            "baselineSelective", "eightBSelective",
            "baselineOnlyCorrect", "eightBOnlyCorrect",
            "mcnemarSufficient", "mcnemarP"
        )
        val rows = mutableListOf<List<Any?>>()

        fun add(name: String, cells: Map<String, StratifiedComparison.StratumCell>) {
            for ((key, cell) in cells) {
                rows.add(
                    listOf(
                        name, key, cell.pairs, cell.eligible,
                        cell.sufficient,
                        cell.metrics?.baseline?.accuracy?.let {
                            metricValue(it)
                        } ?: "",
                        cell.metrics?.eightB?.accuracy?.let {
                            metricValue(it)
                        } ?: "",
                        cell.metrics?.baseline?.coverage?.let {
                            metricValue(it)
                        } ?: "",
                        cell.metrics?.eightB?.coverage?.let {
                            metricValue(it)
                        } ?: "",
                        cell.metrics?.baseline?.selectiveAccuracy?.let {
                            metricValue(it)
                        } ?: "",
                        cell.metrics?.eightB?.selectiveAccuracy?.let {
                            metricValue(it)
                        } ?: "",
                        cell.tally?.baselineOnlyCorrect ?: "",
                        cell.tally?.eightBOnlyCorrect ?: "",
                        cell.mcnemar?.sufficient ?: "",
                        cell.mcnemar?.pValue?.let { fmt(it) } ?: ""
                    )
                )
            }
        }

        add("duration", report.stratified.duration)
        add("platform", report.stratified.platform)
        add("contentType", report.stratified.contentType)
        add("evidenceCoverage", report.stratified.evidenceCoverage)
        add("hasOcr", report.stratified.hasOcr)
        add("temporalConflict", report.stratified.temporalConflict)
        add("representativeFrameOutlier",
            report.stratified.representativeFrameOutlier)
        add("eightBDecisionState", report.stratified.eightBDecisionState)

        val lines = buildList {
            add(header)
            rows.forEach { add(it) }
        }
        return lines.joinToString("\n") { line ->
            line.joinToString(",") { esc(it) }
        }
    }
}
