package com.example.feedsense

import com.example.feedsense.analysis.ConfidenceGate
import com.example.feedsense.analysis.ConfidenceLevel
import com.example.feedsense.analysis.ReferenceConfidence
import com.example.feedsense.analysis.ReferenceMemory
import com.example.feedsense.analysis.ReviewEligibility
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.LabeledReference
import com.example.feedsense.model.ModelFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// --------------------------------
// CORRECTION PIPELINE TEST
// --------------------------------
//
// Milestone 7X / 7Z verification.
//
// End-to-end-style tests for the human correction flow:
//   uncertain item → review queue → human correction
//   → learning record → confidence refinement.
//
// These are pure-function tests (no Room) verifying the
// logic contract between the pipeline components.
//

class CorrectionPipelineTest {

    // --------------------------------
    // 7X: CONFIDENCE GATE → REVIEW
    // --------------------------------

    @Test
    fun lowConfidenceItemEntersReviewQueue() {

        val gate = ConfidenceGate()

        val result = com.example.feedsense.analysis.FrameAnalysisResult(
            status = "ANALYZED",
            fileName = "frame.png",
            width = 1080,
            height = 1920,
            fileSizeBytes = 1024,
            message = "test",
            contentCategory = "comedy",
            confidence = 0.65,
            source = "LOCAL",
            modelVersion = "local-v6.0"
        )

        val disposition = gate.evaluate(result)

        // 0.65 is in the medium band (0.6 .. 0.8).
        assertEquals(
            com.example.feedsense.analysis.AnalysisDisposition.MEDIUM_CONFIDENCE,
            disposition
        )
    }

    @Test
    fun ambiguousItemEntersReviewQueue() {

        val gate = ConfidenceGate()

        val result = com.example.feedsense.analysis.FrameAnalysisResult(
            status = "ANALYZED",
            fileName = "frame.png",
            width = 1080,
            height = 1920,
            fileSizeBytes = 1024,
            message = "test",
            contentCategory = "comedy",
            confidence = 0.65,
            ambiguityScore = 0.85,
            source = "LOCAL",
            modelVersion = "local-v6.0"
        )

        val disposition = gate.evaluate(result)

        assertEquals(
            com.example.feedsense.analysis.AnalysisDisposition.AMBIGUOUS,
            disposition
        )
    }

    @Test
    fun highConfidenceItemIsAcceptedLocally() {

        val gate = ConfidenceGate()

        val result = com.example.feedsense.analysis.FrameAnalysisResult(
            status = "ANALYZED",
            fileName = "frame.png",
            width = 1080,
            height = 1920,
            fileSizeBytes = 1024,
            message = "test",
            contentCategory = "comedy",
            confidence = 0.92,
            source = "LOCAL",
            modelVersion = "local-v6.0"
        )

        val disposition = gate.evaluate(result)

        assertEquals(
            com.example.feedsense.analysis.AnalysisDisposition.LOCAL_ACCEPTED,
            disposition
        )
    }

    // --------------------------------
    // 7X: HUMAN CORRECTION → LEARNING
    // --------------------------------

    @Test
    fun correctionFlowProducesLearningRecord() {

        val reference = LabeledReference(
            frameId = "frame_1",
            sessionId = "session_1",
            filePath = "/tmp/frame.png",
            feedItemId = "item_1",
            aiCategory = "motivation",
            aiConfidence = 0.48,
            aiSource = "LOCAL",
            modelVersion = "local-v6.0",
            candidateCategories = listOf("motivation", "comedy"),
            platform = "Instagram",
            topic = "fitness",
            tone = "inspirational",
            visibleText = "never give up",
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            validationStatus = LabeledReference.VALIDATION_PENDING
        )

        val feedback = ModelFeedback.fromCorrection(
            reference = reference,
            correctedCategory = "comedy",
            correctedTopic = "workout",
            correctedTone = "humorous",
            correctionSource = ModelFeedback.SOURCE_USER
        )

        // Original prediction is preserved.
        assertEquals("motivation", feedback.originalCategory)
        assertEquals(0.48, feedback.originalConfidence!!, 0.001)
        assertEquals("fitness", feedback.originalTopic)
        assertEquals("inspirational", feedback.originalTone)
        assertEquals("local-v6.0", feedback.modelVersion)

        // Corrected values are stored.
        assertEquals("comedy", feedback.correctedCategory)
        assertEquals("workout", feedback.correctedTopic)
        assertEquals("humorous", feedback.correctedTone)

        // Disagreement is recorded.
        assertFalse(feedback.categoryAgreement)
        assertFalse(feedback.topicAgreement!!)
        assertFalse(feedback.toneAgreement!!)
    }

