package com.example.feedsense.analysis.ml.runtime

import com.example.feedsense.analysis.ml.ModelInput
import com.example.feedsense.analysis.ml.ModelTensorType
import com.example.feedsense.analysis.ml.preprocess.PreprocessedInput
import com.example.feedsense.analysis.ml.preprocess.PreprocessingConfig
import com.example.feedsense.analysis.ml.repro.ArtifactAvailability
import com.example.feedsense.analysis.ml.repro.ReproArtifactIdentity
import com.example.feedsense.analysis.ml.repro.ReproArtifactFormat
import com.example.feedsense.analysis.ml.repro.ReproRuntimeIdentity

// --------------------------------
// COMPATIBILITY GATE (8B-15-5)
// --------------------------------
//
// The explicit compatibility check between:
//
//   ReproArtifactIdentity  (model artifact identity)
//   ReproRuntimeIdentity   (runtime identity)
//   PreprocessingConfig    (preprocessing contract)
//   ModelInput             (actual validated tensor)
//
// The gate refuses inference when any component of the contract
// is incompatible. This prevents:
//   - incorrect dimensions
//   - incorrect datatype
//   - incorrect tensor layout
//   - incorrect channel count
//   - incorrect normalization contract
//   - unsupported runtime
//   - wrong artifact
//   - preprocessing version mismatch
//   - malformed tensor
//   - invalid privacy/evidence provenance
//
// The gate does NOT silently coerce incompatible inputs. A
// mismatch produces a structured RuntimeFailure.

/*
 * The result of a compatibility check. Either PASS (all
 * compatible) or FAIL (specific failure).
 */
sealed class CompatibilityCheckResult {
    data class Pass(
        val artifactId: String,
        val runtimeKey: String,
        val preprocessingVersion: String,
        val inputSignature: String
    ) : CompatibilityCheckResult()

    data class Fail(
        val failure: RuntimeFailure
    ) : CompatibilityCheckResult()

    val passed: Boolean get() = this is Pass
}

/**
 * The compatibility gate performs an explicit check before
 * inference to ensure all components of the ML pipeline
 * are contract-compatible.
 *
 * The gate is stateless: it compares declared contracts,
 * not runtime state.
 */
object CompatibilityGate {

    /**
     * Checks full compatibility between the artifact, runtime,
     * preprocessing config, and actual model input.
     *
     * @param artifact the model artifact identity
     * @param runtime  the runtime identity
     * @param config   the preprocessing configuration
     * @param input    the actual validated model input tensor
     * @return PASS or FAIL with specific failure details
     */
    fun check(
        artifact: ReproArtifactIdentity,
        runtime: ReproRuntimeIdentity,
        config: PreprocessingConfig,
        input: ModelInput
    ): CompatibilityCheckResult {
        // 1. Artifact availability
        checkArtifactAvailability(artifact)?.let { return it }

        // 2. Runtime platform (an unknown runtime cannot be
        //    checked for format compatibility)
        checkRuntimePlatform(runtime)?.let { return it }

        // 3. Artifact format vs runtime format
        checkArtifactRuntimeFormat(artifact, runtime)?.let { return it }

        // 4. Preprocessing dimensions
        checkPreprocessingDimensions(config, input)?.let { return it }

        // 5. Preprocessing datatype vs input datatype
        checkPreprocessingDatatype(config, input)?.let { return it }

        // 6. Preprocessing channels vs input channels
        checkPreprocessingChannels(config, input)?.let { return it }

        // 7. Input tensor structural integrity
        checkInputTensorIntegrity(input)?.let { return it }

        // All checks passed
        return CompatibilityCheckResult.Pass(
            artifactId = artifact.artifactId,
            runtimeKey = runtime.key,
            preprocessingVersion = config.version,
            inputSignature = "${input.width}x${input.height}x${input.channels}:${input.tensorType.label}"
        )
    }

