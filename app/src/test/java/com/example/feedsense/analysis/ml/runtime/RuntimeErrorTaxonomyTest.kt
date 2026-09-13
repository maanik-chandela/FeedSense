package com.example.feedsense.analysis.ml.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-5.
 *
 * Runtime error taxonomy: exhaustive failure codes, severity
 * classification, and structured failure creation.
 */
class RuntimeErrorTaxonomyTest {

    // --------------------------------
    // FAILURE CODES
    // --------------------------------

    @Test
    fun `all failure codes have explicit labels`() {
        for (code in RuntimeFailureCode.entries) {
            assertTrue(
                "failure code ${code.name} must have non-blank label",
                code.label.isNotBlank()
            )
            assertEquals(code.name, code.label)
        }
    }

    @Test
    fun `all failure codes are distinct`() {
        val labels = RuntimeFailureCode.entries.map { it.label }
        assertEquals(
            "failure code labels must be unique",
            labels.size,
            labels.toSet().size
        )
    }

    @Test
    fun `all phases have explicit labels`() {
        for (phase in RuntimePhase.entries) {
            assertTrue(
                "phase ${phase.name} must have non-blank label",
                phase.label.isNotBlank()
            )
        }
    }

    @Test
    fun `all severity levels have explicit labels`() {
        for (severity in RuntimeFailureSeverity.entries) {
            assertTrue(
                "severity ${severity.name} must have non-blank label",
                severity.label.isNotBlank()
            )
        }
    }

    // --------------------------------
    // STRUCTURED FAILURES
    // --------------------------------

    @Test
    fun `artifact missing creates correct failure`() {
        val failure = RuntimeFailureFactory.artifactMissing("/path/to/model.tflite")
        assertEquals(RuntimeFailureCode.ARTIFACT_MISSING, failure.code)
        assertEquals(RuntimeFailureSeverity.FATAL, failure.severity)
        assertEquals(RuntimePhase.ARTIFACT_LOAD, failure.phase)
        assertTrue(failure.isFatal)
        assertFalse(failure.isRecoverable)
        assertTrue(failure.message.contains("/path/to/model.tflite"))
    }

    @Test
    fun `artifact corrupt creates correct failure`() {
        val failure = RuntimeFailureFactory.artifactCorrupt("model.tflite", "truncated header")
        assertEquals(RuntimeFailureCode.ARTIFACT_CORRUPT, failure.code)
        assertEquals(RuntimeFailureSeverity.FATAL, failure.severity)
        assertTrue(failure.message.contains("truncated header"))
    }

    @Test
    fun `artifact identity mismatch creates correct failure`() {
        val failure = RuntimeFailureFactory.artifactIdentityMismatch("expected-key", "actual-key")
        assertEquals(RuntimeFailureCode.ARTIFACT_IDENTITY_MISMATCH, failure.code)
        assertEquals(RuntimeFailureSeverity.FATAL, failure.severity)
    }

    @Test
    fun `runtime unavailable creates correct failure`() {
        val failure = RuntimeFailureFactory.runtimeUnavailable("LiteRT")
        assertEquals(RuntimeFailureCode.RUNTIME_UNAVAILABLE, failure.code)
        assertEquals(RuntimeFailureSeverity.FATAL, failure.severity)
        assertEquals(RuntimePhase.RUNTIME_INIT, failure.phase)
    }

    @Test
    fun `runtime init failure creates correct failure`() {
        val failure = RuntimeFailureFactory.runtimeInitFailure("native library not found")
        assertEquals(RuntimeFailureCode.RUNTIME_INIT_FAILURE, failure.code)
        assertEquals(RuntimeFailureSeverity.FATAL, failure.severity)
    }

    @Test
    fun `incompatible preprocessing creates correct failure`() {
        val failure = RuntimeFailureFactory.incompatiblePreprocessing("version mismatch")
        assertEquals(RuntimeFailureCode.INCOMPATIBLE_PREPROCESSING, failure.code)
        assertEquals(RuntimeFailureSeverity.FATAL, failure.severity)
        assertEquals(RuntimePhase.COMPATIBILITY_CHECK, failure.phase)
    }

    @Test
    fun `wrong dimensions creates correct failure`() {
        val failure = RuntimeFailureFactory.wrongDimensions("224x224", "128x128")
        assertEquals(RuntimeFailureCode.WRONG_DIMENSIONS, failure.code)
        assertEquals(RuntimeFailureSeverity.FATAL, failure.severity)
    }

    @Test
    fun `wrong datatype creates correct failure`() {
        val failure = RuntimeFailureFactory.wrongDatatype("FLOAT32", "INT8")
        assertEquals(RuntimeFailureCode.WRONG_DATATYPE, failure.code)
        assertEquals(RuntimeFailureSeverity.FATAL, failure.severity)
    }

    @Test
    fun `wrong layout creates correct failure`() {
        val failure = RuntimeFailureFactory.wrongLayout("NHWC", "NCHW")
        assertEquals(RuntimeFailureCode.WRONG_LAYOUT, failure.code)
        assertEquals(RuntimeFailureSeverity.FATAL, failure.severity)
    }

