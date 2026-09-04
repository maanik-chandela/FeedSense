package com.example.feedsense

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.analysis.evaluation.CapabilityEligibility
import com.example.feedsense.analysis.evaluation.DatasetConstructionPolicy
import com.example.feedsense.analysis.evaluation.DatasetConstructor
import com.example.feedsense.analysis.evaluation.DatasetManifest
import com.example.feedsense.analysis.evaluation.DatasetReporter
import com.example.feedsense.analysis.evaluation.DatasetValidator
import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.EvaluationItem
import com.example.feedsense.model.EvaluationRecord
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.GroundTruth
import java.time.LocalDateTime
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// --------------------------------
// DATASET CONSTRUCTION TESTS (8A-4)
// --------------------------------
//
// Comprehensive tests for the dataset construction,
// validation, reporting, and capability eligibility
// infrastructure introduced in Milestone 8A-4.

class DatasetConstructionTest {

    private val now = LocalDateTime.of(2026, 9, 1, 10, 0)

    // --------------------------------
    // HELPER BUILDERS
    // --------------------------------

    private fun evalItem(
        id: String = UUID.randomUUID().toString(),
        feedItemId: String = "feed-$id",
        sessionId: String = "session-1",
        projectId: String? = "project-1",
        status: String = EvaluationItem.STATUS_EVALUATED,
        datasetVersion: String? = null,
        enqueuedAt: LocalDateTime = now,
        modelVersion: String? = "local-v6.0"
    ) = EvaluationItem(
        id = id,
        feedItemId = feedItemId,
        sessionId = sessionId,
        projectId = projectId,
        modelVersion = modelVersion,
        evaluationStatus = status,
        datasetVersion = datasetVersion,
        enqueuedAt = enqueuedAt,
        createdAt = enqueuedAt
    )

    private fun prediction(
        evaluationItemId: String,
        category: String? = "sports",
        confidence: Double? = 0.85,
        platform: String? = "Instagram",
        contentType: String? = FeedItem.CONTENT_SHORT_VIDEO,
        durationSeconds: Int = 30,
        skipped: Boolean = false,
        interactionSignals: List<String> = emptyList(),
        topic: String? = "cricket",
        tone: String? = "ENTERTAINMENT",
        modelVersion: String? = "local-v6.0",
        feedItemId: String? = "feed-$evaluationItemId"
    ) = AiPredictionRecord(
        evaluationItemId = evaluationItemId,
        modelVersion = modelVersion,
        category = category,
        confidence = confidence,
        platform = platform,
        contentType = contentType,
        durationSeconds = durationSeconds,
        skipped = skipped,
        interactionSignals = interactionSignals,
        topic = topic,
        tone = tone,
        feedItemId = feedItemId
    )

    private fun truth(
        evaluationItemId: String,
        category: String? = "sports",
        ambiguity: String = GroundTruth.AMBIGUITY_CLEAR,
        platform: String? = "Instagram",
        contentType: String? = GroundTruth.CONTENT_TYPE_SHORT_VIDEO,
        durationSeconds: Int? = 28,
        skipped: Boolean? = false,
        liked: Boolean? = null,
        commented: Boolean? = null,
        shared: Boolean? = null,
        saved: Boolean? = null,
        followed: Boolean? = null,
        paused: Boolean? = null,
        playing: Boolean? = null,
        topic: String? = "cricket",
        tone: String? = "ENTERTAINMENT",
        secondaryCategories: List<String> = emptyList(),
        annotatorId: String? = "annotator-1"
    ) = GroundTruth(
        evaluationItemId = evaluationItemId,
        annotatorId = annotatorId,
        category = category,
        ambiguity = ambiguity,
        platform = platform,
        contentType = contentType,
        durationSeconds = durationSeconds,
        skipped = skipped,
        liked = liked,
        commented = commented,
        shared = shared,
        saved = saved,
        followed = followed,
        paused = paused,
        playing = playing,
        topic = topic,
        tone = tone,
        secondaryCategories = secondaryCategories
    )

    // ================================================
    // DATASET CONSTRUCTOR TESTS
    // ================================================

    @Test
    fun `valid dataset construction produces correct manifest`() {
        val item1 = evalItem(id = "item-1")
        val item2 = evalItem(id = "item-2")
        val items = listOf(item1, item2)
        val preds = mapOf(
            "item-1" to prediction(evaluationItemId = "item-1"),
            "item-2" to prediction(evaluationItemId = "item-2", category = "comedy")
        )
        val truths = mapOf(
            "item-1" to truth(evaluationItemId = "item-1"),
            "item-2" to truth(evaluationItemId = "item-2", category = "comedy")
        )

        val result = DatasetConstructor.construct(
            policy = DatasetConstructionPolicy.default("evaluation-v1"),
            evaluationItems = items,
            predictions = preds,
            truths = truths
        )

        assertEquals(2, result.manifest.includedItemIds.size)
        assertEquals(2, result.manifest.totalCandidateItems)
        assertTrue(result.isValid)
        assertEquals("evaluation-v1", result.manifest.datasetVersion)
    }

    @Test
    fun `empty dataset produces empty manifest`() {
        val result = DatasetConstructor.construct(
            policy = DatasetConstructionPolicy.default("evaluation-v1"),
            evaluationItems = emptyList(),
            predictions = emptyMap(),
            truths = emptyMap()
        )

        assertEquals(0, result.manifest.includedItemIds.size)
        assertEquals(0, result.manifest.totalCandidateItems)
        assertEquals(0, result.manifest.excludedItems.size)
        assertTrue(result.isValid)
    }

