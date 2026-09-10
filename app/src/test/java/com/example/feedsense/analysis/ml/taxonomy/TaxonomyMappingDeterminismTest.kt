package com.example.feedsense.analysis.ml.taxonomy

import com.example.feedsense.analysis.ml.RankedPrediction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-7.
 *
 * Determinism tests for taxonomy mapping (section 16):
 *   - Repeated mapping gives identical result
 *   - Mapping serialization is stable
 *   - Mapping hash is stable
 *   - Different versions produce distinguishable identities
 */
class TaxonomyMappingDeterminismTest {

    private val fixtures get() = TaxonomyMappingFixtures

    // --------------------------------
    // REPEATED MAPPING
    // --------------------------------

    @Test
    fun `repeated mapping of same input gives identical results`() {
        val predictions = listOf(
            RankedPrediction("sports", 0.9),
            RankedPrediction("entertainment", 0.05)
        )
        val mappings = listOf(
            fixtures.DIRECT_SPORTS,
            fixtures.DIRECT_ENTERTAINMENT
        )

        val results1 = TaxonomyMappingEngine.mapPredictions(
            predictions = predictions,
            mappings = mappings,
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )
        val results2 = TaxonomyMappingEngine.mapPredictions(
            predictions = predictions,
            mappings = mappings,
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )

        assertEquals(results1.size, results2.size)
        for (i in results1.indices) {
            assertEquals(results1[i], results2[i])
        }
    }

    // --------------------------------
    // SERIALIZATION STABILITY
    // --------------------------------

    @Test
    fun `serialization of mapping results is stable across runs`() {
        val result = TaxonomyMappingResult.Mapped(
            modelLabel = "sports",
            modelLabelIndex = 0,
            feedSenseTaxonomyKey = "sports",
            feedSenseTaxonomyDisplayName = "Sports",
            status = MappingStatus.DIRECT,
            rationale = MappingRationale.SEMANTIC_EQUIVALENCE,
            provenance = MappingProvenance.PROJECT_DEFINED,
            mappingId = "test",
            mappingTableVersion = "v1",
            taxonomyVersion = "taxonomy-v1"
        )

        val text1 = TaxonomyMappingSerializer.toJsonText(result)
        val text2 = TaxonomyMappingSerializer.toJsonText(result)
        assertEquals(text1, text2)

        // The text must contain sorted keys
        val keys = text1
            .trim()
            .removePrefix("{")
            .removeSuffix("}")
            .split(",")
            .map {
                it.substringBefore(":")
                    .trim()
                    .removeSurrounding("\"")
            }
        assertEquals(keys, keys.sorted())
    }

    // --------------------------------
    // VERSION STABILITY
    // --------------------------------

    @Test
    fun `taxonomy version is stable across calls`() {
        val v1 = TaxonomyMappingEngine.computeTaxonomyVersion()
        val v2 = TaxonomyMappingEngine.computeTaxonomyVersion()
        assertEquals(v1, v2)
    }

    @Test
    fun `taxonomy version contains frozen version`() {
        val version = TaxonomyMappingEngine.computeTaxonomyVersion()
        assertTrue(version.contains("taxonomy:"))
    }

    // --------------------------------
    // MAPPING VERSION IDENTITY
    // --------------------------------

    @Test
    fun `same mapping version produces same key`() {
        val v1 = fixtures.MAPPING_VERSION
        val v2 = fixtures.MAPPING_VERSION.copy()
        assertEquals(v1.key, v2.key)
    }

    @Test
    fun `different mapping versions produce different keys`() {
        val v1 = fixtures.MAPPING_VERSION
        val v2 = fixtures.MAPPING_VERSION.copy(
            mappingTableVersion = "mapping-v2"
        )
        assertNotEquals(v1.key, v2.key)
    }

    // --------------------------------
    // DETERMINISTIC ORDERING
    // --------------------------------

    @Test
    fun `mapping results preserve input prediction order`() {
        val predictions = listOf(
            RankedPrediction("education", 0.7),
            RankedPrediction("sports", 0.2),
            RankedPrediction("entertainment", 0.1)
        )
        val mappings = listOf(
            fixtures.DIRECT_SPORTS,
            fixtures.DIRECT_ENTERTAINMENT,
            fixtures.DIRECT_EDUCATION
        )

        val results = TaxonomyMappingEngine.mapPredictions(
            predictions = predictions,
            mappings = mappings,
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )

        assertEquals("education", results[0].modelLabel)
        assertEquals("sports", results[1].modelLabel)
        assertEquals("entertainment", results[2].modelLabel)
    }

    // --------------------------------
    // MAPPING ENTRY KEY STABILITY
    // --------------------------------

    @Test
    fun `mapping entry key is deterministic`() {
        val key1 = fixtures.DIRECT_SPORTS.key
        val key2 = fixtures.DIRECT_SPORTS.copy().key
        assertEquals(key1, key2)
    }

    @Test
    fun `different labels produce different keys`() {
        val key1 = fixtures.DIRECT_SPORTS.key
        val key2 = fixtures.DIRECT_ENTERTAINMENT.key
        assertNotEquals(key1, key2)
    }
}
