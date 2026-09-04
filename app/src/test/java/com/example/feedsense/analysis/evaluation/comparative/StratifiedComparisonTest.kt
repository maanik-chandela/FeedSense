package com.example.feedsense.analysis.evaluation.comparative

import com.example.feedsense.analysis.evidence.decision.DecisionState
import com.example.feedsense.analysis.evidence.temporal.ConflictLevel
import com.example.feedsense.analysis.evidence.temporal.CoverageState
import com.example.feedsense.model.FeedItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-8.
 *
 * Stratification tests: duration, platform, content type, and
 * evidence-condition groupings.
 */
class StratifiedComparisonTest {

    private val cfg = ComparativeConfig.DEFAULT

    @Test
    fun `duration buckets are correct`() {
        assertEquals(DurationBuckets.Bucket.L5, DurationBuckets.of(5))
        assertEquals(DurationBuckets.Bucket.M10, DurationBuckets.of(10))
        assertEquals(DurationBuckets.Bucket.M30, DurationBuckets.of(30))
        assertEquals(DurationBuckets.Bucket.L90, DurationBuckets.of(60))
        assertEquals(DurationBuckets.Bucket.XL, DurationBuckets.of(120))
        assertEquals(DurationBuckets.Bucket.UNKNOWN, DurationBuckets.of(null))
    }

    @Test
    fun `duration stratification groups by bucket`() {
        val pairs = listOf(
            TestFixtures.pair("d1", "food", "food", "food", durationSeconds = 4),
            TestFixtures.pair("d2", "food", "food", "food", durationSeconds = 100),
            TestFixtures.pair("d3", "food", "food", "food", durationSeconds = 4)
        )
        val table = StratifiedComparison.all(pairs, cfg)
        val l5 = table.duration["<=5s"]
        val xl = table.duration[">90s"]
        assertTrue(l5 != null && l5.eligible == 2)
        assertTrue(xl != null && xl.eligible == 1)
    }

    @Test
    fun `content type and platform stratify`() {
        val pairs = listOf(
            TestFixtures.pair("c1", "food", "food", "food",
                contentType = FeedItem.CONTENT_SHORT_VIDEO, platform = "reels"),
            TestFixtures.pair("c2", "food", "food", "food",
                contentType = FeedItem.CONTENT_LONG_VIDEO, platform = "shorts")
        )
        val table = StratifiedComparison.all(pairs, cfg)
        assertEquals(1, table.contentType[FeedItem.CONTENT_SHORT_VIDEO]?.eligible)
        assertEquals(1, table.contentType[FeedItem.CONTENT_LONG_VIDEO]?.eligible)
        assertEquals(1, table.platform["reels"]?.eligible)
        assertEquals(1, table.platform["shorts"]?.eligible)
    }

    @Test
    fun `evidence coverage stratifies`() {
        val high = TestFixtures.pair("e1", "food", "food", "food",
            eightBDecision = TestFixtures.eightBDecision(
                itemId = "e1", primaryCategory = "food",
                coverage = CoverageState.HIGH_COVERAGE))
        val low = TestFixtures.pair("e2", "food", "food", "food",
            eightBDecision = TestFixtures.eightBDecision(
                itemId = "e2", primaryCategory = "food",
                coverage = CoverageState.LOW_COVERAGE))
        val table = StratifiedComparison.all(listOf(high, low), cfg)
        assertEquals(1, table.evidenceCoverage["HIGH_COVERAGE"]?.eligible)
        assertEquals(1, table.evidenceCoverage["LOW_COVERAGE"]?.eligible)
    }

    @Test
    fun `ocr evidence stratifies`() {
        val withOcr = TestFixtures.pair("o1", "food", "food", "food",
            eightBDecision = TestFixtures.eightBDecision(
                itemId = "o1", primaryCategory = "food",
                evidenceTypes = listOf("ocr")))
        val noOcr = TestFixtures.pair("o2", "food", "food", "food",
            eightBDecision = TestFixtures.eightBDecision(
                itemId = "o2", primaryCategory = "food",
                evidenceTypes = listOf("frame_color")))
        val table = StratifiedComparison.all(listOf(withOcr, noOcr), cfg)
        assertEquals(1, table.hasOcr["OCR_PRESENT"]?.eligible)
        assertEquals(1, table.hasOcr["OCR_ABSENT"]?.eligible)
    }

    @Test
    fun `temporal conflict stratifies`() {
        val conflict = TestFixtures.pair("f1", "food", "food", "food",
            eightBDecision = TestFixtures.eightBDecision(
                itemId = "f1", primaryCategory = "food",
                conflict = ConflictLevel.HIGH))
        val clean = TestFixtures.pair("f2", "food", "food", "food",
            eightBDecision = TestFixtures.eightBDecision(
                itemId = "f2", primaryCategory = "food",
                conflict = ConflictLevel.NONE))
        val table = StratifiedComparison.all(listOf(conflict, clean), cfg)
        assertEquals(1, table.temporalConflict["CONFLICT"]?.eligible)
        assertEquals(1, table.temporalConflict["NO_CONFLICT"]?.eligible)
    }

    @Test
    fun `8B decision state stratifies abstention`() {
        val decided = TestFixtures.pair("s1", "food", "food", "food",
            eightBDecision = TestFixtures.eightBDecision(
                itemId = "s1", primaryCategory = "food",
                decisionState = DecisionState.DECIDED))
        val abstained = TestFixtures.pair("s2", "food", "food", null,
            eightBDecision = TestFixtures.eightBDecision(
                itemId = "s2", primaryCategory = null,
                decisionState = DecisionState.INSUFFICIENT_EVIDENCE))
        val table = StratifiedComparison.all(listOf(decided, abstained), cfg)
        assertEquals(1, table.eightBDecisionState["DECIDED"]?.eligible)
        assertEquals(1,
            table.eightBDecisionState["INSUFFICIENT_EVIDENCE"]?.eligible)
    }

    @Test
    fun `tiny stratum is insufficient not silent`() {
        // minimumStratumSupport = 1 by default; a cell of 1 is sufficient.
        val single = TestFixtures.pair("z1", "food", "food", "food")
        val table = StratifiedComparison.all(listOf(single), cfg)
        val cell = table.duration.values.first()
        assertTrue(cell.sufficient)
    }

    @Test
    fun `insufficient stratum guard blocks metrics`() {
        val cfg2 = ComparativeConfig(
            minimumStratumSupport = 10
        )
        val single = TestFixtures.pair("y1", "food", "food", "food")
        val table = StratifiedComparison.all(listOf(single), cfg2)
        val cell = table.duration.values.first()
        assertFalse(cell.sufficient)
        assertEquals(null, cell.metrics)
    }
}
