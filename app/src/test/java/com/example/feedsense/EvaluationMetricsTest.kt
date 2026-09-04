package com.example.feedsense

import com.example.feedsense.analysis.evaluation.BinaryMetrics
import com.example.feedsense.analysis.evaluation.CalibrationMetrics
import com.example.feedsense.analysis.evaluation.CategoryMetrics
import com.example.feedsense.analysis.evaluation.DurationMetrics
import com.example.feedsense.analysis.evaluation.InteractionMetrics
import com.example.feedsense.analysis.evaluation.MetricValue
import com.example.feedsense.analysis.evaluation.MultiLabelMetrics
import com.example.feedsense.analysis.evaluation.MultiLabelSample
import com.example.feedsense.analysis.evaluation.SkipMetrics
import com.example.feedsense.analysis.evaluation.TopicToneMetrics
import com.example.feedsense.analysis.evaluation.WilsonInterval
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8A-3. Deterministic metric-calculator tests:
 * known-values, invariants and the INSUFFICIENT_SAMPLE_SIZE /
 * NOT_APPLICABLE / UNDEFINED honesty semantics.
 */
class EvaluationMetricsTest {

    // --------------------------------
    // WILSON
    // --------------------------------

    @Test
    fun wilson_perfectProportionDoesNotCollapseToZero() {
        val ci = WilsonInterval.forProportion(successes = 5, total = 5, confidenceLevel = 0.95)!!
        // A 5/5 estimate must have a non-trivial lower bound.
        assertTrue("lower bound > 0", ci.lower > 0.0)
        assertEquals(1.0, ci.upper, 0.0001)
    }

    @Test
    fun wilson_symmetricCenterAtHalf() {
        val ci = WilsonInterval.forProportion(successes = 50, total = 100)!!
        assertEquals(0.5, (ci.lower + ci.upper) / 2.0, 0.01)
    }

    @Test
    fun wilson_zeroTotal_returnsNull() {
        assertNull(WilsonInterval.forProportion(0, 0))
    }

    // --------------------------------
    // BINARY
    // --------------------------------

    @Test
    fun binary_perfectClassification() {
        val m = BinaryMetrics.compute(
            listOf(
                true to true,
                true to true,
                false to false,
                false to false
            )
        )
        assertEquals(2, m.tp)
        assertEquals(0, m.fp)
        assertEquals(0, m.fn)
        assertEquals(2, m.tn)
        assertEquals(1.0, m.precision!!.value!!, 1e-9)
        assertEquals(1.0, m.recall!!.value!!, 1e-9)
        assertEquals(1.0, m.f1!!.value!!, 1e-9)
    }

    @Test
    fun binary_withKnownConfusion() {
        // tp=3 fp=1 fn=2 tn=4
        val m = BinaryMetrics.compute(
            listOf(
                true to true,
                true to true,
                true to true,
                true to false,
                false to true,
                false to true,
                false to false,
                false to false,
                false to false,
                false to false
            )
        )
        assertEquals(3, m.tp)
        assertEquals(1, m.fp)
        assertEquals(2, m.fn)
        assertEquals(4, m.tn)
        assertEquals(0.75, m.precision!!.value!!, 1e-9)   // 3/4
        assertEquals(0.6, m.recall!!.value!!, 1e-9)        // 3/5
        assertEquals(2.0 * 0.75 * 0.6 / 1.35, m.f1!!.value!!, 1e-9)
    }

    @Test
    fun binary_noPositivePrediction_precisionNotApplicable() {
        val m = BinaryMetrics.compute(listOf(false to true, false to true))
        assertEquals(MetricValue.State.NOT_APPLICABLE, m.precision!!.state)
        assertEquals(2, m.fn)
        assertEquals(0, m.tp)
    }

    @Test
    fun binary_belowMinimumSample_insufficient() {
        val m = BinaryMetrics.compute(
            pairs = listOf(true to true),
            minimumSample = 5
        )
        assertEquals(MetricValue.State.INSUFFICIENT_SAMPLE_SIZE, m.recall!!.state)
    }

    @Test
    fun binary_empty_noMetricValues() {
        // An empty sample set must NOT fabricate a zero-accuracy
        // or a NOT_APPLICABLE-able value; we expose no data at all.
        val m = BinaryMetrics.compute(emptyList())
        assertNull(m.accuracy)
        assertNull(m.precision)
        assertNull(m.recall)
        assertNull(m.f1)
        assertEquals(0, m.tp)
        assertEquals(0, m.fn)
    }