    @Test
    fun `unreviewed items are excluded`() {
        val item = evalItem(
            id = "item-1",
            status = EvaluationItem.STATUS_NOT_EVALUATED
        )

        val result = DatasetConstructor.construct(
            policy = DatasetConstructionPolicy.default("evaluation-v1"),
            evaluationItems = listOf(item),
            predictions = mapOf("item-1" to prediction(evaluationItemId = "item-1")),
            truths = mapOf("item-1" to truth(evaluationItemId = "item-1"))
        )

        assertEquals(0, result.manifest.includedItemIds.size)
        assertEquals(1, result.manifest.excludedItems.size)
        assertTrue(
            result.manifest.excludedItems.first().reason.contains("EXCLUDED_STATUS")
        )
    }

    @Test
    fun `disputed items excluded when policy says no`() {
        val item = evalItem(
            id = "item-1",
            status = EvaluationItem.STATUS_DISPUTED
        )

        val result = DatasetConstructor.construct(
            policy = DatasetConstructionPolicy(
                datasetVersion = "v1",
                includeDisputed = false
            ),
            evaluationItems = listOf(item),
            predictions = mapOf("item-1" to prediction(evaluationItemId = "item-1")),
            truths = mapOf("item-1" to truth(evaluationItemId = "item-1"))
        )

        assertEquals(0, result.manifest.includedItemIds.size)
        assertEquals(1, result.manifest.excludedItems.size)
        assertEquals("EXCLUDED_DISPUTED", result.manifest.excludedItems.first().reason)
    }

    @Test
    fun `disputed items included when policy says yes`() {
        val item = evalItem(
            id = "item-1",
            status = EvaluationItem.STATUS_DISPUTED
        )

        val result = DatasetConstructor.construct(
            policy = DatasetConstructionPolicy(
                datasetVersion = "v1",
                includeDisputed = true
            ),
            evaluationItems = listOf(item),
            predictions = mapOf("item-1" to prediction(evaluationItemId = "item-1")),
            truths = mapOf("item-1" to truth(evaluationItemId = "item-1"))
        )

        assertEquals(1, result.manifest.includedItemIds.size)
        assertEquals(0, result.manifest.excludedItems.size)
    }

    @Test
    fun `missing prediction causes exclusion`() {
        val item = evalItem(id = "item-1")

        val result = DatasetConstructor.construct(
            policy = DatasetConstructionPolicy.default("evaluation-v1"),
            evaluationItems = listOf(item),
            predictions = emptyMap(),
            truths = mapOf("item-1" to truth(evaluationItemId = "item-1"))
        )

        assertEquals(0, result.manifest.includedItemIds.size)
        assertEquals(1, result.manifest.excludedItems.size)
        assertTrue(
            result.manifest.excludedItems.first().reason.contains("MISSING_PREDICTION")
        )
    }

    @Test
    fun `missing truth causes exclusion`() {
        val item = evalItem(id = "item-1")

        val result = DatasetConstructor.construct(
            policy = DatasetConstructionPolicy.default("evaluation-v1"),
            evaluationItems = listOf(item),
            predictions = mapOf("item-1" to prediction(evaluationItemId = "item-1")),
            truths = emptyMap()
        )

        assertEquals(0, result.manifest.includedItemIds.size)
        assertEquals(1, result.manifest.excludedItems.size)
        assertTrue(
            result.manifest.excludedItems.first().reason.contains("MISSING_TRUTH")
        )
    }

    @Test
    fun `UNKNOWN values are preserved not converted to false`() {
        val item = evalItem(id = "item-1")

        val result = DatasetConstructor.construct(
            policy = DatasetConstructionPolicy.default("evaluation-v1"),
            evaluationItems = listOf(item),
            predictions = mapOf("item-1" to prediction(evaluationItemId = "item-1")),
            truths = mapOf(
                "item-1" to truth(
                    evaluationItemId = "item-1",
                    liked = null, // UNKNOWN
                    skipped = null, // UNKNOWN
                    platform = null,
                    topic = null,
                    tone = null
                )
            )
        )

        assertEquals(1, result.manifest.includedItemIds.size)

        // Check completeness shows unknown rates
        val likeCompleteness = result.manifest.annotationCompleteness["liked"]
        assertNotNull(likeCompleteness)
        assertEquals(0.0, likeCompleteness!!, 0.01)
    }

    @Test
    fun `disputed annotations are explicitly classified`() {
        val item = evalItem(
            id = "item-1",
            status = EvaluationItem.STATUS_DISPUTED
        )

        val result = DatasetConstructor.construct(
            policy = DatasetConstructionPolicy(
                datasetVersion = "v1",
                includeDisputed = true
            ),
            evaluationItems = listOf(item),
            predictions = mapOf("item-1" to prediction(evaluationItemId = "item-1")),
            truths = mapOf("item-1" to truth(evaluationItemId = "item-1"))
        )

        assertEquals(1, result.manifest.disputedItemCount)
    }

    @Test
    fun `ambiguous annotations are preserved`() {
        val item = evalItem(id = "item-1")

        val result = DatasetConstructor.construct(
            policy = DatasetConstructionPolicy(
                datasetVersion = "v1",
                includeAmbiguous = true
            ),
            evaluationItems = listOf(item),
            predictions = mapOf("item-1" to prediction(evaluationItemId = "item-1")),
            truths = mapOf(
                "item-1" to truth(
                    evaluationItemId = "item-1",
                    ambiguity = GroundTruth.AMBIGUITY_AMBIGUOUS
                )
            )
        )

        assertEquals(1, result.manifest.includedItemIds.size)
        assertEquals(1, result.manifest.ambiguousItemCount)
    }

