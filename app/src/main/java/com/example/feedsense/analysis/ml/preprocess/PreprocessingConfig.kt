package com.example.feedsense.analysis.ml.preprocess

// --------------------------------
// CANONICAL PREPROCESSING CONFIG (8B-15-3)
// --------------------------------
//
// The immutable, versioned description of the complete
// preprocessing pipeline. This is the canonical configuration
// representation the milestone specifies (§21):
//
//   PreprocessingConfig {
//     version
//     inputWidth
//     inputHeight
//     resizePolicy
//     cropPolicy
//     paddingPolicy
//     interpolation
//     orientationPolicy
//     colorFormat
//     channelOrder
//     alphaPolicy
//     numericType
//     normalization
//     tensorLayout
//     batchSize
//   }
//
// Only fields applicable to the selected model are present; no
// meaningless placeholder fields are invented.
//
// The config is validated at construction time (see
// PreprocessingConfigValidator) so an invalid configuration
// fails loudly rather than silently falling back.

/**
 * Immutable canonical preprocessing configuration.
 *
 * [inputWidth] / [inputHeight] are the model's spatial tensor
 * size. They may be predefined (verified/reference sizes) or
 * null when the exact value is UNKNOWN pending the real artifact.
 *
 * @param version             immutable preprocessing version
 *   (PreprocessingVersion).
 * @param inputWidth          model input width; null = UNKNOWN.
 * @param inputHeight         model input height; null = UNKNOWN.
 * @param resizePolicy        how the frame is resized.
 * @param cropPolicy          crop anchor when cropping applies.
 * @param paddingPolicy       padding strategy when padding applies.
 * @param interpolation       resize interpolation.
 * @param orientationPolicy   how capture orientation is resolved.
 * @param colorFormat         final input color representation.
 * @param channelOrder        channel order of the input tensor.
 * @param alphaPolicy         what happens to alpha.
 * @param tensorType          numeric tensor element type.
 * @param scale               quantization / value scale (default
 *   matches the [0,1] scale=1/255 Float32 convention).
 * @param zeroPoint           quantization zero point.
 * @param mean                per-channel mean for standardization
 *   (empty when scale-only).
 * @param std                 per-channel std for standardization
 *   (must mirror [mean]; empty when scale-only).
 * @param tensorLayout        NHWC / NCHW.
 * @param batchSize           batch strategy.
 * @param paddingValue        ARGB value for PAD pixels.
 * @param cropAnchor          the crop anchor region policy.
 */