    // --------------------------------
    // CATEGORY (multiclass)
    // --------------------------------

    @Test
    fun category_knownPerClassAndConfusion() {
        // 3 items all truth=sports; AI predicts sports, comedy, sports
        val m = CategoryMetrics.compute(
            pairs = listOf(
                "sports" to "sports",
                "comedy" to "sports",
                "sports" to "sports"
            ),
            classLabels = listOf("sports", "comedy")
        )
        val sports = m.perClass.first { it.label == "sports" }
        assertEquals(2, sports.tp)
        assertEquals(0, sports.fp)
        assertEquals(1, sports.fn)
        assertEquals(3, sports.support)
        assertTrue(sports.recall.defined)
        assertEquals(2.0 / 3.0, sports.recall.value!!, 1e-9)

        val comedy = m.perClass.first { it.label == "comedy" }
        assertEquals(0, comedy.tp)
        assertEquals(1, comedy.fp)
        assertEquals(0, comedy.support)

        // Micro accuracy = 2 correct / 3 samples
        assertEquals(2.0 / 3.0, m.aggregates.microAccuracy.value!!, 1e-9)

        // Confusion: predicted sports & truth sports = 2
        assertEquals(2, m.confusionMatrix.countOf("sports", "sports"))
        assertEquals(1, m.confusionMatrix.countOf("comedy", "sports"))
    }

    @Test
    fun category_emptyPairs_microUndefined() {
        val m = CategoryMetrics.compute(emptyList(), listOf("sports"))
        assertEquals(MetricValue.State.UNDEFINED, m.aggregates.microAccuracy.state)
    }

    @Test
    fun category_absenceIsNotFakeZero() {
        // Truth is only comedy; AI predicts comedy. sports should
        // be NOT_APPLICABLE, never reported as 0 accuracy.
        val m = CategoryMetrics.compute(
            pairs = listOf("comedy" to "comedy"),
            classLabels = listOf("sports", "comedy")
        )
        val sports = m.perClass.first { it.label == "sports" }
        assertEquals(MetricValue.State.NOT_APPLICABLE, sports.recall.state)
        assertEquals(MetricValue.State.NOT_APPLICABLE, sports.precision.state)
        assertFalse(sports.recall.defined)
    }

    // --------------------------------
    // MULTI-LABEL
    // --------------------------------

    @Test
    fun multiLabel_exactMatchAndMicro() {
        val m = MultiLabelMetrics.compute(
            listOf(
                MultiLabelSample(setOf("sports"), setOf("sports")),
                MultiLabelSample(setOf("sports"), setOf("comedy")),
                MultiLabelSample(setOf("sports", "comedy"), setOf("sports"))
            )
        )
        // exact match only item 1
        assertEquals(1.0 / 3.0, m.exactMatch.value!!, 1e-9)
        // item3: predicted {sports,comedy} truth {sports} => tp=1 fp=1
        // micro tp = 1 + 0 + 1 = 2, fp = 0 + 1 + 1 = 2, fn = 0 + 1 + 0 = 1
        assertEquals(2.0 / 4.0, m.microPrecision.value!!, 1e-9)
        assertEquals(2.0 / 3.0, m.microRecall.value!!, 1e-9)
    }

    @Test
    fun multiLabel_empty_undefined() {
        val m = MultiLabelMetrics.compute(emptyList())
        assertEquals(MetricValue.State.UNDEFINED, m.exactMatch.state)
    }

    // --------------------------------
    // DURATION
    // --------------------------------

    @Test
    fun duration_knownValues() {
        // predicted, truth: (10,5)=+5, (10,15)=-5, (10,10)=0, (12,10)=2
        val m = DurationMetrics.compute(
            listOf(10 to 5, 10 to 15, 10 to 10, 12 to 10)
        )
        // abs errors: [5,5,0,2] => sum=12 mean=3
        assertEquals(3.0, m.meanAbsoluteError.value!!, 1e-9)
        // mae.numerator is abs sum
        assertEquals(12, m.meanAbsoluteError.numerator)
        // signed: [5,-5,0,2] sum=2 mean=0.5 bias
        assertEquals(0.5, m.bias.value!!, 1e-9)
        // within 5s: all 4
        assertEquals(4, m.withinTolerances[5]!!.numerator)
        assertEquals(1.0, m.withinTolerances[5]!!.value!!, 1e-9)
        // within 1s: only the 0 error
        assertEquals(1, m.withinTolerances[1]!!.numerator)
    }