    @Test
    fun `mixed content is preserved with primary and secondary`() {
        val item = evalItem(id = "item-1")

        val result = DatasetConstructor.construct(
            policy = DatasetConstructionPolicy(
                datasetVersion = "v1",
                includeMixed = true
            ),
            evaluationItems = listOf(item),
            predictions = mapOf("item-1" to prediction(evaluationItemId = "item-1")),
            truths = mapOf(
                "item-1" to truth(
                    evaluationItemId = "item-1",
                    category = "sports",
                    ambiguity = GroundTruth.AMBIGUITY_MIXED,
                    secondaryCategories = listOf("comedy")
                )
            )
        )

        assertEquals(1, result.manifest.includedItemIds.size)
        assertEquals(1, result.manifest.mixedItemCount)
    }

    @Test
    fun `deterministic ordering produces same result across runs`() {
        val items = (1..5).map { i ->
            evalItem(
                id = "item-$i",
                enqueuedAt = now.plusMinutes((5 - i).toLong())
            )
        }

        val policy = DatasetConstructionPolicy.default("v1")
        val preds = items.associate { it.id to prediction(evaluationItemId = it.id) }
        val truths = items.associate { it.id to truth(evaluationItemId = it.id) }

        val result1 = DatasetConstructor.construct(policy, items, preds, truths)
        val result2 = DatasetConstructor.construct(policy, items, preds, truths)

        assertEquals(result1.manifest.includedItemIds, result2.manifest.includedItemIds)
        assertEquals(result1.manifest.excludedItems, result2.manifest.excludedItems)
        assertEquals(result1.manifest.totalCandidateItems, result2.manifest.totalCandidateItems)
        assertEquals(result1.manifest.categoryDistribution, result2.manifest.categoryDistribution)
    }

    @Test
    fun `dataset version is unique and recorded in manifest`() {
        val item = evalItem(id = "item-1")
        val preds = mapOf("item-1" to prediction(evaluationItemId = "item-1"))
        val truths = mapOf("item-1" to truth(evaluationItemId = "item-1"))

        val result = DatasetConstructor.construct(
            policy = DatasetConstructionPolicy(
                datasetVersion = "evaluation-v42"
            ),
            evaluationItems = listOf(item),
            predictions = preds,
            truths = truths
        )

        assertEquals("evaluation-v42", result.manifest.datasetVersion)
    }

    @Test
    fun `no cherry picking - difficult examples are included`() {
        // Items with low confidence, high ambiguity, etc.
        // should still be included unless explicitly excluded
        val item = evalItem(id = "item-1")

        val result = DatasetConstructor.construct(
            policy = DatasetConstructionPolicy(
                datasetVersion = "v1",
                includeAmbiguous = true,
                includeMixed = true
            ),
            evaluationItems = listOf(item),
            predictions = mapOf(
                "item-1" to prediction(
                    evaluationItemId = "item-1",
                    confidence = 0.15 // Low confidence
                )
            ),
            truths = mapOf(
                "item-1" to truth(
                    evaluationItemId = "item-1",
                    ambiguity = GroundTruth.AMBIGUITY_AMBIGUOUS
                )
            )
        )

        assertEquals(1, result.manifest.includedItemIds.size)
    }

    @Test
    fun `model version is preserved in manifest`() {
        val item = evalItem(id = "item-1")

        val result = DatasetConstructor.construct(
            policy = DatasetConstructionPolicy.default("v1"),
            evaluationItems = listOf(item),
            predictions = mapOf(
                "item-1" to prediction(
                    evaluationItemId = "item-1",
                    modelVersion = "local-v6.0"
                )
            ),
            truths = mapOf("item-1" to truth(evaluationItemId = "item-1"))
        )

        assertEquals(
            listOf("local-v6.0"),
            result.manifest.modelVersionsRepresented
        )
    }

    // ================================================
    // DATASET VALIDATOR TESTS
    // ================================================

    @Test
    fun `valid item passes validation`() {
        val item = evalItem(id = "item-1")
        val pred = prediction(evaluationItemId = "item-1")
        val t = truth(evaluationItemId = "item-1")

        val issues = DatasetValidator.validateItem(item, pred, t)
        assertTrue(issues.none { it.severity == DatasetValidator.Severity.ERROR })
    }

    @Test
    fun `missing AI prediction is an error`() {
        val item = evalItem(id = "item-1")
        val t = truth(evaluationItemId = "item-1")

        val issues = DatasetValidator.validateItem(item, null, t)
        assertTrue(issues.any {
            it.severity == DatasetValidator.Severity.ERROR &&
                it.field == "aiPrediction"
        })
    }

    @Test
    fun `missing ground truth is an error`() {
        val item = evalItem(id = "item-1")
        val pred = prediction(evaluationItemId = "item-1")

        val issues = DatasetValidator.validateItem(item, pred, null)
        assertTrue(issues.any {
            it.severity == DatasetValidator.Severity.ERROR &&
                it.field == "groundTruth"
        })
    }

    @Test
    fun `negative confidence is an error`() {
        val item = evalItem(id = "item-1")
        val pred = prediction(evaluationItemId = "item-1", confidence = -0.5)
        val t = truth(evaluationItemId = "item-1")

        val issues = DatasetValidator.validateItem(item, pred, t)
        assertTrue(issues.any {
            it.severity == DatasetValidator.Severity.ERROR &&
                it.field == "prediction.confidence"
        })
    }

    @Test
    fun `confidence above 1 is an error`() {
        val item = evalItem(id = "item-1")
        val pred = prediction(evaluationItemId = "item-1", confidence = 1.5)
        val t = truth(evaluationItemId = "item-1")

        val issues = DatasetValidator.validateItem(item, pred, t)
        assertTrue(issues.any {
            it.severity == DatasetValidator.Severity.ERROR &&
                it.field == "prediction.confidence"
        })
    }

