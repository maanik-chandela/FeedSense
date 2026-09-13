package com.example.feedsense.analysis.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-14.
 *
 * Deterministic fake model: lifecycle, failure isolation,
 * explicit statuses, and "same input => same output".
 */
class FakeOnDeviceModelTest {

    private fun specInput(): ModelInput {
        return ModelInput(
            width = MlTestFixtures.INPUT_SPEC.width,
            height = MlTestFixtures.INPUT_SPEC.height,
            channels = MlTestFixtures.INPUT_SPEC.channels,
            tensorType = MlTestFixtures.INPUT_SPEC.tensorType,
            floats = FloatArray(MlTestFixtures.INPUT_SPEC.tensorSize) { 0.5f }
        )
    }

    // --------------------------------
    // SUCCESS PATH
    // --------------------------------

    @Test
    fun `loads once and serves successful deterministic inference`() {
        val model = FakeOnDeviceModel(
            metadata = MlTestFixtures.metadata(),
            behavior = FakeModelBehavior.FixedScores(
                MlTestFixtures.successScores()
            )
        )
        assertEquals(ModelState.AVAILABLE, model.state)
        val load = model.load()
        assertTrue(load.ready)
        assertEquals(ModelState.READY, model.state)
        assertTrue(model.isReady())

        val result = model.infer(specInput())
        assertEquals(InferenceStatus.SUCCESS, result.status)
        assertEquals("sports", result.primaryCategory)
        assertEquals(0.82, result.primaryConfidence ?: -1.0, 1e-9)
        assertEquals(listOf("sports", "entertainment", "meme"),
            result.rankedPredictions.map { it.category })
        assertEquals("ml-v1", result.modelVersion)
        assertEquals("checksum-test-1", result.modelChecksum)
    }

    @Test
    fun `identical input produces identical output`() {
        val model = FakeOnDeviceModel(
            metadata = MlTestFixtures.metadata(),
            behavior = FakeModelBehavior.FixedScores(
                MlTestFixtures.successScores()
            )
        )
        model.load()
        val a = model.infer(specInput())
        val b = model.infer(specInput())
        assertEquals(a.status, b.status)
        assertEquals(a.rankedPredictions, b.rankedPredictions)
        assertTrue(a.rankedPredictions.isNotEmpty())
    }

    @Test
    fun `hash-bucket behavior reacts to input deterministically`() {
        val metadata = MlTestFixtures.metadata()
        val model = FakeOnDeviceModel(
            metadata = metadata,
            behavior = FakeModelBehavior.ScoresByInputHash(
                buckets = listOf(
                    mapOf("sports" to 0.9),
                    mapOf("meme" to 0.9)
                )
            )
        )
        model.load()
        val first = model.infer(specInput())
        val second = model.infer(specInput())
        assertEquals(first.primaryCategory, second.primaryCategory)
        assertEquals(first.rankedPredictions, second.rankedPredictions)
    }

    @Test
    fun `low confidence still succeeds explicitly`() {
        val model = FakeOnDeviceModel(
            metadata = MlTestFixtures.metadata(),
            behavior = FakeModelBehavior.FixedScores(
                mapOf("sports" to 0.31, "entertainment" to 0.29)
            )
        )
        model.load()
        val result = model.infer(specInput())
        assertEquals(InferenceStatus.SUCCESS, result.status)
        assertEquals("sports", result.primaryCategory)
        assertEquals(0.31, result.primaryConfidence ?: -1.0, 1e-9)
    }

    @Test
    fun `predictable latency from injected clock`() {
        val clock = StepInferenceClock(initialMs = 0L, stepMs = 2L)
        val model = FakeOnDeviceModel(
            metadata = MlTestFixtures.metadata(),
            behavior = FakeModelBehavior.FixedScores(
                MlTestFixtures.successScores()
            ),
            clock = clock
        )
        model.load()
        val result = model.infer(specInput())
        assertTrue(result.inferenceLatencyMs > 0)
        // repeated inference measures the same latency
        val again = model.infer(specInput())
        assertEquals(result.inferenceLatencyMs, again.inferenceLatencyMs)
    }

    // --------------------------------
    // FAILURE PATHS
    // --------------------------------

    @Test
    fun `unavailable model is explicit and never crashes`() {
        val model = FakeOnDeviceModel(
            metadata = MlTestFixtures.metadata(),
            behavior = FakeModelBehavior.Unavailable("no artifact")
        )
        val load = model.load()
        assertFalse(load.success)
        assertEquals(ModelState.FAILED, model.state)
        val result = model.infer(specInput())
        assertEquals(InferenceStatus.MODEL_UNAVAILABLE, result.status)
        assertNotNull(result.failure)
        assertEquals("ml-v1", result.modelVersion)
    }

