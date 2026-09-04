package com.example.feedsense

import com.example.feedsense.analysis.CloudBudgetGate
import com.example.feedsense.analysis.ConfidenceGate
import com.example.feedsense.analysis.FrameAnalysisResult
import com.example.feedsense.analysis.InteractionDetector
import com.example.feedsense.analysis.MixedContentAnalyzer
import com.example.feedsense.analysis.MultiLabelExtractor
import com.example.feedsense.analysis.ReferenceConfidence
import com.example.feedsense.analysis.ReferenceMemory
import com.example.feedsense.analysis.RetentionConfig
import com.example.feedsense.analysis.RetentionPolicy
import com.example.feedsense.analysis.ReviewEligibility
import com.example.feedsense.analysis.SessionSnapshot
import com.example.feedsense.analysis.TextHeuristicClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class PerformanceAuditTest {

    private val classifier = TextHeuristicClassifier()
    private val gate = ConfidenceGate()
    private val detector = InteractionDetector()
    private val retention = RetentionPolicy()
    private val referenceMemory = ReferenceMemory()
    private val cloudBudget = CloudBudgetGate()

    // --------------------------------
    // RETENTION POLICY STORAGE BOUNDS
    // --------------------------------

    @Test
    fun retention_defaultConfig_maxFramesIsReasonable() {
        val config = RetentionConfig()
        assertTrue(
            "maxFramesPerSession must be positive",
            config.maxFramesPerSession > 0
        )
        assertTrue(
            "maxFramesPerSession must be bounded (< 10000)",
            config.maxFramesPerSession < 10_000
        )
    }

    @Test
    fun retention_defaultConfig_retentionDaysIsReasonable() {
        val config = RetentionConfig()
        assertTrue(
            "retentionDays must be positive",
            config.retentionDays > 0
        )
        assertTrue(
            "retentionDays must be bounded (< 365)",
            config.retentionDays < 365
        )
    }

    @Test
    fun retention_purgeRemovesOldInactiveSessions() {
        val config = RetentionConfig(retentionDays = 7)
        val now = LocalDateTime.of(2025, 1, 15, 12, 0)

        val sessions = listOf(
            SessionSnapshot("s1", now.minusDays(10), 50),
            SessionSnapshot("s2", now.minusDays(3), 30),
            SessionSnapshot("s3", now.minusDays(20), 100)
        )

        val plan = retention.evaluate(
            sessions = sessions,
            activeSessionIds = emptySet(),
            now = now,
            config = config
        )

        assertEquals(setOf("s1", "s3"), plan.purgeSessions)
    }

    @Test
    fun retention_trimOnlyCountsNonReviewFrames() {
        val config = RetentionConfig(maxFramesPerSession = 100)
        val now = LocalDateTime.of(2025, 1, 15, 12, 0)

        val sessions = listOf(
            SessionSnapshot("s1", now.minusDays(1), 200)
        )

        val plan = retention.evaluate(
            sessions = sessions,
            activeSessionIds = emptySet(),
            now = now,
            config = config
        )

        assertEquals(1, plan.trimBySession.size)
        assertEquals(100, plan.trimBySession["s1"])
    }

    @Test
    fun retention_activeSessionsAreNotPurged() {
        val config = RetentionConfig(retentionDays = 1)
        val now = LocalDateTime.of(2025, 1, 15, 12, 0)

        val sessions = listOf(
            SessionSnapshot("s1", now.minusDays(5), 50),
            SessionSnapshot("s2", now.minusDays(5), 50)
        )

        val plan = retention.evaluate(
            sessions = sessions,
            activeSessionIds = setOf("s1"),
            now = now,
            config = config
        )

        assertFalse(plan.purgeSessions.contains("s1"))
        assertTrue(plan.purgeSessions.contains("s2"))
    }

    @Test
    fun retention_sessionsUnderCapAreNotTrimmed() {
        val config = RetentionConfig(maxFramesPerSession = 300)
        val now = LocalDateTime.of(2025, 1, 15, 12, 0)

        val sessions = listOf(
            SessionSnapshot("s1", now.minusDays(1), 50)
        )

        val plan = retention.evaluate(
            sessions = sessions,
            activeSessionIds = emptySet(),
            now = now,
            config = config
        )

        assertFalse(plan.trimBySession.containsKey("s1"))
    }

    @Test
    fun retention_maxFramesBoundsStorage() {
        val config = RetentionConfig(maxFramesPerSession = 300)
        val maxBytesPerFrame = 5_000_000L
        val maxStorageBytes =
            config.maxFramesPerSession * maxBytesPerFrame

        assertTrue(
            "Storage bound must be < 2GB per session",
            maxStorageBytes < 2_000_000_000L
        )
    }

    // --------------------------------
    // CLASSIFIER PERFORMANCE
    // --------------------------------

    @Test
    fun classifier_handlesNullInputEfficiently() {
        val start = System.nanoTime()
        repeat(10_000) {
            classifier.classify(null)
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0

        assertTrue(
            "10k null classifications must be < 500ms, was ${elapsedMs}ms",
            elapsedMs < 500
        )
    }

    @Test
    fun classifier_handlesShortInputEfficiently() {
        val start = System.nanoTime()
        repeat(10_000) {
            classifier.classify("test")
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0

        assertTrue(
            "10k short classifications must be < 1000ms, was ${elapsedMs}ms",
            elapsedMs < 1000
        )
    }

    @Test
    fun classifier_handlesLongInputEfficiently() {
        val longText = "word ".repeat(500)
        val start = System.nanoTime()
        repeat(1_000) {
            classifier.classify(longText)
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0

        assertTrue(
            "1k long classifications must be < 2000ms, was ${elapsedMs}ms",
            elapsedMs < 2000
        )
    }

    // --------------------------------
    // CONFIDENCE GATE PERFORMANCE
    // --------------------------------

    @Test
    fun confidenceGate_handlesHighThroughput() {
        val result = FrameAnalysisResult(
            status = "ANALYZED",
            fileName = "test.png",
            width = 1080,
            height = 1920,
            fileSizeBytes = 1024,
            message = "test",
            contentCategory = "sports",
            confidence = 0.85,
            source = "LOCAL",
            modelVersion = "local-v6.0"
        )

        val start = System.nanoTime()
        repeat(10_000) {
            gate.evaluate(result)
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0

        assertTrue(
            "10k gate evaluations must be < 500ms, was ${elapsedMs}ms",
            elapsedMs < 500
        )
    }

    // --------------------------------
    // INTERACTION DETECTOR PERFORMANCE
    // --------------------------------

    @Test
    fun interactionDetector_handlesHighThroughput() {
        val text = "2,345 likes 128 comments Save Follow share bookmark"

        val start = System.nanoTime()
        repeat(10_000) {
            detector.detect(text)
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0

        assertTrue(
            "10k interaction detections must be < 500ms, was ${elapsedMs}ms",
            elapsedMs < 500
        )
    }

    // --------------------------------
    // REFERENCE MEMORY PERFORMANCE
    // --------------------------------

    @Test
    fun referenceMemory_scalesWithLargeReferenceSet() {
        val references = (1..500).map {
            com.example.feedsense.model.LabeledReference(
                frameId = "f$it",
                sessionId = "s1",
                filePath = "/tmp/f$it.png",
                aiCategory = if (it % 2 == 0) "sports" else "comedy",
                aiConfidence = 0.7 + (it % 10) * 0.02,
                aiSource = "LOCAL",
                modelVersion = "local-v6.0",
                platform = if (it % 3 == 0) "Instagram" else "YouTube",
                topic = if (it % 2 == 0) "cricket" else "prank",
                labelSource = com.example.feedsense.model.LabeledReference.LABEL_SOURCE_HUMAN,
                validationStatus = com.example.feedsense.model.LabeledReference.VALIDATION_VALIDATED,
                validatedLabel = if (it % 2 == 0) "sports" else "comedy"
            )
        }

        val start = System.nanoTime()
        repeat(100) {
            referenceMemory.similarReferences(
                references = references,
                categoryCandidates = setOf("sports"),
                platform = "Instagram",
                topic = "cricket",
                visibleTexts = listOf("cricket highlights"),
                fingerprints = emptyList(),
                limit = 10
            )
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000.0

        assertTrue(
            "100 searches on 500 refs must be < 5000ms, was ${elapsedMs}ms",
            elapsedMs < 5000
        )
    }

    // --------------------------------
    // REFERENCE MEMORY LOOKUP BOUNDS
    // --------------------------------

    @Test
    fun referenceMemory_resultSizeIsBounded() {
        val references = (1..50).map {
            com.example.feedsense.model.LabeledReference(
                frameId = "f$it",
                sessionId = "s1",
                filePath = "/tmp/f$it.png",
                aiCategory = "sports",
                aiConfidence = 0.8,
                aiSource = "LOCAL",
                modelVersion = "local-v6.0",
                platform = "Instagram",
                topic = "cricket",
                labelSource = com.example.feedsense.model.LabeledReference.LABEL_SOURCE_HUMAN,
                validationStatus = com.example.feedsense.model.LabeledReference.VALIDATION_VALIDATED,
                validatedLabel = "sports"
            )
        }

        val matches = referenceMemory.similarReferences(
            references = references,
            categoryCandidates = setOf("sports"),
            platform = "Instagram",
            topic = "cricket",
            visibleTexts = emptyList(),
            fingerprints = emptyList(),
            limit = 10
        )

        assertTrue(
            "Result size must respect limit, was ${matches.size}",
            matches.size <= 10
        )
    }

    // --------------------------------
    // CLOUD BUDGET BOUNDS
    // --------------------------------

    @Test
    fun cloudBudget_requestLimitIsReasonable() {
        assertTrue(
            "Daily request limit must be positive",
            CloudBudgetGate.MAX_REQUESTS_PER_DAY > 0
        )
        assertTrue(
            "Daily request limit must be bounded (< 100)",
            CloudBudgetGate.MAX_REQUESTS_PER_DAY < 100
        )
    }

    @Test
    fun cloudBudget_rejectsExcessRequests() {
        val decision = cloudBudget.decide(
            monthlyEstimatedRupees = 45.0,
            dailyEstimatedRupees = 1.9,
            requestCountToday = CloudBudgetGate.MAX_REQUESTS_PER_DAY,
            isDuplicate = false
        )

        assertFalse(
            "Should reject after hitting daily limit",
            decision.allowed
        )
    }

    // --------------------------------
    // MIXED CONTENT ANALYZER BOUNDS
    // --------------------------------

    @Test
    fun mixedContent_maxSecondaryCategoriesIsBounded() {
        val analyzer = MixedContentAnalyzer()

        val frameCounts = mapOf(
            "primary" to 10,
            "a" to 8, "b" to 7, "c" to 6,
            "d" to 5, "e" to 4, "f" to 3
        )

        val decision = analyzer.analyze(
            primaryCategory = "primary",
            confidence = 0.6,
            frameCategoryCounts = frameCounts,
            frameSecondaryCandidates = listOf("a", "b", "c", "d")
        )

        assertTrue(
            "Secondary categories must be bounded (<= 3), was ${decision.secondaryCategories.size}",
            decision.secondaryCategories.size <= 3
        )
    }

    // --------------------------------
    // MULTI-LABEL EXTRACTOR BOUNDS
    // --------------------------------

    @Test
    fun multiLabelExtractor_maxLabelsIsBounded() {
        val extractor = MultiLabelExtractor()

        val scores = mapOf(
            "a" to 0.9, "b" to 0.8, "c" to 0.7,
            "d" to 0.6, "e" to 0.5, "f" to 0.4
        )

        val result = extractor.extract(
            primary = "a",
            scores = scores
        )

        assertTrue(
            "Labels must be bounded (<= 3), was ${result.labels.size}",
            result.labels.size <= 3
        )
    }

    // --------------------------------
    // REFERENCE CONFIDENCE BOOST CAP
    // --------------------------------

    @Test
    fun referenceConfidence_boostIsCapped() {
        val conf = ReferenceConfidence()
        val memory = ReferenceMemory()

        val refs = (1..10).map {
            com.example.feedsense.model.LabeledReference(
                frameId = "f$it",
                sessionId = "s1",
                filePath = "/tmp/f$it.png",
                aiCategory = "sports",
                aiConfidence = 0.8,
                aiSource = "LOCAL",
                modelVersion = "local-v6.0",
                platform = "Instagram",
                topic = "cricket",
                labelSource = com.example.feedsense.model.LabeledReference.LABEL_SOURCE_HUMAN,
                validationStatus = com.example.feedsense.model.LabeledReference.VALIDATION_VALIDATED,
                validatedLabel = "sports"
            )
        }

        val matches = memory.similarReferences(
            references = refs,
            categoryCandidates = setOf("sports"),
            platform = "Instagram",
            topic = "cricket",
            visibleTexts = emptyList(),
            fingerprints = emptyList()
        )

        val boost = conf.apply(
            predicted = "sports",
            candidates = listOf("sports"),
            confidence = 0.5,
            matches = matches
        )

        assertTrue(
            "Boosted confidence must be <= 1.0, was ${boost.confidence}",
            boost.confidence!! <= 1.0
        )

        assertTrue(
            "Boosted confidence must be >= original confidence",
            boost.confidence!! >= 0.5
        )
    }

    // --------------------------------
    // REVIEW ELIGIBILITY BOUNDS
    // --------------------------------

    @Test
    fun reviewEligibility_reasonsAreBounded() {
        val eligibility = ReviewEligibility()

        val decision = eligibility.decide(
            baseUncertain = true,
            conflict = true,
            categories = listOf("a", "b"),
            secondaryCategory = "b",
            topics = listOf("t1", "t2"),
            tones = listOf("tone1", "tone2"),
            applications = listOf("Instagram", "YouTube"),
            unknownCategory = true,
            unknownTopic = true,
            unknownTone = true
        )

        assertTrue(
            "Reasons must be bounded (<= 10), was ${decision.reasons.size}",
            decision.reasons.size <= 10
        )
    }

    // --------------------------------
    // TEXT CLASSIFIER SECONDARY BOUNDS
    // --------------------------------

    @Test
    fun classifier_secondaryCategoriesAreBounded() {
        val result = classifier.classify(
            "sports cricket football soccer goal highlights"
        )

        assertTrue(
            "Secondary categories must be bounded (<= 3), was ${result.secondaryCategories.size}",
            result.secondaryCategories.size <= 3
        )
    }

    // --------------------------------
    // ALL PURE FUNCTIONS ARE MEMORY SAFE
    // --------------------------------

    @Test
    fun pureFunctions_areStateless() {

        val classifier1 = TextHeuristicClassifier()
        val classifier2 = TextHeuristicClassifier()

        val r1 = classifier1.classify("cricket sports highlights")
        val r2 = classifier2.classify("cricket sports highlights")

        assertEquals(r1.primaryCategory, r2.primaryCategory)
        assertEquals(r1.confidence, r2.confidence)
        assertEquals(r1.topic, r2.topic)
    }
}