    @Test
    fun duration_empty_undefined() {
        val m = DurationMetrics.compute(emptyList())
        assertEquals(MetricValue.State.UNDEFINED, m.meanAbsoluteError.state)
        assertEquals(0, m.sampleCount)
    }

    // --------------------------------
    // SKIP (tri-state)
    // --------------------------------

    @Test
    fun skip_unknownTruthIsNotCoercedToFalse() {
        val m = SkipMetrics.compute(
            listOf(
                true to null,   // UNKNOWN - excluded
                true to true,   // TP
                false to false  // TN
            )
        )
        assertEquals(1, m.unknownTruth)
        assertEquals(2, m.decided)
        assertEquals(3, m.total)
        assertEquals(1, m.binary.tp)
        assertEquals(1, m.binary.tn)
    }

    // --------------------------------
    // INTERACTION (per-signal)
    // --------------------------------

    @Test
    fun interaction_perSignalMetricsAndUnknown() {
        val m = InteractionMetrics.compute(
            listOf(
                // predicted signals, truth map
                setOf("liked") to
                    mapOf("liked" to true, "commented" to null),
                setOf<String>() to
                    mapOf("liked" to false, "commented" to true)
            )
        )
        val liked = m.signals.first { it.signal == "liked" }
        assertEquals(1, liked.binary.tp)
        assertEquals(1, liked.binary.tn)
        val commented = m.signals.first { it.signal == "commented" }
        assertEquals(1, commented.unknown) // one item UNKNOWN
        assertEquals(1, commented.decided)
        assertEquals(0, commented.binary.tp)
        assertEquals(1, commented.binary.fn) // truth true, predicted false
    }

    // --------------------------------
    // TOPIC / TONE
    // --------------------------------

    @Test
    fun topicTone_knownAgreement() {
        val m = TopicToneMetrics.compute(
            listOf(true to true, false to true, true to true),
            dimension = "topic"
        )
        assertEquals(2.0 / 3.0, m.agreement.value!!, 1e-9)
        assertEquals(3, m.decided)
        assertEquals(0, m.unknown)
    }

    @Test
    fun topicTone_noTruthValue_notApplicable() {
        val m = TopicToneMetrics.compute(
            listOf(false to false, false to false),
            dimension = "topic"
        )
        assertEquals(MetricValue.State.NOT_APPLICABLE, m.agreement.state)
        assertEquals(2, m.unknown)
    }

    // --------------------------------
    // CALIBRATION / ECE
    // --------------------------------

    @Test
    fun calibration_knownEceIsHandComputed() {
        // 3 samples @0.90 (all correct) -> bucket [0.9,1.0),
        //    accuracy 1.0, meanConf 0.9   => delta 0.1
        // 2 samples @0.00 (all wrong)  -> bucket [0.0,0.1),
        //    accuracy 0.0, meanConf 0.0   => delta 0.0
        // ECE = (3/5)*0.1 + (2/5)*0.0 = 0.06
        val pairs = buildList {
            repeat(3) { add(0.9 to true) }
            repeat(2) { add(0.0 to false) }
        }
        val m = CalibrationMetrics.compute(pairs, bucketWidth = 0.1)
        assertTrue(m.ece.defined)
        assertEquals(0.06, m.ece.value!!, 1e-9)
        val high = m.buckets.first { it.lower >= 0.9 }
        assertEquals(3, high.sampleCount)
        assertEquals(1.0, high.accuracy.value!!, 1e-9)
    }

    @Test
    fun calibration_insufficientSamples_eceInsufficient() {
        val m = CalibrationMetrics.compute(
            listOf(0.9 to true, 0.9 to false),
            bucketWidth = 0.1
        )
        assertEquals(MetricValue.State.INSUFFICIENT_SAMPLE_SIZE, m.ece.state)
    }

    @Test
    fun calibration_empty_notApplicable() {
        val m = CalibrationMetrics.compute(emptyList())
        assertEquals(MetricValue.State.NOT_APPLICABLE, m.ece.state)
    }
}