package com.example.feedsense.analysis.ml

// --------------------------------
// FAKE / DETERMINISTIC MODEL (8B-14)
// --------------------------------
//
// A fully deterministic OnDeviceModel + RuntimeAdapter for
// tests and for exercising the foundation before the first
// real runtime lands.
//
// Identical input ALWAYS produces identical output - there
// is no randomness. Behavior is configurable via
// FakeModelBehavior so tests can simulate:
//
//   - successful prediction
//   - multiple top-K results
//   - low confidence
//   - unavailable model
//   - invalid output (rejected by ModelOutputValidator)
//   - inference failure
//   - resource limits
//   - predictable latency (injected clock)
//
// The fake runtime never sees raw frames: it consumes
// prepared ModelInput tensors like any real runtime would.

/*
 * Configurable deterministic behavior of the fake runtime.
 */
sealed interface FakeModelBehavior {

    /*
     * Fixed label->confidence scores, returned whatever the
     * input is. Trivially deterministic.
     */
    data class FixedScores(
        val scores: Map<String, Double>
    ) : FakeModelBehavior

    /*
     * Picks a score bucket from the input tensor's content
     * hash - identical input picks the identical bucket, so
     * this models "the model reacts to the image" without any
     * randomness.
     */
    data class ScoresByInputHash(
        val buckets: List<Map<String, Double>>,
        val fallback: Map<String, Double> = emptyMap()
    ) : FakeModelBehavior

    /*
     * Model artifact exists but cannot be used.
     */
    data class Unavailable(val message: String) : FakeModelBehavior

    /*
     * load() fails.
     */
    data class LoadFailure(val message: String) : FakeModelBehavior

    /*
     * inferRaw() crashes; the model converts it to an
     * explicit INFERENCE_FAILURE result.
     */
    data class InferenceCrash(val message: String) : FakeModelBehavior

    /*
     * inferRaw() reports resource exhaustion.
     */
    data class ResourceLimit(val resource: ResourceFailureType) :
        FakeModelBehavior

    /*
     * Raw output that MUST be rejected by the validator
     * (NaN, out-of-range, or out-of-taxonomy categories).
     */
    data class MalformedScores(
        val scores: Map<String, Double>
    ) : FakeModelBehavior
}

class FakeRuntimeAdapter(
    private val behavior: FakeModelBehavior = FakeModelBehavior.FixedScores(emptyMap()),
    private val clock: InferenceClock = SystemInferenceClock
) : RuntimeAdapter {

    override val runtime: ModelRuntime = ModelRuntime.ON_DEVICE

    private var loaded: Boolean = false

    override fun load(metadata: ModelMetadata): RuntimeLoadResult {
        return when (behavior) {
            is FakeModelBehavior.Unavailable -> {
                loaded = false
                RuntimeLoadResult(false, behavior.message)
            }
            is FakeModelBehavior.LoadFailure -> {
                loaded = false
                RuntimeLoadResult(false, behavior.message)
            }
            else -> {
                loaded = true
                RuntimeLoadResult(true)
            }
        }
    }

    override fun isLoaded(): Boolean = loaded

    override fun inferRaw(input: ModelInput): RawModelOutput {
        if (!loaded) {
            throw RuntimeUnavailableException("model not loaded")
        }
        val start = clock.nowMs()
        val latencyMs = clock.nowMs() - start
        return when (behavior) {
            is FakeModelBehavior.FixedScores -> RawModelOutput(
                scoredItems = behavior.scores
                    .map { (k, v) -> RankedPrediction(k, v) },
                latencyMs = latencyMs,
                rawMetadata = mapOf("fake.runtime" to "fixed-scores")
            )
            is FakeModelBehavior.ScoresByInputHash -> {
                val index = (inputHash(input) % behavior.buckets.size.toLong()).toInt()
                RawModelOutput(
                    scoredItems = behavior.buckets[index]
                        .map { (k, v) -> RankedPrediction(k, v) },
                    latencyMs = latencyMs,
                    rawMetadata = mapOf("fake.runtime" to "hash-bucket")
                )
            }
            is FakeModelBehavior.InferenceCrash ->
                throw RuntimeInferenceException(behavior.message)
            is FakeModelBehavior.ResourceLimit ->
                throw RuntimeResourceException(
                    behavior.resource,
                    behavior.resource.label
                )
            is FakeModelBehavior.MalformedScores -> RawModelOutput(
                scoredItems = behavior.scores
                    .map { (k, v) -> RankedPrediction(k, v) },
                latencyMs = latencyMs,
                rawMetadata = mapOf("fake.runtime" to "malformed")
            )
            is FakeModelBehavior.Unavailable ->
                throw RuntimeUnavailableException(behavior.message)
            is FakeModelBehavior.LoadFailure ->
                throw RuntimeUnavailableException(behavior.message)
        }
    }

    override fun release() {
        loaded = false
    }

    /*
     * Stable content hash of the input tensor (FNV-1a over
     * the float bit patterns and dims). Deterministic.
     */
    private fun inputHash(input: ModelInput): Long {
        var hash = 1469598103934665603L
        val prime = 1099511628211L
        fun step(value: Long) {
            hash = (hash xor value) * prime
        }
        step(input.width.toLong())
        step(input.height.toLong())
        step(input.channels.toLong())
        for (v in input.floats) {
            step(v.toRawBits().toLong())
        }
        return hash
    }
}

