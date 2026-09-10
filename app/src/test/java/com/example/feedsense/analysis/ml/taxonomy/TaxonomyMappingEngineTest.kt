package com.example.feedsense.analysis.ml.taxonomy

import com.example.feedsense.analysis.ml.InferenceStatus
import com.example.feedsense.analysis.ml.ModelInferenceResult
import com.example.feedsense.analysis.ml.RankedPrediction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-7.
 *
 * Tests for TaxonomyMappingEngine: deterministic mapping from
 * model-native predictions to the FeedSense taxonomy.
 *
 * Covers: direct mappings, many-to-one, unmapped, ambiguous,
 * implicit DIRECT, invalid targets, and determinism.
 */
class TaxonomyMappingEngineTest {

    private val fixtures get() = TaxonomyMappingFixtures

    // --------------------------------
    // DIRECT MAPPINGS
    // --------------------------------

    @Test
    fun `direct mapping maps model label to correct taxonomy key`() {
        val predictions = listOf(
            RankedPrediction("sports", 0.9),
            RankedPrediction("entertainment", 0.05)
        )

        val results = TaxonomyMappingEngine.mapPredictions(
            predictions = predictions,
            mappings = listOf(
                fixtures.DIRECT_SPORTS,
                fixtures.DIRECT_ENTERTAINMENT
            ),
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )

        assertEquals(2, results.size)

        val first = results[0] as TaxonomyMappingResult.Mapped
        assertEquals("sports", first.modelLabel)
        assertEquals(0, first.modelLabelIndex)
        assertEquals("sports", first.feedSenseTaxonomyKey)
        assertEquals("Sports", first.feedSenseTaxonomyDisplayName)
        assertEquals(MappingStatus.DIRECT, first.status)
        assertEquals(
            fixtures.DIRECT_SPORTS.mappingId,
            first.mappingId
        )
        assertEquals(
            fixtures.TAXONOMY_VERSION,
            first.taxonomyVersion
        )
    }

    @Test
    fun `direct mapping preserves provenance and rationale`() {
        val predictions = listOf(
            RankedPrediction("education", 0.85)
        )

        val results = TaxonomyMappingEngine.mapPredictions(
            predictions = predictions,
            mappings = listOf(fixtures.DIRECT_EDUCATION),
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )

        val mapped = results[0] as TaxonomyMappingResult.Mapped
        assertEquals(
            MappingRationale.SEMANTIC_EQUIVALENCE,
            mapped.rationale
        )
        assertEquals(
            MappingProvenance.PROJECT_DEFINED,
            mapped.provenance
        )
    }

    // --------------------------------
    // MANY-TO-ONE MAPPINGS
    // --------------------------------

    @Test
    fun `many-to-one maps multiple model labels to same taxonomy key`() {
        val predictions = listOf(
            RankedPrediction("model_sports_variant", 0.8),
            RankedPrediction("model_athletics", 0.15),
            RankedPrediction("other", 0.05)
        )

        val results = TaxonomyMappingEngine.mapPredictions(
            predictions = predictions,
            mappings = listOf(
                fixtures.MANY_TO_ONE_MODEL_A,
                fixtures.MANY_TO_ONE_MODEL_B
            ),
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )

        assertEquals(3, results.size)

        val first = results[0] as TaxonomyMappingResult.Mapped
        assertEquals("sports", first.feedSenseTaxonomyKey)

        val second = results[1] as TaxonomyMappingResult.Mapped
        assertEquals("sports", second.feedSenseTaxonomyKey)
    }

    // --------------------------------
    // UNMAPPED LABELS
    // --------------------------------

    @Test
    fun `unmapped label produces unmapped result`() {
        val predictions = listOf(
            RankedPrediction("weather_forecast", 0.7)
        )

        val results = TaxonomyMappingEngine.mapPredictions(
            predictions = predictions,
            mappings = listOf(fixtures.UNMAPPED_LABEL),
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )

        assertEquals(1, results.size)

        val unmapped = results[0] as TaxonomyMappingResult.Unmapped
        assertEquals("weather_forecast", unmapped.modelLabel)
        assertTrue(unmapped.reason.contains("no equivalent"))
    }

    @Test
    fun `label without mapping and not in taxonomy is unmapped`() {
        val predictions = listOf(
            RankedPrediction("completely_unknown_label", 0.5)
        )

        val results = TaxonomyMappingEngine.mapPredictions(
            predictions = predictions,
            mappings = emptyList(),
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )

        assertEquals(1, results.size)
        assertTrue(results[0] is TaxonomyMappingResult.Unmapped)
    }

    // --------------------------------
    // AMBIGUOUS MAPPINGS
    // --------------------------------

    @Test
    fun `ambiguous mapping produces ambiguous result`() {
        val predictions = listOf(
            RankedPrediction("daily_vlog", 0.6)
        )

        val results = TaxonomyMappingEngine.mapPredictions(
            predictions = predictions,
            mappings = listOf(fixtures.AMBIGUOUS_LABEL),
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )

        assertEquals(1, results.size)

        val ambiguous = results[0] as TaxonomyMappingResult.Ambiguous
        assertEquals("daily_vlog", ambiguous.modelLabel)
        assertTrue(ambiguous.ambiguityReason.contains("could map"))
    }

