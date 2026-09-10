package com.example.feedsense.analysis.ml.runtime

import com.example.feedsense.analysis.ml.ModelTensorType
import com.example.feedsense.analysis.ml.repro.ReproArtifactIdentity
import com.example.feedsense.analysis.ml.repro.ReproRuntimeIdentity

// --------------------------------
// ADAPTER RAW MODEL OUTPUT (8B-15-5)
// --------------------------------
//
// The runtime-independent raw output representation returned by
// the adapter after inference.
//
// This is NOT semantic interpretation. The output contains:
//   - raw tensor values (not category labels)
//   - tensor metadata (shape, datatype)
//   - execution metadata (duration, status)
//   - provenance (artifact, runtime, preprocessing)
//
// This output does NOT contain:
//   - category classification
//   - recommendation labels
//   - confidence thresholds
//   - user preference inference
//   - semantic interpretation of any kind
//
// Semantic interpretation belongs to a later milestone.

/*
 * Execution status of the inference attempt.
 */
enum class InferenceExecutionStatus(val label: String) {
    SUCCESS("SUCCESS"),
    FAILURE("FAILURE")
}

/**
 * Structured output tensor metadata.
 *
 * Describes the shape, datatype, and value characteristics of
 * one output tensor from the model runtime.
 */
data class OutputTensorMetadata(
    val tensorIndex: Int,
    val shape: List<Int>,
    val datatype: String,
    val layout: String = "unknown",
    val elementCount: Int = shape.fold(1, Int::times),
    val valueRangeMin: Double? = null,
    val valueRangeMax: Double? = null
)

/**
 * The runtime-independent raw output from model inference.
 *
 * Every field is either deterministic or operationally safe
 * (no pixels, no OCR, no private content).
 *
 * @param artifactIdentity the exact artifact that produced this
 *   output
 * @param runtimeIdentity  the runtime that executed inference
 * @param preprocessingVersion the preprocessing version that
 *   produced the input
 * @param inputSignature   the input tensor signature
 *   (e.g. "224x224x3:FLOAT32")
 * @param outputTensors    metadata about each output tensor
 * @param rawOutputValues  the raw output values organized by
 *   tensor index. For classification models, this is typically
 *   a single tensor of class probabilities.
 * @param executionStatus  whether inference succeeded or failed
 * @param failure          structured failure if execution failed
 * @param loadDurationMs   model load duration
 * @param warmupDurationMs model warm-up duration (0 if no
 *   warm-up)
 * @param inferenceDurationMs inference execution duration
 * @param outputValidationDurationMs output validation duration
 * @param inferenceTimestampMs timestamp of the inference call
 * @param deterministicHash SHA-256 of the serialized output
 *   tensor for determinism verification
 */
data class AdapterRawModelOutput(
    val artifactIdentity: ReproArtifactIdentity,
    val runtimeIdentity: ReproRuntimeIdentity,
    val preprocessingVersion: String,
    val inputSignature: String,
    val outputTensors: List<OutputTensorMetadata>,
    val rawOutputValues: Map<Int, FloatArray>,
    val executionStatus: InferenceExecutionStatus,
    val failure: RuntimeFailure? = null,
    val loadDurationMs: Long = 0L,
    val warmupDurationMs: Long = 0L,
    val inferenceDurationMs: Long = 0L,
    val outputValidationDurationMs: Long = 0L,
    val inferenceTimestampMs: Long = 0L,
    val deterministicHash: String? = null
) {
    val succeeded: Boolean get() = executionStatus == InferenceExecutionStatus.SUCCESS
    val failed: Boolean get() = executionStatus == InferenceExecutionStatus.FAILURE
    val totalDurationMs: Long get() = loadDurationMs + warmupDurationMs + inferenceDurationMs

    /**
     * Returns the primary output tensor values (index 0), or
     * null if no output was produced.
     */
    fun primaryOutput(): FloatArray? = rawOutputValues[0]

    /**
     * Returns the total number of output tensors.
     */
    val outputTensorCount: Int get() = outputTensors.size
}

/**
 * Validates that an AdapterRawModelOutput matches the expected
 * output contract.
 */
object OutputTensorValidator {

