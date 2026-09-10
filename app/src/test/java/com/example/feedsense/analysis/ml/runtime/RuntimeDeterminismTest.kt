package com.example.feedsense.analysis.ml.runtime

import com.example.feedsense.analysis.ml.InferenceClock
import com.example.feedsense.analysis.ml.StepInferenceClock
import com.example.feedsense.analysis.ml.preprocess.PreprocessedInput
import com.example.feedsense.analysis.ml.preprocess.PreprocessingVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-5.
 *
 * Runtime determinism: same input -> same output across
 * repeated inferences. Uses injected clock for deterministic
 * timing.
 */
class RuntimeDeterminismTest {

    private fun makePreprocessed(): PreprocessedInput {
        return PreprocessedInput(
            input = com.example.feedsense.analysis.ml.ModelInput(
                width = 4, height = 4, channels = 3,
                tensorType = com.example.feedsense.analysis.ml.ModelTensorType.FLOAT32,
                floats = FloatArray(48) { (it % 10) / 10f }
            ),
            configVersion = PreprocessingVersion.V2,
            configKey = "determinism-test",
            sourceWidth = 100,
            sourceHeight = 100,
            capturedOrientation = com.example.feedsense.analysis.ml.preprocess.FrameOrientation.DEG_0,
            privacyVersion = "privacy-processing-v1",
            policyMode = "RESEARCH",
            sanitizationStatus = "SANITIZED",
            evidenceId = "det-evidence-1",
            sessionId = "det-session-1",
            feedItemId = "det-item-1",
            fingerprint = "det-fingerprint-abc123"
        )
    }

    @Test
    fun `same input produces same output hash`() {
        val clock = StepInferenceClock(initialMs = 1000L, stepMs = 1L)
        val loader = TestArtifactLoader(TestLoaderBehavior.SUCCESS)
        val backend = TestInferenceBackend(
            TestBackendBehavior.InputHashOutput(
                buckets = listOf(
                    floatArrayOf(0.9f, 0.05f, 0.05f),
                    floatArrayOf(0.1f, 0.8f, 0.1f),
                    floatArrayOf(0.1f, 0.1f, 0.8f)
                ),
                outputShape = listOf(1, 3)
            )
        )
        val adapter = ModelRuntimeAdapter(
            config = RuntimeTestFixtures.adapterConfig(),
            artifactLoader = loader,
            backend = backend,
            clock = clock
        )
        adapter.initialize()

        val input = makePreprocessed()
        val a = adapter.infer(input)
        val b = adapter.infer(input)

        assertEquals(a.executionStatus, b.executionStatus)
        assertEquals(a.outputTensorCount, b.outputTensorCount)
        assertEquals(a.primaryOutput()?.toList(), b.primaryOutput()?.toList())
        assertEquals(a.deterministicHash, b.deterministicHash)
        assertTrue(a.succeeded)
    }

    @Test
    fun `different inputs may produce different outputs`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.SUCCESS)
        val backend = TestInferenceBackend(
            TestBackendBehavior.InputHashOutput(
                buckets = listOf(
                    floatArrayOf(0.9f, 0.05f, 0.05f),
                    floatArrayOf(0.1f, 0.8f, 0.1f),
                    floatArrayOf(0.1f, 0.1f, 0.8f)
                ),
                outputShape = listOf(1, 3)
            )
        )
        val adapter = ModelRuntimeAdapter(
            config = RuntimeTestFixtures.adapterConfig(),
            artifactLoader = loader,
            backend = backend
        )
        adapter.initialize()

        val input1 = PreprocessedInput(
            input = com.example.feedsense.analysis.ml.ModelInput(
                width = 4, height = 4, channels = 3,
                tensorType = com.example.feedsense.analysis.ml.ModelTensorType.FLOAT32,
                floats = FloatArray(48) { 0.1f }
            ),
            configVersion = PreprocessingVersion.V2,
            configKey = "det-test",
            sourceWidth = 100, sourceHeight = 100,
            capturedOrientation = com.example.feedsense.analysis.ml.preprocess.FrameOrientation.DEG_0,
            privacyVersion = "privacy-processing-v1",
            policyMode = "RESEARCH",
            sanitizationStatus = "SANITIZED",
            evidenceId = "ev-1", sessionId = "s-1", feedItemId = "f-1",
            fingerprint = "fp-1"
        )
        val input2 = PreprocessedInput(
            input = com.example.feedsense.analysis.ml.ModelInput(
                width = 4, height = 4, channels = 3,
                tensorType = com.example.feedsense.analysis.ml.ModelTensorType.FLOAT32,
                floats = FloatArray(48) { 0.9f }
            ),
            configVersion = PreprocessingVersion.V2,
            configKey = "det-test",
            sourceWidth = 100, sourceHeight = 100,
            capturedOrientation = com.example.feedsense.analysis.ml.preprocess.FrameOrientation.DEG_0,
            privacyVersion = "privacy-processing-v1",
            policyMode = "RESEARCH",
            sanitizationStatus = "SANITIZED",
            evidenceId = "ev-2", sessionId = "s-2", feedItemId = "f-2",
            fingerprint = "fp-2"
        )

        val a = adapter.infer(input1)
        val b = adapter.infer(input2)
        // They MIGHT be different (depends on hash bucket)
        // but both should succeed
        assertTrue(a.succeeded)
        assertTrue(b.succeeded)
    }

    @Test
    fun `output shape is consistent across runs`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.SUCCESS)
        val backend = TestInferenceBackend(
            TestBackendBehavior.FixedOutput(
                floatArrayOf(0.5f, 0.3f, 0.2f),
                listOf(1, 3)
            )
        )
        val adapter = ModelRuntimeAdapter(
            config = RuntimeTestFixtures.adapterConfig(),
            artifactLoader = loader,
            backend = backend
        )
        adapter.initialize()

        val input = makePreprocessed()
        repeat(10) {
            val output = adapter.infer(input)
            assertEquals(1, output.outputTensorCount)
            assertEquals(listOf(1, 3), output.outputTensors[0].shape)
            assertEquals("FLOAT32", output.outputTensors[0].datatype)
        }
    }

    @Test
    fun `deterministic hash is stable across runs`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.SUCCESS)
        val backend = TestInferenceBackend(
            TestBackendBehavior.FixedOutput(
                floatArrayOf(0.5f, 0.3f, 0.2f),
                listOf(1, 3)
            )
        )
        val adapter = ModelRuntimeAdapter(
            config = RuntimeTestFixtures.adapterConfig(),
            artifactLoader = loader,
            backend = backend
        )
        adapter.initialize()

        val input = makePreprocessed()
        val hashes = mutableSetOf<String>()
        repeat(5) {
            val output = adapter.infer(input)
            assertNotNull(output.deterministicHash)
            hashes.add(output.deterministicHash!!)
        }
        assertEquals("all hashes must be identical", 1, hashes.size)
    }
}