    @Test
    fun `wrong channel count creates correct failure`() {
        val failure = RuntimeFailureFactory.wrongChannelCount(3, 1)
        assertEquals(RuntimeFailureCode.WRONG_CHANNEL_COUNT, failure.code)
        assertEquals(RuntimeFailureSeverity.FATAL, failure.severity)
    }

    @Test
    fun `preprocessing version mismatch creates correct failure`() {
        val failure = RuntimeFailureFactory.preprocessingVersionMismatch("preprocess-v1", "preprocess-v2")
        assertEquals(RuntimeFailureCode.PREPROCESSING_VERSION_MISMATCH, failure.code)
        assertEquals(RuntimeFailureSeverity.FATAL, failure.severity)
    }

    @Test
    fun `invalid input tensor creates correct failure`() {
        val failure = RuntimeFailureFactory.invalidInputTensor("null float array")
        assertEquals(RuntimeFailureCode.INVALID_INPUT_TENSOR, failure.code)
        assertEquals(RuntimeFailureSeverity.FATAL, failure.severity)
        assertEquals(RuntimePhase.INPUT_VALIDATION, failure.phase)
    }

    @Test
    fun `invalid provenance creates correct failure`() {
        val failure = RuntimeFailureFactory.invalidProvenance("missing privacy version")
        assertEquals(RuntimeFailureCode.INVALID_PROVENANCE, failure.code)
        assertEquals(RuntimeFailureSeverity.FATAL, failure.severity)
    }

    @Test
    fun `inference execution failure creates correct failure`() {
        val cause = RuntimeException("boom")
        val failure = RuntimeFailureFactory.inferenceExecutionFailure("runtime crash", cause)
        assertEquals(RuntimeFailureCode.INFERENCE_EXECUTION_FAILURE, failure.code)
        assertEquals(RuntimeFailureSeverity.RECOVERABLE, failure.severity)
        assertEquals(RuntimePhase.INFERENCE, failure.phase)
        assertFalse(failure.isFatal)
        assertTrue(failure.isRecoverable)
        assertEquals(cause, failure.cause)
    }

    @Test
    fun `inference resource exhaustion creates correct failure`() {
        val failure = RuntimeFailureFactory.inferenceResourceExhaustion("out of memory")
        assertEquals(RuntimeFailureCode.INFERENCE_RESOURCE_EXHAUSTION, failure.code)
        assertEquals(RuntimeFailureSeverity.RECOVERABLE, failure.severity)
    }

    @Test
    fun `inference not ready creates correct failure`() {
        val failure = RuntimeFailureFactory.inferenceNotReady("model not loaded")
        assertEquals(RuntimeFailureCode.INFERENCE_NOT_READY, failure.code)
        assertEquals(RuntimeFailureSeverity.RECOVERABLE, failure.severity)
    }

    @Test
    fun `invalid output tensor creates correct failure`() {
        val failure = RuntimeFailureFactory.invalidOutputTensor("wrong shape")
        assertEquals(RuntimeFailureCode.INVALID_OUTPUT_TENSOR, failure.code)
        assertEquals(RuntimeFailureSeverity.FATAL, failure.severity)
        assertEquals(RuntimePhase.OUTPUT_VALIDATION, failure.phase)
    }

    @Test
    fun `invalid output values creates correct failure`() {
        val failure = RuntimeFailureFactory.invalidOutputValues("NaN at index 0")
        assertEquals(RuntimeFailureCode.INVALID_OUTPUT_VALUES, failure.code)
        assertEquals(RuntimeFailureSeverity.FATAL, failure.severity)
    }

    @Test
    fun `lifecycle violation creates correct failure`() {
        val failure = RuntimeFailureFactory.lifecycleViolation("inference after close")
        assertEquals(RuntimeFailureCode.LIFECYCLE_VIOLATION, failure.code)
        assertEquals(RuntimeFailureSeverity.RECOVERABLE, failure.severity)
        assertEquals(RuntimePhase.RESOURCE_LIFECYCLE, failure.phase)
    }

    @Test
    fun `unknown failure creates correct failure`() {
        val failure = RuntimeFailureFactory.unknown("unexpected error")
        assertEquals(RuntimeFailureCode.UNKNOWN, failure.code)
        assertEquals(RuntimeFailureSeverity.RECOVERABLE, failure.severity)
        assertEquals(RuntimePhase.UNKNOWN, failure.phase)
    }

    // --------------------------------
    // FAILURE MESSAGE FORMATTING
    // --------------------------------

    @Test
    fun `messageOnly formats code and phase`() {
        val failure = RuntimeFailureFactory.artifactMissing("model.bin")
        val msg = failure.messageOnly()
        assertTrue(msg.contains("ARTIFACT_MISSING"))
        assertTrue(msg.contains("ARTIFACT_LOAD"))
        assertTrue(msg.contains("model.bin"))
    }

    @Test
    fun `failure is serializable-safe`() {
        val failure = RuntimeFailureFactory.incompatiblePreprocessing("detail")
        // Verify all fields are non-null and safe for serialization
        assertNotNull(failure.code)
        assertNotNull(failure.severity)
        assertNotNull(failure.phase)
        assertNotNull(failure.message)
        assertTrue(failure.message.isNotBlank())
    }
}
