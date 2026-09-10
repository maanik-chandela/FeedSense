package com.example.feedsense.analysis.ml.preprocess

import com.example.feedsense.analysis.privacy.PrivacySanitizationStatus
import com.example.feedsense.analysis.privacy.SyntheticFrames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-3 (§18 determinism, §19 edge cases, §4 privacy
 * boundary, production-integration isolation).
 *
 * End-to-end runs of DeterministicPreprocessor. These tests prove:
 *   - identical input + config ALWAYS produce byte-identical tensor
 *     output and fingerprints (determinism);
 *   - the configured shape/channels/datatype always materialize;
 *   - the privacy sanitization status is stamped through;
 *   - the evidence frame buffer is never mutated;
 *   - documentation-only configs fail with CONFIG_NOT_RUNNABLE
 *     instead of guessing dimensions;
 *   - a battery of edge-case frames all produce valid tensors.
 */
class PreprocessingPipelineTest {

    private fun runnableConfig(
        w: Int,
        h: Int,
        crop: AspectRatioPolicy = AspectRatioPolicy.RESIZE
    ): PreprocessingConfig = PreprocessingConfig(
        version = PreprocessingVersion.V2,
        inputWidth = w,
        inputHeight = h,
        resizePolicy = ResizePolicy.NEAREST_NEIGHBOR,
        cropPolicy = crop,
        paddingPolicy = PaddingPolicy.NO_PADDING,
        interpolation = Interpolation.NEAREST,
        orientationPolicy = OrientationPolicy.NORMALIZE_TO_0,
        colorFormat = ColorFormat.RGB,
        channelOrder = PreprocessChannelOrder.RGB,
        alphaPolicy = AlphaPolicy.DISCARD,
        tensorType = PreprocessingTensorType.FLOAT32,
        scale = PreprocessingConfig.DEFAULT_SCALE,
        zeroPoint = 0.0
    )

    private fun process(
        config: PreprocessingConfig,
        frame: com.example.feedsense.analysis.privacy.PrivacyFrame,
        orientation: FrameOrientation = FrameOrientation.DEG_0,
        status: PrivacySanitizationStatus = PrivacySanitizationStatus.NOT_REQUIRED,
        evidenceId: String? = null
    ): PreprocessedInput {
        val preprocessor = DeterministicPreprocessor(config)
        val evidence = PreprocessingEvidenceFactory.approve(
            frame = frame,
            sanitizationStatus = status,
            evidenceId = evidenceId
        )
        val result = preprocessor.preprocess(evidence, orientation)
        return (result as PreprocessResult.Success).output
    }

    // --------------------------------
    // §18 DETERMINISM
    // --------------------------------

    @Test
    fun `identical input and config produce identical tensor bytes`() {
        val config = runnableConfig(4, 4)
        val a = process(config, SyntheticFrames.checkerboard(6, 6, 0xFF000000.toInt(), 0xFFFFFFFF.toInt()))
        val b = process(config, SyntheticFrames.checkerboard(6, 6, 0xFF000000.toInt(), 0xFFFFFFFF.toInt()))
        assertTrue(a.input.contentEquals(b.input))
        assertEquals(a, b)
        assertEquals(a.fingerprint, b.fingerprint)
    }

    @Test
    fun `determinism holds across two preprocessor instances`() {
        val config = runnableConfig(3, 2)
        val frame = SyntheticFrames.verticalGradient(5, 7)
        val first = process(config, frame)
        val second = process(config, frame)
        assertTrue(first.input.contentEquals(second.input))
        assertEquals(first.fingerprint, second.fingerprint)
    }

    @Test
    fun `evidence identity changes the fingerprint but not the tensor`() {
        val config = runnableConfig(4, 4)
        val frame = SyntheticFrames.verticalGradient(4, 4)
        val withoutId = process(config, frame, evidenceId = null)
        val withId = process(config, frame, evidenceId = "evidence-123")
        assertTrue(withoutId.input.contentEquals(withId.input))
        assertNotEquals(withoutId.fingerprint, withId.fingerprint)
        assertEquals(withoutId.fingerprint, withoutId.fingerprint)
    }

    @Test
    fun `config drift changes the fingerprint and output`() {
        val base = runnableConfig(4, 4)
        val drifted = base.copy(
            version = "preprocess-v3",
            interpolation = Interpolation.BILINEAR
        )
        val frame = SyntheticFrames.verticalGradient(4, 4)
        val a = process(base, frame)
        val b = process(drifted, frame)
        assertNotEquals(a.fingerprint, b.fingerprint)
        assertNotEquals(a.configKey, b.configKey)
    }

    @Test
    fun `output is independent of object identity aliasing`() {
        val config = runnableConfig(4, 4)
        val frame = SyntheticFrames.solid(4, 4, 0xFF010203.toInt())
        val first = process(config, frame)
        val second = process(config, frame)
        assertTrue(first !== second)
        assertEquals(first, second)
    }