    @Test
    fun confirmationFlowPreservesOriginalAndMarksAgreement() {

        val reference = LabeledReference(
            frameId = "frame_2",
            sessionId = "session_1",
            filePath = "/tmp/frame2.png",
            feedItemId = "item_2",
            aiCategory = "sports",
            aiConfidence = 0.85,
            aiSource = "LOCAL",
            modelVersion = "local-v6.0",
            platform = "YouTube",
            topic = "cricket",
            tone = "energetic",
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            validationStatus = LabeledReference.VALIDATION_PENDING
        )

        val feedback = ModelFeedback.fromCorrection(
            reference = reference,
            correctedCategory = "sports",
            correctedTopic = "cricket",
            correctedTone = "energetic",
            correctionSource = ModelFeedback.SOURCE_USER
        )

        // User confirmed the AI prediction.
        assertTrue(feedback.categoryAgreement)
        assertTrue(feedback.topicAgreement!!)
        assertTrue(feedback.toneAgreement!!)
    }

    // --------------------------------
    // 7X: UNCERTAINTY REASONS
    // --------------------------------

    @Test
    fun reviewEligibility_coversAllUncertainReasons() {

        val eligibility = ReviewEligibility()

        val decision = eligibility.decide(
            baseUncertain = true,
            conflict = true,
            categories = listOf("comedy", "comedy", "sports", "sports"),
            secondaryCategory = "music",
            topics = listOf("cooking", "recipe", "prank"),
            tones = listOf("humorous", "serious"),
            applications = listOf("Instagram", "TikTok"),
            unknownCategory = true,
            unknownTopic = true,
            unknownTone = true
        )

        assertTrue(decision.needsReview)

        val expectedReasons = setOf(
            ReviewEligibility.REASON_LOW_CONFIDENCE,
            ReviewEligibility.REASON_CONFLICTING_SIGNALS,
            ReviewEligibility.REASON_UNKNOWN_CATEGORY,
            ReviewEligibility.REASON_UNKNOWN_TOPIC,
            ReviewEligibility.REASON_UNKNOWN_TONE,
            ReviewEligibility.REASON_CATEGORY_TIE,
            ReviewEligibility.REASON_MIXED_CONTENT,
            ReviewEligibility.REASON_TOPIC_AMBIGUOUS,
            ReviewEligibility.REASON_TONE_AMBIGUOUS,
            ReviewEligibility.REASON_PLATFORM_CONFLICT
        )

        assertEquals(expectedReasons, decision.reasons.toSet())
    }

    // --------------------------------
    // 7Y: REFERENCE MEMORY → FEEDBACK
    // --------------------------------

    @Test
    fun validatedReferenceActAsMemoryForFutureItems() {

        val memory = ReferenceMemory()

        val validatedRef = LabeledReference(
            frameId = "frame_past",
            sessionId = "session_old",
            filePath = "/tmp/past.png",
            aiCategory = "comedy",
            aiConfidence = 0.7,
            aiSource = "LOCAL",
            modelVersion = "local-v5.0",
            platform = "Instagram",
            topic = "prank",
            visibleText = "funny challenge",
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            validationStatus = LabeledReference.VALIDATION_VALIDATED,
            validatedLabel = "comedy"
        )

        val matches = memory.similarReferences(
            references = listOf(validatedRef),
            categoryCandidates = setOf("comedy"),
            platform = "Instagram",
            topic = "prank",
            visibleTexts = listOf("funny challenge"),
            fingerprints = emptyList()
        )

        assertTrue(matches.isNotEmpty())
        assertEquals("comedy", matches[0].reference.validatedLabel)
        assertTrue(matches[0].similarity >= 0.6)
    }

    @Test
    fun referenceConfidence_boostsWithMultipleMatches() {

        val confidence = ReferenceConfidence()
        val memory = ReferenceMemory()

        val refs = (1..5).map {
            LabeledReference(
                frameId = "frame_$it",
                sessionId = "session_1",
                filePath = "/tmp/frame_$it.png",
                aiCategory = "sports",
                aiConfidence = 0.8,
                aiSource = "LOCAL",
                modelVersion = "local-v6.0",
                platform = "YouTube",
                topic = "cricket",
                labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
                validationStatus = LabeledReference.VALIDATION_VALIDATED,
                validatedLabel = "sports"
            )
        }

        val matches = memory.similarReferences(
            references = refs,
            categoryCandidates = setOf("sports"),
            platform = "YouTube",
            topic = "cricket",
            visibleTexts = emptyList(),
            fingerprints = emptyList()
        )

        val boost = confidence.apply(
            predicted = "sports",
            candidates = listOf("sports"),
            confidence = 0.7,
            matches = matches
        )

        assertTrue(boost.boosted)
        assertFalse(boost.conflict)
        // 0.70 + 3 * 0.05 = 0.85 (capped at MAX_BOOST_REFS = 3)
        assertEquals(0.85, boost.confidence!!, 0.001)
        // supportingReferences reflects the raw match count.
        assertTrue(boost.supportingReferences >= 3)
    }

