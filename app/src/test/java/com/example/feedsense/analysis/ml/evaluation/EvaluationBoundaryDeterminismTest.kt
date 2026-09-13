package com.example.feedsense.analysis.ml.evaluation

import com.example.feedsense.analysis.ml.taxonomy.MappingStatus
import com.example.feedsense.analysis.ml.taxonomy.TaxonomyMappingResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// --------------------------------
// EVALUATION BOUNDARY DETERMINISM
// TEST (8B-15-8)
// --------------------------------
//
// Determinism tests for the evaluation boundary:
//   - Same input -> byte-identical serialized output
//   - Same comparison -> identical result
//   - Stable ordering of reason codes/fields
//   - Deterministic hashing

class EvaluationBoundaryDeterminismTest {

    private val fixtures = EvaluationBoundaryFixtures

    // --------------------------------
    // SERIALIZATION DETERMINISM
    // --------------------------------

    @Test
    fun `same candidate snapshot produces byte-identical serialized output`() {
        val inference = fixtures.inferenceResultSports()
        val mappingResults = fixtures.mapInferenceResult(inference)
        val primary = mappingResults.first()
        val eligibility =
            EvaluationEligibility.fromMappingResult(primary)
        val snapshot = fixtures.createSnapshot(
            mappingResult = primary,
            inferenceResult = inference,
            candidateId = "det-001",
            eligibility = eligibility
        )

        val text1 =
            EvaluationBoundarySerializer.serializeSnapshot(snapshot)
        val text2 =
            EvaluationBoundarySerializer.serializeSnapshot(snapshot)
        assertEquals(text1, text2)
    }

    @Test
    fun `same eligibility produces byte-identical serialized output`() {
        val inference = fixtures.inferenceResultSports()
        val mappingResults = fixtures.mapInferenceResult(inference)
        val primary = mappingResults.first()
        val eligibility =
            EvaluationEligibility.fromMappingResult(primary)

        val text1 =
            EvaluationBoundarySerializer.serializeEligibility(eligibility)
        val text2 =
            EvaluationBoundarySerializer.serializeEligibility(eligibility)
        assertEquals(text1, text2)
    }

    @Test
    fun `same comparison input produces identical result`() {
        val inference = fixtures.inferenceResultSports()
        val mappingResults = fixtures.mapInferenceResult(inference)
        val primary = mappingResults.first()
        val eligibility =
            EvaluationEligibility.fromMappingResult(primary)
        val snapshot = fixtures.createSnapshot(
            mappingResult = primary,
            inferenceResult = inference,
            candidateId = "det-compare-001",
            eligibility = eligibility
        )

        val gt = fixtures.groundTruthSports()
        val input1 =
            EvaluationComparisonInput.fromCandidateAndTruth(
                snapshot = snapshot,
                truth = gt,
                groundTruthTaxonomyVersion =
                    fixtures.TAXONOMY_VERSION
            )
        val input2 =
            EvaluationComparisonInput.fromCandidateAndTruth(
                snapshot = snapshot,
                truth = gt,
                groundTruthTaxonomyVersion =
                    fixtures.TAXONOMY_VERSION
            )

        val result1 = EvaluationComparison.compare(input1)
        val result2 = EvaluationComparison.compare(input2)

        assertEquals(result1.status, result2.status)
        assertEquals(result1.reasons, result2.reasons)
        assertEquals(
            result1.predictionTaxonomyId,
            result2.predictionTaxonomyId
        )
        assertEquals(
            result1.groundTruthTaxonomyId,
            result2.groundTruthTaxonomyId
        )
    }

    // --------------------------------
    // STABLE FIELD ORDERING
    // --------------------------------

