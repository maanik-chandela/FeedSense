package com.example.feedsense.analysis.evaluation.comparative

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-8.
 *
 * Error-type and (deterministic) root-cause comparison tests.
 */
class ErrorRootCauseComparisonTest {

    // Clean pairs: no platform/contentType/duration truth so the ONLY
    // possible error is a category mismatch (or none when correct).
    private fun cleanPair(
        id: String,
        truth: String,
        baseline: String?,
        eightB: String?
    ): PairedPrediction = TestFixtures.pair(
        id = id,
        truthCategory = truth,
        baselineCategory = baseline,
        eightBCategory = eightB,
        platform = null,
        contentType = null,
        durationSeconds = null
    )

    @Test
    fun `error types tallied per system`() {
        // Two wrong pairs: baseline wrong -> one category error;
        // 8B wrong -> a different category error.
        val pairs = listOf(
            cleanPair("er1", "food", "sports", "food"),
            cleanPair("er2", "food", "food", "music")
        )
        val cmp = ErrorRootCauseComparison.compare(
            pairs, maxRootCauseAssessments = 1
        )
        assertEquals(2, cmp.analyzedPairs)

        val baselineErrors = cmp.errorTypesBySystem["BASELINE"]!!
        val eightBErrors = cmp.errorTypesBySystem["EIGHT_B"]!!

        // Both systems misclassified one item each -> non-empty.
        assertTrue(baselineErrors.isNotEmpty())
        assertTrue(eightBErrors.isNotEmpty())
    }

    @Test
    fun `both systems error maps differ when errors differ`() {
        val pairs = listOf(
            cleanPair("er3", "food", "sports", "food")
        )
        val cmp = ErrorRootCauseComparison.compare(pairs)
        val baseline = cmp.errorTypesBySystem["BASELINE"]!!
        val eightB = cmp.errorTypesBySystem["EIGHT_B"]!!
        // baseline has an error (wrong), 8B correct -> no category error
        assertTrue(baseline.isNotEmpty())
        assertTrue(eightB.isEmpty())
    }

    @Test
    fun `calibration flags separated per system`() {
        // Baseline wrong with high confidence -> overconfident-wrong.
        val pairs = listOf(
            cleanPair("er4", "food", "sports", "food"),
            cleanPair("er5", "food", "music", "food")
        )
        val cmp = ErrorRootCauseComparison.compare(pairs)
        assertTrue(cmp.overconfidentWrongBySystem["BASELINE"]!! > 0)
        assertEquals(0, cmp.overconfidentWrongBySystem["EIGHT_B"]!!)
    }

    @Test
    fun `root cause attribution tallied on bounded subset`() {
        val pairs = (0 until 6).map { i ->
            cleanPair("rc$i", "food", "sports", "food")
        }
        // Cap assessment count to keep the test fast and deterministic.
        val cmp = ErrorRootCauseComparison.compare(pairs, 3)
        val attribution = cmp.attributionBySystem["BASELINE"]!!
        assertTrue(attribution.isNotEmpty())
        val totalAssessed = attribution.values.sum()
        assertTrue(totalAssessed <= 3)
    }

    @Test
    fun `no errors when everything correct`() {
        val pairs = (0 until 3).map { i ->
            cleanPair("ok$i", "food", "food", "food")
        }
        val cmp = ErrorRootCauseComparison.compare(pairs)
        assertEquals(3, cmp.analyzedPairs)
        assertTrue(cmp.errorTypesBySystem["BASELINE"]!!.isEmpty())
        assertTrue(cmp.errorTypesBySystem["EIGHT_B"]!!.isEmpty())
    }
}
