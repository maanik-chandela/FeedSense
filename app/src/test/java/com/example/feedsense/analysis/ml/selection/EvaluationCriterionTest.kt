package com.example.feedsense.analysis.ml.selection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-1.
 *
 * The 20-criterion matrix and its cell type. Ratings are
 * qualitative; a numeric-grade cell would imply a measurement
 * that this phase never ran.
 */
class EvaluationCriterionTest {

    @Test
    fun `defines exactly twenty criteria`() {
        assertEquals(20, EvaluationCriterion.entries.size)
    }

    @Test
    fun `criteria ids are unique and non-blank`() {
        val ids = EvaluationCriterion.entries.map { it.id }
        assertEquals(ids.toSet().size, ids.size)
        assertTrue(ids.all { it.isNotBlank() })
    }

    @Test
    fun `criteria have stable declaration order`() {
        val ids = EvaluationCriterion.entries.map { it.id }
        // declaration order is the canonical order and is stable
        assertEquals(ids, EvaluationCriterion.entries.map { it.id })
    }

    @Test
    fun `blank rationale is rejected`() {
        var threw = false
        try {
            CriterionScore(
                EvaluationCriterion.MODEL_SIZE_MB,
                CriterionRating.STRONG,
                EvidenceLevel.DOCUMENTED_BY_SOURCE,
                ""
            )
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("blank rationale must be rejected", threw)
    }

    @Test
    fun `unknown cells keep short honest rationales`() {
        var threw = false
        try {
            CriterionScore(
                EvaluationCriterion.PEAK_RAM,
                CriterionRating.UNKNOWN,
                EvidenceLevel.UNKNOWN,
                "padded rationale ".repeat(40)
            )
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("verbose UNKNOWN rationale must be rejected", threw)

        val fine = CriterionScore(
            EvaluationCriterion.PEAK_RAM,
            CriterionRating.UNKNOWN,
            EvidenceLevel.UNKNOWN,
            "No evidence in 8B-15-1."
        )
        assertEquals(CriterionRating.UNKNOWN, fine.rating)
    }

    @Test
    fun `rating sort order is stable for matrix rendering`() {
        val sorted = listOf(
            CriterionRating.WEAK,
            CriterionRating.STRONG,
            CriterionRating.NOT_APPLICABLE
        ).sortedBy { it.sortOrder }
        assertEquals(listOf(CriterionRating.STRONG, CriterionRating.WEAK, CriterionRating.NOT_APPLICABLE), sorted)
    }

    @Test
    fun `fromLabel round-trips ratings`() {
        for (rating in CriterionRating.entries) {
            assertEquals(rating, CriterionRating.fromLabel(rating.label))
        }
        assertTrue(CriterionRating.fromLabel("BOGUS") == null)
    }
}