package com.example.feedsense

import com.example.feedsense.analysis.privacy.PixelBounds
import com.example.feedsense.analysis.privacy.ProtectedRegion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-4.
 *
 * ProtectedRegion: normalized coordinates, bounds
 * computation, validation, and overlap detection.
 */
class ProtectedRegionTest {

    // --------------------------------
    // BASIC CONSTRUCTION
    // --------------------------------

    @Test
    fun `valid region constructs correctly`() {

        val region = ProtectedRegion(
            x = 0.1,
            y = 0.2,
            width = 0.3,
            height = 0.4,
            label = "TEST"
        )

        assertEquals(0.1, region.x, 1e-9)
        assertEquals(0.2, region.y, 1e-9)
        assertEquals(0.3, region.width, 1e-9)
        assertEquals(0.4, region.height, 1e-9)
        assertEquals("TEST", region.label)
    }

    @Test
    fun `boundary values construct correctly`() {

        val region = ProtectedRegion(
            x = 0.0,
            y = 0.0,
            width = 1.0,
            height = 1.0,
            label = "FULL"
        )

        assertEquals(0.0, region.x, 1e-9)
        assertEquals(1.0, region.width, 1e-9)
    }

    // --------------------------------
    // PIXEL BOUNDS
    // --------------------------------

    @Test
    fun `toPixelBounds computes correctly`() {

        val region = ProtectedRegion(
            x = 0.0,
            y = 0.0,
            width = 1.0,
            height = 0.05,
            label = "STATUS_BAR"
        )

        val bounds =
            region.toPixelBounds(1080, 2400)

        assertEquals(0, bounds.left)
        assertEquals(0, bounds.top)
        assertEquals(1080, bounds.right)
        assertEquals(120, bounds.bottom)
        assertEquals("STATUS_BAR", bounds.label)
    }

    @Test
    fun `toPixelBounds handles mid-screen region`() {

        val region = ProtectedRegion(
            x = 0.25,
            y = 0.5,
            width = 0.5,
            height = 0.1,
            label = "CENTER"
        )

        val bounds =
            region.toPixelBounds(1000, 2000)

        assertEquals(250, bounds.left)
        assertEquals(1000, bounds.top)
        assertEquals(750, bounds.right)
        assertEquals(1200, bounds.bottom)
    }

    @Test
    fun `pixel bounds width and height correct`() {

        val region = ProtectedRegion(
            x = 0.1,
            y = 0.2,
            width = 0.3,
            height = 0.4,
            label = "TEST"
        )

        val bounds =
            region.toPixelBounds(1000, 2000)

        assertEquals(300, bounds.width)
        assertEquals(800, bounds.height)
    }

    @Test
    fun `pixel bounds isValid for non-empty regions`() {

        val region = ProtectedRegion(
            x = 0.1,
            y = 0.1,
            width = 0.1,
            height = 0.1,
            label = "TEST"
        )

        val bounds =
            region.toPixelBounds(1000, 1000)

        assertTrue(bounds.isValid)
    }

    @Test
    fun `pixel bounds isValid false for zero-size`() {

        val bounds = PixelBounds(
            left = 100,
            top = 100,
            right = 100,
            bottom = 100,
            label = "EMPTY"
        )

        assertFalse(bounds.isValid)
    }

    // --------------------------------
    // OVERLAP DETECTION
    // --------------------------------

    @Test
    fun `overlapping bounds detected`() {

        val bounds1 = PixelBounds(
            left = 0, top = 0,
            right = 100, bottom = 100,
            label = "A"
        )

        val bounds2 = PixelBounds(
            left = 50, top = 50,
            right = 150, bottom = 150,
            label = "B"
        )

        assertTrue(bounds1.overlaps(bounds2))
        assertTrue(bounds2.overlaps(bounds1))
    }

    @Test
    fun `non-overlapping bounds not detected`() {

        val bounds1 = PixelBounds(
            left = 0, top = 0,
            right = 100, bottom = 100,
            label = "A"
        )

        val bounds2 = PixelBounds(
            left = 200, top = 200,
            right = 300, bottom = 300,
            label = "B"
        )

        assertFalse(bounds1.overlaps(bounds2))
        assertFalse(bounds2.overlaps(bounds1))
    }

    @Test
    fun `adjacent bounds not overlapping`() {

        val bounds1 = PixelBounds(
            left = 0, top = 0,
            right = 100, bottom = 100,
            label = "A"
        )

        val bounds2 = PixelBounds(
            left = 100, top = 0,
            right = 200, bottom = 100,
            label = "B"
        )

        assertFalse(bounds1.overlaps(bounds2))
    }

    // --------------------------------
    // PRESETS
    // --------------------------------

    @Test
    fun `STATUS_BAR preset is at top`() {

        val bounds =
            ProtectedRegion.STATUS_BAR
                .toPixelBounds(1080, 2400)

        assertEquals(0, bounds.top)
        assertTrue(bounds.height > 0)
    }

    @Test
    fun `NAVIGATION_BAR preset is at bottom`() {

        val bounds =
            ProtectedRegion.NAVIGATION_BAR
                .toPixelBounds(1080, 2400)

        assertEquals(2400, bounds.bottom)
        assertTrue(bounds.height > 0)
    }

    // --------------------------------
    // EQUALITY
    // --------------------------------

    @Test
    fun `equal regions are equal`() {

        val r1 = ProtectedRegion(
            0.1, 0.2, 0.3, 0.4, "TEST"
        )

        val r2 = ProtectedRegion(
            0.1, 0.2, 0.3, 0.4, "TEST"
        )

        assertEquals(r1, r2)
    }

    @Test
    fun `different regions are not equal`() {

        val r1 = ProtectedRegion(
            0.1, 0.2, 0.3, 0.4, "A"
        )

        val r2 = ProtectedRegion(
            0.1, 0.2, 0.3, 0.4, "B"
        )

        assertFalse(r1 == r2)
    }
}
