package com.example.feedsense.analysis

// --------------------------------
// APP CHROME DETECTOR
// --------------------------------
//
// Identifies app UI chrome frames (status bar, navigation
// bar, app header/footer) versus actual content frames
// from captured screenshots.
//
// A frame showing only chrome (e.g. YouTube's top bar,
// Chrome browser's address bar) carries no research
// value and should not become a feed item. Content
// frames carry the actual video/text/topic being consumed.
//
// Detection is text-only (OCR evidence) so no image
// processing is needed. The detector is stateless and
// cheap enough to run on every frame.
//

class AppChromeDetector {

    // --------------------------------
    // RESULT
    // --------------------------------

    data class Detection(
        val isChromeFrame: Boolean,
        val appContext: String?,
        val chromeSignals: List<String>,
        val contentSignals: List<String>
    )

    // --------------------------------
    // APP CONTEXT
    // --------------------------------
    //
    // Known apps we can identify from OCR text.
    // Stored as a stable key used across the schema.

    enum class AppContext(val key: String, val displayName: String) {
        YOUTUBE("youtube", "YouTube"),
        INSTAGRAM("instagram", "Instagram"),
        TIKTOK("tiktok", "TikTok"),
        TWITTER("twitter", "Twitter / X"),
        FACEBOOK("facebook", "Facebook"),
        REDDIT("reddit", "Reddit"),
        LINKEDIN("linkedin", "LinkedIn"),
        SNAPCHAT("snapchat", "Snapchat"),
        THREADS("threads", "Threads"),
        PINTEREST("pinterest", "Pinterest"),
        CHROME("chrome", "Chrome Browser"),
        SAFARI("safari", "Safari Browser"),
        TELEGRAM("telegram", "Telegram"),
        WHATSAPP("whatsapp", "WhatsApp"),
        SPOTIFY("spotify", "Spotify"),
        UNKNOWN("unknown", "Unknown App");

        companion object {
            private val BY_KEY =
                entries.associateBy { it.key }

            fun fromKey(key: String?): AppContext {
                if (key == null) return UNKNOWN
                return BY_KEY[key.lowercase()]
                    ?: UNKNOWN
            }
        }
    }

    // --------------------------------
    // DETECT
    // --------------------------------

    fun detect(text: String?): Detection {
        if (text.isNullOrBlank()) {
            return Detection(
                isChromeFrame = true,
                appContext = null,
                chromeSignals = listOf("empty_text"),
                contentSignals = emptyList()
            )
        }

        val normalized = " ${text.lowercase()} "

        val chromeSignals = mutableListOf<String>()
        val contentSignals = mutableListOf<String>()

        // --------------------------------
        // STEP 1: APP IDENTITY
        // --------------------------------

        val appContext = identifyApp(normalized)

        // --------------------------------
        // STEP 2: CHROME SIGNALS
        // --------------------------------

        // Status bar patterns
        if (hasStatusBar(normalized)) {
            chromeSignals.add("status_bar")
        }

        // Navigation bar patterns
        if (hasNavigationBar(normalized)) {
            chromeSignals.add("navigation_bar")
        }

        // App header / toolbar patterns
        if (hasAppHeader(normalized, appContext)) {
            chromeSignals.add("app_header")
        }

        // Browser chrome (address bar, tab count, etc.)
        if (hasBrowserChrome(normalized)) {
            chromeSignals.add("browser_chrome")
        }

        // Permission / system dialogs
        if (hasSystemDialog(normalized)) {
            chromeSignals.add("system_dialog")
        }

        // --------------------------------
        // STEP 3: CONTENT SIGNALS
        // --------------------------------

        // Video playback indicators
        if (hasVideoContent(normalized)) {
            contentSignals.add("video_playback")
        }

        // Comment section indicators
        if (hasCommentSection(normalized)) {
            contentSignals.add("comment_section")
        }

        // Substantial text content (body text, captions)
        if (hasSubstantialContent(text)) {
            contentSignals.add("substantial_text")
        }

        // View / like / share counts (engagement metrics)
        if (hasEngagementMetrics(normalized)) {
            contentSignals.add("engagement_metrics")
        }

        // Creator / channel name patterns
        if (hasCreatorInfo(normalized)) {
            contentSignals.add("creator_info")
        }

        // Hashtag content
        if (hasHashtags(normalized)) {
            contentSignals.add("hashtags")
        }

        // --------------------------------
        // STEP 4: VERDICT
        // --------------------------------
        //
        // A frame is chrome if:
        //   - it has at least one chrome signal AND no
        //     content signals, OR
        //   - the app was identified but no content
        //     signals were found (app header/toolbar
        //     with just the app name).
        //
        // Mixed frames (e.g. a video player with status
        // bar visible) are treated as content — the
        // user's attention was on the content.

        val isChrome = (chromeSignals.isNotEmpty() &&
            contentSignals.isEmpty()) ||
            (appContext != null &&
                contentSignals.isEmpty())

        return Detection(
            isChromeFrame = isChrome,
            appContext = appContext?.key,
            chromeSignals = chromeSignals,
            contentSignals = contentSignals
        )
    }

    // --------------------------------
    // APP IDENTITY
    // --------------------------------

    private fun identifyApp(normalized: String): AppContext? {
        return APP_PATTERNS.firstOrNull {
            normalized.contains(it.pattern)
        }?.context
    }

    // --------------------------------
    // CHROME HELPERS
    // --------------------------------

    private fun hasStatusBar(normalized: String): Boolean {
        return STATUS_BAR_PATTERNS.any {
            normalized.contains(it)
        }
    }

