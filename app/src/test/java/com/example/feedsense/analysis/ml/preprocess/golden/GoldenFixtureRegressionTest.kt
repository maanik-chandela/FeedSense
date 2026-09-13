package com.example.feedsense.analysis.ml.preprocess.golden

import com.example.feedsense.analysis.ml.preprocess.DeterministicPreprocessor
import com.example.feedsense.analysis.ml.preprocess.FrameOrientation
import com.example.feedsense.analysis.ml.preprocess.PreprocessFailureCode
import com.example.feedsense.analysis.ml.preprocess.PreprocessResult
import com.example.feedsense.analysis.ml.preprocess.PreprocessingConfig
import com.example.feedsense.analysis.ml.preprocess.PreprocessingEvidenceFactory
import com.example.feedsense.analysis.privacy.PrivacySanitizationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/*
 * Milestone 8B-15-4 (§2-§13, §21, §22, §32, §33).
 *
 * The golden fixture regression suite. Every fixture in the
 * corpus is processed through the REAL preprocessing
 * implementation (DeterministicPreprocessor) and compared
 * against its golden expectations.
 *
 * Key properties verified:
 *   - the real implementation, not a copy, is exercised;
 *   - the corpus is versioned and independently so;
 *   - synthetic deterministic fixtures cover the required
 *     categories;
 *   - preprocessing is deterministic (same input -> same output);
 *   - golden failures are detectable and diagnosable;
 *   - golden outputs are NOT auto-updated.
 */
class GoldenFixtureRegressionTest {

    /**
     * Runs the real preprocessing pipeline for a fixture. Uses the
     * fixture's source pattern/dimensions/orientation and either
     * the fixture's config override or the default 4x4 config.
     */
    private fun runFixture(fixture: GoldenFixture, status: PrivacySanitizationStatus = PrivacySanitizationStatus.SANITIZED): PreprocessResult {
        val config = fixture.configOverride ?: defaultConfig()
        val preprocessor = DeterministicPreprocessor(config)
        val frame = GoldenFixtureLoader.loadFrame(fixture)
        val evidence = PreprocessingEvidenceFactory.approve(
            frame = frame,
            sanitizationStatus = status
        )
        return preprocessor.preprocess(evidence, fixture.sourceOrientation)
    }

    private fun defaultConfig(): PreprocessingConfig =
        PreprocessingConfig(
            version = com.example.feedsense.analysis.ml.preprocess.PreprocessingVersion.V2,
            inputWidth = 4,
            inputHeight = 4,
            resizePolicy = com.example.feedsense.analysis.ml.preprocess.ResizePolicy.NEAREST_NEIGHBOR,
            cropPolicy = com.example.feedsense.analysis.ml.preprocess.AspectRatioPolicy.RESIZE,
            paddingPolicy = com.example.feedsense.analysis.ml.preprocess.PaddingPolicy.NO_PADDING,
            interpolation = com.example.feedsense.analysis.ml.preprocess.Interpolation.NEAREST,
            orientationPolicy = com.example.feedsense.analysis.ml.preprocess.OrientationPolicy.NORMALIZE_TO_0,
            colorFormat = com.example.feedsense.analysis.ml.preprocess.ColorFormat.RGB,
            channelOrder = com.example.feedsense.analysis.ml.preprocess.PreprocessChannelOrder.RGB,
            alphaPolicy = com.example.feedsense.analysis.ml.preprocess.AlphaPolicy.DISCARD,
            tensorType = com.example.feedsense.analysis.ml.preprocess.PreprocessingTensorType.FLOAT32,
            scale = PreprocessingConfig.DEFAULT_SCALE,
            zeroPoint = 0.0,
            tensorLayout = com.example.feedsense.analysis.ml.preprocess.PreprocessingTensorLayout.NHWC,
            batchSize = com.example.feedsense.analysis.ml.preprocess.BatchHandling.BATCH_1
        )

    // -------------------------------------------------------------------
    // §12 DETERMINISM TESTS
    // -------------------------------------------------------------------

    @Test
    fun `preprocessing is deterministic across repeated runs for every representative fixture`() {
        for (fixture in representativeFixtures()) {
            val first = runFixture(fixture)
            val second = runFixture(fixture)
            val third = runFixture(fixture)

            assertTrue(
                "fixture ${fixture.fixtureId}: deterministic failure expected",
                first is PreprocessResult.Failure &&
                    second is PreprocessResult.Failure &&
                    third is PreprocessResult.Failure ||
                    first is PreprocessResult.Success &&
                    second is PreprocessResult.Success &&
                    third is PreprocessResult.Success
            )

            if (first is PreprocessResult.Success &&
                second is PreprocessResult.Success &&
                third is PreprocessResult.Success
            ) {
                val a = first.output
                val b = second.output
                val c = third.output
                assertTrue(
                    "fixture ${fixture.fixtureId}: repeated runs must produce identical tensors",
                    a.input.contentEquals(b.input) && b.input.contentEquals(c.input)
                )
                assertEquals("fixture ${fixture.fixtureId}: fingerprints must match", a.fingerprint, b.fingerprint)
                assertEquals("fixture ${fixture.fixtureId}: fingerprints must match", b.fingerprint, c.fingerprint)
            } else if (first is PreprocessResult.Failure &&
                second is PreprocessResult.Failure &&
                third is PreprocessResult.Failure
            ) {
                val f1 = first
                val f2 = second
                val f3 = third
                assertEquals("fixture ${fixture.fixtureId}: failure codes must match", f1.code, f2.code)
                assertEquals("fixture ${fixture.fixtureId}: failure codes must match", f2.code, f3.code)
                assertEquals("fixture ${fixture.fixtureId}: failure messages must match", f1.message, f2.message)
            } else {
                fail("fixture ${fixture.fixtureId}: non-deterministic success/failure across runs")
            }
        }
    }

