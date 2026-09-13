package com.example.feedsense.analysis.ml.taxonomy

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.analysis.SchemaFreeze
import com.example.feedsense.analysis.ml.InferenceStatus
import com.example.feedsense.analysis.ml.ModelInferenceResult
import com.example.feedsense.analysis.ml.RankedPrediction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-7.
 *
 * Integration test: standalone chain from model prediction
 * through taxonomy mapping to FeedSense research-category
 * mapping result (section 27).
 *
 * Chain:
 *   test model -> simulated 8B-15-5/6 output
 *   -> 8B-15-7 taxonomy mapping -> FeedSense category result
 *
 * Does NOT persist into production tables.
 * Does NOT change the baseline path.
 */
class TaxonomyMappingIntegrationTest {

    private val fixtures get() = TaxonomyMappingFixtures

    // --------------------------------
    // FULL CHAIN: DIRECT MAPPING
    // --------------------------------

    @Test
    fun `full chain maps model prediction to FeedSense category`() {
        // 1. Simulate model output (what 8B-15-5/6 would produce)
        val modelResult = ModelInferenceResult(
            modelId = fixtures.MODEL_ARTIFACT_ID,
            modelVersion = fixtures.MODEL_ARTIFACT_VERSION,
            modelChecksum = "integration-test-checksum",
            status = InferenceStatus.SUCCESS,
            rankedPredictions = listOf(
                RankedPrediction("sports", 0.87),
                RankedPrediction("entertainment", 0.08),
                RankedPrediction("meme", 0.03)
            ),
            preprocessVersion = "preprocess-v2",
            timestampMs = 1000L,
            inferenceLatencyMs = 45L
        )

        // 2. Apply taxonomy mapping (8B-15-7)
        val mappingResults = TaxonomyMappingEngine.mapPredictions(
            inferenceResult = modelResult,
            mappings = fixtures.ALL_MAPPINGS,
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )

        // 3. Verify the mapping chain
        assertEquals(3, mappingResults.size)

        val primary = mappingResults[0]
        assertTrue(primary is TaxonomyMappingResult.Mapped)
        val primaryMapped = primary as TaxonomyMappingResult.Mapped
        assertEquals("sports", primaryMapped.feedSenseTaxonomyKey)
        assertEquals("Sports", primaryMapped.feedSenseTaxonomyDisplayName)
        assertEquals(MappingStatus.DIRECT, primaryMapped.status)

        val secondary = mappingResults[1]
        assertTrue(secondary is TaxonomyMappingResult.Mapped)
        val secondaryMapped =
            secondary as TaxonomyMappingResult.Mapped
        assertEquals(
            "entertainment",
            secondaryMapped.feedSenseTaxonomyKey
        )

        val tertiary = mappingResults[2]
        assertTrue(tertiary is TaxonomyMappingResult.Mapped)
        val tertiaryMapped =
            tertiary as TaxonomyMappingResult.Mapped
        assertEquals("meme", tertiaryMapped.feedSenseTaxonomyKey)

        // 4. Verify version preservation
        assertEquals(
            fixtures.TAXONOMY_VERSION,
            primaryMapped.taxonomyVersion
        )
        assertEquals(
            fixtures.MAPPING_TABLE_VERSION,
            primaryMapped.mappingTableVersion
        )
    }

    // --------------------------------
    // FULL CHAIN: MANY-TO-ONE
    // --------------------------------

    @Test
    fun `full chain handles many-to-one mapping`() {
        val modelResult = ModelInferenceResult(
            modelId = fixtures.MODEL_ARTIFACT_ID,
            modelVersion = fixtures.MODEL_ARTIFACT_VERSION,
            status = InferenceStatus.SUCCESS,
            rankedPredictions = listOf(
                RankedPrediction("model_sports_variant", 0.75),
                RankedPrediction("model_athletics", 0.20)
            )
        )

        val mappingResults = TaxonomyMappingEngine.mapPredictions(
            inferenceResult = modelResult,
            mappings = fixtures.ALL_MAPPINGS,
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )

        assertEquals(2, mappingResults.size)

        val first = mappingResults[0] as TaxonomyMappingResult.Mapped
        val second =
            mappingResults[1] as TaxonomyMappingResult.Mapped

        assertEquals("sports", first.feedSenseTaxonomyKey)
        assertEquals("sports", second.feedSenseTaxonomyKey)
    }

    // --------------------------------
    // FULL CHAIN: UNMAPPED
    // --------------------------------

    @Test
    fun `full chain preserves unmapped labels`() {
        val modelResult = ModelInferenceResult(
            modelId = fixtures.MODEL_ARTIFACT_ID,
            modelVersion = fixtures.MODEL_ARTIFACT_VERSION,
            status = InferenceStatus.SUCCESS,
            rankedPredictions = listOf(
                RankedPrediction("completely_unknown_xyz", 0.6),
                RankedPrediction("sports", 0.3)
            )
        )

        val mappingResults = TaxonomyMappingEngine.mapPredictions(
            inferenceResult = modelResult,
            mappings = fixtures.ALL_MAPPINGS,
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )

        assertEquals(2, mappingResults.size)

        val unmapped = mappingResults[0]
        assertTrue(unmapped is TaxonomyMappingResult.Unmapped)

        val mapped = mappingResults[1]
        assertTrue(mapped is TaxonomyMappingResult.Mapped)
    }

