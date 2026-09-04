package com.example.feedsense.analysis.evaluation

import org.json.JSONArray
import org.json.JSONObject

// --------------------------------
// REPORT JSON SERIALIZER (Milestone 8A-3)
// --------------------------------
//
// Turns a frozen EvaluationReport into a JSON object so it can
// be persisted in evaluation_runs.reportJson and re-inspected /
// exported without recomputing against the live dataset.
//
// The serialization is lossless for every metric section,
// including the state flags and denominators of each MetricValue
// (so INSUFFICIENT_SAMPLE_SIZE / NOT_APPLICABLE / UNDEFINED are
// visible, never collapsed to a bare number).

object ReportJson {

    fun toJson(report: EvaluationReport): String {
        return toJsonObject(report).toString()
    }

    fun toJsonObject(report: EvaluationReport): JSONObject {

        val root = JSONObject()

        root.put("runId", report.runId)
        root.put("datasetVersion", report.datasetVersion ?: "")
        root.put("modelVersion", report.modelVersion ?: "")
        root.put("evaluationMethodVersion", report.evaluationMethodVersion)
        root.put("createdAt", report.createdAt.toString())

        val config = JSONObject()
        config.put("eligibleStatuses", JSONArray(report.config.eligibleStatuses.toList()))
        config.put("includeDisputed", report.config.includeDisputed)
        config.put("confidenceLevel", report.config.confidenceLevel)
        config.put("minimumSampleClass", report.config.minimumSampleClass)
        config.put("minimumSampleBinary", report.config.minimumSampleBinary)
        config.put("minimumSampleCalibration", report.config.minimumSampleCalibration)
        config.put("confidenceBucketWidth", report.config.confidenceBucketWidth)
        config.put(
            "durationTolerancesSeconds",
            JSONArray(report.config.durationTolerancesSeconds)
        )
        config.put(
            "evaluationDurationToleranceSeconds",
            report.config.evaluationDurationToleranceSeconds
        )
        config.put("description", report.config.description)
        root.put("config", config)

        root.put("totalItems", report.totalItems)
        root.put("eligibleItems", report.eligibleItems)
        root.put("excludedItems", report.excludedItems)

        val exclusions = JSONArray()
        report.exclusions.forEach {
            val e = JSONObject()
            e.put("evaluationItemId", it.evaluationItemId)
            e.put("reason", it.reason)
            exclusions.put(e)
        }
        root.put("exclusions", exclusions)

        root.put("categoryMetrics", report.categoryMetrics?.let { category(it) } ?: JSONObject())
        root.put("multiLabelMetrics", report.multiLabelMetrics?.let { multiLabel(it) } ?: JSONObject())
        root.put("platformMetrics", report.platformMetrics?.let { category(it) } ?: JSONObject())
        root.put("contentTypeMetrics", report.contentTypeMetrics?.let { category(it) } ?: JSONObject())
        root.put("durationMetrics", report.durationMetrics?.let { duration(it) } ?: JSONObject())
        root.put("skipMetrics", report.skipMetrics?.let { skip(it) } ?: JSONObject())
        root.put("interactionMetrics", report.interactionMetrics?.let { interaction(it) } ?: JSONObject())
        root.put("topicMetrics", report.topicMetrics?.let { topicTone(it) } ?: JSONObject())
        root.put("toneMetrics", report.toneMetrics?.let { topicTone(it) } ?: JSONObject())
        root.put("calibrationMetrics", report.calibrationMetrics?.let { calibration(it) } ?: JSONObject())
        root.put("datasetBalance", balance(report.datasetBalance))

        root.put("verdictDistribution", JSONObject(report.verdictDistribution))
        root.put("sampleSummary", sampleSummary(report.sampleSummary))

        val errors = JSONArray()
        report.errorRecords.forEach {
            val e = JSONObject()
            e.put("evaluationItemId", it.evaluationItemId)
            e.put("feedItemId", it.feedItemId ?: "")
            e.put("predictedCategory", it.predictedCategory ?: "")
            e.put("truthCategory", it.truthCategory ?: "")
            e.put("confidence", it.confidence ?: JSONObject.NULL)
            e.put("modelVersion", it.modelVersion ?: "")
            e.put("datasetVersion", it.datasetVersion ?: "")
            e.put("verdict", it.verdict)
            e.put("durationErrorSeconds", it.durationErrorSeconds ?: JSONObject.NULL)
            e.put("skippedAgreement", it.skippedAgreement ?: JSONObject.NULL)
            e.put("topicAgreement", it.topicAgreement ?: JSONObject.NULL)
            e.put("toneAgreement", it.toneAgreement ?: JSONObject.NULL)
            e.put("platformAgreement", it.platformAgreement ?: JSONObject.NULL)
            e.put("contentTypeAgreement", it.contentTypeAgreement ?: JSONObject.NULL)
            e.put("interactionSignalsDisagreement", it.interactionSignalsDisagreement ?: JSONObject.NULL)
            errors.put(e)
        }
        root.put("errorRecords", errors)

        return root
    }

