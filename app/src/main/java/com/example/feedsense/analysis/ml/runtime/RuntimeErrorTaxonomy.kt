package com.example.feedsense.analysis.ml.runtime

// --------------------------------
// RUNTIME ERROR TAXONOMY (8B-15-5)
// --------------------------------
//
// Structured failure types for the runtime adapter boundary.
//
// Every failure is distinguishable and attributable to a specific
// phase: artifact loading, compatibility checking, input
// validation, inference execution, output validation, or
// resource/lifecycle management.
//
// This taxonomy replaces generic "AI_FAILED" strings with
// machine-parseable, human-readable codes. The vocabulary is
// intentionally exhaustive: if a failure mode exists, it has a
// code.
//
// Design rules:
//   - every enum carries an explicit .label for serialization
//   - UNKNOWN is always present but never the default
//   - no failure code is reused across phases

/*
 * Top-level runtime failure codes. Each code identifies a
 * distinct failure domain within the adapter boundary.
 */
enum class RuntimeFailureCode(val label: String) {
    // --------------------------------
    // ARTIFACT PHASE
    // --------------------------------

    /**
     * The model artifact file is not present at the expected
     * location.
     */
    ARTIFACT_MISSING("ARTIFACT_MISSING"),

    /**
     * The model artifact file exists but is corrupt (truncated,
     * unreadable, or fails integrity check).
     */
    ARTIFACT_CORRUPT("ARTIFACT_CORRUPT"),

    /**
     * The loaded artifact identity does not match the expected
     * artifact identity (wrong file, wrong version, wrong hash).
     */
    ARTIFACT_IDENTITY_MISMATCH("ARTIFACT_IDENTITY_MISMATCH"),

    // --------------------------------
    // RUNTIME PHASE
    // --------------------------------

    /**
     * The required runtime (e.g. LiteRT, ONNX Runtime) is not
     * available in the current environment.
     */
    RUNTIME_UNAVAILABLE("RUNTIME_UNAVAILABLE"),

    /**
     * The runtime exists but failed to initialize (e.g. native
     * library load failure, version incompatibility).
     */
    RUNTIME_INIT_FAILURE("RUNTIME_INIT_FAILURE"),

    /**
     * The runtime backend/delegate is not supported on this
     * device or configuration.
     */
    UNSUPPORTED_BACKEND("UNSUPPORTED_BACKEND"),

    // --------------------------------
    // COMPATIBILITY PHASE
    // --------------------------------

    /**
     * The preprocessing configuration does not match what the
     * model artifact expects.
     */
    INCOMPATIBLE_PREPROCESSING("INCOMPATIBLE_PREPROCESSING"),

    /**
     * The input tensor dimensions do not match the model's
     * expected input signature.
     */
    WRONG_DIMENSIONS("WRONG_DIMENSIONS"),

    /**
     * The input tensor datatype does not match the model's
     * expected datatype.
     */
    WRONG_DATATYPE("WRONG_DATATYPE"),

    /**
     * The input tensor layout does not match the model's
     * expected layout (e.g. NHWC vs NCHW).
     */
    WRONG_LAYOUT("WRONG_LAYOUT"),

    /**
     * The input tensor channel count does not match the model's
     * expected channel count.
     */
    WRONG_CHANNEL_COUNT("WRONG_CHANNEL_COUNT"),

    /**
     * The preprocessing version has drifted from what the model
     * was validated against.
     */
    PREPROCESSING_VERSION_MISMATCH("PREPROCESSING_VERSION_MISMATCH"),

    // --------------------------------
    // INPUT PHASE
    // --------------------------------

    /**
     * The input tensor is malformed (null data, wrong size,
     * invalid values).
     */
    INVALID_INPUT_TENSOR("INVALID_INPUT_TENSOR"),

    /**
     * The input does not carry valid privacy provenance metadata
     * (missing evidence chain, unsanitized source).
     */
    INVALID_PROVENANCE("INVALID_PROVENANCE"),

    // --------------------------------
    // INFERENCE PHASE
    // --------------------------------

    /**
     * The runtime threw an exception during inference execution.
     */
    INFERENCE_EXECUTION_FAILURE("INFERENCE_EXECUTION_FAILURE"),

    /**
     * The runtime reported resource exhaustion during inference.
     */
    INFERENCE_RESOURCE_EXHAUSTION("INFERENCE_RESOURCE_EXHAUSTION"),

    /**
     * Inference was attempted on a model that is not loaded or
     * has been released.
     */
    INFERENCE_NOT_READY("INFERENCE_NOT_READY"),

    // --------------------------------
    // OUTPUT PHASE
    // --------------------------------

    /**
     * The runtime returned an output that does not match the
     * model's expected output signature (wrong shape, wrong
     * count, wrong datatype).
     */
    INVALID_OUTPUT_TENSOR("INVALID_OUTPUT_TENSOR"),

    /**
     * The output values are outside the expected range or
     * contain non-finite values (NaN, Infinity).
     */
    INVALID_OUTPUT_VALUES("INVALID_OUTPUT_VALUES"),

