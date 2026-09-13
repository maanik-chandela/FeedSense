package com.example.feedsense.analysis.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-13.
 *
 * Coverage evaluation against manually annotated ground truth
 * (spec §37-§38). Ground truth is human, never AI-derived.
 */
class PrivacyCoverageEvaluatorTest {

    private val evaluator = PrivacyCoverageEvaluator()

    private fun frame(width: Int = 100, height: Int = 100): PrivacyFrame =
        SyntheticFrames.checkerboard(
            width, height, 0xFF101010.toInt(), 0xFFE0E0E0.toInt()
        )

    private fun auto(type: PrivacyRegionType, r: ProtectedRegion): PrivacyRegion =
        PrivacyRegion(
            type = type,
            bounds = r,
            signals = listOf(PrivacyDetectionSignal.FIXED_REGION)
        )

    @Test
    fun `matched ground truth gives full coverage and no errors`() {
        val gt = ProtectedRegion(0.0, 0.0, 1.0, 0.3, "GT1")
        val report = evaluator.evaluate(
            frame(),
            groundTruth = listOf(gt),
            automaticRegions = listOf(
                auto(PrivacyRegionType.PRIVATE_TEXT, gt)
            )
        )
        assertEquals(1.0, report.coverage, 0.001)
        assertEquals(0, report.falsePositives)
        assertEquals(0, report.falseNegatives)
        assertEquals(1, report.coveredRegions)
        assertTrue(report.protectionEvidence.isNotEmpty())
    }

    @Test
    fun `out-of-ground-truth protection is a false positive`() {
        val gt = ProtectedRegion(0.0, 0.0, 0.2, 0.2, "GT1")
        val extra = ProtectedRegion(0.8, 0.8, 0.2, 0.2, "OUTSIDE")
        val report = evaluator.evaluate(
            frame(),
            groundTruth = listOf(gt),
            automaticRegions = listOf(
                auto(PrivacyRegionType.PRIVATE_TEXT, gt),
                auto(PrivacyRegionType.NOTIFICATION, extra)
            )
        )
        assertEquals(1, report.falsePositives)
        assertEquals(0, report.falseNegatives)
    }

    @Test
    fun `missed ground truth is a false negative`() {
        val gt = ProtectedRegion(0.0, 0.0, 0.3, 0.3, "GT1")
        val unrelated = ProtectedRegion(0.9, 0.9, 0.1, 0.1, "OTHER")
        val report = evaluator.evaluate(
            frame(),
            groundTruth = listOf(gt),
            automaticRegions = listOf(
                auto(PrivacyRegionType.NOTIFICATION, unrelated)
            )
        )
        assertEquals(1, report.falseNegatives)
        assertEquals(0.0, report.coverage, 0.001)
    }

    @Test
    fun `coverage is the protected fraction over ground truth area`() {
        val gt1 = ProtectedRegion(0.0, 0.0, 1.0, 0.5, "GT1")
        val gt2 = ProtectedRegion(0.0, 0.5, 1.0, 0.5, "GT2")
        val report = evaluator.evaluate(
            frame(),
            groundTruth = listOf(gt1, gt2),
            automaticRegions = listOf(
                auto(PrivacyRegionType.PRIVATE_TEXT, gt1)
            )
        )
        assertEquals(0.5, report.coverage, 0.001)
        assertEquals(1, report.falseNegatives)
        assertEquals(2, report.groundTruthRegions)
    }

    @Test
    fun `evaluation is deterministic`() {
        val gt = ProtectedRegion(0.0, 0.0, 0.4, 0.4, "GT1")
        val auto = auto(PrivacyRegionType.PRIVATE_TEXT, gt)
        val a = evaluator.evaluate(frame(), listOf(gt), listOf(auto))
        val b = evaluator.evaluate(frame(), listOf(gt), listOf(auto))
        assertEquals(a.coverage, b.coverage, 0.0)
        assertEquals(a.protectionEvidence, b.protectionEvidence)
    }

    @Test
    fun `empty ground truth yields zero coverage but no false negatives`() {
        val report = evaluator.evaluate(
            frame(),
            groundTruth = emptyList(),
            automaticRegions = listOf(
                auto(PrivacyRegionType.PRIVATE_TEXT,
                    ProtectedRegion(0.0, 0.0, 0.3, 0.3, "A"))
            )
        )
        assertEquals(0.0, report.coverage, 0.001)
        assertEquals(0, report.falseNegatives)
        assertEquals(1, report.falsePositives)
    }
}