    private fun hasNavigationBar(normalized: String): Boolean {
        return NAV_BAR_PATTERNS.any {
            normalized.contains(it)
        }
    }

    private fun hasAppHeader(
        normalized: String,
        appContext: AppContext?
    ): Boolean {
        // Platform-specific header signals
        val platformHeaders = when (appContext) {
            AppContext.YOUTUBE -> listOf(
                " subscriptions",
                " home",
                " shorts",
                " library",
                "you tab"
            )
            AppContext.INSTAGRAM -> listOf(
                " stories",
                " reels",
                " explore"
            )
            AppContext.TIKTOK -> listOf(
                " following",
                " for you",
                " discover"
            )
            AppContext.FACEBOOK -> listOf(
                " friends",
                " marketplace",
                " groups",
                " watch"
            )
            AppContext.REDDIT -> listOf(
                " popular",
                " home feed",
                " all"
            )
            else -> emptyList()
        }

        return platformHeaders.any {
            normalized.contains(it)
        }
    }

    private fun hasBrowserChrome(normalized: String): Boolean {
        return BROWSER_CHROME_PATTERNS.any {
            normalized.contains(it)
        }
    }

    private fun hasSystemDialog(normalized: String): Boolean {
        return SYSTEM_DIALOG_PATTERNS.any {
            normalized.contains(it)
        }
    }

    // --------------------------------
    // CONTENT HELPERS
    // --------------------------------

    private fun hasVideoContent(normalized: String): Boolean {
        return VIDEO_CONTENT_PATTERNS.any {
            normalized.contains(it)
        }
    }

    private fun hasCommentSection(normalized: String): Boolean {
        return COMMENT_PATTERNS.any {
            normalized.contains(it)
        }
    }

    private fun hasSubstantialContent(text: String): Boolean {
        // Heuristic: if OCR found more than 50 chars of
        // non-trivial text, there is likely content.
        val contentLength = text
            .replace(Regex("\\s+"), " ")
            .trim()
            .length
        return contentLength > SUBSTANTIAL_TEXT_THRESHOLD
    }

    private fun hasEngagementMetrics(normalized: String): Boolean {
        return ENGAGEMENT_PATTERNS.any {
            normalized.contains(it)
        }
    }

    private fun hasCreatorInfo(normalized: String): Boolean {
        return CREATOR_PATTERNS.any {
            normalized.contains(it)
        }
    }

    private fun hasHashtags(normalized: String): Boolean {
        return normalized.contains(" #") ||
            normalized.contains("hashtag")
    }

    // --------------------------------
    // COMPANION: RULES
    // --------------------------------

    companion object {

        private const val SUBSTANTIAL_TEXT_THRESHOLD = 50

        // App identity patterns (first match wins)
        private val APP_PATTERNS = listOf(
            AppPattern(" youtube", AppContext.YOUTUBE),
            AppPattern(" instagram", AppContext.INSTAGRAM),
            AppPattern(" tiktok", AppContext.TIKTOK),
            AppPattern(" twitter", AppContext.TWITTER),
            AppPattern(" x.com", AppContext.TWITTER),
            AppPattern(" facebook", AppContext.FACEBOOK),
            AppPattern(" reddit", AppContext.REDDIT),
            AppPattern(" linkedin", AppContext.LINKEDIN),
            AppPattern(" snapchat", AppContext.SNAPCHAT),
            AppPattern(" threads", AppContext.THREADS),
            AppPattern(" pinterest", AppContext.PINTEREST),
            AppPattern(" telegram", AppContext.TELEGRAM),
            AppPattern(" whatsapp", AppContext.WHATSAPP),
            AppPattern(" spotify", AppContext.SPOTIFY),
            AppPattern(" chrome ", AppContext.CHROME),
            AppPattern(" safari", AppContext.SAFARI)
        )

        // Status bar heuristics
        private val STATUS_BAR_PATTERNS = listOf(
            " battery",
            " wifi",
            " signal",
            " airplane",
            " bluetooth",
            " alarm",
            " do not disturb",
            " focus",
            " location"
        )

        // Navigation bar heuristics
        private val NAV_BAR_PATTERNS = listOf(
            " recent",
            " overview",
            " back",
            " home",
            " dock"
        )

        // Browser chrome
        private val BROWSER_CHROME_PATTERNS = listOf(
            " https://",
            " http://",
            " .com",
            " tab ",
            " new tab",
            " bookmarks",
            " history",
            " incognito",
            " downloads"
        )

        // System dialogs
        private val SYSTEM_DIALOG_PATTERNS = listOf(
            " permission",
            " allow ",
            " deny ",
            " allow access",
            " notification",
            " update available",
            " low battery",
            " storage full"
        )

        // Video playback content
        private val VIDEO_CONTENT_PATTERNS = listOf(
            " views",
            " subscribers",
            " play ",
            " pause ",
            " like ",
            " share ",
            " comment",
            " description",
            " up next",
            " autoplay",
            " full screen",
            " skip ad",
            " ad ",
            " promoted"
        )

        // Comment section
        private val COMMENT_PATTERNS = listOf(
            " replies",
            " reply",
            " reply to",
            " add a comment",
            " write a comment",
            " sort by",
            " view all",
            " most liked"
        )

        // Engagement metrics
        private val ENGAGEMENT_PATTERNS = listOf(
            " likes",
            " shares",
            " saves",
            " views",
            " subscribers",
            " followers",
            " reposts",
            " quotes"
        )

        // Creator / channel info
        private val CREATOR_PATTERNS = listOf(
            " creator",
            " channel",
            " verified",
            " subscribe",
            " subscribed"
        )
    }

    private data class AppPattern(
        val pattern: String,
        val context: AppContext
    )
}
