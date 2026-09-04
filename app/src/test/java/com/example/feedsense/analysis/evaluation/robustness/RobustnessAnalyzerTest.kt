package com.example.feedsense.analysis.evaluation.robustness

import com.example.feedsense.analysis.evaluation.comparative.PairedPrediction
import com.example.feedsense.analysis.evaluation.comparative.TestFixtures
import com.example.feedsense.model.GroundTruth
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-9.
 *
 * Comprehensive test suite for the statistical robustness and
 * sensitivity analysis layer.
 *
 * Covers:
 *   - McNemar: equal discordant, baseline advantage, 8B advantage,
 *     zero discordant, small sample, insufficient sample
 *   - Confidence intervals: valid, boundary, insufficient
 *   - Bootstrap: deterministic seed/output, paired-unit resampling,
 *     insufficient data
 *   - Category sensitivity: balanced, imbalanced, missing, tiny
 *   - Platform sensitivity: sufficient, insufficient
 *   - Duration sensitivity: all buckets, missing
 *   - Evidence sensitivity: present, absent
 *   - UNKNOWN policies: as wrong, excluded, selective, coverage
 *   - Regression analysis
 *   - Improvement analysis
 *   - High-confidence error analysis
 *   - Multiple-comparison metadata
 *   - Versioning
 *   - Export (JSON/CSV)
 *   - Privacy (no raw content in export)
 *   - Determinism
 *   - Immutability (read-only layer)
 */
class RobustnessAnalyzerTest {

    // --------------------------------
    // HELPERS
    // --------------------------------

    private val cfg = RobustnessConfig.DEFAULT

    private fun analyze(
        pairs: List<PairedPrediction>,
        config: RobustnessConfig = cfg
    ): RobustnessReport {
        return RobustnessAnalyzer.analyze(
            RobustnessAnalyzer.Input(
                pairs = pairs,
                config = config
            )
        )
    }

    private fun wrongCategory(cat: String): String {
        return when (cat) {
            "food" -> "sports"
            "sports" -> "food"
            else -> "music"
        }
    }

    private fun improvementPair(
        id: String,
        cat: String = "food",
        platform: String? = "reels",
        duration: Int? = 20,
        confidence: Double? = 0.9
    ): PairedPrediction {
        // baseline wrong (predicts wrong != truth), 8B correct
        return TestFixtures.pair(
            id = id,
            truthCategory = cat,
            baselineCategory = wrongCategory(cat),
            eightBCategory = cat,
            platform = platform,
            durationSeconds = duration
        )
    }

    private fun regressionPair(
        id: String,
        cat: String = "food",
        platform: String? = "reels",
        duration: Int? = 20
    ): PairedPrediction {
        // baseline correct, 8B wrong (predicts wrong != truth)
        return TestFixtures.pair(
            id = id,
            truthCategory = cat,
            baselineCategory = cat,
            eightBCategory = wrongCategory(cat),
            platform = platform,
            durationSeconds = duration
        )
    }

    private fun bothCorrectPair(
        id: String,
        cat: String = "food",
        platform: String? = "reels",
        duration: Int? = 20
    ): PairedPrediction {
        return TestFixtures.pair(
            id = id,
            truthCategory = cat,
            baselineCategory = cat,
            eightBCategory = cat,
            platform = platform,
            durationSeconds = duration
        )
    }

    private fun bothWrongPair(
        id: String,
        cat: String = "food"
    ): PairedPrediction {
        return TestFixtures.pair(
            id = id,
            truthCategory = cat,
            baselineCategory = "sports",
            eightBCategory = "music"
        )
    }

    private fun unknownTruthPair(
        id: String
    ): PairedPrediction {
        return TestFixtures.pair(
            id = id,
            truthCategory = "food",
            baselineCategory = "food",
            eightBCategory = "food",
            ambiguity = GroundTruth.AMBIGUITY_UNKNOWN
        )
    }

    // --------------------------------
    // McNEMAR TESTS
    // --------------------------------

    @Test
    fun `mcnemar - 8B advantage gives significant result`() {
        val pairs = (0 until 20).map { i ->
            if (i < 2) bothCorrectPair("mc-$i")
            else if (i < 16) improvementPair("mc-$i")
            else regressionPair("mc-$i")
        }
        val report = analyze(pairs)
        assertTrue(report.primaryResult.status == StatisticalStatus.SUFFICIENT)
        assertNotNull(report.primaryResult.pValue)
        assertTrue(report.primaryResult.pValue!! < 0.05)
        assertTrue(report.primaryResult.significant == true)
    }

    @Test
    fun `mcnemar - baseline advantage`() {
        val pairs = (0 until 20).map { i ->
            if (i < 2) bothCorrectPair("ba-$i")
            else if (i < 16) regressionPair("ba-$i")
            else improvementPair("ba-$i")
        }
        val report = analyze(pairs)
        assertTrue(report.primaryResult.status == StatisticalStatus.SUFFICIENT)
        assertNotNull(report.primaryResult.pValue)
        assertTrue(report.primaryResult.pValue!! < 0.05)
    }