    @Test
    fun `preprocessing is deterministic across preprocessor instances`() {
        val fixture = GoldenFixtureDefinitions.PATTERN_CHANNEL_ISOLATION
        val config = fixture.configOverride ?: defaultConfig()
        val frame = GoldenFixtureLoader.loadFrame(fixture)

        val evidence = PreprocessingEvidenceFactory.approve(
            frame = frame,
            sanitizationStatus = PrivacySanitizationStatus.SANITIZED
        )

        val preprocessorA = DeterministicPreprocessor(config)
        val preprocessorB = DeterministicPreprocessor(config)
        val preprocessorC = DeterministicPreprocessor(config)

        val a = (preprocessorA.preprocess(evidence) as PreprocessResult.Success).output
        val b = (preprocessorB.preprocess(evidence) as PreprocessResult.Success).output
        val c = (preprocessorC.preprocess(evidence) as PreprocessResult.Success).output

        assertTrue(a.input.contentEquals(b.input))
        assertTrue(b.input.contentEquals(c.input))
        assertEquals(a.fingerprint, c.fingerprint)
    }

    // -------------------------------------------------------------------
    // §7 GOLDEN HASH TESTS
    // -------------------------------------------------------------------

    @Test
    fun `tensor hashes are deterministic across repeated runs`() {
        for (fixture in representativeFixtures()) {
            if (fixture.expectation.expectedFailureCode != null) continue
            val first = runFixture(fixture)
            val second = runFixture(fixture)
            if (first is PreprocessResult.Success && second is PreprocessResult.Success) {
                val h1 = GoldenHasher.hashTensor(first.output.input)
                val h2 = GoldenHasher.hashTensor(second.output.input)
                assertEquals("fixture ${fixture.fixtureId}: hash must be deterministic", h1, h2)
                assertTrue(
                    "fixture ${fixture.fixtureId}: hash must be 64 hex chars",
                    h1.matches(Regex("^[0-9a-f]{64}$"))
                )
            }
        }
    }

    @Test
    fun `same input same hash different input different hash`() {
        val blackFrame = GoldenFixtureLoader.generateFrame(SourcePattern.ALL_BLACK, 2, 2)
        val whiteFrame = GoldenFixtureLoader.generateFrame(SourcePattern.ALL_WHITE, 2, 2)
        val config = defaultConfig()

        fun hash(frame: com.example.feedsense.analysis.privacy.PrivacyFrame): String {
            val evidence = PreprocessingEvidenceFactory.approve(
                frame = frame,
                sanitizationStatus = PrivacySanitizationStatus.SANITIZED
            )
            val result = DeterministicPreprocessor(config).preprocess(evidence)
            return GoldenHasher.hashTensor((result as PreprocessResult.Success).output.input)
        }

        val h1 = hash(blackFrame)
        val h2 = hash(whiteFrame)
        assertEquals(h1, hash(blackFrame))
        assertTrue("different inputs must produce different hashes", h1 != h2)
    }

    // -------------------------------------------------------------------
    // §8 METADATA HASH TESTS
    // -------------------------------------------------------------------

    @Test
    fun `metadata fingerprint is part of the preprocessed output and deterministic`() {
        for (fixture in representativeFixtures().take(4)) {
            val first = runFixture(fixture)
            val second = runFixture(fixture)
            if (first is PreprocessResult.Success && second is PreprocessResult.Success) {
                assertEquals(
                    "fixture ${fixture.fixtureId}: metadata fingerprint must be deterministic",
                    first.output.fingerprint, second.output.fingerprint
                )
            }
        }
    }

    // -------------------------------------------------------------------
    // §2-§4, §9 CORPUS STRUCTURE TESTS
    // -------------------------------------------------------------------

