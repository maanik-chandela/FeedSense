package com.example.feedsense.analysis.ml.preprocess.golden

import com.example.feedsense.analysis.ml.preprocess.*
import com.example.feedsense.analysis.privacy.PrivacyFrame
import com.example.feedsense.analysis.privacy.PrivacySanitizationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-4 (§25 performance sanity, §29, §30).
 *
 * Lightweight, deterministic sanity checks that detect
 * CATASTROPHIC preprocessing regressions ONLY:
 *   - accidentally processing a huge image
 *   - allocating unbounded intermediate buffers
 *   - infinite loops
 *   - unexpectedly enormous tensor output
 *
 * This is NOT benchmarking. No production performance claims are
 * made from JVM fixture tests. Tolerance values are deliberately
 * generous.
 */
class GoldenPerformanceSanityTest {

    @Test
    fun `preprocessing a small frame completes within a generous time bound`() {
        val config = PreprocessingConfig(
            version = PreprocessingVersion.V2,
            inputWidth = 224,
            inputHeight = 224,
            resizePolicy = ResizePolicy.BILINEAR,
            cropPolicy = AspectRatioPolicy.CENTER_CROP,
            interpolation = Interpolation.BILINEAR,
            tensorLayout = PreprocessingTensorLayout.NHWC,
            batchSize = BatchHandling.BATCH_1
        )
        val preprocessor = DeterministicPreprocessor(config)
        val frame = GoldenFixtureLoader.generateFrame(SourcePattern.KNOWN_GRADIENT, 320, 240)
        val evidence = PreprocessingEvidenceFactory.approve(
            frame = frame,
            sanitizationStatus = PrivacySanitizationStatus.SANITIZED
        )

        val start = System.nanoTime()
        val result = preprocessor.preprocess(evidence)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertTrue(
            "reference-size preprocessing must succeed",
            result is PreprocessResult.Success
        )
        // Generous bound: reference-size pipeline must not hang.
        // Threshold is intentionally loose (no performance claims).
        assertTrue(
            "reference-size preprocessing took ${elapsedMs}ms (sanity bound 5000ms)",
            elapsedMs < 5000
        )
    }

    @Test
    fun `output tensor is exactly the configured size never unbounded`() {
        val config = PreprocessingConfig(
            version = PreprocessingVersion.V2,
            inputWidth = 224,
            inputHeight = 224,
            resizePolicy = ResizePolicy.BILINEAR,
            cropPolicy = AspectRatioPolicy.CENTER_CROP,
            interpolation = Interpolation.BILINEAR,
            tensorLayout = PreprocessingTensorLayout.NHWC,
            batchSize = BatchHandling.BATCH_1
        )
        val preprocessor = DeterministicPreprocessor(config)
        // A purposefully LARGE synthetic input (well above a phone
        // screen resolution) must still produce a bound 224x224x3
        // tensor, never a tensor proportional to the input size.
        val frame = GoldenFixtureLoader.generateFrame(SourcePattern.KNOWN_GRADIENT, 1080, 2400)
        val evidence = PreprocessingEvidenceFactory.approve(
            frame = frame,
            sanitizationStatus = PrivacySanitizationStatus.SANITIZED
        )
        val result = preprocessor.preprocess(evidence)
        assertTrue(result is PreprocessResult.Success)
        val output = (result as PreprocessResult.Success).output
        assertEquals(224 * 224 * 3, output.tensorSize)
        assertEquals(224 * 224 * 3, output.input.floats.size)
    }

    @Test
    fun `a pathological repeat of tiny resize does not loop forever`() {
        // 1x1 -> 1x1 (identity-size shortcut) must terminate and
        // preserve the pixel exactly.
        val frame = PrivacyFrame(1, 1, intArrayOf(0xFF00FF00.toInt()))
        val resized = SpatialTransform.resize(
            frame, 1, 1, Interpolation.BILINEAR
        )
        assertEquals(0xFF00FF00.toInt(), resized.pixels[0])
    }

    @Test
    fun `two-hundred preprocessing runs of a tiny frame stay bounded`() {
        // Repeated runs of a 4x4 frame must be fast because the
        // pipeline allocates exactly one intermediate buffer per
        // step. This is a sanity ceiling, not a benchmark.
        val config = PreprocessingConfig(
            version = PreprocessingVersion.V2,
            inputWidth = 4,
            inputHeight = 4,
            tensorLayout = PreprocessingTensorLayout.NHWC,
            batchSize = BatchHandling.BATCH_1
        )
        val preprocessor = DeterministicPreprocessor(config)
        val frame = GoldenFixtureLoader.generateFrame(SourcePattern.HIGH_CONTRAST_EDGES, 4, 4)
        val evidence = PreprocessingEvidenceFactory.approve(
            frame = frame,
            sanitizationStatus = PrivacySanitizationStatus.SANITIZED
        )

        val start = System.nanoTime()
        for (i in 0 until 200) {
            val result = preprocessor.preprocess(evidence)
            assertTrue(result is PreprocessResult.Success)
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        // Loose sanity bound: 200 tiny preprocesses well under 10s.
        assertTrue(
            "200 tiny preprocesses took ${elapsedMs}ms (sanity bound 10000ms)",
            elapsedMs < 10000
        )
    }
}