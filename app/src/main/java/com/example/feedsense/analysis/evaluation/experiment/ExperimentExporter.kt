package com.example.feedsense.analysis.evaluation.experiment

import org.json.JSONArray
import org.json.JSONObject

/**
 * Milestone 8B-9.
 *
 * Privacy-safe JSON / CSV export of an experiment summary.
 *
 * Only COUNTS, version metadata, structured outcome labels, and
 * traceable-but-opaque identifiers are exported. Raw content is
 * NEVER written: no screenshots, OCR text, captions, messages,
 * personal names, notification contents, private URLs, or personal
 * identifiers. This matches the privacy guarantees of the rest of
 * the evaluation layer.
 */
object ExperimentExporter {

    /**
     * Full experiment summary as a JSON object (counts + metadata
     * only). The embedded comparative body reuses the 8B-8
     * [com.example.feedsense.analysis.evaluation.comparative.ComparativeReport]
     * serialization, so the metrics/matrices/conclusion are
     * lossless and deterministic.
     */
    fun toJsonObject(summary: ExperimentSummary): JSONObject {
        val root = JSONObject()

        root.put("experimentId", summary.definition.experimentId)
        root.put("experimentName", summary.definition.name)
        root.put("experimentVersion", summary.definition.experimentVersion)
        root.put("datasetMode", summary.definition.datasetMode.label)
        root.put("maxItems", summary.definition.maxItems)
        root.put("createdAt", summary.definition.createdAt.toString())
        root.put("snapshotId", summary.snapshot.snapshotId)
        root.put("idempotencySignature",
            summary.snapshot.idempotencySignature)
        root.put("baselineModelVersion",
            summary.definition.baselineModelVersion ?: "")
        root.put("evidenceAwareModelVersion",
            summary.definition.evidenceAwareModelVersion ?: "")
        root.put("decisionVersion",
            summary.definition.decisionVersion ?: "")

        root.put("selection", selectionJson(summary.selection))
        root.put("leakage", leakageJson(summary.leakage))
        root.put("datasetQuality",
            qualityJson(summary.datasetQuality))
        root.put("outcome", outcomeJson(summary.outcome))
        root.put("mcnemar", mcnemarJson(summary.mcnemar))
        root.put("realDataStatus", summary.realDataStatus.label)
        root.put("baselineSafety", safetyJson(summary.baselineSafety))
        root.put("comparative", summary.comparativeReport.toJsonObject())

        return root
    }

    fun toJson(summary: ExperimentSummary): String =
        toJsonObject(summary).toString(2)

    /**
     * Per-observation outcome table as CSV. One row per paired
     * observation: opaque evaluation item id, feed item id,
     * eligibility, and five-way outcome. No raw content.
     * Returns a header + rows.
     */
    fun outcomesCsv(summary: ExperimentSummary): String {
        val header = listOf(
            "evaluationItemId", "feedItemId", "eligibility", "outcome"
        )
        val rows = summary.selection.pairs
            .sortedBy { it.item.id }
            .map { p ->
                val r = PairedEvaluationResult.of(p)
                listOf(
                    p.item.id,
                    p.item.feedItemId ?: "",
                    r.eligibility.label,
                    r.outcome.label
                )
            }
        return (listOf(header) + rows)
            .joinToString("\n") { line ->
                line.joinToString(",") { cell -> esc(cell) }
            }
    }

    private fun selectionJson(
        s: DatasetSelection.SelectionResult
    ): JSONObject {
        val o = JSONObject()
        o.put("paired", s.pairs.size)
        o.put("candidateItems", s.candidateItems.size)
        o.put("excludedNoTruth", s.excludedNoTruth.size)
        o.put("excludedNoBaseline", s.excludedNoBaseline.size)
        o.put("excludedNoEvidenceAware", s.excludedNoEvidenceAware.size)
        o.put("excludedCapCount", s.excludedCapCount)
        o.put("selectedItemIds",
            JSONArray(s.selectedItemIds.sorted()))
        return o
    }

