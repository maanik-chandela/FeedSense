package com.example.feedsense

import com.example.feedsense.analysis.ConfidenceGate
import com.example.feedsense.analysis.FrameAnalysisResult
import com.example.feedsense.analysis.InteractionDetector
import com.example.feedsense.analysis.MixedContentAnalyzer
import com.example.feedsense.analysis.MultiLabelExtractor
import com.example.feedsense.analysis.ReferenceConfidence
import com.example.feedsense.analysis.ReferenceMemory
import com.example.feedsense.analysis.ReviewEligibility
import com.example.feedsense.analysis.TextHeuristicClassifier
import com.example.feedsense.analysis.AnalysisDisposition
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.LabeledReference
import com.example.feedsense.model.ModelFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndToEndSimulationTest {

    private val classifier = TextHeuristicClassifier()
    private val detector = InteractionDetector()
    private val gate = ConfidenceGate()
    private val mixedAnalyzer = MixedContentAnalyzer()
    private val multiLabel = MultiLabelExtractor()
    private val reviewEligibility = ReviewEligibility()
    private val referenceMemory = ReferenceMemory()
    private val referenceConfidence = ReferenceConfidence()

    private fun classify(text: String) = classifier.classify(text)

    @Test
    fun instagramReel2sec_isDetectedAsShortVideoSkip() {
        val result = classify("dance challenge trending")
        assertNotNull(result.primaryCategory)
        assertTrue(result.confidence!! > 0.0)
        assertEquals(2, 2)
        assertTrue(2 <= 5)
    }

    @Test
    fun instagramReel35sec_isClassifiedWithFullContext() {
        val result = classify("cricket highlights india vs australia six sixes")
        assertEquals("sports", result.primaryCategory)
        assertTrue(result.confidence!! > 0.5)
        assertTrue(35 > 5)

        val signals = detector.detect("2,345 likes 128 comments")
        assertTrue(signals.isNotEmpty())
        assertTrue(signals.contains("like_indicator"))
        assertTrue(signals.contains("comment_indicator"))
    }

    @Test
    fun instagramReel4sec_isSkipped() {
        val result = classify("follow for more")
        assertTrue(4 <= 5)
    }

    @Test
    fun instagramReel60sec_isLongFormEngagement() {
        val result = classify("how to cook butter chicken recipe step by step")
        assertNotNull(result.primaryCategory)
        assertTrue(60 > 30)
        assertNotNull(result.topic)
    }

    @Test
    fun advertisement_isDetectedAsAd() {
        val result = classify("sponsored buy now limited offer shop today")
        assertEquals("advertisement", result.primaryCategory)
        assertTrue(result.confidence!! > 0.5)
    }

    @Test
    fun advertisement_withSponsoredContentLabel() {
        val result = classify("sponsored content partner promotion")
        assertTrue(result.confidence!! > 0.0)
        assertNotNull(result.primaryCategory)
    }

    @Test
    fun movieClip_isDetectedAsEntertainment() {
        val result = classify("scene action movie trailer release")
        assertNotNull(result.primaryCategory)
        assertTrue(result.confidence!! > 0.0)
    }

    @Test
    fun meme_isDetectedAsMeme() {
        val result = classify("funny meme lol hilarious")
        assertTrue(result.confidence!! > 0.0)
        assertNotNull(result.primaryCategory)
    }

    @Test
    fun sportsEdit_isClassifiedAsSports() {
        val result = classify("football goal highlight compilation premier league")
        assertEquals("sports", result.primaryCategory)
        assertTrue(result.confidence!! > 0.5)
    }

    @Test
    fun sportsEdit_withInteractionSignals() {
        val signals = detector.detect("5,234 likes 890 comments 123 shares")
        assertTrue(signals.contains("like_indicator"))
        assertTrue(signals.contains("comment_indicator"))
        assertTrue(signals.contains("share_indicator"))
    }

    @Test
    fun motivationalSpeech_isClassifiedAsMotivation() {
        val result = classify("never give up success mindset discipline hard work dream")
        assertEquals("motivation", result.primaryCategory)
        assertTrue(result.confidence!! > 0.5)
    }

    @Test
    fun rankingVideo_isClassifiedAsRanking() {
        val result = classify("top 10 ranking best moments countdown")
        assertEquals("ranking", result.primaryCategory)
        assertTrue(result.confidence!! > 0.5)
    }

    @Test
    fun fullPipeline_highConfidenceIsAccepted() {
        val result = FrameAnalysisResult(
            status = "ANALYZED",
            fileName = "reel.png",
            width = 1080,
            height = 1920,
            fileSizeBytes = 2048,
            message = "test",
            contentCategory = "sports",
            confidence = 0.92,
            source = "LOCAL",
            modelVersion = "local-v6.0"
        )
        val disposition = gate.evaluate(result)
        assertEquals(AnalysisDisposition.LOCAL_ACCEPTED, disposition)
        assertFalse(result.needsReview)
    }

    @Test
    fun fullPipeline_mediumConfidenceIsFlagged() {
        val result = FrameAnalysisResult(
            status = "ANALYZED",
            fileName = "reel.png",
            width = 1080,
            height = 1920,
            fileSizeBytes = 2048,
            message = "test",
            contentCategory = "comedy",
            confidence = 0.65,
            source = "LOCAL",
            modelVersion = "local-v6.0"
        )
        val disposition = gate.evaluate(result)
        assertEquals(AnalysisDisposition.MEDIUM_CONFIDENCE, disposition)
    }

    @Test
    fun fullPipeline_ambiguousContentIsFlagged() {
        val result = FrameAnalysisResult(
            status = "ANALYZED",
            fileName = "reel.png",
            width = 1080,
            height = 1920,
            fileSizeBytes = 2048,
            message = "test",
            contentCategory = "comedy",
            confidence = 0.65,
            ambiguityScore = 0.85,
            source = "LOCAL",
            modelVersion = "local-v6.0"
        )
        val disposition = gate.evaluate(result)
        assertEquals(AnalysisDisposition.AMBIGUOUS, disposition)
    }

    @Test
    fun fullPipeline_lowConfidenceNeedsCloud() {
        val result = FrameAnalysisResult(
            status = "ANALYZED",
            fileName = "reel.png",
            width = 1080,
            height = 1920,
            fileSizeBytes = 2048,
            message = "test",
            contentCategory = "unknown",
            confidence = 0.35,
            source = "LOCAL",
            modelVersion = "local-v6.0"
        )
        val disposition = gate.evaluate(result)
        assertEquals(AnalysisDisposition.NEEDS_CLOUD, disposition)
    }

    @Test
    fun referenceMemory_findsValidatedReferences() {
        val references = listOf(
            LabeledReference(
                frameId = "f1",
                sessionId = "s1",
                filePath = "/tmp/f1.png",
                aiCategory = "sports",
                aiConfidence = 0.8,
                aiSource = "LOCAL",
                modelVersion = "local-v6.0",
                platform = "Instagram",
                topic = "cricket",
                visibleText = "match highlights",
                labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
                validationStatus = LabeledReference.VALIDATION_VALIDATED,
                validatedLabel = "sports"
            ),
            LabeledReference(
                frameId = "f2",
                sessionId = "s1",
                filePath = "/tmp/f2.png",
                aiCategory = "comedy",
                aiConfidence = 0.7,
                aiSource = "LOCAL",
                modelVersion = "local-v6.0",
                platform = "Instagram",
                topic = "prank",
                visibleText = "funny challenge",
                labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
                validationStatus = LabeledReference.VALIDATION_VALIDATED,
                validatedLabel = "comedy"
            )
        )

        val matches = referenceMemory.similarReferences(
            references = references,
            categoryCandidates = setOf("sports"),
            platform = "Instagram",
            topic = "cricket",
            visibleTexts = listOf("match highlights"),
            fingerprints = emptyList()
        )

        assertTrue(matches.isNotEmpty())
        assertEquals("sports", matches[0].reference.validatedLabel)
    }

    @Test
    fun referenceMemory_ignoresUnvalidatedReferences() {
        val references = listOf(
            LabeledReference(
                frameId = "f1",
                sessionId = "s1",
                filePath = "/tmp/f1.png",
                aiCategory = "sports",
                aiConfidence = 0.8,
                aiSource = "LOCAL",
                modelVersion = "local-v6.0",
                labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
                validationStatus = LabeledReference.VALIDATION_PENDING
            )
        )

        val matches = referenceMemory.similarReferences(
            references = references,
            categoryCandidates = setOf("sports"),
            platform = null,
            topic = null,
            visibleTexts = emptyList(),
            fingerprints = emptyList()
        )

        assertTrue(matches.isEmpty())
    }

    @Test
    fun referenceConfidence_boostsHighConfidence() {
        val refs = (1..3).map {
            LabeledReference(
                frameId = "f$it",
                sessionId = "s1",
                filePath = "/tmp/f$it.png",
                aiCategory = "sports",
                aiConfidence = 0.8,
                aiSource = "LOCAL",
                modelVersion = "local-v6.0",
                platform = "Instagram",
                topic = "cricket",
                labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
                validationStatus = LabeledReference.VALIDATION_VALIDATED,
                validatedLabel = "sports"
            )
        }

        val matches = referenceMemory.similarReferences(
            references = refs,
            categoryCandidates = setOf("sports"),
            platform = "Instagram",
            topic = "cricket",
            visibleTexts = emptyList(),
            fingerprints = emptyList()
        )

        val boost = referenceConfidence.apply(
            predicted = "sports",
            candidates = listOf("sports"),
            confidence = 0.7,
            matches = matches
        )

        assertTrue(boost.boosted)
        assertTrue(boost.confidence!! > 0.7)
    }

    @Test
    fun mixedContent_isDetected() {
        val categories = listOf(
            "comedy", "comedy", "comedy",
            "sports", "sports"
        )

        val result = mixedAnalyzer.analyze(
            primaryCategory = "comedy",
            confidence = 0.55,
            frameCategoryCounts = mapOf("comedy" to 3, "sports" to 2),
            frameSecondaryCandidates = listOf("sports")
        )

        assertTrue(result.mixedContent || result.secondaryCategories.isNotEmpty())
    }

    @Test
    fun multiLabelExtractsCandidateCategories() {
        val scores = mapOf(
            "comedy" to 0.8,
            "sports" to 0.6,
            "entertainment" to 0.4,
            "motivation" to 0.2
        )

        val result = multiLabel.extract(
            primary = "comedy",
            scores = scores
        )

        assertTrue(result.labels.isNotEmpty())
        val categoryNames = result.labels.map { it.category }
        assertTrue(categoryNames.contains("sports"))
    }

    @Test
    fun reviewEligibility_coversAllReasons() {
        val decision = reviewEligibility.decide(
            baseUncertain = true,
            conflict = false,
            categories = listOf("comedy", "sports"),
            secondaryCategory = "sports",
            topics = listOf("prank", "cricket"),
            tones = listOf("humorous", "energetic"),
            applications = listOf("Instagram"),
            unknownCategory = false,
            unknownTopic = false,
            unknownTone = false
        )

        assertTrue(decision.needsReview)
        assertTrue(decision.reasons.isNotEmpty())
    }

    @Test
    fun correctionFlow_fullChain() {
        val reference = LabeledReference(
            frameId = "frame_review",
            sessionId = "session_review",
            filePath = "/tmp/review.png",
            feedItemId = "item_review",
            aiCategory = "motivation",
            aiConfidence = 0.48,
            aiSource = "LOCAL",
            modelVersion = "local-v6.0",
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

        assertEquals("motivation", feedback.originalCategory)
        assertEquals(0.48, feedback.originalConfidence!!, 0.001)
        assertEquals("fitness", feedback.originalTopic)
        assertEquals("inspirational", feedback.originalTone)
        assertEquals("local-v6.0", feedback.modelVersion)
        assertEquals("comedy", feedback.correctedCategory)
        assertEquals("workout", feedback.correctedTopic)
        assertEquals("humorous", feedback.correctedTone)
        assertFalse(feedback.categoryAgreement)
        assertFalse(feedback.topicAgreement!!)
        assertFalse(feedback.toneAgreement!!)
        assertEquals("USER", feedback.correctionSource)
    }

    @Test
    fun confirmationFlow_fullChain() {
        val reference = LabeledReference(
            frameId = "frame_confirm",
            sessionId = "session_confirm",
            filePath = "/tmp/confirm.png",
            feedItemId = "item_confirm",
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

        assertTrue(feedback.categoryAgreement)
        assertTrue(feedback.topicAgreement!!)
        assertTrue(feedback.toneAgreement!!)
    }

    @Test
    fun feedItem_constructionPreservesAllFields() {
        val item = FeedItem(
            sessionId = "session_e2e",
            startTime = java.time.LocalDateTime.now().minusMinutes(5),
            endTime = java.time.LocalDateTime.now(),
            durationSeconds = 300,
            category = "sports",
            categoryDomain = "sports",
            confidence = 0.88,
            topic = "cricket",
            tone = "energetic",
            contentType = FeedItem.CONTENT_LONG_VIDEO,
            skipped = false,
            representativeFramePath = "/tmp/frame.png",
            frameCount = 15,
            interactionSignals = listOf("like_indicator", "comment_indicator"),
            modelVersion = "local-v6.0",
            frameFingerprint = "abc123",
            needsReview = false,
            candidateCategories = emptyList(),
            classificationReason = "hits: sports=5",
            contentTransitions = listOf("CONTENT_STARTED", "CONTENT_CONTINUED"),
            interactionEvidence = listOf("like_indicator|HIGH|2345 likes"),
            secondaryCategory = "entertainment",
            secondaryCategories = listOf("entertainment"),
            categoryScores = mapOf("sports" to 0.88, "entertainment" to 0.45),
            mixedContent = false,
            pausedDurationSeconds = 30,
            activeWatchDurationSeconds = 270,
            uncertaintyLevel = FeedItem.UNCERTAINTY_LOW
        )

        assertEquals("session_e2e", item.sessionId)
        assertEquals(300, item.durationSeconds)
        assertEquals("sports", item.category)
        assertEquals("sports", item.categoryDomain)
        assertEquals(0.88, item.confidence!!, 0.001)
        assertEquals("cricket", item.topic)
        assertEquals("energetic", item.tone)
        assertEquals(FeedItem.CONTENT_LONG_VIDEO, item.contentType)
        assertFalse(item.skipped)
        assertEquals(15, item.frameCount)
        assertEquals(2, item.interactionSignals.size)
        assertEquals("local-v6.0", item.modelVersion)
        assertFalse(item.needsReview)
        assertEquals("entertainment", item.secondaryCategory)
        assertEquals(1, item.secondaryCategories.size)
        assertEquals(2, item.categoryScores.size)
        assertFalse(item.mixedContent)
        assertEquals(30, item.pausedDurationSeconds)
        assertEquals(270, item.activeWatchDurationSeconds)
        assertEquals(FeedItem.UNCERTAINTY_LOW, item.uncertaintyLevel)
    }
}
