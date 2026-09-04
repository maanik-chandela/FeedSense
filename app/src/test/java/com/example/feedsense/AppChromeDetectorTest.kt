package com.example.feedsense.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

// --------------------------------
// APP CHROME DETECTOR TEST
// --------------------------------
//
// Verifies that AppChromeDetector correctly identifies
// app chrome frames (status bar, nav bar, app header)
// versus actual content frames, and that app context
// detection works across known platforms.
//

class AppChromeDetectorTest {

    private lateinit var detector: AppChromeDetector

    @Before
    fun setup() {
        detector = AppChromeDetector()
    }

    // ========================================
    // EMPTY / NULL INPUT
    // ========================================

    @Test
    fun `null text is chrome`() {
        val result = detector.detect(null)
        assertTrue(result.isChromeFrame)
        assertNull(result.appContext)
    }

    @Test
    fun `blank text is chrome`() {
        val result = detector.detect("   ")
        assertTrue(result.isChromeFrame)
    }

    @Test
    fun `empty text is chrome`() {
        val result = detector.detect("")
        assertTrue(result.isChromeFrame)
    }

    // ========================================
    // YOUTUBE CHROME
    // ========================================

    @Test
    fun `youtube status bar only is chrome`() {
        val text = "YouTube 12:30 WiFi Battery 85%"
        val result = detector.detect(text)
        assertTrue(result.isChromeFrame)
        assertEquals("youtube", result.appContext)
        assertTrue(result.chromeSignals.contains("status_bar"))
    }

    @Test
    fun `youtube app header tabs only is chrome`() {
        val text = "YouTube Home Subscriptions Shorts Library You"
        val result = detector.detect(text)
        assertTrue(result.isChromeFrame)
        assertEquals("youtube", result.appContext)
        assertTrue(result.chromeSignals.contains("app_header"))
    }

    // ========================================
    // INSTAGRAM CHROME
    // ========================================

    @Test
    fun `instagram header only is chrome`() {
        val text = "Instagram Stories Reels Explore"
        val result = detector.detect(text)
        assertTrue(result.isChromeFrame)
        assertEquals("instagram", result.appContext)
    }

    // ========================================
    // TIKTOK CHROME
    // ========================================

    @Test
    fun `tiktok header only is chrome`() {
        val text = "TikTok Following For You Discover"
        val result = detector.detect(text)
        assertTrue(result.isChromeFrame)
        assertEquals("tiktok", result.appContext)
    }

    // ========================================
    // BROWSER CHROME
    // ========================================

    @Test
    fun `chrome browser address bar is chrome`() {
        val text = "Chrome https://www.example.com/article Tab 1 of 3"
        val result = detector.detect(text)
        assertTrue(result.isChromeFrame)
        assertEquals("chrome", result.appContext)
        assertTrue(result.chromeSignals.contains("browser_chrome"))
    }

    @Test
    fun `safari browser is chrome`() {
        val text = "Safari Bookmarks History Downloads"
        val result = detector.detect(text)
        assertTrue(result.isChromeFrame)
        assertEquals("safari", result.appContext)
    }

    // ========================================
    // CONTENT FRAMES (NOT CHROME)
    // ========================================

    @Test
    fun `youtube video with views and comments is content`() {
        val text = "YouTube 1.2M views 45K likes Subscribe How to cook pasta Italian recipe 3:42"
        val result = detector.detect(text)
        assertFalse(result.isChromeFrame)
        assertEquals("youtube", result.appContext)
        assertTrue(result.contentSignals.contains("video_playback"))
    }

    @Test
    fun `instagram post with engagement is content`() {
        val text = "Instagram 15K likes View all 234 comments Great workout routine! #fitness #gym"
        val result = detector.detect(text)
        assertFalse(result.isChromeFrame)
        assertEquals("instagram", result.appContext)
        assertTrue(result.contentSignals.contains("engagement_metrics"))
    }

    @Test
    fun `tiktok video with comments is content`() {
        val text = "TikTok 456K views 12K comments Reply to @user This is hilarious"
        val result = detector.detect(text)
        assertFalse(result.isChromeFrame)
        assertEquals("tiktok", result.appContext)
        assertTrue(result.contentSignals.contains("comment_section"))
    }

    @Test
    fun `substantial text without chrome signals is content`() {
        val text = "The theory of relativity explains that space and time are interwoven into a single continuum. Einstein published this groundbreaking work in 1905."
        val result = detector.detect(text)
        assertFalse(result.isChromeFrame)
        assertTrue(result.contentSignals.contains("substantial_text"))
    }

