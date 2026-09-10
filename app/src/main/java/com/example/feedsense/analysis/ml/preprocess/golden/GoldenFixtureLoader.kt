package com.example.feedsense.analysis.ml.preprocess.golden

import com.example.feedsense.analysis.ml.preprocess.FrameOrientation
import com.example.feedsense.analysis.ml.preprocess.PreprocessingConfig
import com.example.feedsense.analysis.ml.preprocess.PreprocessingVersion
import com.example.feedsense.analysis.privacy.PrivacyFrame

// --------------------------------
// GOLDEN FIXTURE LOADER (8B-15-4 §21)
// --------------------------------
//
// Generates deterministic synthetic PrivacyFrames from fixture
// definitions. The loader is the single source of truth for
// converting a SourcePattern + dimensions into pixels.
//
// Design rules:
//   - no network, no file I/O, no Android state
//   - deterministic: same inputs always produce same pixels
//   - small frames: kept intentionally tiny for easy reasoning
//   - the loader is used by JVM tests only

/**
 * Generates deterministic synthetic PrivacyFrames from fixture
 * definitions.
 */
object GoldenFixtureLoader {

    /**
     * Loads the synthetic frame for a fixture definition.
     */
    fun loadFrame(fixture: GoldenFixture): PrivacyFrame {
        return generateFrame(
            pattern = fixture.sourcePattern,
            width = fixture.sourceWidth,
            height = fixture.sourceHeight
        )
    }

    /**
     * Generates a deterministic PrivacyFrame from a pattern and
     * dimensions.
     */
    fun generateFrame(
        pattern: SourcePattern,
        width: Int,
        height: Int
    ): PrivacyFrame {
        val pixels = when (pattern) {
            SourcePattern.ALL_BLACK -> IntArray(width * height) { 0xFF000000.toInt() }
            SourcePattern.ALL_WHITE -> IntArray(width * height) { 0xFFFFFFFF.toInt() }
            SourcePattern.RED_DOMINANT -> IntArray(width * height) { 0xFFFF0000.toInt() }
            SourcePattern.GREEN_DOMINANT -> IntArray(width * height) { 0xFF00FF00.toInt() }
            SourcePattern.BLUE_DOMINANT -> IntArray(width * height) { 0xFF0000FF.toInt() }
            SourcePattern.RGB_CHANNEL_ISOLATION -> rgbChannelIsolation(width, height)
            SourcePattern.GRAYSCALE_LIKE -> grayscaleLike(width, height)
            SourcePattern.HIGH_CONTRAST_EDGES -> highContrastEdges(width, height)
            SourcePattern.KNOWN_GRADIENT -> knownGradient(width, height)
            SourcePattern.COUNTER_INDEX -> counterIndex(width, height)
            SourcePattern.CHECKERBOARD -> checkerboard(width, height)
            SourcePattern.SOLID_ARGB -> solidArgb(width, height)
            SourcePattern.PORTRAIT_STRIP -> portraitStrip(width, height)
            SourcePattern.LANDSCAPE_STRIP -> landscapeStrip(width, height)
            SourcePattern.SQUARE_SOLID -> IntArray(width * height) { 0xFF808080.toInt() }
            SourcePattern.ONE_BY_ONE -> intArrayOf(0xFFA0B0C0.toInt())
            SourcePattern.VERY_SMALL -> verySmall(width, height)
            SourcePattern.ODD_DIMENSIONS -> oddDimensions(width, height)
            SourcePattern.LARGER_THAN_TARGET -> largerThanTarget(width, height)
            SourcePattern.SMALLER_THAN_TARGET -> smallerThanTarget(width, height)
            SourcePattern.ALREADY_TARGET_SIZE -> alreadyTargetSize(width, height)
            SourcePattern.TRANSLUCENT -> IntArray(width * height) { 0x80FF0000.toInt() }
            SourcePattern.SEMI_TRANSPARENT -> semiTransparent(width, height)
        }
        return PrivacyFrame(width, height, pixels)
    }

    /**
     * Every pixel encodes its position as a unique color channel
     * value. R = x * 80, G = y * 80, B = (x + y) * 40. This
     * makes rotation/channel swaps immediately detectable.
     */
    private fun rgbChannelIsolation(width: Int, height: Int): IntArray {
        val pixels = IntArray(width * height)
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = (x * 80).coerceIn(0, 255)
                val g = (y * 80).coerceIn(0, 255)
                val b = ((x + y) * 40).coerceIn(0, 255)
                pixels[i++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return pixels
    }

    /**
     * A grayscale-like pattern where R=G=B at each pixel.
     * Value varies by position: (x + y*width) scaled to 0..255.
     */
    private fun grayscaleLike(width: Int, height: Int): IntArray {
        val total = (width * height).coerceAtLeast(1)
        val pixels = IntArray(width * height)
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val v = ((x + y * width) * 255 / total).coerceIn(0, 255)
                pixels[i++] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        }
        return pixels
    }