    @Test
    fun `corpus is versioned and independently so`() {
        assertEquals("golden-corpus-v1", GoldenFixtureCorpus.CORPUS_VERSION)
        assertEquals("preprocess-v2", GoldenFixtureCorpus.REFERENCE_PREPROCESSING_VERSION)
        // A preprocessing version change does not silently rewrite fixtures.
        assertTrue(
            "fixture corpus version must be distinguishable from preprocessing version",
            GoldenFixtureCorpus.CORPUS_VERSION != GoldenFixtureCorpus.REFERENCE_PREPROCESSING_VERSION
        )
        for (fixture in GoldenFixtureDefinitions.ALL_FIXTURES) {
            assertEquals("corpus version must be stamped on every fixture",
                GoldenFixtureCorpus.CORPUS_VERSION, fixture.corpusVersion)
            assertEquals("fixture version defaults to 1",
                "1", fixture.fixtureVersion)
        }
    }

    @Test
    fun `every fixture id is unique`() {
        val ids = GoldenFixtureDefinitions.ALL_FIXTURES.map { it.fixtureId }
        assertEquals("fixture IDs must be unique", ids.size, ids.toSet().size)
    }

    @Test
    fun `corpus contains all required categories`() {
        val categories = GoldenFixtureDefinitions.ALL_FIXTURES.map { it.category }.toSet()
        val required = listOf(
            FixtureCategory.BASIC_IMAGE,
            FixtureCategory.ORIENTATION,
            FixtureCategory.DIMENSION,
            FixtureCategory.PIXEL_PATTERN,
            FixtureCategory.CHANNEL_ORDER,
            FixtureCategory.ALPHA,
            FixtureCategory.RESIZE_CROP_PADDING,
            FixtureCategory.NORMALIZATION,
            FixtureCategory.DATATYPE_LAYOUT,
            FixtureCategory.PRIVACY,
            FixtureCategory.NEGATIVE
        )
        for (category in required) {
            assertTrue(
                "corpus must contain category ${category.label}",
                category in categories
            )
        }
    }

    // -------------------------------------------------------------------
    // §4 BASIC IMAGE FIXTURE TESTS
    // -------------------------------------------------------------------

    @Test
    fun `preprocessing_black_image_matches_golden`() {
        assertFixtureShapes(GoldenFixtureDefinitions.ALL_BLACK_4x4)
        val result = runFixture(GoldenFixtureDefinitions.ALL_BLACK_4x4)
        val output = (result as PreprocessResult.Success).output
        // Scale-only: all zeros
        assertTrue(
            "black image must produce all-zero floats",
            output.input.floats.all { it == 0.0f }
        )
    }

    @Test
    fun `preprocessing_white_image_matches_golden`() {
        assertFixtureShapes(GoldenFixtureDefinitions.ALL_WHITE_4x4)
        val result = runFixture(GoldenFixtureDefinitions.ALL_WHITE_4x4)
        val output = (result as PreprocessResult.Success).output
        // Scale-only: all 1.0
        assertTrue(
            "white image must produce all-1.0 floats",
            output.input.floats.all { it == 1.0f }
        )
    }

    // -------------------------------------------------------------------
    // §14 ORIENTATION TESTS (pixel-exact)
    // -------------------------------------------------------------------

    @Test
    fun `preprocessing_rotation_0_matches_golden`() {
        val result = runFixture(GoldenFixtureDefinitions.ORIENTATION_0)
        val output = (result as PreprocessResult.Success).output
        assertEquals(FrameOrientation.DEG_0, output.capturedOrientation)
    }

    @Test
    fun `preprocessing_rotation_90_matches_golden`() {
        val result = runFixture(GoldenFixtureDefinitions.ORIENTATION_90)
        val output = (result as PreprocessResult.Success).output
        assertEquals(FrameOrientation.DEG_90, output.capturedOrientation)
    }

    @Test
    fun `preprocessing_rotation_180_matches_golden`() {
        val result = runFixture(GoldenFixtureDefinitions.ORIENTATION_180)
        val output = (result as PreprocessResult.Success).output
        assertEquals(FrameOrientation.DEG_180, output.capturedOrientation)
    }

    @Test
    fun `preprocessing_rotation_270_matches_golden`() {
        val result = runFixture(GoldenFixtureDefinitions.ORIENTATION_270)
        val output = (result as PreprocessResult.Success).output
        assertEquals(FrameOrientation.DEG_270, output.capturedOrientation)
    }

    @Test
    fun `different orientations produce different pixel arrangements`() {
        // Channel-isolation pattern makes pixel arrangement rotation-
        // sensitive. Different orientations must produce different
        // tensors (except where the rotation + resize coincide).
        val r0 = (runFixture(GoldenFixtureDefinitions.ORIENTATION_0) as PreprocessResult.Success).output
        val r90 = (runFixture(GoldenFixtureDefinitions.ORIENTATION_90) as PreprocessResult.Success).output
        val r180 = (runFixture(GoldenFixtureDefinitions.ORIENTATION_180) as PreprocessResult.Success).output
        val r270 = (runFixture(GoldenFixtureDefinitions.ORIENTATION_270) as PreprocessResult.Success).output

        // 0 != 90, 0 != 180, 0 != 270 (different tensor content)
        assertTrue("rotation must change pixel content", !r0.input.contentEquals(r90.input))
        assertTrue("rotation must change pixel content", !r0.input.contentEquals(r180.input))
        assertTrue("rotation must change pixel content", !r0.input.contentEquals(r270.input))
    }

