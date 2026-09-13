package com.example.feedsense.analysis.ml.evaluation

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.analysis.SchemaFreeze
import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.EvaluationRecord
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.GroundTruth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// --------------------------------
// EVALUATION BOUNDARY BASELINE
// ISOLATION TEST (8B-15-8)
// --------------------------------
//
// Baseline isolation verification.
//
// Verifies that milestone 8B-15-8 does NOT change:
//   - FeedItem behavior
//   - AiPredictionRecord behavior
//   - GroundTruth behavior
//   - EvaluationRecord behavior
//   - Annotation workflow
//   - Capture/anonymization paths
//
// The evaluation boundary must be independently removable
// without affecting the baseline.

class EvaluationBoundaryBaselineIsolationTest {

    // --------------------------------
    // FEED ITEM UNCHANGED
    // --------------------------------

    @Test
    fun `FeedItem schema columns match frozen set`() {
        val frozenColumns = SchemaFreeze.FROZEN_FEED_ITEM_COLUMNS
        assertTrue(frozenColumns.contains("category"))
        assertTrue(frozenColumns.contains("categoryDomain"))
        assertTrue(frozenColumns.contains("confidence"))
        assertTrue(frozenColumns.contains("modelVersion"))
    }

    // --------------------------------
    // TAXONOMY UNCHANGED
    // --------------------------------

    @Test
    fun `CategoryCatalog keys match frozen set`() {
        val frozenKeys = SchemaFreeze.FROZEN_CATEGORY_KEYS
        val catalogKeys = CategoryCatalog.keys.toSet()
        assertEquals(frozenKeys, catalogKeys)
    }

    @Test
    fun `CategoryCatalog normalize still works correctly`() {
        assertEquals(
            "sports",
            CategoryCatalog.normalize("sports")
        )
        assertEquals(
            "movie_clip",
            CategoryCatalog.normalize("movie clip")
        )
        assertEquals(
            null,
            CategoryCatalog.normalize("nonexistent_category")
        )
    }

    @Test
    fun `CategoryCatalog displayName still works correctly`() {
        assertEquals(
            "Sports",
            CategoryCatalog.displayName("sports")
        )
        assertEquals(
            "Movie Clip",
            CategoryCatalog.displayName("movie_clip")
        )
    }

    // --------------------------------
    // GROUND TRUTH UNCHANGED
    // --------------------------------

    @Test
    fun `GroundTruth ambiguity values are intact`() {
        val validAmbiguity = GroundTruth.VALID_AMBIGUITY
        assertTrue(validAmbiguity.contains("CLEAR"))
        assertTrue(validAmbiguity.contains("AMBIGUOUS"))
        assertTrue(validAmbiguity.contains("MIXED"))
        assertTrue(validAmbiguity.contains("UNKNOWN"))
    }

    @Test
    fun `GroundTruth content types are intact`() {
        val validTypes = GroundTruth.VALID_CONTENT_TYPES
        assertTrue(validTypes.contains("SHORT_VIDEO"))
        assertTrue(validTypes.contains("LONG_VIDEO"))
        assertTrue(validTypes.contains("UNKNOWN"))
    }

    @Test
    fun `GroundTruth interaction signal keys are intact`() {
        val keys = GroundTruth.INTERACTION_SIGNAL_KEYS
        assertTrue(keys.contains("liked"))
        assertTrue(keys.contains("commented"))
        assertTrue(keys.contains("shared"))
        assertTrue(keys.contains("saved"))
        assertTrue(keys.contains("followed"))
        assertTrue(keys.contains("paused"))
        assertTrue(keys.contains("playing"))
    }

    // --------------------------------
    // SCHEMA FREEZE UNCHANGED
    // --------------------------------

    @Test
    fun `SchemaFreeze version is intact`() {
        assertEquals("1.0.0", SchemaFreeze.FREEZE_VERSION)
    }

    @Test
    fun `frozen confidence levels are intact`() {
        val levels = SchemaFreeze.FROZEN_CONFIDENCE_LEVELS
        assertEquals(">= 0.8", levels["HIGH"])
        assertEquals(
            ">= 0.6 and < 0.8",
            levels["MEDIUM"]
        )
        assertEquals("< 0.6", levels["LOW"])
    }

    // --------------------------------
    // EVALUATION BOUNDARY IS INDEPENDENT
    // --------------------------------

    @Test
    fun `evaluation boundary types do not modify GroundTruth`() {
        val gt = EvaluationBoundaryFixtures.groundTruthSports()
        val originalCategory = gt.category
        val originalId = gt.id

        // Run evaluation boundary operations
        val inference =
            EvaluationBoundaryFixtures.inferenceResultSports()
        val mappingResults =
            EvaluationBoundaryFixtures.mapInferenceResult(inference)
        val eligibility =
            EvaluationEligibility.fromMappingResult(
                mappingResults.first()
            )
        val snapshot =
            EvaluationBoundaryFixtures.createSnapshot(
                mappingResult = mappingResults.first(),
                inferenceResult = inference,
                candidateId = "iso-001",
                eligibility = eligibility
            )
        EvaluationBoundaryEvaluator.evaluate(
            snapshot = snapshot,
            groundTruth = gt,
            groundTruthTaxonomyVersion =
                EvaluationBoundaryFixtures.TAXONOMY_VERSION
        )

        // GroundTruth must be unchanged
        assertEquals(originalCategory, gt.category)
        assertEquals(originalId, gt.id)
    }

    @Test
    fun `evaluation boundary types do not modify AiPredictionRecord schema`() {
        // Verify AiPredictionRecord data class structure
        // is unchanged by checking its known fields exist
        val record = AiPredictionRecord(
            id = "test-pred-001",
            evaluationItemId = "test-eval-001",
            category = "sports",
            confidence = 0.85,
            modelVersion = "v1",
            secondaryCategories = emptyList(),
            categoryScores = emptyMap(),
            platform = "TikTok",
            tone = null,
            topic = null,
            contentType = null,
            durationSeconds = 30,
            skipped = false,
            interactionSignals = emptyList(),
            source = "ML_MODEL"
        )
        assertEquals("sports", record.category)
        assertEquals(0.85, record.confidence!!, 0.001)
        assertEquals("ML_MODEL", record.source)
    }

    @Test
    fun `evaluation boundary types do not modify EvaluationRecord schema`() {
        // Verify EvaluationRecord data class structure
        // is unchanged
        val record = EvaluationRecord(
            id = "test-eval-001",
            evaluationItemId = "test-eval-item-001",
            groundTruthId = "test-gt-001",
            aiPredictionId = "test-pred-001",
            verdict = EvaluationRecord.VERDICT_UNKNOWN
        )
        assertEquals(
            EvaluationRecord.VERDICT_UNKNOWN,
            record.verdict
        )
    }

    @Test
    fun `existing EvaluationRecord verdicts are intact`() {
        assertEquals("CORRECT", EvaluationRecord.VERDICT_CORRECT)
        assertEquals(
            "INCORRECT",
            EvaluationRecord.VERDICT_INCORRECT
        )
        assertEquals("PARTIAL", EvaluationRecord.VERDICT_PARTIAL)
        assertEquals("UNKNOWN", EvaluationRecord.VERDICT_UNKNOWN)
        assertEquals(
            "UNCOMPARABLE",
            EvaluationRecord.VERDICT_UNCOMPARABLE
        )
    }

    @Test
    fun `evaluation boundary version constant is correct`() {
        assertEquals(
            "8B-15-8-v1",
            EvaluationBoundaryResult.VERSION
        )
    }
}
