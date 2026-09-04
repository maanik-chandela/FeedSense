package com.example.feedsense.analysis.evaluation

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

// --------------------------------
// ERROR ANALYSIS REPORT (Milestone 8A-5)
// --------------------------------
//
// The frozen, serializable output of the error-analysis layer.
// It is pure logic over the already-loaded evaluation cohort
// (like the 8A-3 EvaluationReport and the 8A-4 DatasetManifest)
// and is intended to be stored alongside the 8A-3 report inside
// evaluation_runs.reportJson when the consumer serializes it.
//
// It contains:
//   - per-capability and per-error-type roll-ups
//   - an ordered error queue (severity-descending) awaiting
//     human diagnosis
//   - per-capability confusion matrices
//   - a confidence-vs-correctness analysis
//   - conservative, sample-gated patterns
//   - an explicit "no real data" honest guard so an empty or
//     degenerate run never produces fabricated statistical
//     conclusions.

data class ErrorAnalysisReport(
    val runId: String,
    val datasetVersion: String?,
    val modelVersion: String?,
    val createdAt: LocalDateTime,
    val analysisMethodVersion: String,
    val totalUnits: Int,
    val unitsWithErrors: Int,
    val totalErrors: Int,
    val realDataAvailable: Boolean,
    val perCapability: List<CapabilitySummary>,
    val perErrorType: List<ErrorTypeSummary>,
    val errorQueue: List<QueueEntry>,
    val confusionMatrices: Map<String, ErrorConfusionMatrix.Matrix>,
    val confidence: ConfidenceAnalysis.Result?,
    val patterns: List<Pattern>,
    val finding: String,
    val severityDistribution: Map<String, Int>,
    val qualityFlags: List<QualityFlag>
) {

    data class CapabilitySummary(
        val capability: String,
        val errorCount: Int,
        val affectedUnits: Int,
        val errorTypes: List<String>
    )

    data class ErrorTypeSummary(
        val errorType: String,
        val count: Int,
        val affectedUnits: Int,
        val dominantSeverity: String,
        val researchImpactCount: Int,
        val overconfidentCount: Int
    )

    // A single entry in the error queue, ready for a human
    // analyst to review and (manually) escalate HYPOTHESIS ->
    // CONFIRMED. 8A-5 always leaves diagnosis status AUTO.
    data class QueueEntry(
        val sequence: Int,
        val evaluationItemId: String,
        val capability: String,
        val errorType: String,
        val severity: String,
        val sourceEvidence: String,
        val rootCause: String?,
        val researchImpact: Boolean,
        val diagnosisStatus: String = DiagnosisStatus.AUTO,
        val details: Map<String, String> = emptyMap()
    )

    object DiagnosisStatus {
        const val AUTO = "AUTO_PENDING_REVIEW"
        const val HUMAN_REVIEWED = "HUMAN_REVIEWED"
        const val CONFIRMED = "CONFIRMED"
    }

    data class Pattern(
        val id: String,
        val title: String,
        val description: String,
        val evidenceLevel: String,
        val capability: String?,
        val severity: String,
        val sampleCount: Int,
        val researchImpact: Boolean
    )

    data class QualityFlag(
        val flag: String,
        val severity: FlagSeverity,
        val message: String
    )

    enum class FlagSeverity {
        INFO,
        WARNING,
        CRITICAL
    }

    // --------------------------------
    // JSON SERIALIZATION
    // --------------------------------

    fun toJson(): String {
        return toJsonObject().toString(2)
    }

    fun toJsonObject(): JSONObject {
        val root = JSONObject()
        root.put("runId", runId)
        root.put("datasetVersion", datasetVersion ?: "")
        root.put("modelVersion", modelVersion ?: "")
        root.put("createdAt", createdAt.toString())
        root.put("analysisMethodVersion", analysisMethodVersion)
        root.put("totalUnits", totalUnits)
        root.put("unitsWithErrors", unitsWithErrors)
        root.put("totalErrors", totalErrors)
        root.put("realDataAvailable", realDataAvailable)
        root.put("finding", finding)

        root.put("perCapability", JSONArray(
            perCapability.map {
                val o = JSONObject()
                o.put("capability", it.capability)
                o.put("errorCount", it.errorCount)
                o.put("affectedUnits", it.affectedUnits)
                o.put("errorTypes", JSONArray(it.errorTypes))
                o
            }
        ))

        root.put("perErrorType", JSONArray(
            perErrorType.map {
                val o = JSONObject()
                o.put("errorType", it.errorType)
                o.put("count", it.count)
                o.put("affectedUnits", it.affectedUnits)
                o.put("dominantSeverity", it.dominantSeverity)
                o.put("researchImpactCount", it.researchImpactCount)
                o.put("overconfidentCount", it.overconfidentCount)
                o
            }
        ))

        root.put("errorQueue", JSONArray(
            errorQueue.map {
                val o = JSONObject()
                o.put("sequence", it.sequence)
                o.put("evaluationItemId", it.evaluationItemId)
                o.put("capability", it.capability)
                o.put("errorType", it.errorType)
                o.put("severity", it.severity)
                o.put("sourceEvidence", it.sourceEvidence)
                o.put("rootCause", it.rootCause ?: "")
                o.put("researchImpact", it.researchImpact)
                o.put("diagnosisStatus", it.diagnosisStatus)
                if (it.details.isNotEmpty()) {
                    val d = JSONObject()
                    it.details.forEach { (k, v) -> d.put(k, v) }
                    o.put("details", d)
                }
                o
            }
        ))

        val matrices = JSONObject()
        confusionMatrices.forEach { (capability, matrix) ->
            val m = JSONObject()
            m.put("labels", JSONArray(matrix.labels))
            val cells = JSONArray()
            matrix.labels.forEach { row ->
                matrix.labels.forEach { col ->
                    val c = JSONObject()
                    c.put("predicted", row)
                    c.put("truth", col)
                    c.put("count", matrix.count(row, col))
                    cells.put(c)
                }
            }
            m.put("cells", cells)
            matrices.put(capability, m)
        }
        root.put("confusionMatrices", matrices)

        root.put("confidence", confidence?.let { confidenceJson(it) } ?: JSONObject())

        root.put("patterns", JSONArray(
            patterns.map {
                val o = JSONObject()
                o.put("id", it.id)
                o.put("title", it.title)
                o.put("description", it.description)
                o.put("evidenceLevel", it.evidenceLevel)
                o.put("capability", it.capability ?: "")
                o.put("severity", it.severity)
                o.put("sampleCount", it.sampleCount)
                o.put("researchImpact", it.researchImpact)
                o
            }
        ))

        root.put("severityDistribution", JSONObject(severityDistribution))

        root.put("qualityFlags", JSONArray(
            qualityFlags.map {
                val o = JSONObject()
                o.put("flag", it.flag)
                o.put("severity", it.severity.name)
                o.put("message", it.message)
                o
            }
        ))

        return root
    }

    private fun confidenceJson(r: ConfidenceAnalysis.Result): JSONObject {
        val o = JSONObject()
        o.put("bucketWidth", r.bucketWidth)
        o.put("confidentErrorCount", r.confidentErrorCount)
        o.put("confidentCorrectCount", r.confidentCorrectCount)
        o.put("lowConfidenceCorrectCount", r.lowConfidenceCorrectCount)
        r.confidentErrorRate?.let { o.put("confidentErrorRate", it) }
        val buckets = JSONArray()
        r.buckets.forEach {
            val b = JSONObject()
            b.put("lower", it.lower)
            b.put("upper", it.upper)
            b.put("sampleCount", it.sampleCount)
            b.put("correctCount", it.correctCount)
            b.put("incorrectCount", it.incorrectCount)
            it.accuracy?.let { a -> b.put("accuracy", a) }
            buckets.put(b)
        }
        o.put("buckets", buckets)
        return o
    }

    companion object {
        const val METHOD_VERSION = "1.0.0"

        const val FINDING_INSUFFICIENT_DATA =
            "INSUFFICIENT_REAL_DATA_FOR_STATISTICAL_CONCLUSIONS"
    }
}