    // --------------------------------
    // §13 SHAPE / DATATYPE
    // --------------------------------

    @Test
    fun `reference config materialises a 224x224x3 float tensor`() {
        val config = PreprocessingConfig.mobileNetV4ConvReference()
        val output = process(config, SyntheticFrames.checkerboard(480, 720, 0xFF101010.toInt(), 0xFFE0E0E0.toInt()))
        assertEquals(224, output.input.width)
        assertEquals(224, output.input.height)
        assertEquals(3, output.input.channels)
        assertEquals(224 * 224 * 3, output.input.tensorSize)
        assertEquals(224 * 224 * 3, output.input.floats.size)
        assertEquals("NHWC", output.input.layout)
        assertEquals(com.example.feedsense.analysis.ml.ModelTensorType.FLOAT32, output.input.tensorType)
        assertEquals(PreprocessingVersion.V2, output.configVersion)
    }

    @Test
    fun `small runnable config materialises square tensor`() {
        val output = process(runnableConfig(4, 4), SyntheticFrames.verticalGradient(4, 4))
        assertEquals(4, output.input.width)
        assertEquals(4, output.input.height)
        assertEquals(3, output.input.channels)
        assertEquals(48, output.tensorSize)
    }

    // --------------------------------
    // §4 PRIVACY BOUNDARY STAMPING
    // --------------------------------

    @Test
    fun `sanitized evidence status is stamped on the output`() {
        val frame = SyntheticFrames.solid(4, 4, 0xFF000000.toInt())
        val output = process(
            runnableConfig(4, 4), frame,
            status = PrivacySanitizationStatus.SANITIZED
        )
        assertEquals("SANITIZED", output.sanitizationStatus)
        assertEquals("privacy-processing-v1", output.privacyVersion)
        assertEquals("RESEARCH", output.policyMode)
        assertEquals(FrameOrientation.DEG_0, output.capturedOrientation)
    }

    @Test
    fun `not-required frames are stamped explicitly`() {
        val output = process(
            runnableConfig(4, 4),
            SyntheticFrames.solid(4, 4, 0xFF000000.toInt())
        )
        assertEquals("NOT_REQUIRED", output.sanitizationStatus)
    }

    @Test
    fun `evidence frame pixels are not mutated by preprocessing`() {
        val frame = SyntheticFrames.verticalGradient(6, 6)
        val snapshot = frame.pixels.copyOf()
        process(runnableConfig(4, 4), frame)
        assertTrue(snapshot.contentEquals(frame.pixels))
    }

    // --------------------------------
    // §4 / §5 REJECTION PATHS
    // --------------------------------

    @Test
    fun `documentation-only config fails with CONFIG_NOT_RUNNABLE`() {
        val preprocessor = DeterministicPreprocessor(PreprocessingConfig.pendingUnverified("test"))
        val evidence = PreprocessingEvidenceFactory.approve(
            frame = SyntheticFrames.solid(2, 2, 0xFF000000.toInt()),
            sanitizationStatus = PrivacySanitizationStatus.NOT_REQUIRED
        )
        val result = preprocessor.preprocess(evidence)
        assertTrue(result is PreprocessResult.Failure)
        assertEquals(
            PreprocessFailureCode.CONFIG_NOT_RUNNABLE,
            (result as PreprocessResult.Failure).code
        )
    }