    @Test
    fun conflictingReferencesCauseConflict() {

        val confidence = ReferenceConfidence()
        val memory = ReferenceMemory()

        val refs = listOf(
            LabeledReference(
                frameId = "frame_1",
                sessionId = "session_1",
                filePath = "/tmp/f1.png",
                aiCategory = "comedy",
                aiConfidence = 0.7,
                aiSource = "LOCAL",
                modelVersion = "local-v6.0",
                labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
                validationStatus = LabeledReference.VALIDATION_VALIDATED,
                validatedLabel = "comedy"
            ),
            LabeledReference(
                frameId = "frame_2",
                sessionId = "session_1",
                filePath = "/tmp/f2.png",
                aiCategory = "sports",
                aiConfidence = 0.7,
                aiSource = "LOCAL",
                modelVersion = "local-v6.0",
                labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
                validationStatus = LabeledReference.VALIDATION_VALIDATED,
                validatedLabel = "sports"
            ),
            LabeledReference(
                frameId = "frame_3",
                sessionId = "session_1",
                filePath = "/tmp/f3.png",
                aiCategory = "sports",
                aiConfidence = 0.7,
                aiSource = "LOCAL",
                modelVersion = "local-v6.0",
                labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
                validationStatus = LabeledReference.VALIDATION_VALIDATED,
                validatedLabel = "sports"
            )
        )

        val matches = memory.similarReferences(
            references = refs,
            categoryCandidates = setOf("comedy", "sports"),
            platform = null,
            topic = null,
            visibleTexts = emptyList(),
            fingerprints = emptyList()
        )

        val boost = confidence.apply(
            predicted = "comedy",
            candidates = listOf("comedy", "sports"),
            confidence = 0.7,
            matches = matches
        )

        // 2 sports references vs 1 comedy reference = conflict.
        assertTrue(boost.conflict)
        assertFalse(boost.boosted)
    }

    // --------------------------------
    // 7Z: FEED ITEM STATES
    // --------------------------------

    @Test
    fun feedItemContentTypesCorrectly() {

        assertEquals("SHORT_VIDEO", FeedItem.CONTENT_SHORT_VIDEO)
        assertEquals("LONG_VIDEO", FeedItem.CONTENT_LONG_VIDEO)
        assertEquals("UNKNOWN", FeedItem.CONTENT_UNKNOWN)
    }

    @Test
    fun feedItemUncertaintyLevelsAreDefined() {

        assertEquals("HIGH", FeedItem.UNCERTAINTY_HIGH)
        assertEquals("MEDIUM", FeedItem.UNCERTAINTY_MEDIUM)
        assertEquals("LOW", FeedItem.UNCERTAINTY_LOW)
    }

    @Test
    fun feedItemContentTransitionsAreDefined() {

        assertEquals("CONTENT_STARTED", FeedItem.TRANSITION_CONTENT_STARTED)
        assertEquals("CONTENT_CONTINUED", FeedItem.TRANSITION_CONTENT_CONTINUED)
        assertEquals("CONTENT_CHANGED", FeedItem.TRANSITION_CONTENT_CHANGED)
        assertEquals("CONTENT_SKIPPED", FeedItem.TRANSITION_CONTENT_SKIPPED)
        assertEquals("CONTENT_ENDED", FeedItem.TRANSITION_CONTENT_ENDED)
    }

    @Test
    fun labeledReferenceValidationStatesAreComplete() {

        assertEquals("PENDING", LabeledReference.VALIDATION_PENDING)
        assertEquals("VALIDATED", LabeledReference.VALIDATION_VALIDATED)
        assertEquals("REJECTED", LabeledReference.VALIDATION_REJECTED)
        assertEquals("SKIPPED", LabeledReference.VALIDATION_SKIPPED)
    }

    @Test
    fun modelFeedbackSourceTypesAreComplete() {

        assertEquals("USER", ModelFeedback.SOURCE_USER)
        assertEquals("CLOUD_REFERENCE", ModelFeedback.SOURCE_CLOUD_REFERENCE)
        assertEquals("LOCAL_MODEL", ModelFeedback.SOURCE_LOCAL_MODEL)
        assertEquals("SYSTEM", ModelFeedback.SOURCE_SYSTEM)
    }

    // --------------------------------
    // 7Y: CONFIDENCE LEVEL MAPPING
    // --------------------------------

    @Test
    fun confidenceLevelClassifiesCorrectly() {

        assertEquals(
            com.example.feedsense.analysis.ConfidenceLevel.HIGH,
            ConfidenceLevel.from(0.92)
        )
        assertEquals(
            com.example.feedsense.analysis.ConfidenceLevel.MEDIUM,
            ConfidenceLevel.from(0.7)
        )
        assertEquals(
            com.example.feedsense.analysis.ConfidenceLevel.LOW,
            ConfidenceLevel.from(0.45)
        )
    }
}
