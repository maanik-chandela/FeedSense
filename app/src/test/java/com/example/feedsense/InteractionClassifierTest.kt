package com.example.feedsense

import com.example.feedsense.analysis.ConfidenceLevel
import com.example.feedsense.analysis.InteractionClassifier
import com.example.feedsense.analysis.InteractionDetector
import com.example.feedsense.analysis.InteractionSignal
import com.example.feedsense.model.FeedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionClassifierTest {

    private val classifier =
        InteractionClassifier()

    private fun evidence(
        signal: String,
        confidence: ConfidenceLevel
    ): String {
        return listOf(
            signal,
            confidence.name,
            "matched: test"
        ).joinToString(
            InteractionSignal.EVIDENCE_SEPARATOR
        )
    }

    private fun classify(
        duration: Int,
        skipped: Boolean = false,
        paused: Int = 0,
        transitions: List<String> = emptyList(),
        uiEvidence: List<String> = emptyList()
    ): List<InteractionClassifier.BehavioralInteraction> {

        return classifier.classify(
            durationSeconds = duration,
            skipped = skipped,
            pausedSeconds = paused,
            transitions = transitions,
            uiEvidence = uiEvidence
        )
    }

    @Test
    fun twoSecondReelIsSkipped() {

        val result =
            classify(duration = 2)

        assertTrue(
            result.any {
                it.signal == InteractionClassifier.SIGNAL_SKIPPED &&
                    it.confidence == ConfidenceLevel.HIGH
            }
        )
        assertTrue(
            result.none {
                it.signal == InteractionClassifier.SIGNAL_WATCHED
            }
        )
    }

    @Test
    fun shortGlanceBeforeContentChangeIsSkipped() {

        val result =
            classify(
                duration = 3,
                transitions = listOf(
                    FeedItem.TRANSITION_CONTENT_CHANGED
                )
            )

        assertTrue(
            result.any {
                it.signal == InteractionClassifier.SIGNAL_SKIPPED
            }
        )
    }

    @Test
    fun thirtyFiveSecondReelIsWatched() {

        val result =
            classify(duration = 35)

        assertTrue(
            result.any {
                it.signal == InteractionClassifier.SIGNAL_WATCHED &&
                    it.confidence == ConfidenceLevel.HIGH
            }
        )
        assertTrue(
            result.none {
                it.signal == InteractionClassifier.SIGNAL_SKIPPED
            }
        )
    }

    @Test
    fun ambiguousDwellIsUnknown() {

        // 6 seconds is between skipped and watched.
        val result =
            classify(duration = 6)

        assertTrue(
            result.none {
                it.signal == InteractionClassifier.SIGNAL_WATCHED ||
                    it.signal == InteractionClassifier.SIGNAL_SKIPPED
            }
        )
    }

    @Test
    fun pauseProducesPausedAndResumed() {

        val result =
            classify(
                duration = 30,
                paused = 6,
                transitions = listOf(
                    FeedItem.TRANSITION_CONTENT_CONTINUED
                )
            )

        assertTrue(
            result.any {
                it.signal == InteractionClassifier.SIGNAL_PAUSED
            }
        )
        assertTrue(
            result.any {
                it.signal == InteractionClassifier.SIGNAL_RESUMED
            }
        )
    }

    @Test
    fun shortPauseIsNotClaimed() {

        val result =
            classify(
                duration = 30,
                paused = 2
            )

        assertTrue(
            result.none {
                it.signal == InteractionClassifier.SIGNAL_PAUSED
            }
        )
    }

    @Test
    fun highConfidenceLikeEvidenceBecomesLiked() {

        val result =
            classify(
                duration = 30,
                uiEvidence = listOf(
                    evidence(
                        InteractionDetector.SIGNAL_LIKE,
                        ConfidenceLevel.HIGH
                    )
                )
            )

        assertTrue(
            result.any {
                it.signal == InteractionClassifier.SIGNAL_LIKED
            }
        )
    }

    @Test
    fun lowConfidenceLikeEvidenceIsNotClaimed() {

        // The frame detector tags "playing" as LOW. The
        // classifier must NOT promote it to an
        // interaction claim.
        val result =
            classify(
                duration = 30,
                uiEvidence = listOf(
                    evidence(
                        InteractionDetector.SIGNAL_PLAYING,
                        ConfidenceLevel.LOW
                    )
                )
            )

        assertTrue(
            result.none {
                it.signal == InteractionClassifier.SIGNAL_PLAYING ||
                    it.signal == InteractionClassifier.SIGNAL_LIKED
            }
        )
    }

    @Test
    fun endedContentProducesStopped() {

        val result =
            classify(
                duration = 30,
                transitions = listOf(
                    FeedItem.TRANSITION_CONTENT_ENDED
                )
            )

        assertTrue(
            result.any {
                it.signal == InteractionClassifier.SIGNAL_STOPPED &&
                    it.confidence == ConfidenceLevel.MEDIUM
            }
        )
    }

    @Test
    fun everySignalCarriesConfidenceAndEvidence() {

        val result =
            classify(
                duration = 35,
                paused = 8,
                transitions = listOf(
                    FeedItem.TRANSITION_CONTENT_CONTINUED,
                    FeedItem.TRANSITION_CONTENT_ENDED
                )
            )

        assertTrue(result.isNotEmpty())
        result.forEach {
            assertTrue(it.evidence.isNotBlank())
        }
    }
}
