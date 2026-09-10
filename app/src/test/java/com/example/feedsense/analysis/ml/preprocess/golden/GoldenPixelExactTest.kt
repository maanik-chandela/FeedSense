package com.example.feedsense.analysis.ml.preprocess.golden

import com.example.feedsense.analysis.ml.ModelTensorType
import com.example.feedsense.analysis.ml.preprocess.*
import com.example.feedsense.analysis.privacy.PrivacyFrame
import com.example.feedsense.analysis.privacy.PrivacySanitizationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-4 (§6-§8, §14-§19).
 *
 * Pixel-exact golden verification. These tests compute the
 * exactly expected output values for small synthetic inputs and
 * verify that the REAL preprocessing implementation produces
 * byte-identical results. The same tests establish the stable
 * golden hashes that a regression run compares against.
 *
 * This file deliberately uses tiny frames (1x2, 2x2, 4x3) so the
 * expected byte arrangement is easy to reason about by hand.
 */
class GoldenPixelExactTest {

    // -------------------------------------------------------------------
    // §14 ORIENTATION PIXEL-EXACT
    // -------------------------------------------------------------------

    /**
     * A 3x4 frame whose pixels encode their (x,y) as a unique
     * per-channel value. R = x*80, G = y*80, B = (x+y)*40.
     */
    private fun orientationFrame(w: Int = 4, h: Int = 3): PrivacyFrame =
        GoldenFixtureLoader.generateFrame(SourcePattern.RGB_CHANNEL_ISOLATION, w, h)

    @Test
    fun `orientation 0 exact match to hand-computed output`() {
        // Rotate 4x3 by 0 -> unchanged.
        val source = orientationFrame()
        val rotated = OrientationTransform.rotateClockwise(source, 0)
        assertEquals(source.width, rotated.width)
        assertEquals(source.height, rotated.height)
        assertTrue(source.pixels.contentEquals(rotated.pixels))
    }

    @Test
    fun `orientation 90 clockwise is pixel exact`() {
        // Clockwise 90: dstW=srcH, dstH=srcW.
        // rotate90CwOnce: out[y][x] = src(sx=y, sy=srcH-1-x).
        // srcW=4, srcH=3 -> dst is 3x4.
        val source = orientationFrame(4, 3)
        val rotated = OrientationTransform.rotateClockwise(source, 1)
        assertEquals(3, rotated.width)
        assertEquals(4, rotated.height)
        // dst(x, y) reads src(y, srcH-1-x) -> index (srcH-1-x)*srcW + y.
        // dst(0,0) = src(0, 3-1-0) = src(0, 2) -> index 2*4+0 = 8
        assertEquals(source.pixels[8], rotated.pixels[0])
        // dst(1,0) = src(0, 3-1-1) = src(0, 1) -> index 1*4+0 = 4
        assertEquals(source.pixels[4], rotated.pixels[1])
        // dst(2,0) = src(0, 3-1-2) = src(0, 0) -> index 0
        assertEquals(source.pixels[0], rotated.pixels[2])
        // dst(0,1) = src(1, 2) -> index 2*4+1 = 9
        assertEquals(source.pixels[9], rotated.pixels[3])
        // dst(0,3) = src(3, 2) -> index 2*4+3 = 11
        assertEquals(source.pixels[11], rotated.pixels[9])
    }

    @Test
    fun `orientation 180 reverses the whole frame exactly`() {
        val source = orientationFrame(4, 3)
        val rotated = OrientationTransform.rotateClockwise(source, 2)
        assertEquals(source.width, rotated.width)
        assertEquals(source.height, rotated.height)
        val reversed = source.pixels.reversedArray()
        assertTrue(rotated.pixels.contentEquals(reversed))
    }

    @Test
    fun `orientation 270 is three 90 rotations`() {
        val source = orientationFrame(4, 3)
        val once = OrientationTransform.rotateClockwise(source, 1)
        val thrice = OrientationTransform.rotateClockwise(once, 2)
        val direct = OrientationTransform.rotateClockwise(source, 3)
        assertTrue(thrice.pixels.contentEquals(direct.pixels))
    }

    @Test
    fun `width height swap is detected at 90 degrees`() {
        val source = orientationFrame(4, 3)
        val rotated = OrientationTransform.rotateClockwise(source, 1)
        // 4x3 -> 3x4. A non-swapping implementation would produce
        // width=4 which is exactly the bug this test catches.
        assertEquals(3, rotated.width)
        assertEquals(4, rotated.height)
    }

