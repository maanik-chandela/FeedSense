package com.example.feedsense.analysis.ml.evaluation

import com.example.feedsense.analysis.ml.taxonomy.MappingFailureCode
import com.example.feedsense.analysis.ml.taxonomy.MappingStatus
import com.example.feedsense.analysis.ml.taxonomy.TaxonomyMappingResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// --------------------------------
// EVALUATION ELIGIBILITY TEST (8B-15-8)
// --------------------------------
//
// Focused tests for the evaluation eligibility decision
// logic, covering all mapping states and edge cases.

class EvaluationEligibilityTest {

    private val fixtures = EvaluationBoundaryFixtures

    // --------------------------------
    // ELIGIBLE STATES
    // --------------------------------

    @Test
    fun `DIRECT mapping produces ELIGIBLE`() {
        val mapping = TaxonomyMappingResult.Mapped(
            modelLabel = "sports",
            modelLabelIndex = 0,
            feedSenseTaxonomyKey = "sports",
            feedSenseTaxonomyDisplayName = "Sports",
            status = MappingStatus.DIRECT,
            mappingId = "test-direct",
            mappingTableVersion = "v1",
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )
        val eligibility =
            EvaluationEligibility.fromMappingResult(mapping)
        assertEquals(EligibilityStatus.ELIGIBLE, eligibility.status)
        assertEquals(
            EvaluationDecisionReason.VALID_DIRECT_MAPPING,
            eligibility.reason
        )
        assertTrue(eligibility.isEligible)
    }

    @Test
    fun `MAPPED mapping produces ELIGIBLE`() {
        val mapping = TaxonomyMappingResult.Mapped(
            modelLabel = "model_athletics",
            modelLabelIndex = 0,
            feedSenseTaxonomyKey = "sports",
            feedSenseTaxonomyDisplayName = "Sports",
            status = MappingStatus.MAPPED,
            mappingStrength = 0.8,
            rationale = com.example.feedsense.analysis.ml.taxonomy
                .MappingRationale.BROADER_FEEDSENSE_CATEGORY,
            provenance = com.example.feedsense.analysis.ml.taxonomy
                .MappingProvenance.PROJECT_DEFINED,
            mappingId = "test-mapped",
            mappingTableVersion = "v1",
            taxonomyVersion = fixtures.TAXONOMY_VERSION,
            rationaleNotes = "Test mapping"
        )
        val eligibility =
            EvaluationEligibility.fromMappingResult(mapping)
        assertEquals(EligibilityStatus.ELIGIBLE, eligibility.status)
        assertEquals(
            EvaluationDecisionReason.VALID_MAPPED_MAPPING,
            eligibility.reason
        )
        assertTrue(eligibility.isEligible)
    }

    @Test
    fun `ELIGIBLE with taxonomy version check passes when versions match`() {
        val mapping = TaxonomyMappingResult.Mapped(
            modelLabel = "sports",
            modelLabelIndex = 0,
            feedSenseTaxonomyKey = "sports",
            feedSenseTaxonomyDisplayName = "Sports",
            status = MappingStatus.DIRECT,
            mappingId = "test-version-match",
            mappingTableVersion = "v1",
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )
        val eligibility = EvaluationEligibility.fromMappingResult(
            mappingResult = mapping,
            expectedTaxonomyVersion = fixtures.TAXONOMY_VERSION
        )
        assertEquals(EligibilityStatus.ELIGIBLE, eligibility.status)
        assertTrue(eligibility.isEligible)
    }

    // --------------------------------
    // NOT ELIGIBLE STATES
    // --------------------------------

    @Test
    fun `UNMAPPED result produces NOT_ELIGIBLE_UNMAPPED`() {
        val mapping = TaxonomyMappingResult.Unmapped(
            modelLabel = "weather",
            modelLabelIndex = 0,
            reason = "No equivalent",
            mappingId = "test-unmapped",
            mappingTableVersion = "v1",
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )
        val eligibility =
            EvaluationEligibility.fromMappingResult(mapping)
        assertEquals(
            EligibilityStatus.NOT_ELIGIBLE_UNMAPPED,
            eligibility.status
        )
        assertEquals(
            EvaluationDecisionReason.UNMAPPED_MODEL_LABEL,
            eligibility.reason
        )
        assertFalse(eligibility.isEligible)
    }