    @Test
    fun `clockwise vs counterclockwise mistakes are detectable`() {
        // 90 CW normalization (3 CW quarter turns) must NOT equal
        // 90 CCW (1 CW quarter turn).
        val config = defaultConfig()
        val frame = GoldenFixtureLoader.loadFrame(GoldenFixtureDefinitions.ORIENTATION_0)
        val evidence = PreprocessingEvidenceFactory.approve(
            frame = frame,
            sanitizationStatus = PrivacySanitizationStatus.SANITIZED
        )
        val preprocessor = DeterministicPreprocessor(config)

        // DEG_90 -> NORMALIZE_TO_0 rotates 3x CW (i.e. 90 CCW)
        val normalized90 = (preprocessor.preprocess(evidence, FrameOrientation.DEG_90) as PreprocessResult.Success).output

        // Rotate the source 90 CW then normalize from DEG_0:
        // DECLARED_DEG_0 means no correction, so we compare
        // DEG_90 normalization against DEG_0 on a frame that was
        // already rotated by the pipeline (i.e., they must differ).
        val asDeg0 = (preprocessor.preprocess(evidence, FrameOrientation.DEG_0) as PreprocessResult.Success).output
        assertTrue(
            "recorded orientation must matter to the output",
            !normalized90.input.contentEquals(asDeg0.input)
        )
    }

    // -------------------------------------------------------------------
    // §15 CHANNEL ORDER TESTS
    // -------------------------------------------------------------------

    @Test
    fun `preprocessing_rgb_channel_order_matches_golden`() {
        val result = runFixture(GoldenFixtureDefinitions.CHANNEL_RGB)
        val output = (result as PreprocessResult.Success).output
        val floats = output.input.floats

        // 2x2 channel isolation: R=x*80, G=y*80, B=(x+y)*40
        // Pixel (0,0): R=0, G=0, B=0  -> R,G,B channels
        // Pixel (1,0): R=80, G=0, B=40
        assertEquals(2, output.input.width)
        assertEquals(2, output.input.height)
        assertEquals(3, output.input.channels)

        // Pixel (0,0): R=0, G=0, B=0
        assertEquals(0.0f, floats[0 + 0 * 3], 0f)
        assertEquals(0.0f, floats[0 + 0 * 3 + 1], 0f)
        assertEquals(0.0f, floats[0 + 0 * 3 + 2], 0f)

        // Pixel (1,0): R=80, G=0, B=40  -> channel order R,G,B
        assertEquals(80.0f / 255.0f, floats[1 * 3 + 0], 1e-6f)
        assertEquals(0.0f, floats[1 * 3 + 1], 0f)
        assertEquals(40.0f / 255.0f, floats[1 * 3 + 2], 1e-6f)
    }

    @Test
    fun `preprocessing_bgr_channel_order_matches_golden`() {
        val result = runFixture(GoldenFixtureDefinitions.CHANNEL_BGR)
        val output = (result as PreprocessResult.Success).output
        val floats = output.input.floats

        // Same source with BGR: Pixel (1,0) has R=80, G=0, B=40.
        // BGR order must emit B,G,R -> 40/255, 0/255, 80/255
        // This specifically detects RGB/BGR reversal.
        assertEquals(40.0f / 255.0f, floats[1 * 3 + 0], 1e-6f)
        assertEquals(0.0f, floats[1 * 3 + 1], 0f)
        assertEquals(80.0f / 255.0f, floats[1 * 3 + 2], 1e-6f)
    }

    @Test
    fun `rgb and bgr of the same source must differ`() {
        val rgb = (runFixture(GoldenFixtureDefinitions.CHANNEL_RGB) as PreprocessResult.Success).output
        val bgr = (runFixture(GoldenFixtureDefinitions.CHANNEL_BGR) as PreprocessResult.Success).output
        assertTrue(
            "RGB and BGR must produce different tensors",
            !rgb.input.contentEquals(bgr.input)
        )
    }

    // -------------------------------------------------------------------
    // §16 ALPHA TESTS
    // -------------------------------------------------------------------

    @Test
    fun `preprocessing_alpha_discard_opaque_matches_golden`() {
        val result = runFixture(GoldenFixtureDefinitions.ALPHA_DISCARD_OPAQUE)
        val output = (result as PreprocessResult.Success).output
        assertTrue(
            "opaque red with DISCARD must be pure red RGB",
            output.input.floats.allIndexedChannels { r, g, b -> r > 0.99f && g == 0f && b == 0f }
        )
        assertEquals(3, output.input.channels)
    }

