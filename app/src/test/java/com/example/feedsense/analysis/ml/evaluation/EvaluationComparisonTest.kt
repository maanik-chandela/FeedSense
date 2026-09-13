package com.example.feedsense.analysis.ml.evaluation

import com.example.feedsense.analysis.ml.taxonomy.MappingStatus
import com.example.feedsense.analysis.ml.taxonomy.TaxonomyMappingResult
import com.example.feedsense.model.GroundTruth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// --------------------------------
// EVALUATION COMPARISON TEST (8B-15-8)
// --------------------------------
//
// Focused tests for the structural comparison logic,
// covering MATCH, MISMATCH, UNCOMPARABLE, and edge cases.

class EvaluationComparisonTest {

    private val fixtures = EvaluationBoundaryFixtures

    // --------------------------------
    // MATCH CASES
    // --------------------------------

    @Test
    fun `same taxonomy ID produces MATCH`() {
        val input = buildComparisonInput(
            predictionTaxonomyId = "sports",
            groundTruthCategory = "sports"
        )
        val result = EvaluationComparison.compare(input)
        assertEquals(ComparisonStatus.MATCH, result.status)
        assertTrue(
            result.reasons.contains(
                EvaluationDecisionReason.TAXONOMY_ID_MATCH
            )
        )
        assertEquals("sports", result.predictionTaxonomyId)
        assertEquals("sports", result.groundTruthTaxonomyId)
    }

    @Test
    fun `case-insensitive match still produces MATCH`() {
        val input = buildComparisonInput(
            predictionTaxonomyId = "Sports",
            groundTruthCategory = "sports"
        )
        val result = EvaluationComparison.compare(input)
        // After normalization, both should resolve to "sports"
        assertEquals(ComparisonStatus.MATCH, result.status)
    }

    // --------------------------------
    // MISMATCH CASES
    // --------------------------------

    @Test
    fun `different valid taxonomy IDs produce MISMATCH`() {
        val input = buildComparisonInput(
            predictionTaxonomyId = "sports",
            groundTruthCategory = "entertainment"
        )
        val result = EvaluationComparison.compare(input)
        assertEquals(ComparisonStatus.MISMATCH, result.status)
        assertTrue(
            result.reasons.contains(
                EvaluationDecisionReason.TAXONOMY_ID_MISMATCH
            )
        )
        assertEquals("sports", result.predictionTaxonomyId)
        assertEquals(
            "entertainment",
            result.groundTruthTaxonomyId
        )
    }

    // --------------------------------
    // UNCOMPARABLE CASES
    // --------------------------------

    @Test
    fun `ineligible prediction produces UNCOMPARABLE`() {
        val input = buildIneligibleComparisonInput()
        val result = EvaluationComparison.compare(input)
        assertEquals(ComparisonStatus.UNCOMPARABLE, result.status)
        assertTrue(
            result.reasons.contains(
                EvaluationDecisionReason.STRUCTURALLY_UNCOMPARABLE
            )
        )
    }

    @Test
    fun `missing ground truth category produces UNCOMPARABLE`() {
        val input = buildComparisonInput(
            predictionTaxonomyId = "sports",
            groundTruthCategory = null
        )
        val result = EvaluationComparison.compare(input)
        assertEquals(ComparisonStatus.UNCOMPARABLE, result.status)
    }

    @Test
    fun `UNKNOWN ambiguity ground truth produces UNCOMPARABLE`() {
        val input = buildComparisonInput(
            predictionTaxonomyId = "sports",
            groundTruthCategory = "sports",
            groundTruthAmbiguity = GroundTruth.AMBIGUITY_UNKNOWN
        )
        val result = EvaluationComparison.compare(input)
        assertEquals(ComparisonStatus.UNCOMPARABLE, result.status)
        assertTrue(
            result.reasons.contains(
                EvaluationDecisionReason.INVALID_GROUND_TRUTH_STATE
            )
        )
    }

    @Test
    fun `taxonomy version mismatch produces UNCOMPARABLE`() {
        val input = buildComparisonInput(
            predictionTaxonomyId = "sports",
            groundTruthCategory = "sports",
            predictionTaxonomyVersion = "taxonomy:v1:70:abc",
            groundTruthTaxonomyVersion = "taxonomy:v2:70:def"
        )
        val result = EvaluationComparison.compare(input)
        assertEquals(ComparisonStatus.UNCOMPARABLE, result.status)
        assertTrue(
            result.reasons.contains(
                EvaluationDecisionReason.TAXONOMY_VERSION_MISMATCH
            )
        )
    }