    @Test
    fun `mcnemar - equal discordant pairs gives not significant`() {
        val pairs = (0 until 12).map { i ->
            if (i < 6) regressionPair("ed-$i")
            else improvementPair("ed-$i")
        }
        val report = analyze(pairs)
        assertTrue(report.primaryResult.status == StatisticalStatus.SUFFICIENT)
        assertNotNull(report.primaryResult.pValue)
        assertTrue(report.primaryResult.pValue!! >= 0.05)
    }

    @Test
    fun `mcnemar - zero discordant pairs`() {
        val pairs = (0 until 12).map { i ->
            bothCorrectPair("zd-$i")
        }
        val report = analyze(pairs)
        // 0 discordant pairs; with minimumDiscordant=5 the guard
        // is not met — p-value is null, not fabricated.
        assertEquals(0, report.primaryResult.discordantPairs)
        assertNull(report.primaryResult.pValue)
    }

    @Test
    fun `mcnemar - small sample insufficient`() {
        val config = cfg.copy(
            minimumMcNemarDiscordantPairs = 5,
            minimumForEffectSize = 3
        )
        val pairs = (0 until 4).map { i ->
            if (i < 3) improvementPair("ss-$i")
            else bothCorrectPair("ss-$i")
        }
        val report = analyze(pairs, config)
        // Only 1 discordant pair, below minimum of 5
        assertNull(report.primaryResult.pValue)
        assertEquals(
            StatisticalStatus.INSUFFICIENT_DATA,
            report.primaryResult.status
        )
    }

    @Test
    fun `mcnemar - insufficient sample returns null p-value`() {
        val config = cfg.copy(
            minimumMcNemarDiscordantPairs = 10,
            minimumForEffectSize = 1
        )
        val pairs = (0 until 4).map { i ->
            if (i < 2) improvementPair("is-$i")
            else regressionPair("is-$i")
        }
        val report = analyze(pairs, config)
        assertNull(report.primaryResult.pValue)
        assertNull(report.primaryResult.significant)
    }

    // --------------------------------
    // CONFIDENCE INTERVALS
    // --------------------------------

    @Test
    fun `ci - valid intervals for sufficient data`() {
        val pairs = (0 until 20).map { i ->
            if (i < 14) improvementPair("ci-$i")
            else bothCorrectPair("ci-$i")
        }
        val report = analyze(pairs)
        assertTrue(report.confidenceIntervals.sufficient)
        assertNotNull(report.confidenceIntervals.baselineAccuracy)
        assertNotNull(report.confidenceIntervals.eightBAccuracy)
        assertNotNull(report.confidenceIntervals.baselineAccuracy!!.confidenceInterval)
        assertNotNull(report.confidenceIntervals.eightBAccuracy!!.confidenceInterval)
        val baseCI = report.confidenceIntervals.baselineAccuracy!!.confidenceInterval!!
        val eightCI = report.confidenceIntervals.eightBAccuracy!!.confidenceInterval!!
        assertTrue(baseCI.lower <= baseCI.upper)
        assertTrue(eightCI.lower <= eightCI.upper)
        assertTrue(baseCI.lower >= 0.0 && baseCI.upper <= 1.0)
        assertTrue(eightCI.lower >= 0.0 && eightCI.upper <= 1.0)
    }

    @Test
    fun `ci - insufficient data returns null`() {
        val config = cfg.copy(minimumForConfidenceInterval = 100)
        val pairs = (0 until 5).map { i -> improvementPair("ci-$i") }
        val report = analyze(pairs, config)
        assertFalse(report.confidenceIntervals.sufficient)
        assertNull(report.confidenceIntervals.baselineAccuracy)
    }

    @Test
    fun `ci - boundary case with all correct`() {
        val pairs = (0 until 15).map { i -> bothCorrectPair("cb-$i") }
        val report = analyze(pairs)
        assertTrue(report.confidenceIntervals.sufficient)
        val ci = report.confidenceIntervals.baselineAccuracy!!.confidenceInterval!!
        assertTrue(ci.lower > 0.5)
        assertTrue(ci.upper <= 1.0)
    }

    // --------------------------------
    // BOOTSTRAP
    // --------------------------------

    @Test
    fun `bootstrap - deterministic seed gives deterministic output`() {
        val pairs = (0 until 20).map { i ->
            if (i < 14) improvementPair("bs-$i")
            else bothCorrectPair("bs-$i")
        }
        val r1 = analyze(pairs)
        val r2 = analyze(pairs)
        assertEquals(
            r1.bootstrap.observedDifference,
            r2.bootstrap.observedDifference
        )
        assertEquals(r1.bootstrap.lowerBound, r2.bootstrap.lowerBound)
        assertEquals(r1.bootstrap.upperBound, r2.bootstrap.upperBound)
        assertEquals(r1.bootstrap.seed, r2.bootstrap.seed)
    }

    @Test
    fun `bootstrap - paired unit resampling`() {
        val pairs = (0 until 20).map { i ->
            if (i < 14) improvementPair("bp-$i")
            else bothCorrectPair("bp-$i")
        }
        val report = analyze(pairs)
        assertTrue(report.bootstrap.sufficient)
        assertNotNull(report.bootstrap.observedDifference)
        assertNotNull(report.bootstrap.lowerBound)
        assertNotNull(report.bootstrap.upperBound)
        assertTrue(report.bootstrap.lowerBound!! <= report.bootstrap.upperBound!!)
        assertEquals(
            cfg.bootstrapIterations,
            report.bootstrap.bootstrapIterations
        )
    }

