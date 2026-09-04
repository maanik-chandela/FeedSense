package com.example.feedsense

import com.example.feedsense.analysis.PlatformDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlatformDetectorTest {

    private val detector =
        PlatformDetector()

    @Test
    fun blankText_returnsNull() {

        assertNull(detector.detect(null))
        assertNull(detector.detect(""))
        assertNull(detector.detect("   "))
    }

    @Test
    fun instagram_returnsPlatform() {

        assertEquals(
            "Instagram",
            detector.detect(
                "2,345 likes · @explore on Instagram"
            )
        )
    }

    @Test
    fun youtube_returnsPlatform() {

        assertEquals(
            "YouTube",
            detector.detect(
                "Now playing on YouTube Shorts"
            )
        )
    }

    @Test
    fun tiktok_returnsPlatform() {

        assertEquals(
            "TikTok",
            detector.detect(
                "Follow @creator on TikTok · 1.2M likes"
            )
        )
    }

    @Test
    fun facebook_returnsPlatform() {

        assertEquals(
            "Facebook",
            detector.detect(
                "Share this on Facebook"
            )
        )
    }

    @Test
    fun noPlatformEvidence_returnsNull() {

        assertNull(
            detector.detect(
                "Watch the ipl match live, six and wicket highlights"
            )
        )
    }

    @Test
    fun genericWordsWithoutPlatformName_returnsNull() {

        assertNull(
            detector.detect(
                "The video showed red colored tiles"
            )
        )

        assertNull(
            detector.detect(
                "I like the way this reels smoothly"
            )
        )
    }
}