    // --------------------------------
    // SECTION SERIALIZERS
    // --------------------------------

    private fun metricValue(mv: MetricValue): JSONObject {
        val o = JSONObject()
        o.put("state", mv.state.name)
        o.put("value", mv.value?.let { Math.round(it * 10000.0) / 10000.0 } ?: JSONObject.NULL)
        o.put("numerator", mv.numerator)
        o.put("denominator", mv.denominator)
        mv.confidenceInterval?.let {
            val ci = JSONObject()
            ci.put("lower", it.lower)
            ci.put("upper", it.upper)
            ci.put("confidenceLevel", it.confidenceLevel)
            o.put("confidenceInterval", ci)
        }
        mv.note?.let { o.put("note", it) }
        return o
    }

    private fun category(m: CategoryMetrics): JSONObject {
        val o = JSONObject()
        o.put("sampleCount", m.sampleCount)

        val perClass = JSONArray()
        m.perClass.forEach {
            val c = JSONObject()
            c.put("label", it.label)
            c.put("tp", it.tp)
            c.put("fp", it.fp)
            c.put("fn", it.fn)
            c.put("support", it.support)
            c.put("precision", metricValue(it.precision))
            c.put("recall", metricValue(it.recall))
            c.put("f1", it.f1?.let { mv -> metricValue(mv) } ?: JSONObject())
            perClass.put(c)
        }
        o.put("perClass", perClass)

        val agg = JSONObject()
        agg.put("macroPrecision", metricValue(m.aggregates.macroPrecision))
        agg.put("macroRecall", metricValue(m.aggregates.macroRecall))
        agg.put("macroF1", metricValue(m.aggregates.macroF1))
        agg.put("microAccuracy", metricValue(m.aggregates.microAccuracy))
        agg.put("weightedPrecision", metricValue(m.aggregates.weightedPrecision))
        agg.put("weightedRecall", metricValue(m.aggregates.weightedRecall))
        agg.put("weightedF1", metricValue(m.aggregates.weightedF1))
        o.put("aggregates", agg)

        val matrix = JSONObject()
        matrix.put("labels", JSONArray(m.confusionMatrix.labels))
        val cells = JSONArray()
        m.confusionMatrix.labels.forEach { row ->
            m.confusionMatrix.labels.forEach { col ->
                val cell = JSONObject()
                cell.put("predicted", row)
                cell.put("truth", col)
                cell.put("count", m.confusionMatrix.countOf(row, col))
                cells.put(cell)
            }
        }
        matrix.put("cells", cells)
        o.put("confusionMatrix", matrix)
        return o
    }

    private fun multiLabel(m: MultiLabelMetrics): JSONObject {
        val o = JSONObject()
        o.put("sampleCount", m.sampleCount)
        o.put("exactMatch", metricValue(m.exactMatch))
        o.put("hammingLoss", metricValue(m.hammingLoss))
        o.put("microPrecision", metricValue(m.microPrecision))
        o.put("microRecall", metricValue(m.microRecall))
        o.put("microF1", metricValue(m.microF1))
        o.put("macroPrecision", metricValue(m.macroPrecision))
        o.put("macroRecall", metricValue(m.macroRecall))
        o.put("macroF1", metricValue(m.macroF1))
        return o
    }

    private fun duration(m: DurationMetrics): JSONObject {
        val o = JSONObject()
        o.put("sampleCount", m.sampleCount)
        o.put("meanAbsoluteError", metricValue(m.meanAbsoluteError))
        o.put("medianAbsError", metricValue(m.medianAbsError))
        o.put("rmse", metricValue(m.rmse))
        o.put("bias", metricValue(m.bias))
        val tol = JSONObject()
        m.withinTolerances.forEach { (sec, mv) -> tol.put("within_${sec}s", metricValue(mv)) }
        o.put("withinTolerances", tol)
        val percentiles = JSONObject()
        m.absoluteErrorPercentiles.forEach { (p, v) -> percentiles.put("p$p", v) }
        o.put("absoluteErrorPercentiles", percentiles)
        o.put("signedErrorsSeconds", JSONArray(m.signedErrorsSeconds))
        return o
    }

    private fun skip(m: SkipMetrics): JSONObject {
        val o = JSONObject()
        o.put("decided", m.decided)
        o.put("unknownTruth", m.unknownTruth)
        o.put("total", m.total)
        o.put("binary", binary(m.binary))
        return o
    }