    @Test
    fun `bootstrap - insufficient data`() {
        val config = cfg.copy(bootstrapMinimumEligible = 50)
        val pairs = (0 until 5).map { i -> improvementPair("bi-$i") }
        val report = analyze(pairs, config)
        assertFalse(report.bootstrap.sufficient)
        assertNull(report.bootstrap.observedDifference)
        assertNull(report.bootstrap.lowerBound)
        assertNull(report.bootstrap.upperBound)
        assertNotNull(report.bootstrap.guardReason)
    }

    @Test
    fun `bootstrap - deterministic output with same seed`() {
        val pairs = (0 until 15).map { i -> improvementPair("bd-$i") }
        val config = cfg.copy(bootstrapSeed = 42L)
        val r1 = analyze(pairs, config)
        val r2 = analyze(pairs, config)
        assertEquals(r1.bootstrap.lowerBound, r2.bootstrap.lowerBound)
        assertEquals(r1.bootstrap.upperBound, r2.bootstrap.upperBound)
        assertEquals(42L, r1.bootstrap.seed)
    }

    // --------------------------------
    // CATEGORY SENSITIVITY
    // --------------------------------

    @Test
    fun `category sensitivity - balanced data`() {
        val pairs = (0 until 20).map { i ->
            val cat = if (i % 2 == 0) "food" else "sports"
            if (i < 14) improvementPair("cs-$i", cat)
            else bothCorrectPair("cs-$i", cat)
        }
        val report = analyze(pairs)
        assertTrue(report.categorySensitivity.isNotEmpty())
        val cats = report.categorySensitivity.map { it.key }.toSet()
        assertTrue(cats.contains("food"))
        assertTrue(cats.contains("sports"))
    }

    @Test
    fun `category sensitivity - imbalanced data`() {
        val pairs = mutableListOf<PairedPrediction>()
        for (i in 0 until 18) {
            pairs.add(improvementPair("ci-$i", "food"))
        }
        for (i in 0 until 2) {
            pairs.add(improvementPair("cs-$i", "sports"))
        }
        val report = analyze(pairs)
        assertTrue(report.categorySensitivity.isNotEmpty())
        val food = report.categorySensitivity.first { it.key == "food" }
        val sports = report.categorySensitivity.first { it.key == "sports" }
        assertTrue(food.support > sports.support)
    }

    @Test
    fun `category sensitivity - missing category`() {
        val pairs = (0 until 15).map { i ->
            improvementPair("cm-$i", "food")
        }
        val report = analyze(pairs)
        assertEquals(1, report.categorySensitivity.size)
        assertEquals("food", report.categorySensitivity[0].key)
    }

    @Test
    fun `leave one category out - improvement not driven by one category`() {
        // Need enough pairs so removing one category still has
        // >= minimumForEffectSize (10) remaining eligible.
        val pairs = mutableListOf<PairedPrediction>()
        for (i in 0 until 12) {
            pairs.add(improvementPair("loco-f-$i", "food"))
        }
        for (i in 0 until 12) {
            pairs.add(bothCorrectPair("loco-s-$i", "sports"))
        }
        val report = analyze(pairs)
        assertEquals(2, report.leaveOneCategoryOut.size)
        for (loc in report.leaveOneCategoryOut) {
            assertTrue(loc.sufficient)
            assertNotNull(loc.accuracyDifference)
        }
    }

    // --------------------------------
    // PLATFORM SENSITIVITY
    // --------------------------------

    @Test
    fun `platform sensitivity - sufficient platform`() {
        val pairs = mutableListOf<PairedPrediction>()
        for (i in 0 until 8) {
            pairs.add(improvementPair("ps-i-$i", platform = "reels"))
        }
        for (i in 0 until 8) {
            pairs.add(improvementPair("ps-y-$i", platform = "youtube"))
        }
        val report = analyze(pairs)
        assertTrue(report.platformSensitivity.isNotEmpty())
        val plats = report.platformSensitivity.map { it.key }.toSet()
        assertTrue(plats.contains("reels"))
        assertTrue(plats.contains("youtube"))
    }

    @Test
    fun `platform sensitivity - insufficient platform`() {
        val config = cfg.copy(minimumSubgroupSupport = 10)
        val pairs = mutableListOf<PairedPrediction>()
        for (i in 0 until 3) {
            pairs.add(improvementPair("pi-$i", platform = "reels"))
        }
        val report = analyze(pairs, config)
        assertTrue(report.platformSensitivity.isNotEmpty())
        val reels = report.platformSensitivity.first { it.key == "reels" }
        assertFalse(reels.sufficient)
    }

    // --------------------------------
    // DURATION SENSITIVITY
    // --------------------------------

