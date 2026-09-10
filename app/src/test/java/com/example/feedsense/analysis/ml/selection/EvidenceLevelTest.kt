package com.example.feedsense.analysis.ml.selection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-1.
 *
 * The evidence vocabulary: exactly the seven required levels,
 * unique labels, a total strength order for stable rollup, and a
 * lossless label round-trip used by serialization.
 */
class EvidenceLevelTest {

    @Test
    fun `has exactly the seven required evidence levels`() {
        val expected = setOf(
            "FACT",
            "MEASURED",
            "DOCUMENTED_BY_SOURCE",
            "ENGINEERING_ESTIMATE",
            "HYPOTHESIS",
            "UNKNOWN",
            "REQUIRES_EXPERIMENT"
        )
        assertEquals(expected, EvidenceLevel.entries.map { it.label }.toSet())
        assertEquals(7, EvidenceLevel.entries.size)
    }

    @Test
    fun `labels are unique`() {
        val labels = EvidenceLevel.entries.map { it.label }
        assertEquals(labels.toSet().size, labels.size)
    }

    @Test
    fun `strength order is a strict total order`() {
        val orders = EvidenceLevel.entries.map { it.strengthOrder }
        assertEquals(orders.toSet().size, orders.size)
        assertEquals(orders.sorted(), orders)
    }

    @Test
    fun `fromLabel round-trips every defined level`() {
        for (level in EvidenceLevel.entries) {
            assertEquals(level, EvidenceLevel.fromLabel(level.label))
        }
    }

    @Test
    fun `fromLabel returns null for unknown labels`() {
        assertTrue(EvidenceLevel.fromLabel("SOMETIMES") == null)
        assertTrue(EvidenceLevel.fromLabel("") == null)
    }

    @Test
    fun `every level has a non-blank description`() {
        for (level in EvidenceLevel.entries) {
            assertFalse(level.description.isBlank())
        }
    }
}