    private fun leakageJson(l: LeakageGuard.LeakageReport): JSONObject {
        val o = JSONObject()
        o.put("checked", l.checked)
        o.put("checkedAgainstVersion", l.checkedAgainstVersion ?: "")
        o.put("leaked", l.leaked)
        o.put("overlapItemIds", JSONArray(l.overlapItemIds.sorted()))
        o.put("note", l.note)
        return o
    }

    private fun qualityJson(q: DatasetQualityReport): JSONObject {
        val o = JSONObject()
        o.put("candidateItems", q.candidateItems)
        o.put("paired", q.paired)
        o.put("eligibleForAccuracy", q.eligibleForAccuracy)
        o.put("excludedFromAccuracy", q.excludedFromAccuracy)
        o.put("distinctFeedItems", q.distinctFeedItems)
        o.put("eligibilityRatio",
            q.eligibleRatio?.let { round6(it) } ?: JSONObject.NULL)
        o.put("truthAmbiguityCounts", JSONObject(q.truthAmbiguityCounts))
        o.put("contentByPlatform", JSONObject(q.contentByPlatform))
        o.put("contentByType", JSONObject(q.contentByType))
        o.put("durationBucketCounts", JSONObject(q.durationBucketCounts))
        o.put("note", q.note)
        return o
    }

    private fun outcomeJson(o: ExperimentAnalyzer.OutcomeTally): JSONObject {
        val r = JSONObject()
        r.put("paired", o.paired)
        r.put("eligible", o.eligible)
        r.put("bothCorrect", o.bothCorrect)
        r.put("baselineOnlyCorrect", o.baselineOnlyCorrect)
        r.put("evidenceAwareOnlyCorrect", o.evidenceAwareOnlyCorrect)
        r.put("bothWrong", o.bothWrong)
        r.put("incomplete", o.incomplete)
        r.put("discordantPairs", o.discordantPairs)
        r.put("evidenceAwareImprovements", o.evidenceAwareImprovements)
        r.put("evidenceAwareRegressions", o.evidenceAwareRegressions)
        return r
    }

    private fun mcnemarJson(m: ExperimentAnalyzer.McNemarReady): JSONObject {
        val o = JSONObject()
        o.put("baselineCorrect", m.baselineCorrect)
        o.put("evidenceAwareCorrect", m.evidenceAwareCorrect)
        o.put("baselineOnlyCorrect", m.baselineOnlyCorrect)
        o.put("evidenceAwareOnlyCorrect", m.evidenceAwareOnlyCorrect)
        o.put("discordantPairs", m.discordantPairs)
        o.put("sufficient", m.sufficient)
        m.pValue?.let { o.put("pValue", round6(it)) }
        m.guardReason?.let { o.put("guardReason", it) }
        o.put("direction", m.direction)
        return o
    }

    private fun safetyJson(s: ExperimentSummary.BaselineSafetyStatement): JSONObject {
        val o = JSONObject()
        o.put("baselineUnmodified", s.baselineUnmodified)
        o.put("feedItemsUnmodified", s.feedItemsUnmodified)
        o.put("sessionsUnmodified", s.sessionsUnmodified)
        o.put("predictionsUnmodified", s.predictionsUnmodified)
        o.put("evaluationRecordsUnmodified", s.evaluationRecordsUnmodified)
        o.put("noPipelineChanges", s.noPipelineChanges)
        o.put("observationalOnly", s.observationalOnly)
        o.put("safe", s.safe)
        o.put("note", s.note)
        return o
    }

    private fun round6(d: Double): Double =
        kotlin.math.round(d * 1_000_000.0) / 1_000_000.0

    private fun esc(v: Any?): String {
        if (v == null) return ""
        val s = v.toString()
        return if (s.contains(',') || s.contains('"') ||
            s.contains('\n')) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else s
    }
}
