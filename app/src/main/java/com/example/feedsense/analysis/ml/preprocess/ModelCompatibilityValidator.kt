package com.example.feedsense.analysis.ml.preprocess

import com.example.feedsense.analysis.ml.ModelMetadata
import com.example.feedsense.analysis.ml.ModelPixelFormat
import com.example.feedsense.analysis.ml.ModelTensorType
import com.example.feedsense.analysis.ml.repro.ArtifactAvailability
import com.example.feedsense.analysis.ml.repro.ReproQuantizationIdentity
import com.example.feedsense.analysis.ml.repro.QuantizationDepth
import com.example.feedsense.analysis.ml.repro.ReproArtifactIdentity
import com.example.feedsense.analysis.ml.repro.ReproRuntimeIdentity

// --------------------------------
// MODEL / PREPROCESSING COMPATIBILITY (8B-15-3)
// --------------------------------
//
// A clear compatibility check between the model's declared input
// contract and a PreprocessingConfig, so a mismatch is a FIRST
// discovered before any future inference (which is not executed
// here).
//
// Two layers:
//   A. Declared-contract check: PreprocessingConfig vs an 8B-14
//      ModelMetadata.inputSpec (a runnable concrete contract).
//   B. Reproduction check: PreprocessingConfig vs the 8B-15-2
//      ReproArtifactIdentity + ReproRuntimeIdentity + quantization.
//      While the artifact is PENDING, verifiable items are checked
//      and dimension/quantization pinning is reported as
//      UNVERIFIED rather than claimed.

/*
 * Severity of a compatibility issue.
 */
enum class IssueSeverity(val label: String) {
    ERROR("ERROR"),
    UNVERIFIED("UNVERIFIED")
}

data class CompatibilityIssue(
    val severity: IssueSeverity,
    val aspect: String,
    val detail: String
)

data class CompatibilityReport(
    val issues: List<CompatibilityIssue>
) {
    val hasErrors: Boolean get() = issues.any { it.severity == IssueSeverity.ERROR }
    val hasUnverified: Boolean get() = issues.any { it.severity == IssueSeverity.UNVERIFIED }
    val compatible: Boolean get() = issues.isEmpty() || issues.none { it.severity == IssueSeverity.ERROR }

    val summary: String
        get() = if (issues.isEmpty()) {
            "COMPATIBLE"
        } else if (hasErrors) {
            "INCOMPATIBLE (${issues.count { it.severity == IssueSeverity.ERROR }} error(s))"
        } else {
            "COMPATIBLE_PENDING_VERIFICATION (${issues.size} UNVERIFIED)"
        }
}

/*
 * Layer A: config vs a declared 8B-14 ModelMetadata input spec.
 */
object DeclaredContractChecker {

