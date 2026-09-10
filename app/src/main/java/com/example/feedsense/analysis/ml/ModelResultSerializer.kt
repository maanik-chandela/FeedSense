package com.example.feedsense.analysis.ml

// --------------------------------
// MODEL RESULT SERIALIZER (8B-14)
// --------------------------------
//
// Deterministic, privacy-safe serialization of ML inference
// results.
//
// PRIVACY: only research-safe metadata is serialized. The
// serializer has no access to - and deliberately ignores -
// raw screenshots, OCR text, private content, or model
// input image bytes (8B-14 sections 32, 46). It serializes
// the metadata that already lives on a ModelInferenceResult,
// which by construction never carries that content.
//
// DETERMINISM: identical results serialize to identical
// text. Keys are emitted in sorted order and confidences
// are rounded to a fixed precision, so the text is stable
// across runs (section 33). JSON text is built manually (in
// sorted key order) rather than relying on JSONObject's
// map iteration order, so output is byte-stable.

object ModelResultSerializer {

    private const val CONFIDENCE_DECIMALS = 6

    /*
     * Safe metadata map view of a result.
     */
    fun toMetadataMap(
        result: ModelInferenceResult
    ): Map<String, String> {
        val map = linkedMapOf<String, String>()
        map["modelId"] = result.modelId
        map["modelVersion"] = result.modelVersion
        result.modelChecksum?.let { map["modelChecksum"] = it }
        map["status"] = result.status.label
        map["primaryCategory"] = result.primaryCategory ?: ""
        result.primaryConfidence?.let {
            map["primaryConfidence"] = formatConfidence(it)
        }
        result.preprocessVersion?.let { map["preprocessVersion"] = it }
        result.privacyVersion?.let { map["privacyVersion"] = it }
        result.inputSpecVersion?.let { map["inputSpecVersion"] = it }
        result.outputSpecVersion?.let { map["outputSpecVersion"] = it }
        result.evidenceId?.let { map["evidenceId"] = it }
        result.experimentId?.let { map["experimentId"] = it }
        map["timestampMs"] = result.timestampMs.toString()
        map["preprocessingLatencyMs"] = result.preprocessingLatencyMs.toString()
        map["inferenceLatencyMs"] = result.inferenceLatencyMs.toString()
        map["totalLatencyMs"] = result.totalLatencyMs.toString()
        map["topK"] = result.rankedPredictions
            .joinToString(separator = "|") { entry ->
                "${entry.category}:${formatConfidence(entry.confidence)}"
            }
        result.failure?.let {
            map["failureStatus"] = it.status.label
            map["failurePhase"] = it.phase
            map["failureMessage"] = it.message
        }
        return map
    }

    /*
     * Deterministic single-line JSON text of the safe
     * metadata only. Keys are sorted, confidences are
     * fixed-precision, and the escaping is minimal and
     * deterministic.
     */
    fun toJsonText(
        result: ModelInferenceResult
    ): String {
        return toMetadataMap(result)
            .entries
            .sortedBy { it.key }
            .joinToString(
                prefix = "{",
                postfix = "}",
                separator = ","
            ) { (key, value) ->
                "\"${escape(key)}\":\"${escape(value)}\""
            }
    }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
    }

    /*
     * Rounds to a fixed precision so 0.30000000000000004
     * never leaks into serialized output.
     */
    private fun formatConfidence(value: Double): String {
        val factor = Math.pow(10.0, CONFIDENCE_DECIMALS.toDouble())
        return (Math.round(value * factor) / factor).toString()
    }
}