package com.example.feedsense.analysis

// --------------------------------
// PLATFORM DETECTOR
// --------------------------------
//
// Milestone 7E.
//
// Scans OCR text for the social platform shown on
// screen so observations can record WHERE the content
// was watched, not just what it was.
//
// Like InteractionDetector, this reports visual
// evidence only: the platform name must actually
// appear in the captured screen text.
//
// Returns the first known platform found, or null
// when the screen text gives no platform evidence.
//

class PlatformDetector {

    fun detect(
        text: String?
    ): String? {

        if (text.isNullOrBlank()) {
            return null
        }

        val normalized =
            " ${text.lowercase()} "

        return PLATFORMS.firstOrNull {
            normalized.contains(it.pattern)
        }?.name
    }

    /*
     * Canonical platform vocabulary (the SINGLE source of
     * truth used by the annotation taxonomy). The heuristic
     * detector only reports a platform when its keyword
     * appears on screen, but the annotation UI lets the
     * evaluator pick any canonical platform as ground truth.
     */
    fun platformNames(): List<String> {
        return PLATFORMS.map { it.name }
    }

    private data class PlatformRule(
        val name: String,
        val pattern: String
    )

    companion object {

        /*
         * Patterns are space-padded so the platform name
         * matches anywhere in the screen text (e.g.
         * "instagram.com", "#instagram", "on Instagram")
         * without matching random substrings of words.
         */
        private val PLATFORMS =
            listOf(
                PlatformRule(
                    "Instagram",
                    " instagram"
                ),
                PlatformRule(
                    "YouTube",
                    " youtube"
                ),
                PlatformRule(
                    "TikTok",
                    " tiktok"
                ),
                PlatformRule(
                    "Facebook",
                    " facebook"
                ),
                PlatformRule(
                    "Snapchat",
                    " snapchat"
                ),
                PlatformRule(
                    "Threads",
                    " threads"
                ),
                PlatformRule(
                    "LinkedIn",
                    " linkedin"
                ),
                PlatformRule(
                    "Reddit",
                    " reddit"
                ),
                PlatformRule(
                    "Pinterest",
                    " pinterest"
                ),
                PlatformRule(
                    "Twitch",
                    " twitch"
                )
            )
    }
}
