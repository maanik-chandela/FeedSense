package com.example.feedsense.analysis.ml

// --------------------------------
// MODEL INPUT SPECIFICATION (8B-14)
// --------------------------------
//
// The explicit contract describing what a model consumes.
//
// Preprocessing assumptions (width, height, channels, pixel
// format, normalization, tensor type, quantization,
// orientation) live HERE - not scattered through the
// codebase. A preprocessor is driven by this spec and a
// model declares the spec it expects, so a mismatch is an
// explicit error rather than a silent behavior change.

/*
 * Pixel layout the model consumes. Privacy frames arrive as
 * ARGB IntArray pixels; the preprocessor converts them to
 * the requested model layout.
 */
enum class ModelPixelFormat(val label: String) {
    ARGB("ARGB"),
    RGB("RGB")
}

/*
 * Tensor element type. FLOAT32 is the research default;
 * INT8/UINT8 declare quantized input tensors.
 */
enum class ModelTensorType(val label: String) {
    FLOAT32("FLOAT32"),
    INT8("INT8"),
    UINT8("UINT8")
}

/*
 * Expected orientation of the captured screen. Currently
 * cosmetic (capture is already upright); it documents an
 * assumption so future rotation handling is attributable.
 */
enum class ModelOrientation(val label: String) {
    PORTRAIT("PORTRAIT"),
    LANDSCAPE("LANDSCAPE")
}

/*
 * Per-channel standardization, applied to samples after they
 * are scaled to [0,1]:
 *
 *     out = (sample01 - mean[c]) / std[c]
 *
 * Null for models that only scale.
 */
data class NormalizationSpec(
    val mean: List<Double>,
    val std: List<Double>
) {

    init {
        require(mean.isNotEmpty()) { "mean must not be empty" }
        require(std.size == mean.size) { "std must match mean size" }
        require(std.all { it > 0.0 && it.isFinite() }) {
            "std must be positive and finite"
        }
    }
}

/*
 * Immutable input contract for one model artifact.
 *
 *   - width/height        : model tensor spatial size
 *   - channels            : 3 (RGB) or 4 (ARGB)
 *   - tensorType          : FLOAT32 / INT8 / UINT8
 *   - normalization       : per-channel mean/std (null = scale only)
 *   - scale / zeroPoint   : quantization mapping for INT8/UINT8
 *                           (out ~= scale * (q - zeroPoint))
 */
data class ModelInputSpec(
    val specVersion: String,
    val width: Int,
    val height: Int,
    val channels: Int,
    val pixelFormat: ModelPixelFormat = ModelPixelFormat.RGB,
    val tensorType: ModelTensorType = ModelTensorType.FLOAT32,
    val normalization: NormalizationSpec? = null,
    val scale: Double = DEFAULT_SCALE,
    val zeroPoint: Double = 0.0,
    val orientation: ModelOrientation = ModelOrientation.PORTRAIT
) {

    init {
        require(specVersion.isNotBlank()) { "specVersion must be non-blank" }
        require(width > 0 && height > 0) { "width/height must be > 0" }
        require(channels in 1..4) { "channels must be in 1..4, got $channels" }
        require(scale > 0.0 && scale.isFinite()) { "scale must be positive and finite" }
        require(zeroPoint.isFinite()) { "zeroPoint must be finite" }
        if (pixelFormat == ModelPixelFormat.RGB) {
            require(channels == 3) { "RGB input requires 3 channels" }
        }
        if (normalization != null) {
            require(normalization.mean.size == channels) {
                "normalization must cover all channels"
            }
        }
    }

    /*
     * Number of tensor elements per frame.
     */
    val tensorSize: Int
        get() = width * height * channels

    companion object {
        const val DEFAULT_SCALE = 1.0 / 255.0

        /*
         * Convenience: a typical float RGB input for a
         * lightweight vision model (e.g. 224x224).
         */
        fun rgb224(
            specVersion: String,
            scale: Double = DEFAULT_SCALE
        ): ModelInputSpec =
            ModelInputSpec(
                specVersion = specVersion,
                width = 224,
                height = 224,
                channels = 3,
                pixelFormat = ModelPixelFormat.RGB,
                tensorType = ModelTensorType.FLOAT32,
                scale = scale
            )
    }
}