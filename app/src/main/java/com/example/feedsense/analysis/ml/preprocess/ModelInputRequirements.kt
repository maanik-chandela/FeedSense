package com.example.feedsense.analysis.ml.preprocess

import com.example.feedsense.analysis.ml.ModelTensorType
import com.example.feedsense.analysis.ml.ModelPixelFormat

// --------------------------------
// SELECTED MODEL INPUT REQUIREMENTS (8B-15-3)
// --------------------------------
//
// The source of truth for the selected model's expected input is
// the 8B-15-1 decision record and the 8B-15-2 reproducibility
// contract (ReproContractFactory):
//
//   - runtime   : LiteRT (TFLite), XNNPACK CPU, Android (D1)
//   - model     : MobileNetV4-Conv-S, INT8 target (A1, SHORTLISTED)
//   - fallback  : EfficientNet-Lite (A2, CONDITIONAL)
//   - artifact  : ARTIFACT_PENDING - the real `.tflite` has NOT
//                 been acquired (8B-15-2).
//
// Because the exact artifact is not available, the following are
// authoritative FACTS about the model family (verified against the
// MobileNetV4 paper and the official converted model family) vs
// values that remain UNKNOWN until the actual artifact is pinned.
//
// The milestone rule is explicit: do NOT infer values merely
// because they are common for the family. Where a value cannot be
// verified against the actual artifact/authoritative source it is
// marked UNKNOWN and the source/experiment required to resolve it
// is documented.

object ModelInputRequirements {

    /*
     * Verified against the authoritative MobileNetV4 family
     * (tensorflow/models official vision checkpoints converted to
     * TFLite at 224x224; the MobileNetV4-Conv-Small model card
     * reports Input Shape (1, 224, 224, 3) NHWC).
     *
     * These are the values the reference converted model family
     * uses. They are HIGH confidence but MUST be re-verified when
     * the actual artifact is acquired, because the FeedSense INT8
     * conversion may choose different input quantization.
     */
    data class Verified(
        val width: Int = 224,
        val height: Int = 224,
        val channels: Int = 3,
        val colorFormat: ColorFormat = ColorFormat.RGB,
        val channelOrder: PreprocessChannelOrder = PreprocessChannelOrder.RGB,
        val tensorLayout: PreprocessingTensorLayout = PreprocessingTensorLayout.NHWC,
        val scaleToZeroOne: Double = 1.0 / 255.0,
        val referenceMean: List<Double> = listOf(0.485, 0.456, 0.406),
        val referenceStd: List<Double> = listOf(0.229, 0.224, 0.225)
    ) {
        val source: String = "MobileNetV4 paper (arXiv:2404.10518) + " +
            "official converted MobileNetV4-Conv TFLite family (224x224, RGB, NHWC, " +
            "scale [0,1], ImageNet mean/std in reference exporters)"
    }

    /*
     * UNKNOWN requirements. Each entry documents what source or
     * experiment is required to resolve it.
     */
    enum class UnknownRequirement(val resolutionNeeded: String) {

        /*
         * The exact input tensor type of the ACQUIRED artifact.
         * Reference MobileNetV4 exporters default to float32 input
         * with an internal QUANTIZE node; a fully-quantized INT8
         * input (tflite converter inference_input_type=int8) is a
         * converter choice. Unknowable until the artifact exists.
         */
        INPUT_TENSOR_TYPE(
            "Acquire the actual `.tflite` artifact (8B-15-2 PENDING) and " +
                "read its input tensor from the model metadata / interpreter. " +
                "Record scale+zeroPoint from the quantization parameters."
        ),

        /*
         * The INT8 input quantization mapping (scale/zeroPoint) when
         * the acquired artifact exposes an INT8 input tensor.
         */
        INT8_QUANTIZATION_MAPPING(
            "Read the quantization parameters from the acquired artifact. " +
                "Do NOT reuse a generic tutorial mapping."
        ),

        /*
         * Whether the actual artifact accepts dynamic batch / dynamic
         * dimensions. 8B-15-3 uses batch size 1 regardless.
         */
        DYNAMIC_DIMENSIONS(
            "Inspect the acquired artifact's tensor shape signature " +
                "(static [1,224,224,3] vs [1,None,None,3])."
        ),