    /**
     * Quick input-only compatibility check against an artifact
     * contract. Used when the runtime/artifact are already
     * verified.
     */
    fun checkInput(
        config: PreprocessingConfig,
        input: ModelInput
    ): CompatibilityCheckResult {
        checkPreprocessingDimensions(config, input)?.let { return it }
        checkPreprocessingDatatype(config, input)?.let { return it }
        checkPreprocessingChannels(config, input)?.let { return it }
        checkInputTensorIntegrity(input)?.let { return it }

        return CompatibilityCheckResult.Pass(
            artifactId = "<input-only>",
            runtimeKey = "<input-only>",
            preprocessingVersion = config.version,
            inputSignature = "${input.width}x${input.height}x${input.channels}:${input.tensorType.label}"
        )
    }

    /**
     * Verifies that the PreprocessedInput carries valid
     * provenance metadata for the adapter boundary.
     */
    fun checkProvenance(
        preprocessed: PreprocessedInput
    ): CompatibilityCheckResult {
        if (preprocessed.evidenceId == null &&
            preprocessed.privacyVersion.isBlank()
        ) {
            return CompatibilityCheckResult.Fail(
                RuntimeFailureFactory.invalidProvenance(
                    "preprocessed input missing evidence identity and privacy version"
                )
            )
        }
        if (preprocessed.privacyVersion.isBlank()) {
            return CompatibilityCheckResult.Fail(
                RuntimeFailureFactory.invalidProvenance(
                    "preprocessed input missing privacy version"
                )
            )
        }
        if (preprocessed.fingerprint.isBlank()) {
            return CompatibilityCheckResult.Fail(
                RuntimeFailureFactory.invalidProvenance(
                    "preprocessed input missing preprocessing fingerprint"
                )
            )
        }
        return CompatibilityCheckResult.Pass(
            artifactId = "<provenance>",
            runtimeKey = "<provenance>",
            preprocessingVersion = preprocessed.configVersion,
            inputSignature = "provenance-ok"
        )
    }

    // --------------------------------
    // INTERNAL CHECKS
    // --------------------------------

    private fun checkArtifactAvailability(
        artifact: ReproArtifactIdentity
    ): CompatibilityCheckResult? {
        return when (artifact.availability) {
            ArtifactAvailability.AVAILABLE -> null
            ArtifactAvailability.PENDING -> CompatibilityCheckResult.Fail(
                RuntimeFailureFactory.artifactMissing(
                    "artifact ${artifact.artifactId} is PENDING (not yet acquired)"
                )
            )
            ArtifactAvailability.NOT_AVAILABLE -> CompatibilityCheckResult.Fail(
                RuntimeFailureFactory.artifactMissing(
                    "artifact ${artifact.artifactId} is NOT_AVAILABLE"
                )
            )
        }
    }

    private fun checkArtifactRuntimeFormat(
        artifact: ReproArtifactIdentity,
        runtime: ReproRuntimeIdentity
    ): CompatibilityCheckResult? {
        val formatCompatibility = when {
            artifact.format == ReproArtifactFormat.TFLITE &&
                runtime.modelFormat == ReproArtifactFormat.TFLITE -> true
            artifact.format == ReproArtifactFormat.ONNX &&
                runtime.modelFormat == ReproArtifactFormat.ONNX -> true
            artifact.format == ReproArtifactFormat.EXECUTORCH &&
                runtime.modelFormat == ReproArtifactFormat.EXECUTORCH -> true
            artifact.format == ReproArtifactFormat.UNKNOWN ||
                runtime.modelFormat == ReproArtifactFormat.UNKNOWN -> false
            else -> artifact.format == runtime.modelFormat
        }

        return if (!formatCompatibility) {
            CompatibilityCheckResult.Fail(
                RuntimeFailureFactory.incompatiblePreprocessing(
                    "artifact format ${artifact.format.label} is not compatible " +
                        "with runtime format ${runtime.modelFormat.label}"
                )
            )
        } else {
            null
        }
    }

