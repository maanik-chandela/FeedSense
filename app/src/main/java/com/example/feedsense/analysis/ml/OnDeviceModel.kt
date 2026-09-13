package com.example.feedsense.analysis.ml

// --------------------------------
// ON-DEVICE MODEL (8B-14)
// --------------------------------
//
// The model-independent inference contract. The rest of
// FeedSense depends on this abstraction, never on TensorFlow
// Lite / ONNX / ExecuTorch / a specific architecture / a
// specific quantization format.
//
// Lifecycle is explicitly:
//
//     load once
//        |
//     infer ... infer ... infer
//        |
//     close / release
//
// A model must NOT be re-loaded per frame (8B-14 section
// 30). The implementation already-backed runtime adapter is
// replaceable behind this interface.
//
// Contracts:
//   - infer() NEVER throws. Failures are returned as an
//     explicit ModelInferenceResult with a controlled status
//     (section 34).
//   - inferred results carry the exact model version and let
//     the caller attach provenance (section 7, 24).
//   - inputs are prepared tensors (ModelInput); the model
//     never touches raw frames (section 16).

interface OnDeviceModel {

    /*
     * Immutable identity + contract of this model.
     */
    val metadata: ModelMetadata

    /*
     * Current lifecycle / availability state.
     */
    val state: ModelState

    /*
     * Loads the model once. Idempotent: calling again on a
     * READY model is a no-op returning the ready result.
     * Never throws; failures are returned as a result and
     * the state becomes FAILED.
     */
    fun load(): ModelLoadResult

    /*
     * Whether the model is loaded and ready to infer.
     */
    fun isReady(): Boolean

    /*
     * Runs one inference on an already-preprocessed input.
     * Never throws.
     */
    fun infer(
        input: ModelInput,
        context: InferenceContext = InferenceContext()
    ): ModelInferenceResult

    /*
     * Releases the underlying runtime / resources. After
     * close(), the state becomes UNAVAILABLE and inference
     * returns MODEL_UNAVAILABLE until load() is called
     * again.
     */
    fun close()
}

/*
 * Per-inference caller context. Attaches attribution that
 * the model must stamp into its result:
 *
 *   - evidenceId   : frame/evidence identifier
 *   - privacyVersion : privacy-processing version that
 *                      produced the safe frame (when known)
 *   - experimentId : optional experiment identity
 */
data class InferenceContext(
    val evidenceId: String? = null,
    val privacyVersion: String? = null,
    val experimentId: String? = null
)