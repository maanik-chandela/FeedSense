package com.example.feedsense.analysis.evaluation.comparative

import com.example.feedsense.model.GroundTruth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-8.
 *
 * End-to-end evaluator tests across the main comparative
 * conclusions and sample-size guards.
 */
class ComparativeEvaluatorTest {

    private fun buildPairs(
        n: Int,
        block: (Int) -> Triple<String, String?, String?>
    ): List<PairedPrediction> {
        return (0 until n).map { i ->
            val (truth, base, eight) = block(i)
            TestFixtures.pair(
                id = "eval-$i",
                truthCategory = truth,
                baselineCategory = base,
                eightBCategory = eight
            )
        }
    }

    private val cfg = ComparativeConfig.DEFAULT

    // Truth, baseline, 8B
    private fun bothCorrect(i: Int) = Triple("food", "food", "food")
    private fun improvement(i: Int) = Triple("food", "sports", "food")
    private fun regression(i: Int) = Triple("food", "food", "sports")
    private fun bothWrong(i: Int) = Triple("food", "sports", "music")
    private fun baselineUnknown8bCorrect(i: Int) =
        Triple("food", null, "food")

    @Test
    fun `8B improved - statistically significant`() {
        // 14 paired: mostly both-correct; c=10 improvements, b=0.
        val pairs = buildPairs(14) { i ->
            if (i < 2) bothCorrect(i)
            else if (i < 12) improvement(i)
            else regression(i) // b=2, c=10
        }
        val report = ComparativeEvaluator.evaluate(
            ComparativeEvaluator.Input(
                pairs = pairs,
                config = cfg,
                datasetVersion = "ds-v1",
                baselineModelVersion = "baseline-v1",
                fusionVersion = "fusion-v1",
                decisionVersion = "item-decision-v1"
            )
        )

        assertEquals(14, report.totalPaired)
        assertEquals(14, report.eligible)
        assertEquals(10, report.outcome.eightBImprovements)
        assertEquals(2, report.outcome.eightBRegressions)
        assertTrue(report.mcnemar.sufficient)
        assertNotNull(report.mcnemar.pValue)
        assertTrue(report.mcnemar.pValue!! < 0.05)
        assertEquals(
            ComparativeReport.Verdict.EIGHT_B_IMPROVED,
            report.conclusion.verdict
        )
        assertEquals("ds-v1", report.datasetVersion)
        assertEquals("item-decision-v1", report.decisionVersion)
    }

    @Test
    fun `baseline superior - significant regression`() {
        // b=10, c=0
        val pairs = buildPairs(12) { i ->
            if (i < 2) bothCorrect(i)
            else if (i < 12) regression(i)
            else bothCorrect(i)
        }
        val report = ComparativeEvaluator.evaluate(
            ComparativeEvaluator.Input(pairs = pairs, config = cfg)
        )
        assertTrue(report.mcnemar.sufficient)
        assertTrue(report.mcnemar.pValue!! < 0.05)
        assertEquals(
            ComparativeReport.Verdict.BASELINE_SUPERIOR,
            report.conclusion.verdict
        )
    }

    @Test
    fun `no significant difference when discordant symmetric`() {
        // b = 3, c = 3 -> H0 plausible, p not small.
        val pairs = buildPairs(16) { i ->
            when {
                i < 5 -> bothCorrect(i)
                i < 8 -> regression(i)   // b=3
                i < 11 -> improvement(i) // c=3
                else -> bothCorrect(i)
            }
        }
        val report = ComparativeEvaluator.evaluate(
            ComparativeEvaluator.Input(pairs = pairs, config = cfg)
        )
        assertTrue(report.mcnemar.sufficient)
        assertTrue(report.mcnemar.pValue!! >= 0.05)
        assertEquals(
            ComparativeReport.Verdict.NO_SIGNIFICANT_DIFFERENCE,
            report.conclusion.verdict
        )
    }