    @Test
    fun `serialized snapshot has sorted keys`() {
        val inference = fixtures.inferenceResultSports()
        val mappingResults = fixtures.mapInferenceResult(inference)
        val primary = mappingResults.first()
        val eligibility =
            EvaluationEligibility.fromMappingResult(primary)
        val snapshot = fixtures.createSnapshot(
            mappingResult = primary,
            inferenceResult = inference,
            candidateId = "det-order-001",
            eligibility = eligibility
        )

        val text =
            EvaluationBoundarySerializer.serializeSnapshot(snapshot)
        val keys = text
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

    @Test
    fun `serialized eligibility has sorted keys`() {
        val inference = fixtures.inferenceResultSports()
        val mappingResults = fixtures.mapInferenceResult(inference)
        val primary = mappingResults.first()
        val eligibility =
            EvaluationEligibility.fromMappingResult(primary)

        val text =
            EvaluationBoundarySerializer.serializeEligibility(eligibility)
        val keys = text
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

    @Test
    fun `serialized comparison result has sorted keys`() {
        val inference = fixtures.inferenceResultSports()
        val mappingResults = fixtures.mapInferenceResult(inference)
        val primary = mappingResults.first()
        val eligibility =
            EvaluationEligibility.fromMappingResult(primary)
        val snapshot = fixtures.createSnapshot(
            mappingResult = primary,
            inferenceResult = inference,
            candidateId = "det-order-comp",
            eligibility = eligibility
        )
        val gt = fixtures.groundTruthSports()
        val input =
            EvaluationComparisonInput.fromCandidateAndTruth(
                snapshot = snapshot,
                truth = gt,
                groundTruthTaxonomyVersion =
                    fixtures.TAXONOMY_VERSION
            )
        val result = EvaluationComparison.compare(input)

        val text =
            EvaluationBoundarySerializer.serializeComparisonResult(result)
        val keys = text
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
    // DETERMINISTIC HASHING
    // --------------------------------

    @Test
    fun `snapshot hash is deterministic`() {
        val inference = fixtures.inferenceResultSports()
        val mappingResults = fixtures.mapInferenceResult(inference)
        val primary = mappingResults.first()
        val eligibility =
            EvaluationEligibility.fromMappingResult(primary)
        val snapshot = fixtures.createSnapshot(
            mappingResult = primary,
            inferenceResult = inference,
            candidateId = "det-hash-001",
            eligibility = eligibility
        )

        val hash1 = snapshot.deterministicCreationHash
        val hash2 = EvaluationBoundarySerializer
            .computeSnapshotHash(snapshot)
        assertEquals(hash1, hash2)
    }

    @Test
    fun `same comparison produces same comparison hash`() {
        val inference = fixtures.inferenceResultSports()
        val mappingResults = fixtures.mapInferenceResult(inference)
        val primary = mappingResults.first()
        val eligibility =
            EvaluationEligibility.fromMappingResult(primary)
        val snapshot = fixtures.createSnapshot(
            mappingResult = primary,
            inferenceResult = inference,
            candidateId = "det-chash-001",
            eligibility = eligibility
        )
        val gt = fixtures.groundTruthSports()
        val input =
            EvaluationComparisonInput.fromCandidateAndTruth(
                snapshot = snapshot,
                truth = gt,
                groundTruthTaxonomyVersion =
                    fixtures.TAXONOMY_VERSION
            )

        val result1 = EvaluationComparison.compare(input)
        val result2 = EvaluationComparison.compare(input)
        assertEquals(
            result1.comparisonHash,
            result2.comparisonHash
        )
    }

    @Test
    fun `different inputs produce different hashes`() {
        val inference1 = fixtures.inferenceResultSports()
        val mappingResults1 =
            fixtures.mapInferenceResult(inference1)
        val eligibility1 =
            EvaluationEligibility.fromMappingResult(
                mappingResults1.first()
            )
        val snapshot1 = fixtures.createSnapshot(
            mappingResult = mappingResults1.first(),
            inferenceResult = inference1,
            candidateId = "det-hash-diff-1",
            eligibility = eligibility1
        )

        val inference2 = fixtures.inferenceResultMappedLabel()
        val mappingResults2 =
            fixtures.mapInferenceResult(inference2)
        val eligibility2 =
            EvaluationEligibility.fromMappingResult(
                mappingResults2.first()
            )
        val snapshot2 = fixtures.createSnapshot(
            mappingResult = mappingResults2.first(),
            inferenceResult = inference2,
            candidateId = "det-hash-diff-2",
            eligibility = eligibility2
        )

        val hash1 = EvaluationBoundarySerializer
            .computeSnapshotHash(snapshot1)
        val hash2 = EvaluationBoundarySerializer
            .computeSnapshotHash(snapshot2)
        assertNotEquals(hash1, hash2)
    }

    // --------------------------------
    // ENUM SERIALIZATION STABILITY
    // --------------------------------

    @Test
    fun `eligibility status labels are stable`() {
        assertEquals(
            "ELIGIBLE",
            EligibilityStatus.ELIGIBLE.label
        )
        assertEquals(
            "NOT_ELIGIBLE_UNMAPPED",
            EligibilityStatus.NOT_ELIGIBLE_UNMAPPED.label
        )
        assertEquals(
            "NOT_ELIGIBLE_AMBIGUOUS",
            EligibilityStatus.NOT_ELIGIBLE_AMBIGUOUS.label
        )
        assertEquals(
            "NOT_ELIGIBLE_INVALID",
            EligibilityStatus.NOT_ELIGIBLE_INVALID.label
        )
        assertEquals(
            "NOT_ELIGIBLE_UNSUPPORTED",
            EligibilityStatus.NOT_ELIGIBLE_UNSUPPORTED.label
        )
        assertEquals(
            "NOT_ELIGIBLE_REJECTED",
            EligibilityStatus.NOT_ELIGIBLE_REJECTED.label
        )
        assertEquals(
            "NOT_ELIGIBLE_MISSING_OUTPUT",
            EligibilityStatus.NOT_ELIGIBLE_MISSING_OUTPUT.label
        )
        assertEquals(
            "NOT_ELIGIBLE_MISSING_TAXONOMY",
            EligibilityStatus.NOT_ELIGIBLE_MISSING_TAXONOMY.label
        )
        assertEquals(
            "NOT_ELIGIBLE_MISSING_MAPPING",
            EligibilityStatus.NOT_ELIGIBLE_MISSING_MAPPING.label
        )
        assertEquals(
            "NOT_ELIGIBLE_VERSION_MISMATCH",
            EligibilityStatus.NOT_ELIGIBLE_VERSION_MISMATCH.label
        )
    }

    @Test
    fun `comparison status labels are stable`() {
        assertEquals("MATCH", ComparisonStatus.MATCH.label)
        assertEquals(
            "MISMATCH",
            ComparisonStatus.MISMATCH.label
        )
        assertEquals(
            "UNCOMPARABLE",
            ComparisonStatus.UNCOMPARABLE.label
        )
        assertEquals(
            "PENDING_REVIEW",
            ComparisonStatus.PENDING_REVIEW.label
        )
    }

    @Test
    fun `decision reason labels are stable`() {
        assertEquals(
            "VALID_DIRECT_MAPPING",
            EvaluationDecisionReason.VALID_DIRECT_MAPPING.label
        )
        assertEquals(
            "VALID_MAPPED_MAPPING",
            EvaluationDecisionReason.VALID_MAPPED_MAPPING.label
        )
        assertEquals(
            "UNMAPPED_MODEL_LABEL",
            EvaluationDecisionReason.UNMAPPED_MODEL_LABEL.label
        )
        assertEquals(
            "AMBIGUOUS_MAPPING",
            EvaluationDecisionReason.AMBIGUOUS_MAPPING.label
        )
        assertEquals(
            "UNSUPPORTED_MAPPING",
            EvaluationDecisionReason.UNSUPPORTED_MAPPING.label
        )
        assertEquals(
            "REJECTED_MAPPING",
            EvaluationDecisionReason.REJECTED_MAPPING.label
        )
        assertEquals(
            "INVALID_MAPPING",
            EvaluationDecisionReason.INVALID_MAPPING.label
        )
        assertEquals(
            "MISSING_PREDICTION",
            EvaluationDecisionReason.MISSING_PREDICTION.label
        )
        assertEquals(
            "MISSING_TAXONOMY_VERSION",
            EvaluationDecisionReason.MISSING_TAXONOMY_VERSION.label
        )
        assertEquals(
            "MISSING_MAPPING",
            EvaluationDecisionReason.MISSING_MAPPING.label
        )
        assertEquals(
            "TAXONOMY_VERSION_MISMATCH",
            EvaluationDecisionReason.TAXONOMY_VERSION_MISMATCH.label
        )
        assertEquals(
            "TAXONOMY_ID_MATCH",
            EvaluationDecisionReason.TAXONOMY_ID_MATCH.label
        )
        assertEquals(
            "TAXONOMY_ID_MISMATCH",
            EvaluationDecisionReason.TAXONOMY_ID_MISMATCH.label
        )
        assertEquals(
            "STRUCTURALLY_UNCOMPARABLE",
            EvaluationDecisionReason.STRUCTURALLY_UNCOMPARABLE.label
        )
        assertEquals(
            "INVALID_GROUND_TRUTH_STATE",
            EvaluationDecisionReason.INVALID_GROUND_TRUTH_STATE.label
        )
    }

    // --------------------------------
    // FULL BOUNDARY RESULT DETERMINISM
    // --------------------------------

    @Test
    fun `full boundary result is deterministic across identical runs`() {
        val inference = fixtures.inferenceResultSports()
        val mappingResults = fixtures.mapInferenceResult(inference)
        val primary = mappingResults.first()
        val eligibility =
            EvaluationEligibility.fromMappingResult(primary)
        val snapshot = fixtures.createSnapshot(
            mappingResult = primary,
            inferenceResult = inference,
            candidateId = "det-full-001",
            eligibility = eligibility
        )
        val gt = fixtures.groundTruthSports()

        val result1 = EvaluationBoundaryEvaluator.evaluate(
            snapshot = snapshot,
            groundTruth = gt,
            groundTruthTaxonomyVersion =
                fixtures.TAXONOMY_VERSION
        )
        val result2 = EvaluationBoundaryEvaluator.evaluate(
            snapshot = snapshot,
            groundTruth = gt,
            groundTruthTaxonomyVersion =
                fixtures.TAXONOMY_VERSION
        )

        assertEquals(
            result1.eligibility.status,
            result2.eligibility.status
        )
        assertEquals(
            result1.comparison!!.status,
            result2.comparison!!.status
        )
        assertEquals(
            result1.comparison!!.reasons,
            result2.comparison!!.reasons
        )
        assertEquals(
            result1.comparison!!.comparisonHash,
            result2.comparison!!.comparisonHash
        )
    }
}
