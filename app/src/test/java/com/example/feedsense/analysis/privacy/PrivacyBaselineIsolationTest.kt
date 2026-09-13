package com.example.feedsense.analysis.privacy

import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.GroundTruth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/*
 * Milestone 8B-13 (acceptance: privacy / baseline isolation
 * invariants).
 *
 * The privacy layer operates ONLY on frame pixels, regions,
 * policy, rules, and OCR availability. It must never:
 *   - mutate a GroundTruth record,
 *   - mutate the frozen baseline prediction
 *     (AiPredictionRecord),
 *   - leak raw text or pixel coordinates into its serializable
 *     metadata.
 *
 * Same input + same policy must produce the same result.
 */
class PrivacyBaselineIsolationTest {

    private var clock = 0L

    private fun frame() = SyntheticFrames.checkerboard(
        100, 100, 0xFF101010.toInt(), 0xFFE0E0E0.toInt()
    )

    private fun truth(): GroundTruth =
        GroundTruth(
            evaluationItemId = "item1",
            category = "sports",
            ambiguity = GroundTruth.AMBIGUITY_CLEAR
        )

    private fun frozenPrediction(): AiPredictionRecord =
        AiPredictionRecord(
            evaluationItemId = "item1",
            category = "sports",
            confidence = 0.82,
            modelVersion = "baseline-v1"
        )

    @Test
    fun `privacy processing never mutates ground truth`() {
        val before = truth()
        val snapshot = before.copy()

        PrivacyProcessor(nowMs = { clock }).process(
            frame(),
            listOf(
                PrivacyRegion(
                    type = PrivacyRegionType.PRIVATE_TEXT,
                    bounds = ProtectedRegion(0.05, 0.3, 0.9, 0.2, "CHAT"),
                    signals = listOf(PrivacyDetectionSignal.OCR_TEXT_PATTERN)
                )
            ),
            OcrAvailability.OCR_AVAILABLE
        )

        assertEquals(snapshot, before)
    }

    @Test
    fun `privacy processing never mutates the frozen baseline prediction`() {
        val before = frozenPrediction()
        val snapshot = before.copy()

        val regions = PrivacyOcrDetection.detectPrivateText(
            listOf(OcrLine("reach john.doe@example.com in the reply"))
        )
        PrivacyProcessor(nowMs = { clock }).process(
            frame(), regions, OcrAvailability.OCR_AVAILABLE
        )

        assertEquals(snapshot, before)
    }

    @Test
    fun `safe metadata never carries raw text or coordinates`() {
        val regions = PrivacyOcrDetection.detectPrivateText(
            listOf(OcrLine("reach john.doe@example.com in the reply"))
        )
        val result = PrivacyProcessor(nowMs = { clock }).process(
            frame(), regions, OcrAvailability.OCR_AVAILABLE
        )
        val values = result.toSafeMetadataMap().values.joinToString(" ")
        assertFalse("raw text leaked", values.contains("john.doe"))
        assertFalse("raw text leaked", values.contains("example.com"))
        assertFalse("coordinates leaked", values.contains("left="))
        assertFalse("coordinates leaked", values.contains("top="))
    }

    @Test
    fun `same input and same policy give the same result`() {
        val regions = listOf(
            PrivacyRegion(
                type = PrivacyRegionType.SYSTEM_UI,
                bounds = ProtectedRegion(0.0, 0.0, 1.0, 0.05, "STATUS"),
                signals = listOf(PrivacyDetectionSignal.FIXED_REGION)
            )
        )
        val a = PrivacyProcessor(nowMs = { clock }).process(
            frame(), regions, OcrAvailability.OCR_AVAILABLE
        )
        val b = PrivacyProcessor(nowMs = { clock }).process(
            frame(), regions, OcrAvailability.OCR_AVAILABLE
        )
        assertEquals(a.toSafeMetadataMap(), b.toSafeMetadataMap())
        assertEquals(a.safeFrame!!.frameChecksum(), b.safeFrame!!.frameChecksum())
        assertEquals(a.decision.regionsDetected, b.decision.regionsDetected)
    }
}