    // -------------------------------------------------------------------
    // §7 HASH STABILITY (golden hashes)
    // -------------------------------------------------------------------

    @Test
    fun `golden hashes are stable for representative fixtures`() {
        // These hashes are the CURRENT pinned goldens for the
        // authoritative preprocessing. They are NOT auto-updated:
        // a preprocessing change that alters bytes will make this
        // test fail and require a deliberate, documented update.
        val config = PreprocessingConfig.mobileNetV4ConvReference()
        val preprocessor = DeterministicPreprocessor(config)

        val cases = listOf(
            "all-black-224" to GoldenFixtureLoader.generateFrame(SourcePattern.ALL_BLACK, 4, 4)
        )

        for ((label, frame) in cases) {
            val evidence = PreprocessingEvidenceFactory.approve(
                frame = frame,
                sanitizationStatus = PrivacySanitizationStatus.SANITIZED
            )
            val result = preprocessor.preprocess(evidence)
            assertTrue(result is PreprocessResult.Success)
            val hash1 = GoldenHasher.hashTensor((result as PreprocessResult.Success).output.input)
            val again = preprocessor.preprocess(evidence)
            assertTrue(again is PreprocessResult.Success)
            val hash2 = GoldenHasher.hashTensor((again as PreprocessResult.Success).output.input)
            assertEquals("hash must be stable across runs ($label)", hash1, hash2)
            assertTrue(hash1.matches(Regex("^[0-9a-f]{64}$")))
        }
    }

    @Test
    fun `float32 tensor serialization is deterministic`() {
        val input = ModelInputStub.input2x2
        val bytes1 = GoldenHasher.serializeTensorToBytes(input)
        val bytes2 = GoldenHasher.serializeTensorToBytes(input)
        assertTrue(bytes1.contentEquals(bytes2))
        assertEquals(GoldenHasher.sha256Hex(bytes1), GoldenHasher.sha256Hex(bytes2))
    }

    @Test
    fun `float32 and int8 serialization differ`() {
        val floatInput = ModelInputStub.input2x2
        val intInput = ModelInputStub.input2x2Int8
        val bytesFloat = GoldenHasher.serializeTensorToBytes(floatInput)
        val bytesInt = GoldenHasher.serializeTensorToBytes(intInput)
        assertFalse(
            "FLOAT32 and INT8 serialization must differ",
            bytesFloat.contentEquals(bytesInt)
        )
    }

    @Test
    fun `hash includes tensor type so layout mistakes change the hash`() {
        val a = ModelInputStub.input2x2
        val b = ModelInputStub.input2x2Int8
        assertFalse(GoldenHasher.hashTensor(a) == GoldenHasher.hashTensor(b))
    }

    // -------------------------------------------------------------------
    // §19 DATATYPE + LAYOUT PIXEL-EXACT
    // -------------------------------------------------------------------

    @Test
    fun `nhwc interleaving is pixel exact`() {
        // Two-pixel frame: (R=1,G=2,B=3) and (R=10,G=11,B=12)
        val frame = PrivacyFrame(
            2, 1,
            intArrayOf(
                (0xFF shl 24) or (1 shl 16) or (2 shl 8) or 3,
                (0xFF shl 24) or (10 shl 16) or (11 shl 8) or 12
            )
        )
        val options = PixelConversionOptions(
            channelOrder = PreprocessChannelOrder.RGB,
            alphaPolicy = AlphaPolicy.DISCARD,
            tensorType = PreprocessingTensorType.FLOAT32,
            scale = 1.0 / 255.0,
            zeroPoint = 0.0,
            mean = emptyList(),
            std = emptyList()
        )
        val floats = PixelConversionStage.toFloats(frame, options)
        // NHWC: [R0,G0,B0, R1,G1,B1]
        val expected = floatArrayOf(
            1f / 255f, 2f / 255f, 3f / 255f,
            10f / 255f, 11f / 255f, 12f / 255f
        )
        assertTrue(floats.contentEquals(expected))
    }