    @Test
    fun `negative duration is an error`() {
        val item = evalItem(id = "item-1")
        val pred = prediction(evaluationItemId = "item-1", durationSeconds = -5)
        val t = truth(evaluationItemId = "item-1", durationSeconds = -3)

        val issues = DatasetValidator.validateItem(item, pred, t)
        assertTrue(issues.any {
            it.severity == DatasetValidator.Severity.ERROR &&
                it.field == "truth.durationSeconds"
        })
    }

    @Test
    fun `invalid category is a warning`() {
        val item = evalItem(id = "item-1")
        val pred = prediction(evaluationItemId = "item-1", category = "nonexistent_category_xyz")
        val t = truth(evaluationItemId = "item-1", category = "nonexistent_category_xyz")

        val issues = DatasetValidator.validateItem(item, pred, t)
        assertTrue(issues.any {
            it.severity == DatasetValidator.Severity.WARNING &&
                it.field == "prediction.category"
        })
    }

    @Test
    fun `batch validation counts errors correctly`() {
        val item1 = evalItem(id = "item-1")
        val item2 = evalItem(id = "item-2")

        val triples = listOf(
            Triple(item1, prediction(evaluationItemId = "item-1"), truth(evaluationItemId = "item-1")),
            Triple(item2, null, truth(evaluationItemId = "item-2")) // Missing prediction
        )

        val result = DatasetValidator.validateBatch(triples)
        assertEquals(2, result.totalChecked)
        assertEquals(1, result.invalidItemCount)
        assertEquals(1, result.validItemCount)
    }

    @Test
    fun `duplicate IDs are flagged`() {
        val item = evalItem(id = "item-1")

        val triples = listOf(
            Triple(item, prediction(evaluationItemId = "item-1"), truth(evaluationItemId = "item-1")),
            Triple(item, prediction(evaluationItemId = "item-1"), truth(evaluationItemId = "item-1"))
        )

        val result = DatasetValidator.validateBatch(triples)
        assertTrue(result.issues.any {
            it.field == "id" && it.message.contains("Duplicate")
        })
    }

    @Test
    fun `UNKNOWN ambiguity with null category is valid for category`() {
        // When ambiguity is UNKNOWN, null category is acceptable
        val item = evalItem(id = "item-1")
        val t = truth(
            evaluationItemId = "item-1",
            category = null,
            ambiguity = GroundTruth.AMBIGUITY_UNKNOWN
        )

        val issues = DatasetValidator.validateItem(
            item, prediction(evaluationItemId = "item-1"), t
        )
        // Should not have a category error
        assertTrue(issues.none {
            it.field == "truth.category" && it.severity == DatasetValidator.Severity.ERROR
        })
    }

    @Test
    fun `invalid platform is a warning not error`() {
        val item = evalItem(id = "item-1")
        val t = truth(
            evaluationItemId = "item-1",
            platform = "FakePlatform"
        )

        val issues = DatasetValidator.validateItem(
            item, prediction(evaluationItemId = "item-1"), t
        )
        assertTrue(issues.any {
            it.severity == DatasetValidator.Severity.WARNING &&
                it.field == "truth.platform"
        })
    }

    @Test
    fun `dataset cohort validation catches mismatched versions`() {
        val items = listOf(
            evalItem(id = "item-1", datasetVersion = "v1"),
            evalItem(id = "item-2", datasetVersion = "v2") // Mismatched
        )

        val issues = DatasetValidator.validateDatasetCohort(items, "v1")
        assertTrue(issues.any {
            it.field == "datasetVersion"
        })
    }

    @Test
    fun `manifest validation checks count consistency`() {
        val manifest = DatasetManifest(
            datasetVersion = "v1",
            createdAt = now,
            policyDescription = "test",
            sourceSessionIds = listOf("s1"),
            sourceProjectIds = listOf("p1"),
            modelVersionsRepresented = listOf("local-v6.0"),
            totalCandidateItems = 10, // Wrong: should be 5
            includedItemIds = listOf("1", "2", "3", "4", "5"),
            excludedItems = emptyList(),
            categoryDistribution = emptyList(),
            platformDistribution = emptyList(),
            contentTypeDistribution = emptyList(),
            durationDistribution = emptyList(),
            skipDistribution = emptyList(),
            interactionDistribution = emptyMap(),
            confidenceDistribution = emptyList(),
            modelVersionDistribution = emptyList(),
            totalCategoriesAvailable = 62,
            categoriesRepresented = 1,
            categoriesWithMeaningfulSupport = 1,
            totalPlatformsSupported = 10,
            platformsRepresented = 1,
            platformsWithMeaningfulSupport = 1,
            annotationCompleteness = emptyMap(),
            qualityFlags = emptyList(),
            ambiguousItemCount = 0,
            mixedItemCount = 0,
            disputedItemCount = 0,
            unknownCategoryCount = 0,
            duplicateCandidateCount = 0,
            duplicateExcludedCount = 0,
            temporalEarliestItem = null,
            temporalLatestItem = null
        )

        val issues = DatasetValidator.validateManifest(manifest)
        assertTrue(issues.any {
            it.field == "totalCandidateItems"
        })
    }

    // ================================================
    // CAPABILITY ELIGIBILITY TESTS
    // ================================================

