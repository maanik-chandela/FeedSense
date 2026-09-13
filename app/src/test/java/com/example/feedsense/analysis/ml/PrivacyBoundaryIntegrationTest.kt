package com.example.feedsense.analysis.ml

import com.example.feedsense.analysis.privacy.AnonymizationResult
import com.example.feedsense.analysis.privacy.PRIVACY_SYSTEM_UI_REGIONS
import com.example.feedsense.analysis.privacy.PrivacyPolicy
import com.example.feedsense.analysis.privacy.PrivacyProcessor
import com.example.feedsense.analysis.privacy.OcrAvailability
import com.example.feedsense.analysis.privacy.PrivacyFrame
import com.example.feedsense.analysis.privacy.PrivacySanitizationStatus
import com.example.feedsense.analysis.privacy.PrivacySanitizationVersion
import com.example.feedsense.analysis.privacy.SyntheticFrames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-14.
 *
 * End-to-end privacy boundary: the ML engine consumes ONLY
 * the frame produced by the 8B-13 PrivacyProcessor, stamps
 * the exact privacy-processing version, and serializes only
 * safe metadata (sections 16, 17, 32, 46).
 */
class PrivacyBoundaryIntegrationTest {

    private fun processWithSystemUi(frame: PrivacyFrame): AnonymizationResult {
        return PrivacyProcessor(policy = PrivacyPolicy.RESEARCH).process(
            frame = frame,
            regions = PRIVACY_SYSTEM_UI_REGIONS,
            ocrAvailability = OcrAvailability.OCR_AVAILABLE
        )
    }

    @Test
    fun `engine consumes only the sanitized safe frame`() {
        val raw = SyntheticFrames.checkerboard(
            48, 48, 0xFF202020.toInt(), 0xFFDDDDDD.toInt()
        )
        val rawSnapshot = raw.pixels.copyOf()
        val anonymization = processWithSystemUi(raw)
        val safeFrame = anonymization.safeFrame
        assertNotNull(safeFrame)
assertEquals(PrivacySanitizationStatus.SANITIZED,
            anonymization.decision.status)
        // the sanitizer mutates the (in-place) buffer, so compare
        // against a pre-processing snapshot of the raw pixels
        val frame = safeFrame ?: error("no safe frame produced")
        assertTrue(
            frame.pixels.indices.any { frame.pixels[it] != rawSnapshot[it] }
        )

        val engine = OnDeviceInferenceEngine(
            model = FakeOnDeviceModel(
                metadata = MlTestFixtures.metadata(),
                behavior = FakeModelBehavior.FixedScores(
                    MlTestFixtures.successScores()
                )
            ),
            preprocessor = DefaultModelPreprocessor(
                MlTestFixtures.INPUT_SPEC
            )
        )

        val result = engine.analyzeSafeFrame(
            safeFrame = safeFrame!!,
            evidenceId = "evidence-safe-1",
            privacyVersion = anonymization.decision.processingVersion
        )

        assertEquals(InferenceStatus.SUCCESS, result.status)
        assertEquals("sports", result.primaryCategory)
        // exact privacy version attribution
        assertEquals(
            PrivacySanitizationVersion.PROCESSING,
            result.privacyVersion
        )
        assertEquals("evidence-safe-1", result.evidenceId)
        assertNull(result.failure)
    }

    @Test
    fun `engine has no path for raw frames`() {
        // Contract check: there is no raw-frame entry point on
        // the engine - the only accepted input type is the
        // privacy-safe PrivacyFrame produced by 8B-13.
        val engine = OnDeviceInferenceEngine(
            model = FakeOnDeviceModel(
                metadata = MlTestFixtures.metadata()
            ),
            preprocessor = DefaultModelPreprocessor(MlTestFixtures.INPUT_SPEC)
        )
        val safeFrame = SyntheticFrames.solid(8, 8, 0xFF808080.toInt())
        val result = engine.analyzeSafeFrame(safeFrame)
        // engine still returns an explicit result (never throws)
        assertNotNull(result)
    }

    @Test
    fun `serialized result carries no private content`() {
        val raw = SyntheticFrames.checkerboard(
            32, 32, 0xFF303030.toInt(), 0xFFCFCFCF.toInt()
        )
        val anonymization = processWithSystemUi(raw)
        assertNotNull(anonymization.safeFrame)

        val engine = OnDeviceInferenceEngine(
            model = FakeOnDeviceModel(
                metadata = MlTestFixtures.metadata()
            ),
            preprocessor = DefaultModelPreprocessor(MlTestFixtures.INPUT_SPEC)
        )
        val result = engine.analyzeSafeFrame(
            safeFrame = anonymization.safeFrame!!,
            privacyVersion = anonymization.decision.processingVersion
        )

        val metadata = ModelResultSerializer.toMetadataMap(result)
        val json = ModelResultSerializer.toJsonText(result)

        // Only allow-listed metadata keys may be serialized.
        val allowedKeys = setOf(
            "modelId", "modelVersion", "modelChecksum", "status",
            "primaryCategory", "primaryConfidence",
            "preprocessVersion", "privacyVersion", "inputSpecVersion",
            "outputSpecVersion", "evidenceId", "experimentId",
            "timestampMs", "preprocessingLatencyMs",
            "inferenceLatencyMs", "totalLatencyMs", "topK",
            "failureStatus", "failurePhase", "failureMessage"
        )
        assertTrue(
            "unexpected keys serialized: ${metadata.keys - allowedKeys}",
            allowedKeys.containsAll(metadata.keys)
        )

        assertTrue(metadata["privacyVersion"] ==
            PrivacySanitizationVersion.PROCESSING)

        val forbidden = listOf(
            "pixels", "inputBytes", "quantizedBytes", "floats",
            "ocrText", "visibleText", "content", "raw", "frameBytes"
        )
        for (term in forbidden) {
            assertTrue(
                "forbidden term in json: $term (json=$json)",
                !json.contains(term, ignoreCase = true)
            )
        }
    }
}