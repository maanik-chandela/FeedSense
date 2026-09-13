package com.example.feedsense.analysis.ml.preprocess

// --------------------------------
// CONFIGURATION VALIDATION (8B-15-3)
// --------------------------------
//
// Invalid preprocessing configurations fail explicitly at
// construction time. There is no silent fall-back to another
// configuration: an invalid config never drives a pipeline.

object PreprocessingConfigValidator {

    fun validate(config: PreprocessingConfig) {
        require(config.version.isNotBlank()) { "preprocessing version must be non-blank" }

        // Dimensions: 0 / negative are invalid; null is allowed
        // ONLY as an explicit "UNKNOWN" marker and must not drive
        // a pipeline.
        config.inputWidth?.let {
            require(it > 0) { "inputWidth must be > 0 when set, got $it" }
        }
        config.inputHeight?.let {
            require(it > 0) { "inputHeight must be > 0 when set, got $it" }
        }
        require(config.inputWidth == null || config.inputHeight == null ||
            config.inputWidth * config.inputHeight <= Long.MAX_VALUE
        ) { "input dimensions overflow" }
        require((config.inputWidth == null) == (config.inputHeight == null)) {
            "inputWidth and inputHeight must both be set or both be null"
        }

        require(config.scale > 0.0 && config.scale.isFinite()) {
            "scale must be positive and finite"
        }
        require(config.zeroPoint.isFinite()) { "zeroPoint must be finite" }
        require(config.paddingValue and 0xFF000000.toInt() != 0) {
            "paddingValue must be opaque (alpha != 0)"
        }

        // Normalization: mean and std must mirror each other and
        // must match the configured channel count when set.
        require(config.mean.size == config.std.size) {
            "mean and std must have equal length"
        }
        require(config.std.all { it > 0.0 && it.isFinite() }) {
            "std must be positive and finite"
        }
        require(config.mean.all { it.isFinite() }) { "mean must be finite" }
        val channels = config.channels
        if (channels != null && config.mean.isNotEmpty()) {
            require(config.mean.size == channels) {
                "normalization must cover all $channels channels, " +
                    "got ${config.mean.size}"
            }
        }

        // Cross-field invariants.
        validateSpatial(config)
        validateColor(config)
        validateTensor(config)
        validateBatch(config)
    }

    /*
     * Spatial policy invariants.
     */
    private fun validateSpatial(config: PreprocessingConfig) {
        if (config.resizePolicy == ResizePolicy.UNKNOWN ||
            config.cropPolicy == AspectRatioPolicy.UNKNOWN ||
            config.interpolation == Interpolation.UNKNOWN ||
            config.orientationPolicy == OrientationPolicy.UNKNOWN
        ) {
            // UNKNOWN spatial policies are only valid for
            // documentation-only configs (pending artifact).
            require(config.inputWidth == null && config.inputHeight == null) {
                "UNKNOWN spatial policy requires UNKNOWN dimensions " +
                    "(documentation-only config)"
            }
            return
        }

        // A valid pipeline requires verified dimensions.
        require(config.inputWidth != null && config.inputHeight != null) {
            "a runnable preprocessing config requires verified input dimensions"
        }

        if (config.cropPolicy == AspectRatioPolicy.PAD) {
            require(config.paddingPolicy == PaddingPolicy.PAD_BLACK) {
                "PAD crop policy requires a concrete padding policy"
            }
        }
        if (config.paddingPolicy == PaddingPolicy.PAD_BLACK) {
            require(config.cropPolicy == AspectRatioPolicy.PAD) {
                "PAD_BLACK padding requires the PAD crop policy"
            }
        }
    }