    // --------------------------------
    // FULL CHAIN: MODEL LABELS REMAIN SEPARATE
    // --------------------------------

    @Test
    fun `model-native labels are preserved alongside taxonomy`() {
        val modelResult = ModelInferenceResult(
            modelId = fixtures.MODEL_ARTIFACT_ID,
            modelVersion = fixtures.MODEL_ARTIFACT_VERSION,
            status = InferenceStatus.SUCCESS,
            rankedPredictions = listOf(
                RankedPrediction("model_sports_variant", 0.8)
            )
        )

        val mappingResults = TaxonomyMappingEngine.mapPredictions(
            inferenceResult = modelResult,
            mappings = fixtures.ALL_MAPPINGS,
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )

        val mapped = mappingResults[0] as TaxonomyMappingResult.Mapped
        // Model label remains separate from taxonomy key
        assertEquals("model_sports_variant", mapped.modelLabel)
        assertEquals("sports", mapped.feedSenseTaxonomyKey)
    }

    // --------------------------------
    // FULL CHAIN: SERIALIZATION
    // --------------------------------

    @Test
    fun `mapping results are serializable`() {
        val modelResult = ModelInferenceResult(
            modelId = fixtures.MODEL_ARTIFACT_ID,
            modelVersion = fixtures.MODEL_ARTIFACT_VERSION,
            status = InferenceStatus.SUCCESS,
            rankedPredictions = listOf(
                RankedPrediction("sports", 0.9),
                RankedPrediction("completely_unknown", 0.1)
            )
        )

        val mappingResults = TaxonomyMappingEngine.mapPredictions(
            inferenceResult = modelResult,
            mappings = fixtures.ALL_MAPPINGS,
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )

        for (result in mappingResults) {
            val json = TaxonomyMappingSerializer.toJsonText(result)
            assertTrue(json.startsWith("{"))
            assertTrue(json.endsWith("}"))
            assertTrue(json.contains("modelLabel"))
            assertTrue(json.contains("resultType"))
        }
    }

    // --------------------------------
    // FULL CHAIN: VALIDATION
    // --------------------------------

    @Test
    fun `mapping table passes validation before use`() {
        val issues = TaxonomyMappingValidator.validate(
            mappings = fixtures.ALL_MAPPINGS,
            expectedArtifactId = fixtures.MODEL_ARTIFACT_ID,
            expectedTaxonomyVersion = fixtures.TAXONOMY_VERSION
        )
        assertTrue(
            "Golden mapping table should be valid, " +
                "but found: ${issues.map { it.code }}",
            issues.isEmpty()
        )
    }

    // --------------------------------
    // FULL CHAIN: TAXONOMY REUSED
    // --------------------------------

    @Test
    fun `FeedSense taxonomy is reused, not duplicated`() {
        val computedVersion =
            TaxonomyMappingEngine.computeTaxonomyVersion()
        val expectedVersion =
            TaxonomyMappingVersion.computeTaxonomyVersion(
                frozenVersion = SchemaFreeze.FREEZE_VERSION,
                sortedCategoryKeys = CategoryCatalog.keys.sorted()
            )
        assertEquals(expectedVersion, computedVersion)
    }

    // --------------------------------
    // FULL CHAIN: IMPLICIT DIRECT
    // --------------------------------

    @Test
    fun `model label matching taxonomy key maps via normalization`() {
        val modelResult = ModelInferenceResult(
            modelId = fixtures.MODEL_ARTIFACT_ID,
            modelVersion = fixtures.MODEL_ARTIFACT_VERSION,
            status = InferenceStatus.SUCCESS,
            rankedPredictions = listOf(
                RankedPrediction("meme", 0.7),
                RankedPrediction("cricket", 0.2)
            )
        )

        val mappingResults = TaxonomyMappingEngine.mapPredictions(
            inferenceResult = modelResult,
            mappings = emptyList(),
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )

        assertEquals(2, mappingResults.size)

        val meme = mappingResults[0] as TaxonomyMappingResult.Mapped
        assertEquals("meme", meme.feedSenseTaxonomyKey)
        assertEquals(MappingStatus.DIRECT, meme.status)

        val cricket =
            mappingResults[1] as TaxonomyMappingResult.Mapped
        assertEquals("cricket", cricket.feedSenseTaxonomyKey)
        assertEquals(MappingStatus.DIRECT, cricket.status)
    }

    // --------------------------------
    // NO PRODUCTION SIDE EFFECTS
    // --------------------------------

    @Test
    fun `integration test does not modify baseline types`() {
        val modelResult = ModelInferenceResult(
            modelId = "test",
            modelVersion = "v1",
            status = InferenceStatus.SUCCESS,
            rankedPredictions = listOf(
                RankedPrediction("sports", 0.9)
            )
        )

        val results = TaxonomyMappingEngine.mapPredictions(
            inferenceResult = modelResult,
            mappings = listOf(fixtures.DIRECT_SPORTS),
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )

        assertEquals(1, results.size)
        assertTrue(results[0] is TaxonomyMappingResult.Mapped)
    }
}
