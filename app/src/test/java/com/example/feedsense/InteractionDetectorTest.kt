package com.example.feedsense

import com.example.feedsense.analysis.InteractionDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InteractionDetectorTest {

    private val detector =
        InteractionDetector()

    @Test
    fun blankText_returnsNoSignals() {

        assertEquals(
            emptyList<String>(),
            detector.detect(null)
        )

        assertEquals(
            emptyList<String>(),
            detector.detect("")
        )

        assertEquals(
            emptyList<String>(),
            detector.detect("   ")
        )
    }

    @Test
    fun likeCount_raisesLikeSignal() {

        val signals =
            detector.detect(
                "2.1M likes · 1,200 comments"
            )

        assertTrue(
            InteractionDetector.SIGNAL_LIKE in signals
        )

        assertTrue(
            InteractionDetector.SIGNAL_COMMENT in signals
        )
    }

    @Test
    fun shareAndSave_raiseSignals() {

        val signals =
            detector.detect(
                "Share to your story and Save this post"
            )

        assertTrue(
            InteractionDetector.SIGNAL_SHARE in signals
        )

        assertTrue(
            InteractionDetector.SIGNAL_SAVE in signals
        )
    }

    @Test
    fun pauseUi_raisesPauseSignal() {

        val signals =
            detector.detect(
                "Paused · Resume playing"
            )

        assertTrue(
            InteractionDetector.SIGNAL_PAUSE in signals
        )
    }

    @Test
    fun followUi_raisesFollowSignal() {

        val signals =
            detector.detect(
                "Follow @creator and view 12k followers"
            )

        assertTrue(
            InteractionDetector.SIGNAL_FOLLOW in signals
        )
    }

    @Test
    fun contentWordLike_doesNotRaiseSignal() {

        val signals =
            detector.detect(
                "This looks alike my last photo"
            )

        assertTrue(
            InteractionDetector.SIGNAL_LIKE !in signals
        )
    }

    @Test
    fun noEvidence_returnsEmpty() {

        assertEquals(
            emptyList<String>(),
            detector.detect(
                "Today is a wonderful day outside"
            )
        )
    }
}
