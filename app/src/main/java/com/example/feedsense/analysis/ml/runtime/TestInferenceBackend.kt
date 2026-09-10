package com.example.feedsense.analysis.ml.runtime

import com.example.feedsense.analysis.ml.ModelInput

// --------------------------------
// TEST MODEL BACKEND (8B-15-5) - TEST_ONLY
// --------------------------------
//
// A deterministic, fully-in-JVM inference backend for testing
// the adapter contract without any real runtime dependency.
//
// TEST_ONLY - not intended for production use. Clearly labeled
// so no one mistakes it for the real model.
//
// Behavior:
//   - identical input ALWAYS produces identical output
//   - configurable output via TestBackendBehavior
//   - no network, no native libraries, no Android dependencies
//   - simulates model loading, inference, and release

/**
 * Configurable behavior for the test inference backend.
 */
sealed interface TestBackendBehavior {

    /**
     * Returns fixed output values regardless of input. Useful
     * for testing the adapter's output validation path.
     */
    data class FixedOutput(
        val outputValues: FloatArray,
        val outputShape: List<Int>
    ) : TestBackendBehavior

    /**
     * Returns output based on a deterministic hash of the input.
     * Different inputs produce different outputs, but the same
     * input always produces the same output.
     */
    data class InputHashOutput(
        val buckets: List<FloatArray>,
        val outputShape: List<Int>
    ) : TestBackendBehavior

    /**
     * Simulates a model that cannot load.
     */
    data class LoadFailure(val message: String) : TestBackendBehavior

    /**
     * Simulates inference failure.
     */
    data class InferenceFailure(val message: String) : TestBackendBehavior

    /**
     * Simulates resource exhaustion.
     */
    data class ResourceExhaustion(val detail: String) : TestBackendBehavior

    /**
     * Simulates malformed output (wrong shape, NaN values).
     */
    data class MalformedOutput(
        val values: FloatArray,
        val shape: List<Int>
    ) : TestBackendBehavior
}

/**
 * TEST_ONLY deterministic inference backend.
 *
 * Produces deterministic output without any real runtime
 * dependency. Used to exercise the adapter contract in JVM
 * tests.
 *
 * TEST_ONLY - this is NOT a real model.
 */
class TestInferenceBackend(
    private val behavior: TestBackendBehavior = TestBackendBehavior.FixedOutput(
        floatArrayOf(0.5f, 0.3f, 0.2f),
        listOf(1, 3)
    )
) : RuntimeInferenceBackend {

    private var loaded = false
    private var inferenceCount = 0

    override fun loadModel(artifactHandle: Any?): ModelLoadAttemptResult {
        return when (behavior) {
            is TestBackendBehavior.LoadFailure -> {
                loaded = false
                ModelLoadAttemptResult.Failure(
                    failure = RuntimeFailureFactory.runtimeInitFailure(behavior.message),
                    loadDurationMs = 0L
                )
            }
            else -> {
                loaded = true
                ModelLoadAttemptResult.Success(
                    artifactIdentity = com.example.feedsense.analysis.ml.repro.ReproArtifactIdentity(
                        artifactId = "test-artifact",
                        format = com.example.feedsense.analysis.ml.repro.ReproArtifactFormat.OTHER,
                        modelId = "test-model",
                        artifactVersion = "test-v1",
                        availability = com.example.feedsense.analysis.ml.repro.ArtifactAvailability.AVAILABLE,
                        sha256 = "0".repeat(64)
                    ),
                    loadDurationMs = 0L,
                    runtimeHandle = null
                )
            }
        }
    }

    override fun executeInference(input: ModelInput): BackendInferenceResult {
        if (!loaded) {
            return BackendInferenceResult.Failure(
                RuntimeFailureFactory.inferenceNotReady("model not loaded")
            )
        }

        inferenceCount++

        return when (behavior) {
            is TestBackendBehavior.FixedOutput -> {
                BackendInferenceResult.Success(
                    outputTensors = mapOf(0 to behavior.outputValues.copyOf()),
                    outputMetadata = listOf(
                        OutputTensorMetadata(
                            tensorIndex = 0,
                            shape = behavior.outputShape,
                            datatype = "FLOAT32",
                            valueRangeMin = behavior.outputValues.min().toDouble(),
                            valueRangeMax = behavior.outputValues.max().toDouble()
                        )
                    )
                )
            }
            is TestBackendBehavior.InputHashOutput -> {
                val index = (inputHash(input) % behavior.buckets.size.toLong()).toInt()
                    .coerceIn(0, behavior.buckets.size - 1)
                BackendInferenceResult.Success(
                    outputTensors = mapOf(0 to behavior.buckets[index].copyOf()),
                    outputMetadata = listOf(
                        OutputTensorMetadata(
                            tensorIndex = 0,
                            shape = behavior.outputShape,
                            datatype = "FLOAT32",
                            valueRangeMin = behavior.buckets[index].min().toDouble(),
                            valueRangeMax = behavior.buckets[index].max().toDouble()
                        )
                    )
                )
            }
            is TestBackendBehavior.InferenceFailure -> {
                BackendInferenceResult.Failure(
                    RuntimeFailureFactory.inferenceExecutionFailure(behavior.message)
                )
            }
            is TestBackendBehavior.ResourceExhaustion -> {
                BackendInferenceResult.Failure(
                    RuntimeFailureFactory.inferenceResourceExhaustion(behavior.detail)
                )
            }
            is TestBackendBehavior.MalformedOutput -> {
                BackendInferenceResult.Success(
                    outputTensors = mapOf(0 to behavior.values.copyOf()),
                    outputMetadata = listOf(
                        OutputTensorMetadata(
                            tensorIndex = 0,
                            shape = behavior.shape,
                            datatype = "FLOAT32"
                        )
                    )
                )
            }
            is TestBackendBehavior.LoadFailure -> {
                BackendInferenceResult.Failure(
                    RuntimeFailureFactory.inferenceNotReady("model not loaded")
                )
            }
        }
    }

    override fun release() {
        loaded = false
    }

    override fun isModelLoaded(): Boolean = loaded

    /**
     * Returns the number of inference calls made.
     */
    fun inferenceCallCount(): Int = inferenceCount

    private fun inputHash(input: ModelInput): Long {
        var hash = 1469598103934665603L
        val prime = 1099511628211L
        hash = (hash xor input.width.toLong()) * prime
        hash = (hash xor input.height.toLong()) * prime
        hash = (hash xor input.channels.toLong()) * prime
        for (v in input.floats) {
            hash = (hash xor v.toRawBits().toLong()) * prime
        }
        return hash
    }
}