        /*
         * Normalization: the reference exporters standardize with
         * ImageNet mean/std, but the actual FeedSense model may have
         * been trained/converted with a different mapping. The mean/
         * std only matter for the pre-trained ImageNet head; for a
         * fine-tuned model the training preprocessing governs.
         */
        NORMALIZATION_MAPPING(
            "Pin against the model artifact's attached metadata / task " +
                "library normalization; if fine-tuning occurs, the fine-tuning " +
                "preprocessing becomes the contract (later milestone)."
        ),

        /*
         * Exact alpha semantics of the artifact. Reference ImageNet
         * models are opaque RGB; alpha is discarded.
         */
        ALPHA_SEMANTICS(
            "Verified as DISCARD for the reference family; re-confirm if " +
                "the acquired artifact declares an RGBA input."
        ),

        /*
         * The label set / output mapping is OUT OF SCOPE (8B-15-4);
         * recorded here so the preprocessing side does not depend on it.
         */
        OUTPUT_LABELS(
            "8B-15-4 output compatibility against the acquired artifact."
        )
    }

    /*
     * The normative summary a consumer should cite. The verified
     * reference is the HIGH-confidence contract; the remaining
     * values are explicit UNKNOWNs that must not be fabricated.
     */
    val verified: Verified = Verified()

    /*
     * The reference preprocessing config derived from ModelInputRequirements.
     */
    fun referenceConfig(): PreprocessingConfig =
        PreprocessingConfig.mobileNetV4ConvReference(
            inputWidth = verified.width,
            inputHeight = verified.height
        )

    /*
     * Maps the reference values into the 8B-15-2 vocabulary for a
     * future `ReproPreprocessingIdentity` (dimensions now pinned by
     * authoritative source; the INT8 mapping remains UNKNOWN).
     */
    fun toVerifiedRepro(): VerifiedRepro {
        return VerifiedRepro(
            inputWidth = verified.width,
            inputHeight = verified.height,
            channelOrder = verified.channelOrder.label,
            colorFormat = verified.colorFormat.label
        )
    }
}

/*
 * The reproducible subset that CAN be stated given an authoritative
 * source, distinct from the still-UNKNOWN values.
 */
data class VerifiedRepro(
    val inputWidth: Int,
    val inputHeight: Int,
    val channelOrder: String,
    val colorFormat: String
)

/*
 * Maps a verified/known PreprocessingConfig into an 8B-14
 * ModelInputSpec so the config-driven boundary can drive the
 * existing 8B-14 foundation (and future runtimes) without
 * redefining the contract.
 *
 * Returns null when the config carries UNKNOWN dimensions or an
 * unsupported combination (i.e. a documentation-only config).
 */
fun PreprocessingConfig.toModelInputSpec(
    specVersion: String = "input-spec-v2"
): com.example.feedsense.analysis.ml.ModelInputSpec? {
    val width = inputWidth ?: return null
    val height = inputHeight ?: return null
    val channels = channels ?: return null
    val tensorType = when (tensorType) {
        PreprocessingTensorType.FLOAT32 -> ModelTensorType.FLOAT32
        PreprocessingTensorType.INT8 -> ModelTensorType.INT8
        PreprocessingTensorType.UINT8 -> ModelTensorType.UINT8
        else -> return null
    }
    val pixelFormat = when (channelOrder) {
        PreprocessChannelOrder.RGB, PreprocessChannelOrder.BGR -> ModelPixelFormat.RGB
        PreprocessChannelOrder.RGBA -> ModelPixelFormat.ARGB
        else -> null
    } ?: return null

    val normalization = if (mean.isNotEmpty()) {
        com.example.feedsense.analysis.ml.NormalizationSpec(mean = mean, std = std)
    } else {
        null
    }

    return com.example.feedsense.analysis.ml.ModelInputSpec(
        specVersion = specVersion,
        width = width,
        height = height,
        channels = channels,
        pixelFormat = pixelFormat,
        tensorType = tensorType,
        normalization = normalization,
        scale = scale,
        zeroPoint = zeroPoint
    )
}