package com.example.feedsense.analysis.ml

import com.example.feedsense.analysis.privacy.PrivacyFrame
import com.example.feedsense.analysis.privacy.SyntheticFrames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-14.
 *
 * Full composition: privacy-safe frame -> preprocess -> model
 * -> provenance-stamped result. Verifies isolation,
 * determinism, latency instrumentation and explicit failures.
 */
class OnDeviceInferenceEngineTest {

    private fun engine(
        metadata: ModelMetadata = MlTestFixtures.metadata(),
        behavior: FakeModelBehavior = FakeModelBehavior.FixedScores(
            MlTestFixtures.successScores()
        ),
        clock: InferenceClock = SystemInferenceClock
    ): OnDeviceInferenceEngine {
        val model = FakeOnDeviceModel(metadata, behavior, clock)
        val preprocessor = DefaultModelPreprocessor(
            metadata.inputSpec,
            version = "preprocess-v1",
            clock = clock
        )
        return OnDeviceInferenceEngine(model, preprocessor, clock)
    }

    @Test
    fun `end to end inference succeeds with full provenance`() {
        val engine = engine()
        val result = engine.analyzeSafeFrame(
            safeFrame = MlTestFixtures.frame(),
            evidenceId = "frame-42",
            privacyVersion = "privacy-processing-v1",
            experimentId = "exp-A"
        )
        assertEquals(InferenceStatus.SUCCESS, result.status)
        assertEquals("sports", result.primaryCategory)
        assertEquals(0.82, result.primaryConfidence ?: -1.0, 1e-9)
        assertEquals("ml-v1", result.modelVersion)
        assertEquals("preprocess-v1", result.preprocessVersion)
        assertEquals("privacy-processing-v1", result.privacyVersion)
        assertEquals("frame-42", result.evidenceId)
        assertEquals("exp-A", result.experimentId)
        assertEquals("input-v1", result.inputSpecVersion)
        assertEquals("output-v1", result.outputSpecVersion)
        assertTrue(result.timestampMs > 0)
    }

    @Test
    fun `latencies are measured separately and deterministically`() {
        val clock = StepInferenceClock(initialMs = 0L, stepMs = 3L)
        val engine = engine(clock = clock)
        val frame = MlTestFixtures.frame()

        val first = engine.analyzeSafeFrame(frame, evidenceId = "f1")
        val second = engine.analyzeSafeFrame(frame, evidenceId = "f2")

        assertEquals(first.preprocessingLatencyMs, second.preprocessingLatencyMs)
        assertEquals(first.inferenceLatencyMs, second.inferenceLatencyMs)
        assertTrue(first.preprocessingLatencyMs > 0)
        assertTrue(first.inferenceLatencyMs > 0)
        assertEquals(
            first.preprocessingLatencyMs + first.inferenceLatencyMs,
            first.totalLatencyMs
        )
    }

    @Test
    fun `identical frames yield identical logical output`() {
        val clock = StepInferenceClock(initialMs = 100L, stepMs = 1L)
        val engine = engine(clock = clock)
        val frame = SyntheticFrames.checkerboard(
            32, 32, 0xFF202020.toInt(), 0xFFDDDDDD.toInt()
        )
        val a = engine.analyzeSafeFrame(frame, evidenceId = "e1")
        val b = engine.analyzeSafeFrame(frame, evidenceId = "e2")
        assertEquals(a.status, b.status)
        assertEquals(a.rankedPredictions, b.rankedPredictions)
        assertEquals(a.preprocessingLatencyMs, b.preprocessingLatencyMs)
        assertEquals(a.inferenceLatencyMs, b.inferenceLatencyMs)
        assertEquals(a.primaryCategory, b.primaryCategory)
    }

    @Test
    fun `unavailable model yields explicit result and keeps pipeline alive`() {
        val engine = engine(
            behavior = FakeModelBehavior.Unavailable("missing")
        )
        val result = engine.analyzeSafeFrame(
            safeFrame = MlTestFixtures.frame(),
            evidenceId = "frame-1"
        )
        assertEquals(InferenceStatus.MODEL_UNAVAILABLE, result.status)
        assertNotNull(result.failure)
        // identity still attributable
        assertEquals("ml-v1", result.modelVersion)
        assertEquals("frame-1", result.evidenceId)
    }

