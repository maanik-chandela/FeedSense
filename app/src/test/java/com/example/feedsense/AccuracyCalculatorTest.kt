package com.example.feedsense

import com.example.feedsense.analysis.AccuracyCalculator
import com.example.feedsense.model.ModelFeedback
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class AccuracyCalculatorTest {

    private fun feedback(
        topicAgreement: Boolean?,
        toneAgreement: Boolean?
    ): ModelFeedback {

        return ModelFeedback(
            frameId = "frame",
            feedItemId = null,
            sessionId = "session_1",
            correctionSource =
                ModelFeedback.SOURCE_USER,
            categoryAgreement = true,
            topicAgreement = topicAgreement,
            toneAgreement = toneAgreement
        )
    }

    @Test
    fun topicAccuracyIsShareOfAgreements() {

        val rows = listOf(
            feedback(topicAgreement = true, toneAgreement = null),
            feedback(topicAgreement = true, toneAgreement = null),
            feedback(topicAgreement = false, toneAgreement = null)
        )

        assertEquals(
            66.66666666666667,
            AccuracyCalculator.topicAccuracy(rows),
            0.0001
        )
    }

    @Test
    fun topicAccuracyIgnoresRowsWithoutTopicData() {

        val rows = listOf(
            feedback(topicAgreement = null, toneAgreement = null),
            feedback(topicAgreement = true, toneAgreement = null)
        )

        assertEquals(
            100.0,
            AccuracyCalculator.topicAccuracy(rows),
            0.0001
        )
    }

    @Test
    fun toneAccuracyIsZeroWithoutComparableRows() {

        val rows = listOf(
            feedback(topicAgreement = true, toneAgreement = null)
        )

        assertEquals(
            0.0,
            AccuracyCalculator.toneAccuracy(rows),
            0.0001
        )
    }

    @Test
    fun toneAccuracyWorksIndependentlyOfTopic() {

        val rows = listOf(
            feedback(topicAgreement = null, toneAgreement = true),
            feedback(topicAgreement = null, toneAgreement = false)
        )

        assertEquals(
            50.0,
            AccuracyCalculator.toneAccuracy(rows),
            0.0001
        )
    }

    @Test
    fun uncertaintyRateIsShareOfUncertain() {

        assertEquals(
            25.0,
            AccuracyCalculator.uncertaintyRate(
                uncertain = 2,
                total = 8
            ),
            0.0001
        )
    }

    @Test
    fun uncertaintyRateIsZeroWhenNoClassifications() {

        assertEquals(
            0.0,
            AccuracyCalculator.uncertaintyRate(
                uncertain = 3,
                total = 0
            ),
            0.0001
        )
    }
}
