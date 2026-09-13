package com.example.feedsense.analysis.ml

import com.example.feedsense.analysis.privacy.SyntheticFrames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-14.
 *
 * Preprocessing abstraction: privacy-approved frame ->
 * framework-agnostic ModelInput. Deterministic, in-memory,
 * never persists raw frames (sections 15-17).
 */
class DefaultModelPreprocessorTest {

    private val spec = ModelInputSpec(
        specVersion = "input-v1",
        width = 2,
        height = 2,
        channels = 3,
        pixelFormat = ModelPixelFormat.RGB,
        tensorType = ModelTensorType.FLOAT32,
        scale = 1.0 / 255.0
    )

    @Test
    fun `produces a float tensor at spec size with rgb extraction`() {
        val frame = SyntheticFrames.solid(8, 8, 0xFF00FF00.toInt())
        val preprocessor = DefaultModelPreprocessor(spec)
        val result = preprocessor.preprocess(frame)

        assertTrue(result is PreprocessingResult.Success)
        val success = result as PreprocessingResult.Success
        assertEquals(2, success.input.width)
        assertEquals(2, success.input.height)
        assertEquals(3, success.input.channels)
        assertEquals(ModelTensorType.FLOAT32, success.input.tensorType)
        // ARGB green -> RGB (0,255,0) scaled by 1/255 -> (0,1,0)
        assertEquals(0.0f, success.input.floats[0], 1e-6f)
        assertEquals(1.0f, success.input.floats[1], 1e-6f)
        assertEquals(0.0f, success.input.floats[2], 1e-6f)
        assertNull(success.input.quantizedBytes)
    }

    @Test
    fun `resizes deterministically with nearest neighbour`() {
        val frame = SyntheticFrames.checkerboard(
            16, 16, 0xFF000000.toInt(), 0xFFFFFFFF.toInt()
        )
        val preprocessor = DefaultModelPreprocessor(spec)
        val first = (preprocessor.preprocess(frame) as PreprocessingResult.Success).input
        val second = (preprocessor.preprocess(frame) as PreprocessingResult.Success).input
        assertTrue(first.contentEquals(second))
        assertEquals(2 * 2 * 3, first.floats.size)
    }

    @Test
    fun `applies per-channel normalization`() {
        val normSpec = spec.copy(
            normalization = NormalizationSpec(
                mean = listOf(0.5, 0.5, 0.5),
                std = listOf(0.25, 0.25, 0.25)
            )
        )
        val frame = SyntheticFrames.solid(4, 4, 0xFF808080.toInt())
        val preprocessor = DefaultModelPreprocessor(normSpec)
        val success = preprocessor.preprocess(frame) as PreprocessingResult.Success
        // sample01 = 0.5039..., (sample01 - 0.5)/0.25
        val expected = (128.0f / 255.0f - 0.5f) / 0.25f
        assertEquals(expected, success.input.floats[0], 1e-4f)
        assertEquals(expected, success.input.floats[2], 1e-4f)
    }

    @Test
    fun `quantizes int8 tensors and exposes bytes`() {
        val quantSpec = spec.copy(
            tensorType = ModelTensorType.INT8,
            scale = 1.0 / 255.0,
            zeroPoint = -128.0
        )
        val frame = SyntheticFrames.solid(4, 4, 0xFFFFFFFF.toInt())
        val preprocessor = DefaultModelPreprocessor(quantSpec)
        val success = preprocessor.preprocess(frame) as PreprocessingResult.Success
        assertEquals(ModelTensorType.INT8, success.input.tensorType)
        assertEquals(2 * 2 * 3, success.input.quantizedBytes?.size)
        // white -> 1.0 -> q = round(1.0 / (1/255)) + (-128) = 127
        assertEquals(127, success.input.quantizedBytes!![0].toInt())
        // floats carry the dequantized approximation (within
        // INT8 rounding error)
        assertEquals(1.0f, success.input.floats[0], 0.01f)
    }

    @Test
    fun `supports argb four channel tensors`() {
        val argbSpec = ModelInputSpec(
            specVersion = "input-v1",
            width = 1,
            height = 1,
            channels = 4,
            pixelFormat = ModelPixelFormat.ARGB,
            scale = 1.0 / 255.0
        )
        val frame = SyntheticFrames.solid(2, 2, 0xFFA0B0C0.toInt())
        val preprocessor = DefaultModelPreprocessor(argbSpec)
        val success = preprocessor.preprocess(frame) as PreprocessingResult.Success
        assertEquals(4, success.input.channels)
        assertEquals(0xFF.toFloat() / 255.0f, success.input.floats[0], 1e-6f)  // alpha
        assertEquals(0xA0.toFloat() / 255.0f, success.input.floats[1], 1e-6f)  // r
        assertEquals(0xB0.toFloat() / 255.0f, success.input.floats[2], 1e-6f)  // g
        assertEquals(0xC0.toFloat() / 255.0f, success.input.floats[3], 1e-6f)  // b
    }

    @Test
    fun `reports deterministic latency from injected clock`() {
        val clock = StepInferenceClock(initialMs = 100L, stepMs = 5L)
        val preprocessor = DefaultModelPreprocessor(spec, clock = clock)
        val frame = SyntheticFrames.solid(8, 8, 0xFF000000.toInt())
        val result = preprocessor.preprocess(frame)
        // one start read + one end read -> one step of 5ms
        assertEquals(5L, result.latencyMs)
        val again = preprocessor.preprocess(frame)
        assertEquals(5L, again.latencyMs)
    }

    @Test
    fun `model input equality is based on tensor content`() {
        val frame = SyntheticFrames.checkerboard(8, 8, 0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        val preprocessor = DefaultModelPreprocessor(spec)
        val first = (preprocessor.preprocess(frame) as PreprocessingResult.Success).input
        val second = (preprocessor.preprocess(frame) as PreprocessingResult.Success).input
        assertTrue(first == second)
        assertEquals(first.hashCode(), second.hashCode())
        assertEquals(2 * 2 * 3, first.floats.size)
    }
}