    // --------------------------------
    // GROUND TRUTH INDEPENDENCE
    // --------------------------------

    @Test
    fun `comparison does not modify ground truth`() {
        val gt = fixtures.groundTruthSports()
        val originalCategory = gt.category
        val originalId = gt.id

        val input = EvaluationComparisonInput.fromCandidateAndTruth(
            snapshot = buildEligibleSnapshot("sports"),
            truth = gt,
            groundTruthTaxonomyVersion = fixtures.TAXONOMY_VERSION
        )
        EvaluationComparison.compare(input)

        assertEquals(originalCategory, gt.category)
        assertEquals(originalId, gt.id)
    }

    @Test
    fun `prediction does not alter ground truth during comparison`() {
        val gt = fixtures.groundTruthSports()
        val originalAmbiguity = gt.ambiguity

        val input = EvaluationComparisonInput.fromCandidateAndTruth(
            snapshot = buildEligibleSnapshot("sports"),
            truth = gt,
            groundTruthTaxonomyVersion = fixtures.TAXONOMY_VERSION
        )
        val result = EvaluationComparison.compare(input)
        assertEquals(ComparisonStatus.MATCH, result.status)

        // Ground truth is untouched
        assertEquals(originalAmbiguity, gt.ambiguity)
    }

    // --------------------------------
    // COMPARISON RESULT PROPERTIES
    // --------------------------------

    @Test
    fun `comparison hash is present for all valid results`() {
        val input = buildComparisonInput(
            predictionTaxonomyId = "sports",
            groundTruthCategory = "sports"
        )
        val result = EvaluationComparison.compare(input)
        assertNotNull(result.comparisonHash)
        assertTrue(result.comparisonHash.isNotEmpty())
    }

    @Test
    fun `taxonomy identity compatible is true for same version`() {
        val input = buildComparisonInput(
            predictionTaxonomyId = "sports",
            groundTruthCategory = "sports"
        )
        val result = EvaluationComparison.compare(input)
        assertTrue(result.taxonomyIdentityCompatible)
        assertTrue(result.taxonomyVersionCompatible)
    }

    @Test
    fun `ground truth annotation valid is true for CLEAR ambiguity`() {
        val input = buildComparisonInput(
            predictionTaxonomyId = "sports",
            groundTruthCategory = "sports",
            groundTruthAmbiguity = GroundTruth.AMBIGUITY_CLEAR
        )
        val result = EvaluationComparison.compare(input)
        assertTrue(result.groundTruthAnnotationValid)
    }

    @Test
    fun `ground truth annotation valid is false for UNKNOWN ambiguity`() {
        val input = buildComparisonInput(
            predictionTaxonomyId = "sports",
            groundTruthCategory = "sports",
            groundTruthAmbiguity = GroundTruth.AMBIGUITY_UNKNOWN
        )
        val result = EvaluationComparison.compare(input)
        assertFalse(result.groundTruthAnnotationValid)
    }

    // --------------------------------
    // UNCOMPARABLE IS NOT MISMATCH
    // --------------------------------

    @Test
    fun `UNCOMPARABLE and MISMATCH are distinct statuses`() {
        val uncomparableInput = buildIneligibleComparisonInput()
        val mismatchInput = buildComparisonInput(
            predictionTaxonomyId = "sports",
            groundTruthCategory = "education"
        )

        val uncomparableResult =
            EvaluationComparison.compare(uncomparableInput)
        val mismatchResult =
            EvaluationComparison.compare(mismatchInput)

        assertEquals(
            ComparisonStatus.UNCOMPARABLE,
            uncomparableResult.status
        )
        assertEquals(
            ComparisonStatus.MISMATCH,
            mismatchResult.status
        )
        // These are fundamentally different:
        // UNCOMPARABLE means structural incompatibility
        // MISMATCH means both valid but different IDs
        assertNotEquals(
            uncomparableResult.status,
            mismatchResult.status
        )
    }

    // --------------------------------
    // HELPERS
    // --------------------------------

