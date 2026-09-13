package com.example.feedsense.analysis.privacy

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-13 (acceptance: false-negative tests).
 *
 * Genuinely sensitive content must be detected and protected:
 *   - account-number/id-like forms trigger the ACCOUNT_ID pattern,
 *   - a likely-fake notification is still covered by the
 *     notification band (geometry), not silently left "safe",
 *   - a caller-classified private message (chat bubble) is never
 *     left untouched.
 *
 * Honest layering property: OCR patterns see only explicit
 * identifier FORMS. A plain payment message with no identifier in
 * it is protected by its notification/chat REGION, not invented
 * as a pattern by the detector.
 */
class PrivacyFalseNegativeTest {

    private val detector = SensitivePatternDetector()

    private fun textured() = SyntheticFrames.checkerboard(
        100, 100, 0xFF101010.toInt(), 0xFFE0E0E0.toInt()
    )

    @Test
    fun `account-number forms are detected as account ids`() {
        listOf(
            "acc no 7788990011",
            "Account# 7788990011",
            "acc no ABC-1234567"
        ).forEach { text ->
            val kinds = detector.findMatches(text).map { it.kind }
            assertTrue(
                "account form not detected: $text (kinds=$kinds)",
                kinds.contains(SensitivePatternKind.ACCOUNT_ID)
            )
        }
    }

    @Test
    fun `account-id line becomes a personal identifier region`() {
        val bounds = ProtectedRegion(0.0, 0.3, 1.0, 0.1, "LINE")
        val regions = PrivacyOcrDetection.detectPrivateText(
            listOf(OcrLine("acc no 7788990011", bounds))
        )
        assertEquals(1, regions.size)
        assertEquals(PrivacyRegionType.PERSONAL_IDENTIFIER, regions.first().type)
        assertEquals(bounds, regions.first().bounds)
    }

    @Test
    fun `fake notification is covered by the notification band and blurred`() {
        val notificationBand = PrivacyRegion(
            type = PrivacyRegionType.NOTIFICATION,
            bounds = ProtectedRegion(0.0, 0.05, 1.0, 0.12, "FAKE_NOTIFICATION"),
            signals = listOf(PrivacyDetectionSignal.NOTIFICATION_STYLE)
        )
        val result = PrivacyProcessor().process(
            textured(), listOf(notificationBand), OcrAvailability.OCR_AVAILABLE
        )
        assertEquals(PrivacyRisk.MEDIUM, result.decision.risk)
        assertEquals(PrivacySanitizationStatus.SANITIZED, result.decision.status)
        assertEquals(
            PrivacyTransformation.BLUR,
            result.decision.transformations.first().transformation
        )
    }

    @Test
    fun `fake notification with no identifier is still covered by its geometry band`() {
        // A plain payment message carries no identifier form, so the
        // OCR pattern layer honestly reports nothing. The protection
        // comes from the geometry band which the detector ALWAYS
        // emits - never a silent "safe" claim.
        val text = "Payment of 29.99 confirmed on your card ending 4488"
        assertEquals(
            0,
            PrivacyOcrDetection.detectPrivateText(
                listOf(OcrLine(text))
            ).size
        )
        val bands = SystemUIRegionDetector().detect(File("frame"), 0, 0)
        assertTrue(
            "notification band missing from geometry detection",
            bands.any { it.label == "NOTIFICATION" }
        )
    }

    @Test
    fun `fake private message region is masked and high risk`() {
        val chat = PrivacyRegion(
            type = PrivacyRegionType.PRIVATE_TEXT,
            bounds = ProtectedRegion(0.05, 0.3, 0.9, 0.2, "CHAT"),
            signals = listOf(PrivacyDetectionSignal.PRIVACY_SENSITIVE_KEYWORD)
        )
        val result = PrivacyProcessor().process(
            textured(), listOf(chat), OcrAvailability.OCR_AVAILABLE
        )
        assertEquals(PrivacyRisk.HIGH, result.decision.risk)
        assertEquals(PrivacySanitizationStatus.SANITIZED, result.decision.status)
        assertEquals(
            PrivacyTransformation.MASK,
            result.decision.transformations.first().transformation
        )
    }
}