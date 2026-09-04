package com.example.feedsense

import com.example.feedsense.analysis.privacy.SystemUIRegionDetector
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-4.
 *
 * SystemUIRegionDetector: system UI region detection
 * with configurable fractions.
 */
class SystemUIRegionDetectorTest {

    // --------------------------------
    // DEFAULT DETECTION
    // --------------------------------

    @Test
    fun `default detector finds 3 regions`() {

        val detector = SystemUIRegionDetector()

        val regions = detector.detect(
            frameFile = File("dummy.png"),
            frameWidth = 1080,
            frameHeight = 2400
        )

        assertEquals(3, regions.size)
    }

    @Test
    fun `default detector includes STATUS_BAR`() {

        val detector = SystemUIRegionDetector()

        val regions = detector.detect(
            frameFile = File("dummy.png"),
            frameWidth = 1080,
            frameHeight = 2400
        )

        val labels = regions.map { it.label }

        assertTrue(labels.contains("STATUS_BAR"))
    }

    @Test
    fun `default detector includes NOTIFICATION`() {

        val detector = SystemUIRegionDetector()

        val regions = detector.detect(
            frameFile = File("dummy.png"),
            frameWidth = 1080,
            frameHeight = 2400
        )

        val labels = regions.map { it.label }

        assertTrue(labels.contains("NOTIFICATION"))
    }

    @Test
    fun `default detector includes NAVIGATION`() {

        val detector = SystemUIRegionDetector()

        val regions = detector.detect(
            frameFile = File("dummy.png"),
            frameWidth = 1080,
            frameHeight = 2400
        )

        val labels = regions.map { it.label }

        assertTrue(labels.contains("NAVIGATION"))
    }

    // --------------------------------
    // SELECTIVE DETECTION
    // --------------------------------

    @Test
    fun `status bar only detector finds 1 region`() {

        val detector = SystemUIRegionDetector(
            includeStatusBar = true,
            includeNotification = false,
            includeNavigation = false
        )

        val regions = detector.detect(
            frameFile = File("dummy.png"),
            frameWidth = 1080,
            frameHeight = 2400
        )

        assertEquals(1, regions.size)
        assertEquals(
            "STATUS_BAR",
            regions[0].label
        )
    }

    @Test
    fun `no regions detector finds 0`() {

        val detector = SystemUIRegionDetector(
            includeStatusBar = false,
            includeNotification = false,
            includeNavigation = false
        )

        val regions = detector.detect(
            frameFile = File("dummy.png"),
            frameWidth = 1080,
            frameHeight = 2400
        )

        assertEquals(0, regions.size)
    }

    // --------------------------------
    // CUSTOM FRACTIONS
    // --------------------------------

    @Test
    fun `custom status bar fraction applied`() {

        val detector = SystemUIRegionDetector(
            statusBarHeightFraction = 0.08,
            includeNotification = false,
            includeNavigation = false
        )

        val regions = detector.detect(
            frameFile = File("dummy.png"),
            frameWidth = 1080,
            frameHeight = 2400
        )

        assertEquals(1, regions.size)

        val region = regions[0]
        assertEquals(
            0.08,
            region.height,
            1e-9
        )
    }

    // --------------------------------
    // PIXEL BOUNDS
    // --------------------------------

    @Test
    fun `detected regions have valid pixel bounds`() {

        val detector = SystemUIRegionDetector()

        val regions = detector.detect(
            frameFile = File("dummy.png"),
            frameWidth = 1080,
            frameHeight = 2400
        )

        for (region in regions) {
            val bounds =
                region.toPixelBounds(1080, 2400)

            assertTrue(bounds.isValid)
            assertTrue(bounds.width > 0)
            assertTrue(bounds.height > 0)
        }
    }

    @Test
    fun `status bar is at top of frame`() {

        val detector = SystemUIRegionDetector(
            includeNotification = false,
            includeNavigation = false
        )

        val regions = detector.detect(
            frameFile = File("dummy.png"),
            frameWidth = 1080,
            frameHeight = 2400
        )

        val bounds =
            regions[0].toPixelBounds(1080, 2400)

        assertEquals(0, bounds.top)
    }

    @Test
    fun `navigation bar is at bottom of frame`() {

        val detector = SystemUIRegionDetector(
            includeStatusBar = false,
            includeNotification = false,
            includeNavigation = true
        )

        val regions = detector.detect(
            frameFile = File("dummy.png"),
            frameWidth = 1080,
            frameHeight = 2400
        )

        val bounds =
            regions[0].toPixelBounds(1080, 2400)

        assertEquals(2400, bounds.bottom)
    }
}
