package com.example.feedsense.analysis.evaluation.comparative

import com.example.feedsense.model.GroundTruth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-8.
 *
 * Outcome classification and builder behavior tests.
 */
class PairedPredictionTest {

    @Test
    fun `both correct`() {
        val p = TestFixtures.pair(
            "x1", "food", "food", "food"
        )
        assertTrue(p.baselineCorrect)
        assertFalse(p.baselineUnknown)
        assertTrue(p.eightBCorrect)
        assertFalse(p.eightBUnknown)
        assertEquals(ComparisonOutcome.BOTH_CORRECT, p.outcome)
        assertTrue(p.eligibleForAccuracy)
    }

    @Test
    fun `8B-only correct is improvement`() {
        val p = TestFixtures.pair(
            "x2", "food", "sports", "food"
        )
        assertEquals(
            ComparisonOutcome.EIGHT_B_ONLY_CORRECT, p.outcome
        )
        assertTrue(
            ComparisonOutcome.isEightBImprovement(p.outcome)
        )
        assertFalse(
            ComparisonOutcome.isEightBRegression(p.outcome)
        )
    }

    @Test
    fun `baseline-only correct is regression`() {
        val p = TestFixtures.pair(
            "x3", "food", "food", "sports"
        )
        assertEquals(
            ComparisonOutcome.BASELINE_ONLY_CORRECT, p.outcome
        )
        assertTrue(
            ComparisonOutcome.isEightBRegression(p.outcome)
        )
        assertFalse(
            ComparisonOutcome.isEightBImprovement(p.outcome)
        )
    }

    @Test
    fun `baseline unknown and 8B correct is improvement`() {
        val p = TestFixtures.pair(
            "x4", "food", null, "food"
        )
        assertEquals(
            ComparisonOutcome.BASELINE_UNKNOWN_EIGHT_B_CORRECT,
            p.outcome
        )
        assertTrue(
            ComparisonOutcome.isEightBImprovement(p.outcome)
        )
    }

    @Test
    fun `baseline correct and 8B unknown abstention`() {
        val p = TestFixtures.pair(
            "x5", "food", "food", null
        )
        assertEquals(
            ComparisonOutcome.BASELINE_CORRECT_EIGHT_B_UNKNOWN,
            p.outcome
        )
    }

    @Test
    fun `both unknown`() {
        val p = TestFixtures.pair(
            "x6", "food", null, null
        )
        assertEquals(ComparisonOutcome.BOTH_UNKNOWN, p.outcome)
    }

    @Test
    fun `baseline wrong and 8B unknown - no claim`() {
        val p = TestFixtures.pair(
            "x7", "food", "sports", null
        )
        assertEquals(
            ComparisonOutcome.BASELINE_WRONG_EIGHT_B_UNKNOWN,
            p.outcome
        )
    }

    @Test
    fun `8B wrong and baseline unknown - no claim`() {
        val p = TestFixtures.pair(
            "x8", "food", null, "sports"
        )
        assertEquals(
            ComparisonOutcome.EIGHT_B_WRONG_BASELINE_UNKNOWN,
            p.outcome
        )
    }

    @Test
    fun `both wrong`() {
        val p = TestFixtures.pair(
            "x9", "food", "sports", "music"
        )
        assertEquals(ComparisonOutcome.BOTH_WRONG, p.outcome)
    }

    @Test
    fun `unknown truth excluded from accuracy`() {
        val p = TestFixtures.pair(
            "x10", "food", "food", "food",
            ambiguity = GroundTruth.AMBIGUITY_UNKNOWN
        )
        assertFalse(p.eligibleForAccuracy)
    }

    @Test
    fun `of rejects correct-and-unknown contradiction`() {
        try {
            ComparisonOutcome.of(
                baselineCorrect = true,
                baselineUnknown = true,
                eightBCorrect = false,
                eightBUnknown = false
            )
            assertTrue("should have thrown", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(true)
        }
    }

    @Test
    fun `unknown truth yields UNKNOWN verdict on both sides`() {
        val p = TestFixtures.pair(
            "x11", "food", "food", "food",
            ambiguity = GroundTruth.AMBIGUITY_UNKNOWN
        )
        assertEquals(
            com.example.feedsense.model.EvaluationRecord.VERDICT_UNKNOWN,
            p.baselineUnit.result.verdict
        )
        assertEquals(
            com.example.feedsense.model.EvaluationRecord.VERDICT_UNKNOWN,
            p.eightBUnit.result.verdict
        )
    }

    @Test
    fun `builder preserves baseline record unchanged`() {
        val baseline = TestFixtures.baselineRecord("eid1", "food")
        val item = TestFixtures.item("eid1")
        val truth = TestFixtures.truth("eid1", "food")
        val pair = PairedPredictionBuilder.build(
            item = item,
            truth = truth,
            baseline = baseline,
            eightBDecision = TestFixtures.eightBDecisionFor("food"),
            eightBRecordOverride = TestFixtures.eightBRecord("eid1", "food")
        )
        // The baseline object must be the SAME immutable instance.
        assertTrue(pair.baselineRecord === baseline)
        assertNotNull(pair.baselineUnit)
        assertNotNull(pair.eightBUnit)
    }

    @Test
    fun `builder requires baseline matching the item`() {
        val baseline = TestFixtures.baselineRecord("other", "food")
        val item = TestFixtures.item("eid1")
        val truth = TestFixtures.truth("eid1", "food")
        try {
            PairedPredictionBuilder.build(
                item = item,
                truth = truth,
                baseline = baseline,
                eightBDecision = TestFixtures.eightBDecisionFor("food")
            )
            assertTrue("should have thrown", false)
        } catch (e: IllegalArgumentException) {
            assertTrue(true)
        }
    }
}
