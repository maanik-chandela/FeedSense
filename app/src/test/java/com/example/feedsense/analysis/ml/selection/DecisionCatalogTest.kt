package com.example.feedsense.analysis.ml.selection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-1.
 *
 * The compiled research catalog must stay a valid decision set:
 * unique ids, families A-F present in order, fully-scored
 * shortlist, complete (never fabricated) matrix rows, and the
 * license rule for SHORTLISTED candidates.
 */
class DecisionCatalogTest {

    @Test
    fun `catalog version is pinned`() {
        assertEquals("8b-15-1-v1", ResearchDecisionCatalog.CATALOG_VERSION)
    }

    @Test
    fun `candidate ids are unique`() {
        val ids = ResearchDecisionCatalog.candidates.map { it.candidateId }
        assertEquals(ids.toSet().size, ids.size)
    }

    @Test
    fun `every declared family A-F has a candidate`() {
        for (family in ResearchDecisionCatalog.familiesInOrder) {
            val members = ResearchDecisionCatalog.candidatesByFamily(family)
            assertTrue(
                "family $family must have candidates",
                members.isNotEmpty()
            )
            assertTrue(members.all { it.familyId == family })
        }
    }

    @Test
    fun `families are presented in canonical order A-F`() {
        assertEquals(
            listOf("A", "B", "C", "D", "E", "F"),
            ResearchDecisionCatalog.familiesInOrder
        )
    }

    @Test
    fun `candidates are resolvable by id`() {
        for (candidate in ResearchDecisionCatalog.candidates) {
            assertEquals(candidate, ResearchDecisionCatalog.candidate(candidate.candidateId))
        }
        assertTrue(ResearchDecisionCatalog.candidate("does-not-exist") == null)
    }

    @Test
    fun `shortlisted candidates score all twenty criteria`() {
        for (candidate in ResearchDecisionCatalog.candidates) {
            if (candidate.status == CandidateStatus.SHORTLISTED) {
                var scored = 0
                for (criterion in researchCriteriaClone()) {
                    if (candidate.hasScore(criterion)) scored++
                }
                assertEquals(
                    "shortlisted ${candidate.candidateId} must be fully scored",
                    20,
                    scored
                )
            }
        }
    }

    @Test
    fun `shortlisted candidates carry a verified license`() {
        for (candidate in ResearchDecisionCatalog.candidates) {
            if (candidate.status == CandidateStatus.SHORTLISTED) {
                assertTrue(
                    "${candidate.candidateId} license must be verified",
                    candidate.license != "UNKNOWN"
                )
                assertTrue(
                    "${candidate.candidateId} license evidence must not be UNKNOWN",
                    candidate.licenseEvidence != EvidenceLevel.UNKNOWN
                )
            }
        }
    }

    @Test
    fun `all four status dispositions are represented`() {
        val statuses = ResearchDecisionCatalog.candidates.map { it.status }.toSet()
        assertEquals(
            setOf(
                CandidateStatus.SHORTLISTED,
                CandidateStatus.CONDITIONAL,
                CandidateStatus.REJECTED,
                CandidateStatus.OBSERVED_ONLY
            ),
            statuses
        )
    }

    @Test
    fun `completeScores produces exactly twenty cells with no invented ratings`() {
        for (candidate in ResearchDecisionCatalog.candidates) {
            val row = ResearchDecisionCatalog.completeScores(candidate)
            assertEquals(
                "matrix row for ${candidate.candidateId}",
                researchCriteriaClone().size,
                row.size
            )
            for (score in row.values) {
                assertTrue(score.rationale.isNotBlank())
            }
        }
    }

    @Test
    fun `catalog criteria order matches the canonical enum order`() {
        assertEquals(
            EvaluationCriterion.entries,
            ResearchDecisionCatalog.criteria
        )
    }

    @Test
    fun `adr is accepted at its first version`() {
        val record = ResearchDecisionCatalog.decision
        assertEquals("adr-0001", record.adrId)
        assertEquals(AdrStatus.ACCEPTED, record.status)
        assertEquals("1.0", record.version)
        assertEquals("8B-15-1", record.milestoneId)
        assertTrue(record.decision.isNotBlank())
        assertTrue(record.alternatives.isNotEmpty())
        assertTrue(record.risks.isNotEmpty())
        assertTrue(record.futureValidation.isNotEmpty())
    }

    @Test
    fun `candidate runtimes are distinct`() {
        for (candidate in ResearchDecisionCatalog.candidates) {
            assertEquals(
                "runtimes on ${candidate.candidateId}",
                candidate.runtimes.toSet().size,
                candidate.runtimes.size
            )
        }
    }

    private fun researchCriteriaClone(): List<EvaluationCriterion> =
        EvaluationCriterion.entries
}