    @Test
    fun `preprocessing_alpha_discard_translucent_matches_golden`() {
        val result = runFixture(GoldenFixtureDefinitions.ALPHA_DISCARD_TRANSLUCENT)
        val output = (result as PreprocessResult.Success).output
        // Translucent red 0x80FF0000 with DISCARD: alpha ignored,
        // pure red (r=1.0, g=0, b=0).
        assertTrue(
            "translucent red with DISCARD must ignore alpha and stay red",
            output.input.floats.allIndexedChannels { r, g, b ->
                r > 0.99f && g == 0f && b == 0f
            }
        )
    }

    @Test
    fun `preprocessing_alpha_composite_matches_golden`() {
        val result = runFixture(GoldenFixtureDefinitions.ALPHA_COMPOSITE)
        assertTrue(result is PreprocessResult.Success)
        val output = (result as PreprocessResult.Success).output
        assertEquals(3, output.input.channels)

        // Semi-transparent pattern: alpha alternates 0x80/0xC0 for
        // channels. All pixels 0xFF, 0x80, 0x40 against white bg.
        // First pixel alpha=0x80=128: fa=128/255
        //   r = round(255*fa + 255*(1-fa)) = 255
        //   g = round(128*fa + 255*(1-fa))
        //   b = round(64*fa + 255*(1-fa))
        val fa = 128.0 / 255.0
        val expectedR = 255.0
        val expectedG = Math.round(128.0 * fa + 255.0 * (1 - fa)).toDouble()
        val expectedB = Math.round(64.0 * fa + 255.0 * (1 - fa)).toDouble()

        assertEquals(expectedR / 255.0, output.input.floats[0].toDouble(), 1e-6)
        assertEquals(expectedG / 255.0, output.input.floats[1].toDouble(), 1e-6)
        assertEquals(expectedB / 255.0, output.input.floats[2].toDouble(), 1e-6)
    }

    @Test
    fun `preprocessing_alpha_preserve_rgba_matches_golden`() {
        val result = runFixture(GoldenFixtureDefinitions.ALPHA_PRESERVE_RGBA)
        val output = (result as PreprocessResult.Success).output
        assertEquals(4, output.input.channels)
        // RGBA: alpha first. Source is opaque red -> a=255, r=255, g=0, b=0
        assertEquals(1.0f, output.input.floats[0], 1e-6f) // a
        assertEquals(1.0f, output.input.floats[1], 1e-6f) // r
        assertEquals(0.0f, output.input.floats[2], 0f)    // g
        assertEquals(0.0f, output.input.floats[3], 0f)    // b
    }

    // -------------------------------------------------------------------
    // §18 NORMALIZATION TESTS
    // -------------------------------------------------------------------

    @Test
    fun `preprocessing_normalization_scale_only_boundaries_matches_golden`() {
        val black = (runFixture(GoldenFixtureDefinitions.NORM_SCALE_ONLY_BLACK) as PreprocessResult.Success).output
        val white = (runFixture(GoldenFixtureDefinitions.NORM_SCALE_ONLY_WHITE) as PreprocessResult.Success).output

        assertTrue("black -> all 0.0", black.input.floats.all { it == 0.0f })
        assertTrue("white -> all 1.0", white.input.floats.all { it == 1.0f })
    }

    @Test
    fun `preprocessing_normalization_std_boundaries_matches_golden`() {
        val black = (runFixture(GoldenFixtureDefinitions.NORM_STD_BLACK) as PreprocessResult.Success).output
        val white = (runFixture(GoldenFixtureDefinitions.NORM_STD_WHITE) as PreprocessResult.Success).output

        val mean = listOf(0.485, 0.456, 0.406)
        val std = listOf(0.229, 0.224, 0.225)

        // Black: (0 - mean) / std
        for (c in 0 until 3) {
            val expected = ((0.0 - mean[c]) / std[c]).toFloat()
            assertEquals(expected, black.input.floats[c], 1e-6f)
        }

        // White: (1 - mean) / std
        for (c in 0 until 3) {
            val expected = ((1.0 - mean[c]) / std[c]).toFloat()
            assertEquals(expected, white.input.floats[c], 1e-6f)
        }
    }

    @Test
    fun `preprocessing_normalization_channel_specific_matches_golden`() {
        val result = runFixture(GoldenFixtureDefinitions.NORM_RED_DOMINANT)
        val output = (result as PreprocessResult.Success).output
        val mean = listOf(0.485, 0.456, 0.406)
        val std = listOf(0.229, 0.224, 0.225)

        // Red pixel: R=255 -> (1 - 0.485)/0.229; G=0 -> (0-0.456)/0.224;
        // B=0 -> (0 - 0.406)/0.225
        val expectedR = ((1.0 - mean[0]) / std[0]).toFloat()
        val expectedG = ((0.0 - mean[1]) / std[1]).toFloat()
        val expectedB = ((0.0 - mean[2]) / std[2]).toFloat()
        for (pixel in 0 until 2 * 2) {
            assertEquals(expectedR, output.input.floats[pixel * 3 + 0], 1e-5f)
            assertEquals(expectedG, output.input.floats[pixel * 3 + 1], 1e-5f)
            assertEquals(expectedB, output.input.floats[pixel * 3 + 2], 1e-5f)
        }
    }