    fun check(
        config: PreprocessingConfig,
        metadata: ModelMetadata
    ): CompatibilityReport {
        val issues = mutableListOf<CompatibilityIssue>()
        val spec = metadata.inputSpec
        val configWidth = config.inputWidth
        val configHeight = config.inputHeight

        if (configWidth != spec.width) {
            issues += CompatibilityIssue(
                IssueSeverity.ERROR,
                "inputWidth",
                "config=$configWidth model=${spec.width}"
            )
        }
        if (configHeight != spec.height) {
            issues += CompatibilityIssue(
                IssueSeverity.ERROR,
                "inputHeight",
                "config=$configHeight model=${spec.height}"
            )
        }

        val configChannels = config.channels
        if (configChannels != spec.channels) {
            issues += CompatibilityIssue(
                IssueSeverity.ERROR,
                "channels",
                "config=$configChannels model=${spec.channels}"
            )
        }

        val configTensorType = when (config.tensorType) {
            PreprocessingTensorType.FLOAT32 -> ModelTensorType.FLOAT32
            PreprocessingTensorType.INT8 -> ModelTensorType.INT8
            PreprocessingTensorType.UINT8 -> ModelTensorType.UINT8
            else -> null
        }
        if (configTensorType != spec.tensorType) {
            issues += CompatibilityIssue(
                IssueSeverity.ERROR,
                "tensorType",
                "config=${configTensorType?.label ?: "?"} model=${spec.tensorType.label}"
            )
        }

        val configPixelFormat = when (config.channelOrder) {
            PreprocessChannelOrder.RGB, PreprocessChannelOrder.BGR -> ModelPixelFormat.RGB
            PreprocessChannelOrder.RGBA -> ModelPixelFormat.ARGB
            else -> null
        }
        if (configPixelFormat != spec.pixelFormat) {
            issues += CompatibilityIssue(
                IssueSeverity.ERROR,
                "pixelFormat",
                "config=${configPixelFormat?.label ?: "?"} model=${spec.pixelFormat.label}"
            )
        }

        val configMeanPresent = config.mean.isNotEmpty()
        val specMeanPresent = spec.normalization != null
        if (configMeanPresent != specMeanPresent) {
            issues += CompatibilityIssue(
                IssueSeverity.ERROR,
                "normalization",
                "config.present=$configMeanPresent model.present=$specMeanPresent"
            )
        }
        if (configMeanPresent && specMeanPresent) {
            if (config.mean != spec.normalization!!.mean ||
                config.std != spec.normalization.std
            ) {
                issues += CompatibilityIssue(
                    IssueSeverity.ERROR,
                    "normalization",
                    "mean/std values differ from the model's declared normalization"
                )
            }
        }
        if (config.tensorLayout != PreprocessingTensorLayout.NHWC) {
            issues += CompatibilityIssue(
                IssueSeverity.ERROR,
                "tensorLayout",
                "only NHWC is supported by the 8B-15-3 pipeline"
            )
        }
        return CompatibilityReport(issues)
    }
}

/*
 * Layer B: config vs the 8B-15-2 reproduction identities. Items
 * that depend on the not-yet-acquired artifact are reported as
 * UNVERIFIED, never claimed as compatible.
 */
object ReproductionContractChecker {

    fun check(
        config: PreprocessingConfig,
        artifact: ReproArtifactIdentity,
        runtime: ReproRuntimeIdentity,
        quantization: ReproQuantizationIdentity?
    ): CompatibilityReport {
        val issues = mutableListOf<CompatibilityIssue>()

        // Verifiable today.
        if (runtime.supportedPlatform.label != "ANDROID") {
            issues += CompatibilityIssue(
                IssueSeverity.ERROR,
                "runtimePlatform",
                "FeedSense targets Android; runtime declares ${runtime.supportedPlatform.label}"
            )
        }
        if (runtime.modelFormat.label != "TFLITE") {
            issues += CompatibilityIssue(
                IssueSeverity.ERROR,
                "runtimeFormat",
                "D1 runtime consumes TFLITE; declares ${runtime.modelFormat.label}"
            )
        }

        // Quantization contract (INT8 direction from 8B-15-1).
        if (quantization != null && quantization.depth != QuantizationDepth.INT8) {
            issues += CompatibilityIssue(
                IssueSeverity.ERROR,
                "quantizationDepth",
                "8B-15-1 selected INT8 target; contract declares ${quantization.depth.label}"
            )
        }

        // Artifact-dependent items.
        if (artifact.availability == ArtifactAvailability.AVAILABLE) {
            // The real artifact exists: pin all dimensions now.
            issues += CompatibilityIssue(
                IssueSeverity.UNVERIFIED,
                "artifactPinning",
                "artifact bytes present; dimension/datatype/normalization pinning " +
                    "from the actual tensor signature is required before inference"
            )
        } else {
            issues += CompatibilityIssue(
                IssueSeverity.UNVERIFIED,
                "artifactPinning",
                "artifact is ${artifact.availability.label}; exact input tensor " +
                    "dimensions / datatype / quantization mapping of the real " +
                    "artifact are UNVERIFIED until acquisition (8B-15-2 PENDING)"
            )
        }

        if (config.tensorType == PreprocessingTensorType.INT8 ||
            config.tensorType == PreprocessingTensorType.UINT8
        ) {
            issues += CompatibilityIssue(
                IssueSeverity.UNVERIFIED,
                "quantizationMapping",
                "input tensor scale/zeroPoint must be read from the acquired " +
                    "artifact, not assumed"
            )
        }

        return CompatibilityReport(issues)
    }
}