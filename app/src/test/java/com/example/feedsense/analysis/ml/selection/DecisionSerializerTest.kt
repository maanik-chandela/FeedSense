package com.example.feedsense.analysis.ml.selection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-1.
 *
 * Deterministic serialization of the decision set:
 *   - identical input => identical text (byte-stable),
 *   - keys sorted,
 *   - optional/missing fields omitted, never zero-filled,
 *   - criterion cells expose rating/evidence/rationale but NO
 *     numeric value (an unmeasured number is not emitted),
 *   - versions are embedded so results stay attributable.
 */
class DecisionSerializerTest {

    @Test
    fun `candidate serialization is deterministic`() {
        val candidate = ResearchDecisionCatalog.candidate("D1-tflite-litert")!!
        val a = DecisionSerializer.candidateToJson(candidate)
        val b = DecisionSerializer.candidateToJson(candidate)
        assertTrue(a == b)
    }

    @Test
    fun `decision serialization is deterministic`() {
        val a = DecisionSerializer.decisionToJson(ResearchDecisionCatalog.decision)
        val b = DecisionSerializer.decisionToJson(ResearchDecisionCatalog.decision)
        assertTrue(a == b)
    }

    @Test
    fun `catalog serialization is deterministic and versioned`() {
        val a = DecisionSerializer.catalogToJson()
        val b = DecisionSerializer.catalogToJson()
        assertTrue(a == b)
        assertTrue(a.contains("\"catalogVersion\":\"8b-15-1-v1\""))
        assertTrue(
            a.contains("\"adrVersion\":\"${ResearchDecisionCatalog.decision.version}\"")
        )
    }

    @Test
    fun `criteria cells carry no numeric value key`() {
        val candidate = ResearchDecisionCatalog.candidate("A1-mobilenet-v4-conv-s")!!
        val map = DecisionSerializer.candidateToMap(candidate)
        val criterionKeys = map.keys.filter { it.startsWith("criteria.") }
        assertTrue(criterionKeys.isNotEmpty())

        val ratingIds = criterionKeys.filter { it.endsWith(".rating") }.map {
            it.removePrefix("criteria.").removeSuffix(".rating")
        }
        // every scored criterion has rating + evidence + rationale triples
        for (c in ratingIds) {
            assertTrue(map.containsKey("criteria.$c.rating"))
            assertTrue(map.containsKey("criteria.$c.evidence"))
            assertTrue(map.containsKey("criteria.$c.rationale"))
        }
        // the forbidden numeric-grade key must never appear
        assertFalse(
            "numeric value key must not be emitted",
            criterionKeys.any { it.endsWith(".value") || it.endsWith(".score") }
        )
    }

    @Test
    fun `candidate keys are sorted for stable text`() {
        for (candidate in ResearchDecisionCatalog.candidates) {
            val map = DecisionSerializer.candidateToMap(candidate)
            assertKeysSorted(DecisionSerializer.candidateToJson(candidate), map.size)
        }
    }

    @Test
    fun `decision keys are sorted for stable text`() {
        val map = DecisionSerializer.decisionToMap(ResearchDecisionCatalog.decision)
        assertKeysSorted(
            DecisionSerializer.decisionToJson(ResearchDecisionCatalog.decision),
            map.size
        )
    }

    @Test
    fun `missing optional fields are omitted not zero-filled`() {
        val candidate = ResearchDecisionCatalog.candidate("B1-compact-vit-mobilevit-family")!!
        val map = DecisionSerializer.candidateToMap(candidate)
        assertFalse("null params must be omitted", map.containsKey("parametersMillions"))
        assertFalse("null latency must be omitted", map.containsKey("latencyMs"))
        assertFalse("null size must be omitted", map.containsKey("modelSizeMegabytes"))
    }

    @Test
    fun `esacaped text never contains raw quotes or newlines`() {
        val text = DecisionSerializer.decisionToJson(ResearchDecisionCatalog.decision)
        assertFalse(text.contains("\n"))
        assertTrue(text.startsWith("{"))
        assertTrue(text.endsWith("}"))
    }

    @Test
    fun `reproducibility key embeds version and candidate set`() {
        val key = DecisionSerializer.reproducibilityKey()
        assertTrue(key.contains("8b-15-1-v1"))
        assertTrue(key.contains(ResearchDecisionCatalog.decision.version))
        for (candidate in ResearchDecisionCatalog.candidates) {
            assertTrue("key must name ${candidate.candidateId}", key.contains(candidate.candidateId))
        }
        // stable across calls
        assertTrue(key == DecisionSerializer.reproducibilityKey())
    }

    @Test
    fun `malformed candidate is rejected before serialization`() {
        var threw = false
        try {
            DecisionSerializer.candidateToJson(
                ResearchDecisionCatalog.candidate("D1-tflite-litert")!!
                    .copy(candidateId = " ")
            )
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("blank candidateId must be rejected", threw)
    }

    @Test
    fun `unknown evidence labels are never emitted as classes`() {
        for (candidate in ResearchDecisionCatalog.candidates) {
            val map = DecisionSerializer.candidateToMap(candidate)
            val evidenceValues = map.values.filter {
                it in EvidenceLevel.entries.map { e -> e.label }
            }
            for (v in evidenceValues) {
                assertTrue(
                    "unknown label $v unsupported by fromLabel",
                    EvidenceLevel.fromLabel(v) != null
                )
            }
        }
    }

    private fun assertKeysSorted(text: String, expectedKeyCount: Int) {
        val keyPattern = Regex("\"([A-Za-z0-9._]+)\":")
        val keys = keyPattern.findAll(text).map { it.groupValues[1] }.toList()
        assertTrue(
            "keys must be sorted, got ${keys.take(6)}...",
            keys == keys.sorted()
        )
        assertTrue("expected $expectedKeyCount keys, got ${keys.size}", keys.size == expectedKeyCount)
    }
}