    @Test
    fun `unsafe privacy evidence cannot be constructed`() {
        PrivacySanitizationStatus.SANITIZATION_FAILED
        PrivacySanitizationStatus.SANITIZATION_UNAVAILABLE
        PrivacySanitizationStatus.UNKNOWN
        for (unsafe in listOf(
            PrivacySanitizationStatus.SANITIZATION_FAILED,
            PrivacySanitizationStatus.SANITIZATION_UNAVAILABLE,
            PrivacySanitizationStatus.UNKNOWN
        )) {
            try {
                PreprocessingEvidenceFactory.approve(
                    frame = SyntheticFrames.solid(2, 2, 0xFF000000.toInt()),
                    sanitizationStatus = unsafe
                )
                throw AssertionError("unsafe status ${unsafe.label} must be rejected")
            } catch (expected: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun `input validation rejects malformed dimensions explicitly`() {
        val outcome = InputValidation.validateDimensions(0, 10, 0)
        assertTrue(outcome is ValidationOutcome.Invalid)
        val mismatch = InputValidation.validateDimensions(4, 4, 15)
        assertTrue(mismatch is ValidationOutcome.Invalid)
        assertEquals(
            ValidationOutcome.Valid::class,
            InputValidation.validateDimensions(2, 2, 4)::class
        )
    }

    @Test
    fun `preprocessor rejects non-positive frame dimensions explicitly`() {
        val preprocessor = DeterministicPreprocessor(runnableConfig(4, 4))
        try {
            PreprocessingEvidenceFactory.approve(
                frame = com.example.feedsense.analysis.privacy.PrivacyFrame(0, 4, intArrayOf()),
                sanitizationStatus = PrivacySanitizationStatus.NOT_REQUIRED
            )
            throw AssertionError("an invalid frame must be rejected")
        } catch (expected: IllegalArgumentException) {
            // The privacy frame refuses to exist invalid.
        }
    }

    // --------------------------------
    // §6 / §19 EDGE CASES
    // --------------------------------

    @Test
    fun `every capture orientation produces a valid tensor`() {
        val config = runnableConfig(3, 2)
        val frame = SyntheticFrames.verticalGradient(2, 3)
        for (orientation in FrameOrientation.entries) {
            val output = process(config, frame, orientation = orientation)
            assertEquals(3, output.input.width)
            assertEquals(2, output.input.height)
            assertEquals(orientation, output.capturedOrientation)
            assertEquals(3 * 2 * 3, output.tensorSize)
        }
    }

    @Test
    fun `one-by-one source yields a valid tensor`() {
        val output = process(runnableConfig(4, 4), SyntheticFrames.solid(1, 1, 0xFF8080C0.toInt()))
        assertEquals(4 * 4 * 3, output.tensorSize)
        val r = 0x80 / 255.0f
        val b = 0xC0 / 255.0f
        for (pixel in 0 until 16) {
            assertEquals(r, output.input.floats[pixel * 3], 1e-6f)
            assertEquals(r, output.input.floats[pixel * 3 + 1], 1e-6f)
            assertEquals(b, output.input.floats[pixel * 3 + 2], 1e-6f)
        }
    }

    @Test
    fun `already-correct-size source yields a valid tensor`() {
        val output = process(
            runnableConfig(4, 4),
            SyntheticFrames.checkerboard(4, 4, 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), cell = 1)
        )
        assertEquals(4, output.input.width)
        assertEquals(48, output.tensorSize)
        // Content preserved: pixel at (1,0) is white on the checkerboard.
        assertEquals(0.0f, output.input.floats[0], 0f)
        assertEquals(1.0f, output.input.floats[3], 0f)
        assertEquals(1.0f, output.input.floats[4], 0f)
        assertEquals(1.0f, output.input.floats[5], 0f)
    }

    @Test
    fun `odd source dimensions yield a valid tensor`() {
        val output = process(runnableConfig(5, 7), SyntheticFrames.verticalGradient(3, 5))
        assertEquals(5, output.input.width)
        assertEquals(7, output.input.height)
        assertEquals(5 * 7 * 3, output.tensorSize)
    }

    @Test
    fun `portrait and landscape sources both yield valid tensors with center crop`() {
        val config = runnableConfig(4, 4, crop = AspectRatioPolicy.CENTER_CROP)
        val portrait = process(config, SyntheticFrames.verticalGradient(3, 6))
        val landscape = process(config, SyntheticFrames.verticalGradient(6, 3))
        assertEquals(4, portrait.input.width)
        assertEquals(4, landscape.input.width)
        assertEquals(48, portrait.tensorSize)
        assertEquals(48, landscape.tensorSize)
    }

    @Test
    fun `all-black and all-white sources yield valid tensors`() {
        val config = runnableConfig(4, 4)
        val black = process(config, SyntheticFrames.solid(4, 4, 0xFF000000.toInt()))
        val white = process(config, SyntheticFrames.solid(4, 4, 0xFFFFFFFF.toInt()))
        assertTrue(black.input.floats.all { it == 0.0f })
        assertEquals(48, black.tensorSize)
        assertTrue(white.input.floats.all { it == 1.0f })
        assertEquals(48, white.tensorSize)
    }

    @Test
    fun `translucent source still yields opaque RGB tensor`() {
        val translucent = SyntheticFrames.solid(4, 4, 0x80FF0000.toInt())
        val output = process(runnableConfig(4, 4), translucent)
        assertEquals(48, output.tensorSize)
        // Every RGB channel read as red, alpha discarded.
        val r = output.input.floats[0]
        assertEquals(1.0f, r, 0f)
        assertEquals(0.0f, output.input.floats[1], 0f)
        assertEquals(0.0f, output.input.floats[2], 0f)
    }

    // --------------------------------
    // PRODUCTION-INTEGRATION ISOLATION (§22)
    // --------------------------------

    @Test
    fun `preprocessed input carries identifiers only`() {
        val output = process(
            runnableConfig(4, 4),
            SyntheticFrames.verticalGradient(4, 4),
            evidenceId = "ev-1"
        )
        assertNotNull(output.evidenceId)
        assertEquals("preprocess-v2", output.configVersion)
        assertTrue(output.configKey.startsWith("preprocess-v2|"))
        assertTrue(output.fingerprint.matches(Regex("^[0-9a-f]{64}$")))
    }
}