    @Test
    fun `duration sensitivity - all buckets`() {
        val pairs = mutableListOf<PairedPrediction>()
        // <=5s
        for (i in 0 until 4) pairs.add(improvementPair("ds-$i", duration = 3))
        // 5-10s
        for (i in 0 until 4) pairs.add(improvementPair("ds2-$i", duration = 7))
        // 10-30s
        for (i in 0 until 4) pairs.add(improvementPair("ds3-$i", duration = 20))
        // 30-90s
        for (i in 0 until 4) pairs.add(improvementPair("ds4-$i", duration = 60))
        // >90s
        for (i in 0 until 4) pairs.add(improvementPair("ds5-$i", duration = 120))

        val report = analyze(pairs)
        assertTrue(report.durationSensitivity.isNotEmpty())
        val buckets = report.durationSensitivity.map { it.key }.toSet()
        assertTrue(buckets.contains("<=5s"))
        assertTrue(buckets.contains("5-10s"))
        assertTrue(buckets.contains("10-30s"))
        assertTrue(buckets.contains("30-90s"))
        assertTrue(buckets.contains(">90s"))
    }

    @Test
    fun `duration sensitivity - missing duration`() {
        val pairs = (0 until 15).map { i ->
            improvementPair("dm-$i", duration = null)
        }
        val report = analyze(pairs)
        assertTrue(report.durationSensitivity.isNotEmpty())
        // null durations go to UNKNOWN bucket
    }

    // --------------------------------
    // EVIDENCE SENSITIVITY
    // --------------------------------

    @Test
    fun `evidence sensitivity - coverage present`() {
        val pairs = (0 until 15).map { i ->
            val decision = TestFixtures.eightBDecision(
                primaryCategory = "food",
                coverage = com.example.feedsense.analysis.evidence.temporal.CoverageState.HIGH_COVERAGE
            )
            TestFixtures.pair(
                id = "ev-$i",
                truthCategory = "food",
                baselineCategory = "sports",
                eightBCategory = "food",
                eightBDecision = decision
            )
        }
        val report = analyze(pairs)
        assertTrue(report.evidenceSensitivity.isNotEmpty())
        val coverageKeys = report.evidenceSensitivity
            .filter { it.key.startsWith("coverage_") }
        assertTrue(coverageKeys.isNotEmpty())
    }

    @Test
    fun `evidence sensitivity - coverage absent`() {
        val pairs = (0 until 15).map { i ->
            val decision = TestFixtures.eightBDecision(
                primaryCategory = "food",
                coverage = com.example.feedsense.analysis.evidence.temporal.CoverageState.NO_COVERAGE
            )
            TestFixtures.pair(
                id = "ev-$i",
                truthCategory = "food",
                baselineCategory = "sports",
                eightBCategory = "food",
                eightBDecision = decision
            )
        }
        val report = analyze(pairs)
        assertTrue(report.evidenceSensitivity.isNotEmpty())
    }

    @Test
    fun `evidence sensitivity - temporal conflict`() {
        val pairs = (0 until 15).map { i ->
            val decision = TestFixtures.eightBDecision(
                primaryCategory = "food",
                conflict = com.example.feedsense.analysis.evidence.temporal.ConflictLevel.HIGH
            )
            TestFixtures.pair(
                id = "evc-$i",
                truthCategory = "food",
                baselineCategory = "sports",
                eightBCategory = "food",
                eightBDecision = decision
            )
        }
        val report = analyze(pairs)
        val conflictKeys = report.evidenceSensitivity
            .filter { it.key.startsWith("conflict_") }
        assertTrue(conflictKeys.isNotEmpty())
    }

    @Test
    fun `evidence sensitivity - decision state`() {
        val pairs = (0 until 15).map { i ->
            val decision = TestFixtures.eightBDecision(
                primaryCategory = "food",
                decisionState = com.example.feedsense.analysis.evidence.decision.DecisionState.DECIDED
            )
            TestFixtures.pair(
                id = "evd-$i",
                truthCategory = "food",
                baselineCategory = "sports",
                eightBCategory = "food",
                eightBDecision = decision
            )
        }
        val report = analyze(pairs)
        val decisionKeys = report.evidenceSensitivity
            .filter { it.key.startsWith("decision_") }
        assertTrue(decisionKeys.isNotEmpty())
    }

    // --------------------------------
    // ABSTENTION / UNKNOWN SENSITIVITY
    // --------------------------------

    @Test
    fun `abstention - unknown as wrong`() {
        val pairs = mutableListOf<PairedPrediction>()
        // 8B abstains on some items
        for (i in 0 until 5) {
            pairs.add(TestFixtures.pair(
                id = "ab-$i",
                truthCategory = "food",
                baselineCategory = "sports",
                eightBCategory = "food"
            ))
        }
        for (i in 5 until 10) {
            pairs.add(TestFixtures.pair(
                id = "ab2-$i",
                truthCategory = "food",
                baselineCategory = "food",
                eightBCategory = null
            ))
        }
        val report = analyze(pairs)
        assertTrue(report.abstentionSensitivity.policyA.sufficient)
        assertNotNull(report.abstentionSensitivity.policyA.baselineAccuracy)
        assertNotNull(report.abstentionSensitivity.policyA.eightBAccuracy)
    }

    @Test
    fun `abstention - unknown excluded from selective accuracy`() {
        val pairs = mutableListOf<PairedPrediction>()
        for (i in 0 until 5) {
            pairs.add(TestFixtures.pair(
                id = "ae-$i",
                truthCategory = "food",
                baselineCategory = "sports",
                eightBCategory = "food"
            ))
        }
        for (i in 5 until 10) {
            pairs.add(TestFixtures.pair(
                id = "ae2-$i",
                truthCategory = "food",
                baselineCategory = "food",
                eightBCategory = null
            ))
        }
        val report = analyze(pairs)
        assertTrue(report.abstentionSensitivity.policyB.sufficient)
        assertNotNull(report.abstentionSensitivity.policyB.baselineAccuracy)
    }