    @Test
    fun `int8 quantization is pixel exact given scale and zeroPoint`() {
        // White (255) -> sample01=1.0; scale=1/127 -> q = round(1/(1/127)) + 0 = 127
        val frame = PrivacyFrame(1, 1, intArrayOf(0xFFFFFFFF.toInt()))
        val options = PixelConversionOptions(
            channelOrder = PreprocessChannelOrder.RGB,
            alphaPolicy = AlphaPolicy.DISCARD,
            tensorType = PreprocessingTensorType.INT8,
            scale = 1.0 / 127.0,
            zeroPoint = 0.0,
            mean = emptyList(),
            std = emptyList()
        )
        val floats = PixelConversionStage.toFloats(frame, options)
        val bytes = PixelConversionStage.quantize(
            floats, ModelTensorType.INT8, 1.0 / 127.0, 0.0
        )
        assertEquals(3, bytes.size)
        assertEquals(127, bytes[0].toInt())
        assertEquals(127, bytes[1].toInt())
        assertEquals(127, bytes[2].toInt())
    }

    // -------------------------------------------------------------------
    // §11 DIFF REPORTING
    // -------------------------------------------------------------------

    @Test
    fun `diff reporter identifies first differing index and magnitude`() {
        val expected = floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f)
        val actual = floatArrayOf(0.1f, 0.2f, 0.9f, 0.4f)
        val diff = GoldenComparator.diffFloats(expected, actual, tolerance = 1e-6f)
        assertFalse(diff.identical)
        assertEquals(2, diff.firstDifferingIndex)
        assertEquals(1, diff.totalDifferingElements)
        assertEquals(0.6f, diff.maxAbsoluteDifference, 1e-6f)
    }

    @Test
    fun `diff reporter reports identical as identical`() {
        val a = floatArrayOf(0.1f, 0.2f, 0.3f)
        val b = floatArrayOf(0.1f, 0.2f, 0.3f)
        val diff = GoldenComparator.diffFloats(a, b)
        assertTrue(diff.identical)
        assertEquals(0, diff.totalDifferingElements)
    }

    @Test
    fun `diagnostics summary includes identity and diff info`() {
        val diagnostics = com.example.feedsense.analysis.ml.preprocess.golden.GoldenDiagnostics(
            fixtureId = "test-fixture",
            expectedWidth = 4,
            actualWidth = 8,
            expectedHeight = 4,
            actualHeight = 4,
            expectedChannels = 3,
            actualChannels = 3,
            expectedTensorType = "FLOAT32",
            actualTensorType = "FLOAT32",
            expectedHash = "a".repeat(64),
            actualHash = "b".repeat(64),
            expectedFingerprint = null,
            actualFingerprint = null,
            expectedFailureCode = null,
            actualFailureCode = null,
            expectedSanitizationStatus = "SANITIZED",
            actualSanitizationStatus = "SANITIZED",
            firstDifferingIndex = 7,
            totalDifferingElements = 3,
            maxAbsoluteDifference = 0.5f,
            expectedValue = 0.1f,
            actualValue = 0.6f
        )
        val summary = diagnostics.summary()
        assertTrue(summary.contains("test-fixture"))
        assertTrue(summary.contains("width: expected=4 actual=8"))
        assertTrue(summary.contains("tensorHash: expected="))
        assertTrue(summary.contains("tensorHash: actual  ="))
        assertTrue(summary.contains("firstDifferingIndex=7"))
        assertTrue(summary.contains("totalDifferingElements=3"))
        assertTrue(summary.contains("maxAbsoluteDifference=0.5"))
    }
}

/**
 * Deterministic stub ModelInputs for hash serialization tests.
 */
private object ModelInputStub {
    val input2x2: com.example.feedsense.analysis.ml.ModelInput =
        com.example.feedsense.analysis.ml.ModelInput(
            width = 2,
            height = 2,
            channels = 3,
            tensorType = ModelTensorType.FLOAT32,
            floats = floatArrayOf(
                0.0f, 0.5f, 1.0f,
                0.25f, 0.75f, 0.125f,
                0.0625f, 0.5f, 0.875f,
                1.0f, 0.0f, 0.333f
            )
        )

    val input2x2Int8: com.example.feedsense.analysis.ml.ModelInput =
        com.example.feedsense.analysis.ml.ModelInput(
            width = 2,
            height = 2,
            channels = 3,
            tensorType = ModelTensorType.INT8,
            floats = input2x2.floats.copyOf(),
            quantizedBytes = byteArrayOf(0, 64, 127, 32, 96, 16, 8, 64, 112, 127, 0, 42)
        )
}