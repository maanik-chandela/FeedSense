package com.example.feedsense.analysis.ml.runtime

import com.example.feedsense.analysis.ml.ModelInput
import com.example.feedsense.analysis.ml.ModelTensorType
import com.example.feedsense.analysis.ml.preprocess.PreprocessingConfig
import com.example.feedsense.analysis.ml.preprocess.PreprocessingVersion
import com.example.feedsense.analysis.ml.repro.ArtifactAvailability
import com.example.feedsense.analysis.ml.repro.ArtifactValidationStatus
import com.example.feedsense.analysis.ml.repro.ExecutionBackend
import com.example.feedsense.analysis.ml.repro.ReproArtifactFormat
import com.example.feedsense.analysis.ml.repro.ReproArtifactIdentity
import com.example.feedsense.analysis.ml.repro.ReproRuntimeIdentity
import com.example.feedsense.analysis.ml.repro.ReproRuntimeName
import com.example.feedsense.analysis.ml.repro.SupportedPlatform

/*
 * Shared test fixtures for 8B-15-5 runtime adapter tests.
 */
object RuntimeTestFixtures {

    // --------------------------------
    // ARTIFACT IDENTITY
    // --------------------------------

    val TEST_ARTIFACT_AVAILABLE = ReproArtifactIdentity(
        artifactId = "mobilenetv4-conv-s-test",
        fileName = "mobilenetv4_conv_s_test.tflite",
        format = ReproArtifactFormat.TFLITE,
        byteSize = 1024L,
        sha256 = "a".repeat(64),
        sourceReference = "TEST_ONLY - not a real artifact",
        modelId = "mobilenetv4-conv-s",
        artifactVersion = "test-v1",
        availability = ArtifactAvailability.AVAILABLE,
        validationStatus = ArtifactValidationStatus.HASH_MATCHES
    )

    val TEST_ARTIFACT_PENDING = ReproArtifactIdentity(
        artifactId = "mobilenetv4-conv-s-real",
        format = ReproArtifactFormat.TFLITE,
        sourceReference = "8B-15-1 ADR",
        modelId = "mobilenetv4-conv-s",
        artifactVersion = "real-v1",
        availability = ArtifactAvailability.PENDING
    )

    val TEST_ARTIFACT_NOT_AVAILABLE = ReproArtifactIdentity(
        artifactId = "mobilenetv4-conv-s-missing",
        format = ReproArtifactFormat.TFLITE,
        modelId = "mobilenetv4-conv-s",
        artifactVersion = "missing-v1",
        availability = ArtifactAvailability.NOT_AVAILABLE
    )

    // --------------------------------
    // RUNTIME IDENTITY
    // --------------------------------

    val TEST_RUNTIME_LITERT = ReproRuntimeIdentity(
        runtimeName = ReproRuntimeName.LITERT,
        runtimeVersion = "2.16.1",
        executionBackend = ExecutionBackend.XNNPACK_CPU,
        supportedPlatform = SupportedPlatform.ANDROID,
        modelFormat = ReproArtifactFormat.TFLITE
    )

    val TEST_RUNTIME_UNKNOWN = ReproRuntimeIdentity(
        runtimeName = ReproRuntimeName.UNKNOWN,
        runtimeVersion = "unknown",
        modelFormat = ReproArtifactFormat.UNKNOWN
    )

    // --------------------------------
    // PREPROCESSING CONFIGS
    // --------------------------------

    val CONFIG_4x4_DEFAULT = PreprocessingConfig(
        version = PreprocessingVersion.V2,
        inputWidth = 4,
        inputHeight = 4,
        channelOrder = com.example.feedsense.analysis.ml.preprocess.PreprocessChannelOrder.RGB,
        tensorType = com.example.feedsense.analysis.ml.preprocess.PreprocessingTensorType.FLOAT32,
        scale = 1.0 / 255.0,
        tensorLayout = com.example.feedsense.analysis.ml.preprocess.PreprocessingTensorLayout.NHWC
    )

    val CONFIG_224x224_REFERENCED = PreprocessingConfig.mobileNetV4ConvReference()

    // --------------------------------
    // MODEL INPUTS
    // --------------------------------

    fun input4x4Float32(): ModelInput {
        return ModelInput(
            width = 4,
            height = 4,
            channels = 3,
            tensorType = ModelTensorType.FLOAT32,
            floats = FloatArray(4 * 4 * 3) { 0.5f }
        )
    }

    fun input2x2Float32(): ModelInput {
        return ModelInput(
            width = 2,
            height = 2,
            channels = 3,
            tensorType = ModelTensorType.FLOAT32,
            floats = FloatArray(2 * 2 * 3) { 0.25f }
        )
    }

    fun inputWrongDimensions(): ModelInput {
        return ModelInput(
            width = 8,
            height = 8,
            channels = 3,
            tensorType = ModelTensorType.FLOAT32,
            floats = FloatArray(8 * 8 * 3) { 0.5f }
        )
    }

    fun inputWrongDatatype(): ModelInput {
        return ModelInput(
            width = 4,
            height = 4,
            channels = 3,
            tensorType = ModelTensorType.INT8,
            floats = FloatArray(4 * 4 * 3) { 0.5f },
            quantizedBytes = ByteArray(4 * 4 * 3) { 127 }
        )
    }

    fun input4x4Float32WithValues(values: FloatArray): ModelInput {
        return ModelInput(
            width = 4,
            height = 4,
            channels = 3,
            tensorType = ModelTensorType.FLOAT32,
            floats = values.copyOf()
        )
    }

    // --------------------------------
    // ADAPTER CONFIGS
    // --------------------------------

    fun adapterConfig(
        artifact: ReproArtifactIdentity = TEST_ARTIFACT_AVAILABLE,
        runtime: ReproRuntimeIdentity = TEST_RUNTIME_LITERT
    ): ModelRuntimeAdapterConfig {
        return ModelRuntimeAdapterConfig(
            artifactIdentity = artifact,
            runtimeIdentity = runtime,
            expectedOutputShape = listOf(1, 3),
            expectedOutputDatatype = "FLOAT32",
            expectedValueRangeMin = 0.0,
            expectedValueRangeMax = 1.0
        )
    }

    /**
     * Adapter config pinned to a concrete expected input
     * contract (strict dimension/datatype verification).
     */
    fun adapterConfigWithContract(
        expectedInput: com.example.feedsense.analysis.ml.preprocess.PreprocessingConfig =
            CONFIG_4x4_DEFAULT,
        artifact: ReproArtifactIdentity = TEST_ARTIFACT_AVAILABLE,
        runtime: ReproRuntimeIdentity = TEST_RUNTIME_LITERT
    ): ModelRuntimeAdapterConfig {
        return ModelRuntimeAdapterConfig(
            artifactIdentity = artifact,
            runtimeIdentity = runtime,
            expectedOutputShape = listOf(1, 3),
            expectedOutputDatatype = "FLOAT32",
            expectedValueRangeMin = 0.0,
            expectedValueRangeMax = 1.0,
            expectedInputContract = expectedInput
        )
    }
}