    @Test
    fun `preprocessing failure is explicit and baseline can continue`() {
        val model = FakeOnDeviceModel(
            metadata = MlTestFixtures.metadata(),
            behavior = FakeModelBehavior.FixedScores(
                MlTestFixtures.successScores()
            )
        )
        val failing = object : ModelPreprocessor {
            override val version = "preprocess-fail"
            override val inputSpec = MlTestFixtures.INPUT_SPEC
            override fun preprocess(safeFrame: PrivacyFrame): PreprocessingResult {
                return PreprocessingResult.Failure(
                    status = InferenceStatus.PREPROCESSING_FAILURE,
                    latencyMs = 7,
                    message = "simulated pipeline failure"
                )
            }
        }
        val engine = OnDeviceInferenceEngine(model, failing)
        val result = engine.analyzeSafeFrame(
            safeFrame = MlTestFixtures.frame(),
            evidenceId = "f"
        )
        assertEquals(InferenceStatus.PREPROCESSING_FAILURE, result.status)
        assertEquals(7, result.preprocessingLatencyMs)
        assertEquals("preprocess", result.failure?.phase)
    }

    @Test
    fun `engine never throws on model failures`() {
        val frames = listOf(
            MlTestFixtures.frame(),
            MlTestFixtures.frame(20, 20),
            MlTestFixtures.frame(12, 8)
        )
        val behaviors = listOf(
            FakeModelBehavior.Unavailable("nope"),
            FakeModelBehavior.LoadFailure("bad"),
            FakeModelBehavior.InferenceCrash("crash"),
            FakeModelBehavior.ResourceLimit(ResourceFailureType.OUT_OF_MEMORY),
            FakeModelBehavior.MalformedScores(mapOf("sports" to Double.NaN))
        )
        for (behavior in behaviors) {
            for (frame in frames) {
                var threw = false
                val result = try {
                    engine(behavior = behavior).analyzeSafeFrame(frame)
                } catch (e: Exception) {
                    threw = true
                    null
                }
                assertFalse("behavior=$behavior frame=${frame.width}x${frame.height}",
                    threw)
                assertNotNull(result)
                assertTrue(
                    result!!.status in listOf(
                        InferenceStatus.MODEL_UNAVAILABLE,
                        InferenceStatus.INFERENCE_FAILURE,
                        InferenceStatus.RESOURCE_LIMIT,
                        InferenceStatus.OUTPUT_INVALID,
                        InferenceStatus.INVALID_INPUT,
                        InferenceStatus.PREPROCESSING_FAILURE
                    )
                )
            }
        }
    }

    @Test
    fun `engine requires preprocessing spec to match model spec`() {
        val model = FakeOnDeviceModel(
            metadata = MlTestFixtures.metadata()
        )
        val mismatched = ModelInputSpec(
            specVersion = "input-v2",
            width = 2,
            height = 2,
            channels = 3,
            scale = 0.5
        ) != MlTestFixtures.INPUT_SPEC

        // constructing with a mismatching preprocessor is a
        // configuration error and must fail fast.
        if (mismatched) {
            var threw = false
            try {
                val preprocessor = DefaultModelPreprocessor(
                    ModelInputSpec(
                        specVersion = "input-v2",
                        width = 2,
                        height = 2,
                        channels = 3,
                        scale = 0.5
                    )
                )
                OnDeviceInferenceEngine(model, preprocessor)
            } catch (e: IllegalArgumentException) {
                threw = true
            }
            assertTrue(threw)
        }
    }

    @Test
    fun `provenance derives from a result`() {
        val engine = engine()
        val result = engine.analyzeSafeFrame(
            safeFrame = MlTestFixtures.frame(),
            evidenceId = "frame-1",
            privacyVersion = "privacy-processing-v1",
            experimentId = "exp-A"
        )
        val provenance = InferenceProvenance.from(result)
        assertNotNull(provenance)
        assertEquals("ml-v1", provenance!!.modelVersion)
        assertEquals(
            "feedsense-category-model/ml-v1/preprocess-v1/" +
                "privacy-processing-v1/input-v1/output-v1",
            provenance.summaryKey()
        )
        assertEquals("privacy-processing-v1", provenance.privacyVersion)
        assertEquals("exp-A", provenance.experimentId)
    }
}