    @Test
    fun `item with full truth is eligible for all capabilities`() {
        val item = evalItem(id = "item-1")
        val pred = prediction(
            evaluationItemId = "item-1",
            confidence = 0.9
        )
        val t = truth(
            evaluationItemId = "item-1",
            liked = true,
            commented = false,
            shared = true,
            saved = false,
            followed = false,
            paused = true,
            playing = false
        )

        val report = CapabilityEligibility.checkItem(item, pred, t)
        assertTrue(report.isEligibleFor(CapabilityEligibility.Capability.CATEGORY))
        assertTrue(report.isEligibleFor(CapabilityEligibility.Capability.PLATFORM))
        assertTrue(report.isEligibleFor(CapabilityEligibility.Capability.DURATION))
        assertTrue(report.isEligibleFor(CapabilityEligibility.Capability.SKIP))
        assertTrue(report.isEligibleFor(CapabilityEligibility.Capability.LIKE))
        assertTrue(report.isEligibleFor(CapabilityEligibility.Capability.CALIBRATION))
    }

    @Test
    fun `item with unknown liked is ineligible for like evaluation`() {
        val item = evalItem(id = "item-1")
        val pred = prediction(evaluationItemId = "item-1")
        val t = truth(
            evaluationItemId = "item-1",
            liked = null // UNKNOWN
        )

        val report = CapabilityEligibility.checkItem(item, pred, t)
        assertTrue(report.isEligibleFor(CapabilityEligibility.Capability.CATEGORY))
        assertFalse(report.isEligibleFor(CapabilityEligibility.Capability.LIKE))
    }

    @Test
    fun `unreviewed item is ineligible for all capabilities`() {
        val item = evalItem(
            id = "item-1",
            status = EvaluationItem.STATUS_NOT_EVALUATED
        )

        val report = CapabilityEligibility.checkItem(item, null, null)
        assertFalse(report.isEligibleFor(CapabilityEligibility.Capability.CATEGORY))
        assertEquals(
            CapabilityEligibility.Capability.entries.size,
            report.ineligibleCapabilities().size
        )
    }

    @Test
    fun `batch summary computes correct eligibility rates`() {
        val item1 = evalItem(id = "item-1")
        val item2 = evalItem(id = "item-2")

        val triples = listOf(
            Triple(
                item1,
                prediction(evaluationItemId = "item-1"),
                truth(evaluationItemId = "item-1", liked = true)
            ),
            Triple(
                item2,
                prediction(evaluationItemId = "item-2"),
                truth(evaluationItemId = "item-2", liked = null)
            )
        )

        val summary = CapabilityEligibility.batchSummary(triples)
        val likeSummary = summary[CapabilityEligibility.Capability.LIKE]!!

        assertEquals(2, likeSummary.totalItems)
        assertEquals(1, likeSummary.eligibleCount)
        assertEquals(1, likeSummary.excludedCount)
        assertEquals(0.5, likeSummary.eligibleFraction, 0.01)
    }

    @Test
    fun `missing platform is ineligible for platform evaluation`() {
        val item = evalItem(id = "item-1")
        val t = truth(evaluationItemId = "item-1", platform = null)

        val report = CapabilityEligibility.checkItem(
            item, prediction(evaluationItemId = "item-1"), t
        )
        assertFalse(report.isEligibleFor(CapabilityEligibility.Capability.PLATFORM))
    }

    @Test
    fun `filter by capability returns correct eligible and excluded`() {
        val item1 = evalItem(id = "item-1")
        val item2 = evalItem(id = "item-2")

        val triples = listOf(
            Triple(item1, prediction(evaluationItemId = "item-1"), truth(evaluationItemId = "item-1")),
            Triple(item2, prediction(evaluationItemId = "item-2"), truth(evaluationItemId = "item-2", platform = null))
        )

        val result = CapabilityEligibility.filterByCapability(
            triples, CapabilityEligibility.Capability.PLATFORM
        )

        assertEquals(1, result.eligibleItemIds.size)
        assertEquals(1, result.excluded.size)
        assertEquals("item-1", result.eligibleItemIds[0])
        assertEquals("item-2", result.excluded[0].evaluationItemId)
    }

    // ================================================
    // DATASET REPORTER TESTS
    // ================================================

    @Test
    fun `report generates correct category distribution`() {
        val items = (1..3).map { i ->
            evalItem(id = "item-$i")
        }

        val preds = items.associate { it.id to prediction(evaluationItemId = it.id) }
        val truths = mapOf(
            "item-1" to truth(evaluationItemId = "item-1", category = "sports"),
            "item-2" to truth(evaluationItemId = "item-2", category = "sports"),
            "item-3" to truth(evaluationItemId = "item-3", category = "comedy")
        )

        val report = DatasetReporter.generateReport(items, preds, truths)

        assertEquals(3, report.totalItems)
        val sportsEntry = report.categoryDistribution.find { it.key == "sports" }
        assertNotNull(sportsEntry)
        assertEquals(2, sportsEntry!!.count)
        val comedyEntry = report.categoryDistribution.find { it.key == "comedy" }
        assertNotNull(comedyEntry)
        assertEquals(1, comedyEntry!!.count)
    }

    @Test
    fun `report tracks unknown values correctly`() {
        val items = listOf(evalItem(id = "item-1"))

        val preds = mapOf("item-1" to prediction(evaluationItemId = "item-1"))
        val truths = mapOf(
            "item-1" to truth(
                evaluationItemId = "item-1",
                platform = null,
                topic = null,
                tone = null
            )
        )

        val report = DatasetReporter.generateReport(items, preds, truths)

        // Platform completeness should be 0
        assertEquals(0.0, report.annotationCompleteness["platform"]!!, 0.01)
        // Topic completeness should be 0
        assertEquals(0.0, report.annotationCompleteness["topic"]!!, 0.01)
        // Category completeness should be 1 (has category, not UNKNOWN ambiguity)
        assertEquals(1.0, report.annotationCompleteness["category"]!!, 0.01)
    }

