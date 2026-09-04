package com.example.feedsense

import com.example.feedsense.model.LabeledReference
import com.example.feedsense.model.ModelFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelFeedbackTest {

    private fun reference(
        aiCategory: String? = "comedy",
        topic: String? = "cooking",
        tone: String? = "humorous",
        modelVersion: String? = "heuristic-v5"
    ): LabeledReference {

        return LabeledReference(
            frameId = "frame_1",
            sessionId = "session_1",
            filePath = "path",
            feedItemId = "item_1",
            aiCategory = aiCategory,
            aiConfidence = 0.55,
            aiSource = "LOCAL",
            modelVersion = modelVersion,
            candidateCategories = listOf("comedy", "sports"),
            platform = "Instagram",
            topic = topic,
            tone = tone,
            visibleText = "text",
            interactionSignals = listOf("skipped"),
            labelSource =
                LabeledReference.LABEL_SOURCE_HUMAN,
            validationStatus =
                LabeledReference.VALIDATION_PENDING
        )
    }

    @Test
    fun correctionPreservesOriginalPrediction() {

        val feedback =
            ModelFeedback.fromCorrection(
                reference = reference(),
                correctedCategory = "sports",
                correctedTopic = null,
                correctedTone = null,
                correctionSource =
                    ModelFeedback.SOURCE_USER
            )

        assertEquals("comedy", feedback.originalCategory)
        assertEquals(0.55, feedback.originalConfidence ?: 0.0, 0.0001)
        assertEquals("cooking", feedback.originalTopic)
        assertEquals("humorous", feedback.originalTone)
        assertEquals("heuristic-v5", feedback.modelVersion)
    }

    @Test
    fun correctionKeepsCorrectedValues() {

        val feedback =
            ModelFeedback.fromCorrection(
                reference = reference(),
                correctedCategory = "sports",
                correctedTopic = "baking",
                correctedTone = "calm",
                correctionSource =
                    ModelFeedback.SOURCE_USER
            )

        assertEquals("sports", feedback.correctedCategory)
        assertEquals("baking", feedback.correctedTopic)
        assertEquals("calm", feedback.correctedTone)
        assertEquals(1.0, feedback.confidenceAfterCorrection ?: 0.0, 0.0001)
    }

    @Test
    fun agreementIsTrueWhenHumanKeepsPrediction() {

        val feedback =
            ModelFeedback.fromCorrection(
                reference = reference(
                    aiCategory = "comedy",
                    topic = "cooking",
                    tone = "humorous"
                ),
                correctedCategory = "comedy",
                correctedTopic = "cooking",
                correctedTone = "humorous",
                correctionSource =
                    ModelFeedback.SOURCE_USER
            )

        assertTrue(feedback.categoryAgreement)
        assertEquals(true, feedback.topicAgreement)
        assertEquals(true, feedback.toneAgreement)
    }

    @Test
    fun agreementIsFalseOnCorrection() {

        val feedback =
            ModelFeedback.fromCorrection(
                reference = reference(
                    aiCategory = "comedy"
                ),
                correctedCategory = "sports",
                correctedTopic = "baking",
                correctedTone = "calm",
                correctionSource =
                    ModelFeedback.SOURCE_USER
            )

        assertFalse(feedback.categoryAgreement)
        assertEquals(false, feedback.topicAgreement)
        assertEquals(false, feedback.toneAgreement)
    }

    @Test
    fun agreementIsNullWhenNeitherSideHasTopic() {

        val feedback =
            ModelFeedback.fromCorrection(
                reference = reference(
                    topic = null,
                    tone = null
                ),
                correctedCategory = "comedy",
                correctedTopic = "",
                correctedTone = "  ",
                correctionSource =
                    ModelFeedback.SOURCE_USER
            )

        assertNull(feedback.topicAgreement)
        assertNull(feedback.toneAgreement)
    }

    @Test
    fun agreementIsFalseWhenHumanAddsTopicTheAiMissed() {

        val feedback =
            ModelFeedback.fromCorrection(
                reference = reference(
                    topic = null,
                    tone = null
                ),
                correctedCategory = "comedy",
                correctedTopic = "cooking",
                correctedTone = "humorous",
                correctionSource =
                    ModelFeedback.SOURCE_USER
            )

        assertEquals(false, feedback.topicAgreement)
        assertEquals(false, feedback.toneAgreement)
    }

    @Test
    fun recordsContextAndSource() {

        val feedback =
            ModelFeedback.fromCorrection(
                reference = reference(),
                correctedCategory = "comedy",
                correctedTopic = "cooking",
                correctedTone = "humorous",
                correctionSource =
                    ModelFeedback.SOURCE_USER
            )

        assertEquals("frame_1", feedback.frameId)
        assertEquals("item_1", feedback.feedItemId)
        assertEquals("session_1", feedback.sessionId)
        assertEquals("Instagram", feedback.platform)
        assertEquals("text", feedback.visibleText)
        assertEquals(listOf("skipped"), feedback.interactionSignals)
        assertEquals(
            ModelFeedback.SOURCE_USER,
            feedback.correctionSource
        )
    }
}