    @Test
    fun `AMBIGUOUS result produces NOT_ELIGIBLE_AMBIGUOUS`() {
        val mapping = TaxonomyMappingResult.Ambiguous(
            modelLabel = "daily_vlog",
            modelLabelIndex = 0,
            candidateTaxonomyKeys = listOf(
                "lifestyle",
                "entertainment"
            ),
            ambiguityReason = "Multiple candidates",
            mappingId = "test-ambiguous",
            mappingTableVersion = "v1",
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )
        val eligibility =
            EvaluationEligibility.fromMappingResult(mapping)
        assertEquals(
            EligibilityStatus.NOT_ELIGIBLE_AMBIGUOUS,
            eligibility.status
        )
        assertEquals(
            EvaluationDecisionReason.AMBIGUOUS_MAPPING,
            eligibility.reason
        )
    }

    @Test
    fun `REJECTED mapping produces NOT_ELIGIBLE_REJECTED`() {
        val mapping = TaxonomyMappingResult.Invalid(
            modelLabel = "political",
            modelLabelIndex = 0,
            failureReason = "Rejected: semantic mismatch",
            failureCode = MappingFailureCode.INCOMPATIBLE_MAPPING,
            mappingId = "test-rejected",
            mappingTableVersion = "v1",
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )
        val eligibility =
            EvaluationEligibility.fromMappingResult(mapping)
        assertEquals(
            EligibilityStatus.NOT_ELIGIBLE_REJECTED,
            eligibility.status
        )
        assertEquals(
            EvaluationDecisionReason.REJECTED_MAPPING,
            eligibility.reason
        )
    }

    @Test
    fun `INVALID mapping produces NOT_ELIGIBLE_INVALID`() {
        val mapping = TaxonomyMappingResult.Invalid(
            modelLabel = "fake",
            modelLabelIndex = 0,
            failureReason = "Invalid taxonomy key",
            failureCode = MappingFailureCode.INVALID_TAXONOMY_KEY,
            mappingId = "test-invalid",
            mappingTableVersion = "v1",
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )
        val eligibility =
            EvaluationEligibility.fromMappingResult(mapping)
        assertEquals(
            EligibilityStatus.NOT_ELIGIBLE_INVALID,
            eligibility.status
        )
        assertEquals(
            EvaluationDecisionReason.INVALID_MAPPING,
            eligibility.reason
        )
    }

    @Test
    fun `taxonomy version mismatch produces NOT_ELIGIBLE_VERSION_MISMATCH`() {
        val mapping = TaxonomyMappingResult.Mapped(
            modelLabel = "sports",
            modelLabelIndex = 0,
            feedSenseTaxonomyKey = "sports",
            feedSenseTaxonomyDisplayName = "Sports",
            status = MappingStatus.DIRECT,
            mappingId = "test-version-mismatch",
            mappingTableVersion = "v1",
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )
        val eligibility = EvaluationEligibility.fromMappingResult(
            mappingResult = mapping,
            expectedTaxonomyVersion = "taxonomy:wrong:0:abc"
        )
        assertEquals(
            EligibilityStatus.NOT_ELIGIBLE_VERSION_MISMATCH,
            eligibility.status
        )
        assertEquals(
            EvaluationDecisionReason.TAXONOMY_VERSION_MISMATCH,
            eligibility.reason
        )
    }

    // --------------------------------
    // EXPLICIT STATE METHODS
    // --------------------------------

    @Test
    fun `missingOutput produces correct status`() {
        val eligibility = EvaluationEligibility.missingOutput()
        assertEquals(
            EligibilityStatus.NOT_ELIGIBLE_MISSING_OUTPUT,
            eligibility.status
        )
        assertEquals(
            EvaluationDecisionReason.MISSING_PREDICTION,
            eligibility.reason
        )
    }

    @Test
    fun `missingTaxonomy produces correct status`() {
        val eligibility = EvaluationEligibility.missingTaxonomy()
        assertEquals(
            EligibilityStatus.NOT_ELIGIBLE_MISSING_TAXONOMY,
            eligibility.status
        )
        assertEquals(
            EvaluationDecisionReason.MISSING_TAXONOMY_VERSION,
            eligibility.reason
        )
    }

