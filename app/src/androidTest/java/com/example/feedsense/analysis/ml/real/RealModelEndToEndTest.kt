package com.example.feedsense.analysis.ml.real

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.feedsense.analysis.ml.preprocess.PreprocessResult
import com.example.feedsense.analysis.ml.repro.ArtifactAvailability
import com.example.feedsense.analysis.ml.runtime.litert.LiteRtArtifactLoader
import com.example.feedsense.analysis.ml.runtime.litert.LiteRtRuntimeBackend
import com.example.feedsense.analysis.ml.runtime.litert.MobileNetV2Artifact
import com.example.feedsense.analysis.ml.runtime.litert.MobileNetV2Labels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

// --------------------------------
// REAL MODEL END-TO-END TEST (8B-15-9, Phase 13)
// --------------------------------
//
// Android instrumented test for real model inference.
//
// Prerequisites:
//   1. TFLite dependency added to build.gradle.kts
//   2. Model artifact (model.tflite) placed in the device's
//      test directory
//
// This test:
//   - Loads the real MobileNetV2 TFLite model
//   - Preprocesses a synthetic image
//   - Runs real inference
//   - Captures and verifies the real output
//   - Measures timing
//   - Verifies output shape and label mapping
//
// The test is skipped if the model artifact is not available.

@RunWith(AndroidJUnit4::class)
class RealModelEndToEndTest {

    private lateinit var context: Context
    private var modelAvailable = false
    private var modelPath: String? = null

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext

