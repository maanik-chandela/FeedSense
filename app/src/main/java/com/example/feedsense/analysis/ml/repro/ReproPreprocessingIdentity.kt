package com.example.feedsense.analysis.ml.repro

// --------------------------------
// PREPROCESSING IDENTITY (8B-15-2)
// --------------------------------
//
// The exact preprocessing contract the model assumes. This is a
// METADATA CONTRACT only: the underlying pipeline is built in a
// later milestone. A model cannot be reproduced if preprocessing
// changes silently, so preprocessing is versioned independently.
//
// preprocessingVersion is the reproducibility component; the
// detailed fields make the contract auditable without assuming
// preprocessing can be inferred from the model.

/*
 * How the source image is resized to the model tensor size.
 */
enum class ResizeMethod(override val label: String) : ReprLabeled {
    BILINEAR("BILINEAR"),
    BICUBIC("BICUBIC"),
    NEAREST("NEAREST"),
    OTHER("OTHER"),
    UNKNOWN("UNKNOWN")
}

/*
 * Aspect-ratio / crop behavior when the source and tensor aspect
 * ratios differ.
 */
enum class AspectRatioBehavior(override val label: String) : ReprLabeled {
    CENTER_CROP("CENTER_CROP"),
    STRETCH("STRETCH"),
    PAD("PAD"),
    LETTERBOX("LETTERBOX"),
    OTHER("OTHER"),
    UNKNOWN("UNKNOWN")
}

/*
 * Channel order of the model input tensor.
 */
enum class ChannelOrder(override val label: String) : ReprLabeled {
    RGB("RGB"),
    BGR("BGR"),
    RGBA("RGBA"),
    GRAYSCALE("GRAYSCALE"),
    UNKNOWN("UNKNOWN")
}

/*
 * Per-channel standardization applied after scaling to [0,1]:
 *     out = (sample01 - mean[c]) / std[c]
 * Null/empty means scale-only (no mean/std normalization).
 */
data class NormalizationParams(
    val mean: List<Double> = emptyList(),
    val std: List<Double> = emptyList(),
    val scaleToZeroOne: Boolean = true
) {
    init {
        require(mean.size == std.size) { "mean and std must have equal length" }
        require(std.all { it > 0.0 && it.isFinite() }) { "std must be positive and finite" }
    }
}

/*
 * Immutable preprocessing identity contract.
 *
 * inputWidth/inputHeight may be null when the exact tensor size
 * has not yet been verified against a real artifact (that is
 * 8B-15-3 Input Compatibility work). A null dimension is an
 * explicit "unverified", never a fabricated size.
 */
data class ReproPreprocessingIdentity(
    val preprocessingVersion: String,
    val resizeMethod: ResizeMethod,
    val inputWidth: Int? = null,
    val inputHeight: Int? = null,
    val aspectRatioBehavior: AspectRatioBehavior,
    val cropBehavior: String? = null,
    val colorFormat: String? = null,
    val channelOrder: ChannelOrder,
    val normalization: NormalizationParams = NormalizationParams(),
    val alphaHandling: String? = null,
    val orientationHandling: String? = null
) {

    init {
        require(preprocessingVersion.isNotBlank()) { "preprocessingVersion must be non-blank" }
        require(inputWidth == null || inputWidth > 0) { "inputWidth must be > 0 when set" }
        require(inputHeight == null || inputHeight > 0) { "inputHeight must be > 0 when set" }
        require((inputWidth == null) == (inputHeight == null)) {
            "inputWidth and inputHeight must both be set or both be null"
        }
        val channels = when (channelOrder) {
            ChannelOrder.RGB, ChannelOrder.BGR -> 3
            ChannelOrder.RGBA -> 4
            ChannelOrder.GRAYSCALE -> 1
            ChannelOrder.UNKNOWN -> null
        }
        if (channels != null && normalization.mean.isNotEmpty()) {
            require(normalization.mean.size == channels) {
                "normalization mean must match channels ($channels)"
            }
        }
    }

    val key: String
        get() = "$preprocessingVersion:${inputWidth}x$inputHeight:${channelOrder.label}"
}
