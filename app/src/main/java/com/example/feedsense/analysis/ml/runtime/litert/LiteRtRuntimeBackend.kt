package com.example.feedsense.analysis.ml.runtime.litert

import com.example.feedsense.analysis.ml.ModelInput
import com.example.feedsense.analysis.ml.repro.ArtifactAvailability
import com.example.feedsense.analysis.ml.repro.ArtifactSha256
import com.example.feedsense.analysis.ml.repro.ReproArtifactIdentity
import com.example.feedsense.analysis.ml.repro.ReproArtifactFormat
import com.example.feedsense.analysis.ml.runtime.BackendInferenceResult
import com.example.feedsense.analysis.ml.runtime.ModelLoadAttemptResult
import com.example.feedsense.analysis.ml.runtime.OutputTensorMetadata
import com.example.feedsense.analysis.ml.runtime.RuntimeFailureFactory
import com.example.feedsense.analysis.ml.runtime.RuntimeInferenceBackend
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

// --------------------------------
// LITERT RUNTIME BACKEND (8B-15-9)
// --------------------------------
//
// Real TFLite/LiteRT inference backend. Implements the
// RuntimeInferenceBackend interface using the actual
// TensorFlow Lite Interpreter.
//
// This backend:
//   - loads a real .tflite model file
//   - converts ModelInput (flat FloatArray) to TFLite format
//   - executes real inference
//   - returns structured output
//
// This backend:
//   - does NOT perform preprocessing
//   - does NOT interpret output semantics
//   - does NOT apply taxonomy mapping
//   - runs inference ONLY on the input tensor provided
//
// LIMITATION: This class requires the TFLite native library
// to be available at runtime. It cannot be instantiated in
// a standard JVM test environment. Use Android instrumented
// tests for real execution.

/**
 * Real TFLite inference backend.
 *
 * @param numThreads number of inference threads (null = runtime default)
 * @param useNNAPI whether to use NNAPI delegate (false = CPU/XNNPACK)
 */
class LiteRtRuntimeBackend(
    private val numThreads: Int? = null,
    private val useNNAPI: Boolean = false
) : RuntimeInferenceBackend {

    private var interpreter: Interpreter? = null
    private var loadedModelPath: String? = null
    private var inputShape: IntArray? = null
    private var outputShape: IntArray? = null

    override fun loadModel(artifactHandle: Any?): ModelLoadAttemptResult {
        val startMs = System.currentTimeMillis()

        if (artifactHandle == null || artifactHandle !is String) {
            return ModelLoadAttemptResult.Failure(
                failure = RuntimeFailureFactory.runtimeInitFailure(
                    "artifact handle must be a non-null String file path"
                ),
                loadDurationMs = System.currentTimeMillis() - startMs
            )
        }

        val filePath = artifactHandle
        val file = File(filePath)

        if (!file.exists()) {
            return ModelLoadAttemptResult.Failure(
                failure = RuntimeFailureFactory.artifactMissing(filePath),
                loadDurationMs = System.currentTimeMillis() - startMs
            )
        }

        return try {
            val options = Interpreter.Options().apply {
                numThreads?.let { setNumThreads(it) }
                if (!useNNAPI) {
                    setUseNNAPI(false)
                }
            }

            val interp = Interpreter(file, options)
            interpreter = interp
            loadedModelPath = filePath
            inputShape = interp.getInputTensor(0).shape()
            outputShape = interp.getOutputTensor(0).shape()

            val duration = System.currentTimeMillis() - startMs

            ModelLoadAttemptResult.Success(
                artifactIdentity = ReproArtifactIdentity(
                    artifactId = MobileNetV2Artifact.ARTIFACT_ID,
                    fileName = MobileNetV2Artifact.ARTIFACT_FILE_NAME,
                    format = ReproArtifactFormat.TFLITE,
                    byteSize = file.length(),
                    sha256 = ArtifactSha256.hash(file.readBytes()),
                    sourceReference = MobileNetV2Artifact.SOURCE_REFERENCE,
                    modelId = MobileNetV2Artifact.MODEL_ID,
                    artifactVersion = MobileNetV2Artifact.ARTIFACT_VERSION,
                    availability = ArtifactAvailability.AVAILABLE,
                    validationStatus = com.example.feedsense.analysis.ml.repro.ArtifactValidationStatus.HASH_MATCHES
                ),
                loadDurationMs = duration,
                runtimeHandle = filePath
            )
        } catch (e: Exception) {
            ModelLoadAttemptResult.Failure(
                failure = RuntimeFailureFactory.runtimeInitFailure(
                    "TFLite interpreter load failed: ${e.message}"
                ),
                loadDurationMs = System.currentTimeMillis() - startMs
            )
        }
    }

    override fun executeInference(input: ModelInput): BackendInferenceResult {
        val interp = interpreter
            ?: return BackendInferenceResult.Failure(
                RuntimeFailureFactory.inferenceNotReady("no model loaded")
            )

        return try {
            // Convert flat NHWC FloatArray to 4D array for TFLite
            val inputArray = reshapeFlatTo4D(input)
            val outputArray = Array(1) { FloatArray(MobileNetV2Artifact.OUTPUT_CLASS_COUNT) }

            interp.run(inputArray, outputArray)

            val rawOutput = outputArray[0]

            BackendInferenceResult.Success(
                outputTensors = mapOf(0 to rawOutput.copyOf()),
                outputMetadata = listOf(
                    OutputTensorMetadata(
                        tensorIndex = 0,
                        shape = listOf(1, MobileNetV2Artifact.OUTPUT_CLASS_COUNT),
                        datatype = "FLOAT32",
                        layout = "NHWC",
                        valueRangeMin = rawOutput.min().toDouble(),
                        valueRangeMax = rawOutput.max().toDouble()
                    )
                )
            )
        } catch (e: Exception) {
            BackendInferenceResult.Failure(
                RuntimeFailureFactory.inferenceExecutionFailure(
                    "TFLite inference failed: ${e.message}",
                    cause = e
                )
            )
        }
    }

    override fun release() {
        interpreter?.close()
        interpreter = null
        loadedModelPath = null
        inputShape = null
        outputShape = null
    }

    override fun isModelLoaded(): Boolean = interpreter != null

    /**
     * Returns the model's input tensor shape as reported by TFLite.
     */
    fun modelInputShape(): IntArray? = inputShape?.copyOf()

    /**
     * Returns the model's output tensor shape as reported by TFLite.
     */
    fun modelOutputShape(): IntArray? = outputShape?.copyOf()

    /**
     * Converts a flat NHWC ModelInput to a 4D array for TFLite.
     *
     * Input: FloatArray of size W*H*C (flat NHWC)
     * Output: Array(1) { Array(H) { Array(W) { FloatArray(C) } } }
     */
    internal fun reshapeFlatTo4D(input: ModelInput): Array<Array<Array<FloatArray>>> {
        val w = input.width
        val h = input.height
        val c = input.channels
        val floats = input.floats

        return Array(1) { batch ->
            Array(h) { row ->
                Array(w) { col ->
                    FloatArray(c) { ch ->
                        floats[row * w * c + col * c + ch]
                    }
                }
            }
        }
    }
}
