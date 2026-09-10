package com.example.feedsense.analysis.ml.runtime

// --------------------------------
// RUNTIME THREADING (8B-15-5)
// --------------------------------
//
// Explicit threading configuration for the runtime adapter.
//
// Rules:
//   - model inference MUST NOT run on the Android main thread
//   - the adapter itself is UI-independent
//   - threading configuration is explicit, not assumed
//   - the objective is correctness and reproducibility, not
//     aggressive optimization
//
// The threading configuration is documented and exportable for
// research reproducibility. Thread counts are not aggressively
// tuned in this milestone.

/**
 * Threading configuration for model runtime execution.
 *
 * This is a research configuration, not a performance-tuned
 * production profile.
 */
data class RuntimeThreadingConfig(
    /**
     * Number of threads for model inference. null = runtime
     * default.
     */
    val inferenceThreadCount: Int? = null,

    /**
     * Number of threads for model initialization/loading.
     * null = runtime default.
     */
    val initializationThreadCount: Int? = null,

    /**
     * Whether inference is allowed on the main thread.
     * MUST be false for production.
     */
    val allowMainThreadInference: Boolean = false,

    /**
     * Whether the adapter should use a dedicated thread for
     * inference (recommended for production).
     */
    val useDedicatedInferenceThread: Boolean = false,

    /**
     * Human-readable description of the threading configuration.
     */
    val description: String = ""
) {
    init {
        inferenceThreadCount?.let {
            require(it > 0) { "inferenceThreadCount must be > 0" }
        }
        initializationThreadCount?.let {
            require(it > 0) { "initializationThreadCount must be > 0" }
        }
    }

    /**
     * Stable key for the threading configuration.
     */
    val key: String
        get() = buildString {
            append("infer=${inferenceThreadCount ?: "default"}")
            append(":init=${initializationThreadCount ?: "default"}")
            append(":mainThread=$allowMainThreadInference")
            append(":dedicated=$useDedicatedInferenceThread")
        }

    companion object {
        /**
         * Default configuration: runtime defaults, no main
         * thread inference.
         */
        val DEFAULT = RuntimeThreadingConfig(
            description = "default runtime threading, no main thread inference"
        )

        /**
         * Single-threaded configuration for deterministic
         * testing.
         */
        val SINGLE_THREADED = RuntimeThreadingConfig(
            inferenceThreadCount = 1,
            initializationThreadCount = 1,
            allowMainThreadInference = true,
            description = "single-threaded for deterministic testing"
        )
    }
}
