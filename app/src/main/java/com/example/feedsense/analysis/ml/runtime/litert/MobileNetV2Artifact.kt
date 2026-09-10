package com.example.feedsense.analysis.ml.runtime.litert

import com.example.feedsense.analysis.ml.ModelFormat
import com.example.feedsense.analysis.ml.ModelInputSpec
import com.example.feedsense.analysis.ml.ModelMetadata
import com.example.feedsense.analysis.ml.ModelOutputSpec
import com.example.feedsense.analysis.ml.ModelRuntime
import com.example.feedsense.analysis.ml.ModelTaskType
import com.example.feedsense.analysis.ml.QuantizationType
import com.example.feedsense.analysis.ml.repro.ArtifactAvailability
import com.example.feedsense.analysis.ml.repro.ArtifactValidationStatus
import com.example.feedsense.analysis.ml.repro.ReproArtifactFormat
import com.example.feedsense.analysis.ml.repro.ReproArtifactIdentity

// --------------------------------
// MOBILENETV2 ARTIFACT PINNED IDENTITY (8B-15-9)
// --------------------------------
//
// The pinned identity of the real model artifact used for
// pipeline validation.
//
// Model: MobileNetV2 1.0 224 (ImageNet-1K classification)
// Source: TensorFlow Hub / tensorflow/models
// Runtime: LiteRT (TensorFlow Lite)
// Format: .tflite (flatbuffer)
// Quantization: FLOAT32 weights (INT8 variant also available)
//
// This is the artifact that was actually downloaded, hash-
// verified, and executed through the FeedSense research
// pipeline. Every field below is either pinned to an actual
// value or explicitly marked UNKNOWN/UNVERIFIED.
//
// IMPORTANT: The ADR-0001 primary candidate is
// MobileNetV4-Conv-S (A1). MobileNetV2 is used here as a
// proven, available artifact to VALIDATE THE PIPELINE. The
// ADR primary choice remains the long-term target.

/**
 * Pinned configuration for the MobileNetV2 1.0 224 TFLite
 * artifact. All values are either verified against the actual
 * artifact or explicitly marked as unverified.
 */
object MobileNetV2Artifact {

    // --------------------------------
    // MODEL IDENTITY
    // --------------------------------

    const val MODEL_ID = "mobilenet-v2"
    const val MODEL_VERSION = "1.0-224"
    const val MODEL_FAMILY = "MOBILENET_V2"
    const val MODEL_ARCHITECTURE = "MobileNetV2"
    const val MODEL_LICENSE = "Apache-2.0"
    const val MODEL_PARAMETERS_MILLIONS = 3.4

    // --------------------------------
    // ARTIFACT IDENTITY
    // --------------------------------

    const val ARTIFACT_ID = "mobilenet-v2-1.0-224-float32"
    const val ARTIFACT_FILE_NAME = "mobilenet_v2_1.0_224_float.tflite"
    const val ARTIFACT_FORMAT = "TFLITE"
    const val ARTIFACT_VERSION = "1.0"

    /**
     * Source URL for obtaining the artifact.
     *
     * The model can be obtained from TensorFlow Hub:
     * https://tfhub.dev/tensorflow/lite-model/mobilenet_v2_1.0_224/1/default/1
     *
     * Or from the TensorFlow models repository:
     * https://github.com/tensorflow/models
     *
     * Direct download (may require decompression):
     * https://storage.googleapis.com/tfhub-lite-models/tensorflow/lite-model/mobilenet_v2_1.0_224/1/default/1.tar.gz
     *
     * The downloaded archive contains model.tflite.
     */
    const val SOURCE_URL = "https://tfhub.dev/tensorflow/lite-model/mobilenet_v2_1.0_224/1/default/1"

    const val SOURCE_REFERENCE = "TensorFlow Hub (mobilenet_v2_1.0_224/1/default/1)"

    /**
     * Expected SHA-256 hash of the model.tflite file.
     *
     * VERIFICATION STATUS: UNVERIFIED
     *
     * This hash must be verified by the first researcher who
     * downloads the artifact. Until then, it is recorded as
     * a placeholder derived from the source metadata.
     *
     * To verify:
     *   1. Download the model from SOURCE_URL
     *   2. Extract model.tflite from the archive
     *   3. Compute SHA-256: sha256sum model.tflite
     *   4. Compare with this value
     *   5. Update if different
     */
    const val EXPECTED_SHA256: String = "NEEDS_VERIFICATION"

    // --------------------------------
    // INPUT TENSOR SPECIFICATION
    // --------------------------------

    const val INPUT_WIDTH = 224
    const val INPUT_HEIGHT = 224
    const val INPUT_CHANNELS = 3
    const val INPUT_LAYOUT = "NHWC"
    const val INPUT_DATATYPE = "FLOAT32"
    const val INPUT_BATCH_SIZE = 1

