package com.example.feedsense.analysis.evaluation.comparative

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/*
 * Milestone 8B-8.
 *
 * Comparative evaluation report.
 *
 * A frozen, auditable snapshot of the baseline-vs-8B comparison.
 * Carries full version provenance (dataset / baseline model /
 * fusion / decision / evaluation method / diagnostic / schema) so
 * a report can be reproduced or re-inspected without recomputing
 * against the live dataset.
 *
 * Serialization is lossless and deterministic: every MetricValue
 * keeps its state plus numerator/denominator, every stratum cell
 * keeps its support and suffciency flag, and every count is
 * explicit. No raw item content, prediction text, or screen
 * content is ever embedded (privacy).
 */
data class ComparativeReport(
    val reportId: String,
    val createdAt: LocalDateTime,

    // --------------------------------
    // VERSIONING
    // --------------------------------
    val evaluationMethodVersion: String,
    val evaluationVersion: String,
    val diagnosticVersion: String,
    val schemaVersion: String,
    val datasetVersion: String?,
    val baselineModelVersion: String?,
    val fusionVersion: String?,
    val decisionVersion: String?,

    // --------------------------------
    // POPULATION
    // --------------------------------
    val totalPaired: Int,
    val eligible: Int,
    val ineligible: Int,

    // --------------------------------
    // OUTCOME & OVERALL METRICS
    // --------------------------------
    val outcome: ComparativeMetrics.OutcomeTally,
    val overall: ComparativeMetrics.BothSystems,
    val mcnemar: PairedStats.McNemarResult,
    val effect: PairedStats.EffectSizeResult,

    // --------------------------------
    // PER-CATEGORY
    // --------------------------------
    val perCategoryBaseline:
        Map<String, ComparativeMetrics.PerCategoryEntry>,
    val perCategoryEightB:
        Map<String, ComparativeMetrics.PerCategoryEntry>,

    // --------------------------------
    // MATRICES
    // --------------------------------
    val baselineConfusion: TransitionMatrices.ClassConfusion,
    val eightBConfusion: TransitionMatrices.ClassConfusion,
    val coverageTransition: TransitionMatrices.CoverageTransition,

    // --------------------------------
    // STRATIFICATION
    // --------------------------------
    val stratified: StratifiedComparison.Table,

    // --------------------------------
    // ERROR / ROOT CAUSE
    // --------------------------------
    val errorRootCause: ErrorRootCauseComparison.Comparison,

    // --------------------------------
    // SYNTHESIS
    // --------------------------------
    val conclusion: Conclusion

) {
    enum class Verdict(val label: String) {
        EIGHT_B_IMPROVED("EIGHT_B_IMPROVED"),
        BASELINE_SUPERIOR("BASELINE_SUPERIOR"),
        NO_SIGNIFICANT_DIFFERENCE("NO_SIGNIFICANT_DIFFERENCE"),
        INSUFFICIENT_REAL_DATA_FOR_COMPARATIVE_CONCLUSIONS(
            "INSUFFICIENT_REAL_DATA_FOR_COMPARATIVE_CONCLUSIONS"
        )
    }

    data class Conclusion(
        val verdict: Verdict,
        val headline: String,
        val baselineAccuracy: Double?,
        val eightBAccuracy: Double?,
        val pValue: Double?,
        val discordantPairs: Int,
        val improvementEvidenceQualitativeOnly: Boolean,
        val notes: List<String>
    )

    private fun metricToJson(
        m: com.example.feedsense.analysis.evaluation.MetricValue?
    ): JSONObject {
        val o = JSONObject()
        if (m == null) {
            o.put("present", false)
            return o
        }
        o.put("present", true)
        o.put("state", m.state.name)
        o.put("numerator", m.numerator)
        o.put("denominator", m.denominator)
        m.value?.let { o.put("value", round6(it)) }
        m.confidenceInterval?.let { ci ->
            val c = JSONObject()
            c.put("lower", round6(ci.lower))
            c.put("upper", round6(ci.upper))
            c.put("confidenceLevel", ci.confidenceLevel)
            o.put("confidenceInterval", c)
        }
        m.note?.let { o.put("note", it) }
        return o
    }

    fun toJsonObject(): JSONObject {
        val root = JSONObject()
        root.put("reportId", reportId)
        root.put("createdAt", createdAt.toString())
        root.put("evaluationMethodVersion", evaluationMethodVersion)
        root.put("evaluationVersion", evaluationVersion)
        root.put("diagnosticVersion", diagnosticVersion)
        root.put("schemaVersion", schemaVersion)
        root.put("datasetVersion", datasetVersion ?: "")
        root.put("baselineModelVersion", baselineModelVersion ?: "")
        root.put("fusionVersion", fusionVersion ?: "")
        root.put("decisionVersion", decisionVersion ?: "")

        val pop = JSONObject()
        pop.put("totalPaired", totalPaired)
        pop.put("eligible", eligible)
        pop.put("ineligible", ineligible)
        root.put("population", pop)

        val out = JSONObject()
        out.put("bothCorrect", outcome.bothCorrect)
        out.put("bothWrong", outcome.bothWrong)
        out.put("baselineOnlyCorrect", outcome.baselineOnlyCorrect)
        out.put("eightBOnlyCorrect", outcome.eightBOnlyCorrect)
        out.put("bothUnknown", outcome.bothUnknown)
        out.put("baselineUnknownEightBCorrect",
            outcome.baselineUnknownEightBCorrect)
        out.put("baselineCorrectEightBUnknown",
            outcome.baselineCorrectEightBUnknown)
        out.put("baselineWrongEightBUnknown",
            outcome.baselineWrongEightBUnknown)
        out.put("eightBWrongBaselineUnknown",
            outcome.eightBWrongBaselineUnknown)
        out.put("eightBImprovements", outcome.eightBImprovements)
        out.put("eightBRegressions", outcome.eightBRegressions)
        root.put("outcome", out)

        val overallJson = JSONObject()
        overallJson.put("eligible", overall.eligible)
        overallJson.put("baseline", systemMetricsJson(overall.baseline))
        overallJson.put("eightB", systemMetricsJson(overall.eightB))
        root.put("overall", overallJson)

        val mcn = JSONObject()
        mcn.put("baselineOnlyCorrect", mcnemar.baselineOnlyCorrect)
        mcn.put("eightBOnlyCorrect", mcnemar.eightBOnlyCorrect)
        mcn.put("discordantPairs", mcnemar.discordantPairs)
        mcn.put("sufficient", mcnemar.sufficient)
        mcn.put("exact", mcnemar.exact)
        mcnemar.pValue?.let { mcn.put("pValue", round6(it)) }
        mcnemar.significantAt95?.let {
            mcn.put("significantAt95", it)
        }
        mcnemar.guardReason?.let { mcn.put("guardReason", it) }
        root.put("mcnemar", mcn)

        val eff = JSONObject()
        eff.put("sufficient", effect.sufficient)
        effect.guardReason?.let { eff.put("guardReason", it) }
        effect.baselineAccuracy?.let {
            eff.put("baselineAccuracy", round6(it))
        }
        effect.eightBAccuracy?.let { eff.put("eightBAccuracy", round6(it)) }
        effect.difference?.let { eff.put("difference", round6(it)) }
        effect.oddsRatio?.let { eff.put("oddsRatio", round6(it)) }
        effect.differenceCi?.let { ci ->
            val c = JSONObject()
            c.put("lower", round6(ci.lower))
            c.put("upper", round6(ci.upper))
            c.put("confidenceLevel", ci.confidenceLevel)
            eff.put("differenceConfidenceInterval", c)
        }
        root.put("effectSize", eff)

        root.put("perCategoryBaseline",
            perCategoryJson(perCategoryBaseline))
        root.put("perCategoryEightB", perCategoryJson(perCategoryEightB))

        root.put("baselineConfusion", confusionJson(baselineConfusion))
        root.put("eightBConfusion", confusionJson(eightBConfusion))
        root.put("coverageTransition", transitionJson(coverageTransition))

        val strat = JSONObject()
        strat.put("duration", stratTableJson(stratified.duration))
        strat.put("platform", stratTableJson(stratified.platform))
        strat.put("contentType", stratTableJson(stratified.contentType))
        strat.put("evidenceCoverage", stratTableJson(stratified.evidenceCoverage))
        strat.put("hasOcr", stratTableJson(stratified.hasOcr))
        strat.put("temporalConflict", stratTableJson(stratified.temporalConflict))
        strat.put("representativeFrameOutlier",
            stratTableJson(stratified.representativeFrameOutlier))
        strat.put("eightBDecisionState",
            stratTableJson(stratified.eightBDecisionState))
        root.put("stratified", strat)

        root.put("errorRootCause", errorRootCauseJson(errorRootCause))

        val concl = JSONObject()
        concl.put("verdict", conclusion.verdict.label)
        concl.put("headline", conclusion.headline)
        conclusion.baselineAccuracy?.let {
            concl.put("baselineAccuracy", round6(it))
        }
        conclusion.eightBAccuracy?.let {
            concl.put("eightBAccuracy", round6(it))
        }
        conclusion.pValue?.let { concl.put("pValue", round6(it)) }
        concl.put("discordantPairs", conclusion.discordantPairs)
        concl.put("improvementEvidenceQualitativeOnly",
            conclusion.improvementEvidenceQualitativeOnly)
        val notes = JSONArray()
        conclusion.notes.forEach { notes.put(it) }
        concl.put("notes", notes)
        root.put("conclusion", concl)

        return root
    }

    fun toJson(): String = toJsonObject().toString(2)

    private fun systemMetricsJson(
        m: ComparativeMetrics.SystemMetrics
    ): JSONObject {
        val o = JSONObject()
        o.put("eligible", m.eligible)
        o.put("correct", m.correct)
        o.put("notCorrect", m.notCorrect)
        o.put("unknown", m.unknown)
        o.put("accuracy", metricToJson(m.accuracy))
        o.put("coverage", metricToJson(m.coverage))
        o.put("abstention", metricToJson(m.abstention))
        o.put("selectiveAccuracy", metricToJson(m.selectiveAccuracy))
        return o
    }

    private fun perCategoryJson(
        map: Map<String, ComparativeMetrics.PerCategoryEntry>
    ): JSONObject {
        val o = JSONObject()
        for ((cat, entry) in map) {
            val e = JSONObject()
            when (entry) {
                is ComparativeMetrics.PerCategoryEntry.Defined -> {
                    e.put("state", "DEFINED")
                    e.put("truthSupport", entry.truthSupport)
                    e.put("tp", entry.tp)
                    e.put("fp", entry.fp)
                    e.put("fn", entry.fn)
                    e.put("precision", metricToJson(entry.precision))
                    e.put("recall", metricToJson(entry.recall))
                    e.put("f1", metricToJson(entry.f1))
                }
                is ComparativeMetrics.PerCategoryEntry.Insufficient -> {
                    e.put("state", "INSUFFICIENT")
                    e.put("truthSupport", entry.truthSupport)
                    e.put("tp", entry.tp)
                    e.put("fp", entry.fp)
                    e.put("fn", entry.fn)
                }
            }
            o.put(cat, e)
        }
        return o
    }

    private fun confusionJson(
        c: TransitionMatrices.ClassConfusion
    ): JSONObject {
        val o = JSONObject()
        o.put("categories", JSONArray(c.categories))
        val counts = JSONObject()
        for ((truth, row) in c.counts) {
            val r = JSONObject()
            for ((pred, n) in row) r.put(pred, n)
            counts.put(truth, r)
        }
        o.put("counts", counts)
        o.put("rowTotals", JSONObject(c.rowTotals))
        o.put("colTotals", JSONObject(c.colTotals))
        return o
    }

    private fun transitionJson(
        t: TransitionMatrices.CoverageTransition
    ): JSONObject {
        val o = JSONObject()
        o.put("eligible", t.eligible)
        val mat = JSONObject()
        for ((b, row) in t.matrix) {
            mat.put(b, JSONObject(row))
        }
        o.put("matrix", mat)
        return o
    }

    private fun stratTableJson(
        cells: Map<String, StratifiedComparison.StratumCell>
    ): JSONObject {
        val o = JSONObject()
        for ((key, cell) in cells) {
            val c = JSONObject()
            c.put("pairs", cell.pairs)
            c.put("eligible", cell.eligible)
            c.put("sufficient", cell.sufficient)
            cell.metrics?.let { m ->
                c.put("baselineAccuracy",
                    metricToJson(m.baseline.accuracy))
                c.put("eightBAccuracy", metricToJson(m.eightB.accuracy))
                c.put("baselineCoverage",
                    metricToJson(m.baseline.coverage))
                c.put("eightBCoverage", metricToJson(m.eightB.coverage))
                c.put("baselineSelective",
                    metricToJson(m.baseline.selectiveAccuracy))
                c.put("eightBSelective",
                    metricToJson(m.eightB.selectiveAccuracy))
            }
            cell.tally?.let { t ->
                c.put("baselineOnlyCorrect", t.baselineOnlyCorrect)
                c.put("eightBOnlyCorrect", t.eightBOnlyCorrect)
            }
            cell.mcnemar?.let { mc ->
                c.put("mcnemarSufficient", mc.sufficient)
                mc.pValue?.let { c.put("mcnemarP", round6(it)) }
            }
            o.put(key, c)
        }
        return o
    }

    private fun errorRootCauseJson(
        c: ErrorRootCauseComparison.Comparison
    ): JSONObject {
        val o = JSONObject()
        o.put("analyzedPairs", c.analyzedPairs)
        val et = JSONObject()
        c.errorTypesBySystem.forEach { (sys, map) ->
            et.put(sys, JSONObject(map))
        }
        o.put("errorTypesBySystem", et)
        val at = JSONObject()
        c.attributionBySystem.forEach { (sys, map) ->
            at.put(sys, JSONObject(map))
        }
        o.put("attributionBySystem", at)
        o.put("overconfidentWrongBySystem",
            JSONObject(c.overconfidentWrongBySystem))
        o.put("underconfidentCorrectBySystem",
            JSONObject(c.underconfidentCorrectBySystem))
        return o
    }

    private fun round6(d: Double): Double =
        kotlin.math.round(d * 1_000_000.0) / 1_000_000.0
}
