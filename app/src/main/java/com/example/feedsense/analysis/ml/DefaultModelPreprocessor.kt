package com.example.feedsense.analysis.ml

import com.example.feedsense.analysis.privacy.PrivacyFrame

// --------------------------------
// DEFAULT MODEL PREPROCESSOR (8B-14)
// --------------------------------
//
// The standard deterministic frame -> tensor pipeline:
//
//   1. validate the privacy-approved frame against the spec
//   2. resize to the model's spatial size (nearest-neighbor,
//      deterministic)
//   3. extract the requested channels (ARGB -> RGB by
//      default)
//   4. scale to [0,1] and apply optional per-channel
//      normalization
//   5. optionally quantize into INT8/UINT8 payload bytes
//
// Everything is in-memory: no Bitmap copy, no byte-array
// serialization, no temporary file (8B-14 section 17).
//
// Fully deterministic: same frame + same spec + same clock
// => identical tensor and identical latency.

class DefaultModelPreprocessor(
    override val inputSpec: ModelInputSpec,
    override val version: String = PREPROCESS_VERSION,
    private val clock: InferenceClock = SystemInferenceClock
) : ModelPreprocessor {

    override fun preprocess(safeFrame: PrivacyFrame): PreprocessingResult {
        val start = clock.nowMs()

        // --------------------------------
        // 1. VALIDATE FRAME vs SPEC
        // --------------------------------

        if (safeFrame.width <= 0 || safeFrame.height <= 0) {
            return failure(
                InferenceStatus.INVALID_INPUT,
                "frame dimensions must be positive",
                start
            )
        }

        try {
            // --------------------------------
            // 2. RESIZE (nearest-neighbor, deterministic)
            // --------------------------------

            val out = FloatArray(inputSpec.tensorSize)
            val srcW = safeFrame.width
            val srcH = safeFrame.height
            val dstW = inputSpec.width
            val dstH = inputSpec.height
            val channels = inputSpec.channels
            val src = safeFrame.pixels

            for (y in 0 until dstH) {
                val srcY = y * srcH / dstH
                for (x in 0 until dstW) {
                    val srcX = x * srcW / dstW
                    val pixel = src[srcY * srcW + srcX]
                    val outIndex = (y * dstW + x) * channels

                    if (channels == 3) {
                        // ARGB -> RGB
                        out[outIndex] = ((pixel ushr 16) and 0xFF).toFloat()
                        out[outIndex + 1] = ((pixel ushr 8) and 0xFF).toFloat()
                        out[outIndex + 2] = (pixel and 0xFF).toFloat()
                    } else if (channels == 4) {
                        // ARGB preserved
                        out[outIndex] = ((pixel ushr 24) and 0xFF).toFloat()
                        out[outIndex + 1] = ((pixel ushr 16) and 0xFF).toFloat()
                        out[outIndex + 2] = ((pixel ushr 8) and 0xFF).toFloat()
                        out[outIndex + 3] = (pixel and 0xFF).toFloat()
                    } else {
                        throw IllegalArgumentException(
                            "unsupported channel count $channels"
                        )
                    }
                }
            }

            // --------------------------------
            // 3. SCALE + NORMALIZE IN PLACE
            // --------------------------------

            if (inputSpec.normalization != null) {
                applyNormalization(out, inputSpec.normalization, dstW, dstH, channels)
            } else {
                val scale = inputSpec.scale.toFloat()
                for (i in out.indices) {
                    out[i] = out[i] * scale
                }
            }

            // --------------------------------
            // 4. QUANTIZE (when the tensor is not float)
            // --------------------------------

            val quantized = when (inputSpec.tensorType) {
                ModelTensorType.FLOAT32 -> null
                ModelTensorType.INT8 ->
                    quantize(out, inputSpec, dstW, dstH, channels)
                ModelTensorType.UINT8 ->
                    quantize(out, inputSpec, dstW, dstH, channels)
            }

            if (inputSpec.tensorType != ModelTensorType.FLOAT32) {
                // floats carries the dequantized approximation.
                dequantizeInPlace(out, inputSpec)
            }

            val latencyMs = clock.nowMs() - start
            val input = ModelInput(
                width = dstW,
                height = dstH,
                channels = channels,
                tensorType = inputSpec.tensorType,
                floats = out,
                quantizedBytes = quantized
            )

            return PreprocessingResult.Success(
                input = input,
                latencyMs = latencyMs,
                sourceWidth = srcW,
                sourceHeight = srcH
            )
        } catch (e: IllegalArgumentException) {
            return failure(
                InferenceStatus.INVALID_INPUT,
                e.message ?: "invalid frame",
                start
            )
        } catch (e: Exception) {
            return failure(
                InferenceStatus.PREPROCESSING_FAILURE,
                "preprocessing failed: ${e.javaClass.simpleName}",
                start
            )
        }
    }

    /*
     * out = (sample01 - mean[c]) / std[c], per channel.
     */
    private fun applyNormalization(
        out: FloatArray,
        normalization: NormalizationSpec,
        width: Int,
        height: Int,
        channels: Int
    ) {
        for (y in 0 until height) {
            for (x in 0 until width) {
                for (c in 0 until channels) {
                    val index = (y * width + x) * channels + c
                    val sample01 = out[index] / 255.0f
                    val mean = normalization.mean[c].toFloat()
                    val std = normalization.std[c].toFloat()
                    out[index] = (sample01 - mean) / std
                }
            }
        }
    }

    /*
     * q = round(out / scale) + zeroPoint, clamped to the
     * target byte range. `out` is already normalized.
     */
    private fun quantize(
        out: FloatArray,
        spec: ModelInputSpec,
        width: Int,
        height: Int,
        channels: Int
    ): ByteArray {
        val bytes = ByteArray(width * height * channels)
        val scale = spec.scale.toFloat()
        val zeroPoint = spec.zeroPoint.toFloat()
        for (i in out.indices) {
            val q = Math.round(out[i] / scale) + zeroPoint
            bytes[i] = q.toInt().coerceIn(Byte.MIN_VALUE.toInt(), Byte.MAX_VALUE.toInt()).toByte()
        }
        return bytes
    }

    /*
     * Dequantized approximation: out ~= (q - zeroPoint) * scale.
     */
    private fun dequantizeInPlace(
        out: FloatArray,
        spec: ModelInputSpec
    ) {
        val scale = spec.scale.toFloat()
        val zeroPoint = spec.zeroPoint.toFloat()
        for (i in out.indices) {
            val q = (out[i] / scale + zeroPoint).toLong()
            out[i] = (q - zeroPoint) * scale
        }
    }

    private fun failure(
        status: InferenceStatus,
        message: String,
        startMs: Long
    ): PreprocessingResult.Failure {
        return PreprocessingResult.Failure(
            status = status,
            latencyMs = clock.nowMs() - startMs,
            message = message
        )
    }

    companion object {
        const val PREPROCESS_VERSION = "preprocess-v1"
    }
}