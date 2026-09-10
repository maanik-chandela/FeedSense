package com.example.feedsense.analysis.ml.selection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-1.
 *
 * ADR records are immutable and versioned. The phase rule: a
 * decision is never silently changed - revisions bump `version`
 * and only a SUPERSEDED record carries a `supersedes` pointer.
 */
class ArchitectureDecisionRecordTest {

    private fun base(): ArchitectureDecisionRecord {
        return ArchitectureDecisionRecord(
            adrId = "adr-9999",
            title = "Test decision",
            date = "2026-09-06",
            milestoneId = "8B-15-1",
            context = listOf("context line"),
            decision = "adopt nothing",
            alternatives = listOf(
                DecisionAlternative("x1", "X", "held")
            ),
            consequences = listOf("none"),
            risks = listOf(
                AdrRisk(
                    "risk",
                    ImpactLevel.LOW,
                    ImpactLevel.LOW,
                    EvidenceLevel.HYPOTHESIS,
                    "mitigate"
                )
            ),
            futureValidation = listOf("validate later")
        )
    }

    @Test
    fun `version must be major-dot-minor`() {
        assertThrows { base().copy(version = "1") }
        assertThrows { base().copy(version = "" ) }
        val ok = base().copy(version = "2.1")
        assertEquals("2.1", ok.version)
    }

    @Test
    fun `date must be iso yyyy-mm-dd`() {
        assertThrows { base().copy(date = "06-09-2026") }
        assertThrows { base().copy(date = "2026/09/06") }
    }

    @Test
    fun `context must not be empty`() {
        assertThrows { base().copy(context = emptyList()) }
    }

    @Test
    fun `superseded records must name their supersedes pointer`() {
        assertThrows {
            base().copy(status = AdrStatus.SUPERSEDED, supersedes = null)
        }
        val ok = base().copy(
            status = AdrStatus.SUPERSEDED,
            supersedes = "adr-9999 v1.0"
        )
        assertEquals("adr-9999 v1.0", ok.supersedes)
    }

    @Test
    fun `identity key is stable`() {
        assertEquals("adr-9999 v1.0", base().identityKey)
    }

    @Test
    fun `blank decision is rejected`() {
        assertThrows { base().copy(decision = "   ") }
    }

    @Test
    fun `risk and alternative require content`() {
        assertThrows {
            AdrRisk("", ImpactLevel.LOW, ImpactLevel.LOW, EvidenceLevel.UNKNOWN, "m")
        }
        assertThrows {
            AdrRisk("r", ImpactLevel.LOW, ImpactLevel.LOW, EvidenceLevel.UNKNOWN, "")
        }
        assertThrows {
            DecisionAlternative("", "name", "reason")
        }
        assertThrows {
            DecisionAlternative("id", "", "reason")
        }
    }

    @Test
    fun `status labels round-trip`() {
        for (status in AdrStatus.entries) {
            assertEquals(status, AdrStatus.fromLabel(status.label))
        }
        assertTrue(AdrStatus.fromLabel("nope") == null)
    }

    private fun assertThrows(block: () -> Any?) {
        var threw = false
        try {
            block()
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("expected IllegalArgumentException", threw)
    }
}