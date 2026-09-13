package com.example.feedsense.analysis.ml.runtime

import com.example.feedsense.analysis.ml.ModelTensorType
import com.example.feedsense.analysis.ml.preprocess.PreprocessingConfig
import com.example.feedsense.analysis.ml.preprocess.PreprocessingVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-5.
 *
 * Compatibility gate: artifact/runtime/config/input
 * compatibility checks.
 */
class CompatibilityGateTest {

    // --------------------------------
    // FULL COMPATIBILITY CHECK
    // --------------------------------

    @Test
    fun `compatible artifact and runtime passes`() {
        val result = CompatibilityGate.check(
            artifact = RuntimeTestFixtures.TEST_ARTIFACT_AVAILABLE,
            runtime = RuntimeTestFixtures.TEST_RUNTIME_LITERT,
            config = RuntimeTestFixtures.CONFIG_4x4_DEFAULT,
            input = RuntimeTestFixtures.input4x4Float32()
        )
        assertTrue(result.passed)
    }

    @Test
    fun `pending artifact fails`() {
        val result = CompatibilityGate.check(
            artifact = RuntimeTestFixtures.TEST_ARTIFACT_PENDING,
            runtime = RuntimeTestFixtures.TEST_RUNTIME_LITERT,
            config = RuntimeTestFixtures.CONFIG_4x4_DEFAULT,
            input = RuntimeTestFixtures.input4x4Float32()
        )
        assertFalse(result.passed)
        val failure = (result as CompatibilityCheckResult.Fail).failure
        assertEquals(RuntimeFailureCode.ARTIFACT_MISSING, failure.code)
    }

    @Test
    fun `not available artifact fails`() {
        val result = CompatibilityGate.check(
            artifact = RuntimeTestFixtures.TEST_ARTIFACT_NOT_AVAILABLE,
            runtime = RuntimeTestFixtures.TEST_RUNTIME_LITERT,
            config = RuntimeTestFixtures.CONFIG_4x4_DEFAULT,
            input = RuntimeTestFixtures.input4x4Float32()
        )
        assertFalse(result.passed)
        val failure = (result as CompatibilityCheckResult.Fail).failure
        assertEquals(RuntimeFailureCode.ARTIFACT_MISSING, failure.code)
    }

    @Test
    fun `unknown runtime fails`() {
        val result = CompatibilityGate.check(
            artifact = RuntimeTestFixtures.TEST_ARTIFACT_AVAILABLE,
            runtime = RuntimeTestFixtures.TEST_RUNTIME_UNKNOWN,
            config = RuntimeTestFixtures.CONFIG_4x4_DEFAULT,
            input = RuntimeTestFixtures.input4x4Float32()
        )
        assertFalse(result.passed)
        val failure = (result as CompatibilityCheckResult.Fail).failure
        assertEquals(RuntimeFailureCode.RUNTIME_UNAVAILABLE, failure.code)
    }

    @Test
    fun `wrong dimensions fails`() {
        val result = CompatibilityGate.check(
            artifact = RuntimeTestFixtures.TEST_ARTIFACT_AVAILABLE,
            runtime = RuntimeTestFixtures.TEST_RUNTIME_LITERT,
            config = RuntimeTestFixtures.CONFIG_4x4_DEFAULT,
            input = RuntimeTestFixtures.inputWrongDimensions()
        )
        assertFalse(result.passed)
        val failure = (result as CompatibilityCheckResult.Fail).failure
        assertEquals(RuntimeFailureCode.WRONG_DIMENSIONS, failure.code)
    }

    @Test
    fun `wrong datatype fails`() {
        val result = CompatibilityGate.check(
            artifact = RuntimeTestFixtures.TEST_ARTIFACT_AVAILABLE,
            runtime = RuntimeTestFixtures.TEST_RUNTIME_LITERT,
            config = RuntimeTestFixtures.CONFIG_4x4_DEFAULT,
            input = RuntimeTestFixtures.inputWrongDatatype()
        )
        assertFalse(result.passed)
        val failure = (result as CompatibilityCheckResult.Fail).failure
        assertEquals(RuntimeFailureCode.WRONG_DATATYPE, failure.code)
    }

    // --------------------------------
    // INPUT-ONLY CHECK
    // --------------------------------

    @Test
    fun `input-only check passes for matching input`() {
        val result = CompatibilityGate.checkInput(
            config = RuntimeTestFixtures.CONFIG_4x4_DEFAULT,
            input = RuntimeTestFixtures.input4x4Float32()
        )
        assertTrue(result.passed)
    }