    @Test
    fun `inferring without loading reports model unavailable`() {
        val model = FakeOnDeviceModel(
            metadata = MlTestFixtures.metadata(),
            behavior = FakeModelBehavior.FixedScores(
                MlTestFixtures.successScores()
            )
        )
        val result = model.infer(specInput())
        assertEquals(InferenceStatus.MODEL_UNAVAILABLE, result.status)
    }

    @Test
    fun `load failure is explicit`() {
        val model = FakeOnDeviceModel(
            metadata = MlTestFixtures.metadata(),
            behavior = FakeModelBehavior.LoadFailure("corrupt artifact")
        )
        val load = model.load()
        assertFalse(load.success)
        assertEquals(ModelState.FAILED, model.state)
    }

    @Test
    fun `inference exception becomes explicit inference failure`() {
        val model = FakeOnDeviceModel(
            metadata = MlTestFixtures.metadata(),
            behavior = FakeModelBehavior.InferenceCrash("boom")
        )
        model.load()
        val result = model.infer(specInput())
        assertEquals(InferenceStatus.INFERENCE_FAILURE, result.status)
        assertNotNull(result.failure)
        assertEquals("infer", result.failure!!.phase)
    }

    @Test
    fun `resource limit is explicit`() {
        val model = FakeOnDeviceModel(
            metadata = MlTestFixtures.metadata(),
            behavior = FakeModelBehavior.ResourceLimit(
                ResourceFailureType.OUT_OF_MEMORY
            )
        )
        model.load()
        val result = model.infer(specInput())
        assertEquals(InferenceStatus.RESOURCE_LIMIT, result.status)
        assertEquals(ResourceFailureType.OUT_OF_MEMORY.label,
            result.failure?.message)
    }

    @Test
    fun `malformed output becomes output invalid not a crash`() {
        val model = FakeOnDeviceModel(
            metadata = MlTestFixtures.metadata(),
            behavior = FakeModelBehavior.MalformedScores(
                mapOf("sports" to Double.NaN)
            )
        )
        model.load()
        val result = model.infer(specInput())
        assertEquals(InferenceStatus.OUTPUT_INVALID, result.status)
        assertNotNull(result.failure)
        assertEquals("validate", result.failure!!.phase)
    }

    @Test
    fun `spec mismatch is invalid input`() {
        val model = FakeOnDeviceModel(
            metadata = MlTestFixtures.metadata(),
            behavior = FakeModelBehavior.FixedScores(
                MlTestFixtures.successScores()
            )
        )
        model.load()
        val wrong = ModelInput(
            width = 8,
            height = 8,
            channels = 3,
            tensorType = ModelTensorType.FLOAT32,
            floats = FloatArray(8 * 8 * 3)
        )
        val result = model.infer(wrong)
        assertEquals(InferenceStatus.INVALID_INPUT, result.status)
    }

    @Test
    fun `close releases the model and marks it unavailable`() {
        val model = FakeOnDeviceModel(
            metadata = MlTestFixtures.metadata(),
            behavior = FakeModelBehavior.FixedScores(
                MlTestFixtures.successScores()
            )
        )
        model.load()
        assertTrue(model.isReady())
        model.close()
        assertEquals(ModelState.UNAVAILABLE, model.state)
        assertFalse(model.isReady())
        val result = model.infer(specInput())
        assertEquals(InferenceStatus.MODEL_UNAVAILABLE, result.status)
    }

    @Test
    fun `load is idempotent once ready`() {
        val model = FakeOnDeviceModel(
            metadata = MlTestFixtures.metadata(),
            behavior = FakeModelBehavior.FixedScores(
                MlTestFixtures.successScores()
            )
        )
        model.load()
        assertTrue(model.load().ready)
        assertEquals(ModelState.READY, model.state)
    }

    @Test
    fun `provenance context flows into the result`() {
        val model = FakeOnDeviceModel(
            metadata = MlTestFixtures.metadata(),
            behavior = FakeModelBehavior.FixedScores(
                MlTestFixtures.successScores()
            )
        )
        model.load()
        val result = model.infer(
            specInput(),
            context = InferenceContext(
                evidenceId = "frame-1",
                privacyVersion = "privacy-processing-v1",
                experimentId = "exp-1"
            )
        )
        assertEquals("frame-1", result.evidenceId)
        assertEquals("privacy-processing-v1", result.privacyVersion)
        assertEquals("exp-1", result.experimentId)
        assertEquals("input-v1", result.inputSpecVersion)
        assertEquals("output-v1", result.outputSpecVersion)
        assertNull(result.preprocessVersion)
    }
}