    private fun buildComparisonInput(
        predictionTaxonomyId: String,
        groundTruthCategory: String?,
        groundTruthTaxonomyVersion: String? =
            fixtures.TAXONOMY_VERSION,
        predictionTaxonomyVersion: String =
            fixtures.TAXONOMY_VERSION,
        groundTruthAmbiguity: String =
            GroundTruth.AMBIGUITY_CLEAR
    ): EvaluationComparisonInput {
        val gt = GroundTruth(
            id = "gt-compare-test",
            evaluationItemId = "eval-compare-test",
            category = groundTruthCategory,
            ambiguity = groundTruthAmbiguity
        )

        val eligibility = EvaluationEligibility(
            status = EligibilityStatus.ELIGIBLE,
            reason = EvaluationDecisionReason.VALID_DIRECT_MAPPING
        )

        val snapshot = EvaluationCandidateSnapshot(
            candidateId = "c-compare-test",
            modelId = fixtures.MODEL_ARTIFACT_ID,
            modelVersion = fixtures.MODEL_ARTIFACT_VERSION,
            modelNativeLabel = "test_label",
            modelNativeLabelIndex = 0,
            predictionRank = 0,
            mappingId = "test-mapping",
            mappingTableVersion = fixtures.MAPPING_TABLE_VERSION,
            taxonomyVersion = predictionTaxonomyVersion,
            taxonomyIdentity =
                "feedsense-taxonomy:$predictionTaxonomyVersion",
            mappingStatus = MappingStatus.DIRECT,
            mappedTaxonomyKey = predictionTaxonomyId,
            eligibility = eligibility,
            deterministicCreationHash = "test-hash"
        )

        return EvaluationComparisonInput.fromCandidateAndTruth(
            snapshot = snapshot,
            truth = gt,
            groundTruthTaxonomyVersion =
                groundTruthTaxonomyVersion
        )
    }

    private fun buildIneligibleComparisonInput(): EvaluationComparisonInput {
        val gt = GroundTruth(
            id = "gt-ineligible",
            evaluationItemId = "eval-ineligible",
            category = "sports"
        )

        val eligibility = EvaluationEligibility(
            status = EligibilityStatus.NOT_ELIGIBLE_UNMAPPED,
            reason = EvaluationDecisionReason.UNMAPPED_MODEL_LABEL
        )

        val snapshot = EvaluationCandidateSnapshot(
            candidateId = "c-ineligible",
            modelId = fixtures.MODEL_ARTIFACT_ID,
            modelVersion = fixtures.MODEL_ARTIFACT_VERSION,
            modelNativeLabel = "weather",
            modelNativeLabelIndex = 0,
            predictionRank = 0,
            mappingId = "unmapped-mapping",
            mappingTableVersion = fixtures.MAPPING_TABLE_VERSION,
            taxonomyVersion = fixtures.TAXONOMY_VERSION,
            taxonomyIdentity =
                "feedsense-taxonomy:${fixtures.TAXONOMY_VERSION}",
            mappingStatus = MappingStatus.UNMAPPED,
            eligibility = eligibility,
            deterministicCreationHash = "test-hash-ineligible"
        )

        return EvaluationComparisonInput.fromCandidateAndTruth(
            snapshot = snapshot,
            truth = gt,
            groundTruthTaxonomyVersion =
                fixtures.TAXONOMY_VERSION
        )
    }

    private fun buildEligibleSnapshot(
        taxonomyKey: String
    ): EvaluationCandidateSnapshot {
        val eligibility = EvaluationEligibility(
            status = EligibilityStatus.ELIGIBLE,
            reason = EvaluationDecisionReason.VALID_DIRECT_MAPPING
        )
        return EvaluationCandidateSnapshot(
            candidateId = "c-eligible-snap",
            modelId = fixtures.MODEL_ARTIFACT_ID,
            modelVersion = fixtures.MODEL_ARTIFACT_VERSION,
            modelNativeLabel = taxonomyKey,
            modelNativeLabelIndex = 0,
            predictionRank = 0,
            mappingId = "direct-mapping",
            mappingTableVersion = fixtures.MAPPING_TABLE_VERSION,
            taxonomyVersion = fixtures.TAXONOMY_VERSION,
            taxonomyIdentity =
                "feedsense-taxonomy:${fixtures.TAXONOMY_VERSION}",
            mappingStatus = MappingStatus.DIRECT,
            mappedTaxonomyKey = taxonomyKey,
            eligibility = eligibility,
            deterministicCreationHash = "test-hash-eligible"
        )
    }

    private fun assertNotEquals(a: Any?, b: Any?) {
        org.junit.Assert.assertNotEquals(a, b)
    }
}
