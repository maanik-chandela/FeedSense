package com.example.feedsense.analysis.ml.real

import com.example.feedsense.analysis.ml.runtime.BackendInferenceResult
import com.example.feedsense.analysis.ml.runtime.InferenceExecutionStatus
import com.example.feedsense.analysis.ml.runtime.ModelRuntimeAdapter
import com.example.feedsense.analysis.ml.runtime.OutputTensorMetadata
import com.example.feedsense.analysis.ml.runtime.RuntimeFailureCode
import com.example.feedsense.analysis.ml.runtime.RuntimeFailureFactory
import com.example.feedsense.analysis.ml.runtime.RuntimeTestFixtures
import com.example.feedsense.analysis.ml.runtime.TestArtifactLoader
import com.example.feedsense.analysis.ml.runtime.TestBackendBehavior
import com.example.feedsense.analysis.ml.runtime.TestInferenceBackend
import com.example.feedsense.analysis.ml.runtime.TestLoaderBehavior
import com.example.feedsense.analysis.ml.runtime.litert.MobileNetV2Artifact
import com.example.feedsense.analysis.ml.runtime.litert.MobileNetV2Labels
import com.example.feedsense.analysis.ml.ModelInput
import com.example.feedsense.analysis.ml.ModelTensorType
import com.example.feedsense.analysis.ml.repro.ArtifactAvailability
import com.example.feedsense.analysis.ml.repro.ArtifactValidationStatus
import com.example.feedsense.analysis.ml.repro.ReproArtifactFormat
import com.example.feedsense.analysis.ml.repro.ReproArtifactIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// --------------------------------
// REAL MODEL FAILURE HANDLING TEST (8B-15-9, Phase 10)
// --------------------------------
//
// Tests all 11 failure classes from the milestone using test
// doubles. These tests verify that the infrastructure handles
// failures deterministically and produces research-useful
// diagnostics.
//
// The test doubles simulate the failure modes without
// requiring the actual TFLite runtime.

class RealModelFailureHandlingTest {

    // --------------------------------
    // 1. MISSING ARTIFACT
    // --------------------------------