    @Test
    fun `report generates quality flags for small dataset`() {
        val items = listOf(evalItem(id = "item-1"))

        val preds = mapOf("item-1" to prediction(evaluationItemId = "item-1"))
        val truths = mapOf("item-1" to truth(evaluationItemId = "item-1"))

        val report = DatasetReporter.generateReport(items, preds, truths)

        assertTrue(report.qualityFlags.any {
            it.flag == "INSUFFICIENT_SAMPLE_SIZE"
        })
    }

    @Test
    fun `report computes platform coverage correctly`() {
        val items = listOf(
            evalItem(id = "item-1"),
            evalItem(id = "item-2")
        )

        val preds = items.associate { it.id to prediction(evaluationItemId = it.id) }
        val truths = mapOf(
            "item-1" to truth(evaluationItemId = "item-1", platform = "Instagram"),
            "item-2" to truth(evaluationItemId = "item-2", platform = "YouTube")
        )

        val report = DatasetReporter.generateReport(items, preds, truths)

        assertEquals(10, report.totalPlatformsSupported) // 10 canonical platforms
        assertEquals(2, report.platformsRepresented)
    }

    @Test
    fun `report computes category coverage correctly`() {
        val items = listOf(evalItem(id = "item-1"))

        val preds = mapOf("item-1" to prediction(evaluationItemId = "item-1"))
        val truths = mapOf("item-1" to truth(evaluationItemId = "item-1", category = "sports"))

        val report = DatasetReporter.generateReport(items, preds, truths)

        assertTrue(report.totalCategoriesAvailable > 0)
        assertTrue(report.categoriesRepresented >= 1)
    }

    @Test
    fun `interaction distribution reports tri-state correctly`() {
        val items = listOf(evalItem(id = "item-1"))

        val preds = mapOf("item-1" to prediction(evaluationItemId = "item-1"))
        val truths = mapOf(
            "item-1" to truth(
                evaluationItemId = "item-1",
                liked = true,
                commented = null, // UNKNOWN
                shared = false
            )
        )

        val report = DatasetReporter.generateReport(items, preds, truths)

        val likeDist = report.interactionDistribution["liked"]
        assertNotNull(likeDist)
        assertTrue(likeDist!!.any { it.key == "true" && it.count == 1 })

        val commentDist = report.interactionDistribution["commented"]
        assertNotNull(commentDist)
        assertTrue(commentDist!!.any { it.key == "unknown" && it.count == 1 })

        val shareDist = report.interactionDistribution["shared"]
        assertNotNull(shareDist)
        assertTrue(shareDist!!.any { it.key == "false" && it.count == 1 })
    }

    @Test
    fun `confidence distribution buckets are correct`() {
        val items = (1..3).map { i ->
            evalItem(id = "item-$i")
        }

        val preds = mapOf(
            "item-1" to prediction(evaluationItemId = "item-1", confidence = 0.05),
            "item-2" to prediction(evaluationItemId = "item-2", confidence = 0.55),
            "item-3" to prediction(evaluationItemId = "item-3", confidence = 0.95)
        )
        val truths = items.associate { it.id to truth(evaluationItemId = it.id) }

        val report = DatasetReporter.generateReport(items, preds, truths)

        assertEquals(3, report.confidenceDistribution.size)
        assertTrue(report.confidenceDistribution.any { it.key == "0-10%" && it.count == 1 })
        assertTrue(report.confidenceDistribution.any { it.key == "50-60%" && it.count == 1 })
        assertTrue(report.confidenceDistribution.any { it.key == "90-100%" && it.count == 1 })
    }

    // ================================================
    // DATASET MANIFEST TESTS
    // ================================================

    @Test
    fun `manifest round-trips through JSON`() {
        val manifest = DatasetManifest(
            datasetVersion = "evaluation-v1",
            createdAt = now,
            policyDescription = "test policy",
            sourceSessionIds = listOf("s1", "s2"),
            sourceProjectIds = listOf("p1"),
            modelVersionsRepresented = listOf("local-v6.0"),
            totalCandidateItems = 10,
            includedItemIds = listOf("i1", "i2", "i3"),
            excludedItems = listOf(
                DatasetManifest.ExcludedItemRecord("i4", "EXCLUDED_DISPUTED")
            ),
            categoryDistribution = listOf(
                DatasetManifest.CountEntry("sports", 2),
                DatasetManifest.CountEntry("comedy", 1)
            ),
            platformDistribution = listOf(DatasetManifest.CountEntry("Instagram", 3)),
            contentTypeDistribution = listOf(DatasetManifest.CountEntry("SHORT_VIDEO", 3)),
            durationDistribution = listOf(DatasetManifest.CountEntry("0-5s", 3)),
            skipDistribution = listOf(DatasetManifest.CountEntry("watched", 2), DatasetManifest.CountEntry("skipped", 1)),
            interactionDistribution = mapOf(
                "liked" to listOf(DatasetManifest.CountEntry("true", 1), DatasetManifest.CountEntry("unknown", 2))
            ),
            confidenceDistribution = listOf(DatasetManifest.CountEntry("80-90%", 3)),
            modelVersionDistribution = listOf(DatasetManifest.CountEntry("local-v6.0", 3)),
            totalCategoriesAvailable = 62,
            categoriesRepresented = 2,
            categoriesWithMeaningfulSupport = 1,
            totalPlatformsSupported = 10,
            platformsRepresented = 1,
            platformsWithMeaningfulSupport = 1,
            annotationCompleteness = mapOf("category" to 1.0, "platform" to 1.0),
            qualityFlags = listOf(
                DatasetManifest.QualityFlag(
                    flag = "INSUFFICIENT_SAMPLE_SIZE",
                    severity = DatasetManifest.QualityFlag.Severity.WARNING,
                    message = "Only 3 items"
                )
            ),
            ambiguousItemCount = 0,
            mixedItemCount = 0,
            disputedItemCount = 1,
            unknownCategoryCount = 0,
            duplicateCandidateCount = 0,
            duplicateExcludedCount = 0,
            temporalEarliestItem = now.toString(),
            temporalLatestItem = now.plusHours(1).toString()
        )

        val json = manifest.toJson()
        val reparsed = DatasetManifest.fromJson(json)

        assertEquals(manifest.datasetVersion, reparsed.datasetVersion)
        assertEquals(manifest.includedItemIds, reparsed.includedItemIds)
        assertEquals(manifest.excludedItems.size, reparsed.excludedItems.size)
        assertEquals(manifest.categoryDistribution, reparsed.categoryDistribution)
        assertEquals(manifest.qualityFlags.size, reparsed.qualityFlags.size)
        assertEquals(manifest.annotationCompleteness, reparsed.annotationCompleteness)
    }

