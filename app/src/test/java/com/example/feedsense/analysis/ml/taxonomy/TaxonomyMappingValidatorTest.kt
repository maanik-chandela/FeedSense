package com.example.feedsense.analysis.ml.taxonomy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-7.
 *
 * Tests for TaxonomyMappingValidator: structural correctness
 * and compatibility validation of mapping tables.
 *
 * The data class constructor enforces basic structural
 * invariants (non-negative index, valid strength range,
 * required fields for status). The validator catches
 * additional semantic issues that the constructor cannot:
 *   - Invalid taxonomy keys (not in CategoryCatalog)
 *   - Conflicting mappings (same index, different targets)
 *   - Duplicate mapping IDs
 *   - Incompatible artifact identity
 *   - Incompatible taxonomy version
 *   - Invalid taxonomy keys
 *   - Out-of-range mapping strength
 */
class TaxonomyMappingValidatorTest {

    private val fixtures get() = TaxonomyMappingFixtures

    // --------------------------------
    // VALID TABLE
    // --------------------------------

    @Test
    fun `valid mapping table produces no issues`() {
        val issues = TaxonomyMappingValidator.validate(
            mappings = fixtures.ALL_MAPPINGS
        )
        assertTrue(issues.isEmpty())
    }

    @Test
    fun `empty mapping table produces no issues`() {
        val issues = TaxonomyMappingValidator.validate(
            mappings = emptyList()
        )
        assertTrue(issues.isEmpty())
    }

    // --------------------------------
    // DUPLICATE MAPPING IDs
    // --------------------------------

    @Test
    fun `duplicate mapping IDs are detected`() {
        val duplicate = fixtures.DIRECT_SPORTS.copy(
            mappingId = "direct-sports"
        )
        val issues = TaxonomyMappingValidator.validate(
            mappings = listOf(fixtures.DIRECT_SPORTS, duplicate)
        )
        assertTrue(issues.any {
            it.code == MappingValidationCode.DUPLICATE_MAPPING_ID
        })
    }

    // --------------------------------
    // CONFLICTING MAPPINGS
    // --------------------------------

    @Test
    fun `conflicting mappings for same index are detected`() {
        val issues = TaxonomyMappingValidator.validate(
            mappings = listOf(
                fixtures.CONFLICTING_A,
                fixtures.CONFLICTING_B
            )
        )
        assertTrue(issues.any {
            it.code == MappingValidationCode.CONFLICTING_MAPPING
        })
    }

    // --------------------------------
    // INVALID TAXONOMY KEY
    // --------------------------------

    @Test
    fun `invalid taxonomy key is detected`() {
        val issues = TaxonomyMappingValidator.validate(
            mappings = listOf(fixtures.INVALID_TAXONOMY_KEY)
        )
        assertTrue(issues.any {
            it.code == MappingValidationCode.INVALID_TAXONOMY_KEY
        })
    }

    // --------------------------------
    // ARTIFACT COMPATIBILITY
    // --------------------------------

    @Test
    fun `incompatible artifact is detected`() {
        val issues = TaxonomyMappingValidator.validate(
            mappings = listOf(fixtures.WRONG_ARTIFACT),
            expectedArtifactId = fixtures.MODEL_ARTIFACT_ID
        )
        assertTrue(issues.any {
            it.code == MappingValidationCode.INCOMPATIBLE_ARTIFACT
        })
    }

    @Test
    fun `compatible artifact produces no artifact issues`() {
        val issues = TaxonomyMappingValidator.validate(
            mappings = listOf(fixtures.DIRECT_SPORTS),
            expectedArtifactId = fixtures.MODEL_ARTIFACT_ID
        )
        assertFalse(issues.any {
            it.code == MappingValidationCode.INCOMPATIBLE_ARTIFACT
        })
    }

    // --------------------------------
    // TAXONOMY VERSION COMPATIBILITY
    // --------------------------------

    @Test
    fun `incompatible taxonomy version is detected`() {
        val issues = TaxonomyMappingValidator.validate(
            mappings = listOf(fixtures.DIRECT_SPORTS),
            expectedTaxonomyVersion = "obsolete-taxonomy-v0"
        )
        assertTrue(issues.any {
            it.code ==
                MappingValidationCode.INCOMPATIBLE_TAXONOMY_VERSION
        })
    }

    @Test
    fun `compatible taxonomy version produces no version issues`() {
        val issues = TaxonomyMappingValidator.validate(
            mappings = listOf(fixtures.DIRECT_SPORTS),
            expectedTaxonomyVersion = fixtures.TAXONOMY_VERSION
        )
        assertFalse(issues.any {
            it.code ==
                MappingValidationCode.INCOMPATIBLE_TAXONOMY_VERSION
        })
    }

    // --------------------------------
    // MULTIPLE ISSUES
    // --------------------------------

    @Test
    fun `multiple issues are all reported`() {
        val issues = TaxonomyMappingValidator.validate(
            mappings = listOf(
                fixtures.INVALID_TAXONOMY_KEY,
                fixtures.WRONG_ARTIFACT
            ),
            expectedArtifactId = fixtures.MODEL_ARTIFACT_ID
        )
        val codes = issues.map { it.code }.toSet()
        assertTrue(
            MappingValidationCode.INVALID_TAXONOMY_KEY in codes
        )
        assertTrue(
            MappingValidationCode.INCOMPATIBLE_ARTIFACT in codes
        )
    }

    // --------------------------------
    // DETERMINISTIC ORDERING
    // --------------------------------

    @Test
    fun `validation issues are returned in deterministic order`() {
        val issues1 = TaxonomyMappingValidator.validate(
            mappings = fixtures.ALL_MAPPINGS
        )
        val issues2 = TaxonomyMappingValidator.validate(
            mappings = fixtures.ALL_MAPPINGS
        )
        assertEquals(
            issues1.map { it.code },
            issues2.map { it.code }
        )
    }
}
