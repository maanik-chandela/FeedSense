package com.example.feedsense.analysis.ml.preprocess

import com.example.feedsense.analysis.ml.ModelTensorType
import com.example.feedsense.analysis.privacy.PrivacyFrame
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-3 (§20 pixel-level tests, §6-§15).
 *
 * Every test inspects actual output values/shapes - never just
 * "the pipeline completed". Tiny synthetic frames are used with
 * deliberately distinguishable per-channel values so orientation,
 * channel order and layout mistakes become obvious.
 *
 * Convention used throughout: frames built from an IntArray of
 * plain index values (0..n-1) exercise spatial transforms exactly;
 * ARGB-qualified values (0xFFA0B0C0 etc.) exercise color handling.
 */
class PreprocessingPixelTest {

    /*
     * Builds a 2x3 frame whose pixels are the flat index values
     * 0..5 (row0=[0,1], row1=[2,3], row2=[4,5]).
     */
    private fun counterFrame2x3(): PrivacyFrame =
        PrivacyFrame(2, 3, intArrayOf(0, 1, 2, 3, 4, 5))

    // --------------------------------
    // §6 ORIENTATION
    // --------------------------------

    @Test
    fun `rotateClockwise 90 turns source rows into columns`() {
        // Source (row-major): [0,1] / [2,3] / [4,5]
        // Clockwise 90 turns a (w=2,h=3) into (w=3,h=2):
        //   row0 = bottom row reversed      -> [4, 2, 0]
        //   row1 = middle, then top reversed-> [5, 3, 1]
        val rotated = OrientationTransform.rotateClockwise(
            counterFrame2x3(), quarterTurns = 1
        )
        assertEquals(3, rotated.width)
        assertEquals(2, rotated.height)
        assertArrayEquals(
            intArrayOf(4, 2, 0, 5, 3, 1),
            rotated.pixels
        )
    }

    @Test
    fun `rotateClockwise 180 reverses the whole frame`() {
        val rotated = OrientationTransform.rotateClockwise(
            counterFrame2x3(), quarterTurns = 2
        )
        assertEquals(2, rotated.width)
        assertEquals(3, rotated.height)
        assertArrayEquals(
            intArrayOf(5, 4, 3, 2, 1, 0),
            rotated.pixels
        )
    }

    @Test
    fun `rotateClockwise 270 equals three single rotations`() {
        val once = OrientationTransform.rotateClockwise(
            counterFrame2x3(), quarterTurns = 1
        )
        val twice = OrientationTransform.rotateClockwise(
            once, quarterTurns = 1
        )
        val thrice = OrientationTransform.rotateClockwise(
            twice, quarterTurns = 1
        )
        val direct = OrientationTransform.rotateClockwise(
            counterFrame2x3(), quarterTurns = 3
        )
        assertArrayEquals(thrice.pixels, direct.pixels)
    }

    @Test
    fun `rotateClockwise 0 returns a copy with identical pixels`() {
        val source = counterFrame2x3()
        val copy = OrientationTransform.rotateClockwise(source, 0)
        assertArrayEquals(source.pixels, copy.pixels)
        assertTrue(source.pixels !== copy.pixels)
    }

    @Test
    fun `orient normalize_to_0 is identity for DEG_0 frames`() {
        val source = counterFrame2x3()
        val upright = OrientationTransform.orient(
            source,
            FrameOrientation.DEG_0,
            OrientationPolicy.NORMALIZE_TO_0
        )
        assertArrayEquals(source.pixels, upright.pixels)
    }

    @Test
    fun `orient normalize_to_0 compensates a 90-clockwise capture`() {
        // A capture recorded as DEG_90 is rotated 90 CW vs upright;
        // normalizing must rotate it back (90 CCW = 270 CW).
        val source = counterFrame2x3()
        val normalized = OrientationTransform.orient(
            source,
            FrameOrientation.DEG_90,
            OrientationPolicy.NORMALIZE_TO_0
        )
        val expected = OrientationTransform.rotateClockwise(source, 3)
        assertArrayEquals(expected.pixels, normalized.pixels)
    }

    @Test
    fun `orient normalize_to_0 rotates DEG_180 back to upright`() {
        val source = counterFrame2x3()
        val normalized = OrientationTransform.orient(
            source,
            FrameOrientation.DEG_180,
            OrientationPolicy.NORMALIZE_TO_0
        )
        assertArrayEquals(
            intArrayOf(5, 4, 3, 2, 1, 0),
            normalized.pixels
        )
    }

