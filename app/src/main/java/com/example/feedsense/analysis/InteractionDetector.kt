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

// --------------------------------
// INTERACTION SIGNAL
// --------------------------------
//
// Milestone 7F (Part 3).
//
// One detected interaction/playback indicator with its
// confidence level and the raw visual evidence that
// produced it. The evidence is the matched UI text, so
// every signal stays auditable and honest.
//
// Confidence reflects how unambiguous the UI text is:
//
// - HIGH   for explicit affordances ("Paused",
//          "2,345 likes", "Save", ...)
// - LOW    for weaker phrases ("playing", "watch now")
//

data class InteractionSignal(
    val signal: String,
    val confidence: ConfidenceLevel,
    val evidence: String
) {

    /*
     * Stable, pipe-separated encoding used in the frame
     * analysis result JSON and the learning dataset:
     *
     *   "like_indicator|HIGH|matched: likes"
     */
    fun toEvidenceString(): String {
        return listOf(
            signal,
            confidence.name,
            evidence
        ).joinToString(EVIDENCE_SEPARATOR)
    }

    companion object {

        const val EVIDENCE_SEPARATOR = "|"
    }
}

class InteractionDetector {

    fun detect(
        text: String?
    ): List<String> {

        return detectWithEvidence(text).map {
            it.signal
        }
    }

    fun detectWithEvidence(
        text: String?
    ): List<InteractionSignal> {

        if (text.isNullOrBlank()) {
            return emptyList()
        }

        val normalized =
            " ${text.lowercase()} "

        return buildList {

            firstMatch(normalized, LIKE_PATTERNS)?.let {
                add(
                    InteractionSignal(
                        signal = SIGNAL_LIKE,
                        confidence = ConfidenceLevel.HIGH,
                        evidence = "matched: $it"
                    )
                )
            }

            firstMatch(normalized, COMMENT_PATTERNS)?.let {
                add(
                    InteractionSignal(
                        signal = SIGNAL_COMMENT,
                        confidence = ConfidenceLevel.HIGH,
                        evidence = "matched: $it"
                    )
                )
            }

            firstMatch(normalized, SHARE_PATTERNS)?.let {
                add(
                    InteractionSignal(
                        signal = SIGNAL_SHARE,
                        confidence = ConfidenceLevel.HIGH,
                        evidence = "matched: $it"
                    )
                )
            }

            firstMatch(normalized, SAVE_PATTERNS)?.let {
                add(
                    InteractionSignal(
                        signal = SIGNAL_SAVE,
                        confidence = ConfidenceLevel.HIGH,
                        evidence = "matched: $it"
                    )
                )
            }

            firstMatch(normalized, FOLLOW_PATTERNS)?.let {
                add(
                    InteractionSignal(
                        signal = SIGNAL_FOLLOW,
                        confidence = ConfidenceLevel.HIGH,
                        evidence = "matched: $it"
                    )
                )
            }

            firstMatch(normalized, PAUSE_PATTERNS)?.let {
                add(
                    InteractionSignal(
                        signal = SIGNAL_PAUSE,
                        confidence = ConfidenceLevel.HIGH,
                        evidence = "matched: $it"
                    )
                )
            }

            firstMatch(normalized, PLAYING_PATTERNS)?.let {
                add(
                    InteractionSignal(
                        signal = SIGNAL_PLAYING,
                        confidence = ConfidenceLevel.LOW,
                        evidence = "matched: $it"
                    )
                )
            }
        }
    }

    private fun firstMatch(
        normalized: String,
        patterns: List<String>
    ): String? {

        return patterns.firstOrNull {
            normalized.contains(it)
        }?.trim()
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