data class PreprocessingConfig(
    val version: String = PreprocessingVersion.V2,
    val inputWidth: Int? = null,
    val inputHeight: Int? = null,
    val resizePolicy: ResizePolicy = ResizePolicy.NEAREST_NEIGHBOR,
    val cropPolicy: AspectRatioPolicy = AspectRatioPolicy.CENTER_CROP,
    val paddingPolicy: PaddingPolicy = PaddingPolicy.NO_PADDING,
    val interpolation: Interpolation = Interpolation.NEAREST,
    val orientationPolicy: OrientationPolicy = OrientationPolicy.NORMALIZE_TO_0,
    val colorFormat: ColorFormat = ColorFormat.RGB,
    val channelOrder: PreprocessChannelOrder = PreprocessChannelOrder.RGB,
    val alphaPolicy: AlphaPolicy = AlphaPolicy.DISCARD,
    val tensorType: PreprocessingTensorType = PreprocessingTensorType.FLOAT32,
    val scale: Double = DEFAULT_SCALE,
    val zeroPoint: Double = 0.0,
    val mean: List<Double> = emptyList(),
    val std: List<Double> = emptyList(),
    val tensorLayout: PreprocessingTensorLayout = PreprocessingTensorLayout.NHWC,
    val batchSize: BatchHandling = BatchHandling.BATCH_1,
    val paddingValue: Int = DEFAULT_PADDING_VALUE,
    val cropAnchor: CropAnchor = CropAnchor.CENTER
) {

    init {
        PreprocessingConfigValidator.validate(this)
    }

    /*
     * Number of tensor channels for the configured color/order.
     * null when the format is UNKNOWN (i.e. UNKNOWN config not
     * intended to drive a pipeline).
     */
    val channels: Int?
        get() = when (channelOrder) {
            PreprocessChannelOrder.RGB,
            PreprocessChannelOrder.BGR -> 3
            PreprocessChannelOrder.RGBA -> 4
            PreprocessChannelOrder.GRAYSCALE -> 1
            PreprocessChannelOrder.UNKNOWN -> null
        }

    /*
     * Canonical, byte-stable key for the configuration. Two
     * logically identical configs produce the same key; any
     * change produces a different key. Used for experiment
     * tagging and for detecting silent config drift.
     */
    val canonicalKey: String
        get() {
            val dims = when {
                inputWidth == null || inputHeight == null -> "nullxnull"
                else -> "${inputWidth}x$inputHeight"
            }
            return listOf(
                version,
                dims,
                resizePolicy.label,
                cropPolicy.label,
                paddingPolicy.label,
                interpolation.label,
                orientationPolicy.label,
                colorFormat.label,
                channelOrder.label,
                alphaPolicy.label,
                tensorType.label,
                scale.toString(),
                zeroPoint.toString(),
                renderDoubleList(mean),
                renderDoubleList(std),
                tensorLayout.label,
                batchSize.label,
                paddingValue.toString(),
                cropAnchor.label
            ).joinToString("|")
        }

    private fun renderDoubleList(values: List<Double>): String {
        if (values.isEmpty()) return "[]"
        return values.joinToString(",", "[", "]") { it.toString() }
    }

    companion object {
        const val DEFAULT_SCALE = 1.0 / 255.0
        const val DEFAULT_PADDING_VALUE = 0xFF000000.toInt() // opaque black

        /*
         * The authoritative REFERENCE contracts collected in
         * 8B-15-3 for the selected model family (see
         * ModelInputRequirements). These encode the verified-against-
         * authoritative-source values and leave the not-yet-pinned
         * fields UNKNOWN (null dimensions where the actual artifact
         * input is unacquired).
         *
         * Do NOT treat the reference config as a final
         * production contract: it must be pinned against the real
         * `.tflite` artifact on acquisition (8B-15-2 ARTIFACT_PENDING).
         */

        /*
         * MobileNetV4-Conv family reference contract.
         *
         * Verified against the authoritative MobileNetV4 converted
         * artifact family (224x224, RGB, [0,1] scale, ImageNet
         * mean/std in the reference converters):
         *   - spatial size 224x224
         *   - RGB, NHWC, float32 (quantization mapping to INT8 not
         *     yet pinned)
         *   - scale [0,1] via 1/255
         *   - ImageNet standardization mean/std are reported by the
         *     reference converter family, but the exact INT8 input
         *     mapping (scale/zeroPoint) is UNKNOWN until the actual
         *     artifact is acquired.
         */
        fun mobileNetV4ConvReference(
            inputWidth: Int = 224,
            inputHeight: Int = 224
        ): PreprocessingConfig = PreprocessingConfig(
            version = PreprocessingVersion.V2,
            inputWidth = inputWidth,
            inputHeight = inputHeight,
            resizePolicy = ResizePolicy.BILINEAR,
            cropPolicy = AspectRatioPolicy.CENTER_CROP,
            paddingPolicy = PaddingPolicy.NO_PADDING,
            interpolation = Interpolation.BILINEAR,
            orientationPolicy = OrientationPolicy.NORMALIZE_TO_0,
            colorFormat = ColorFormat.RGB,
            channelOrder = PreprocessChannelOrder.RGB,
            alphaPolicy = AlphaPolicy.DISCARD,
            tensorType = PreprocessingTensorType.FLOAT32,
            scale = DEFAULT_SCALE,
            zeroPoint = 0.0,
            mean = listOf(0.485, 0.456, 0.406),
            std = listOf(0.229, 0.224, 0.225),
            tensorLayout = PreprocessingTensorLayout.NHWC,
            batchSize = BatchHandling.BATCH_1,
            paddingValue = DEFAULT_PADDING_VALUE,
            cropAnchor = CropAnchor.CENTER
        )

        /*
         * A config with an honest UNKNOWN spatial size: used when
         * the exact artifact input size is not acquired yet. The
         * pipeline refuses to run with an UNKNOWN size; it only
         * documents the contract.
         */
        fun pendingUnverified(
            modelHint: String
        ): PreprocessingConfig = PreprocessingConfig(
            version = PreprocessingVersion.V2,
            inputWidth = null,
            inputHeight = null,
            resizePolicy = ResizePolicy.UNKNOWN,
            cropPolicy = AspectRatioPolicy.UNKNOWN,
            paddingPolicy = PaddingPolicy.UNKNOWN,
            interpolation = Interpolation.UNKNOWN,
            orientationPolicy = OrientationPolicy.UNKNOWN,
            colorFormat = ColorFormat.UNKNOWN,
            channelOrder = PreprocessChannelOrder.UNKNOWN,
            alphaPolicy = AlphaPolicy.UNKNOWN,
            tensorType = PreprocessingTensorType.UNKNOWN,
            tensorLayout = PreprocessingTensorLayout.UNKNOWN,
            batchSize = BatchHandling.UNKNOWN,
            cropAnchor = CropAnchor.UNKNOWN
        )
    }
}

/*
 * Padding strategy when PAD / aspect-ratio handling requires a
 * border (milestone §10).
 */
enum class PaddingPolicy(override val label: String) : PreprocessLabeled {
    NO_PADDING("NO_PADDING"),
    PAD_BLACK("PAD_BLACK"),
    UNKNOWN("UNKNOWN")
}