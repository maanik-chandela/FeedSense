package com.example.feedsense.analysis

// --------------------------------
// INTERACTION DETECTOR
// --------------------------------
//
// Milestone 7D.
//
// Scans OCR text for *visual evidence* of UI state
// that implies an interaction or playback state.
//
// Important honesty rule (7D-A):
//
// These are INDICATORS, not claims. If the OCR text
// contains "2,345 likes" we know a like interaction
// happened (someone liked the post), but we never
// infer an interaction without such evidence.
//
// Signal vocabulary (lowercase, stable):
//
// - like_indicator       : "2.1M likes", "liked by ..."
// - comment_indicator    : "Add a comment", "1,200 comments"
// - share_indicator      : "Share", "Send", "Repost"
// - save_indicator       : "Save", "Bookmark", "Collect"
// - follow_indicator     : "Follow", "Following", "followers"
// - playback_paused      : "Pause", "Paused", "Resume"
// - playback_playing     : "Now playing", "Replay", "Playing"
//

class InteractionDetector {

    fun detect(
        text: String?
    ): List<String> {

        if (text.isNullOrBlank()) {
            return emptyList()
        }

        val normalized =
            " ${text.lowercase()} "

        return buildList {

            if (matchesAny(normalized, LIKE_PATTERNS)) {
                add(SIGNAL_LIKE)
            }

            if (matchesAny(normalized, COMMENT_PATTERNS)) {
                add(SIGNAL_COMMENT)
            }

            if (matchesAny(normalized, SHARE_PATTERNS)) {
                add(SIGNAL_SHARE)
            }

            if (matchesAny(normalized, SAVE_PATTERNS)) {
                add(SIGNAL_SAVE)
            }

            if (matchesAny(normalized, FOLLOW_PATTERNS)) {
                add(SIGNAL_FOLLOW)
            }

            if (matchesAny(normalized, PAUSE_PATTERNS)) {
                add(SIGNAL_PAUSE)
            }

            if (matchesAny(normalized, PLAYING_PATTERNS)) {
                add(SIGNAL_PLAYING)
            }
        }
    }

    private fun matchesAny(
        normalized: String,
        patterns: List<String>
    ): Boolean {

        return patterns.any {
            normalized.contains(it)
        }
    }

    companion object {

        const val SIGNAL_LIKE =
            "like_indicator"

        const val SIGNAL_COMMENT =
            "comment_indicator"

        const val SIGNAL_SHARE =
            "share_indicator"

        const val SIGNAL_SAVE =
            "save_indicator"

        const val SIGNAL_FOLLOW =
            "follow_indicator"

        const val SIGNAL_PAUSE =
            "playback_paused"

        const val SIGNAL_PLAYING =
            "playback_playing"

        /*
         * Patterns are space-padded so "like" inside a
         * normal word (e.g. "alike") does not match.
         */
        private val LIKE_PATTERNS =
            listOf(
                " like",
                " likes",
                " liked",
                "like this",
                " double tap",
                "reactions",
                " hearts"
            )

        private val COMMENT_PATTERNS =
            listOf(
                " comment",
                " comments",
                "add a comment",
                "add comment",
                " reply",
                " replies",
                "view all comments"
            )

        private val SHARE_PATTERNS =
            listOf(
                " share",
                " shares",
                " send",
                " repost",
                "share this",
                "share to"
            )

        private val SAVE_PATTERNS =
            listOf(
                " save",
                " saves",
                " bookmark",
                " collect",
                " save this"
            )

        private val FOLLOW_PATTERNS =
            listOf(
                " follow",
                " following",
                " followers",
                "follow back"
            )

        private val PAUSE_PATTERNS =
            listOf(
                " pause",
                " paused",
                " resume",
                "resume playing"
            )

        private val PLAYING_PATTERNS =
            listOf(
                " now playing",
                " replay",
                " playing",
                "watch now"
            )
    }
}