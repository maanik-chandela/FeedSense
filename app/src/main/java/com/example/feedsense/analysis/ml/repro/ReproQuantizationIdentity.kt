package com.example.feedsense.analysis.ml.repro

// --------------------------------
// QUANTIZATION IDENTITY (8B-15-2)
// --------------------------------
//
// Explicit identity of the quantization applied to the model.
//
// Quantization QUALITY / accuracy are NOT represented here: those
// belong to experimental milestones. This records *which*
// quantization was applied and with what tool/configuration so it
// is reproducible. NONE/INT8/... follow the 8B-15-1 vocabulary.

/*
 * Quantization depth of the artifact.
 */
enum class QuantizationDepth(override val label: String) : ReprLabeled {
    NONE("NONE"),
    FP32("FP32"),
    FP16("FP16"),
    INT8("INT8"),
    INT4("INT4"),
    OTHER("OTHER"),
    UNKNOWN("UNKNOWN")
}

/*
 * Quantization method.
 */
enum class QuantizationMethod(override val label: String) : ReprLabeled {
    NONE("NONE"),
    POST_TRAINING_INTEGER("POST_TRAINING_INTEGER"),
    POST_TRAINING_DYNAMIC("POST_TRAINING_DYNAMIC"),
    POST_TRAINING_FP16("POST_TRAINING_FP16"),
    QUANTIZATION_AWARE_TRAINING("QUANTIZATION_AWARE_TRAINING"),
    WEIGHT_ONLY("WEIGHT_ONLY"),
    OTHER("OTHER"),
    UNKNOWN("UNKNOWN")
}

/*
 * Whether weights, activations, or both are quantized.
 */
enum class QuantizationScope(override val label: String) : ReprLabeled {
    NONE("NONE"),
    WEIGHTS_ONLY("WEIGHTS_ONLY"),
    WEIGHTS_AND_ACTIVATIONS("WEIGHTS_AND_ACTIVATIONS"),
    UNKNOWN("UNKNOWN")
}

/*
 * Immutable identity of the quantization configuration.
 */
data class ReproQuantizationIdentity(
    val depth: QuantizationDepth,
    val method: QuantizationMethod = QuantizationMethod.UNKNOWN,
    val scope: QuantizationScope = QuantizationScope.UNKNOWN,
    val calibrationMethod: String? = null,
    val calibrationDatasetId: String? = null,
    val toolName: String? = null,
    val toolVersion: String? = null,
    val sourceArtifactHash: String? = null,
    val resultingArtifactHash: String? = null
) {

    init {
        sourceArtifactHash?.let { s ->
            require(ReproArtifactIdentity.isSha256Hex(s)) { "sourceArtifactHash must be SHA-256 hex" }
        }
        resultingArtifactHash?.let { s ->
            require(ReproArtifactIdentity.isSha256Hex(s)) { "resultingArtifactHash must be SHA-256 hex" }
        }
        require(!(depth == QuantizationDepth.NONE &&
            (method != QuantizationMethod.NONE || scope != QuantizationScope.NONE))) {
            "a NONE quantization depth cannot carry a non-NONE method/scope"
        }
    }

    val key: String
        get() = "${depth.label}:${method.label}:${scope.label}:${toolName ?: "-"}:${toolVersion ?: "-"}"
}