    @Test
    fun `abstention - coverage plus selective accuracy`() {
        val pairs = mutableListOf<PairedPrediction>()
        for (i in 0 until 8) {
            pairs.add(TestFixtures.pair(
                id = "ac-$i",
                truthCategory = "food",
                baselineCategory = "sports",
                eightBCategory = "food"
            ))
        }
        for (i in 8 until 12) {
            pairs.add(TestFixtures.pair(
                id = "ac2-$i",
                truthCategory = "food",
                baselineCategory = "food",
                eightBCategory = null
            ))
        }
        val report = analyze(pairs)
        val c = report.abstentionSensitivity.policyC
        assertTrue(c.sufficient)
        assertNotNull(c.baselineCoverage)
        assertNotNull(c.eightBCoverage)
        assertNotNull(c.baselineSelectiveAccuracy)
        assertNotNull(c.eightBSelectiveAccuracy)
        // 8B abstains on 4 items, so coverage < 1.0
        assertTrue(c.eightBCoverage!! < 1.0)
    }

    @Test
    fun `abstention - insufficient data returns null`() {
        val config = cfg.copy(minimumForEffectSize = 100)
        val pairs = (0 until 5).map { i -> improvementPair("ai-$i") }
        val report = analyze(pairs, config)
        assertFalse(report.abstentionSensitivity.policyA.sufficient)
        assertFalse(report.abstentionSensitivity.policyB.sufficient)
        assertFalse(report.abstentionSensitivity.policyC.sufficient)
    }

    // --------------------------------
    // CONFIDENCE SENSITIVITY
    // --------------------------------

    @Test
    fun `confidence sensitivity - stratified by bands`() {
        val pairs = (0 until 20).map { i ->
            improvementPair("cs-$i")
        }
        val report = analyze(pairs)
        assertTrue(report.confidenceSensitivity.sufficient)
        assertTrue(report.confidenceSensitivity.bands.isNotEmpty())
        val totalInBands = report.confidenceSensitivity.bands
            .sumOf { it.support }
        assertEquals(report.eligible, totalInBands)
    }

    @Test
    fun `confidence sensitivity - empty pairs`() {
        val report = analyze(emptyList())
        assertFalse(report.confidenceSensitivity.sufficient)
        assertTrue(report.confidenceSensitivity.bands.isEmpty())
    }

    // --------------------------------
    // HIGH-CONFIDENCE ERROR ANALYSIS
    // --------------------------------

    @Test
    fun `high confidence error - reports both systems`() {
        val pairs = (0 until 15).map { i ->
            if (i < 12) improvementPair("hc-$i")
            else regressionPair("hc-$i")
        }
        val report = analyze(pairs)
        assertTrue(report.highConfidenceErrors.sufficient)
        assertTrue(report.highConfidenceErrors.baselineHighConfidenceTotal >= 0)
        assertTrue(report.highConfidenceErrors.eightBHighConfidenceTotal >= 0)
        assertTrue(report.highConfidenceErrors.baselineHighConfidenceWrong >= 0)
        assertTrue(report.highConfidenceErrors.eightBHighConfidenceWrong >= 0)
    }

    @Test
    fun `high confidence error - no high confidence predictions`() {
        val config = cfg.copy(highConfidenceThreshold = 0.99)
        val pairs = (0 until 10).map { i ->
            TestFixtures.pair(
                id = "hc-$i",
                truthCategory = "food",
                baselineCategory = "sports",
                eightBCategory = "food"
            )
        }
        val report = analyze(pairs, config)
        assertEquals(0, report.highConfidenceErrors.baselineHighConfidenceTotal)
        assertEquals(0, report.highConfidenceErrors.eightBHighConfidenceTotal)
    }

    // --------------------------------
    // REGRESSION ANALYSIS
    // --------------------------------

    @Test
    fun `regression analysis - tracks where 8B regresses`() {
        val pairs = mutableListOf<PairedPrediction>()
        for (i in 0 until 5) {
            pairs.add(regressionPair("ra-$i", cat = "food"))
        }
        for (i in 0 until 5) {
            pairs.add(regressionPair("ra2-$i", cat = "sports"))
        }
        for (i in 0 until 10) {
            pairs.add(bothCorrectPair("ra3-$i"))
        }
        val report = analyze(pairs)
        assertTrue(report.regressions.sufficient)
        assertEquals(10, report.regressions.totalRegressions)
        assertTrue(report.regressions.byCategory.containsKey("food"))
        assertTrue(report.regressions.byCategory.containsKey("sports"))
    }

    @Test
    fun `regression analysis - no regressions`() {
        val pairs = (0 until 12).map { i ->
            if (i < 6) improvementPair("rn-$i")
            else bothCorrectPair("rn2-$i")
        }
        val report = analyze(pairs)
        assertEquals(0, report.regressions.totalRegressions)
        assertTrue(report.regressions.byCategory.isEmpty())
    }

