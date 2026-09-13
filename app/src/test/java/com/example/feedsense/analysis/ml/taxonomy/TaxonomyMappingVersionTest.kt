package com.example.feedsense.analysis.ml.taxonomy

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.analysis.SchemaFreeze
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-7.
 *
 * Tests for TaxonomyMappingVersion: versioning, identity,
 * and taxonomy version computation.
 */
class TaxonomyMappingVersionTest {

    // --------------------------------
    // VERSION IDENTITY
    // --------------------------------

    @Test
    fun `version key is composite of all identity fields`() {
        val version = TaxonomyMappingVersion(
            mappingTableVersion = "map-v1",
            taxonomyVersion = "tax-v1",
            modelArtifactId = "model-a",
            modelArtifactVersion = "v1",
            labelCount = 10,
            taxonomyKeyCount = 50
        )

        val key = version.key
        assertTrue(key.contains("map-v1"))
        assertTrue(key.contains("tax-v1"))
        assertTrue(key.contains("model-a"))
        assertTrue(key.contains("v1"))
    }

    @Test
    fun `same version data produces same key`() {
        val v1 = TaxonomyMappingVersion(
            mappingTableVersion = "map-v1",
            taxonomyVersion = "tax-v1",
            modelArtifactId = "model-a",
            modelArtifactVersion = "v1",
            labelCount = 10,
            taxonomyKeyCount = 50
        )
        val v2 = v1.copy()
        assertEquals(v1.key, v2.key)
    }

    @Test
    fun `different data produces different key`() {
        val v1 = TaxonomyMappingVersion(
            mappingTableVersion = "map-v1",
            taxonomyVersion = "tax-v1",
            modelArtifactId = "model-a",
            modelArtifactVersion = "v1",
            labelCount = 10,
            taxonomyKeyCount = 50
        )
        val v2 = v1.copy(mappingTableVersion = "map-v2")
        assertNotEquals(v1.key, v2.key)
    }

    // --------------------------------
    // TAXONOMY VERSION COMPUTATION
    // --------------------------------

    @Test
    fun `taxonomy version includes frozen version`() {
        val version = TaxonomyMappingVersion.computeTaxonomyVersion(
            frozenVersion = "1.0.0",
            sortedCategoryKeys = CategoryCatalog.keys.sorted()
        )
        assertTrue(version.contains("taxonomy:1.0.0:"))
    }

    @Test
    fun `taxonomy version includes key count`() {
        val keys = CategoryCatalog.keys.sorted()
        val version = TaxonomyMappingVersion.computeTaxonomyVersion(
            frozenVersion = "1.0.0",
            sortedCategoryKeys = keys
        )
        assertTrue(version.contains(":${keys.size}:"))
    }

    @Test
    fun `taxonomy version is deterministic`() {
        val keys = CategoryCatalog.keys.sorted()
        val v1 = TaxonomyMappingVersion.computeTaxonomyVersion(
            frozenVersion = "1.0.0",
            sortedCategoryKeys = keys
        )
        val v2 = TaxonomyMappingVersion.computeTaxonomyVersion(
            frozenVersion = "1.0.0",
            sortedCategoryKeys = keys
        )
        assertEquals(v1, v2)
    }

    @Test
    fun `different frozen versions produce different taxonomy versions`() {
        val keys = CategoryCatalog.keys.sorted()
        val v1 = TaxonomyMappingVersion.computeTaxonomyVersion(
            frozenVersion = "1.0.0",
            sortedCategoryKeys = keys
        )
        val v2 = TaxonomyMappingVersion.computeTaxonomyVersion(
            frozenVersion = "2.0.0",
            sortedCategoryKeys = keys
        )
        assertNotEquals(v1, v2)
    }

    @Test
    fun `different key sets produce different taxonomy versions`() {
        val v1 = TaxonomyMappingVersion.computeTaxonomyVersion(
            frozenVersion = "1.0.0",
            sortedCategoryKeys = listOf("a", "b", "c")
        )
        val v2 = TaxonomyMappingVersion.computeTaxonomyVersion(
            frozenVersion = "1.0.0",
            sortedCategoryKeys = listOf("a", "b", "d")
        )
        assertNotEquals(v1, v2)
    }

    // --------------------------------
    // VALIDATION
    // --------------------------------

    @Test(expected = IllegalArgumentException::class)
    fun `blank mappingTableVersion is rejected`() {
        TaxonomyMappingVersion(
            mappingTableVersion = "",
            taxonomyVersion = "v1",
            modelArtifactId = "model",
            modelArtifactVersion = "v1",
            labelCount = 0,
            taxonomyKeyCount = 0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank taxonomyVersion is rejected`() {
        TaxonomyMappingVersion(
            mappingTableVersion = "v1",
            taxonomyVersion = "",
            modelArtifactId = "model",
            modelArtifactVersion = "v1",
            labelCount = 0,
            taxonomyKeyCount = 0
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative labelCount is rejected`() {
        TaxonomyMappingVersion(
            mappingTableVersion = "v1",
            taxonomyVersion = "v1",
            modelArtifactId = "model",
            modelArtifactVersion = "v1",
            labelCount = -1,
            taxonomyKeyCount = 0
        )
    }
}