    @Test
    fun `insufficient real data report`() {
        // Fewer than minimumForEffectSize paired observations.
        val pairs = buildPairs(2) { i -> improvement(i) }
        val report = ComparativeEvaluator.evaluate(
            ComparativeEvaluator.Input(pairs = pairs, config = cfg)
        )
        assertEquals(2, report.totalPaired)
        assertEquals(
            ComparativeReport.Verdict
                .INSUFFICIENT_REAL_DATA_FOR_COMPARATIVE_CONCLUSIONS,
            report.conclusion.verdict
        )
        assertFalse(report.conclusion.notes.isEmpty())
    }

    @Test
    fun `insufficient discordant pairs - qualitative only`() {
        // Eligible >= min, but only 1 discordant pair.
        val pairs = buildPairs(12) { i ->
            if (i == 0) regression(i) else bothCorrect(i)
        }
        val report = ComparativeEvaluator.evaluate(
            ComparativeEvaluator.Input(pairs = pairs, config = cfg)
        )
        assertNull(report.mcnemar.pValue)
        assertEquals(
            ComparativeReport.Verdict.NO_SIGNIFICANT_DIFFERENCE,
            report.conclusion.verdict
        )
        assertTrue(report.conclusion.improvementEvidenceQualitativeOnly)
    }

    @Test
    fun `unknown truth pairs excluded from accuracy but counted in total`() {
        val known = TestFixtures.pair(
            id = "k1", truthCategory = "food",
            baselineCategory = "food", eightBCategory = "food"
        )
        val unknownTruth = TestFixtures.pair(
            id = "u1", truthCategory = "food",
            baselineCategory = "food", eightBCategory = "food",
            ambiguity = GroundTruth.AMBIGUITY_UNKNOWN
        )
        val report = ComparativeEvaluator.evaluate(
            ComparativeEvaluator.Input(pairs = listOf(known, unknownTruth))
        )
        assertEquals(2, report.totalPaired)
        assertEquals(1, report.eligible)
        assertEquals(1, report.ineligible)
    }

    @Test
    fun `abstention is not counted as accuracy for either side`() {
        // 8B abstains on all, baseline is wrong on all: both accuracy 0,
        // but 8B coverage 0 / abstention 1.
        val pairs = buildPairs(11) { i ->
            Triple("food", "sports", null)
        }
        val report = ComparativeEvaluator.evaluate(
            ComparativeEvaluator.Input(pairs = pairs, config = cfg)
        )
        assertEquals(0.0, report.overall.baseline.accuracy.value!!, 1e-9)
        assertEquals(0.0, report.overall.eightB.accuracy.value!!, 1e-9)
        assertEquals(1.0, report.overall.eightB.abstention.value!!, 1e-9)
        assertEquals(0.0, report.overall.eightB.coverage.value!!, 1e-9)
        // selective accuracy undefined (no decided) -> UNDEFINED
        assertEquals(
            com.example.feedsense.analysis.evaluation.MetricValue.State.UNDEFINED,
            report.overall.eightB.selectiveAccuracy.state
        )
    }

    @Test
    fun `coverage transition counts match outcome tally`() {
        // b=1, c=2, bothUnknown=1
        val pairs = listOf(
            TestFixtures.pair("a", "food", "food", "sports"),
            TestFixtures.pair("b", "food", "sports", "food"),
            TestFixtures.pair("c", "food", "music", "food"),
            TestFixtures.pair("d", "food", null, null)
        )
        val report = ComparativeEvaluator.evaluate(
            ComparativeEvaluator.Input(pairs = pairs)
        )
        val t = report.coverageTransition
        assertEquals(
            1,
            t.count(TransitionMatrices.Judgment.CORRECT,
                TransitionMatrices.Judgment.WRONG)
        )
        assertEquals(
            2,
            t.count(TransitionMatrices.Judgment.WRONG,
                TransitionMatrices.Judgment.CORRECT)
        )
        assertEquals(
            1,
            t.count(TransitionMatrices.Judgment.UNKNOWN,
                TransitionMatrices.Judgment.UNKNOWN)
        )
    }
}
