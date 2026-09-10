package com.example.feedsense.analysis.ml

// --------------------------------
// MODEL STATE (8B-14)
// --------------------------------
//
// Lifecycle / availability state vocabulary shared by the
// model abstraction and the model registry. One vocabulary,
// so callers never inspect runtime-specific exceptions just
// to learn whether a model can serve a frame.
//
// The six observable states match the milestone list:
// available / unavailable / incompatible / loading / ready /
// failed (8B-14 section 37).

enum class ModelState(val label: String) {
    /*
     * Registered but not yet loaded or probed.
     */
    AVAILABLE("AVAILABLE"),

    /*
     * Model cannot serve inference (missing, closed,
     * never loaded successfully).
     */
    UNAVAILABLE("UNAVAILABLE"),

    /*
     * Model's artifact is incompatible with this device /
     * runtime.
     */
    INCOMPATIBLE("INCOMPATIBLE"),

    /*
     * Load in progress (asynchronous loaders can observe
     * this between load() start and completion).
     */
    LOADING("LOADING"),

    /*
     * Loaded and ready to serve repeated inferences.
     */
    READY("READY"),

    /*
     * Load attempted and failed.
     */
    FAILED("FAILED")
}

/*
 * Outcome of an OnDeviceModel.load() call.
 */
data class ModelLoadResult(
    val modelId: String,
    val modelVersion: String,
    val success: Boolean,
    val state: ModelState,
    val message: String? = null,
    val loadedAtMs: Long = 0L
) {

    val ready: Boolean
        get() = success && state == ModelState.READY
}