    @Test
    fun `manifest is valid JSON`() {
        val manifest = DatasetManifest(
            datasetVersion = "v1",
            createdAt = now,
            policyDescription = "test",
            sourceSessionIds = emptyList(),
            sourceProjectIds = emptyList(),
            modelVersionsRepresented = emptyList(),
            totalCandidateItems = 0,
            includedItemIds = emptyList(),
            excludedItems = emptyList(),
            categoryDistribution = emptyList(),
            platformDistribution = emptyList(),
            contentTypeDistribution = emptyList(),
            durationDistribution = emptyList(),
            skipDistribution = emptyList(),
            interactionDistribution = emptyMap(),
            confidenceDistribution = emptyList(),
            modelVersionDistribution = emptyList(),
            totalCategoriesAvailable = 62,
            categoriesRepresented = 0,
            categoriesWithMeaningfulSupport = 0,
            totalPlatformsSupported = 10,
            platformsRepresented = 0,
            platformsWithMeaningfulSupport = 0,
            annotationCompleteness = emptyMap(),
            qualityFlags = emptyList(),
            ambiguousItemCount = 0,
            mixedItemCount = 0,
            disputedItemCount = 0,
            unknownCategoryCount = 0,
            duplicateCandidateCount = 0,
            duplicateExcludedCount = 0,
            temporalEarliestItem = null,
            temporalLatestItem = null
        )

        val json = manifest.toJson()
        // Should parse without exception
        val parsed = JSONObject(json)
        assertEquals("v1", parsed.getString("datasetVersion"))
    }

    // ================================================
    // PROPERTY TESTS
    // ================================================

    @Test
    fun `included plus excluded equals candidate population`() {
        val items = (1..5).map { i ->
            evalItem(
                id = "item-$i",
                status = if (i <= 3) EvaluationItem.STATUS_EVALUATED
                else EvaluationItem.STATUS_NOT_EVALUATED
            )
        }

        val preds = items.associate { it.id to prediction(evaluationItemId = it.id) }
        val truths = items.associate { it.id to truth(evaluationItemId = it.id) }

        val result = DatasetConstructor.construct(
            DatasetConstructionPolicy.default("v1"),
            items, preds, truths
        )

        assertEquals(
            result.manifest.totalCandidateItems,
            result.manifest.includedItemIds.size + result.manifest.excludedItems.size
        )
    }

