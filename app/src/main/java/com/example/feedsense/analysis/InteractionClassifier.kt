package com.example.feedsense.analysis

import com.example.feedsense.model.FeedItem

// --------------------------------
// INTERACTION CLASSIFIER
// --------------------------------
//
// Milestone 7N.
//
// Distinguishes CONTENT OBSERVATION from USER
// INTERACTION at the FeedItem level:
//
//   observation   watched / skipped / paused / resumed /
//                 stopped - derived from timing and
//                 lifecycle transitions.
//   interaction   liked / commented / shared / saved /
//                 followed - only reported when the UI
//                 affordance text (OCR evidence) is
//                 unambiguous.
//
// Honesty rules:
//
//   - A short glance  -> skipped (high confidence).
//   - A long dwell    -> watched (high confidence).
//   - A visual change that merely resembles a button is
//     NEVER turned into an interaction.
//   - When in doubt the classifier returns UNKNOWN (no
//     signal), never a fabricated one.
//
// Confidence is encoded in each BehavioralInteraction and
// the caller stores it with the signal, so downstream
// code can always tell how sure the detector was.
//

class InteractionClassifier {

    data class BehavioralInteraction(
        val signal: String,
        val confidence: ConfidenceLevel,
        val evidence: String
    )

    /*
     * durationSeconds     how long the item was on screen
     * skipped             builder-level skip flag
     * pausedSeconds       estimated paused time in the item
     * transitions         FeedItem content transitions
     * uiEvidence          frame-level "signal|CONF|evidence"
     *                     strings (InteractionSignal encoding)
     */
    fun classify(
        durationSeconds: Int,
        skipped: Boolean,
        pausedSeconds: Int,
        transitions: List<String>,
        uiEvidence: List<String>
    ): List<BehavioralInteraction> {

        val result =
            mutableListOf<BehavioralInteraction>()

        // --------------------------------
        // CONTENT OBSERVATION
        // --------------------------------

        if (skipped || durationSeconds <= SKIPPED_MAX_SECONDS) {

            result += BehavioralInteraction(
                signal = SIGNAL_SKIPPED,
                confidence = ConfidenceLevel.HIGH,
                evidence = "duration=${durationSeconds}s"
            )

        } else if (durationSeconds >= WATCHED_MIN_SECONDS) {

            result += BehavioralInteraction(
                signal = SIGNAL_WATCHED,
                confidence = ConfidenceLevel.HIGH,
                evidence = "duration=${durationSeconds}s"
            )

            /*
             * Between the two thresholds the dwell time is
             * ambiguous -> UNKNOWN, i.e. no signal at all.
             */
        }

        if (pausedSeconds >= PAUSED_MIN_SECONDS) {

            result += BehavioralInteraction(
                signal = SIGNAL_PAUSED,
                confidence = ConfidenceLevel.HIGH,
                evidence = "paused=${pausedSeconds}s"
            )

            if (
                FeedItem.TRANSITION_CONTENT_CONTINUED
                in transitions
            ) {
                result += BehavioralInteraction(
                    signal = SIGNAL_RESUMED,
                    confidence = ConfidenceLevel.MEDIUM,
                    evidence = "paused then continued"
                )
            }
        }

        if (
            FeedItem.TRANSITION_CONTENT_ENDED
            in transitions
        ) {
            result += BehavioralInteraction(
                signal = SIGNAL_STOPPED,
                confidence = ConfidenceLevel.MEDIUM,
                evidence = "content ended"
            )
        }

        // --------------------------------
        // USER INTERACTION (UI evidence only)
        // --------------------------------
        //
        // Passed through only when the OCR evidence was
        // an explicit affordance (HIGH confidence in the
        // frame detector). Anything ambiguous stays out.

        result += parseEvidence(uiEvidence)

        return result
    }

    /*
     * Parses "signal|CONF|evidence" entries from frame
     * analysis and keeps only HIGH-confidence ones - the
     * explicit-affordance rule. The signal ids are
     * remapped to the user-facing vocabulary.
     */
    private fun parseEvidence(
        entries: List<String>
    ): List<BehavioralInteraction> {

        if (entries.isEmpty()) {
            return emptyList()
        }

        return entries.mapNotNull { entry ->

            val parts =
                entry.split(
                    InteractionSignal.EVIDENCE_SEPARATOR
                )

            if (parts.size < 3) {
                return@mapNotNull null
            }

            val signal =
                parts[0]

            val confidence =
                ConfidenceLevel
                    .values()
                    .firstOrNull {
                        it.name == parts[1]
                    }
                    ?: return@mapNotNull null

            if (confidence != ConfidenceLevel.HIGH) {
                return@mapNotNull null
            }

            val userSignal =
                USER_SIGNAL_MAP[signal]
                    ?: return@mapNotNull null

            BehavioralInteraction(
                signal = userSignal,
                confidence = confidence,
                evidence = parts[2]
            )
        }
    }

    companion object {

        const val SKIPPED_MAX_SECONDS = 5

        const val WATCHED_MIN_SECONDS = 10

        const val PAUSED_MIN_SECONDS = 4

        const val SIGNAL_WATCHED =
            "watched"

        const val SIGNAL_SKIPPED =
            "skipped"

        const val SIGNAL_PAUSED =
            "paused"

        const val SIGNAL_RESUMED =
            "resumed"

        const val SIGNAL_STOPPED =
            "stopped"

        const val SIGNAL_LIKED =
            "liked"

        const val SIGNAL_COMMENTED =
            "commented"

        const val SIGNAL_SHARED =
            "shared"

        const val SIGNAL_SAVED =
            "saved"

        const val SIGNAL_FOLLOWED =
            "followed"

        const val SIGNAL_PLAYING =
            "playing"

        const val SIGNAL_UNKNOWN =
            "unknown"

        /*
         * Frame-level UI signal ids -> user-facing
         * vocabulary. Only explicit, HIGH-confidence
         * affordances are mapped.
         */
        private val USER_SIGNAL_MAP: Map<String, String> =
            mapOf(
                InteractionDetector.SIGNAL_LIKE to SIGNAL_LIKED,
                InteractionDetector.SIGNAL_COMMENT to SIGNAL_COMMENTED,
                InteractionDetector.SIGNAL_SHARE to SIGNAL_SHARED,
                InteractionDetector.SIGNAL_SAVE to SIGNAL_SAVED,
                InteractionDetector.SIGNAL_FOLLOW to SIGNAL_FOLLOWED,
                InteractionDetector.SIGNAL_PLAYING to SIGNAL_PLAYING
            )
    }
}
