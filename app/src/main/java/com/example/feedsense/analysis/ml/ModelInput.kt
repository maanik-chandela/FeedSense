package com.example.feedsense.analysis.ml

// --------------------------------
// MODEL INPUT (8B-14)
// --------------------------------
//
// The framework-agnostic prepared tensor produced by a
// ModelPreprocessor and consumed by an OnDeviceModel.
//
// No TensorFlow Lite / ONNX / ExecuTorch types leak into the
// rest of FeedSense: the runtime adapter boundary converts a
// framework tensor into this neutral representation, and the
// preprocessor produces it without touching a runtime.
//
// Layout is NHWC (height-row-major, channel-interleaved),
// the conventional mobile layout.
//
// floats always carries the normalized values. For quantized
// tensor types, quantizedBytes carries the quantized payload
// computed with the spec's scale/zeroPoint; floats then
// carries the dequantized approximation so downstream code
// never has to interpret raw quantization.

class ModelInput(
    val width: Int,
    val height: Int,
    val channels: Int,
    val tensorType: ModelTensorType,
    val floats: FloatArray,
    val quantizedBytes: ByteArray? = null,
    val layout: String = "NHWC"
) {

    init {
        require(width > 0 && height > 0) { "width/height must be > 0" }
        require(channels in 1..4) { "channels must be in 1..4" }
        require(floats.size == width * height * channels) {
            "floats must hold width*height*channels=${width * height * channels} " +
                "entries, got ${floats.size}"
        }
        if (tensorType != ModelTensorType.FLOAT32) {
            require(quantizedBytes?.size == width * height * channels) {
                "quantizedBytes (${quantizedBytes?.size}) must match tensor size " +
                    "${width * height * channels} for ${tensorType.label}"
            }
        }
    }

    val tensorSize: Int
        get() = width * height * channels

    /*
     * Structural equality over tensor contents - used so
     * "same input produces same output" claims are testable
     * without reference aliasing.
     */
    fun contentEquals(other: ModelInput): Boolean {
        return width == other.width &&
            height == other.height &&
            channels == other.channels &&
            tensorType == other.tensorType &&
            floats.contentEquals(other.floats) &&
            (quantizedBytes == null && other.quantizedBytes == null ||
                quantizedBytes != null && quantizedBytes.contentEquals(other.quantizedBytes))
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModelInput) return false
        return contentEquals(other)
    }

    override fun hashCode(): Int {
        var result = width
        result = 31 * result + height
        result = 31 * result + channels
        result = 31 * result + tensorType.hashCode()
        result = 31 * result + floats.contentHashCode()
        result = 31 * result + (quantizedBytes?.contentHashCode() ?: 0)
        return result
    }
}