package com.example.feedsense.analysis.ml.taxonomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-7.
 *
 * Tests for ModelTaxonomyMapping: construction rules,
 * validation, and key generation.
 */
class ModelTaxonomyMappingTest {

    private val fixtures get() = TaxonomyMappingFixtures

    // --------------------------------
    // CONSTRUCTION
    // --------------------------------

    @Test
    fun `direct mapping has all required fields`() {
        val mapping = fixtures.DIRECT_SPORTS
        assertEquals("direct-sports", mapping.mappingId)
        assertEquals(0, mapping.modelLabelIndex)
        assertEquals("sports", mapping.modelLabel)
        assertEquals("sports", mapping.feedSenseTaxonomyKey)
        assertEquals(MappingStatus.DIRECT, mapping.status)
        assertEquals(
            MappingRationale.SEMANTIC_EQUIVALENCE,
            mapping.rationale
        )
    }

    @Test
    fun `mapped entry has correct key`() {
        val key = fixtures.DIRECT_SPORTS.key
        assertEquals(
            "${fixtures.MODEL_ARTIFACT_ID}:0:sports",
            key
        )
    }

    @Test
    fun `hasTarget is true for mapped entries`() {
        assertTrue(fixtures.DIRECT_SPORTS.hasTarget)
        assertTrue(fixtures.MANY_TO_ONE_MODEL_A.hasTarget)
    }

    @Test
    fun `hasTarget is false for unmapped entries`() {
        assertFalse(fixtures.UNMAPPED_LABEL.hasTarget)
        assertFalse(fixtures.UNSUPPORTED_LABEL.hasTarget)
    }

    // --------------------------------
    // STATUS CONSISTENCY
    // --------------------------------

    @Test(expected = IllegalArgumentException::class)
    fun `DIRECT status without taxonomy key is rejected`() {
        ModelTaxonomyMapping(
            mappingId = "test",
            modelArtifactId = "model",
            modelArtifactVersion = "v1",
            modelLabelIndex = 0,
            modelLabel = "label",
            feedSenseTaxonomyKey = null,
            status = MappingStatus.DIRECT,
            mappingTableVersion = "v1",
            taxonomyVersion = "v1"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `MAPPED status without taxonomy key is rejected`() {
        ModelTaxonomyMapping(
            mappingId = "test",
            modelArtifactId = "model",
            modelArtifactVersion = "v1",
            modelLabelIndex = 0,
            modelLabel = "label",
            feedSenseTaxonomyKey = null,
            status = MappingStatus.MAPPED,
            rationale = MappingRationale.SEMANTIC_EQUIVALENCE,
            mappingTableVersion = "v1",
            taxonomyVersion = "v1"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `UNMAPPED status with taxonomy key is rejected`() {
        ModelTaxonomyMapping(
            mappingId = "test",
            modelArtifactId = "model",
            modelArtifactVersion = "v1",
            modelLabelIndex = 0,
            modelLabel = "label",
            feedSenseTaxonomyKey = "sports",
            status = MappingStatus.UNMAPPED,
            mappingTableVersion = "v1",
            taxonomyVersion = "v1"
        )
    }

    // --------------------------------
    // RATIONALE CONSISTENCY
    // --------------------------------

    @Test(expected = IllegalArgumentException::class)
    fun `MAPPED status without rationale is rejected`() {
        ModelTaxonomyMapping(
            mappingId = "test",
            modelArtifactId = "model",
            modelArtifactVersion = "v1",
            modelLabelIndex = 0,
            modelLabel = "label",
            feedSenseTaxonomyKey = "sports",
            status = MappingStatus.MAPPED,
            rationale = null,
            mappingTableVersion = "v1",
            taxonomyVersion = "v1"
        )
    }

    // --------------------------------
    // MAPPING STRENGTH
    // --------------------------------

    @Test
    fun `mapping strength within range is accepted`() {
        val mapping = fixtures.DIRECT_SPORTS.copy(
            mappingStrength = 0.75
        )
        assertEquals(0.75, mapping.mappingStrength!!, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mapping strength above max is rejected`() {
        fixtures.DIRECT_SPORTS.copy(mappingStrength = 1.1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mapping strength below min is rejected`() {
        fixtures.DIRECT_SPORTS.copy(mappingStrength = -0.1)
    }

    // --------------------------------
    // EQUALITY
    // --------------------------------

    @Test
    fun `same mapping data produces equal entries`() {
        val a = fixtures.DIRECT_SPORTS
        val b = fixtures.DIRECT_SPORTS.copy()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `different labels produce different entries`() {
        assertNotEquals(
            fixtures.DIRECT_SPORTS,
            fixtures.DIRECT_ENTERTAINMENT
        )
    }
}