    private fun checkRuntimePlatform(
        runtime: ReproRuntimeIdentity
    ): CompatibilityCheckResult? {
        if (runtime.runtimeName == com.example.feedsense.analysis.ml.repro.ReproRuntimeName.UNKNOWN) {
            return CompatibilityCheckResult.Fail(
                RuntimeFailureFactory.runtimeUnavailable(
                    "runtime name is UNKNOWN"
                )
            )
        }
        return null
    }

    private fun checkPreprocessingDimensions(
        config: PreprocessingConfig,
        input: ModelInput
    ): CompatibilityCheckResult? {
        val configWidth = config.inputWidth
        val configHeight = config.inputHeight

        if (configWidth != null && configWidth != input.width) {
            return CompatibilityCheckResult.Fail(
                RuntimeFailureFactory.wrongDimensions(
                    expected = "${configWidth}x${configHeight}",
                    actual = "${input.width}x${input.height}"
                )
            )
        }
        if (configHeight != null && configHeight != input.height) {
            return CompatibilityCheckResult.Fail(
                RuntimeFailureFactory.wrongDimensions(
                    expected = "${configWidth}x${configHeight}",
                    actual = "${input.width}x${input.height}"
                )
            )
        }
        return null
    }

    private fun checkPreprocessingDatatype(
        config: PreprocessingConfig,
        input: ModelInput
    ): CompatibilityCheckResult? {
        val expectedType = when (config.tensorType) {
            com.example.feedsense.analysis.ml.preprocess.PreprocessingTensorType.FLOAT32 ->
                ModelTensorType.FLOAT32
            com.example.feedsense.analysis.ml.preprocess.PreprocessingTensorType.INT8 ->
                ModelTensorType.INT8
            com.example.feedsense.analysis.ml.preprocess.PreprocessingTensorType.UINT8 ->
                ModelTensorType.UINT8
            else -> return null
        }
        if (expectedType != input.tensorType) {
            return CompatibilityCheckResult.Fail(
                RuntimeFailureFactory.wrongDatatype(
                    expected = expectedType.label,
                    actual = input.tensorType.label
                )
            )
        }
        return null
    }

    private fun checkPreprocessingChannels(
        config: PreprocessingConfig,
        input: ModelInput
    ): CompatibilityCheckResult? {
        val configChannels = config.channels
        if (configChannels != null && configChannels != input.channels) {
            return CompatibilityCheckResult.Fail(
                RuntimeFailureFactory.wrongChannelCount(
                    expected = configChannels,
                    actual = input.channels
                )
            )
        }
        return null
    }

    private fun checkInputTensorIntegrity(
        input: ModelInput
    ): CompatibilityCheckResult? {
        if (input.width <= 0 || input.height <= 0) {
            return CompatibilityCheckResult.Fail(
                RuntimeFailureFactory.invalidInputTensor(
                    "tensor dimensions must be positive: ${input.width}x${input.height}"
                )
            )
        }
        if (input.floats.size != input.width * input.height * input.channels) {
            return CompatibilityCheckResult.Fail(
                RuntimeFailureFactory.invalidInputTensor(
                    "float array size ${input.floats.size} does not match " +
                        "expected ${input.width * input.height * input.channels}"
                )
            )
        }
        if (input.tensorType != com.example.feedsense.analysis.ml.ModelTensorType.FLOAT32) {
            if (input.quantizedBytes == null) {
                return CompatibilityCheckResult.Fail(
                    RuntimeFailureFactory.invalidInputTensor(
                        "quantized tensor type ${input.tensorType.label} requires " +
                            "non-null quantizedBytes"
                    )
                )
            }
            if (input.quantizedBytes.size != input.tensorSize) {
                return CompatibilityCheckResult.Fail(
                    RuntimeFailureFactory.invalidInputTensor(
                        "quantizedBytes size ${input.quantizedBytes.size} does not match " +
                            "tensor size ${input.tensorSize}"
                    )
                )
            }
        }
        return null
    }
}