class RuntimeInferenceException(message: String) : Exception(message)

/*
 * Deterministic OnDeviceModel over the fake runtime.
 *
 * Handles lifecycle, input validation, exception isolation
 * and output validation so tests exercise the SAME paths a
 * real model will later use.
 */
class FakeOnDeviceModel(
    override val metadata: ModelMetadata,
    behavior: FakeModelBehavior =
        FakeModelBehavior.FixedScores(emptyMap()),
    private val clock: InferenceClock = SystemInferenceClock
) : OnDeviceModel {

    private val runtimeAdapter =
        FakeRuntimeAdapter(behavior, clock)

    private val validator = ModelOutputValidator(metadata.outputSpec)

    private var currentState: ModelState = ModelState.AVAILABLE

    override val state: ModelState
        get() = currentState

    override fun load(): ModelLoadResult {
        if (currentState == ModelState.READY) {
            return readyResult()
        }
        val start = clock.nowMs()
        val result = try {
            runtimeAdapter.load(metadata)
        } catch (e: Exception) {
            RuntimeLoadResult(false, e.message)
        }
        val loadedAtMs = clock.nowMs()
        return if (result.success) {
            currentState = ModelState.READY
            ModelLoadResult(
                modelId = metadata.modelId,
                modelVersion = metadata.modelVersion,
                success = true,
                state = ModelState.READY,
                loadedAtMs = loadedAtMs
            )
        } else {
            currentState = ModelState.FAILED
            ModelLoadResult(
                modelId = metadata.modelId,
                modelVersion = metadata.modelVersion,
                success = false,
                state = ModelState.FAILED,
                message = result.message,
                loadedAtMs = loadedAtMs
            )
        }
    }

    override fun isReady(): Boolean = currentState == ModelState.READY

    override fun infer(
        input: ModelInput,
        context: InferenceContext
    ): ModelInferenceResult {
        if (!isReady()) {
            return ModelInferenceResult.failure(
                modelId = metadata.modelId,
                modelVersion = metadata.modelVersion,
                modelChecksum = metadata.checksum,
                status = InferenceStatus.MODEL_UNAVAILABLE,
                phase = "infer",
                message = "model not ready (state=${currentState.label})",
                context = context
            )
        }

        if (!inputMatchesSpec(input, metadata.inputSpec)) {
            return ModelInferenceResult.failure(
                modelId = metadata.modelId,
                modelVersion = metadata.modelVersion,
                modelChecksum = metadata.checksum,
                status = InferenceStatus.INVALID_INPUT,
                phase = "infer",
                message = "input does not match model input spec",
                context = context
            )
        }

        val start = clock.nowMs()
        val raw = try {
            runtimeAdapter.inferRaw(input)
        } catch (e: RuntimeResourceException) {
            return resourceLimitResult(e.resource, context)
        } catch (e: Exception) {
            return ModelInferenceResult.failure(
                modelId = metadata.modelId,
                modelVersion = metadata.modelVersion,
                modelChecksum = metadata.checksum,
                status = InferenceStatus.INFERENCE_FAILURE,
                phase = "infer",
                message = "inference exception: ${e.javaClass.simpleName}",
                context = context
            )
        }

        val latencyMs = clock.nowMs() - start
        val validation = validator.validate(raw.scoredItems)

        return ModelInferenceResult(
            modelId = metadata.modelId,
            modelVersion = metadata.modelVersion,
            modelChecksum = metadata.checksum,
            status = validation.status,
            rankedPredictions = validation.ranked,
            preprocessVersion = null,
            privacyVersion = context.privacyVersion,
            inputSpecVersion = metadata.inputSpec.specVersion,
            outputSpecVersion = metadata.outputSpec.specVersion,
            evidenceId = context.evidenceId,
            experimentId = context.experimentId,
            timestampMs = clock.nowMs(),
            inferenceLatencyMs = latencyMs,
            rawModelMetadata = raw.rawMetadata,
            failure = if (validation.valid) {
                null
            } else {
                InferenceFailure(
                    status = InferenceStatus.OUTPUT_INVALID,
                    phase = "validate",
                    message = validation.message ?: "invalid model output"
                )
            }
        )
    }

    override fun close() {
        runtimeAdapter.release()
        currentState = ModelState.UNAVAILABLE
    }

    private fun inputMatchesSpec(
        input: ModelInput,
        spec: ModelInputSpec
    ): Boolean {
        return input.width == spec.width &&
            input.height == spec.height &&
            input.channels == spec.channels &&
            input.tensorType == spec.tensorType
    }

    private fun resourceLimitResult(
        resource: ResourceFailureType,
        context: InferenceContext
    ): ModelInferenceResult {
        return ModelInferenceResult(
            modelId = metadata.modelId,
            modelVersion = metadata.modelVersion,
            modelChecksum = metadata.checksum,
            status = InferenceStatus.RESOURCE_LIMIT,
            privacyVersion = context.privacyVersion,
            evidenceId = context.evidenceId,
            experimentId = context.experimentId,
            failure = InferenceFailure(
                status = InferenceStatus.RESOURCE_LIMIT,
                phase = "infer",
                message = resource.label
            )
        )
    }

    private fun readyResult(): ModelLoadResult {
        return ModelLoadResult(
            modelId = metadata.modelId,
            modelVersion = metadata.modelVersion,
            success = true,
            state = ModelState.READY,
            loadedAtMs = clock.nowMs()
        )
    }
}