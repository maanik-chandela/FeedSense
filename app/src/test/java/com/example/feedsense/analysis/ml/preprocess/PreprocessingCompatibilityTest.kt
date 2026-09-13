package com.example.feedsense.analysis.ml.preprocess

import com.example.feedsense.analysis.ml.ModelFormat
import com.example.feedsense.analysis.ml.ModelMetadata
import com.example.feedsense.analysis.ml.ModelOutputSpec
import com.example.feedsense.analysis.ml.ModelPixelFormat
import com.example.feedsense.analysis.ml.ModelTensorType
import com.example.feedsense.analysis.ml.repro.ArtifactAvailability
import com.example.feedsense.analysis.ml.repro.ArtifactValidationStatus
import com.example.feedsense.analysis.ml.repro.ExecutionBackend
import com.example.feedsense.analysis.ml.repro.QuantizationDepth
import com.example.feedsense.analysis.ml.repro.QuantizationMethod
import com.example.feedsense.analysis.ml.repro.QuantizationScope
import com.example.feedsense.analysis.ml.repro.ReproArtifactFormat
import com.example.feedsense.analysis.ml.repro.ReproArtifactIdentity
import com.example.feedsense.analysis.ml.repro.ReproQuantizationIdentity
import com.example.feedsense.analysis.ml.repro.ReproRuntimeIdentity
import com.example.feedsense.analysis.ml.repro.ReproRuntimeName
import com.example.feedsense.analysis.ml.repro.SupportedPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-3 (§17 compatibility identity).
 *
 * The system must answer COMPATIBLE / INCOMPATIBLE deterministically
 * between a PreprocessingConfig and (a) a declared 8B-14 model input
 * spec, and (b) a 8B-15-2 artifact/runtime/quantization contract.
 * Incompatible configurations are never silently adapted.
 */
class PreprocessingCompatibilityTest {

    private val reference: PreprocessingConfig =
        PreprocessingConfig.mobileNetV4ConvReference()

    private fun metadataFor(spec: com.example.feedsense.analysis.ml.ModelInputSpec): ModelMetadata =
        ModelMetadata(
            modelId = "test-model",
            modelVersion = "v1",
            format = ModelFormat.DETERMINISTIC,
            quantization = com.example.feedsense.analysis.ml.QuantizationType.NONE,
            checksum = null,
            inputSpec = spec,
            outputSpec = ModelOutputSpec("output-v1", labels = listOf("a", "b"))
        )

    private fun matchingSpec(): com.example.feedsense.analysis.ml.ModelInputSpec {
        val spec = reference.toModelInputSpec("input-spec-test")!!
        assertEquals(224, spec.width)
        assertEquals(224, spec.height)
        assertEquals(ModelPixelFormat.RGB, spec.pixelFormat)
        return spec
    }

    // --------------------------------
    // LAYER A: config vs declared 8B-14 contract
    // --------------------------------

    @Test
    fun `matching config and spec report COMPATIBLE`() {
        val report = DeclaredContractChecker.check(reference, metadataFor(matchingSpec()))
        assertFalse(report.hasErrors)
        assertTrue(report.compatible)
        assertEquals("COMPATIBLE", report.summary)
    }

    @Test
    fun `dimension mismatch is an ERROR`() {
        val metadata = metadataFor(matchingSpec())
        val report = DeclaredContractChecker.check(
            reference.copy(inputWidth = 320, inputHeight = 320),
            metadata
        )
        assertTrue(report.hasErrors)
        assertFalse(report.compatible)
        assertTrue(
            report.issues.any { it.aspect == "inputWidth" && it.severity == IssueSeverity.ERROR }
        )
        assertTrue(
            report.issues.any { it.aspect == "inputHeight" && it.severity == IssueSeverity.ERROR }
        )
    }

    @Test
    fun `tensor datatype mismatch is an ERROR`() {
        val metadata = metadataFor(matchingSpec())
        val int8Config = reference.copy(
            tensorType = PreprocessingTensorType.INT8,
            scale = 1.0 / 255.0,
            zeroPoint = -128.0
        )
        val report = DeclaredContractChecker.check(int8Config, metadata)
        assertTrue(report.hasErrors)
        assertTrue(
            report.issues.any { it.aspect == "tensorType" && it.severity == IssueSeverity.ERROR }
        )
    }

    @Test
    fun `channel mismatch is an ERROR`() {
        val metadata = metadataFor(matchingSpec())
        val rgbaConfig = reference.copy(
            colorFormat = ColorFormat.RGBA,
            channelOrder = PreprocessChannelOrder.RGBA,
            alphaPolicy = AlphaPolicy.PRESERVE,
            mean = emptyList(),
            std = emptyList()
        )
        val report = DeclaredContractChecker.check(rgbaConfig, metadata)
        assertTrue(report.hasErrors)
        assertTrue(
            report.issues.any { it.aspect == "channels" && it.severity == IssueSeverity.ERROR }
        )
    }

    @Test
    fun `normalization contract mismatch is an ERROR`() {
        // Model declares NO normalization; config applies mean/std.
        val noNormSpec = com.example.feedsense.analysis.ml.ModelInputSpec(
            specVersion = "input-spec-test",
            width = 224,
            height = 224,
            channels = 3,
            pixelFormat = ModelPixelFormat.RGB,
            tensorType = ModelTensorType.FLOAT32,
            scale = 1.0 / 255.0,
            normalization = null
        )
        val report = DeclaredContractChecker.check(reference, metadataFor(noNormSpec))
        assertTrue(report.hasErrors)
        assertTrue(
            report.issues.any { it.aspect == "normalization" && it.severity == IssueSeverity.ERROR }
        )
    }

