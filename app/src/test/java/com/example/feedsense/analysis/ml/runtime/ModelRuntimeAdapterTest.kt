package com.example.feedsense.analysis.ml.runtime

import com.example.feedsense.analysis.ml.preprocess.PreprocessedInput
import com.example.feedsense.analysis.ml.preprocess.PreprocessingVersion
import com.example.feedsense.analysis.ml.repro.ArtifactAvailability
import com.example.feedsense.analysis.ml.repro.ArtifactValidationStatus
import com.example.feedsense.analysis.ml.repro.ReproArtifactFormat
import com.example.feedsense.analysis.ml.repro.ReproArtifactIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-5.
 *
 * ModelRuntimeAdapter: initialization, inference, lifecycle,
 * error handling.
 */
class ModelRuntimeAdapterTest {

    private fun makePreprocessed(
        width: Int = 4,
        height: Int = 4,
        channels: Int = 3
    ): PreprocessedInput {
        return PreprocessedInput(
            input = com.example.feedsense.analysis.ml.ModelInput(
                width = width,
                height = height,
                channels = channels,
                tensorType = com.example.feedsense.analysis.ml.ModelTensorType.FLOAT32,
                floats = FloatArray(width * height * channels) { 0.5f }
            ),
            configVersion = PreprocessingVersion.V2,
            configKey = "test-config",
            sourceWidth = 100,
            sourceHeight = 100,
            capturedOrientation = com.example.feedsense.analysis.ml.preprocess.FrameOrientation.DEG_0,
            privacyVersion = "privacy-processing-v1",
            policyMode = "RESEARCH",
            sanitizationStatus = "SANITIZED",
            evidenceId = "evidence-1",
            sessionId = "session-1",
            feedItemId = "item-1",
            fingerprint = "test-fingerprint-abc123"
        )
    }

    private fun createAdapter(
        backendBehavior: TestBackendBehavior = TestBackendBehavior.FixedOutput(
            floatArrayOf(0.8f, 0.15f, 0.05f),
            listOf(1, 3)
        )
    ): ModelRuntimeAdapter {
        val loader = TestArtifactLoader(TestLoaderBehavior.SUCCESS)
        val backend = TestInferenceBackend(backendBehavior)
        return ModelRuntimeAdapter(
            config = RuntimeTestFixtures.adapterConfig(),
            artifactLoader = loader,
            backend = backend
        )
    }

    // --------------------------------
    // INITIALIZATION
    // --------------------------------

    @Test
    fun `successful initialization`() {
        val adapter = createAdapter()
        val result = adapter.initialize()
        assertTrue(result.success)
        assertTrue(adapter.isReady)
        assertEquals(RuntimeLifecycleState.READY, adapter.state)
    }

    @Test
    fun `initialization is idempotent`() {
        val adapter = createAdapter()
        adapter.initialize()
        val second = adapter.initialize()
        assertTrue(second.success)
        assertTrue(adapter.isReady)
    }