        // Check if the model artifact is available
        val modelFile = findModelArtifact()
        modelAvailable = modelFile != null && modelFile.exists()
        modelPath = modelFile?.absolutePath
    }

    @Test
    fun `real model end-to-end inference`() {
        assumeTrue(
            "Model artifact not available. " +
                "Download ${MobileNetV2Artifact.SOURCE_URL} " +
                "and place as ${MobileNetV2Artifact.ARTIFACT_FILE_NAME} " +
                "in the app's files directory.",
            modelAvailable
        )

        val path = modelPath!!

        // 1. Load artifact
        val loader = LiteRtArtifactLoader(
            artifactDirectory = File(path).parentFile
        )
        val artifactIdentity = MobileNetV2Artifact.artifactIdentity(
            available = true
        )
        val loadResult = loader.load(artifactIdentity)
        assertTrue("artifact should load", loadResult.succeeded)

        // 2. Initialize backend
        val backend = LiteRtRuntimeBackend()
        val backendResult = backend.loadModel(path)
        assertTrue("backend should load model", backendResult is com.example.feedsense.analysis.ml.runtime.ModelLoadAttemptResult.Success)

        // 3. Preprocess golden fixture
        val fixture = RealModelGoldenFixtures.CANONICAL_224_KNOWN_GRADIENT
        val preprocessResult = RealModelGoldenFixtures.preprocessFixture(fixture)
        assertTrue("preprocessing should succeed",
            preprocessResult is PreprocessResult.Success)
        val preprocessed = (preprocessResult as PreprocessResult.Success).output

        assertEquals(224, preprocessed.input.width)
        assertEquals(224, preprocessed.input.height)
        assertEquals(3, preprocessed.input.channels)

        // 4. Run inference
        val startTime = System.currentTimeMillis()
        val inferenceResult = backend.executeInference(preprocessed.input)
        val inferenceTimeMs = System.currentTimeMillis() - startTime

        assertTrue("inference should succeed",
            inferenceResult is com.example.feedsense.analysis.ml.runtime.BackendInferenceResult.Success)

        val success = inferenceResult as com.example.feedsense.analysis.ml.runtime.BackendInferenceResult.Success
        val outputTensor = success.outputTensors[0]!!

        // 5. Verify output shape
        assertEquals(1001, outputTensor.size)
        assertEquals(listOf(1, 1001), success.outputMetadata[0].shape)

        // 6. Verify output is finite
        assertTrue("all output values should be finite",
            outputTensor.all { it.isFinite() })

        // 7. Verify output has variation (not all same value)
        val uniqueValues = outputTensor.toSet().size
        assertTrue("output should have variation (got $uniqueValues unique values)",
            uniqueValues > 1)

        // 8. Find top-1 label
        val maxIndex = outputTensor.indices.maxByOrNull { outputTensor[it] }!!
        val top1Label = MobileNetV2Labels.REPRESENTATIVE_TEST_LABELS
            .getOrElse(maxIndex) { "index_$maxIndex" }

        // 9. Log results
        android.util.Log.i(TAG, "=== REAL MODEL END-TO-END RESULTS ===")
        android.util.Log.i(TAG, "Model: ${MobileNetV2Artifact.MODEL_ID} ${MobileNetV2Artifact.MODEL_VERSION}")
        android.util.Log.i(TAG, "Input: ${preprocessed.input.width}x${preprocessed.input.height}x${preprocessed.input.channels}")
        android.util.Log.i(TAG, "Output shape: ${success.outputMetadata[0].shape}")
        android.util.Log.i(TAG, "Inference time: ${inferenceTimeMs}ms")
        android.util.Log.i(TAG, "Top-1 index: $maxIndex")
        android.util.Log.i(TAG, "Top-1 score: ${outputTensor[maxIndex]}")
        android.util.Log.i(TAG, "Top-5 indices: ${
            outputTensor.indices.sortedByDescending { outputTensor[it] }
                .take(5).joinToString(", ")
        }")
        android.util.Log.i(TAG, "Output min: ${outputTensor.min()}")
        android.util.Log.i(TAG, "Output max: ${outputTensor.max()}")
        android.util.Log.i(TAG, "Output mean: ${outputTensor.average()}")
        android.util.Log.i(TAG, "======================================")

        // 10. Cleanup
        backend.release()
        loader.release()
    }

    @Test
    fun `real model output is deterministic across runs`() {
        assumeTrue("Model artifact not available", modelAvailable)

        val path = modelPath!!

        val backend1 = LiteRtRuntimeBackend()
        backend1.loadModel(path)

        val backend2 = LiteRtRuntimeBackend()
        backend2.loadModel(path)

        val fixture = RealModelGoldenFixtures.CANONICAL_224_KNOWN_GRADIENT
        val preprocessResult = RealModelGoldenFixtures.preprocessFixture(fixture)
        val preprocessed = (preprocessResult as PreprocessResult.Success).output

        val result1 = backend1.executeInference(preprocessed.input)
        val result2 = backend2.executeInference(preprocessed.input)

        assertTrue(result1 is com.example.feedsense.analysis.ml.runtime.BackendInferenceResult.Success)
        assertTrue(result2 is com.example.feedsense.analysis.ml.runtime.BackendInferenceResult.Success)

        val output1 = (result1 as com.example.feedsense.analysis.ml.runtime.BackendInferenceResult.Success)
            .outputTensors[0]!!
        val output2 = (result2 as com.example.feedsense.analysis.ml.runtime.BackendInferenceResult.Success)
            .outputTensors[0]!!

        // Outputs should be bitwise identical for same input
        // (TFLite CPU inference is deterministic for same model + input)
        assertEquals("output length should match", output1.size, output2.size)

        var maxDiff = 0f
        for (i in output1.indices) {
            val diff = kotlin.math.abs(output1[i] - output2[i])
            if (diff > maxDiff) maxDiff = diff
        }

        android.util.Log.i(TAG, "Determinism check: max diff = $maxDiff")
        assertEquals(
            "outputs should be bitwise identical for CPU inference",
            0f, maxDiff
        )

        backend1.release()
        backend2.release()
    }

    @Test
    fun `real model timing measurement`() {
        assumeTrue("Model artifact not available", modelAvailable)

        val path = modelPath!!
        val backend = LiteRtRuntimeBackend()
        backend.loadModel(path)

        val fixture = RealModelGoldenFixtures.CANONICAL_224_KNOWN_GRADIENT
        val preprocessResult = RealModelGoldenFixtures.preprocessFixture(fixture)
        val preprocessed = (preprocessResult as PreprocessResult.Success).output

        val measurements = mutableListOf<Long>()
        val warmupRuns = 3
        val measuredRuns = 10

        // Warmup
        repeat(warmupRuns) {
            backend.executeInference(preprocessed.input)
        }

        // Measured runs
        repeat(measuredRuns) {
            val start = System.nanoTime()
            backend.executeInference(preprocessed.input)
            val elapsed = (System.nanoTime() - start) / 1_000_000
            measurements.add(elapsed)
        }

        val sorted = measurements.sorted()
        val min = sorted.first()
        val max = sorted.last()
        val median = sorted[sorted.size / 2]
        val mean = measurements.average()
        val p95 = sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)]

        android.util.Log.i(TAG, "=== REAL MODEL TIMING ===")
        android.util.Log.i(TAG, "N: $measuredRuns")
        android.util.Log.i(TAG, "min: ${min}ms")
        android.util.Log.i(TAG, "median: ${median}ms")
        android.util.Log.i(TAG, "mean: ${"%.1f".format(mean)}ms")
        android.util.Log.i(TAG, "p95: ${p95}ms")
        android.util.Log.i(TAG, "max: ${max}ms")
        android.util.Log.i(TAG, "=========================")

        backend.release()
    }

    private fun findModelArtifact(): File? {
        // Check in app's internal files directory
        val internalFile = File(context.filesDir, MobileNetV2Artifact.ARTIFACT_FILE_NAME)
        if (internalFile.exists()) return internalFile

        // Check in app's cache directory
        val cacheFile = File(context.cacheDir, MobileNetV2Artifact.ARTIFACT_FILE_NAME)
        if (cacheFile.exists()) return cacheFile

        // Check in assets
        return null
    }

    companion object {
        private const val TAG = "RealModelE2E"
    }
}
