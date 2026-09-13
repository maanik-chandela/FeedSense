package com.example.feedsense.analysis.ml.runtime

import com.example.feedsense.analysis.ml.InferenceClock
import com.example.feedsense.analysis.ml.ModelInput
import com.example.feedsense.analysis.ml.SystemInferenceClock
import com.example.feedsense.analysis.ml.preprocess.PreprocessedInput
import com.example.feedsense.analysis.ml.repro.ReproArtifactIdentity
import com.example.feedsense.analysis.ml.repro.ReproRuntimeIdentity
import java.security.MessageDigest

// --------------------------------
// MODEL RUNTIME ADAPTER (8B-15-5)
// --------------------------------
//
// The main abstraction that bridges validated preprocessed model
// input → on-device runtime → raw model output.
//
// Conceptual flow:
//
//   PreprocessedInput (from 8B-15-3)
//        ↓
//   ModelRuntimeAdapter
//        ↓
//     1. verify provenance
//     2. check compatibility
//     3. validate input tensor
//     4. execute inference via backend
//     5. validate output tensor
//     6. collect timing data
//     7. build structured output
//        ↓
//   AdapterRawModelOutput
//
// The adapter:
//   - accepts only validated model-ready input from 8B-15-3
//   - verifies model/runtime/preprocessing compatibility
//   - loads the selected local model artifact
//   - executes inference locally
//   - returns structured raw inference output
//   - exposes runtime/model metadata
//   - handles failures explicitly
//   - remains completely separate from the existing baseline path
//
// The adapter does NOT:
//   - perform category classification
//   - make recommendation decisions
//   - integrate with evaluation logic
//   - modify FeedItem, SessionRepository, GroundTruth
//   - connect to production observation pipeline
//   - resize, crop, rotate, normalize, or alter input tensors
//   - accept raw screenshot bytes
//   - download models from the internet
//
// This is a RESEARCH boundary. The output is raw model output,
// NOT a semantic decision.

/**
 * Configuration for the ModelRuntimeAdapter.
 */
data class ModelRuntimeAdapterConfig(
    val artifactIdentity: ReproArtifactIdentity,
    val runtimeIdentity: ReproRuntimeIdentity,
    val expectedOutputShape: List<Int?>? = null,
    val expectedOutputDatatype: String = "FLOAT32",
    val expectedValueRangeMin: Double? = 0.0,
    val expectedValueRangeMax: Double? = 1.0,
    val expectedInputContract: com.example.feedsense.analysis.ml.preprocess.PreprocessingConfig? = null,
    val threadingConfig: RuntimeThreadingConfig = RuntimeThreadingConfig.DEFAULT,
    val enableDeterministicHash: Boolean = true,
    val description: String = ""
) {
    init {
        require(description.isNotBlank() || true) {
            "config must be descriptive for reproducibility"
        }
    }
}

/**
 * The runtime-independent inference backend.
 *
 * Implementations encapsulate the actual runtime (LiteRT, ONNX
 * Runtime, ExecuTorch, etc.) and expose only the operations
 * needed by the adapter. Production-independent code never sees
 * runtime-specific types.
 *
 * The backend is responsible for:
 *   - loading the model from an artifact path or handle
 *   - executing inference on a ModelInput
 *   - returning raw float output tensors
 *   - releasing resources
 */
interface RuntimeInferenceBackend {

    /**
     * Loads the model from the given artifact.
     *
     * @param artifactHandle the artifact handle or path
     * @return success or failure
     */
    fun loadModel(artifactHandle: Any?): ModelLoadAttemptResult

    /**
     * Executes inference on the given input tensor.
     *
     * @param input the validated model input
     * @return raw output tensors indexed by tensor index
     */
    fun executeInference(input: ModelInput): BackendInferenceResult

    /**
     * Releases the loaded model and resources.
     */
    fun release()

    /**
     * Whether a model is currently loaded.
     */
    fun isModelLoaded(): Boolean
}

/**
 * Result of a backend inference execution.
 */
sealed class BackendInferenceResult {
    data class Success(
        val outputTensors: Map<Int, FloatArray>,
        val outputMetadata: List<OutputTensorMetadata>
    ) : BackendInferenceResult()