    @Test
    fun `reddit post with engagement is content`() {
        val text = "Reddit r/sports 2.3K upvotes 456 comments IPL cricket match highlights discussion thread"
        val result = detector.detect(text)
        assertFalse(result.isChromeFrame)
        assertEquals("reddit", result.appContext)
        assertTrue(result.contentSignals.contains("video_playback"))
    }

    // ========================================
    // MIXED SIGNALS
    // ========================================

    @Test
    fun `mixed chrome and content is treated as content`() {
        val text = "YouTube WiFi 1.2M views How to cook pasta Italian recipe Subscribe"
        val result = detector.detect(text)
        assertFalse(result.isChromeFrame)
        assertTrue(result.chromeSignals.isNotEmpty())
        assertTrue(result.contentSignals.isNotEmpty())
    }

    // ========================================
    // APP CONTEXT DETECTION
    // ========================================

    @Test
    fun `detects twitter context`() {
        val text = "Twitter Retweet 5K Quote tweets"
        val result = detector.detect(text)
        assertEquals("twitter", result.appContext)
    }

    @Test
    fun `detects facebook context`() {
        val text = "Facebook Friends Marketplace Groups Watch"
        val result = detector.detect(text)
        assertEquals("facebook", result.appContext)
    }

    @Test
    fun `detects linkedin context`() {
        val text = "LinkedIn Connections Posts Jobs Networking"
        val result = detector.detect(text)
        assertEquals("linkedin", result.appContext)
    }

    @Test
    fun `detects telegram context`() {
        val text = "Telegram Messages Chats Channels"
        val result = detector.detect(text)
        assertEquals("telegram", result.appContext)
    }

    @Test
    fun `detects spotify context`() {
        val text = "Spotify Now Playing Playlist Songs Albums"
        val result = detector.detect(text)
        assertEquals("spotify", result.appContext)
    }

    @Test
    fun `unknown app when no pattern matches`() {
        val text = "Some random text with no app identifiers"
        val result = detector.detect(text)
        assertNull(result.appContext)
    }

    // ========================================
    // SYSTEM DIALOG DETECTION
    // ========================================

    @Test
    fun `permission dialog is chrome`() {
        val text = "Allow FeedSense to access your photos? Permission"
        val result = detector.detect(text)
        assertTrue(result.isChromeFrame)
        assertTrue(result.chromeSignals.contains("system_dialog"))
    }

    // ========================================
    // EDGE CASES
    // ========================================

    @Test
    fun `very short text with app name is chrome`() {
        val text = "YouTube"
        val result = detector.detect(text)
        assertTrue(result.isChromeFrame)
        assertEquals("youtube", result.appContext)
    }

    @Test
    fun `hashtags indicate content`() {
        val text = "#trending #viral Check out this amazing content today"
        val result = detector.detect(text)
        assertFalse(result.isChromeFrame)
        assertTrue(result.contentSignals.contains("hashtags"))
    }

    @Test
    fun `creator info indicates content`() {
        val text = "MrBeast Subscribe to channel Verified creator 100M subscribers"
        val result = detector.detect(text)
        assertFalse(result.isChromeFrame)
        assertTrue(result.contentSignals.contains("creator_info"))
    }

    // ========================================
    // APP CONTEXT ENUM
    // ========================================

    @Test
    fun `AppContext fromKey returns correct context`() {
        assertEquals(
            AppChromeDetector.AppContext.YOUTUBE,
            AppChromeDetector.AppContext.fromKey("youtube")
        )
        assertEquals(
            AppChromeDetector.AppContext.INSTAGRAM,
            AppChromeDetector.AppContext.fromKey("instagram")
        )
        assertEquals(
            AppChromeDetector.AppContext.UNKNOWN,
            AppChromeDetector.AppContext.fromKey(null)
        )
        assertEquals(
            AppChromeDetector.AppContext.UNKNOWN,
            AppChromeDetector.AppContext.fromKey("nonexistent")
        )
    }

    @Test
    fun `AppContext key matches enum key`() {
        assertEquals("youtube", AppChromeDetector.AppContext.YOUTUBE.key)
        assertEquals("instagram", AppChromeDetector.AppContext.INSTAGRAM.key)
        assertEquals("tiktok", AppChromeDetector.AppContext.TIKTOK.key)
    }
}