    @Test
    fun `input-only check fails for wrong dimensions`() {
        val result = CompatibilityGate.checkInput(
            config = RuntimeTestFixtures.CONFIG_4x4_DEFAULT,
            input = RuntimeTestFixtures.inputWrongDimensions()
        )
        assertFalse(result.passed)
    }

    @Test
    fun `input-only check fails for wrong datatype`() {
        val result = CompatibilityGate.checkInput(
            config = RuntimeTestFixtures.CONFIG_4x4_DEFAULT,
            input = RuntimeTestFixtures.inputWrongDatatype()
        )
        assertFalse(result.passed)
    }

    // --------------------------------
    // CHANNEL COUNT CHECK
    // --------------------------------

    @Test
    fun `wrong channel count fails`() {
        val input3ch = RuntimeTestFixtures.input4x4Float32()
        val config4ch = PreprocessingConfig(
            version = PreprocessingVersion.V2,
            inputWidth = 4,
            inputHeight = 4,
            channelOrder = com.example.feedsense.analysis.ml.preprocess.PreprocessChannelOrder.RGBA,
            colorFormat = com.example.feedsense.analysis.ml.preprocess.ColorFormat.RGBA,
            alphaPolicy = com.example.feedsense.analysis.ml.preprocess.AlphaPolicy.PRESERVE,
            tensorType = com.example.feedsense.analysis.ml.preprocess.PreprocessingTensorType.FLOAT32,
            scale = 1.0 / 255.0,
            tensorLayout = com.example.feedsense.analysis.ml.preprocess.PreprocessingTensorLayout.NHWC
        )
        val result = CompatibilityGate.checkInput(config4ch, input3ch)
        assertFalse(result.passed)
        val failure = (result as CompatibilityCheckResult.Fail).failure
        assertEquals(RuntimeFailureCode.WRONG_CHANNEL_COUNT, failure.code)
    }

    // --------------------------------
    // PROVENANCE CHECK
    // --------------------------------

    @Test
    fun `provenance check passes for valid preprocessed input`() {
        val preprocessed = com.example.feedsense.analysis.ml.preprocess.PreprocessedInput(
            input = RuntimeTestFixtures.input4x4Float32(),
            configVersion = PreprocessingVersion.V2,
            configKey = "test-config-key",
            sourceWidth = 100,
            sourceHeight = 100,
            capturedOrientation = com.example.feedsense.analysis.ml.preprocess.FrameOrientation.DEG_0,
            privacyVersion = "privacy-processing-v1",
            policyMode = "RESEARCH",
            sanitizationStatus = "SANITIZED",
            evidenceId = "evidence-1",
            sessionId = "session-1",
            feedItemId = "item-1",
            fingerprint = "abc123"
        )
        val result = CompatibilityGate.checkProvenance(preprocessed)
        assertTrue(result.passed)
    }

    @Test
    fun `provenance check fails for missing privacy version`() {
        val preprocessed = com.example.feedsense.analysis.ml.preprocess.PreprocessedInput(
            input = RuntimeTestFixtures.input4x4Float32(),
            configVersion = PreprocessingVersion.V2,
            configKey = "test-config-key",
            sourceWidth = 100,
            sourceHeight = 100,
            capturedOrientation = com.example.feedsense.analysis.ml.preprocess.FrameOrientation.DEG_0,
            privacyVersion = "",
            policyMode = "RESEARCH",
            sanitizationStatus = "SANITIZED",
            evidenceId = "evidence-1",
            sessionId = "session-1",
            feedItemId = "item-1",
            fingerprint = "abc123"
        )
        val result = CompatibilityGate.checkProvenance(preprocessed)
        assertFalse(result.passed)
    }

    @Test
    fun `provenance check fails for missing fingerprint`() {
        val preprocessed = com.example.feedsense.analysis.ml.preprocess.PreprocessedInput(
            input = RuntimeTestFixtures.input4x4Float32(),
            configVersion = PreprocessingVersion.V2,
            configKey = "test-config-key",
            sourceWidth = 100,
            sourceHeight = 100,
            capturedOrientation = com.example.feedsense.analysis.ml.preprocess.FrameOrientation.DEG_0,
            privacyVersion = "privacy-processing-v1",
            policyMode = "RESEARCH",
            sanitizationStatus = "SANITIZED",
            evidenceId = "evidence-1",
            sessionId = "session-1",
            feedItemId = "item-1",
            fingerprint = ""
        )
        val result = CompatibilityGate.checkProvenance(preprocessed)
        assertFalse(result.passed)
    }
}