    // --------------------------------
    // RESOURCE / LIFECYCLE PHASE
    // --------------------------------

    /**
     * A resource lifecycle violation occurred (e.g. inference
     * after close, double release).
     */
    LIFECYCLE_VIOLATION("LIFECYCLE_VIOLATION"),

    /**
     * Native resources could not be released (e.g. timeout,
     * OS refusal).
     */
    RESOURCE_RELEASE_FAILURE("RESOURCE_RELEASE_FAILURE"),

    // --------------------------------
    // UNSPECIFIED
    // --------------------------------

    /**
     * An unexpected failure that does not fit any other code.
     * Used only as a last resort.
     */
    UNKNOWN("UNKNOWN")
}

/*
 * Severity classification for runtime failures. Determines
 * whether the failure is recoverable (retry/load again) or
 * fatal (model cannot serve this session).
 */
enum class RuntimeFailureSeverity(val label: String) {
    /**
     * The failure is transient or recoverable. The adapter may
     * retry or the caller may attempt re-initialization.
     */
    RECOVERABLE("RECOVERABLE"),

    /**
     * The failure is fatal for this model/artifact
     * configuration. No retry will succeed without changing
     * the configuration.
     */
    FATAL("FATAL")
}

/*
 * Structured runtime failure descriptor. Immutable, safe for
 * serialization, and free of private content.
 *
 * The message is SAFE: it may describe the failure cause but
 * never carries pixels, OCR text, user content, or private
 * coordinates.
 */
data class RuntimeFailure(
    val code: RuntimeFailureCode,
    val severity: RuntimeFailureSeverity,
    val phase: RuntimePhase,
    val message: String,
    val cause: Throwable? = null
) {
    val isFatal: Boolean get() = severity == RuntimeFailureSeverity.FATAL
    val isRecoverable: Boolean get() = severity == RuntimeFailureSeverity.RECOVERABLE

    fun messageOnly(): String =
        "${code.label} [${phase.label}]: $message"
}

/*
 * The adapter phase where a failure occurred.
 */
enum class RuntimePhase(val label: String) {
    ARTIFACT_LOAD("ARTIFACT_LOAD"),
    RUNTIME_INIT("RUNTIME_INIT"),
    COMPATIBILITY_CHECK("COMPATIBILITY_CHECK"),
    INPUT_VALIDATION("INPUT_VALIDATION"),
    INFERENCE("INFERENCE"),
    OUTPUT_VALIDATION("OUTPUT_VALIDATION"),
    RESOURCE_LIFECYCLE("RESOURCE_LIFECYCLE"),
    UNKNOWN("UNKNOWN")
}

/*
 * Factory for creating standard RuntimeFailure instances.
 */
object RuntimeFailureFactory {

    fun artifactMissing(path: String): RuntimeFailure = RuntimeFailure(
        code = RuntimeFailureCode.ARTIFACT_MISSING,
        severity = RuntimeFailureSeverity.FATAL,
        phase = RuntimePhase.ARTIFACT_LOAD,
        message = "model artifact not found: $path"
    )

    fun artifactCorrupt(path: String, reason: String): RuntimeFailure = RuntimeFailure(
        code = RuntimeFailureCode.ARTIFACT_CORRUPT,
        severity = RuntimeFailureSeverity.FATAL,
        phase = RuntimePhase.ARTIFACT_LOAD,
        message = "model artifact corrupt at $path: $reason"
    )

    fun artifactIdentityMismatch(
        expected: String,
        actual: String
    ): RuntimeFailure = RuntimeFailure(
        code = RuntimeFailureCode.ARTIFACT_IDENTITY_MISMATCH,
        severity = RuntimeFailureSeverity.FATAL,
        phase = RuntimePhase.ARTIFACT_LOAD,
        message = "artifact identity mismatch: expected=$expected actual=$actual"
    )

    fun runtimeUnavailable(name: String): RuntimeFailure = RuntimeFailure(
        code = RuntimeFailureCode.RUNTIME_UNAVAILABLE,
        severity = RuntimeFailureSeverity.FATAL,
        phase = RuntimePhase.RUNTIME_INIT,
        message = "runtime unavailable: $name"
    )

    fun runtimeInitFailure(message: String): RuntimeFailure = RuntimeFailure(
        code = RuntimeFailureCode.RUNTIME_INIT_FAILURE,
        severity = RuntimeFailureSeverity.FATAL,
        phase = RuntimePhase.RUNTIME_INIT,
        message = "runtime initialization failed: $message"
    )

    fun incompatiblePreprocessing(detail: String): RuntimeFailure = RuntimeFailure(
        code = RuntimeFailureCode.INCOMPATIBLE_PREPROCESSING,
        severity = RuntimeFailureSeverity.FATAL,
        phase = RuntimePhase.COMPATIBILITY_CHECK,
        message = "incompatible preprocessing: $detail"
    )

