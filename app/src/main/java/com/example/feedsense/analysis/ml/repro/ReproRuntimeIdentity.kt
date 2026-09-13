package com.example.feedsense.analysis.ml.repro

// --------------------------------
// RUNTIME IDENTITY (8B-15-2)
// --------------------------------
//
// The identity of the runtime that will execute the model.
//
// Deliberately NOT the runtime installed on the developer
// machine: the deployment artifact must identify its runtime
// independently, because a future APK may carry a different
// LiteRT version than the one used to convert/test.
//
// A change to the runtime version or backend -> NEW runtime
// identity (immutability).

/*
 * Supported execution runtimes, aligned with the 8B-15-1
 * vocabulary.
 */
enum class ReproRuntimeName(override val label: String) : ReprLabeled {
    LITERT("LITERT"),
    TFLITE("TFLITE"),
    ONNX_RUNTIME("ONNX_RUNTIME"),
    EXECUTORCH("EXECUTORCH"),
    OTHER("OTHER"),
    UNKNOWN("UNKNOWN")
}

/*
 * Execution backend / delegate.
 */
enum class ExecutionBackend(override val label: String) : ReprLabeled {
    XNNPACK_CPU("XNNPACK_CPU"),
    GPU_DELEGATE("GPU_DELEGATE"),
    VENDOR_NPU("VENDOR_NPU"),
    CPU("CPU"),
    OTHER("OTHER"),
    UNKNOWN("UNKNOWN")
}

/*
 * Targeted platform. Hardware-specific execution identity
 * belongs to runtime benchmarking, NOT to model identity; this
 * is the supported platform of the runtime, not a bound device.
 */
enum class SupportedPlatform(override val label: String) : ReprLabeled {
    ANDROID("ANDROID"),
    OTHER("OTHER"),
    UNKNOWN("UNKNOWN")
}

/*
 * Immutable identity of the executing runtime.
 */
data class ReproRuntimeIdentity(
    val runtimeName: ReproRuntimeName,
    val runtimeVersion: String,
    val executionBackend: ExecutionBackend = ExecutionBackend.XNNPACK_CPU,
    val supportedPlatform: SupportedPlatform = SupportedPlatform.ANDROID,
    val modelFormat: ReproArtifactFormat,
    val runtimeConfiguration: String? = null
) {

    init {
        require(runtimeVersion.isNotBlank()) { "runtimeVersion must be non-blank" }
    }

    /*
     * Stable key "<runtimeName>:<runtimeVersion>:<backend>".
     */
    val key: String
        get() = "${runtimeName.label}:${runtimeVersion}:${executionBackend.label}"
}