    /**
     * Checkerboard of pure black and pure white with cell=1,
     * creating maximum contrast at every pixel boundary.
     */
    private fun highContrastEdges(width: Int, height: Int): IntArray {
        val pixels = IntArray(width * height)
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[i++] = if (((x + y) and 1) == 0) {
                    0xFF000000.toInt()
                } else {
                    0xFFFFFFFF.toInt()
                }
            }
        }
        return pixels
    }

    /**
     * Known gradient: R = x * 255 / width, G = y * 255 / height,
     * B = (x + y) * 128 / (width + height).
     */
    private fun knownGradient(width: Int, height: Int): IntArray {
        val pixels = IntArray(width * height)
        val total = (width + height).coerceAtLeast(1)
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = (x * 255 / width).coerceIn(0, 255)
                val g = (y * 255 / height).coerceIn(0, 255)
                val b = ((x + y) * 128 / total).coerceIn(0, 255)
                pixels[i++] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
        }
        return pixels
    }

    /**
     * Counter-index: each pixel is its flat index (0..N-1) as an
     * opaque gray value, wrapping at 256.
     */
    private fun counterIndex(width: Int, height: Int): IntArray {
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val v = i and 0xFF
            pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        return pixels
    }

    /**
     * Checkerboard of opaque black and opaque white, cell size 2.
     */
    private fun checkerboard(width: Int, height: Int): IntArray {
        val pixels = IntArray(width * height)
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pattern = ((x / 2) + (y / 2)) and 1
                pixels[i++] = if (pattern == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            }
        }
        return pixels
    }

    /**
     * Solid opaque black frame.
     */
    private fun solidArgb(width: Int, height: Int): IntArray {
        return IntArray(width * height) { 0xFF000000.toInt() }
    }

    /**
     * Portrait: tall frame with gradient bands.
     */
    private fun portraitStrip(width: Int, height: Int): IntArray {
        val pixels = IntArray(width * height)
        var i = 0
        for (y in 0 until height) {
            val v = (y * 255 / height).coerceIn(0, 255)
            for (x in 0 until width) {
                pixels[i++] = (0xFF shl 24) or (v shl 16) or (0x80 shl 8) or (255 - v)
            }
        }
        return pixels
    }

    /**
     * Landscape: wide frame with gradient bands.
     */
    private fun landscapeStrip(width: Int, height: Int): IntArray {
        val pixels = IntArray(width * height)
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val v = (x * 255 / width).coerceIn(0, 255)
                pixels[i++] = (0xFF shl 24) or (0x80 shl 16) or (v shl 8) or (255 - v)
            }
        }
        return pixels
    }

    /**
     * Very small: 2x2 with distinct per-pixel values.
     */
    private fun verySmall(width: Int, height: Int): IntArray {
        return intArrayOf(
            0xFFFF0000.toInt(), 0xFF00FF00.toInt(),
            0xFF0000FF.toInt(), 0xFFFFFF00.toInt()
        ).copyOfRange(0, width * height)
    }

    /**
     * Odd dimensions: gradient over (5, 7).
     */
    private fun oddDimensions(width: Int, height: Int): IntArray {
        return knownGradient(width, height)
    }

    /**
     * Larger than model target: 480x640 gradient.
     */
    private fun largerThanTarget(width: Int, height: Int): IntArray {
        return knownGradient(width, height)
    }

    /**
     * Smaller than model target: 32x24 gradient.
     */
    private fun smallerThanTarget(width: Int, height: Int): IntArray {
        return knownGradient(width, height)
    }

    /**
     * Already target size: exact model dimensions, gradient.
     */
    private fun alreadyTargetSize(width: Int, height: Int): IntArray {
        return knownGradient(width, height)
    }

    /**
     * Semi-transparent: alternating alpha values.
     */
    private fun semiTransparent(width: Int, height: Int): IntArray {
        val pixels = IntArray(width * height)
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val alpha = if (((x + y) and 1) == 0) 0x80 else 0xC0
                pixels[i++] = (alpha shl 24) or (0xFF shl 16) or (0x80 shl 8) or 0x40
            }
        }
        return pixels
    }
}
