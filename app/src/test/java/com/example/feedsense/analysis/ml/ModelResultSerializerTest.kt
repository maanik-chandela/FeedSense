package com.example.feedsense.analysis.ml

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-14.
 *
 * Deterministic, privacy-safe serialization: identical result
 * => identical text; only research-safe metadata is ever
 * emitted (sections 32, 33, 46).
 */
class ModelResultSerializerTest {

    private fun successResult(): ModelInferenceResult {
        return ModelInferenceResult(
            modelId = "feedsense-category-model",
            modelVersion = "ml-v1",
            modelChecksum = "abc123",
            status = InferenceStatus.SUCCESS,
            rankedPredictions = listOf(
                RankedPrediction("sports", 0.8200001),
                RankedPrediction("entertainment", 0.11000009)
            ),
            preprocessVersion = "preprocess-v1",
            privacyVersion = "privacy-processing-v1",
            inputSpecVersion = "input-v1",
            outputSpecVersion = "output-v1",
            evidenceId = "frame-1",
            experimentId = "exp-A",
            timestampMs = 12345L,
            preprocessingLatencyMs = 11,
            inferenceLatencyMs = 22,
            rawModelMetadata = mapOf("fake.runtime" to "fixed-scores")
        )
    }

    @Test
    fun `identical results serialize to identical json`() {
        val a = successResult()
        val b = successResult()
        val textA = ModelResultSerializer.toJsonText(a)
        val textB = ModelResultSerializer.toJsonText(b)
        assertTrue(textA == textB)
    }

    @Test
    fun `json is valid and contains top-k plus versions`() {
        val text = ModelResultSerializer.toJsonText(successResult())
        assertTrue(text.startsWith("{"))
        assertTrue(text.contains("\"modelVersion\":\"ml-v1\""))
        assertTrue(text.contains("\"topK\":\"sports:0.82|entertainment:0.11\""))
        assertTrue(text.contains("\"preprocessVersion\":\"preprocess-v1\""))
        assertTrue(text.contains("\"privacyVersion\":\"privacy-processing-v1\""))
    }

    @Test
    fun `confidences are rounded to a fixed precision`() {
        val text = ModelResultSerializer.toJsonText(successResult())
        // 0.8200001 must serialize as 0.82, not the noisy binary form
        assertTrue(text.contains("sports:0.82"))
        assertFalse(text.contains("0.8200001"))
    }

    @Test
    fun `serialized output never carries sensitive content keys`() {
        val map = ModelResultSerializer.toMetadataMap(successResult())
        val keys = map.keys
        val forbidden = listOf(
            "pixels", "rawPixels", "frame", "frameBytes", "inputBytes",
            "bytePayload", "quantizedBytes", "floats", "ocrText",
            "visibleText", "privateContent", "screenshot"
        )
        for (key in forbidden) {
            assertFalse("forbidden key present: $key", keys.contains(key))
            assertFalse(
                "forbidden key embedded: $key",
                keys.any { it.contains(key, ignoreCase = true) }
            )
        }
        assertTrue(keys.contains("modelId"))
        assertTrue(keys.contains("topK"))
    }

    @Test
    fun `failure metadata is serialized when present`() {
        val failed = successResult().copy(
            status = InferenceStatus.RESOURCE_LIMIT,
            rankedPredictions = emptyList(),
            failure = InferenceFailure(
                status = InferenceStatus.RESOURCE_LIMIT,
                phase = "infer",
                message = "OUT_OF_MEMORY"
            )
        )
        val map = ModelResultSerializer.toMetadataMap(failed)
        assertTrue(map["status"] == "RESOURCE_LIMIT")
        assertTrue(map["failurePhase"] == "infer")
        assertTrue(map["failureMessage"] == "OUT_OF_MEMORY")
    }

    @Test
    fun `keys are sorted for stable text output`() {
        val text = ModelResultSerializer.toJsonText(successResult())
        // extract key order directly
        val keys = text
            .trim()
            .removePrefix("{")
            .removeSuffix("}")
            .split(",")
            .map { it.substringBefore(":").trim().removeSurrounding("\"") }
        assertTrue(
            "keys must be sorted, got $keys",
            keys == keys.sorted()
        )
    }
}