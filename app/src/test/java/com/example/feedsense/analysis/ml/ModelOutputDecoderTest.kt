package com.example.feedsense.analysis.ml

import com.example.feedsense.analysis.ml.runtime.AdapterRawModelOutput
import com.example.feedsense.analysis.ml.runtime.InferenceExecutionStatus
import com.example.feedsense.analysis.ml.runtime.OutputTensorMetadata
import com.example.feedsense.analysis.ml.runtime.RuntimeFailureFactory
import com.example.feedsense.analysis.ml.repro.ArtifactAvailability
import com.example.feedsense.analysis.ml.repro.ReproArtifactFormat
import com.example.feedsense.analysis.ml.repro.ReproArtifactIdentity
import com.example.feedsense.analysis.ml.repro.ReproRuntimeIdentity
import com.example.feedsense.analysis.ml.repro.ReproRuntimeName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelOutputDecoderTest {

    private val testArtifact = ReproArtifactIdentity(
        artifactId = "test-artifact",
        format = ReproArtifactFormat.TFLITE,
        modelId = "test-model",
        artifactVersion = "test-v1",
        availability = ArtifactAvailability.AVAILABLE,
        sha256 = "0".repeat(64)
    )

    private val testRuntime = ReproRuntimeIdentity(
        runtimeName = ReproRuntimeName.LITERT,
        runtimeVersion = "2.16.1",
        modelFormat = ReproArtifactFormat.TFLITE
    )

    private val sportsConfig = OutputInterpretationConfig(
        interpretationVersion = "interp-v1",
        labels = listOf("sports", "entertainment", "education"),
        outputSemantics = OutputSemantics.RAW_SCORES,
        topK = 3
    )

    private fun successOutput(values: FloatArray): AdapterRawModelOutput {
        return AdapterRawModelOutput(
            artifactIdentity = testArtifact,
            runtimeIdentity = testRuntime,
            preprocessingVersion = "v1",
            inputSignature = "224x224x3:FLOAT32",
            outputTensors = listOf(
                OutputTensorMetadata(
                    tensorIndex = 0,
                    shape = listOf(1, values.size),
                    datatype = "FLOAT32",
                    valueRangeMin = values.min().toDouble(),
                    valueRangeMax = values.max().toDouble()
                )
            ),
            rawOutputValues = mapOf(0 to values.copyOf()),
            executionStatus = InferenceExecutionStatus.SUCCESS,
            inferenceDurationMs = 42L
        )
    }

    @Test
    fun `decode maps tensor to labels deterministically`() {
        val raw = successOutput(floatArrayOf(0.9f, 0.05f, 0.05f))
        val result = ModelOutputDecoder.decode(raw, sportsConfig)
        assertTrue(result is DecodedOutput.Success)
        val success = result as DecodedOutput.Success
        assertEquals(3, success.rankedPredictions.size)
        assertEquals("sports", success.rankedPredictions[0].category)
        assertEquals(0.9, success.rankedPredictions[0].confidence, 1e-6)
        assertEquals("education", success.rankedPredictions[1].category)
        assertEquals(0.05, success.rankedPredictions[1].confidence, 1e-6)
        assertEquals("entertainment", success.rankedPredictions[2].category)
    }

    @Test
    fun `decode is deterministic for same input`() {
        val raw = successOutput(floatArrayOf(0.3f, 0.6f, 0.1f))
        val r1 = ModelOutputDecoder.decode(raw, sportsConfig)
        val r2 = ModelOutputDecoder.decode(raw, sportsConfig)
        assertTrue(r1 is DecodedOutput.Success)
        assertTrue(r2 is DecodedOutput.Success)
        assertEquals(
            (r1 as DecodedOutput.Success).rankedPredictions,
            (r2 as DecodedOutput.Success).rankedPredictions
        )
    }

    @Test
    fun `decode ranks by confidence descending then label ascending on ties`() {
        val raw = successOutput(floatArrayOf(0.5f, 0.5f, 0.5f))
        val result = ModelOutputDecoder.decode(raw, sportsConfig) as DecodedOutput.Success
        assertEquals("education", result.rankedPredictions[0].category)
        assertEquals("entertainment", result.rankedPredictions[1].category)
        assertEquals("sports", result.rankedPredictions[2].category)
    }

    @Test
    fun `decode respects topK limit`() {
        val config = sportsConfig.copy(topK = 1)
        val raw = successOutput(floatArrayOf(0.3f, 0.6f, 0.1f))
        val result = ModelOutputDecoder.decode(raw, config) as DecodedOutput.Success
        assertEquals(1, result.rankedPredictions.size)
        assertEquals("entertainment", result.rankedPredictions[0].category)
    }

    @Test
    fun `softmax transforms logits to probabilities`() {
        val logits = floatArrayOf(1.0f, 2.0f, 3.0f)
        val result = ModelOutputDecoder.softmax(logits)
        val sum = result.sum()
        assertEquals(1.0, sum.toDouble(), 1e-5)
        assertEquals(2, result.indices.maxByOrNull { result[it] })
    }

    @Test
    fun `softmax is numerically stable`() {
        val logits = floatArrayOf(1000f, 1001f, 1002f)
        val result = ModelOutputDecoder.softmax(logits)
        assertTrue(result.all { it.isFinite() })
        assertEquals(1.0, result.sum().toDouble(), 1e-5)
    }

    @Test
    fun `softmax with empty array returns empty`() {
        val result = ModelOutputDecoder.softmax(floatArrayOf())
        assertEquals(0, result.size)
    }

    @Test
    fun `softmax with single element returns 1_0`() {
        val result = ModelOutputDecoder.softmax(floatArrayOf(5.0f))
        assertEquals(1, result.size)
        assertEquals(1.0f, result[0], 1e-6f)
    }

    @Test
    fun `softmax with equal values returns uniform`() {
        val result = ModelOutputDecoder.softmax(floatArrayOf(3.0f, 3.0f, 3.0f))
        assertEquals(3, result.size)
        for (v in result) {
            assertEquals(1.0f / 3.0f, v, 1e-5f)
        }
    }

    @Test
    fun `softmax semantics applies softmax transformation`() {
        val config = sportsConfig.copy(outputSemantics = OutputSemantics.SOFTMAX)
        val raw = successOutput(floatArrayOf(1.0f, 2.0f, 3.0f))
        val result = ModelOutputDecoder.decode(raw, config) as DecodedOutput.Success
        assertEquals("education", result.rankedPredictions[0].category)
        val sum = result.rankedPredictions.sumOf { it.confidence }
        assertEquals(1.0, sum, 1e-5)
    }

    @Test
    fun `raw scores semantics preserves values`() {
        val raw = successOutput(floatArrayOf(0.9f, 0.05f, 0.05f))
        val result = ModelOutputDecoder.decode(raw, sportsConfig) as DecodedOutput.Success
        assertEquals(0.9, result.rankedPredictions[0].confidence, 1e-6)
    }

    @Test
    fun `argmax semantics behaves like raw scores for ranking`() {
        val config = sportsConfig.copy(outputSemantics = OutputSemantics.ARGMAX)
        val raw = successOutput(floatArrayOf(0.1f, 0.8f, 0.1f))
        val result = ModelOutputDecoder.decode(raw, config) as DecodedOutput.Success
        assertEquals("entertainment", result.rankedPredictions[0].category)
    }

    @Test
    fun `decode fails when adapter output is failed`() {
        val raw = AdapterRawModelOutput(
            artifactIdentity = testArtifact,
            runtimeIdentity = testRuntime,
            preprocessingVersion = "v1",
            inputSignature = "224x224x3:FLOAT32",
            outputTensors = emptyList(),
            rawOutputValues = emptyMap(),
            executionStatus = InferenceExecutionStatus.FAILURE,
            failure = RuntimeFailureFactory.runtimeUnavailable("test-runtime")
        )
        val result = ModelOutputDecoder.decode(raw, sportsConfig)
        assertTrue(result is DecodedOutput.Failure)
        assertEquals(InferenceStatus.INFERENCE_FAILURE, (result as DecodedOutput.Failure).status)
    }

    @Test
    fun `decode fails when output tensor is empty`() {
        val raw = AdapterRawModelOutput(
            artifactIdentity = testArtifact,
            runtimeIdentity = testRuntime,
            preprocessingVersion = "v1",
            inputSignature = "224x224x3:FLOAT32",
            outputTensors = listOf(OutputTensorMetadata(0, listOf(1, 3), "FLOAT32")),
            rawOutputValues = mapOf(0 to floatArrayOf()),
            executionStatus = InferenceExecutionStatus.SUCCESS
        )
        val result = ModelOutputDecoder.decode(raw, sportsConfig)
        assertTrue(result is DecodedOutput.Failure)
        assertEquals(InferenceStatus.OUTPUT_INVALID, (result as DecodedOutput.Failure).status)
    }

    @Test
    fun `decode fails when tensor size does not match label count`() {
        val raw = successOutput(floatArrayOf(0.5f, 0.5f))
        val result = ModelOutputDecoder.decode(raw, sportsConfig)
        assertTrue(result is DecodedOutput.Failure)
        val failure = result as DecodedOutput.Failure
        assertEquals(InferenceStatus.OUTPUT_INVALID, failure.status)
        assertTrue(failure.message.contains("size 2"))
        assertTrue(failure.message.contains("label count 3"))
    }

    @Test
    fun `decode fails when tensor contains NaN`() {
        val raw = successOutput(floatArrayOf(0.5f, Float.NaN, 0.5f))
        val result = ModelOutputDecoder.decode(raw, sportsConfig)
        assertTrue(result is DecodedOutput.Failure)
        assertEquals(InferenceStatus.OUTPUT_INVALID, (result as DecodedOutput.Failure).status)
    }

    @Test
    fun `decode fails when tensor contains Infinity`() {
        val raw = successOutput(floatArrayOf(0.5f, Float.POSITIVE_INFINITY, 0.5f))
        val result = ModelOutputDecoder.decode(raw, sportsConfig)
        assertTrue(result is DecodedOutput.Failure)
        assertEquals(InferenceStatus.OUTPUT_INVALID, (result as DecodedOutput.Failure).status)
    }

    @Test
    fun `decodeToInferenceResult produces SUCCESS status`() {
        val raw = successOutput(floatArrayOf(0.9f, 0.05f, 0.05f))
        val result = ModelOutputDecoder.decodeToInferenceResult(
            rawOutput = raw, config = sportsConfig,
            modelId = "test-model", modelVersion = "v1"
        )
        assertEquals(InferenceStatus.SUCCESS, result.status)
        assertEquals("test-model", result.modelId)
        assertEquals("sports", result.primaryCategory)
        assertEquals(0.9, result.primaryConfidence!!, 1e-6)
        assertTrue(result.hasPrediction)
    }

    @Test
    fun `decodeToInferenceResult stamps provenance`() {
        val raw = successOutput(floatArrayOf(0.9f, 0.05f, 0.05f))
        val result = ModelOutputDecoder.decodeToInferenceResult(
            rawOutput = raw, config = sportsConfig,
            modelId = "test-model", modelVersion = "v1",
            preprocessVersion = "preprocess-v2", evidenceId = "evidence-001"
        )
        assertEquals("preprocess-v2", result.preprocessVersion)
        assertEquals("evidence-001", result.evidenceId)
        assertEquals("interp-v1", result.rawModelMetadata["interpretation.version"])
    }

    @Test
    fun `decodeToInferenceResult preserves raw output metadata`() {
        val raw = successOutput(floatArrayOf(0.9f, 0.05f, 0.05f))
            .copy(deterministicHash = "abc123")
        val result = ModelOutputDecoder.decodeToInferenceResult(
            rawOutput = raw, config = sportsConfig,
            modelId = "test-model", modelVersion = "v1"
        )
        assertEquals("abc123", result.rawModelMetadata["adapter.deterministicHash"])
    }

    @Test
    fun `decodeToInferenceResult failure produces explicit failure result`() {
        val raw = AdapterRawModelOutput(
            artifactIdentity = testArtifact,
            runtimeIdentity = testRuntime,
            preprocessingVersion = "v1",
            inputSignature = "224x224x3:FLOAT32",
            outputTensors = emptyList(),
            rawOutputValues = emptyMap(),
            executionStatus = InferenceExecutionStatus.FAILURE,
            failure = RuntimeFailureFactory.runtimeUnavailable("test-runtime")
        )
        val result = ModelOutputDecoder.decodeToInferenceResult(
            rawOutput = raw, config = sportsConfig,
            modelId = "test-model", modelVersion = "v1"
        )
        assertEquals(InferenceStatus.INFERENCE_FAILURE, result.status)
        assertFalse(result.hasPrediction)
        assertNotNull(result.failure)
        assertEquals("decode", result.failure!!.phase)
    }
}