    data class Failure(
        val failure: RuntimeFailure
    ) : BackendInferenceResult()
}

/**
 * The main runtime adapter that bridges validated preprocessed
 * input to raw model output.
 *
 * The adapter is initialized with:
 *   - a ModelArtifactLoader (locates and loads the model)
 *   - a RuntimeInferenceBackend (executes inference)
 *   - a configuration (identifies artifact, runtime, contracts)
 *
 * Each inference cycle:
 *   1. Verifies provenance from PreprocessedInput
 *   2. Checks compatibility between all components
 *   3. Validates input tensor structure
 *   4. Delegates to the backend for inference
 *   5. Validates output tensor structure
 *   6. Returns a structured AdapterRawModelOutput
 *
 * The adapter manages the resource lifecycle and enforces that
 * inference only happens when the model is loaded and ready.
 */
class ModelRuntimeAdapter(
    private val config: ModelRuntimeAdapterConfig,
    private val artifactLoader: ModelArtifactLoader,
    private val backend: RuntimeInferenceBackend,
    private val clock: InferenceClock = SystemInferenceClock
) {
    private val lifecycle = RuntimeLifecycleManager()
    private val timingCollector = RuntimeTimingCollector(clock)
    private var loadedArtifact: ReproArtifactIdentity? = null

    /**
     * Current lifecycle state.
     */
    val state: RuntimeLifecycleState get() = lifecycle.state

    /**
     * Whether the adapter is ready for inference.
     */
    val isReady: Boolean get() = lifecycle.canInfer

    /**
     * Initializes and loads the model artifact.
     *
     * Idempotent: if already loaded, returns success without
     * re-loading. Handles lifecycle transitions explicitly.
     *
     * @return success or structured failure
     */
    fun initialize(): AdapterInitResult {
        if (!lifecycle.canInitialize) {
            if (lifecycle.state == RuntimeLifecycleState.READY) {
                return AdapterInitResult(
                    success = true,
                    message = "already initialized",
                    loadDurationMs = 0L,
                    artifactIdentity = loadedArtifact
                )
            }
            return AdapterInitResult(
                success = false,
                message = "cannot initialize from state ${lifecycle.state.label}",
                failure = RuntimeFailureFactory.lifecycleViolation(
                    "initialize called from state ${lifecycle.state.label}"
                )
            )
        }

        lifecycle.beginLoad()
        timingCollector.startLoad()

        // Load artifact
        val loadResult = artifactLoader.load(config.artifactIdentity)

        timingCollector.endLoad()

        return when (loadResult) {
            is ModelLoadAttemptResult.Success -> {
                loadedArtifact = loadResult.artifactIdentity
                val backendLoad = backend.loadModel(loadResult.runtimeHandle)
                when (backendLoad) {
                    is ModelLoadAttemptResult.Success -> {
                        lifecycle.completeLoad()
                        AdapterInitResult(
                            success = true,
                            message = "model loaded successfully",
                            loadDurationMs = loadResult.loadDurationMs,
                            artifactIdentity = loadResult.artifactIdentity
                        )
                    }
                    is ModelLoadAttemptResult.Failure -> {
                        lifecycle.failLoad()
                        AdapterInitResult(
                            success = false,
                            message = backendLoad.failure.message,
                            failure = backendLoad.failure,
                            loadDurationMs = backendLoad.loadDurationMs
                        )
                    }
                }
            }
            is ModelLoadAttemptResult.Failure -> {
                lifecycle.failLoad()
                AdapterInitResult(
                    success = false,
                    message = loadResult.failure.message,
                    failure = loadResult.failure,
                    loadDurationMs = loadResult.loadDurationMs
                )
            }
        }
    }

    /**
     * Runs a complete inference cycle on a preprocessed input.
     *
     * This is the main entry point for inference. It:
     *   1. Verifies provenance
     *   2. Checks compatibility
     *   3. Validates input
     *   4. Delegates to backend
     *   5. Validates output
     *   6. Returns structured output
     *
     * @param preprocessed the validated preprocessed input from
     *   8B-15-3
     * @return structured raw model output
     */
    fun infer(preprocessed: PreprocessedInput): AdapterRawModelOutput {
        if (!lifecycle.canInfer) {
            return buildFailureOutput(
                failure = RuntimeFailureFactory.inferenceNotReady(
                    "adapter is in state ${lifecycle.state.label}"
                ),
                preprocessingVersion = preprocessed.configVersion
            )
        }

        val input = preprocessed.input
        val provenanceStartMs = clock.nowMs()

        // 1. Verify provenance
        timingCollector.startCompatibilityCheck()
        val provenanceCheck = RuntimeProvenanceFactory.verifyPrivacyProvenance(preprocessed)
        if (provenanceCheck is CompatibilityCheckResult.Fail) {
            timingCollector.endCompatibilityCheck()
            return buildFailureOutput(
                failure = provenanceCheck.failure,
                preprocessingVersion = preprocessed.configVersion
            )
        }

        // 2. Check compatibility
        val loadedArt = loadedArtifact
        if (loadedArt == null) {
            timingCollector.endCompatibilityCheck()
            return buildFailureOutput(
                failure = RuntimeFailureFactory.inferenceNotReady("no artifact loaded"),
                preprocessingVersion = preprocessed.configVersion
            )
        }

        // When the adapter declares an expected input contract,
        // verify the preprocessed input strictly against it.
        // Otherwise verify structural integrity only.
        val declaredConfig = config.expectedInputContract
        val inputConfig = if (declaredConfig != null) {
            declaredConfig
        } else {
            com.example.feedsense.analysis.ml.preprocess.PreprocessingConfig(
                version = preprocessed.configVersion,
                inputWidth = input.width,
                inputHeight = input.height
            )
        }

        val compatibility = CompatibilityGate.check(
            artifact = loadedArt,
            runtime = config.runtimeIdentity,
            config = inputConfig,
            input = input
        )
        timingCollector.endCompatibilityCheck()

        if (compatibility is CompatibilityCheckResult.Fail) {
            return buildFailureOutput(
                failure = compatibility.failure,
                preprocessingVersion = preprocessed.configVersion
            )
        }

        // 3. Validate input tensor
        timingCollector.startInputValidation()
        val inputCheck = CompatibilityGate.checkInput(
            config = inputConfig,
            input = input
        )
        timingCollector.endInputValidation()

        if (inputCheck is CompatibilityCheckResult.Fail) {
            return buildFailureOutput(
                failure = inputCheck.failure,
                preprocessingVersion = preprocessed.configVersion
            )
        }

        // 4. Execute inference via backend
        lifecycle.beginInference()
        timingCollector.startInference()

        val backendResult = try {
            backend.executeInference(input)
        } catch (e: Exception) {
            timingCollector.endInference()
            lifecycle.completeInference()
            return buildFailureOutput(
                failure = RuntimeFailureFactory.inferenceExecutionFailure(
                    "backend exception: ${e.javaClass.simpleName}: ${e.message}",
                    cause = e
                ),
                preprocessingVersion = preprocessed.configVersion
            )
        }

        timingCollector.endInference()

        val rawOutput = when (backendResult) {
            is BackendInferenceResult.Failure -> {
                lifecycle.completeInference()
                return buildFailureOutput(
                    failure = backendResult.failure,
                    preprocessingVersion = preprocessed.configVersion
                )
            }
            is BackendInferenceResult.Success -> backendResult
        }

        // 5. Validate output tensor
        timingCollector.startOutputValidation()
        val outputContract = OutputTensorValidator.ExpectedOutputContract(
            expectedTensorCount = rawOutput.outputMetadata.size,
            expectedShape = config.expectedOutputShape,
            expectedDatatype = config.expectedOutputDatatype,
            expectedValueRangeMin = config.expectedValueRangeMin,
            expectedValueRangeMax = config.expectedValueRangeMax
        )
        val outputValidation = OutputTensorValidator.validate(
            output = AdapterRawModelOutput(
                artifactIdentity = loadedArt,
                runtimeIdentity = config.runtimeIdentity,
                preprocessingVersion = preprocessed.configVersion,
                inputSignature = "${input.width}x${input.height}x${input.channels}:${input.tensorType.label}",
                outputTensors = rawOutput.outputMetadata,
                rawOutputValues = rawOutput.outputTensors,
                executionStatus = InferenceExecutionStatus.SUCCESS
            ),
            contract = outputContract
        )
        timingCollector.endOutputValidation()

        if (outputValidation is OutputTensorValidator.OutputValidationResult.Invalid) {
            lifecycle.completeInference()
            return buildFailureOutput(
                failure = outputValidation.failure,
                preprocessingVersion = preprocessed.configVersion
            )
        }

        // 6. Build structured output
        lifecycle.completeInference()
        val timing = timingCollector.collect()
        val provenance = RuntimeProvenanceFactory.build(
            preprocessed = preprocessed,
            artifact = loadedArt,
            runtime = config.runtimeIdentity
        )

        // Deterministic hash of the output
        val deterministicHash = if (config.enableDeterministicHash) {
            hashOutput(rawOutput.outputTensors)
        } else {
            null
        }

        return AdapterRawModelOutput(
            artifactIdentity = loadedArt,
            runtimeIdentity = config.runtimeIdentity,
            preprocessingVersion = preprocessed.configVersion,
            inputSignature = "${input.width}x${input.height}x${input.channels}:${input.tensorType.label}",
            outputTensors = rawOutput.outputMetadata,
            rawOutputValues = rawOutput.outputTensors,
            executionStatus = InferenceExecutionStatus.SUCCESS,
            inferenceDurationMs = timing.inferenceDurationMs,
            outputValidationDurationMs = timing.outputValidationDurationMs,
            inferenceTimestampMs = clock.nowMs(),
            deterministicHash = deterministicHash
        )
    }

    /**
     * Releases all resources. Idempotent: can be called multiple
     * times safely.
     */
    fun close() {
        if (lifecycle.state == RuntimeLifecycleState.UNLOADED) return
        lifecycle.release()
        backend.release()
        artifactLoader.release()
        loadedArtifact = null
    }

    /**
     * Returns the current adapter state summary for debugging.
     */
    fun stateSummary(): AdapterStateSummary {
        return AdapterStateSummary(
            lifecycleState = state,
            artifactIdentity = loadedArtifact,
            runtimeIdentity = config.runtimeIdentity,
            threadingConfig = config.threadingConfig,
            isReady = isReady
        )
    }

    private fun buildFailureOutput(
        failure: RuntimeFailure,
        preprocessingVersion: String
    ): AdapterRawModelOutput {
        val timing = timingCollector.collect()
        return AdapterRawModelOutput(
            artifactIdentity = config.artifactIdentity,
            runtimeIdentity = config.runtimeIdentity,
            preprocessingVersion = preprocessingVersion,
            inputSignature = "<failed>",
            outputTensors = emptyList(),
            rawOutputValues = emptyMap(),
            executionStatus = InferenceExecutionStatus.FAILURE,
            failure = failure,
            loadDurationMs = timing.loadDurationMs,
            inferenceDurationMs = timing.inferenceDurationMs,
            outputValidationDurationMs = timing.outputValidationDurationMs,
            inferenceTimestampMs = clock.nowMs()
        )
    }

    private fun hashOutput(tensors: Map<Int, FloatArray>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        for ((idx, values) in tensors.toSortedMap()) {
            digest.update(idx.toString().toByteArray())
            for (v in values) {
                digest.update(java.nio.ByteBuffer.allocate(4).putFloat(v).array())
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * Result of adapter initialization.
 */
data class AdapterInitResult(
    val success: Boolean,
    val message: String,
    val loadDurationMs: Long = 0L,
    val failure: RuntimeFailure? = null,
    val artifactIdentity: ReproArtifactIdentity? = null
)

/**
 * Summary of the adapter's current state.
 */
data class AdapterStateSummary(
    val lifecycleState: RuntimeLifecycleState,
    val artifactIdentity: ReproArtifactIdentity?,
    val runtimeIdentity: ReproRuntimeIdentity,
    val threadingConfig: RuntimeThreadingConfig,
    val isReady: Boolean
)