    /*
     * Color / channel / alpha invariants.
     */
    private fun validateColor(config: PreprocessingConfig) {
        if (config.colorFormat == ColorFormat.UNKNOWN ||
            config.channelOrder == PreprocessChannelOrder.UNKNOWN ||
            config.alphaPolicy == AlphaPolicy.UNKNOWN
        ) {
            require(config.inputWidth == null && config.inputHeight == null) {
                "UNKNOWN color/order/alpha requires UNKNOWN dimensions " +
                    "(documentation-only config)"
            }
            return
        }

        val orderChannels = when (config.channelOrder) {
            PreprocessChannelOrder.RGB, PreprocessChannelOrder.BGR -> 3
            PreprocessChannelOrder.RGBA -> 4
            else -> null
        }
        val formatChannels = when (config.colorFormat) {
            ColorFormat.RGB, ColorFormat.BGR -> 3
            ColorFormat.RGBA -> 4
            ColorFormat.GRAYSCALE -> 1
            else -> null
        }
        require(
            (config.colorFormat == ColorFormat.GRAYSCALE &&
                config.channelOrder == PreprocessChannelOrder.GRAYSCALE) ||
                (orderChannels != null && orderChannels == formatChannels)
        ) {
            "colorFormat and channelOrder must describe the same channel count"
        }

        when (config.alphaPolicy) {
            AlphaPolicy.PRESERVE ->
                require(config.channelOrder == PreprocessChannelOrder.RGBA) {
                    "PRESERVE alpha requires an RGBA channel order"
                }
            AlphaPolicy.DISCARD ->
                require(config.channelOrder != PreprocessChannelOrder.RGBA) {
                    "DISCARD alpha cannot be combined with RGBA channel order"
                }
            else -> {}
        }
    }

    /*
     * Tensor / datatype invariants.
     */
    private fun validateTensor(config: PreprocessingConfig) {
        if (config.tensorType == PreprocessingTensorType.UNKNOWN ||
            config.tensorLayout == PreprocessingTensorLayout.UNKNOWN
        ) {
            require(config.inputWidth == null && config.inputHeight == null) {
                "UNKNOWN tensorType/layout requires UNKNOWN dimensions " +
                    "(documentation-only config)"
            }
            return
        }

        // INT8/UINT8 inputs require an explicit scale+zeroPoint
        // that defines the quantization mapping.
        if (config.tensorType == PreprocessingTensorType.INT8 ||
            config.tensorType == PreprocessingTensorType.UINT8
        ) {
            require(config.scale > 0.0) { "quantized tensors require a positive scale" }
            require(config.zeroPoint.isFinite()) { "quantized tensors require a finite zeroPoint" }
        }

        require(config.tensorLayout == PreprocessingTensorLayout.NHWC) {
            "only NHWC is implemented in the 8B-15-3 deterministic pipeline; " +
                "NCHW would require a new preprocessing version"
        }
    }

    /*
     * Batch handling invariants.
     */
    private fun validateBatch(config: PreprocessingConfig) {
        if (config.inputWidth == null && config.inputHeight == null) {
            // Documentation-only config: batch policy may be
            // UNKNOWN too.
            return
        }
        require(config.batchSize == BatchHandling.BATCH_1) {
            "8B-15-3 supports batch size 1 only; dynamic batches are a " +
                "later performance milestone"
        }
    }

    /*
     * Validates the lowered PixelConversionOptions used by the
     * conversion stage (defense in depth on top of the config).
     */
    fun requireValidConversion(options: PixelConversionOptions) {
        val channels = when (options.channelOrder) {
            PreprocessChannelOrder.RGB, PreprocessChannelOrder.BGR -> 3
            PreprocessChannelOrder.RGBA -> 4
            PreprocessChannelOrder.GRAYSCALE -> 1
            PreprocessChannelOrder.UNKNOWN -> throw IllegalArgumentException(
                "UNKNOWN channel order has no channel count"
            )
        }
        require(options.mean.size == options.std.size) {
            "mean and std must have equal length"
        }
        require(options.std.all { it > 0.0 && it.isFinite() }) {
            "std must be positive and finite"
        }
        require(options.mean.all { it.isFinite() }) { "mean must be finite" }
        if (options.mean.isNotEmpty()) {
            require(options.mean.size == channels) {
                "normalization must cover all $channels channels"
            }
        }
        require(options.scale > 0.0 && options.scale.isFinite()) {
            "scale must be positive and finite"
        }
        require(options.zeroPoint.isFinite()) { "zeroPoint must be finite" }

        when (options.alphaPolicy) {
            AlphaPolicy.PRESERVE -> require(options.channelOrder == PreprocessChannelOrder.RGBA) {
                "PRESERVE alpha requires RGBA channel order"
            }
            AlphaPolicy.DISCARD -> require(options.channelOrder != PreprocessChannelOrder.RGBA) {
                "DISCARD alpha cannot be combined with RGBA channel order"
            }
            else -> {}
        }
        require(options.tensorType != PreprocessingTensorType.UNKNOWN) {
            "pixel conversion requires a concrete tensor type"
        }
    }
}