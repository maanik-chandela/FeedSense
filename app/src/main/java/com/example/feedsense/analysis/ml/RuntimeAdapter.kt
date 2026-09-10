package com.example.feedsense.analysis.ml

// --------------------------------
// RUNTIME ADAPTER (8B-14)
// --------------------------------
//
// The boundary between FeedSense and a concrete ML runtime.
//
//     FeedSense
//        ↓
//      OnDeviceModel
//        ↓
//      RuntimeAdapter
//        ↓
//     TFLite / ONNX / ExecuTorch / future runtime
//
// This milestone adds NO runtime dependency (8B-14 sections
// 18, 53). The interface exists so that when the first real
// model lands, the runtime decision (model support, Android
// compatibility, size, latency, memory, quantization,
// maintainability) is contained behind this boundary.
//
// The adapter works on the neutral ModelInput/data types and
// returns a neutral RawModelOutput; no framework tensor
// leaks into the rest of FeedSense.

/*
 * Raised by adapters when a model cannot be loaded or no
 * longer exists. Caught by the OnDeviceModel and converted
 * into an explicit MODEL_UNAVAILABLE result.
 */
class RuntimeUnavailableException(message: String) : Exception(message)

/*
 * Raised by adapters on resource exhaustion during
 * inference. Converted into RESOURCE_LIMIT by the model.
 */
class RuntimeResourceException(
    val resource: ResourceFailureType,
    message: String
) : Exception(message)

/*
 * Outcome of loading a runtime/model artifact.
 */
data class RuntimeLoadResult(
    val success: Boolean,
    val message: String? = null
)

/*
 * Framework output converted to a neutral representation:
 * label->confidence candidates plus safe metadata.
 *
 * rawMetadata must be SAFE: version/count style keys only,
 * never pixels, OCR text, or private content.
 */
data class RawModelOutput(
    val scoredItems: List<RankedPrediction>,
    val latencyMs: Long,
    val rawMetadata: Map<String, String> = emptyMap()
)

interface RuntimeAdapter {

    val runtime: ModelRuntime

    /*
     * Loads the artifact described by metadata. Returns
     * success, or throws RuntimeUnavailableException/
     * RuntimeResourceException when it cannot load.
     */
    fun load(metadata: ModelMetadata): RuntimeLoadResult

    /*
     * Whether a model is currently loaded.
     */
    fun isLoaded(): Boolean

    /*
     * Runs the raw inference for a prepared input. May
     * throw RuntimeInferenceException (converted to
     * INFERENCE_FAILURE) or RuntimeResourceException
     * (converted to RESOURCE_LIMIT).
     */
    fun inferRaw(input: ModelInput): RawModelOutput

    /*
     * Releases the loaded model.
     */
    fun release()
}