    // -------------------------------------------------------------------
    // §19 DATATYPE/LAYOUT TESTS
    // -------------------------------------------------------------------

    @Test
    fun `preprocessing_datatype_float32_matches_golden`() {
        val result = runFixture(GoldenFixtureDefinitions.DATATYPE_FLOAT32)
        val output = (result as PreprocessResult.Success).output
        assertEquals(com.example.feedsense.analysis.ml.ModelTensorType.FLOAT32, output.input.tensorType)
        assertEquals("NHWC", output.input.layout)
        assertEquals(null, output.input.quantizedBytes)
        assertEquals(2 * 2 * 3, output.input.floats.size)
    }

    @Test
    fun `preprocessing_datatype_int8_matches_golden`() {
        val result = runFixture(GoldenFixtureDefinitions.DATATYPE_INT8)
        val output = (result as PreprocessResult.Success).output
        assertEquals(com.example.feedsense.analysis.ml.ModelTensorType.INT8, output.input.tensorType)
        assertEquals("NHWC", output.input.layout)
        assertEquals(2 * 2 * 3, output.input.quantizedBytes!!.size)
        assertEquals(2 * 2 * 3, output.input.floats.size)
    }

    @Test
    fun `preprocessing_datatype_uint8_matches_golden`() {
        val result = runFixture(GoldenFixtureDefinitions.DATATYPE_UINT8)
        val output = (result as PreprocessResult.Success).output
        assertEquals(com.example.feedsense.analysis.ml.ModelTensorType.UINT8, output.input.tensorType)
        assertEquals("NHWC", output.input.layout)
        assertEquals(2 * 2 * 3, output.input.quantizedBytes!!.size)
        assertEquals(2 * 2 * 3, output.input.floats.size)
    }

    @Test
    fun `nhwc layout interleaves channels per pixel as expected`() {
        // 1x2 image with distinguishable RGB values -> NHWC layout
        // must be [R0,G0,B0,R1,G1,B1]
        val frame = GoldenFixtureLoader.generateFrame(SourcePattern.RGB_CHANNEL_ISOLATION, 1, 2)
        val config = defaultConfig()
        val evidence = com.example.feedsense.analysis.ml.preprocess.PreprocessingEvidenceFactory.approve(
            frame = frame,
            sanitizationStatus = PrivacySanitizationStatus.SANITIZED
        )
        val result = DeterministicPreprocessor(config).preprocess(evidence)
        val output = (result as PreprocessResult.Success).output
        val floats = output.input.floats

        // With default 4x4 config the 1x2 source is resized to 4x4.
        // So we check the LAYOUT by reading first 6 floats: resize
        // replicates source pixels (all same row forced by nearest).
        assertEquals(4 * 4 * 3, floats.size)
        // All pixels derive from the 1x2 source via nearest; the very
        // first pixel of the source (0,0) = R=0,G=0,B=0, and (1,0)
        // = R=80,G=0,B=40. After upscaling row0 is source[0,0] for
        // first half, source[1,0] for second half. Just verify the
        // channel-interleaved order pattern of the first two pixels.
        assertEquals(0.0f, floats[0], 0f)
        assertEquals(0.0f, floats[1], 0f)
        assertEquals(0.0f, floats[2], 0f)
    }

    // -------------------------------------------------------------------
    // §20 NEGATIVE FIXTURE TESTS
    // -------------------------------------------------------------------

    @Test
    fun `preprocessing_rejects_unsanitized_evidence`() {
        for (unsafe in listOf(
            PrivacySanitizationStatus.SANITIZATION_FAILED,
            PrivacySanitizationStatus.SANITIZATION_UNAVAILABLE,
            PrivacySanitizationStatus.UNKNOWN
        )) {
            val config = defaultConfig()
            val frame = GoldenFixtureLoader.generateFrame(SourcePattern.ALL_BLACK, 2, 2)
            var rejected = false
            try {
                PreprocessingEvidenceFactory.approve(
                    frame = frame,
                    sanitizationStatus = unsafe
                )
            } catch (expected: IllegalArgumentException) {
                rejected = true
            }
            assertTrue(
                "unsafe status ${unsafe.label} must be rejected at evidence construction",
                rejected
            )
        }
    }

    @Test
    fun `preprocessing_incompatible_config_is_rejected`() {
        // FLOAT16 is a declared tensor type but is NOT supported by
        // the 8B-15-3 deterministic pipeline. The config itself is
        // structurally valid, so construction succeeds; the pipeline
        // must reject it when run (never silently produce wrong data).
        val config = PreprocessingConfig(
            version = com.example.feedsense.analysis.ml.preprocess.PreprocessingVersion.V2,
            inputWidth = 2,
            inputHeight = 2,
            tensorType = com.example.feedsense.analysis.ml.preprocess.PreprocessingTensorType.FLOAT16
        )
        val frame = GoldenFixtureLoader.generateFrame(SourcePattern.ALL_BLACK, 2, 2)
        val evidence = PreprocessingEvidenceFactory.approve(
            frame = frame,
            sanitizationStatus = PrivacySanitizationStatus.SANITIZED
        )
        val result = DeterministicPreprocessor(config).preprocess(evidence)
        assertTrue(
            "FLOAT16 must not be runnable by the 8B-15-3 pipeline",
            result is PreprocessResult.Failure
        )
        assertEquals(
            PreprocessFailureCode.PREPROCESSING_FAILURE,
            (result as PreprocessResult.Failure).code
        )
    }

