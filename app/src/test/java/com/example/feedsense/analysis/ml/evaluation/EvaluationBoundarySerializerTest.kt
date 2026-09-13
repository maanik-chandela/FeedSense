package com.example.feedsense.analysis.ml.evaluation

import com.example.feedsense.analysis.ml.taxonomy.MappingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// --------------------------------
// EVALUATION BOUNDARY SERIALIZER
// TEST (8B-15-8)
// --------------------------------
//
// Tests for deterministic serialization of evaluation
// boundary types.

class EvaluationBoundarySerializerTest {

    private val fixtures = EvaluationBoundaryFixtures

    // --------------------------------
    // SNAPSHOT SERIALIZATION
    // --------------------------------

    @Test
    fun `snapshot serialization is deterministic`() {
        val snapshot = buildTestSnapshot()
        val text1 =
            EvaluationBoundarySerializer.serializeSnapshot(snapshot)
        val text2 =
            EvaluationBoundarySerializer.serializeSnapshot(snapshot)
        assertEquals(text1, text2)
    }

    @Test
    fun `snapshot serialization contains expected fields`() {
        val snapshot = buildTestSnapshot()
        val text =
            EvaluationBoundarySerializer.serializeSnapshot(snapshot)
        assertTrue(text.contains("\"candidateId\""))
        assertTrue(text.contains("\"modelId\""))
        assertTrue(text.contains("\"modelVersion\""))
        assertTrue(text.contains("\"modelNativeLabel\""))
        assertTrue(text.contains("\"mappingId\""))
        assertTrue(text.contains("\"taxonomyVersion\""))
        assertTrue(text.contains("\"eligibilityStatus\""))
        assertTrue(text.contains("\"eligibilityReason\""))
        assertTrue(text.contains("\"deterministicCreationHash\""))
    }

    @Test
    fun `snapshot serialization has sorted keys`() {
        val snapshot = buildTestSnapshot()
        val text =
            EvaluationBoundarySerializer.serializeSnapshot(snapshot)
        val keys = extractKeys(text)
        assertEquals(keys, keys.sorted())
    }

    // --------------------------------
    // ELIGIBILITY SERIALIZATION
    // --------------------------------

    @Test
    fun `eligibility serialization is deterministic`() {
        val eligibility = EvaluationEligibility(
            status = EligibilityStatus.ELIGIBLE,
            reason = EvaluationDecisionReason.VALID_DIRECT_MAPPING,
            reasonDetail = "Direct mapping to sports"
        )
        val text1 =
            EvaluationBoundarySerializer.serializeEligibility(eligibility)
        val text2 =
            EvaluationBoundarySerializer.serializeEligibility(eligibility)
        assertEquals(text1, text2)
    }

    @Test
    fun `eligibility serialization has sorted keys`() {
        val eligibility = EvaluationEligibility(
            status = EligibilityStatus.NOT_ELIGIBLE_UNMAPPED,
            reason = EvaluationDecisionReason.UNMAPPED_MODEL_LABEL,
            reasonDetail = "No FeedSense equivalent"
        )
        val text =
            EvaluationBoundarySerializer.serializeEligibility(eligibility)
        val keys = extractKeys(text)
        assertEquals(keys, keys.sorted())
    }

    // --------------------------------
    // COMPARISON RESULT SERIALIZATION
    // --------------------------------

    @Test
    fun `comparison result serialization is deterministic`() {
        val result = buildTestComparisonResult()
        val text1 =
            EvaluationBoundarySerializer.serializeComparisonResult(result)
        val text2 =
            EvaluationBoundarySerializer.serializeComparisonResult(result)
        assertEquals(text1, text2)
    }

    @Test
    fun `comparison result serialization has sorted keys`() {
        val result = buildTestComparisonResult()
        val text =
            EvaluationBoundarySerializer.serializeComparisonResult(result)
        val keys = extractKeys(text)
        assertEquals(keys, keys.sorted())
    }

    // --------------------------------
    // FULL BOUNDARY RESULT SERIALIZATION
    // --------------------------------

    @Test
    fun `full boundary result serialization is deterministic`() {
        val result = buildTestBoundaryResult()
        val text1 =
            EvaluationBoundarySerializer.serializeBoundaryResult(result)
        val text2 =
            EvaluationBoundarySerializer.serializeBoundaryResult(result)
        assertEquals(text1, text2)
    }

    @Test
    fun `full boundary result serialization starts with boundary prefix`() {
        val result = buildTestBoundaryResult()
        val text =
            EvaluationBoundarySerializer.serializeBoundaryResult(result)
        assertTrue(text.startsWith("boundary{"))
        assertTrue(text.endsWith("}"))
    }

    // --------------------------------
    // HASH COMPUTATION
    // --------------------------------