    @Test
    fun `display rotation is never assumed to equal image rotation`() {
        // Same pixels, two different recorded orientations MUST
        // produce different normalized pixels - proving orientation
        // is consumed as explicit metadata, not assumed.
        val source = counterFrame2x3()
        val as0 = OrientationTransform.orient(
            source, FrameOrientation.DEG_0, OrientationPolicy.NORMALIZE_TO_0
        )
        val as270 = OrientationTransform.orient(
            source, FrameOrientation.DEG_270, OrientationPolicy.NORMALIZE_TO_0
        )
        assertTrue(!as0.pixels.contentEquals(as270.pixels))
    }

    // --------------------------------
    // §8 RESIZE
    // --------------------------------

    @Test
    fun `nearest resize samples integer floor coordinates`() {
        val source = PrivacyFrame(
            4, 4,
            IntArray(16) { it } // 0..15 row-major
        )
        val resized = SpatialTransform.resize(
            source, 2, 2, Interpolation.NEAREST
        )
        assertEquals(2, resized.width)
        assertEquals(2, resized.height)
        // srcY = y*4/2 = 2y ; srcX = x*4/2 = 2x
        assertArrayEquals(
            intArrayOf(0, 2, 8, 10),
            resized.pixels
        )
    }

    @Test
    fun `nearest resize of odd dimensions is deterministic`() {
        val source = PrivacyFrame(
            3, 3,
            IntArray(9) { it }
        )
        val first = SpatialTransform.resize(
            source, 2, 2, Interpolation.NEAREST
        )
        val second = SpatialTransform.resize(
            source, 2, 2, Interpolation.NEAREST
        )
        assertArrayEquals(first.pixels, second.pixels)
        assertEquals(4, first.pixels.size)
    }

    @Test
    fun `bilinear resize interpolates half-pixel centers`() {
        // Horizontal stripes: value = (x+1), constant per column.
        val source = PrivacyFrame(
            4, 4,
            intArrayOf(
                1, 2, 3, 4,
                1, 2, 3, 4,
                1, 2, 3, 4,
                1, 2, 3, 4
            )
        )
        val resized = SpatialTransform.resize(
            source, 2, 2, Interpolation.BILINEAR
        )
        // out(0,0): src x=0.5 -> blend(1,2)=1.5 -> round=2
        // out(1,0): src x=2.5 -> blend(3,4)=3.5 -> round=4
        assertArrayEquals(
            intArrayOf(2, 4, 2, 4),
            resized.pixels
        )
    }

    @Test
    fun `bilinear of a solid color preserves the color exactly`() {
        val source = PrivacyFrame(
            3, 3, IntArray(9) { 0xFF00FF00.toInt() }
        )
        val resized = SpatialTransform.resize(
            source, 5, 5, Interpolation.BILINEAR
        )
        assertEquals(25, resized.pixels.size)
        assertTrue(
            resized.pixels.all { it == 0xFF00FF00.toInt() }
        )
    }

    @Test
    fun `resize at target dimensions returns identical pixels`() {
        val source = PrivacyFrame(
            4, 4, IntArray(16) { it * 7 }
        )
        val resized = SpatialTransform.resize(
            source, 4, 4, Interpolation.NEAREST
        )
        assertArrayEquals(source.pixels, resized.pixels)
    }

    // --------------------------------
    // §9 CROP
    // --------------------------------

    @Test
    fun `crop extracts a deterministic window`() {
        val source = PrivacyFrame(
            4, 4, IntArray(16) { it }
        )
        val cropped = SpatialTransform.crop(source, 1, 1, 2, 2)
        assertEquals(2, cropped.width)
        assertEquals(2, cropped.height)
        assertArrayEquals(
            intArrayOf(5, 6, 9, 10),
            cropped.pixels
        )
    }