    @Test
    fun `missingMapping produces correct status`() {
        val eligibility = EvaluationEligibility.missingMapping()
        assertEquals(
            EligibilityStatus.NOT_ELIGIBLE_MISSING_MAPPING,
            eligibility.status
        )
        assertEquals(
            EvaluationDecisionReason.MISSING_MAPPING,
            eligibility.reason
        )
    }

    @Test
    fun `versionMismatch produces correct status`() {
        val eligibility = EvaluationEligibility.versionMismatch(
            expected = "taxonomy:v1:70:abc",
            actual = "taxonomy:v2:70:def"
        )
        assertEquals(
            EligibilityStatus.NOT_ELIGIBLE_VERSION_MISMATCH,
            eligibility.status
        )
        assertEquals(
            EvaluationDecisionReason.TAXONOMY_VERSION_MISMATCH,
            eligibility.reason
        )
    }

    // --------------------------------
    // INDEPENDENCE FROM CONFIDENCE
    // --------------------------------

    @Test
    fun `eligibility does not depend on prediction score`() {
        val mapping = TaxonomyMappingResult.Mapped(
            modelLabel = "sports",
            modelLabelIndex = 0,
            feedSenseTaxonomyKey = "sports",
            feedSenseTaxonomyDisplayName = "Sports",
            status = MappingStatus.DIRECT,
            mappingId = "test-conf-indep",
            mappingTableVersion = "v1",
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )

        val eligibility =
            EvaluationEligibility.fromMappingResult(mapping)
        assertEquals(EligibilityStatus.ELIGIBLE, eligibility.status)
        // The eligibility decision is purely structural;
        // prediction score is not a factor
        assertTrue(eligibility.isEligible)
    }

    @Test
    fun `unmapped status remains not eligible regardless of context`() {
        val mapping = TaxonomyMappingResult.Unmapped(
            modelLabel = "weather",
            modelLabelIndex = 0,
            reason = "No equivalent",
            mappingId = "test-unmapped-ctx",
            mappingTableVersion = "v1",
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )
        val eligibility =
            EvaluationEligibility.fromMappingResult(mapping)
        assertFalse(eligibility.isEligible)
        assertEquals(
            EligibilityStatus.NOT_ELIGIBLE_UNMAPPED,
            eligibility.status
        )
    }

    // --------------------------------
    // REASON DETAIL PRESERVATION
    // --------------------------------

    @Test
    fun `unmapped reason detail is preserved`() {
        val reason = "Weather has no FeedSense equivalent"
        val mapping = TaxonomyMappingResult.Unmapped(
            modelLabel = "weather",
            modelLabelIndex = 0,
            reason = reason,
            mappingId = "test-detail",
            mappingTableVersion = "v1",
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )
        val eligibility =
            EvaluationEligibility.fromMappingResult(mapping)
        assertEquals(reason, eligibility.reasonDetail)
    }

    @Test
    fun `ambiguous reason detail is preserved`() {
        val reason = "Could be lifestyle or entertainment"
        val mapping = TaxonomyMappingResult.Ambiguous(
            modelLabel = "daily_vlog",
            modelLabelIndex = 0,
            candidateTaxonomyKeys = listOf(
                "lifestyle",
                "entertainment"
            ),
            ambiguityReason = reason,
            mappingId = "test-amb-detail",
            mappingTableVersion = "v1",
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )
        val eligibility =
            EvaluationEligibility.fromMappingResult(mapping)
        assertEquals(reason, eligibility.reasonDetail)
    }

    @Test
    fun `invalid failure code is preserved`() {
        val mapping = TaxonomyMappingResult.Invalid(
            modelLabel = "fake",
            modelLabelIndex = 0,
            failureReason = "Invalid taxonomy key",
            failureCode = MappingFailureCode.INVALID_TAXONOMY_KEY,
            mappingId = "test-fc",
            mappingTableVersion = "v1",
            taxonomyVersion = fixtures.TAXONOMY_VERSION
        )
        val eligibility =
            EvaluationEligibility.fromMappingResult(mapping)
        assertEquals(
            MappingFailureCode.INVALID_TAXONOMY_KEY,
            eligibility.failureCode
        )
    }
}