    @Test
    fun `manifest IDs are unique`() {
        val items = (1..10).map { i ->
            evalItem(id = "item-$i")
        }

        val preds = items.associate { it.id to prediction(evaluationItemId = it.id) }
        val truths = items.associate { it.id to truth(evaluationItemId = it.id) }

        val result = DatasetConstructor.construct(
            DatasetConstructionPolicy.default("v1"),
            items, preds, truths
        )

        val ids = result.manifest.includedItemIds
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `no item appears twice in included`() {
        val items = (1..20).map { i ->
            evalItem(id = "item-$i")
        }

        val preds = items.associate { it.id to prediction(evaluationItemId = it.id) }
        val truths = items.associate { it.id to truth(evaluationItemId = it.id) }

        val result = DatasetConstructor.construct(
            DatasetConstructionPolicy.default("v1"),
            items, preds, truths
        )

        assertEquals(
            result.manifest.includedItemIds.size,
            result.manifest.includedItemIds.toSet().size
        )
    }

    @Test
    fun `category counts sum correctly`() {
        val items = (1..5).map { i ->
            evalItem(id = "item-$i")
        }

        val preds = items.associate { it.id to prediction(evaluationItemId = it.id) }
        val truths = items.associate { it.id to truth(evaluationItemId = it.id) }

        val result = DatasetConstructor.construct(
            DatasetConstructionPolicy.default("v1"),
            items, preds, truths
        )

        val categorySum = result.manifest.categoryDistribution.sumOf { it.count }
        assertEquals(result.manifest.includedItemIds.size, categorySum)
    }

    @Test
    fun `skip counts sum correctly`() {
        val items = (1..3).map { i ->
            evalItem(id = "item-$i")
        }

        val preds = items.associate { it.id to prediction(evaluationItemId = it.id) }
        val truths = mapOf(
            "item-1" to truth(evaluationItemId = "item-1", skipped = true),
            "item-2" to truth(evaluationItemId = "item-2", skipped = false),
            "item-3" to truth(evaluationItemId = "item-3", skipped = null)
        )

        val result = DatasetConstructor.construct(
            DatasetConstructionPolicy.default("v1"),
            items, preds, truths
        )

        val skipSum = result.manifest.skipDistribution.sumOf { it.count }
        assertEquals(result.manifest.includedItemIds.size, skipSum)
    }

    @Test
    fun `unknown plus known equals relevant field population for interactions`() {
        val items = listOf(evalItem(id = "item-1"))

        val preds = mapOf("item-1" to prediction(evaluationItemId = "item-1"))
        val truths = mapOf(
            "item-1" to truth(
                evaluationItemId = "item-1",
                liked = true,
                commented = null,
                shared = false
            )
        )

        val report = DatasetReporter.generateReport(items, preds, truths)

        // For liked: true=1, unknown=0, false=0 -> total=1
        val likeDist = report.interactionDistribution["liked"]!!
        val likeSum = likeDist.sumOf { it.count }
        assertEquals(1, likeSum)

        // For commented: unknown=1 -> total=1
        val commentDist = report.interactionDistribution["commented"]!!
        val commentSum = commentDist.sumOf { it.count }
        assertEquals(1, commentSum)
    }

    // ================================================
    // EXCLUSION REASON TESTS
    // ================================================

    @Test
    fun `each exclusion has a reason`() {
        val items = listOf(
            evalItem(id = "item-1", status = EvaluationItem.STATUS_NOT_EVALUATED),
            evalItem(id = "item-2", status = EvaluationItem.STATUS_DISPUTED),
            evalItem(id = "item-3") // evaluated but no prediction
        )

        val truths = items.associate { it.id to truth(evaluationItemId = it.id) }

        val result = DatasetConstructor.construct(
            DatasetConstructionPolicy(
                datasetVersion = "v1",
                includeDisputed = false
            ),
            items, emptyMap(), truths
        )

        // All excluded items should have a non-empty reason
        result.excludedItems.forEach { excluded ->
            assertTrue(excluded.reason.isNotBlank())
            assertTrue(excluded.evaluationItemId.isNotBlank())
        }
    }

    @Test
    fun `disputed reason is specific`() {
        val item = evalItem(
            id = "item-1",
            status = EvaluationItem.STATUS_DISPUTED
        )

        val result = DatasetConstructor.construct(
            DatasetConstructionPolicy(
                datasetVersion = "v1",
                includeDisputed = false
            ),
            listOf(item),
            mapOf("item-1" to prediction(evaluationItemId = "item-1")),
            mapOf("item-1" to truth(evaluationItemId = "item-1"))
        )

        assertEquals("EXCLUDED_DISPUTED", result.excludedItems.first().reason)
    }

    // ================================================
    // TIMING / PROVENANCE TESTS
    // ================================================

    @Test
    fun `temporal range is preserved in manifest`() {
        val items = listOf(
            evalItem(id = "item-1", enqueuedAt = now),
            evalItem(id = "item-2", enqueuedAt = now.plusHours(2))
        )

        val preds = items.associate { it.id to prediction(evaluationItemId = it.id) }
        val truths = items.associate { it.id to truth(evaluationItemId = it.id) }

        val result = DatasetConstructor.construct(
            DatasetConstructionPolicy.default("v1"),
            items, preds, truths
        )

        assertNotNull(result.manifest.temporalEarliestItem)
        assertNotNull(result.manifest.temporalLatestItem)
        assertEquals(now.toString(), result.manifest.temporalEarliestItem)
        assertEquals(now.plusHours(2).toString(), result.manifest.temporalLatestItem)
    }

    @Test
    fun `session IDs are preserved in manifest`() {
        val items = listOf(
            evalItem(id = "item-1", sessionId = "session-A"),
            evalItem(id = "item-2", sessionId = "session-B")
        )

        val preds = items.associate { it.id to prediction(evaluationItemId = it.id) }
        val truths = items.associate { it.id to truth(evaluationItemId = it.id) }

        val result = DatasetConstructor.construct(
            DatasetConstructionPolicy.default("v1"),
            items, preds, truths
        )

        assertTrue(result.manifest.sourceSessionIds.contains("session-A"))
        assertTrue(result.manifest.sourceSessionIds.contains("session-B"))
    }

    // ================================================
    // PRIVACY TESTS
    // ================================================

    @Test
    fun `no filesystem paths in manifest included IDs`() {
        val items = listOf(evalItem(id = "item-1"))

        val preds = mapOf("item-1" to prediction(evaluationItemId = "item-1"))
        val truths = mapOf("item-1" to truth(evaluationItemId = "item-1"))

        val result = DatasetConstructor.construct(
            DatasetConstructionPolicy.default("v1"),
            items, preds, truths
        )

        // IDs should not contain path separators
        result.manifest.includedItemIds.forEach { id ->
            assertFalse(id.contains("/"))
            assertFalse(id.contains("\\"))
        }
    }

    // ================================================
    // VALIDATION RESULT TESTS
    // ================================================

    @Test
    fun `validation result reports error and warning counts`() {
        val item = evalItem(id = "item-1")

        val result = DatasetValidator.validateBatch(
            listOf(
                Triple(
                    item,
                    prediction(evaluationItemId = "item-1", confidence = -0.1), // Error
                    truth(evaluationItemId = "item-1", platform = "FakePlatform") // Warning
                )
            )
        )

        assertTrue(result.errorCount > 0)
        assertTrue(result.warningCount > 0)
        assertFalse(result.isValid)
    }

    @Test
    fun `all items with no errors are counted as valid`() {
        val items = (1..5).map { i ->
            Triple(
                evalItem(id = "item-$i"),
                prediction(evaluationItemId = "item-$i"),
                truth(evaluationItemId = "item-$i")
            )
        }

        val result = DatasetValidator.validateBatch(items)
        assertEquals(5, result.validItemCount)
        assertEquals(0, result.invalidItemCount)
        assertTrue(result.isValid)
    }
}