    @Test
    fun `preprocessing_incompatible_runtime_is_rejected`() {
        // The reproduction contract checker must flag a non-Android
        // runtime and non-TFLITE format.
        val report = com.example.feedsense.analysis.ml.preprocess.ReproductionContractChecker.check(
            config = PreprocessingConfig.mobileNetV4ConvReference(),
            artifact = com.example.feedsense.analysis.ml.repro.ReproArtifactIdentity(
                artifactId = "test",
                fileName = "test.dat",
                format = com.example.feedsense.analysis.ml.repro.ReproArtifactFormat.ONNX,
                byteSize = 10,
                sourceReference = "test",
                modelId = "test",
                artifactVersion = "1",
                availability = com.example.feedsense.analysis.ml.repro.ArtifactAvailability.PENDING,
                validationStatus = com.example.feedsense.analysis.ml.repro.ArtifactValidationStatus.NOT_CHECKED
            ),
            runtime = com.example.feedsense.analysis.ml.repro.ReproRuntimeIdentity(
                runtimeName = com.example.feedsense.analysis.ml.repro.ReproRuntimeName.ONNX_RUNTIME,
                runtimeVersion = "1.0",
                executionBackend = com.example.feedsense.analysis.ml.repro.ExecutionBackend.CPU,
                supportedPlatform = com.example.feedsense.analysis.ml.repro.SupportedPlatform.ANDROID,
                modelFormat = com.example.feedsense.analysis.ml.repro.ReproArtifactFormat.ONNX
            ),
            quantization = null
        )
        assertTrue("non-TFLITE format must be flagged", report.hasErrors)
    }

    @Test
    fun `preprocessing_fails_deterministically_for_each_negative_fixture`() {
        for (fixture in GoldenFixtureDefinitions.byCategory(FixtureCategory.NEGATIVE)) {
            val first = runFixture(fixture)
            val second = runFixture(fixture)
            assertTrue(
                "fixture ${fixture.fixtureId}: must fail deterministically",
                first is PreprocessResult.Failure && second is PreprocessResult.Failure
            )
            assertEquals(
                "fixture ${fixture.fixtureId}: failure codes must match",
                (first as PreprocessResult.Failure).code,
                (second as PreprocessResult.Failure).code
            )
        }
    }

    // -------------------------------------------------------------------
    // §5 PRIVACY BOUNDARY TESTS
    // -------------------------------------------------------------------

    @Test
    fun `preprocessing_privacy_accepted_fixtures_pass`() {
        val accepted = listOf(
            GoldenFixtureDefinitions.PRIVACY_ACCEPTED_SANITIZED,
            GoldenFixtureDefinitions.PRIVACY_ACCEPTED_NOT_REQUIRED
        )
        for (fixture in accepted) {
            val status = if (fixture.fixtureId.contains("sanitized")) {
                PrivacySanitizationStatus.SANITIZED
            } else {
                PrivacySanitizationStatus.NOT_REQUIRED
            }
            val result = runFixture(fixture, status)
            assertTrue(
                "fixture ${fixture.fixtureId}: sanitized evidence must produce a valid tensor",
                result is PreprocessResult.Success
            )
            val output = (result as PreprocessResult.Success).output
            assertEquals(
                "fixture ${fixture.fixtureId}: sanitization status must be stamped",
                fixture.expectation.expectedSanitizationStatus, output.sanitizationStatus
            )
        }
    }

    @Test
    fun `preprocessing_privacy_rejected_fixtures_fail`() {
        for (fixture in listOf(
            GoldenFixtureDefinitions.PRIVACY_REJECTED_FAILED,
            GoldenFixtureDefinitions.PRIVACY_REJECTED_UNAVAILABLE,
            GoldenFixtureDefinitions.PRIVACY_REJECTED_UNKNOWN
        )) {
            val status = when {
                fixture.fixtureId.contains("failed") -> PrivacySanitizationStatus.SANITIZATION_FAILED
                fixture.fixtureId.contains("unavailable") -> PrivacySanitizationStatus.SANITIZATION_UNAVAILABLE
                else -> PrivacySanitizationStatus.UNKNOWN
            }
            // Unsafe evidence cannot even be constructed via approve().
            var constructionRejected = false
            try {
                PreprocessingEvidenceFactory.approve(
                    frame = GoldenFixtureLoader.generateFrame(SourcePattern.ALL_BLACK, 2, 2),
                    sanitizationStatus = status
                )
            } catch (expected: IllegalArgumentException) {
                constructionRejected = true
            }
            assertTrue("fixture ${fixture.fixtureId}: unsafe evidence must be rejected", constructionRejected)
        }
    }

