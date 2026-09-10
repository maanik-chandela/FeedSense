package com.example.feedsense.analysis.ml.taxonomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-7.
 *
 * Tests for TaxonomyMappingResult: sealed class hierarchy,
 * data integrity, and construction rules.
 */
class TaxonomyMappingResultTest {

    private val fixtures get() = TaxonomyMappingFixtures

    // --------------------------------
    // MAPPED RESULT
    // --------------------------------

    @Test
    fun `mapped result contains all required fields`() {
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

        assertEquals("sports", result.modelLabel)
        assertEquals(0, result.modelLabelIndex)
        assertEquals("sports", result.feedSenseTaxonomyKey)
        assertEquals("Sports", result.feedSenseTaxonomyDisplayName)
        assertEquals(MappingStatus.DIRECT, result.status)
        assertEquals(
            MappingRationale.SEMANTIC_EQUIVALENCE,
            result.rationale
        )
        assertEquals(
            MappingProvenance.PROJECT_DEFINED,
            result.provenance
        )
        assertEquals("test-mapping", result.mappingId)
        assertEquals("v1", result.mappingTableVersion)
        assertEquals("taxonomy-v1", result.taxonomyVersion)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mapped result rejects blank taxonomy key`() {
        TaxonomyMappingResult.Mapped(
            modelLabel = "sports",
            modelLabelIndex = 0,
            feedSenseTaxonomyKey = "",
            feedSenseTaxonomyDisplayName = "Sports",
            status = MappingStatus.DIRECT,
            mappingId = "test",
            mappingTableVersion = "v1",
            taxonomyVersion = "v1"
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mapped result rejects UNSUPPORTED status`() {
        TaxonomyMappingResult.Mapped(
            modelLabel = "sports",
            modelLabelIndex = 0,
            feedSenseTaxonomyKey = "sports",
            feedSenseTaxonomyDisplayName = "Sports",
            status = MappingStatus.UNSUPPORTED,
            mappingId = "test",
            mappingTableVersion = "v1",
            taxonomyVersion = "v1"
        )
    }

    // --------------------------------
    // UNMAPPED RESULT
    // --------------------------------

    @Test
    fun `unmapped result contains reason`() {
        val result = TaxonomyMappingResult.Unmapped(
            modelLabel = "weather",
            modelLabelIndex = 3,
            reason = "No FeedSense equivalent",
            mappingId = "unmapped-1",
            mappingTableVersion = "v1",
            taxonomyVersion = "taxonomy-v1"
        )

        assertEquals("weather", result.modelLabel)
        assertEquals(3, result.modelLabelIndex)
        assertEquals("No FeedSense equivalent", result.reason)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unmapped result rejects blank reason`() {
        TaxonomyMappingResult.Unmapped(
            modelLabel = "weather",
            modelLabelIndex = 0,
            reason = "",
            mappingId = "test",
            mappingTableVersion = "v1",
            taxonomyVersion = "v1"
        )
    }

    // --------------------------------
    // AMBIGUOUS RESULT
    // --------------------------------

    @Test
    fun `ambiguous result contains candidates`() {
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

        assertTrue(result.candidateTaxonomyKeys.size >= 1)
        assertTrue(result.candidateTaxonomyKeys.contains("lifestyle"))
        assertTrue(
            result.candidateTaxonomyKeys.contains("entertainment")
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `ambiguous result requires at least 1 candidate`() {
        TaxonomyMappingResult.Ambiguous(
            modelLabel = "test",
            modelLabelIndex = 0,
            candidateTaxonomyKeys = emptyList(),
            ambiguityReason = "test",
            mappingId = "test",
            mappingTableVersion = "v1",
            taxonomyVersion = "v1"
        )
    }

    // --------------------------------
    // INVALID RESULT
    // --------------------------------

    @Test
    fun `invalid result contains failure code`() {
        val result = TaxonomyMappingResult.Invalid(
            modelLabel = "bad_label",
            modelLabelIndex = 7,
            failureReason = "Invalid taxonomy key",
            failureCode = MappingFailureCode.INVALID_TAXONOMY_KEY,
            mappingId = "inv-1",
            mappingTableVersion = "v1",
            taxonomyVersion = "taxonomy-v1"
        )

        assertEquals(
            MappingFailureCode.INVALID_TAXONOMY_KEY,
            result.failureCode
        )
        assertEquals("Invalid taxonomy key", result.failureReason)
    }

    // --------------------------------
    // EQUALITY
    // --------------------------------

    @Test
    fun `mapped results with same data are equal`() {
        val a = TaxonomyMappingResult.Mapped(
            modelLabel = "sports",
            modelLabelIndex = 0,
            feedSenseTaxonomyKey = "sports",
            feedSenseTaxonomyDisplayName = "Sports",
            status = MappingStatus.DIRECT,
            mappingId = "test",
            mappingTableVersion = "v1",
            taxonomyVersion = "taxonomy-v1"
        )
        val b = a.copy()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }
}