    private fun binary(b: BinaryMetrics): JSONObject {
        val o = JSONObject()
        o.put("tp", b.tp)
        o.put("fp", b.fp)
        o.put("fn", b.fn)
        o.put("tn", b.tn)
        o.put("accuracy", b.accuracy?.let { metricValue(it) } ?: JSONObject())
        o.put("precision", b.precision?.let { metricValue(it) } ?: JSONObject())
        o.put("recall", b.recall?.let { metricValue(it) } ?: JSONObject())
        o.put("specificity", b.specificity?.let { metricValue(it) } ?: JSONObject())
        o.put("f1", b.f1?.let { metricValue(it) } ?: JSONObject())
        o.put("mcc", b.mcc ?: JSONObject.NULL)
        return o
    }

    private fun interaction(m: InteractionMetrics): JSONObject {
        val o = JSONObject()
        o.put("sampleCount", m.sampleCount)
        val signals = JSONArray()
        m.signals.forEach {
            val s = JSONObject()
            s.put("signal", it.signal)
            s.put("decided", it.decided)
            s.put("unknown", it.unknown)
            s.put("binary", binary(it.binary))
            signals.put(s)
        }
        o.put("signals", signals)
        return o
    }

    private fun topicTone(m: TopicToneMetrics): JSONObject {
        val o = JSONObject()
        o.put("dimension", m.dimension)
        o.put("decided", m.decided)
        o.put("unknown", m.unknown)
        o.put("total", m.total)
        o.put("agreement", metricValue(m.agreement))
        o.put("limitation", m.limitation)
        return o
    }

    private fun calibration(m: CalibrationMetrics): JSONObject {
        val o = JSONObject()
        o.put("sampleCount", m.sampleCount)
        o.put("excludedAmbiguous", m.excludedAmbiguous)
        o.put("bucketWidth", m.bucketWidth)
        o.put("ece", metricValue(m.ece))
        val buckets = JSONArray()
        m.buckets.forEach {
            val b = JSONObject()
            b.put("lower", it.lower)
            b.put("upper", it.upper)
            b.put("sampleCount", it.sampleCount)
            b.put("meanConfidence", it.meanConfidence ?: JSONObject.NULL)
            b.put("accuracy", metricValue(it.accuracy))
            b.put("delta", it.delta ?: JSONObject.NULL)
            buckets.put(b)
        }
        o.put("buckets", buckets)
        return o
    }

    private fun balance(r: DatasetBalanceReport): JSONObject {
        fun list(pairs: List<Pair<String, Int>>): JSONArray {
            val a = JSONArray()
            pairs.forEach { (k, v) ->
                val e = JSONObject()
                e.put("key", k)
                e.put("count", v)
                a.put(e)
            }
            return a
        }
        val o = JSONObject()
        o.put("byCategory", list(r.byCategory))
        o.put("byPlatform", list(r.byPlatform))
        o.put("byContentType", list(r.byContentType))
        o.put("bySource", list(r.bySource))
        o.put("byModelVersion", list(r.byModelVersion))
        o.put("byDurationBucket", list(r.byDurationBucket))
        o.put("totalItems", r.totalItems)
        o.put("notes", JSONArray(r.notes))
        return o
    }

    private fun sampleSummary(s: EvaluationReport.SampleSummary): JSONObject {
        val o = JSONObject()
        o.put("corrected", s.corrected)
        o.put("incorrect", s.incorrect)
        o.put("partial", s.partial)
        o.put("unknown", s.unknown)
        o.put("uncomparable", s.uncomparable)
        o.put("reviewed", s.reviewed)
        o.put("disputed", s.disputed)
        return o
    }
}

/*
 * Serializes an EvaluatorConfig to JSON for the
 * evaluation_runs.configJson column.
 */
object ConfigJson {

    fun toJson(config: EvaluatorConfig): String {
        val o = JSONObject()
        o.put("eligibleStatuses", JSONArray(config.eligibleStatuses.toList()))
        o.put("includeDisputed", config.includeDisputed)
        o.put("confidenceLevel", config.confidenceLevel)
        o.put("minimumSampleClass", config.minimumSampleClass)
        o.put("minimumSampleBinary", config.minimumSampleBinary)
        o.put("minimumSampleCalibration", config.minimumSampleCalibration)
        o.put("confidenceBucketWidth", config.confidenceBucketWidth)
        o.put(
            "durationTolerancesSeconds",
            JSONArray(config.durationTolerancesSeconds)
        )
        o.put("evaluationDurationToleranceSeconds", config.evaluationDurationToleranceSeconds)
        o.put("description", config.description)
        o.put("schemaVersion", EvaluatorConfig.SCHEMA_VERSION)
        return o.toString()
    }
}