    // --------------------------------
    // IMPLICIT DIRECT MAPPING
    // --------------------------------

    @Test
    fun `label matching taxonomy key via normalize is implicit direct`() {
        val predictions = listOf(
            RankedPrediction("meme", 0.75)
        )

        val results = TaxonomyMappingEngine.mapPredictions(
            predictions = predictions,
            mappings = emptyList(),
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )

        assertEquals(1, results.size)

        val mapped = results[0] as TaxonomyMappingResult.Mapped
        assertEquals("meme", mapped.feedSenseTaxonomyKey)
        assertEquals(MappingStatus.DIRECT, mapped.status)
        assertTrue(mapped.mappingId.startsWith("implicit-direct:"))
    }

    @Test
    fun `normalized alias produces implicit direct mapping`() {
        val predictions = listOf(
            RankedPrediction("movie clip", 0.65)
        )

        val results = TaxonomyMappingEngine.mapPredictions(
            predictions = predictions,
            mappings = emptyList(),
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )

        val mapped = results[0] as TaxonomyMappingResult.Mapped
        assertEquals("movie_clip", mapped.feedSenseTaxonomyKey)
    }

    // --------------------------------
    // INVALID TARGET
    // --------------------------------

    @Test
    fun `mapping with invalid taxonomy key produces invalid result`() {
        val predictions = listOf(
            RankedPrediction("fake_label", 0.5)
        )
        val invalidMapping = ModelTaxonomyMapping(
            mappingId = "test-invalid",
            modelArtifactId = fixtures.MODEL_ARTIFACT_ID,
            modelArtifactVersion = fixtures.MODEL_ARTIFACT_VERSION,
            modelLabelIndex = 0,
            modelLabel = "fake_label",
            feedSenseTaxonomyKey = "nonexistent_key",
            status = MappingStatus.MAPPED,
            rationale = MappingRationale.SEMANTIC_EQUIVALENCE,
            provenance = MappingProvenance.PROJECT_DEFINED,
            mappingTableVersion = fixtures.MAPPING_TABLE_VERSION,
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )

        val results = TaxonomyMappingEngine.mapPredictions(
            predictions = predictions,
            mappings = listOf(invalidMapping),
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )

        val invalid = results[0] as TaxonomyMappingResult.Invalid
        assertEquals(
            MappingFailureCode.INVALID_TAXONOMY_KEY,
            invalid.failureCode
        )
    }

    // --------------------------------
    // REJECTED MAPPING
    // --------------------------------

    @Test
    fun `rejected mapping produces invalid result`() {
        val predictions = listOf(
            RankedPrediction("model_political_commentary", 0.4)
        )

        val results = TaxonomyMappingEngine.mapPredictions(
            predictions = predictions,
            mappings = listOf(fixtures.REJECTED_LABEL),
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )

        val invalid = results[0] as TaxonomyMappingResult.Invalid
        assertEquals(
            MappingFailureCode.INCOMPATIBLE_MAPPING,
            invalid.failureCode
        )
    }

    // --------------------------------
    // UNSUPPORTED LABEL
    // --------------------------------

    @Test
    fun `unsupported label produces unmapped result`() {
        val predictions = listOf(
            RankedPrediction("unknown_model_class", 0.3)
        )

        val results = TaxonomyMappingEngine.mapPredictions(
            predictions = predictions,
            mappings = listOf(fixtures.UNSUPPORTED_LABEL),
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )

        val unmapped = results[0] as TaxonomyMappingResult.Unmapped
        assertTrue(unmapped.reason.contains("outside the known"))
    }

    // --------------------------------
    // MODEL INFERENCE RESULT INPUT
    // --------------------------------

    @Test
    fun `engine accepts ModelInferenceResult as input`() {
        val inferenceResult = ModelInferenceResult(
            modelId = fixtures.MODEL_ARTIFACT_ID,
            modelVersion = fixtures.MODEL_ARTIFACT_VERSION,
            status = InferenceStatus.SUCCESS,
            rankedPredictions = listOf(
                RankedPrediction("sports", 0.9)
            )
        )

        val results = TaxonomyMappingEngine.mapPredictions(
            inferenceResult = inferenceResult,
            mappings = listOf(fixtures.DIRECT_SPORTS),
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )

        assertEquals(1, results.size)
        assertTrue(results[0] is TaxonomyMappingResult.Mapped)
    }

    // --------------------------------
    // EMPTY PREDICTIONS
    // --------------------------------

    @Test
    fun `empty predictions produce empty results`() {
        val results = TaxonomyMappingEngine.mapPredictions(
            predictions = emptyList(),
            mappings = emptyList(),
            taxonomyVersion = "test-version"
        )

        assertTrue(results.isEmpty())
    }

    // --------------------------------
    // TAXONOMY VERSION COMPUTATION
    // --------------------------------

    @Test
    fun `taxonomy version is deterministic`() {
        val v1 = TaxonomyMappingEngine.computeTaxonomyVersion()
        val v2 = TaxonomyMappingEngine.computeTaxonomyVersion()
        assertEquals(v1, v2)
        assertTrue(v1.startsWith("taxonomy:"))
    }
}
