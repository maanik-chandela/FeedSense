package com.example.feedsense.analysis.ml.taxonomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-7.
 *
 * Tests for TaxonomyMappingSerializer: deterministic,
 * privacy-safe serialization of mapping results.
 *
 * The same mapping result must serialize to identical text.
 */
class TaxonomyMappingSerializerTest {

    private val fixtures get() = TaxonomyMappingFixtures

    // --------------------------------
    // DETERMINISTIC SERIALIZATION
    // --------------------------------

    @Test
    fun `identical mapped results serialize to identical json`() {
        val result = TaxonomyMappingResult.Mapped(
            modelLabel = "sports",
            modelLabelIndex = 0,
            feedSenseTaxonomyKey = "sports",
            feedSenseTaxonomyDisplayName = "Sports",
            status = MappingStatus.DIRECT,
            rationale = MappingRationale.SEMANTIC_EQUIVALENCE,
            provenance = MappingProvenance.PROJECT_DEFINED,
            mappingId = "test-mapping",
            mappingTableVersion = "v1",
            taxonomyVersion = "taxonomy-v1"
        )
        val text1 = TaxonomyMappingSerializer.toJsonText(result)
        val text2 = TaxonomyMappingSerializer.toJsonText(result)
        assertEquals(text1, text2)
    }

    @Test
    fun `identical unmapped results serialize to identical json`() {
        val result = TaxonomyMappingResult.Unmapped(
            modelLabel = "weather",
            modelLabelIndex = 3,
            reason = "No equivalent",
            mappingId = "unmapped-1",
            mappingTableVersion = "v1",
            taxonomyVersion = "taxonomy-v1"
        )
        val text1 = TaxonomyMappingSerializer.toJsonText(result)
        val text2 = TaxonomyMappingSerializer.toJsonText(result)
        assertEquals(text1, text2)
    }

    // --------------------------------
    // JSON STRUCTURE
    // --------------------------------

    @Test
    fun `mapped json contains all required fields`() {
        val result = TaxonomyMappingResult.Mapped(
            modelLabel = "sports",
            modelLabelIndex = 0,
            feedSenseTaxonomyKey = "sports",
            feedSenseTaxonomyDisplayName = "Sports",
            status = MappingStatus.DIRECT,
            mappingId = "test-mapping",
            mappingTableVersion = "v1",
            taxonomyVersion = "taxonomy-v1"
        )
        val text = TaxonomyMappingSerializer.toJsonText(result)

        assertTrue(text.startsWith("{"))
        assertTrue(text.endsWith("}"))
        assertTrue(text.contains("\"resultType\":\"MAPPED\""))
        assertTrue(text.contains("\"modelLabel\":\"sports\""))
        assertTrue(text.contains("\"feedSenseTaxonomyKey\":\"sports\""))
        assertTrue(text.contains("\"status\":\"DIRECT\""))
    }

    @Test
    fun `unmapped json contains reason`() {
        val result = TaxonomyMappingResult.Unmapped(
            modelLabel = "weather",
            modelLabelIndex = 3,
            reason = "No equivalent",
            mappingId = "unmapped-1",
            mappingTableVersion = "v1",
            taxonomyVersion = "taxonomy-v1"
        )
        val text = TaxonomyMappingSerializer.toJsonText(result)

        assertTrue(text.contains("\"resultType\":\"UNMAPPED\""))
        assertTrue(text.contains("\"reason\":\"No equivalent\""))
    }

    @Test
    fun `ambiguous json contains candidates`() {
        val result = TaxonomyMappingResult.Ambiguous(
            modelLabel = "daily_vlog",
            modelLabelIndex = 5,
            candidateTaxonomyKeys = listOf(
                "lifestyle", "entertainment"
            ),
            ambiguityReason = "Could map to either",
            mappingId = "amb-1",
            mappingTableVersion = "v1",
            taxonomyVersion = "taxonomy-v1"
        )
        val text = TaxonomyMappingSerializer.toJsonText(result)

        assertTrue(text.contains("\"resultType\":\"AMBIGUOUS\""))
        assertTrue(
            text.contains("\"candidateTaxonomyKeys\"")
        )
        assertTrue(text.contains("lifestyle"))
        assertTrue(text.contains("entertainment"))
    }