    @Test
    fun `normalization value mismatch is an ERROR`() {
        val metadata = metadataFor(matchingSpec())
        val wrongNorm = reference.copy(mean = listOf(0.0, 0.0, 0.0), std = listOf(1.0, 1.0, 1.0))
        val report = DeclaredContractChecker.check(wrongNorm, metadata)
        assertTrue(report.hasErrors)
        assertTrue(
            report.issues.any { it.aspect == "normalization" && it.severity == IssueSeverity.ERROR }
        )
    }

    @Test
    fun `pixel format mismatch is an ERROR`() {
        val argbSpec = com.example.feedsense.analysis.ml.ModelInputSpec(
            specVersion = "input-spec-test",
            width = 224,
            height = 224,
            channels = 4,
            pixelFormat = ModelPixelFormat.ARGB,
            tensorType = ModelTensorType.FLOAT32,
            scale = 1.0 / 255.0,
            normalization = null
        )
        val report = DeclaredContractChecker.check(reference, metadataFor(argbSpec))
        assertTrue(report.hasErrors)
        assertTrue(
            report.issues.any { it.aspect == "pixelFormat" && it.severity == IssueSeverity.ERROR }
        )
    }

    // --------------------------------
    // LAYER B: config vs 8B-15-2 reproduction identities
    // --------------------------------

    private fun pendingArtifact(): ReproArtifactIdentity =
        ReproArtifactIdentity(
            artifactId = "mobilenet-v4-conv-s-int8",
            format = ReproArtifactFormat.TFLITE,
            modelId = "mobilenet-v4-conv-s",
            artifactVersion = "<UNSPECIFIED>",
            availability = ArtifactAvailability.PENDING,
            validationStatus = ArtifactValidationStatus.NOT_APPLICABLE
        )

    private fun litertRuntime(
        platform: SupportedPlatform = SupportedPlatform.ANDROID,
        modelFormat: ReproArtifactFormat = ReproArtifactFormat.TFLITE
    ): ReproRuntimeIdentity =
        ReproRuntimeIdentity(
            runtimeName = ReproRuntimeName.LITERT,
            runtimeVersion = "<UNSPECIFIED>",
            executionBackend = ExecutionBackend.XNNPACK_CPU,
            supportedPlatform = platform,
            modelFormat = modelFormat
        )

    private fun quantization(depth: QuantizationDepth): ReproQuantizationIdentity =
        ReproQuantizationIdentity(
            depth = depth,
            method = QuantizationMethod.UNKNOWN,
            scope = QuantizationScope.UNKNOWN
        )

    @Test
    fun `matching reproduction contract is compatible pending verification`() {
        val report = ReproductionContractChecker.check(
            reference,
            pendingArtifact(),
            litertRuntime(),
            quantization(QuantizationDepth.INT8)
        )
        assertFalse(report.hasErrors)
        assertTrue(report.compatible)
        assertTrue(report.hasUnverified)
        assertEquals(
            "COMPATIBLE_PENDING_VERIFICATION (1 UNVERIFIED)",
            report.summary
        )
    }

    @Test
    fun `unsupported runtime platform is an ERROR`() {
        val report = ReproductionContractChecker.check(
            reference,
            pendingArtifact(),
            litertRuntime(platform = SupportedPlatform.OTHER),
            quantization(QuantizationDepth.INT8)
        )
        assertTrue(report.hasErrors)
        assertFalse(report.compatible)
        assertTrue(
            report.issues.any { it.aspect == "runtimePlatform" && it.severity == IssueSeverity.ERROR }
        )
    }

    @Test
    fun `non-tflite runtime format is an ERROR`() {
        val report = ReproductionContractChecker.check(
            reference,
            pendingArtifact(),
            litertRuntime(modelFormat = ReproArtifactFormat.ONNX),
            quantization(QuantizationDepth.INT8)
        )
        assertTrue(report.hasErrors)
        assertTrue(
            report.issues.any { it.aspect == "runtimeFormat" && it.severity == IssueSeverity.ERROR }
        )
    }

    @Test
    fun `non-int8 quantization depth is an ERROR`() {
        val report = ReproductionContractChecker.check(
            reference,
            pendingArtifact(),
            litertRuntime(),
            ReproQuantizationIdentity(
                depth = QuantizationDepth.NONE,
                method = QuantizationMethod.NONE,
                scope = QuantizationScope.NONE
            )
        )
        assertTrue(report.hasErrors)
        assertTrue(
            report.issues.any { it.aspect == "quantizationDepth" && it.severity == IssueSeverity.ERROR }
        )
    }

    @Test
    fun `int8 config flags an unverified quantization mapping`() {
        val int8Config = reference.copy(
            tensorType = PreprocessingTensorType.INT8,
            scale = 1.0 / 255.0,
            zeroPoint = -128.0
        )
        val report = ReproductionContractChecker.check(
            int8Config,
            pendingArtifact(),
            litertRuntime(),
            quantization(QuantizationDepth.INT8)
        )
        assertTrue(report.hasUnverified)
        assertTrue(
            report.issues.any { it.aspect == "quantizationMapping" }
        )
        assertFalse(report.hasErrors)
    }
}