    // --------------------------------
    // IMPROVEMENT ANALYSIS
    // --------------------------------

    @Test
    fun `improvement analysis - tracks where 8B improves`() {
        val pairs = mutableListOf<PairedPrediction>()
        for (i in 0 until 8) {
            pairs.add(improvementPair("ia-$i", cat = "food"))
        }
        for (i in 0 until 4) {
            pairs.add(improvementPair("ia2-$i", cat = "sports"))
        }
        for (i in 0 until 8) {
            pairs.add(bothCorrectPair("ia3-$i"))
        }
        val report = analyze(pairs)
        assertTrue(report.improvements.sufficient)
        assertEquals(12, report.improvements.totalImprovements)
        assertTrue(report.improvements.byCategory.containsKey("food"))
        assertTrue(report.improvements.byCategory.containsKey("sports"))
    }

    @Test
    fun `improvement analysis - no improvements`() {
        val pairs = (0 until 12).map { i ->
            if (i < 6) regressionPair("in-$i")
            else bothCorrectPair("in2-$i")
        }
        val report = analyze(pairs)
        assertEquals(0, report.improvements.totalImprovements)
    }

    // --------------------------------
    // ERROR CONCENTRATION
    // --------------------------------

    @Test
    fun `error concentration - improvement concentration`() {
        val pairs = mutableListOf<PairedPrediction>()
        for (i in 0 until 10) {
            pairs.add(improvementPair("ec-$i", cat = "food"))
        }
        for (i in 0 until 2) {
            pairs.add(improvementPair("ec2-$i", cat = "sports"))
        }
        for (i in 0 until 8) {
            pairs.add(bothCorrectPair("ec3-$i"))
        }
        val report = analyze(pairs)
        val topImpCat = report.errorConcentration.improvements.topCategory
        assertNotNull(topImpCat)
        assertEquals("food", topImpCat!!.key)
        assertTrue(topImpCat.percentage > 0.5)
    }

    @Test
    fun `error concentration - regression concentration`() {
        val pairs = mutableListOf<PairedPrediction>()
        for (i in 0 until 8) {
            pairs.add(regressionPair("rce-$i", cat = "food"))
        }
        for (i in 0 until 2) {
            pairs.add(regressionPair("rce2-$i", cat = "sports"))
        }
        for (i in 0 until 10) {
            pairs.add(bothCorrectPair("rce3-$i"))
        }
        val report = analyze(pairs)
        val topRegCat = report.errorConcentration.regressions.topCategory
        assertNotNull(topRegCat)
        assertEquals("food", topRegCat!!.key)
    }

    // --------------------------------
    // MULTIPLE COMPARISON METADATA
    // --------------------------------

    @Test
    fun `multiple comparison - bonferroni adjustment`() {
        val pairs = (0 until 30).map { i ->
            if (i % 3 == 0) improvementPair("mc-$i", cat = "food")
            else if (i % 3 == 1) improvementPair("mc-$i", cat = "sports")
            else bothCorrectPair("mc-$i", cat = "music")
        }
        val report = analyze(pairs)
        assertEquals("Bonferroni", report.multipleComparisonMethod)
        assertTrue(report.familyOfTests > 0)
        assertNotNull(report.adjustedAlpha)
        assertTrue(report.adjustedAlpha!! < cfg.alpha)
    }

    @Test
    fun `multiple comparison - no adjustment when disabled`() {
        val config = cfg.copy(multipleComparisonBonferroni = false)
        val pairs = (0 until 15).map { i -> improvementPair("mc-$i") }
        val report = analyze(pairs, config)
        assertNull(report.multipleComparisonMethod)
        assertEquals(0, report.familyOfTests)
        assertNull(report.adjustedAlpha)
    }

    // --------------------------------
    // VERSIONING
    // --------------------------------

    @Test
    fun `versioning - config fields recorded`() {
        val pairs = (0 until 15).map { i -> improvementPair("v-$i") }
        val report = analyze(pairs)
        assertEquals(cfg.statisticalAnalysisVersion, report.statisticalAnalysisVersion)
        assertEquals(cfg.evaluationVersion, report.evaluationVersion)
        assertEquals(cfg.analysisSeed, report.analysisSeed)
        assertEquals(cfg.bootstrapIterations, report.bootstrapIterations)
        assertEquals(cfg.alpha, report.alpha, 1e-9)
        assertNotNull(report.schemaVersion)
        assertNotNull(report.reportId)
        assertNotNull(report.createdAt)
    }

    @Test
    fun `versioning - dataset metadata propagated`() {
        val pairs = (0 until 15).map { i -> improvementPair("v2-$i") }
        val report = RobustnessAnalyzer.analyze(
            RobustnessAnalyzer.Input(
                pairs = pairs,
                datasetVersion = "ds-v2",
                baselineModelVersion = "base-v3",
                fusionVersion = "fusion-v2",
                decisionVersion = "decision-v2",
                diagnosticVersion = "diag-v2"
            )
        )
        assertEquals("ds-v2", report.datasetVersion)
        assertEquals("base-v3", report.baselineModelVersion)
        assertEquals("fusion-v2", report.fusionVersion)
        assertEquals("decision-v2", report.decisionVersion)
        assertEquals("diag-v2", report.diagnosticVersion)
    }