    /**
     * Expected output shape for the selected model. null values
     * mean "any value is acceptable" for that dimension.
     */
    data class ExpectedOutputContract(
        val expectedTensorCount: Int = 1,
        val expectedShape: List<Int?>? = null,
        val expectedDatatype: String? = "FLOAT32",
        val expectedValueRangeMin: Double? = 0.0,
        val expectedValueRangeMax: Double? = 1.0,
        val allowNonFinite: Boolean = false
    )

    /*
     * Result of output validation.
     */
    sealed class OutputValidationResult {
        data class Valid(
            val tensorCount: Int,
            val shapeMatch: Boolean,
            val datatypeMatch: Boolean,
            val valueRangeValid: Boolean
        ) : OutputValidationResult()

        data class Invalid(
            val failure: RuntimeFailure
        ) : OutputValidationResult()

        val isValid: Boolean get() = this is Valid
    }

    /**
     * Validates the raw model output against the expected
     * contract.
     */
    fun validate(
        output: AdapterRawModelOutput,
        contract: ExpectedOutputContract = ExpectedOutputContract()
    ): OutputValidationResult {
        if (output.failed) {
            return OutputValidationResult.Invalid(
                RuntimeFailureFactory.invalidOutputTensor(
                    "cannot validate a failed inference output"
                )
            )
        }

        // Check tensor count
        if (output.outputTensorCount != contract.expectedTensorCount) {
            return OutputValidationResult.Invalid(
                RuntimeFailureFactory.invalidOutputTensor(
                    "expected ${contract.expectedTensorCount} output tensors, " +
                        "got ${output.outputTensorCount}"
                )
            )
        }

        // Check shape for each tensor
        var shapeMatch = true
        for ((idx, tensor) in output.outputTensors.withIndex()) {
            if (contract.expectedShape != null) {
                val expected = contract.expectedShape.filterNotNull()
                if (tensor.shape != expected) {
                    shapeMatch = false
                    return OutputValidationResult.Invalid(
                        RuntimeFailureFactory.invalidOutputTensor(
                            "tensor $idx shape mismatch: expected=$expected " +
                                "actual=${tensor.shape}"
                        )
                    )
                }
            }
        }

        // Check datatype
        var datatypeMatch = true
        if (contract.expectedDatatype != null) {
            for ((idx, tensor) in output.outputTensors.withIndex()) {
                if (tensor.datatype != contract.expectedDatatype) {
                    datatypeMatch = false
                    return OutputValidationResult.Invalid(
                        RuntimeFailureFactory.invalidOutputTensor(
                            "tensor $idx datatype mismatch: " +
                                "expected=${contract.expectedDatatype} " +
                                "actual=${tensor.datatype}"
                        )
                    )
                }
            }
        }

        // Check value range and finiteness
        var valueRangeValid = true
        if (!contract.allowNonFinite) {
            for ((idx, values) in output.rawOutputValues) {
                for ((i, v) in values.withIndex()) {
                    if (!v.isFinite()) {
                        return OutputValidationResult.Invalid(
                            RuntimeFailureFactory.invalidOutputValues(
                                "tensor $idx contains non-finite value at " +
                                    "index $i: $v"
                            )
                        )
                    }
                }
            }
        }

        if (contract.expectedValueRangeMin != null ||
            contract.expectedValueRangeMax != null
        ) {
            for ((idx, values) in output.rawOutputValues) {
                for (v in values) {
                    if (contract.expectedValueRangeMin != null &&
                        v < contract.expectedValueRangeMin
                    ) {
                        valueRangeValid = false
                        return OutputValidationResult.Invalid(
                            RuntimeFailureFactory.invalidOutputValues(
                                "tensor $idx value $v below expected min " +
                                    "${contract.expectedValueRangeMin}"
                            )
                        )
                    }
                    if (contract.expectedValueRangeMax != null &&
                        v > contract.expectedValueRangeMax
                    ) {
                        valueRangeValid = false
                        return OutputValidationResult.Invalid(
                            RuntimeFailureFactory.invalidOutputValues(
                                "tensor $idx value $v above expected max " +
                                    "${contract.expectedValueRangeMax}"
                            )
                        )
                    }
                }
            }
        }

        return OutputValidationResult.Valid(
            tensorCount = output.outputTensorCount,
            shapeMatch = shapeMatch,
            datatypeMatch = datatypeMatch,
            valueRangeValid = valueRangeValid
        )
    }
}
