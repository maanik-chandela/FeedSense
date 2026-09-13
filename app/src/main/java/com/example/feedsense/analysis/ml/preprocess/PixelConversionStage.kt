package com.example.feedsense.analysis.ml.preprocess

import com.example.feedsense.analysis.ml.ModelInput
import com.example.feedsense.analysis.ml.ModelTensorType
import com.example.feedsense.analysis.privacy.PrivacyFrame

// --------------------------------
// PIXEL CONVERSION STAGE (8B-15-3)
// --------------------------------
//
// Turns a spatially-transformed, privacy-approved ARGB frame into
// the model tensor:
//
//   ARGB pixel
//     -> channel extraction / alpha handling (color stage)
//     -> range conversion to [0,1]
//     -> optional per-channel normalization (mean / std)
//     -> optional quantization into INT8/UINT8
//     -> NHWC layout (channel-interleaved)
//
// Conventions (documented so they can never be silently changed):
//   - Android ARGB buffers are NEVER treated as the model's channel
//     layout; every converter step is explicit.
//   - Alpha is discarded by default (FeedSense evidence is a
//     screen capture, opaque by construction); the policy is
//     explicit, never silent.
//   - Range conversion is [0,1] via /255, matching the reference
//     preprocessing for the selected model family.
//   - Normalization is (sample01 - mean[c]) / std[c].
//   - INT8/UINT8 quantization is q = round(sample / scale) + zeroPoint
//     clamped to the byte range; floats carry the dequantized
//     approximation so downstream code never interprets raw bytes.

data class PixelConversionOptions(
    val channelOrder: PreprocessChannelOrder,
    val alphaPolicy: AlphaPolicy,
    val tensorType: PreprocessingTensorType,
    val scale: Double,
    val zeroPoint: Double,
    val mean: List<Double>,
    val std: List<Double>,
    val compositeBackgroundArgb: Int = 0xFFFFFFFF.toInt() // opaque white
) {
    init {
        PreprocessingConfigValidator.requireValidConversion(this)
    }
}

object PixelConversionStage {

    /*
     * Returns the model's FloatArray. Channel-count driven by the
     * color config. Never mutates the input frame.
     */
    fun toFloats(
        frame: PrivacyFrame,
        options: PixelConversionOptions
    ): FloatArray {
        val channels = channelCount(options.channelOrder)
        val out = FloatArray(frame.width * frame.height * channels)
        var i = 0
        for (pixel in frame.pixels) {
            val rgb = extractChannels(pixel, options)
            for (c in 0 until channels) {
                val raw01 = rgb[c] / 255.0
                out[i] = if (options.mean.isNotEmpty()) {
                    ((raw01 - options.mean[c]) / options.std[c]).toFloat()
                } else {
                    (raw01 * (255.0 * options.scale)).toFloat()
                }
                i++
            }
        }
        return out
    }

    /*
     * Quantizes already-computed floats into INT8/UINT8 payload.
     * q = round(sample / scale) + zeroPoint, clamped.
     */
    fun quantize(
        floats: FloatArray,
        tensorType: ModelTensorType,
        scale: Double,
        zeroPoint: Double
    ): ByteArray {
        val bytes = ByteArray(floats.size)
        val s = scale.toFloat()
        val z = zeroPoint.toFloat()
        val min: Int
        val max: Int
        when (tensorType) {
            ModelTensorType.INT8 -> {
                min = Byte.MIN_VALUE.toInt()
                max = Byte.MAX_VALUE.toInt()
            }
            ModelTensorType.UINT8 -> {
                min = 0
                max = 255
            }
            else -> throw IllegalArgumentException(
                "quantize requires INT8/UINT8 tensor type"
            )
        }
        for (i in floats.indices) {
            val q = Math.round(floats[i] / s) + z
            bytes[i] = q.toInt().coerceIn(min, max).toByte()
        }
        return bytes
    }

    /*
     * Dequantized approximation: sample ~= (q - zeroPoint) * scale.
     * UINT8 bytes are masked to their unsigned value.
     */
    fun dequantize(
        quantized: ByteArray,
        tensorType: ModelTensorType,
        scale: Double,
        zeroPoint: Double
    ): FloatArray {
        val s = scale.toFloat()
        val z = zeroPoint.toFloat()
        val floats = FloatArray(quantized.size)
        for (i in quantized.indices) {
            val raw = quantized[i].toInt()
            val q = if (tensorType == ModelTensorType.UINT8) raw and 0xFF else raw
            floats[i] = (q - z) * s
        }
        return floats
    }