    @Test
    fun `snapshot hash is deterministic`() {
        val snapshot = buildTestSnapshot()
        val hash1 =
            EvaluationBoundarySerializer.computeSnapshotHash(snapshot)
        val hash2 =
            EvaluationBoundarySerializer.computeSnapshotHash(snapshot)
        assertEquals(hash1, hash2)
        assertTrue(hash1.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `comparison hash is deterministic`() {
        val result = buildTestComparisonResult()
        val hash1 =
            EvaluationBoundarySerializer.computeComparisonHash(result)
        val hash2 =
            EvaluationBoundarySerializer.computeComparisonHash(result)
        assertEquals(hash1, hash2)
        assertTrue(hash1.matches(Regex("[0-9a-f]{64}")))
    }

    @Test
    fun `different snapshots produce different hashes`() {
        val snapshot1 = buildTestSnapshot()
        val snapshot2 = buildTestSnapshot().copy(
            candidateId = "different-id"
        )
        val hash1 =
            EvaluationBoundarySerializer.computeSnapshotHash(snapshot1)
        val hash2 =
            EvaluationBoundarySerializer.computeSnapshotHash(snapshot2)
        assertNotEquals(hash1, hash2)
    }

    // --------------------------------
    // ENUM STABILITY
    // --------------------------------

    @Test
    fun `metadata map enum values use labels not ordinals`() {
        val eligibility = EvaluationEligibility(
            status = EligibilityStatus.NOT_ELIGIBLE_AMBIGUOUS,
            reason = EvaluationDecisionReason.AMBIGUOUS_MAPPING
        )
        val map =
            EvaluationBoundarySerializer.eligibilityMetadataMap(
                eligibility
            )
        assertEquals(
            "NOT_ELIGIBLE_AMBIGUOUS",
            map["status"]
        )
        assertEquals(
            "AMBIGUOUS_MAPPING",
            map["reason"]
        )
    }

    // --------------------------------
    // NULL HANDLING
    // --------------------------------

    @Test
    fun `null optional fields are omitted from metadata map`() {
        val eligibility = EvaluationEligibility(
            status = EligibilityStatus.ELIGIBLE,
            reason = EvaluationDecisionReason.VALID_DIRECT_MAPPING
        )
        val map =
            EvaluationBoundarySerializer.eligibilityMetadataMap(
                eligibility
            )
        assertTrue(!map.containsKey("reasonDetail"))
        assertTrue(!map.containsKey("mappingStatus"))
        assertTrue(!map.containsKey("failureCode"))
    }

    // --------------------------------
    // HELPERS
    // --------------------------------

    private fun buildTestSnapshot(): EvaluationCandidateSnapshot {
        val eligibility = EvaluationEligibility(
            status = EligibilityStatus.ELIGIBLE,
            reason = EvaluationDecisionReason.VALID_DIRECT_MAPPING,
            reasonDetail = "Test detail"
        )
        return EvaluationCandidateSnapshot(
            candidateId = "test-snap-001",
            modelId = "test-model",
            modelVersion = "v1",
            modelNativeLabel = "sports",
            modelNativeLabelIndex = 0,
            predictionRank = 0,
            predictionScore = 0.92,
            mappingId = "test-mapping",
            mappingTableVersion = "mapping-v1",
            taxonomyVersion = fixtures.TAXONOMY_VERSION,
            taxonomyIdentity =
                "feedsense-taxonomy:${fixtures.TAXONOMY_VERSION}",
            mappingStatus = MappingStatus.DIRECT,
            mappedTaxonomyKey = "sports",
            mappedTaxonomyDisplayName = "Sports",
            eligibility = eligibility,
            deterministicCreationHash = "test-hash-001"
        )
    }

    private fun buildTestComparisonResult(): EvaluationComparisonResult {
        return EvaluationComparisonResult(
            status = ComparisonStatus.MATCH,
            reasons = listOf(
                EvaluationDecisionReason.TAXONOMY_ID_MATCH
            ),
            predictionTaxonomyId = "sports",
            groundTruthTaxonomyId = "sports",
            taxonomyIdentityCompatible = true,
            taxonomyVersionCompatible = true,
            groundTruthAnnotationValid = true,
            comparisonHash = "test-comp-hash"
        )
    }

    private fun buildTestBoundaryResult(): EvaluationBoundaryResult {
        val eligibility = EvaluationEligibility(
            status = EligibilityStatus.ELIGIBLE,
            reason = EvaluationDecisionReason.VALID_DIRECT_MAPPING
        )
        val snapshot = buildTestSnapshot()
        val comparison = buildTestComparisonResult()
        return EvaluationBoundaryResult(
            candidateSnapshot = snapshot,
            eligibility = eligibility,
            comparison = comparison
        )
    }

    private fun extractKeys(jsonText: String): List<String> {
        return jsonText
            .trim()
            .removePrefix("{")
            .removeSuffix("}")
            .split(",")
            .map {
                it.substringBefore(":")
                    .trim()
                    .removeSurrounding("\"")
            }
    }
}
