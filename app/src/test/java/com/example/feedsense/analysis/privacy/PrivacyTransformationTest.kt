package com.example.feedsense.analysis.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-13.
 *
 * Transformation primitives: deterministic, content-obscuring,
 * and bounded by the marriage with PrivacyFrame.
 */
class PrivacyTransformationTest {

    /*
     * A deterministic "text-like" test region: a checkerboard so
     * blur/pixelate have real structure to destroy.
     */
    private fun frame(width: Int = 100, height: Int = 100): PrivacyFrame =
        SyntheticFrames.checkerboard(width, height, 0xFF101010.toInt(), 0xFFE0E0E0.toInt())

    private fun regionBounds(
        frame: PrivacyFrame, y0: Double, y1: Double
    ): PixelBounds {
        val region = ProtectedRegion(0.0, y0, 1.0, y1 - y0, "TEST")
        return region.toPixelBounds(frame.width, frame.height)
    }

    @Test
    fun `transform severity ordering matches documented precedence`() {
        val order = PrivacyTransformation.entries.sortedBy { it.severity }
        assertEquals(
            listOf(
                PrivacyTransformation.NONE,
                PrivacyTransformation.BLUR,
                PrivacyTransformation.PIXELATE,
                PrivacyTransformation.MASK,
                PrivacyTransformation.CROP,
                PrivacyTransformation.DROP_FRAME
            ),
            order
        )
    }

    @Test
    fun `blur changes the region checksum on structured content`() {
        val f = frame()
        val bounds = regionBounds(f, 0.0, 0.3)
        val before = f.regionChecksum(bounds)
        PrivacyTransformers.blur(f, bounds)
        assertNotEquals(before, f.regionChecksum(bounds))
    }

    @Test
    fun `blur is deterministic`() {
        val a = frame()
        val b = frame()
        val bounds = regionBounds(a, 0.2, 0.8)
        PrivacyTransformers.blur(a, bounds)
        PrivacyTransformers.blur(b, bounds)
        assertEquals(a.pixels.contentEquals(b.pixels), true)
    }

    @Test
    fun `pixelate produces uniform cells and changes content`() {
        val f = frame()
        val bounds = regionBounds(f, 0.1, 0.5)
        val before = f.regionChecksum(bounds)
        PrivacyTransformers.pixelate(f, bounds)
        assertNotEquals(before, f.regionChecksum(bounds))

        // Within one cell all pixels must be identical.
        val left = 0
        val top = (0.1 * f.height).toInt()
        val cellValue = f.getPixel(left, top)
        for (y in top until top + 2) {
            for (x in left until left + 2) {
                assertEquals(cellValue, f.getPixel(x, y))
            }
        }
    }

    @Test
    fun `mask fully replaces region content`() {
        val f = frame()
        val bounds = regionBounds(f, 0.3, 0.7)
        val before = f.regionChecksum(bounds)
        PrivacyTransformers.mask(f, bounds)
        assertNotEquals(before, f.regionChecksum(bounds))
    }

    @Test
    fun `mask is deterministic across copies`() {
        val a = frame()
        val b = frame()
        val boundsA = regionBounds(a, 0.25, 0.75)
        val boundsB = regionBounds(b, 0.25, 0.75)
        PrivacyTransformers.mask(a, boundsA)
        PrivacyTransformers.mask(b, boundsB)
        assertEquals(true, a.pixels.contentEquals(b.pixels))
    }

    @Test
    fun `crop removes a full-width band and shifts content up`() {
        val f = frame(100, 200)
        val band = ProtectedRegion(0.0, 0.0, 1.0, 0.1, "BAND")
        val bounds = band.toPixelBounds(f.width, f.height)
        assertEquals(20, bounds.height)

        val cropped = PrivacyTransformers.cropBand(f, bounds)
        assertEquals(100, cropped.width)
        assertEquals(180, cropped.height)

        // Content below the band shifted up: row 20 of the
        // original moved to row 0 of the cropped frame.
        assertEquals(
            f.pixels[20 * 100],
            cropped.pixels[0]
        )
        assertEquals(
            f.pixels[199 * 100],
            cropped.pixels[179 * 100]
        )
    }

    @Test
    fun `crop of a partial-width region returns the frame unchanged`() {
        val f = frame()
        val partial = ProtectedRegion(0.1, 0.2, 0.4, 0.2, "PARTIAL")
        val before = f.pixels.copyOf()
        val out = PrivacyTransformers.cropBand(f, partial.toPixelBounds(f.width, f.height))
        assertTrue(out === f)
        assertTrue(before.contentEquals(f.pixels))
    }

    @Test
    fun `PrivacyFrame rejects inconsistent buffers`() {
        try {
            PrivacyFrame(10, 10, IntArray(99))
            throw AssertionError("should have thrown")
        } catch (_: IllegalArgumentException) {
            // expected
        }
        try {
            PrivacyFrame(0, 10, IntArray(0))
            throw AssertionError("should have thrown")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `PrivacyFrame checksum is deterministic and content-free`() {
        val a = SyntheticFrames.verticalGradient(64, 64)
        val b = SyntheticFrames.verticalGradient(64, 64)
        assertEquals(a.frameChecksum(), b.frameChecksum())

        val mutated = SyntheticFrames.verticalGradient(64, 64)
        mutated.setPixel(5, 5, 0xFFFF0000.toInt())
        assertFalse(mutated.frameChecksum() == a.frameChecksum())
    }

    @Test
    fun `PrivacyFrame duplicate is an independent buffer`() {
        val a = SyntheticFrames.verticalGradient(32, 32)
        val dup = a.duplicated()
        assertTrue(a == dup)
        dup.setPixel(0, 0, 0xFF000000.toInt())
        assertNotEquals(a.frameChecksum(), dup.frameChecksum())
    }
}