    /*
     * Produces the final ModelInput (NHWC, channel-interleaved).
     */
    fun toModelInput(
        frame: PrivacyFrame,
        options: PixelConversionOptions,
        tensorType: ModelTensorType
    ): ModelInput {
        val channels = channelCount(options.channelOrder)
        val floats = toFloats(frame, options)
        val quantized: ByteArray? = when (tensorType) {
            ModelTensorType.FLOAT32 -> null
            ModelTensorType.INT8 ->
                quantize(floats, tensorType, options.scale, options.zeroPoint)
            ModelTensorType.UINT8 ->
                quantize(floats, tensorType, options.scale, options.zeroPoint)
        }
        val floating = when (tensorType) {
            ModelTensorType.FLOAT32 -> floats
            ModelTensorType.INT8, ModelTensorType.UINT8 ->
                dequantize(quantized!!, tensorType, options.scale, options.zeroPoint)
        }
        return ModelInput(
            width = frame.width,
            height = frame.height,
            channels = channels,
            tensorType = tensorType,
            floats = floating,
            quantizedBytes = quantized,
            layout = "NHWC"
        )
    }

    /*
     * Extracts the ordered channel values from one ARGB pixel,
     * applying the alpha policy. Returns values in 0..255.
     */
    private fun extractChannels(
        pixel: Int,
        options: PixelConversionOptions
    ): IntArray {
        val a = (pixel ushr 24) and 0xFF
        val r = (pixel ushr 16) and 0xFF
        val g = (pixel ushr 8) and 0xFF
        val b = pixel and 0xFF

        val effective: Triple<Int, Int, Int> = when (options.alphaPolicy) {
            AlphaPolicy.DISCARD, AlphaPolicy.PRESERVE -> Triple(r, g, b)
            AlphaPolicy.COMPOSITE -> {
                val bg = options.compositeBackgroundArgb
                val br = (bg ushr 16) and 0xFF
                val bg2 = (bg ushr 8) and 0xFF
                val bb = bg and 0xFF
                val fa = a / 255.0
                Triple(
                    Math.round(r * fa + br * (1 - fa)).toInt(),
                    Math.round(g * fa + bg2 * (1 - fa)).toInt(),
                    Math.round(b * fa + bb * (1 - fa)).toInt()
                )
            }
            AlphaPolicy.UNKNOWN -> Triple(r, g, b)
        }

        return when (options.channelOrder) {
            PreprocessChannelOrder.RGB -> intArrayOf(
                effective.first, effective.second, effective.third
            )
            PreprocessChannelOrder.BGR -> intArrayOf(
                effective.third, effective.second, effective.first
            )
            PreprocessChannelOrder.RGBA -> intArrayOf(
                a, effective.first, effective.second, effective.third
            )
            PreprocessChannelOrder.GRAYSCALE -> intArrayOf(
                luminance(effective.first, effective.second, effective.third)
            )
            PreprocessChannelOrder.UNKNOWN -> throw IllegalArgumentException(
                "cannot convert with UNKNOWN channel order"
            )
        }
    }

    /*
     * Rec.601 luma, deterministic integer rounding.
     */
    private fun luminance(r: Int, g: Int, b: Int): Int {
        return Math.round(0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }

    fun channelCount(order: PreprocessChannelOrder): Int = when (order) {
        PreprocessChannelOrder.RGB,
        PreprocessChannelOrder.BGR -> 3
        PreprocessChannelOrder.RGBA -> 4
        PreprocessChannelOrder.GRAYSCALE -> 1
        PreprocessChannelOrder.UNKNOWN -> throw IllegalArgumentException(
            "UNKNOWN channel order has no channel count"
        )
    }

    fun optionsFrom(config: PreprocessingConfig): PixelConversionOptions {
        return PixelConversionOptions(
            channelOrder = config.channelOrder,
            alphaPolicy = config.alphaPolicy,
            tensorType = config.tensorType,
            scale = config.scale,
            zeroPoint = config.zeroPoint,
            mean = config.mean,
            std = config.std
        )
    }
}