    @Test
    fun `initialization failure from loader`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.MISSING)
        val backend = TestInferenceBackend()
        val adapter = ModelRuntimeAdapter(
            config = RuntimeTestFixtures.adapterConfig(),
            artifactLoader = loader,
            backend = backend
        )
        val result = adapter.initialize()
        assertFalse(result.success)
        assertNotNull(result.failure)
        assertEquals(RuntimeFailureCode.ARTIFACT_MISSING, result.failure!!.code)
        assertFalse(adapter.isReady)
    }

    // --------------------------------
    // INFERENCE
    // --------------------------------

    @Test
    fun `successful inference returns structured output`() {
        val adapter = createAdapter()
        adapter.initialize()
        val output = adapter.infer(makePreprocessed())
        assertTrue(output.succeeded)
        assertEquals(InferenceExecutionStatus.SUCCESS, output.executionStatus)
        assertEquals(1, output.outputTensorCount)
        assertNotNull(output.primaryOutput())
        assertEquals(3, output.primaryOutput()!!.size)
        assertEquals(0.8f, output.primaryOutput()!![0], 1e-6f)
    }

    @Test
    fun `inference stamps artifact and runtime identity`() {
        val adapter = createAdapter()
        adapter.initialize()
        val output = adapter.infer(makePreprocessed())
        assertEquals(
            RuntimeTestFixtures.TEST_ARTIFACT_AVAILABLE.artifactId,
            output.artifactIdentity.artifactId
        )
        assertEquals(
            RuntimeTestFixtures.TEST_RUNTIME_LITERT.key,
            output.runtimeIdentity.key
        )
    }

    @Test
    fun `inference stamps preprocessing version`() {
        val adapter = createAdapter()
        adapter.initialize()
        val output = adapter.infer(makePreprocessed())
        assertEquals(PreprocessingVersion.V2, output.preprocessingVersion)
    }

    @Test
    fun `inference without initialization fails`() {
        val adapter = createAdapter()
        val output = adapter.infer(makePreprocessed())
        assertFalse(output.succeeded)
        assertEquals(InferenceExecutionStatus.FAILURE, output.executionStatus)
        assertNotNull(output.failure)
        assertEquals(RuntimeFailureCode.INFERENCE_NOT_READY, output.failure!!.code)
    }

    @Test
    fun `inference after close fails`() {
        val adapter = createAdapter()
        adapter.initialize()
        adapter.close()
        val output = adapter.infer(makePreprocessed())
        assertFalse(output.succeeded)
        assertEquals(RuntimeFailureCode.INFERENCE_NOT_READY, output.failure!!.code)
    }

    @Test
    fun `inference with wrong dimensions fails at compatibility check`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.SUCCESS)
        val backend = TestInferenceBackend()
        val adapter = ModelRuntimeAdapter(
            config = RuntimeTestFixtures.adapterConfigWithContract(),
            artifactLoader = loader,
            backend = backend
        )
        adapter.initialize()
        val output = adapter.infer(
            makePreprocessed(width = 8, height = 8)
        )
        assertFalse(output.succeeded)
        assertEquals(RuntimeFailureCode.WRONG_DIMENSIONS, output.failure!!.code)
    }

    @Test
    fun `inference with missing provenance fails`() {
        val adapter = createAdapter()
        adapter.initialize()
        val preprocessed = PreprocessedInput(
            input = com.example.feedsense.analysis.ml.ModelInput(
                width = 4, height = 4, channels = 3,
                tensorType = com.example.feedsense.analysis.ml.ModelTensorType.FLOAT32,
                floats = FloatArray(48) { 0.5f }
            ),
            configVersion = PreprocessingVersion.V2,
            configKey = "test",
            sourceWidth = 100,
            sourceHeight = 100,
            capturedOrientation = com.example.feedsense.analysis.ml.preprocess.FrameOrientation.DEG_0,
            privacyVersion = "",
            policyMode = "",
            sanitizationStatus = "",
            evidenceId = null,
            sessionId = null,
            feedItemId = null,
            fingerprint = ""
        )
        val output = adapter.infer(preprocessed)
        assertFalse(output.succeeded)
        assertEquals(RuntimeFailureCode.INVALID_PROVENANCE, output.failure!!.code)
    }

    @Test
    fun `backend inference failure is handled`() {
        val adapter = createAdapter(
            backendBehavior = TestBackendBehavior.InferenceFailure("boom")
        )
        adapter.initialize()
        val output = adapter.infer(makePreprocessed())
        assertFalse(output.succeeded)
        assertEquals(RuntimeFailureCode.INFERENCE_EXECUTION_FAILURE, output.failure!!.code)
    }

    @Test
    fun `backend resource exhaustion is handled`() {
        val adapter = createAdapter(
            backendBehavior = TestBackendBehavior.ResourceExhaustion("OOM")
        )
        adapter.initialize()
        val output = adapter.infer(makePreprocessed())
        assertFalse(output.succeeded)
        assertEquals(RuntimeFailureCode.INFERENCE_RESOURCE_EXHAUSTION, output.failure!!.code)
    }

    // --------------------------------
    // DETERMINISM
    // --------------------------------

    @Test
    fun `identical inputs produce identical outputs`() {
        val adapter = createAdapter()
        adapter.initialize()
        val a = adapter.infer(makePreprocessed())
        val b = adapter.infer(makePreprocessed())
        assertEquals(a.executionStatus, b.executionStatus)
        assertEquals(a.outputTensorCount, b.outputTensorCount)
        assertEquals(a.primaryOutput()?.toList(), b.primaryOutput()?.toList())
        assertEquals(a.deterministicHash, b.deterministicHash)
    }

    // --------------------------------
    // DETERMINISTIC HASH
    // --------------------------------

    @Test
    fun `deterministic hash is computed when enabled`() {
        val adapter = createAdapter()
        adapter.initialize()
        val output = adapter.infer(makePreprocessed())
        assertNotNull(output.deterministicHash)
        assertTrue(output.deterministicHash!!.length == 64)
    }

    // --------------------------------
    // CLOSE / LIFECYCLE
    // --------------------------------

    @Test
    fun `close releases resources`() {
        val adapter = createAdapter()
        adapter.initialize()
        adapter.close()
        assertFalse(adapter.isReady)
        assertEquals(RuntimeLifecycleState.UNLOADED, adapter.state)
    }

    @Test
    fun `close is idempotent`() {
        val adapter = createAdapter()
        adapter.initialize()
        adapter.close()
        adapter.close()
        assertFalse(adapter.isReady)
    }

    @Test
    fun `state summary reflects current state`() {
        val adapter = createAdapter()
        val summary = adapter.stateSummary()
        assertEquals(RuntimeLifecycleState.UNLOADED, summary.lifecycleState)
        assertFalse(summary.isReady)
        adapter.initialize()
        val readySummary = adapter.stateSummary()
        assertEquals(RuntimeLifecycleState.READY, readySummary.lifecycleState)
        assertTrue(readySummary.isReady)
    }

    // --------------------------------
    // OUTPUT TENSOR VALIDATION
    // --------------------------------

    @Test
    fun `output with NaN values fails validation`() {
        val adapter = createAdapter(
            backendBehavior = TestBackendBehavior.MalformedOutput(
                floatArrayOf(Float.NaN, 0.5f, 0.3f),
                listOf(1, 3)
            )
        )
        adapter.initialize()
        val output = adapter.infer(makePreprocessed())
        assertFalse(output.succeeded)
        assertEquals(RuntimeFailureCode.INVALID_OUTPUT_VALUES, output.failure!!.code)
    }

    @Test
    fun `output with out-of-range values fails validation`() {
        val adapter = createAdapter(
            backendBehavior = TestBackendBehavior.MalformedOutput(
                floatArrayOf(1.5f, 0.5f, 0.3f),
                listOf(1, 3)
            )
        )
        adapter.initialize()
        val output = adapter.infer(makePreprocessed())
        assertFalse(output.succeeded)
        assertEquals(RuntimeFailureCode.INVALID_OUTPUT_VALUES, output.failure!!.code)
    }
}
