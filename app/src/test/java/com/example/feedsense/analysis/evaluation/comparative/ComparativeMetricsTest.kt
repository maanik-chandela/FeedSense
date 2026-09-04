package com.example.feedsense.analysis.evaluation.comparative

import com.example.feedsense.analysis.evaluation.MetricValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-8.
 *
 * Metrics, per-category, confusion and transition matrix tests.
 */
class ComparativeMetricsTest {

    private val cfg = ComparativeConfig.DEFAULT

    @Test
    fun `outcome tally counts all outcomes`() {
        val pairs = listOf(
            TestFixtures.pair("1", "food", "food", "food"),       // both correct
            TestFixtures.pair("2", "food", "sports", "music"),    // both wrong
            TestFixtures.pair("3", "food", "food", "sports"),     // base only
            TestFixtures.pair("4", "food", "sports", "food"),     // 8b only
            TestFixtures.pair("5", "food", null, null),           // both unknown
            TestFixtures.pair("6", "food", null, "food"),         // base unknown 8b correct
            TestFixtures.pair("7", "food", "food", null),         // base correct 8b unknown
            TestFixtures.pair("8", "food", "sports", null),       // base wrong 8b unknown
            TestFixtures.pair("9", "food", null, "sports")        // 8b wrong base unknown
        )
        val tally = ComparativeMetrics.tally(pairs)
        assertEquals(9, tally.paired)
        assertEquals(9, tally.eligible)
        assertEquals(1, tally.bothCorrect)
        assertEquals(1, tally.bothWrong)
        assertEquals(1, tally.baselineOnlyCorrect)
        assertEquals(1, tally.eightBOnlyCorrect)
        assertEquals(1, tally.bothUnknown)
        assertEquals(1, tally.baselineUnknownEightBCorrect)
        assertEquals(1, tally.baselineCorrectEightBUnknown)
        assertEquals(1, tally.baselineWrongEightBUnknown)
        assertEquals(1, tally.eightBWrongBaselineUnknown)
        // improvements = 8b-only + base-unknown-8b-correct = 2
        assertEquals(2, tally.eightBImprovements)
        assertEquals(1, tally.eightBRegressions)
    }

    @Test
    fun `system metrics compute accuracy coverage selective`() {
        // 4 correct, 1 not-correct, 1 unknown over 6 eligible.
        val m = ComparativeMetrics.systemMetrics(
            correct = 4,
            notCorrect = 1,
            unknown = 1,
            minConfidence = 1
        )
        assertEquals(6, m.eligible)
        assertEquals(4.0 / 6.0, m.accuracy.value!!, 1e-9)
        assertEquals(5.0 / 6.0, m.coverage.value!!, 1e-9)
        assertEquals(1.0 / 6.0, m.abstention.value!!, 1e-9)
        assertEquals(4.0 / 5.0, m.selectiveAccuracy.value!!, 1e-9)
        assertEquals(MetricValue.State.DEFINED, m.accuracy.state)
    }

    @Test
    fun `both systems computed over identical eligible population`() {
        val pairs = listOf(
            TestFixtures.pair("1", "food", "food", "food"),
            TestFixtures.pair("2", "food", "sports", "food"),
            TestFixtures.pair("3", "food", "food", "sports")
        )
        val bs = ComparativeMetrics.bothSystems(pairs, 1)
        assertEquals(3, bs.eligible)
        assertEquals(2.0 / 3.0, bs.baseline.accuracy.value!!, 1e-9)
        assertEquals(2.0 / 3.0, bs.eightB.accuracy.value!!, 1e-9)
    }

    @Test
    fun `per category precision recall f1`() {
        val pairs = listOf(
            TestFixtures.pair("1", "food", "food", "food"),
            TestFixtures.pair("2", "food", "sports", "food"),
            TestFixtures.pair("3", "sports", "sports", "sports")
        )
        // baseline: food tp=1 (item1), fp=0, fn=1 (item2);
        // sports tp=1 (item3), fp=1 (item2), fn=0
        val base = ComparativeMetrics.perCategory(pairs, true, 1)
        val foodB = base["food"]
        assertTrue(foodB is ComparativeMetrics.PerCategoryEntry.Defined)
        val d = foodB as ComparativeMetrics.PerCategoryEntry.Defined
        assertEquals(1, d.tp)
        assertEquals(0, d.fp)
        assertEquals(1, d.fn)
        assertEquals(1.0, d.precision.value!!, 1e-9)
        assertEquals(0.5, d.recall.value!!, 1e-9)
    }

    @Test
    fun `class confusion matrix`() {
        val pairs = listOf(
            TestFixtures.pair("1", "food", "food", "food"),
            TestFixtures.pair("2", "food", "sports", "food"),
            TestFixtures.pair("3", "sports", "sports", "sports")
        )
        val conf = TransitionMatrices.classConfusion(pairs, true)
        assertEquals(1, conf.count("food", "food")) // includes pair3? no
        // food row: pair1 food->food, pair2 food->sports
        assertEquals(2, conf.rowTotals["food"])
        assertEquals(1, conf.count("food", "sports"))
        assertEquals(0, conf.count("food", "music"))
    }

    @Test
    fun `coverage transition square and sums`() {
        val pairs = listOf(
            TestFixtures.pair("1", "food", "food", "food"),
            TestFixtures.pair("2", "food", "sports", "food"),
            TestFixtures.pair("3", "food", null, null)
        )
        val t = TransitionMatrices.coverageTransition(pairs)
        assertEquals(3, t.eligible)
        assertEquals(1, t.count(
            TransitionMatrices.Judgment.CORRECT,
            TransitionMatrices.Judgment.CORRECT))
        assertEquals(1, t.count(
            TransitionMatrices.Judgment.WRONG,
            TransitionMatrices.Judgment.CORRECT))
        assertEquals(1, t.count(
            TransitionMatrices.Judgment.UNKNOWN,
            TransitionMatrices.Judgment.UNKNOWN))
    }
}