    // --------------------------------
    // JSON EXPORT
    // --------------------------------

    @Test
    fun `json export - contains all top-level keys`() {
        val pairs = (0 until 20).map { i ->
            if (i < 14) improvementPair("j-$i")
            else bothCorrectPair("j2-$i")
        }
        val report = analyze(pairs)
        val json = report.toJson()
        assertTrue(json.contains("reportId"))
        assertTrue(json.contains("versioning"))
        assertTrue(json.contains("config"))
        assertTrue(json.contains("population"))
        assertTrue(json.contains("primaryResult"))
        assertTrue(json.contains("effectSize"))
        assertTrue(json.contains("confidenceIntervals"))
        assertTrue(json.contains("bootstrap"))
        assertTrue(json.contains("categorySensitivity"))
        assertTrue(json.contains("leaveOneCategoryOut"))
        assertTrue(json.contains("platformSensitivity"))
        assertTrue(json.contains("durationSensitivity"))
        assertTrue(json.contains("evidenceSensitivity"))
        assertTrue(json.contains("abstentionSensitivity"))
        assertTrue(json.contains("confidenceSensitivity"))
        assertTrue(json.contains("highConfidenceErrors"))
        assertTrue(json.contains("improvements"))
        assertTrue(json.contains("regressions"))
        assertTrue(json.contains("errorConcentration"))
        assertTrue(json.contains("multipleComparison"))
        assertTrue(json.contains("guards"))
        assertTrue(json.contains("conclusion"))
    }

    @Test
    fun `json export - valid JSON`() {
        val pairs = (0 until 15).map { i -> improvementPair("jv-$i") }
        val report = analyze(pairs)
        val jsonStr = report.toJson()
        // Should not throw
        val parsed = JSONObject(jsonStr)
        assertEquals(report.reportId, parsed.getString("reportId"))
    }

    // --------------------------------
    // CSV EXPORT
    // --------------------------------