    @Test
    fun `crop window outside the frame is rejected`() {
        val source = PrivacyFrame(4, 4, IntArray(16))
        var rejected = false
        try {
            SpatialTransform.crop(source, 3, 3, 2, 2)
        } catch (expected: IllegalArgumentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun `center crop anchors the window deterministically`() {
        // Portrait source 2x4, target 2x2. Source aspect 0.5 < 1.0
        // so the window is full-width and height-limited.
        val source = PrivacyFrame(
            2, 4,
            intArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
        )
        val center = SpatialTransform.centerCropThenResize(
            source, 2, 2, Interpolation.NEAREST, CropAnchor.CENTER
        )
        // ch = srcW*targetH/targetW = 2*2/2 = 2 -> window rows 1..2,
        // values [2,3,4,5], resize 2x2 keeps them.
        assertArrayEquals(
            intArrayOf(2, 3, 4, 5),
            center.pixels
        )

        val top = SpatialTransform.centerCropThenResize(
            source, 2, 2, Interpolation.NEAREST, CropAnchor.TOP
        )
        assertArrayEquals(
            intArrayOf(0, 1, 2, 3),
            top.pixels
        )

        val bottom = SpatialTransform.centerCropThenResize(
            source, 2, 2, Interpolation.NEAREST, CropAnchor.BOTTOM
        )
        assertArrayEquals(
            intArrayOf(4, 5, 6, 7),
            bottom.pixels
        )
    }

    @Test
    fun `center crop of a wide source crops horizontally`() {
        // Wide source 4x2 (aspect 2.0), target 2x2 (aspect 1.0).
        // Window is full-height and width-limited: cw = 2*2/2 = 2.
        val source = PrivacyFrame(
            4, 2,
            intArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
        )
        val center = SpatialTransform.centerCropThenResize(
            source, 2, 2, Interpolation.NEAREST, CropAnchor.CENTER
        )
        // xOffset = (4-2)/2 = 1 -> columns 1..2 -> values [1,2,5,6]
        assertArrayEquals(
            intArrayOf(1, 2, 5, 6),
            center.pixels
        )
    }

    // --------------------------------
    // §9 PADDING
    // --------------------------------

    @Test
    fun `padToFit adds deterministic top and bottom bars`() {
        // Wide source 4x2 into a square 4x4 -> top/bottom bars.
        val source = PrivacyFrame(
            4, 2,
            intArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
        )
        val padded = SpatialTransform.padToFit(
            source, 4, 4, Interpolation.NEAREST, 0xFF000000.toInt()
        )
        val pad = 0xFF000000.toInt()
        assertArrayEquals(
            intArrayOf(
                pad, pad, pad, pad,
                0, 1, 2, 3,
                4, 5, 6, 7,
                pad, pad, pad, pad
            ),
            padded.pixels
        )
    }

    @Test
    fun `padToFit adds deterministic left and right bars`() {
        // Portrait source 2x4 into a square 4x4 -> left/right bars.
        val source = PrivacyFrame(
            2, 4,
            intArrayOf(0, 1, 2, 3, 4, 5, 6, 7)
        )
        val padded = SpatialTransform.padToFit(
            source, 4, 4, Interpolation.NEAREST, 0xFF000000.toInt()
        )
        val pad = 0xFF000000.toInt()
        assertArrayEquals(
            intArrayOf(
                pad, 0, 1, pad,
                pad, 2, 3, pad,
                pad, 4, 5, pad,
                pad, 6, 7, pad
            ),
            padded.pixels
        )
    }

    @Test
    fun `padToFit uses the configured padding value only on bars`() {
        // Source that exactly fits the target produces NO padding
        // pixels: the padding value must never contaminate content.
        val source = PrivacyFrame(2, 2, intArrayOf(0, 1, 2, 3))
        val paddingValue = 0xFF123456.toInt()
        val padded = SpatialTransform.padToFit(
            source, 4, 4, Interpolation.NEAREST, paddingValue
        )
        assertEquals(4, padded.width)
        assertEquals(4, padded.height)
        assertTrue(padded.pixels.none { it == paddingValue })
    }

    // --------------------------------
    // §10 COLOR / CHANNELS
    // --------------------------------

    private fun options(
        channelOrder: PreprocessChannelOrder,
        alphaPolicy: AlphaPolicy = AlphaPolicy.DISCARD,
        tensorType: PreprocessingTensorType = PreprocessingTensorType.FLOAT32
    ): PixelConversionOptions = PixelConversionOptions(
        channelOrder = channelOrder,
        alphaPolicy = alphaPolicy,
        tensorType = tensorType,
        scale = 1.0 / 255.0,
        zeroPoint = 0.0,
        mean = emptyList(),
        std = emptyList()
    )

    @Test
    fun `rgb extracts red green blue in model order`() {
        // ARGB pixel 0xFFA0B0C0 => R=0xA0 G=0xB0 B=0xC0.
        val frame = PrivacyFrame(1, 1, intArrayOf(0xFFA0B0C0.toInt()))
        val floats = PixelConversionStage.toFloats(frame, options(PreprocessChannelOrder.RGB))
        assertEquals(3, floats.size)
        assertEquals(0xA0 / 255.0f, floats[0], 1e-6f)
        assertEquals(0xB0 / 255.0f, floats[1], 1e-6f)
        assertEquals(0xC0 / 255.0f, floats[2], 1e-6f)
    }

    @Test
    fun `bgr reverses channel order`() {
        val frame = PrivacyFrame(1, 1, intArrayOf(0xFFA0B0C0.toInt()))
        val floats = PixelConversionStage.toFloats(frame, options(PreprocessChannelOrder.BGR))
        assertEquals(0xC0 / 255.0f, floats[0], 1e-6f)
        assertEquals(0xB0 / 255.0f, floats[1], 1e-6f)
        assertEquals(0xA0 / 255.0f, floats[2], 1e-6f)
    }

    @Test
    fun `rgba preserves alpha as the first channel`() {
        val frame = PrivacyFrame(1, 1, intArrayOf(0xFFA0B0C0.toInt()))
        val floats = PixelConversionStage.toFloats(
            frame,
            options(PreprocessChannelOrder.RGBA, alphaPolicy = AlphaPolicy.PRESERVE)
        )
        assertEquals(4, floats.size)
        assertEquals(0xFF / 255.0f, floats[0], 1e-6f) // a
        assertEquals(0xA0 / 255.0f, floats[1], 1e-6f) // r
        assertEquals(0xB0 / 255.0f, floats[2], 1e-6f) // g
        assertEquals(0xC0 / 255.0f, floats[3], 1e-6f) // b
    }

    @Test
    fun `grayscale uses rec601 luma`() {
        // 0.299*160 + 0.587*176 + 0.114*192 = 173.04 -> 173
        val frame = PrivacyFrame(1, 1, intArrayOf(0xFFA0B0C0.toInt()))
        val floats = PixelConversionStage.toFloats(
            frame, options(PreprocessChannelOrder.GRAYSCALE)
        )
        assertEquals(1, floats.size)
        assertEquals(173 / 255.0f, floats[0], 1e-6f)
    }

    // --------------------------------
    // §11 ALPHA
    // --------------------------------

    @Test
    fun `alpha discard ignores the alpha bytes`() {
        // Translucent red 0x80FF0000 with DISCARD must equal the
        // opaque red 0xFFFF0000 output.
        val translucent = PrivacyFrame(1, 1, intArrayOf(0x80FF0000.toInt()))
        val opaque = PrivacyFrame(1, 1, intArrayOf(0xFFFF0000.toInt()))
        val a = PixelConversionStage.toFloats(translucent, options(PreprocessChannelOrder.RGB))
        val b = PixelConversionStage.toFloats(opaque, options(PreprocessChannelOrder.RGB))
        assertArrayEquals(a, b, 0f)
    }

    @Test
    fun `alpha composite blends against the deterministic background`() {
        val composite = PixelConversionOptions(
            channelOrder = PreprocessChannelOrder.RGB,
            alphaPolicy = AlphaPolicy.COMPOSITE,
            tensorType = PreprocessingTensorType.FLOAT32,
            scale = 1.0 / 255.0,
            zeroPoint = 0.0,
            mean = emptyList(),
            std = emptyList(),
            compositeBackgroundArgb = 0xFFFFFFFF.toInt() // opaque white
        )
        // 0x80A0B0C0: alpha=128/255. r = round(160*fa + 255*(1-fa))
        val frame = PrivacyFrame(1, 1, intArrayOf(0x80A0B0C0.toInt()))
        val floats = PixelConversionStage.toFloats(frame, composite)
        val fa = 128.0 / 255.0
        val expectedR = Math.round(160.0 * fa + 255.0 * (1 - fa))
        val expectedG = Math.round(176.0 * fa + 255.0 * (1 - fa))
        val expectedB = Math.round(192.0 * fa + 255.0 * (1 - fa))
        assertEquals(expectedR / 255.0f, floats[0], 1e-6f)
        assertEquals(expectedG / 255.0f, floats[1], 1e-6f)
        assertEquals(expectedB / 255.0f, floats[2], 1e-6f)
    }

    // --------------------------------
    // §12 NORMALIZATION
    // --------------------------------

    @Test
    fun `scale-only maps byte range to zero-to-one`() {
        val frame = PrivacyFrame(1, 1, intArrayOf(0xFF0000FF.toInt())) // blue
        val floats = PixelConversionStage.toFloats(frame, options(PreprocessChannelOrder.RGB))
        assertEquals(0.0f, floats[0], 0f) // r
        assertEquals(0.0f, floats[1], 0f) // g
        assertEquals(1.0f, floats[2], 1e-6f) // b = 255/255
    }

    @Test
    fun `standardization applies per-channel mean and std to known pixels`() {
        val normalized = PixelConversionOptions(
            channelOrder = PreprocessChannelOrder.RGB,
            alphaPolicy = AlphaPolicy.DISCARD,
            tensorType = PreprocessingTensorType.FLOAT32,
            scale = 1.0 / 255.0,
            zeroPoint = 0.0,
            mean = listOf(0.485, 0.456, 0.406),
            std = listOf(0.229, 0.224, 0.225)
        )
        // Black pixel: sample01=0 -> (0-mean)/std
        val black = PrivacyFrame(1, 1, intArrayOf(0xFF000000.toInt()))
        val blackFloats = PixelConversionStage.toFloats(black, normalized)
        assertEquals((0.0 - 0.485) / 0.229, blackFloats[0].toDouble(), 1e-6)
        assertEquals((0.0 - 0.456) / 0.224, blackFloats[1].toDouble(), 1e-6)
        assertEquals((0.0 - 0.406) / 0.225, blackFloats[2].toDouble(), 1e-6)

        // White pixel: sample01=1 -> (1-mean)/std
        val white = PrivacyFrame(1, 1, intArrayOf(0xFFFFFFFF.toInt()))
        val whiteFloats = PixelConversionStage.toFloats(white, normalized)
        assertEquals((1.0 - 0.485) / 0.229, whiteFloats[0].toDouble(), 1e-6)
        assertEquals((1.0 - 0.456) / 0.224, whiteFloats[1].toDouble(), 1e-6)
        assertEquals((1.0 - 0.406) / 0.225, whiteFloats[2].toDouble(), 1e-6)
    }

    @Test
    fun `no double normalization between scale and meanstd`() {
        // With mean/std present the scale factor must NOT be applied
        // again: raw01 only. 255 -> (1.0-mean)/std exactly.
        val normalized = PixelConversionOptions(
            channelOrder = PreprocessChannelOrder.RGB,
            alphaPolicy = AlphaPolicy.DISCARD,
            tensorType = PreprocessingTensorType.FLOAT32,
            scale = 1.0 / 255.0,
            zeroPoint = 0.0,
            mean = listOf(0.5, 0.5, 0.5),
            std = listOf(0.5, 0.5, 0.5)
        )
        val white = PrivacyFrame(1, 1, intArrayOf(0xFFFFFFFF.toInt()))
        val floats = PixelConversionStage.toFloats(white, normalized)
        assertEquals(1.0f, floats[0], 1e-6f) // (1.0-0.5)/0.5 = 1.0
    }

    // --------------------------------
    // §14 DATATYPE
    // --------------------------------

    @Test
    fun `float32 tensors carry floats and no quantized bytes`() {
        val frame = PrivacyFrame(1, 1, intArrayOf(0xFFFFFFFF.toInt()))
        val input = PixelConversionStage.toModelInput(
            frame,
            options(PreprocessChannelOrder.RGB),
            ModelTensorType.FLOAT32
        )
        assertEquals(ModelTensorType.FLOAT32, input.tensorType)
        assertNull(input.quantizedBytes)
    }

    @Test
    fun `int8 quantization applies scale and zeroPoint`() {
        val int8opts = PixelConversionOptions(
            channelOrder = PreprocessChannelOrder.RGB,
            alphaPolicy = AlphaPolicy.DISCARD,
            tensorType = PreprocessingTensorType.INT8,
            scale = 1.0 / 255.0,
            zeroPoint = -128.0,
            mean = emptyList(),
            std = emptyList()
        )
        // Scale-only: white -> 1.0 -> round(1/(1/255))-128 = 127
        val white = PrivacyFrame(1, 1, intArrayOf(0xFFFFFFFF.toInt()))
        val input = PixelConversionStage.toModelInput(
            white, int8opts, ModelTensorType.INT8
        )
        assertEquals(127, input.quantizedBytes!!.first().toInt())
        assertEquals(1.0f, input.floats[0], 0.01f) // dequantized approx
    }

    @Test
    fun `int8 quantization clamps positive boundaries`() {
        val clamping = PixelConversionOptions(
            channelOrder = PreprocessChannelOrder.RGB,
            alphaPolicy = AlphaPolicy.DISCARD,
            tensorType = PreprocessingTensorType.INT8,
            scale = 1.0 / 255.0,
            zeroPoint = 0.0, // white would exceed 127 -> clamp
            mean = emptyList(),
            std = emptyList()
        )
        val white = PrivacyFrame(1, 1, intArrayOf(0xFFFFFFFF.toInt()))
        val bytes = PixelConversionStage.quantize(
            PixelConversionStage.toFloats(white, clamping),
            ModelTensorType.INT8,
            clamping.scale,
            clamping.zeroPoint
        )
        assertEquals(Byte.MAX_VALUE.toInt(), bytes[0].toInt())
    }

    @Test
    fun `uint8 quantization maps values into the unsigned byte range`() {
        val uint8opts = PixelConversionOptions(
            channelOrder = PreprocessChannelOrder.RGB,
            alphaPolicy = AlphaPolicy.DISCARD,
            tensorType = PreprocessingTensorType.UINT8,
            scale = 1.0 / 255.0,
            zeroPoint = 0.0,
            mean = emptyList(),
            std = emptyList()
        )
        val frame = PrivacyFrame(1, 1, intArrayOf(0xFF0000FF.toInt())) // blue
        val bytes = PixelConversionStage.quantize(
            PixelConversionStage.toFloats(frame, uint8opts),
            ModelTensorType.UINT8,
            uint8opts.scale,
            uint8opts.zeroPoint
        )
        // blue: b channel sample01 = 1.0 -> round(1/(1/255)) = 255
        assertEquals(255, bytes[2].toInt() and 0xFF)
    }

    // --------------------------------
    // §13/§15 TENSOR LAYOUT + SHAPE
    // --------------------------------

    @Test
    fun `nhwc layout interleaves channels per pixel`() {
        // Two pixels with distinct per-channel values:
        //   pixel0 = R=1,G=2,B=3 ; pixel1 = R=10,G=11,B=12
        val frame = PrivacyFrame(
            2, 1,
            intArrayOf(
                (0xFF shl 24) or (1 shl 16) or (2 shl 8) or 3,
                (0xFF shl 24) or (10 shl 16) or (11 shl 8) or 12
            )
        )
        val floats = PixelConversionStage.toFloats(
            frame, options(PreprocessChannelOrder.RGB)
        )
        // NHWC: [R0,G0,B0, R1,G1,B1] (channel-interleaved).
        // NCHW would be [R0,R1, G0,G1, B0,B1].
        val expected = floatArrayOf(
            1f / 255f, 2f / 255f, 3f / 255f,
            10f / 255f, 11f / 255f, 12f / 255f
        )
        assertArrayEquals(expected, floats, 1e-6f)
    }

    @Test
    fun `final tensor shape matches model dimensions and batch one`() {
        val config = PreprocessingConfig(
            version = "preprocess-v2",
            inputWidth = 4,
            inputHeight = 4,
            resizePolicy = ResizePolicy.NEAREST_NEIGHBOR,
            cropPolicy = AspectRatioPolicy.RESIZE,
            paddingPolicy = PaddingPolicy.NO_PADDING,
            interpolation = Interpolation.NEAREST,
            orientationPolicy = OrientationPolicy.NORMALIZE_TO_0,
            colorFormat = ColorFormat.RGB,
            channelOrder = PreprocessChannelOrder.RGB,
            alphaPolicy = AlphaPolicy.DISCARD,
            tensorType = PreprocessingTensorType.FLOAT32,
            scale = 1.0 / 255.0,
            mean = emptyList(),
            std = emptyList(),
            tensorLayout = PreprocessingTensorLayout.NHWC,
            batchSize = BatchHandling.BATCH_1
        )
        val source = PrivacyFrame(8, 8, IntArray(64) { 0xFF404040.toInt() })
        val evidence = PreprocessingEvidenceFactory.approve(
            frame = source,
            sanitizationStatus = com.example.feedsense.analysis.privacy.PrivacySanitizationStatus.NOT_REQUIRED
        )
        val preprocessor = DeterministicPreprocessor(config)
        val result = preprocessor.preprocess(evidence)
        val output = (result as PreprocessResult.Success).output
        // Single-frame, batch size 1 => tensor is the raw frame
        // volume: [1, H, W, C].
        assertEquals(4, output.input.height)
        assertEquals(4, output.input.width)
        assertEquals(3, output.input.channels)
        assertEquals(4 * 4 * 3, output.input.floats.size)
        assertEquals(BatchHandling.BATCH_1, config.batchSize)
    }
}