    @Test
    fun `invalid json contains failure code`() {
        val result = TaxonomyMappingResult.Invalid(
            modelLabel = "bad",
            modelLabelIndex = 0,
            failureReason = "Invalid key",
            failureCode = MappingFailureCode.INVALID_TAXONOMY_KEY,
            mappingId = "inv-1",
            mappingTableVersion = "v1",
            taxonomyVersion = "taxonomy-v1"
        )
        val text = TaxonomyMappingSerializer.toJsonText(result)

        assertTrue(text.contains("\"resultType\":\"INVALID\""))
        assertTrue(
            text.contains(
                "\"failureCode\":\"INVALID_TAXONOMY_KEY\""
            )
        )
    }

    // --------------------------------
    // VERSION SERIALIZATION
    // --------------------------------

    @Test
    fun `version serialization is deterministic`() {
        val v1 = TaxonomyMappingSerializer.versionToJsonText(
            fixtures.MAPPING_VERSION
        )
        val v2 = TaxonomyMappingSerializer.versionToJsonText(
            fixtures.MAPPING_VERSION
        )
        assertEquals(v1, v2)
    }

    @Test
    fun `version json contains all fields`() {
        val text = TaxonomyMappingSerializer.versionToJsonText(
            fixtures.MAPPING_VERSION
        )
        assertTrue(text.contains("\"mappingTableVersion\""))
        assertTrue(text.contains("\"taxonomyVersion\""))
        assertTrue(text.contains("\"modelArtifactId\""))
        assertTrue(text.contains("\"labelCount\""))
        assertTrue(text.contains("\"taxonomyKeyCount\""))
    }

    // --------------------------------
    // METADATA MAP
    // --------------------------------

    @Test
    fun `metadata map is deterministic`() {
        val result = TaxonomyMappingResult.Mapped(
            modelLabel = "sports",
            modelLabelIndex = 0,
            feedSenseTaxonomyKey = "sports",
            feedSenseTaxonomyDisplayName = "Sports",
            status = MappingStatus.DIRECT,
            mappingId = "test",
            mappingTableVersion = "v1",
            taxonomyVersion = "taxonomy-v1"
        )
        val map1 = TaxonomyMappingSerializer.toMetadataMap(result)
        val map2 = TaxonomyMappingSerializer.toMetadataMap(result)
        assertEquals(map1, map2)
    }

    @Test
    fun `metadata map never contains sensitive keys`() {
        val result = TaxonomyMappingResult.Mapped(
            modelLabel = "sports",
            modelLabelIndex = 0,
            feedSenseTaxonomyKey = "sports",
            feedSenseTaxonomyDisplayName = "Sports",
            status = MappingStatus.DIRECT,
            mappingId = "test",
            mappingTableVersion = "v1",
            taxonomyVersion = "taxonomy-v1"
        )
        val map = TaxonomyMappingSerializer.toMetadataMap(result)
        val forbidden = listOf(
            "pixels", "rawPixels", "frame", "ocrText",
            "privateContent", "screenshot", "imageBytes"
        )
        for (key in forbidden) {
            assertFalse(
                "forbidden key present: $key",
                map.containsKey(key)
            )
        }
    }

    // --------------------------------
    // ESCAPING
    // --------------------------------

    @Test
    fun `special characters in reason are escaped`() {
        val result = TaxonomyMappingResult.Unmapped(
            modelLabel = "test",
            modelLabelIndex = 0,
            reason = "Has \"quotes\" and\\backslash",
            mappingId = "test",
            mappingTableVersion = "v1",
            taxonomyVersion = "taxonomy-v1"
        )
        val text = TaxonomyMappingSerializer.toJsonText(result)
        assertTrue(text.contains("\\\"quotes\\\""))
        assertTrue(text.contains("\\\\backslash"))
    }
}