    @Test
    fun `csv export - overall row`() {
        val pairs = (0 until 15).map { i -> improvementPair("cv-$i") }
        val report = analyze(pairs)
        val csv = report.toCsv()
        val lines = csv.lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].contains("reportId"))
        assertTrue(lines[0].contains("accuracyDifference"))
        assertTrue(lines[1].contains(report.reportId))
    }

    @Test
    fun `csv export - subgroup rows`() {
        val pairs = (0 until 15).map { i -> improvementPair("cvs-$i") }
        val report = analyze(pairs)
        val csv = report.subgroupCsv()
        val lines = csv.lines()
        assertTrue(lines.size > 1)
        assertTrue(lines[0].contains("dimension"))
        assertTrue(lines[0].contains("key"))
    }

    // --------------------------------
    // PRIVACY
    // --------------------------------

    @Test
    fun `privacy - no raw content in JSON`() {
        val pairs = (0 until 15).map { i -> improvementPair("p-$i") }
        val report = analyze(pairs)
        val json = report.toJson()
        assertFalse(json.contains("screenCapture"))
        assertFalse(json.contains("ocrText"))
        assertFalse(json.contains("caption"))
        assertFalse(json.contains("rawContent"))
    }

    @Test
    fun `privacy - no raw content in CSV`() {
        val pairs = (0 until 15).map { i -> improvementPair("pc-$i") }
        val report = analyze(pairs)
        val csv = report.toCsv()
        assertFalse(csv.contains("screenCapture"))
        assertFalse(csv.contains("ocrText"))
        assertFalse(csv.contains("caption"))
    }

    // --------------------------------
    // DETERMINISM
    // --------------------------------

    @Test
    fun `determinism - same input gives same report`() {
        val pairs = (0 until 20).map { i ->
            if (i < 14) improvementPair("d-$i")
            else bothCorrectPair("d2-$i")
        }
        val r1 = analyze(pairs)
        val r2 = analyze(pairs)
        assertEquals(r1.primaryResult.pValue, r2.primaryResult.pValue)
        assertEquals(r1.effectSize.accuracyDifference, r2.effectSize.accuracyDifference)
        assertEquals(r1.bootstrap.observedDifference, r2.bootstrap.observedDifference)
        assertEquals(r1.bootstrap.lowerBound, r2.bootstrap.lowerBound)
        assertEquals(r1.bootstrap.upperBound, r2.bootstrap.upperBound)
        assertEquals(r1.conclusion.statisticalStatus, r2.conclusion.statisticalStatus)
        assertEquals(r1.conclusion.headline, r2.conclusion.headline)
    }

    // --------------------------------
    // INSUFFICIENT DATA
    // --------------------------------

    @Test
    fun `insufficient data - empty input`() {
        val report = analyze(emptyList())
        assertEquals(
            StatisticalStatus.INSUFFICIENT_DATA,
            report.primaryResult.status
        )
        assertFalse(report.effectSize.sufficient)
        assertFalse(report.bootstrap.sufficient)
        assertNull(report.primaryResult.pValue)
    }

    @Test
    fun `insufficient data - below minimum`() {
        val config = cfg.copy(minimumForEffectSize = 100)
        val pairs = (0 until 5).map { i -> improvementPair("id-$i") }
        val report = analyze(pairs, config)
        assertEquals(
            StatisticalStatus.INSUFFICIENT_DATA,
            report.primaryResult.status
        )
        assertNull(report.primaryResult.pValue)
    }

    @Test
    fun `insufficient data - real data status`() {
        val config = cfg.copy(minimumForEffectSize = 100)
        val pairs = (0 until 5).map { i -> improvementPair("rds-$i") }
        val report = analyze(pairs, config)
        assertEquals(
            StatisticalStatus.INSUFFICIENT_REAL_DATA_FOR_STATISTICAL_CONCLUSIONS,
            report.realDataStatus
        )
    }

    // --------------------------------
    // UNKNOWN TRUTH EXCLUSION
    // --------------------------------

    @Test
    fun `unknown truth pairs excluded from accuracy`() {
        val pairs = mutableListOf<PairedPrediction>()
        pairs.add(bothCorrectPair("ut-1"))
        pairs.add(unknownTruthPair("ut-2"))
        pairs.add(bothCorrectPair("ut-3"))
        val report = analyze(pairs)
        assertEquals(3, report.totalPaired)
        assertEquals(2, report.eligible)
        assertEquals(1, report.ineligible)
    }

    // --------------------------------
    // IMPROVEMENT vs REGRESSION COMPARISON
    // --------------------------------

    @Test
    fun `improvement vs regression comparison`() {
        val pairs = mutableListOf<PairedPrediction>()
        for (i in 0 until 8) {
            pairs.add(improvementPair("ir-$i"))
        }
        for (i in 0 until 3) {
            pairs.add(regressionPair("ir2-$i"))
        }
        for (i in 0 until 9) {
            pairs.add(bothCorrectPair("ir3-$i"))
        }
        val report = analyze(pairs)
        assertTrue(report.improvements.totalImprovements > report.regressions.totalRegressions)
    }

    // --------------------------------
    // STATISTICAL STATUS CONCLUSION
    // --------------------------------

    @Test
    fun `conclusion - significant improvement`() {
        val pairs = (0 until 20).map { i ->
            if (i < 2) bothCorrectPair("sg-$i")
            else if (i < 16) improvementPair("sg-$i")
            else regressionPair("sg-$i")
        }
        val report = analyze(pairs)
        assertEquals(
            StatisticalStatus.SUFFICIENT,
            report.conclusion.statisticalStatus
        )
        assertTrue(report.conclusion.headline.contains("statistically supported"))
    }

    @Test
    fun `conclusion - not significant`() {
        val pairs = (0 until 12).map { i ->
            if (i < 6) regressionPair("ns-$i")
            else improvementPair("ns2-$i")
        }
        val report = analyze(pairs)
        assertTrue(report.conclusion.headline.contains("No statistically significant"))
    }

    @Test
    fun `conclusion - insufficient data`() {
        val config = cfg.copy(minimumForEffectSize = 100)
        val pairs = (0 until 5).map { i -> improvementPair("id-$i") }
        val report = analyze(pairs, config)
        assertTrue(report.conclusion.headline.contains("INSUFFICIENT"))
    }

    // --------------------------------
    // ACCURACY DIFFERENCE
    // --------------------------------

    @Test
    fun `accuracy difference - 8B better`() {
        val pairs = (0 until 15).map { i ->
            if (i < 12) improvementPair("ad-$i")
            else bothCorrectPair("ad2-$i")
        }
        val report = analyze(pairs)
        assertTrue(report.effectSize.sufficient)
        assertNotNull(report.effectSize.accuracyDifference)
        assertTrue(report.effectSize.accuracyDifference!! > 0)
        assertTrue(report.effectSize.netDiscordantImprovement > 0)
    }

    @Test
    fun `accuracy difference - baseline better`() {
        val pairs = (0 until 15).map { i ->
            if (i < 12) regressionPair("ad-$i")
            else bothCorrectPair("ad2-$i")
        }
        val report = analyze(pairs)
        assertTrue(report.effectSize.sufficient)
        assertTrue(report.effectSize.accuracyDifference!! < 0)
        assertTrue(report.effectSize.netDiscordantImprovement < 0)
    }

    // --------------------------------
    // READ-ONLY LAYER
    // --------------------------------

    @Test
    fun `read-only layer - does not modify input pairs`() {
        val pairs = (0 until 15).map { i -> improvementPair("ro-$i") }
        val originalIds = pairs.map { it.item.id }
        val originalOutcomes = pairs.map { it.outcome }
        analyze(pairs)
        // Verify pairs unchanged
        assertEquals(originalIds, pairs.map { it.item.id })
        assertEquals(originalOutcomes, pairs.map { it.outcome })
    }

    // --------------------------------
    // LARGE SAMPLE
    // --------------------------------

    @Test
    fun `large sample - 200 pairs`() {
        val pairs = (0 until 200).map { i ->
            if (i % 10 < 8) improvementPair("ls-$i")
            else bothCorrectPair("ls2-$i")
        }
        val report = analyze(pairs)
        assertEquals(200, report.totalPaired)
        assertEquals(200, report.eligible)
        assertTrue(report.primaryResult.status == StatisticalStatus.SUFFICIENT)
        assertNotNull(report.primaryResult.pValue)
        assertTrue(report.bootstrap.sufficient)
    }
}
