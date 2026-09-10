package com.example.feedsense.analysis.ml.repro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/*
 * Milestone 8B-15-2.
 *
 * OUTPUT MAPPING identity: the adapter from model output to the
 * existing FeedSense taxonomy is versioned independently of the
 * taxonomy. Same mapping -> equal; changed mapping -> different.
 */
class ReproOutputMappingIdentityTest {

    @Test
    fun `same mapping produces equal identity`() {
        assertEquals(ReproFix.outputMapping, ReproFix.outputMapping.copy())
    }

    @Test
    fun `changed output mapping version produces different identity`() {
        assertNotEquals(
            ReproFix.outputMapping,
            ReproFix.outputMapping.copy(outputMappingVersion = "map-v2")
        )
    }

    @Test
    fun `changed model label ordering produces different identity`() {
        assertNotEquals(
            ReproFix.outputMapping,
            ReproFix.outputMapping.copy(
                modelOutputLabels = listOf("classB", "classA"),
                labelOrdering = listOf("classB", "classA")
            )
        )
    }

    @Test
    fun `changed category mapping version produces different identity`() {
        assertNotEquals(
            ReproFix.outputMapping,
            ReproFix.outputMapping.copy(feedSenseCategoryMappingVersion = "cat-v2")
        )
    }

    @Test
    fun `changed top-k produces different identity`() {
        assertNotEquals(
            ReproFix.outputMapping,
            ReproFix.outputMapping.copy(topK = 5)
        )
    }

    @Test
    fun `same mapping produces identical canonical serialization`() {
        assertEquals(
            ReproCanonicalSerializer.serializeOutputMapping(ReproFix.outputMapping),
            ReproCanonicalSerializer.serializeOutputMapping(ReproFix.outputMapping.copy())
        )
    }

    @Test
    fun `empty label list is rejected`() {
        try {
            ReproFix.outputMapping.copy(modelOutputLabels = emptyList(), labelOrdering = emptyList())
            throw AssertionError("empty label list must be rejected")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `label ordering missing a model label is rejected`() {
        try {
            ReproFix.outputMapping.copy(labelOrdering = listOf("classA"))
            throw AssertionError("mismatched labelOrdering must be rejected")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `duplicate model labels are rejected`() {
        try {
            ReproFix.outputMapping.copy(
                modelOutputLabels = listOf("classA", "classA"),
                labelOrdering = listOf("classA", "classA")
            )
            throw AssertionError("duplicate labels must be rejected")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `unresolved label set is an explicit sentinel not a fabrication`() {
        val unresolved = ReproFix.outputMapping.copy(
            outputMappingVersion = "map-v0",
            modelOutputLabels = listOf(REPRO_UNRESOLVED_LABEL),
            labelOrdering = listOf(REPRO_UNRESOLVED_LABEL),
            feedSenseCategoryMappingVersion = REPRO_UNSPECIFIED,
            mappingMode = OutputMappingMode.UNKNOWN,
            unknownBehavior = UnknownMappingBehavior.UNKNOWN,
            outputSemantics = null
        )
        assertEquals("map-v0", unresolved.outputMappingVersion)
        assertEquals(listOf(REPRO_UNRESOLVED_LABEL), unresolved.modelOutputLabels)
    }
}