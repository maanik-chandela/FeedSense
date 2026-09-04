package com.example.feedsense.analysis.evaluation.robustness

import com.example.feedsense.analysis.evaluation.comparative.PairedStats
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

/*
 * Milestone 8B-9.
 *
 * Robustness / sensitivity analysis report.
 *
 * A frozen, auditable snapshot of the statistical significance,
 * robustness, and sensitivity analysis sitting above 8B-8.
 * Carries full version provenance and deterministic configuration
 * so the report can be reproduced.
 *
 * The report does NOT modify FeedItem, AiPredictionRecord,
 * GroundTruth, EvaluationItem, or any 8B-6/7/8 result. It is
 * a read-only statistical layer.
 *
 * JSON/CSV export is lossless and deterministic; no raw item
 * content, prediction text, or screen content is ever embedded.
 */
data class RobustnessReport(
    val reportId: String,
    val createdAt: LocalDateTime,

    // --------------------------------
    // VERSIONING
    // --------------------------------
    val statisticalAnalysisVersion: String,
    val evaluationVersion: String,
    val datasetVersion: String?,
    val baselineModelVersion: String?,
    val fusionVersion: String?,
    val decisionVersion: String?,
    val diagnosticVersion: String?,
    val schemaVersion: String,

    // --------------------------------
    // CONFIGURATION
    // --------------------------------
    val analysisSeed: Long,
    val bootstrapIterations: Int,
    val alpha: Double,

    // --------------------------------
    // POPULATION
    // --------------------------------
    val totalPaired: Int,
    val eligible: Int,
    val ineligible: Int,

    // --------------------------------
    // PRIMARY RESULT
    // --------------------------------
    val primaryResult: PairedTestResult,
    val effectSize: AccuracyDifference,

    // --------------------------------
    // CONFIDENCE INTERVALS
    // --------------------------------
    val confidenceIntervals: ConfidenceIntervalAnalysis,

    // --------------------------------
    // BOOTSTRAP
    // --------------------------------
    val bootstrap: BootstrapAnalysis,

    // --------------------------------
    // SENSITIVITY ANALYSES
    // --------------------------------
    val categorySensitivity: List<SubgroupResult>,
    val leaveOneCategoryOut: List<LeaveOneCategoryOutResult>,
    val platformSensitivity: List<SubgroupResult>,
    val durationSensitivity: List<SubgroupResult>,
    val evidenceSensitivity: List<SubgroupResult>,
    val abstentionSensitivity: AbstentionSensitivity,
    val confidenceSensitivity: ConfidenceSensitivity,

    // --------------------------------
    // ERROR ANALYSIS
    // --------------------------------
    val highConfidenceErrors: HighConfidenceError,
    val improvements: ImprovementAnalysis,
    val regressions: RegressionAnalysis,
    val errorConcentration: ErrorConcentration,

    // --------------------------------
    // MULTIPLE-COMPARISON METADATA
    // --------------------------------
    val multipleComparisonMethod: String?,
    val familyOfTests: Int,
    val adjustedAlpha: Double?,

    // --------------------------------
    // INSUFFICIENT DATA GUARDS
    // --------------------------------
    val realDataStatus: String,
    val statisticalStatus: String,

    // --------------------------------
    // CONCLUSION
    // --------------------------------
    val conclusion: Conclusion
) {

    data class LeaveOneCategoryOutResult(
        val excludedCategory: String,
        val remainingSupport: Int,
        val remainingEligible: Int,
        val test: PairedTestResult?,
        val accuracyDifference: Double?,
        val sufficient: Boolean,
        val guardReason: String? = null
    )

    data class Conclusion(
        val statisticalStatus: StatisticalStatus,
        val headline: String,
        val notes: List<String>,
        val exploratoryFindings: List<String>
    )

    // --------------------------------
    // JSON EXPORT
    // --------------------------------

    fun toJsonObject(): JSONObject {
        val root = JSONObject()
        root.put("reportId", reportId)
        root.put("createdAt", createdAt.toString())

        val versioning = JSONObject()
        versioning.put("statisticalAnalysisVersion", statisticalAnalysisVersion)
        versioning.put("evaluationVersion", evaluationVersion)
        versioning.put("datasetVersion", datasetVersion ?: "")
        versioning.put("baselineModelVersion", baselineModelVersion ?: "")
        versioning.put("fusionVersion", fusionVersion ?: "")
        versioning.put("decisionVersion", decisionVersion ?: "")
        versioning.put("diagnosticVersion", diagnosticVersion ?: "")
        versioning.put("schemaVersion", schemaVersion)
        root.put("versioning", versioning)

        val config = JSONObject()
        config.put("analysisSeed", analysisSeed)
        config.put("bootstrapIterations", bootstrapIterations)
        config.put("alpha", alpha)
        root.put("config", config)

        val pop = JSONObject()
        pop.put("totalPaired", totalPaired)
        pop.put("eligible", eligible)
        pop.put("ineligible", ineligible)
        root.put("population", pop)

        root.put("primaryResult", testResultJson(primaryResult))

        val eff = JSONObject()
        eff.put("accuracyDifference", effectSize.accuracyDifference?.let { round6(it) })
        eff.put("netDiscordantImprovement", effectSize.netDiscordantImprovement)
        eff.put("baselineAccuracy", effectSize.baselineAccuracy?.let { round6(it) })
        eff.put("eightBAccuracy", effectSize.eightBAccuracy?.let { round6(it) })
        eff.put("sufficient", effectSize.sufficient)
        effectSize.guardReason?.let { eff.put("guardReason", it) }
        root.put("effectSize", eff)

        root.put("confidenceIntervals", ciJson(confidenceIntervals))
        root.put("bootstrap", bootstrapJson(bootstrap))

        root.put("categorySensitivity", subgroupJson(categorySensitivity))

        val loco = JSONArray()
        for (r in leaveOneCategoryOut) {
            val o = JSONObject()
            o.put("excludedCategory", r.excludedCategory)
            o.put("remainingSupport", r.remainingSupport)
            o.put("remainingEligible", r.remainingEligible)
            o.put("sufficient", r.sufficient)
            r.accuracyDifference?.let { o.put("accuracyDifference", round6(it)) }
            r.test?.let { o.put("test", testResultJson(it)) }
            r.guardReason?.let { o.put("guardReason", it) }
            loco.put(o)
        }
        root.put("leaveOneCategoryOut", loco)

        root.put("platformSensitivity", subgroupJson(platformSensitivity))
        root.put("durationSensitivity", subgroupJson(durationSensitivity))
        root.put("evidenceSensitivity", subgroupJson(evidenceSensitivity))

        root.put("abstentionSensitivity", abstentionJson(abstentionSensitivity))
        root.put("confidenceSensitivity", confidenceSensJson(confidenceSensitivity))
        root.put("highConfidenceErrors", hcErrorJson(highConfidenceErrors))
        root.put("improvements", improvementJson(improvements))
        root.put("regressions", regressionJson(regressions))
        root.put("errorConcentration", ecJson(errorConcentration))

        val mc = JSONObject()
        mc.put("method", multipleComparisonMethod ?: "none")
        mc.put("familyOfTests", familyOfTests)
        adjustedAlpha?.let { mc.put("adjustedAlpha", round6(it)) }
        root.put("multipleComparison", mc)

        val guards = JSONObject()
        guards.put("realDataStatus", realDataStatus)
        guards.put("statisticalStatus", statisticalStatus)
        root.put("guards", guards)

        val concl = JSONObject()
        concl.put("statisticalStatus", conclusion.statisticalStatus.name)
        concl.put("headline", conclusion.headline)
        val notes = JSONArray()
        conclusion.notes.forEach { notes.put(it) }
        concl.put("notes", notes)
        val explor = JSONArray()
        conclusion.exploratoryFindings.forEach { explor.put(it) }
        concl.put("exploratoryFindings", explor)
        root.put("conclusion", concl)

        return root
    }

    fun toJson(): String = toJsonObject().toString(2)

    // --------------------------------
    // CSV EXPORT
    // --------------------------------

    fun toCsv(): String {
        val header = listOf(
            "reportId", "statisticalAnalysisVersion",
            "totalPaired", "eligible", "ineligible",
            "mcnemarN", "mcnemarBaselineOnlyCorrect",
            "mcnemarEightBOnlyCorrect", "mcnemarPValue",
            "mcnemarSignificant", "mcnemarSignificanceThreshold",
            "accuracyDifference", "netDiscordantImprovement",
            "baselineAccuracy", "eightBAccuracy",
            "ciBaselineLower", "ciBaselineUpper",
            "ciEightBLower", "ciEightBUpper",
            "bootstrapObserved", "bootstrapLower", "bootstrapUpper",
            "abstentionPolicyABaseline", "abstentionPolicyAEightB",
            "abstentionPolicyBBaseline", "abstentionPolicyBEightB",
            "totalImprovements", "totalRegressions",
            "statisticalStatus", "conclusionVerdict",
            "alpha", "analysisSeed", "bootstrapIterations"
        )
        val row = listOf(
            reportId, statisticalAnalysisVersion,
            totalPaired, eligible, ineligible,
            primaryResult.n, primaryResult.baselineOnlyCorrect,
            primaryResult.eightBOnlyCorrect,
            primaryResult.pValue?.let { round6(it) } ?: "",
            primaryResult.significant ?: "",
            primaryResult.significanceThreshold,
            effectSize.accuracyDifference?.let { round6(it) } ?: "",
            effectSize.netDiscordantImprovement,
            effectSize.baselineAccuracy?.let { round6(it) } ?: "",
            effectSize.eightBAccuracy?.let { round6(it) } ?: "",
            confidenceIntervals.baselineAccuracy?.confidenceInterval?.lower?.let { round6(it) } ?: "",
            confidenceIntervals.baselineAccuracy?.confidenceInterval?.upper?.let { round6(it) } ?: "",
            confidenceIntervals.eightBAccuracy?.confidenceInterval?.lower?.let { round6(it) } ?: "",
            confidenceIntervals.eightBAccuracy?.confidenceInterval?.upper?.let { round6(it) } ?: "",
            bootstrap.observedDifference?.let { round6(it) } ?: "",
            bootstrap.lowerBound?.let { round6(it) } ?: "",
            bootstrap.upperBound?.let { round6(it) } ?: "",
            abstentionSensitivity.policyA.baselineAccuracy?.let { round6(it) } ?: "",
            abstentionSensitivity.policyA.eightBAccuracy?.let { round6(it) } ?: "",
            abstentionSensitivity.policyB.baselineAccuracy?.let { round6(it) } ?: "",
            abstentionSensitivity.policyB.eightBAccuracy?.let { round6(it) } ?: "",
            improvements.totalImprovements,
            regressions.totalRegressions,
            statisticalStatus,
            conclusion.statisticalStatus.name,
            alpha, analysisSeed, bootstrapIterations
        )
        return listOf(header, row).joinToString("\n") { line ->
            line.joinToString(",") { escCsv(it) }
        }
    }

    fun subgroupCsv(): String {
        val header = listOf(
            "dimension", "key", "support", "baselineAccuracy",
            "eightBAccuracy", "accuracyDifference",
            "netDiscordantImprovement", "testName",
            "mcnemarP", "mcnemarSignificant", "sufficient"
        )
        val rows = mutableListOf<List<Any?>>()
        for (s in categorySensitivity) {
            rows.add(subgroupRow("category", s))
        }
        for (s in platformSensitivity) {
            rows.add(subgroupRow("platform", s))
        }
        for (s in durationSensitivity) {
            rows.add(subgroupRow("duration", s))
        }
        for (s in evidenceSensitivity) {
            rows.add(subgroupRow("evidence", s))
        }
        return (listOf(header) + rows).joinToString("\n") { line ->
            line.joinToString(",") { escCsv(it) }
        }
    }

    // --------------------------------
    // PRIVATE JSON HELPERS
    // --------------------------------

    private fun testResultJson(r: PairedTestResult): JSONObject {
        val o = JSONObject()
        o.put("role", r.role.name)
        r.subgroupLabel?.let { o.put("subgroupLabel", it) }
        o.put("n", r.n)
        o.put("baselineCorrect", r.baselineCorrect)
        o.put("baselineWrong", r.baselineWrong)
        o.put("eightBCorrect", r.eightBCorrect)
        o.put("eightBWrong", r.eightBWrong)
        o.put("baselineOnlyCorrect", r.baselineOnlyCorrect)
        o.put("eightBOnlyCorrect", r.eightBOnlyCorrect)
        o.put("testName", r.testName)
        r.testStatistic?.let { o.put("testStatistic", round6(it)) }
        r.pValue?.let { o.put("pValue", round6(it)) }
        o.put("significanceThreshold", r.significanceThreshold)
        r.significant?.let { o.put("significant", it) }
        o.put("status", r.status.name)
        r.guardReason?.let { o.put("guardReason", it) }
        return o
    }

    private fun ciJson(ci: ConfidenceIntervalAnalysis): JSONObject {
        val o = JSONObject()
        o.put("sufficient", ci.sufficient)
        ci.guardReason?.let { o.put("guardReason", it) }
        ci.baselineAccuracy?.let { o.put("baselineAccuracy", metricJson(it)) }
        ci.eightBAccuracy?.let { o.put("eightBAccuracy", metricJson(it)) }
        ci.accuracyDifference?.let { o.put("accuracyDifference", metricJson(it)) }
        return o
    }

    private fun bootstrapJson(b: BootstrapAnalysis): JSONObject {
        val o = JSONObject()
        o.put("sufficient", b.sufficient)
        o.put("bootstrapIterations", b.bootstrapIterations)
        b.observedDifference?.let { o.put("observedDifference", round6(it)) }
        b.lowerBound?.let { o.put("lowerBound", round6(it)) }
        b.upperBound?.let { o.put("upperBound", round6(it)) }
        o.put("seed", b.seed)
        o.put("statisticalVersion", b.statisticalVersion)
        b.guardReason?.let { o.put("guardReason", it) }
        return o
    }

    private fun subgroupJson(items: List<SubgroupResult>): JSONArray {
        val arr = JSONArray()
        for (s in items) {
            val o = JSONObject()
            o.put("key", s.key)
            o.put("support", s.support)
            s.baselineAccuracy?.let { o.put("baselineAccuracy", round6(it)) }
            s.eightBAccuracy?.let { o.put("eightBAccuracy", round6(it)) }
            s.accuracyDifference?.let { o.put("accuracyDifference", round6(it)) }
            o.put("netDiscordantImprovement", s.netDiscordantImprovement)
            o.put("test", testResultJson(s.test))
            o.put("sufficient", s.sufficient)
            arr.put(o)
        }
        return arr
    }

    private fun abstentionJson(a: AbstentionSensitivity): JSONObject {
        val o = JSONObject()
        o.put("policyA", policyResultJson(a.policyA))
        o.put("policyB", policyResultJson(a.policyB))
        val c = JSONObject()
        a.policyC.baselineCoverage?.let { c.put("baselineCoverage", round6(it)) }
        a.policyC.eightBCoverage?.let { c.put("eightBCoverage", round6(it)) }
        a.policyC.baselineSelectiveAccuracy?.let { c.put("baselineSelectiveAccuracy", round6(it)) }
        a.policyC.eightBSelectiveAccuracy?.let { c.put("eightBSelectiveAccuracy", round6(it)) }
        a.policyC.baselineAccuracy?.let { c.put("baselineAccuracy", round6(it)) }
        a.policyC.eightBAccuracy?.let { c.put("eightBAccuracy", round6(it)) }
        c.put("n", a.policyC.n)
        c.put("sufficient", a.policyC.sufficient)
        o.put("policyC", c)
        return o
    }

    private fun policyResultJson(p: AbstentionSensitivity.PolicyResult): JSONObject {
        val o = JSONObject()
        o.put("label", p.label)
        p.baselineAccuracy?.let { o.put("baselineAccuracy", round6(it)) }
        p.eightBAccuracy?.let { o.put("eightBAccuracy", round6(it)) }
        p.accuracyDifference?.let { o.put("accuracyDifference", round6(it)) }
        o.put("n", p.n)
        o.put("sufficient", p.sufficient)
        return o
    }

    private fun confidenceSensJson(c: ConfidenceSensitivity): JSONObject {
        val o = JSONObject()
        o.put("sufficient", c.sufficient)
        val arr = JSONArray()
        for (band in c.bands) {
            val b = JSONObject()
            b.put("lowerBound", round6(band.lowerBound))
            b.put("upperBound", round6(band.upperBound))
            b.put("support", band.support)
            b.put("baselineCorrect", band.baselineCorrect)
            b.put("eightBCorrect", band.eightBCorrect)
            band.baselineAccuracy?.let { b.put("baselineAccuracy", round6(it)) }
            band.eightBAccuracy?.let { b.put("eightBAccuracy", round6(it)) }
            band.accuracyDifference?.let { b.put("accuracyDifference", round6(it)) }
            b.put("mcnemarSufficient", band.mcnemarSufficient)
            band.mcnemarP?.let { b.put("mcnemarP", round6(it)) }
            arr.put(b)
        }
        o.put("bands", arr)
        return o
    }

    private fun hcErrorJson(h: HighConfidenceError): JSONObject {
        val o = JSONObject()
        o.put("baselineHighConfidenceWrong", h.baselineHighConfidenceWrong)
        o.put("eightBHighConfidenceWrong", h.eightBHighConfidenceWrong)
        o.put("baselineHighConfidenceTotal", h.baselineHighConfidenceTotal)
        o.put("eightBHighConfidenceTotal", h.eightBHighConfidenceTotal)
        h.baselineHighConfidenceErrorRate?.let {
            o.put("baselineHighConfidenceErrorRate", round6(it))
        }
        h.eightBHighConfidenceErrorRate?.let {
            o.put("eightBHighConfidenceErrorRate", round6(it))
        }
        o.put("highConfidenceThreshold", h.highConfidenceThreshold)
        o.put("sufficient", h.sufficient)
        return o
    }

    private fun improvementJson(imp: ImprovementAnalysis): JSONObject {
        val o = JSONObject()
        o.put("totalImprovements", imp.totalImprovements)
        imp.improvementPercentage?.let { o.put("improvementPercentage", round6(it)) }
        o.put("byCategory", JSONObject(imp.byCategory))
        o.put("byPlatform", JSONObject(imp.byPlatform))
        o.put("byDuration", JSONObject(imp.byDuration))
        o.put("sufficient", imp.sufficient)
        return o
    }

    private fun regressionJson(reg: RegressionAnalysis): JSONObject {
        val o = JSONObject()
        o.put("totalRegressions", reg.totalRegressions)
        reg.regressionPercentage?.let { o.put("regressionPercentage", round6(it)) }
        o.put("byCategory", JSONObject(reg.byCategory))
        o.put("byPlatform", JSONObject(reg.byPlatform))
        o.put("byDuration", JSONObject(reg.byDuration))
        o.put("sufficient", reg.sufficient)
        return o
    }

    private fun ecJson(ec: ErrorConcentration): JSONObject {
        val o = JSONObject()
        o.put("improvements", ecReportJson(ec.improvements))
        o.put("regressions", ecReportJson(ec.regressions))
        return o
    }

    private fun ecReportJson(r: ErrorConcentration.ConcentrationReport): JSONObject {
        val o = JSONObject()
        o.put("total", r.total)
        r.topCategory?.let { o.put("topCategory", rankedJson(it)) }
        r.topPlatform?.let { o.put("topPlatform", rankedJson(it)) }
        r.topDuration?.let { o.put("topDuration", rankedJson(it)) }
        r.topEvidenceCondition?.let { o.put("topEvidenceCondition", rankedJson(it)) }
        o.put("categoryDistribution", JSONObject(r.categoryDistribution))
        o.put("platformDistribution", JSONObject(r.platformDistribution))
        o.put("durationDistribution", JSONObject(r.durationDistribution))
        o.put("evidenceConditionDistribution", JSONObject(r.evidenceConditionDistribution))
        return o
    }

    private fun rankedJson(r: ErrorConcentration.RankedContribution): JSONObject {
        val o = JSONObject()
        o.put("key", r.key)
        o.put("count", r.count)
        o.put("percentage", round6(r.percentage))
        o.put("description", r.description)
        return o
    }

    private fun metricJson(m: com.example.feedsense.analysis.evaluation.MetricValue): JSONObject {
        val o = JSONObject()
        o.put("state", m.state.name)
        m.value?.let { o.put("value", round6(it)) }
        o.put("numerator", m.numerator)
        o.put("denominator", m.denominator)
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

    // --------------------------------
    // PRIVATE HELPERS
    // --------------------------------

    private fun subgroupRow(
        dimension: String,
        s: SubgroupResult
    ): List<Any?> = listOf(
        dimension, s.key, s.support,
        s.baselineAccuracy?.let { round6(it) } ?: "",
        s.eightBAccuracy?.let { round6(it) } ?: "",
        s.accuracyDifference?.let { round6(it) } ?: "",
        s.netDiscordantImprovement,
        s.test.testName,
        s.test.pValue?.let { round6(it) } ?: "",
        s.test.significant ?: "",
        s.sufficient
    )

    private fun escCsv(v: Any?): String {
        if (v == null) return ""
        val s = v.toString()
        return if (s.contains(',') || s.contains('"') ||
            s.contains('\n')) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else s
    }

    private fun round6(d: Double): Double =
        kotlin.math.round(d * 1_000_000.0) / 1_000_000.0
}