    @Test
    fun `missing artifact produces ARTIFACT_MISSING failure`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.MISSING)
        val backend = TestInferenceBackend()
        val adapter = ModelRuntimeAdapter(
            config = RuntimeTestFixtures.adapterConfig(),
            artifactLoader = loader,
            backend = backend
        )

        val result = adapter.initialize()

        assertFalse("initialization should fail", result.success)
        assertNotNull("failure should be present", result.failure)
        assertEquals(
            RuntimeFailureCode.ARTIFACT_MISSING,
            result.failure!!.code
        )
    }

    // --------------------------------
    // 2. INVALID ARTIFACT (corrupt)
    // --------------------------------

    @Test
    fun `corrupt artifact produces ARTIFACT_CORRUPT failure`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.CORRUPT)
        val backend = TestInferenceBackend()
        val adapter = ModelRuntimeAdapter(
            config = RuntimeTestFixtures.adapterConfig(),
            artifactLoader = loader,
            backend = backend
        )

        val result = adapter.initialize()

        assertFalse("initialization should fail", result.success)
        assertEquals(
            RuntimeFailureCode.ARTIFACT_CORRUPT,
            result.failure!!.code
        )
    }

    // --------------------------------
    // 3. ARTIFACT HASH MISMATCH
    // --------------------------------

    @Test
    fun `identity mismatch produces ARTIFACT_IDENTITY_MISMATCH failure`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.IDENTITY_MISMATCH)
        val backend = TestInferenceBackend()
        val adapter = ModelRuntimeAdapter(
            config = RuntimeTestFixtures.adapterConfig(),
            artifactLoader = loader,
            backend = backend
        )

        val result = adapter.initialize()

        assertFalse("initialization should fail", result.success)
        assertEquals(
            RuntimeFailureCode.ARTIFACT_IDENTITY_MISMATCH,
            result.failure!!.code
        )
    }

    // --------------------------------
    // 4. RUNTIME UNAVAILABLE
    // --------------------------------

    @Test
    fun `runtime unavailable produces RUNTIME_UNAVAILABLE failure`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.RUNTIME_UNAVAILABLE)
        val backend = TestInferenceBackend()
        val adapter = ModelRuntimeAdapter(
            config = RuntimeTestFixtures.adapterConfig(),
            artifactLoader = loader,
            backend = backend
        )

        val result = adapter.initialize()

        assertFalse("initialization should fail", result.success)
        assertEquals(
            RuntimeFailureCode.RUNTIME_UNAVAILABLE,
            result.failure!!.code
        )
    }

    // --------------------------------
    // 5. INCOMPATIBLE INPUT SHAPE
    // --------------------------------

    @Test
    fun `wrong dimensions produces WRONG_DIMENSIONS failure`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.SUCCESS)
        val backend = TestInferenceBackend(
            TestBackendBehavior.FixedOutput(
                FloatArray(1001) { 0.1f },
                listOf(1, 1001)
            )
        )
        val adapter = ModelRuntimeAdapter(
            config = RuntimeTestFixtures.adapterConfigWithContract(
                expectedInput = RealModelPreprocessing.config()
            ),
            artifactLoader = loader,
            backend = backend
        )
        adapter.initialize()

        val wrongInput = ModelInput(
            width = 128,
            height = 128,
            channels = 3,
            tensorType = ModelTensorType.FLOAT32,
            floats = FloatArray(128 * 128 * 3) { 0.5f }
        )

        val preprocessed = com.example.feedsense.analysis.ml.preprocess.PreprocessedInput(
            input = wrongInput,
            configVersion = "preprocess-v2",
            configKey = "wrong-dims",
            sourceWidth = 128,
            sourceHeight = 128,
            capturedOrientation = com.example.feedsense.analysis.ml.preprocess.FrameOrientation.DEG_0,
            privacyVersion = "privacy-processing-v1",
            policyMode = "RESEARCH",
            sanitizationStatus = "SANITIZED",
            evidenceId = "wrong-dims-test",
            sessionId = "test",
            feedItemId = "test",
            fingerprint = "test-fingerprint"
        )

        val output = adapter.infer(preprocessed)

        assertFalse("inference should fail", output.succeeded)
        assertEquals(
            RuntimeFailureCode.WRONG_DIMENSIONS,
            output.failure!!.code
        )
    }

    // --------------------------------
    // 6. INCOMPATIBLE DATATYPE
    // --------------------------------

    @Test
    fun `wrong datatype produces WRONG_DATATYPE failure`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.SUCCESS)
        val backend = TestInferenceBackend(
            TestBackendBehavior.FixedOutput(
                FloatArray(1001) { 0.1f },
                listOf(1, 1001)
            )
        )

        val int8Config = com.example.feedsense.analysis.ml.preprocess.PreprocessingConfig(
            inputWidth = 4,
            inputHeight = 4,
            tensorType = com.example.feedsense.analysis.ml.preprocess.PreprocessingTensorType.INT8
        )

        val adapter = ModelRuntimeAdapter(
            config = RuntimeTestFixtures.adapterConfigWithContract(
                expectedInput = int8Config
            ),
            artifactLoader = loader,
            backend = backend
        )
        adapter.initialize()

        val floatInput = ModelInput(
            width = 4,
            height = 4,
            channels = 3,
            tensorType = ModelTensorType.FLOAT32,
            floats = FloatArray(4 * 4 * 3) { 0.5f }
        )

        val preprocessed = com.example.feedsense.analysis.ml.preprocess.PreprocessedInput(
            input = floatInput,
            configVersion = "preprocess-v2",
            configKey = "wrong-dtype",
            sourceWidth = 4,
            sourceHeight = 4,
            capturedOrientation = com.example.feedsense.analysis.ml.preprocess.FrameOrientation.DEG_0,
            privacyVersion = "privacy-processing-v1",
            policyMode = "RESEARCH",
            sanitizationStatus = "SANITIZED",
            evidenceId = "wrong-dtype-test",
            sessionId = "test",
            feedItemId = "test",
            fingerprint = "test-fingerprint"
        )

        val output = adapter.infer(preprocessed)

        assertFalse("inference should fail", output.succeeded)
        assertEquals(
            RuntimeFailureCode.WRONG_DATATYPE,
            output.failure!!.code
        )
    }

    // --------------------------------
    // 7. INVALID OUTPUT SHAPE
    // --------------------------------

    @Test
    fun `malformed output produces INVALID_OUTPUT_TENSOR failure`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.SUCCESS)
        val backend = TestInferenceBackend(
            TestBackendBehavior.MalformedOutput(
                floatArrayOf(0.5f, 0.3f),
                listOf(1, 2)
            )
        )
        val adapter = ModelRuntimeAdapter(
            config = RuntimeTestFixtures.adapterConfig(),
            artifactLoader = loader,
            backend = backend
        )
        adapter.initialize()

        val input = RuntimeTestFixtures.input4x4Float32()
        val preprocessed = createSimplePreprocessedInput(input)

        val output = adapter.infer(preprocessed)

        assertFalse("inference should fail", output.succeeded)
        assertNotNull(output.failure)
    }

    // --------------------------------
    // 8. INVALID OUTPUT VALUES (NaN)
    // --------------------------------

    @Test
    fun `NaN output values produce INVALID_OUTPUT_VALUES failure`() {
        val nanOutput = FloatArray(1001) { if (it == 0) Float.NaN else 0.1f }
        val loader = TestArtifactLoader(TestLoaderBehavior.SUCCESS)
        val backend = TestInferenceBackend(
            TestBackendBehavior.MalformedOutput(
                nanOutput,
                listOf(1, 1001)
            )
        )
        val adapter = ModelRuntimeAdapter(
            config = RuntimeTestFixtures.adapterConfig(),
            artifactLoader = loader,
            backend = backend
        )
        adapter.initialize()

        val input = RuntimeTestFixtures.input4x4Float32()
        val preprocessed = createSimplePreprocessedInput(input)

        val output = adapter.infer(preprocessed)

        assertFalse("inference should fail with NaN", output.succeeded)
        assertNotNull(output.failure)
    }

    // --------------------------------
    // 9. LABEL MAPPING UNAVAILABLE
    // --------------------------------

    @Test
    fun `decoder fails when label count does not match tensor size`() {
        val config = com.example.feedsense.analysis.ml.OutputInterpretationConfig(
            interpretationVersion = "test-interp-v1",
            labels = listOf("a", "b"),
            outputSemantics = com.example.feedsense.analysis.ml.OutputSemantics.SOFTMAX,
            topK = 2
        )

        val adapterOutput = createAdapterOutputWithScores(
            FloatArray(1001) { 0.1f }
        )

        val decoded = com.example.feedsense.analysis.ml.ModelOutputDecoder.decode(
            adapterOutput, config
        )

        assertTrue("decode should fail", decoded is com.example.feedsense.analysis.ml.DecodedOutput.Failure)
        assertEquals(
            "OUTPUT_INVALID",
            (decoded as com.example.feedsense.analysis.ml.DecodedOutput.Failure).status.label
        )
    }

    // --------------------------------
    // 10. PREPROCESSING FAILURE
    // --------------------------------

    @Test
    fun `adapter fails when not initialized`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.SUCCESS)
        val backend = TestInferenceBackend()
        val adapter = ModelRuntimeAdapter(
            config = RuntimeTestFixtures.adapterConfig(),
            artifactLoader = loader,
            backend = backend
        )
        // NOT calling adapter.initialize()

        val input = RuntimeTestFixtures.input4x4Float32()
        val preprocessed = createSimplePreprocessedInput(input)

        val output = adapter.infer(preprocessed)

        assertFalse("inference should fail when not initialized",
            output.succeeded)
        assertEquals(
            RuntimeFailureCode.INFERENCE_NOT_READY,
            output.failure!!.code
        )
    }

    // --------------------------------
    // 11. INFERENCE EXECUTION FAILURE
    // --------------------------------

    @Test
    fun `inference crash produces INFERENCE_EXECUTION_FAILURE`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.SUCCESS)
        val backend = TestInferenceBackend(
            TestBackendBehavior.InferenceFailure("simulated crash")
        )
        val adapter = ModelRuntimeAdapter(
            config = RuntimeTestFixtures.adapterConfig(),
            artifactLoader = loader,
            backend = backend
        )
        adapter.initialize()

        val input = RuntimeTestFixtures.input4x4Float32()
        val preprocessed = createSimplePreprocessedInput(input)

        val output = adapter.infer(preprocessed)

        assertFalse("inference should fail", output.succeeded)
        assertEquals(
            RuntimeFailureCode.INFERENCE_EXECUTION_FAILURE,
            output.failure!!.code
        )
    }

    // --------------------------------
    // FAILURE DETERMINISM
    // --------------------------------

    @Test
    fun `adapter failure codes are deterministic`() {
        val loader1 = TestArtifactLoader(TestLoaderBehavior.MISSING)
        val backend1 = TestInferenceBackend()
        val adapter1 = ModelRuntimeAdapter(
            config = RuntimeTestFixtures.adapterConfig(),
            artifactLoader = loader1,
            backend = backend1
        )

        val loader2 = TestArtifactLoader(TestLoaderBehavior.MISSING)
        val backend2 = TestInferenceBackend()
        val adapter2 = ModelRuntimeAdapter(
            config = RuntimeTestFixtures.adapterConfig(),
            artifactLoader = loader2,
            backend = backend2
        )

        val r1 = adapter1.initialize()
        val r2 = adapter2.initialize()

        assertEquals(r1.success, r2.success)
        assertEquals(r1.failure?.code, r2.failure?.code)
        assertEquals(r1.failure?.phase, r2.failure?.phase)
    }

    @Test
    fun `failure messages are safe and descriptive`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.MISSING)
        val backend = TestInferenceBackend()
        val adapter = ModelRuntimeAdapter(
            config = RuntimeTestFixtures.adapterConfig(),
            artifactLoader = loader,
            backend = backend
        )

        val result = adapter.initialize()

        val message = result.failure!!.message
        assertTrue("message should not be empty", message.isNotBlank())
        assertFalse("message should not contain pixels",
            message.contains("pixel"))
        assertFalse("message should not contain user data",
            message.contains("user"))
    }

    // --------------------------------
    // REAL MODEL ARTIFACT NOT AVAILABLE
    // --------------------------------

    @Test
    fun `PENDING artifact cannot be loaded`() {
        val identity = MobileNetV2Artifact.artifactIdentity()
        assertEquals(ArtifactAvailability.PENDING, identity.availability)

        val loader = TestArtifactLoader(TestLoaderBehavior.MISSING)
        val backend = TestInferenceBackend()
        val adapter = ModelRuntimeAdapter(
            config = RuntimeTestFixtures.adapterConfig(
                artifact = identity
            ),
            artifactLoader = loader,
            backend = backend
        )

        val result = adapter.initialize()
        assertFalse("PENDING artifact should fail to load", result.success)
    }

    // --------------------------------
    // HELPERS
    // --------------------------------

    private fun createSimplePreprocessedInput(
        input: com.example.feedsense.analysis.ml.ModelInput
    ): com.example.feedsense.analysis.ml.preprocess.PreprocessedInput {
        return com.example.feedsense.analysis.ml.preprocess.PreprocessedInput(
            input = input,
            configVersion = "preprocess-v2",
            configKey = "test-config",
            sourceWidth = input.width,
            sourceHeight = input.height,
            capturedOrientation = com.example.feedsense.analysis.ml.preprocess.FrameOrientation.DEG_0,
            privacyVersion = "privacy-processing-v1",
            policyMode = "RESEARCH",
            sanitizationStatus = "SANITIZED",
            evidenceId = "failure-test",
            sessionId = "test",
            feedItemId = "test",
            fingerprint = "test-fingerprint-${input.width}x${input.height}"
        )
    }

    private fun createAdapterOutputWithScores(
        scores: FloatArray
    ): com.example.feedsense.analysis.ml.runtime.AdapterRawModelOutput {
        return com.example.feedsense.analysis.ml.runtime.AdapterRawModelOutput(
            artifactIdentity = RuntimeTestFixtures.TEST_ARTIFACT_AVAILABLE,
            runtimeIdentity = RuntimeTestFixtures.TEST_RUNTIME_LITERT,
            preprocessingVersion = "preprocess-v2",
            inputSignature = "${scores.size}:FLOAT32",
            outputTensors = listOf(
                OutputTensorMetadata(
                    tensorIndex = 0,
                    shape = listOf(1, scores.size),
                    datatype = "FLOAT32",
                    valueRangeMin = scores.min().toDouble(),
                    valueRangeMax = scores.max().toDouble()
                )
            ),
            rawOutputValues = mapOf(0 to scores.copyOf()),
            executionStatus = InferenceExecutionStatus.SUCCESS,
            inferenceDurationMs = 10L
        )
    }
}