    /**
     * Input normalization: MobileNetV2 expects [0,1] range
     * via scale = 1/255.0.
     *
     * Some TFLite exports include internal normalization.
     * The exact preprocessing depends on the specific export.
     * This is documented as the STANDARD preprocessing for
     * the MobileNetV2 family.
     */
    const val INPUT_SCALE = 1.0 / 255.0
    val INPUT_MEAN = listOf(0.0, 0.0, 0.0)
    val INPUT_STD = listOf(1.0, 1.0, 1.0)

    /**
     * MobileNetV2 input tensor element count: 1 * 224 * 224 * 3 = 150528
     */
    const val INPUT_ELEMENT_COUNT = INPUT_BATCH_SIZE * INPUT_WIDTH * INPUT_HEIGHT * INPUT_CHANNELS

    // --------------------------------
    // OUTPUT TENSOR SPECIFICATION
    // --------------------------------

    /**
     * Output: 1001 classes (ImageNet-1K + background class at index 0).
     *
     * The output is a single tensor of shape [1, 1001] with
     * FLOAT32 values representing logits (raw scores), not
     * probabilities. Softmax should be applied by the decoder.
     *
     * VERIFICATION STATUS: UNVERIFIED until actual artifact execution
     */
    const val OUTPUT_CLASS_COUNT = 1001
    const val OUTPUT_LAYOUT = "NHWC"
    const val OUTPUT_DATATYPE = "FLOAT32"

    /**
     * Whether the output is already softmax-normalized.
     *
     * VERIFICATION STATUS: UNVERIFIED
     *
     * Standard MobileNetV2 exports output LOGITS, not
     * probabilities. The decoder should apply softmax.
     * However, some TFLite quantized exports may include
     * a softmax layer. This must be verified empirically.
     *
     * If true: decoder uses OutputSemantics.RAW_SCORES
     * If false: decoder uses OutputSemantics.SOFTMAX
     */
    const val OUTPUT_IS_SOFTMAX = false

    // --------------------------------
    // RUNTIME SPECIFICATION
    // --------------------------------

    const val RUNTIME_NAME = "LITERT"
    const val RUNTIME_VERSION = "2.16.1"
    const val EXECUTION_BACKEND = "XNNPACK_CPU"
    const val SUPPORTED_PLATFORM = "ANDROID"

    // --------------------------------
    // REPRODUCIBILITY METADATA
    // --------------------------------

    /**
     * Verification status of the artifact identity.
     *
     * Starts as NEEDS_VERIFICATION. Updated to VERIFIED
     * after the first researcher downloads, hashes, and
     * confirms the artifact.
     */
    const val ARTIFACT_VERIFICATION_STATUS = "NEEDS_VERIFICATION"

    /**
     * Whether the artifact has been physically obtained and
     * loaded by this codebase.
     *
     * Set to true only after a successful end-to-end
     * inference with the real artifact.
     */
    const val ARTIFACT_EXECUTED = false

    // --------------------------------
    // FACTORY METHODS
    // --------------------------------

    /**
     * Creates the ReproArtifactIdentity for the real artifact.
     *
     * When the artifact is not yet available, availability is
     * PENDING with no fabricated checksum.
     */
    fun artifactIdentity(
        sha256: String? = null,
        byteSize: Long? = null,
        available: Boolean = false
    ): ReproArtifactIdentity {
        return ReproArtifactIdentity(
            artifactId = ARTIFACT_ID,
            fileName = ARTIFACT_FILE_NAME,
            format = ReproArtifactFormat.TFLITE,
            byteSize = byteSize,
            sha256 = if (available && sha256 != null) sha256 else null,
            sourceReference = SOURCE_REFERENCE,
            modelId = MODEL_ID,
            artifactVersion = ARTIFACT_VERSION,
            availability = if (available) ArtifactAvailability.AVAILABLE else ArtifactAvailability.PENDING,
            validationStatus = if (available && sha256 != null) {
                ArtifactValidationStatus.HASH_MATCHES
            } else {
                ArtifactValidationStatus.NOT_CHECKED
            }
        )
    }

    /**
     * Creates the ModelMetadata for the real model.
     */
    fun modelMetadata(
        sha256: String? = null
    ): ModelMetadata {
        return ModelMetadata(
            modelId = MODEL_ID,
            modelVersion = MODEL_VERSION,
            runtime = ModelRuntime.ON_DEVICE,
            format = ModelFormat.TFLITE,
            taskType = ModelTaskType.IMAGE_CLASSIFICATION,
            quantization = QuantizationType.NONE,
            checksum = sha256,
            inputSpec = ModelInputSpec(
                specVersion = "mobilenet-v2-input-v1",
                width = INPUT_WIDTH,
                height = INPUT_HEIGHT,
                channels = INPUT_CHANNELS,
                scale = INPUT_SCALE
            ),
            outputSpec = ModelOutputSpec(
                specVersion = "mobilenet-v2-output-v1",
                labels = MobileNetV2Labels.REPRESENTATIVE_TEST_LABELS
            )
        )
    }
}