    fun wrongDimensions(
        expected: String,
        actual: String
    ): RuntimeFailure = RuntimeFailure(
        code = RuntimeFailureCode.WRONG_DIMENSIONS,
        severity = RuntimeFailureSeverity.FATAL,
        phase = RuntimePhase.COMPATIBILITY_CHECK,
        message = "wrong dimensions: expected=$expected actual=$actual"
    )

    fun wrongDatatype(
        expected: String,
        actual: String
    ): RuntimeFailure = RuntimeFailure(
        code = RuntimeFailureCode.WRONG_DATATYPE,
        severity = RuntimeFailureSeverity.FATAL,
        phase = RuntimePhase.COMPATIBILITY_CHECK,
        message = "wrong datatype: expected=$expected actual=$actual"
    )

    fun wrongLayout(
        expected: String,
        actual: String
    ): RuntimeFailure = RuntimeFailure(
        code = RuntimeFailureCode.WRONG_LAYOUT,
        severity = RuntimeFailureSeverity.FATAL,
        phase = RuntimePhase.COMPATIBILITY_CHECK,
        message = "wrong layout: expected=$expected actual=$actual"
    )

    fun wrongChannelCount(
        expected: Int,
        actual: Int
    ): RuntimeFailure = RuntimeFailure(
        code = RuntimeFailureCode.WRONG_CHANNEL_COUNT,
        severity = RuntimeFailureSeverity.FATAL,
        phase = RuntimePhase.COMPATIBILITY_CHECK,
        message = "wrong channel count: expected=$expected actual=$actual"
    )

    fun preprocessingVersionMismatch(
        expected: String,
        actual: String
    ): RuntimeFailure = RuntimeFailure(
        code = RuntimeFailureCode.PREPROCESSING_VERSION_MISMATCH,
        severity = RuntimeFailureSeverity.FATAL,
        phase = RuntimePhase.COMPATIBILITY_CHECK,
        message = "preprocessing version mismatch: expected=$expected actual=$actual"
    )

    fun invalidInputTensor(detail: String): RuntimeFailure = RuntimeFailure(
        code = RuntimeFailureCode.INVALID_INPUT_TENSOR,
        severity = RuntimeFailureSeverity.FATAL,
        phase = RuntimePhase.INPUT_VALIDATION,
        message = "invalid input tensor: $detail"
    )

    fun invalidProvenance(detail: String): RuntimeFailure = RuntimeFailure(
        code = RuntimeFailureCode.INVALID_PROVENANCE,
        severity = RuntimeFailureSeverity.FATAL,
        phase = RuntimePhase.INPUT_VALIDATION,
        message = "invalid provenance: $detail"
    )

    fun inferenceExecutionFailure(
        message: String,
        cause: Throwable? = null
    ): RuntimeFailure = RuntimeFailure(
        code = RuntimeFailureCode.INFERENCE_EXECUTION_FAILURE,
        severity = RuntimeFailureSeverity.RECOVERABLE,
        phase = RuntimePhase.INFERENCE,
        message = "inference execution failed: $message",
        cause = cause
    )

    fun inferenceResourceExhaustion(
        detail: String
    ): RuntimeFailure = RuntimeFailure(
        code = RuntimeFailureCode.INFERENCE_RESOURCE_EXHAUSTION,
        severity = RuntimeFailureSeverity.RECOVERABLE,
        phase = RuntimePhase.INFERENCE,
        message = "inference resource exhaustion: $detail"
    )

    fun inferenceNotReady(detail: String): RuntimeFailure = RuntimeFailure(
        code = RuntimeFailureCode.INFERENCE_NOT_READY,
        severity = RuntimeFailureSeverity.RECOVERABLE,
        phase = RuntimePhase.INFERENCE,
        message = "inference not ready: $detail"
    )

    fun invalidOutputTensor(detail: String): RuntimeFailure = RuntimeFailure(
        code = RuntimeFailureCode.INVALID_OUTPUT_TENSOR,
        severity = RuntimeFailureSeverity.FATAL,
        phase = RuntimePhase.OUTPUT_VALIDATION,
        message = "invalid output tensor: $detail"
    )

    fun invalidOutputValues(detail: String): RuntimeFailure = RuntimeFailure(
        code = RuntimeFailureCode.INVALID_OUTPUT_VALUES,
        severity = RuntimeFailureSeverity.FATAL,
        phase = RuntimePhase.OUTPUT_VALIDATION,
        message = "invalid output values: $detail"
    )

    fun lifecycleViolation(detail: String): RuntimeFailure = RuntimeFailure(
        code = RuntimeFailureCode.LIFECYCLE_VIOLATION,
        severity = RuntimeFailureSeverity.RECOVERABLE,
        phase = RuntimePhase.RESOURCE_LIFECYCLE,
        message = "lifecycle violation: $detail"
    )

    fun unknown(message: String, cause: Throwable? = null): RuntimeFailure = RuntimeFailure(
        code = RuntimeFailureCode.UNKNOWN,
        severity = RuntimeFailureSeverity.RECOVERABLE,
        phase = RuntimePhase.UNKNOWN,
        message = "unknown runtime failure: $message",
        cause = cause
    )
}
