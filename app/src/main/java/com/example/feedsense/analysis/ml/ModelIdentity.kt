package com.example.feedsense.analysis.ml

// --------------------------------
// MODEL IDENTITY (Milestone 8B-14)
// --------------------------------
//
// Every on-device ML model carries explicit identity and
// metadata so predictions are always attributable to the
// exact model artifact that produced them.
//
// The FeedSense baseline and the on-device ML path stay
// separate and independently reproducible. This file
// defines the runtime/format/task/quantization vocabulary
// plus the immutable ModelMetadata carried by every model.

/*
 * Where the model executes.
 *
 * FeedSense is local-only (8B-14 section 38): there is no
 * cloud inference fallback. Additional runtime placements
 * can be added later as separate enum values.
 */
enum class ModelRuntime(val label: String) {
    ON_DEVICE("ON_DEVICE")
}

/*
 * The concrete model artifact format / framework. FeedSense
 * does NOT depend on any of these runtimes today; the enum
 * documents what an artifact claims to be so a future
 * runtime adapter can be chosen by model support.
 */
enum class ModelFormat(val label: String) {
    TFLITE("TFLITE"),
    ONNX("ONNX"),
    EXECUTORCH("EXECUTORCH"),
    DETERMINISTIC("DETERMINISTIC"),
    UNKNOWN("UNKNOWN")
}

/*
 * The task the model performs. IMAGE_CLASSIFICATION is the
 * current research target; MULTI_LABEL_IMAGE keeps the
 * contract open for future multi-label work (8B-14 section
 * 41) without redesigning the category schema.
 */
enum class ModelTaskType(val label: String) {
    IMAGE_CLASSIFICATION("IMAGE_CLASSIFICATION"),
    MULTI_LABEL_IMAGE("MULTI_LABEL_IMAGE")
}

/*
 * Quantization format, where applicable. Independent of the
 * tensor type declared in the input spec: it describes what
 * weight/activation encoding the ARTIFACT uses.
 */
enum class QuantizationType(val label: String) {
    NONE("NONE"),
    INT8("INT8"),
    UINT8("UINT8"),
    FP16("FP16")
}

/*
 * Immutable model identity + contract metadata.
 *
 * A prediction is reproducible only when the model identity,
 * the exact version, the checksum, and the input/output
 * specifications are all recorded alongside it.
 *
 *   - modelVersion is a HUMAN-READABLE tag (e.g. "ml-v1").
 *     Two artifacts claiming the same tag but different
 *     bytes are DETECTED via checksum, never assumed equal.
 *   - checksum is the SHA-256 hex of the artifact bytes where
 *     practical; null when no artifact is backed by bytes
 *     (e.g. a deterministic test model).
 */
data class ModelMetadata(
    val modelId: String,
    val modelVersion: String,
    val runtime: ModelRuntime = ModelRuntime.ON_DEVICE,
    val format: ModelFormat = ModelFormat.DETERMINISTIC,
    val taskType: ModelTaskType = ModelTaskType.IMAGE_CLASSIFICATION,
    val quantization: QuantizationType = QuantizationType.NONE,
    val checksum: String? = null,
    val inputSpec: ModelInputSpec,
    val outputSpec: ModelOutputSpec
) {

    init {
        require(modelId.isNotBlank()) { "modelId must be non-blank" }
        require(modelVersion.isNotBlank()) { "modelVersion must be non-blank" }
        require(runtime == ModelRuntime.ON_DEVICE) {
            "FeedSense is local-only; runtime must be ON_DEVICE"
        }
    }

    /*
     * Stable "<modelId>:<modelVersion>" key. Not a content
     * identity: two artifacts may share this key yet differ
     * in bytes, which is why checksum is tracked too.
     */
    val identityKey: String
        get() = "$modelId:$modelVersion"
}