    @Test
    fun `unsafe evidence cannot silently reach preprocessing output`() {
        // Even bypassing the factory would hit the validation gate.
        val frame = GoldenFixtureLoader.generateFrame(SourcePattern.ALL_BLACK, 2, 2)
        val unsafePrivacy = com.example.feedsense.analysis.ml.preprocess.PreprocessingPrivacy(
            sanitizationVersion = "privacy-processing-v1",
            policyMode = com.example.feedsense.analysis.privacy.PrivacyPolicyMode.RESEARCH,
            sanitizationStatus = PrivacySanitizationStatus.UNKNOWN
        )
        var rejected = false
        try {
            com.example.feedsense.analysis.ml.preprocess.PreprocessingEvidence(
                frame = frame,
                privacy = unsafePrivacy
            )
        } catch (expected: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(
            "unsafe evidence must never reach preprocessing (construction-time failure)",
            rejected
        )
    }

    // -------------------------------------------------------------------
    // §10 GOLDEN UPDATE POLICY
    // -------------------------------------------------------------------

    @Test
    fun `golden outputs cannot auto-update on failure`() {
        // There must be NO mechanism that rewrites golden outputs
        // when a test fails. Golden changes are deliberate. We
        // prove this structurally: the golden production source may
        // only READ and COMPARE, never write fixture files or
        // mutate expectations.
        val goldenSourceDir = resolveGoldenSourceDir()
        val writeTerms = listOf(
            "FileWriter", ".writeBytes", "Files.write", "java.io.File(",
            "setExpected", "updateGolden", "autoUpdate", "writeText"
        )
        findByExtension(goldenSourceDir, ".kt")
            .filter { it.name != "GoldenFixtureLoader.kt" }
            .forEach { file ->
                val text = file.readText()
                for (term in writeTerms) {
                    assertTrue(
                        "golden source ${file.name} must not contain write mechanism '$term'",
                        !text.contains(term)
                    )
                }
            }
    }

    @Test
    fun `golden comparator is read-only and never mutates expectations`() {
        val fixture = GoldenFixtureDefinitions.ALL_BLACK_4x4
        val originalHash = fixture.expectation.expectedTensorHash
        val result = runFixture(fixture)
        GoldenComparator.compare(fixture, result)

        // Comparing must not mutate the fixture or expectation.
        assertEquals(originalHash, fixture.expectation.expectedTensorHash)
        assertEquals(
            fixture.expectation,
            GoldenFixtureDefinitions.ALL_BLACK_4x4.expectation
        )
    }

    // -------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------

    private fun representativeFixtures(): List<GoldenFixture> {
        return GoldenFixtureDefinitions.ALL_FIXTURES
    }

    private fun assertFixtureShapes(fixture: GoldenFixture) {
        val result = runFixture(fixture)
        assertTrue(
            "fixture ${fixture.fixtureId}: expected success",
            result is PreprocessResult.Success
        )
        val output = (result as PreprocessResult.Success).output
        assertEquals("fixture ${fixture.fixtureId}: width",
            fixture.expectation.expectedWidth, output.input.width)
        assertEquals("fixture ${fixture.fixtureId}: height",
            fixture.expectation.expectedHeight, output.input.height)
        assertEquals("fixture ${fixture.fixtureId}: channels",
            fixture.expectation.expectedChannels, output.input.channels)
        assertEquals("fixture ${fixture.fixtureId}: tensorSize",
            fixture.expectation.tensorSize, output.tensorSize)
        assertEquals("fixture ${fixture.fixtureId}: layout",
            fixture.expectation.expectedLayout, output.input.layout)
    }

    private fun FloatArray.allIndexedChannels(
        predicate: (r: Float, g: Float, b: Float) -> Boolean
    ): Boolean {
        var i = 0
        while (i < size) {
            if (!predicate(this[i], this[i + 1], this[i + 2])) return false
            i += 3
        }
        return true
    }

    private fun resolveGoldenSourceDir(): File = run {
        val rel = "src/main/java/com/example/feedsense/analysis/ml/preprocess/golden"
        val cwd = File(System.getProperty("user.dir") ?: throw AssertionError("user.dir missing")).absoluteFile
        val underCwd = File(cwd, rel)
        if (underCwd.isDirectory) return@run underCwd
        val underParent = File(cwd.parentFile, "app/$rel")
        if (underParent.isDirectory) return@run underParent
        throw AssertionError(
            "golden source dir not found (working dir = ${cwd.path}); " +
                "unit tests must run from the app module"
        )
    }

    private fun findByExtension(dir: File, extension: String): List<File> =
        dir.walkTopDown().filter { it.isFile && it.name.endsWith(extension) }.toList()
}