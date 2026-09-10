package com.example.feedsense.analysis.ml.preprocess.golden

import com.example.feedsense.analysis.ml.ModelInput
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

// --------------------------------
// GOLDEN HASHER (8B-15-4 §7)
// --------------------------------
//
// Deterministic hashing of model tensors and preprocessing
// metadata for golden fixture verification.
//
// Determinism rules:
//   - SHA-256 over a deterministic byte serialization of the
//     tensor content.
//   - Float serialization uses IEEE 754 single-precision
//     (Float.toBits -> 4 bytes) to avoid locale/format issues.
//   - Never depends on JVM memory layout, object identity,
//     timestamps, or unordered collections.
//
// Hash algorithm: SHA-256 (consistent with 8B-15-2
// ReproCanonicalSerializer.sha256Hex).

/**
 * Deterministic, content-free hashing of preprocessing outputs.
 */
object GoldenHasher {

    /**
     * SHA-256 hash of the final model tensor bytes.
     *
     * For FLOAT32 tensors, the float array is serialized to
     * IEEE 754 single-precision bytes (little-endian) before
     * hashing. For INT8/UINT8 tensors, the quantized bytes
     * are hashed directly.
     *
     * The serialization is deterministic:
     *   - same FloatArray always produces the same bytes
     *   - same ByteArray always produces the same hash
     *   - no locale, timezone, or platform dependence
     */
    fun hashTensor(input: ModelInput): String {
        val bytes = serializeTensorToBytes(input)
        return sha256Hex(bytes)
    }

    /**
     * Serializes a ModelInput to a deterministic byte array.
     *
     * Format: width(4) | height(4) | channels(4) | tensorType(1) |
     *         data(N)
     *
     * The data section is:
     *   FLOAT32: float bits in little-endian byte order
     *   INT8/UINT8: raw quantized bytes
     */
    fun serializeTensorToBytes(input: ModelInput): ByteArray {
        val headerSize = 4 + 4 + 4 + 1
        val dataSize = when (input.tensorType) {
            com.example.feedsense.analysis.ml.ModelTensorType.FLOAT32 ->
                input.tensorSize * 4
            com.example.feedsense.analysis.ml.ModelTensorType.INT8,
            com.example.feedsense.analysis.ml.ModelTensorType.UINT8 ->
                input.tensorSize
            else -> input.tensorSize * 4
        }
        val buffer = ByteBuffer.allocate(headerSize + dataSize)
            .order(ByteOrder.LITTLE_ENDIAN)

        buffer.putInt(input.width)
        buffer.putInt(input.height)
        buffer.putInt(input.channels)
        buffer.put(
            when (input.tensorType) {
                com.example.feedsense.analysis.ml.ModelTensorType.FLOAT32 -> 0.toByte()
                com.example.feedsense.analysis.ml.ModelTensorType.INT8 -> 1.toByte()
                com.example.feedsense.analysis.ml.ModelTensorType.UINT8 -> 2.toByte()
                else -> 0.toByte()
            }
        )

        when (input.tensorType) {
            com.example.feedsense.analysis.ml.ModelTensorType.FLOAT32 -> {
                for (f in input.floats) {
                    buffer.putFloat(f)
                }
            }
            com.example.feedsense.analysis.ml.ModelTensorType.INT8,
            com.example.feedsense.analysis.ml.ModelTensorType.UINT8 -> {
                buffer.put(input.quantizedBytes!!)
            }
            else -> {
                for (f in input.floats) {
                    buffer.putFloat(f)
                }
            }
        }

        return buffer.array()
    }

    /**
     * SHA-256 hash of a deterministic string (mirrors
     * ReproCanonicalSerializer.sha256Hex).
     */
    fun sha256Hex(data: String): String {
        return sha256Hex(data.toByteArray(Charsets.UTF_8))
    }

    /**
     * SHA-256 hash of raw bytes.
     */
    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
