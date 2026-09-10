package com.example.feedsense.analysis.ml.preprocess

import com.example.feedsense.analysis.ml.repro.ArtifactAvailability
import com.example.feedsense.analysis.ml.repro.ArtifactValidationStatus
import com.example.feedsense.analysis.ml.repro.ExecutionBackend
import com.example.feedsense.analysis.ml.repro.ReproArtifactFormat
import com.example.feedsense.analysis.ml.repro.ReproArtifactIdentity
import com.example.feedsense.analysis.ml.repro.ReproModelIdentity
import com.example.feedsense.analysis.ml.repro.ReproRuntimeIdentity
import com.example.feedsense.analysis.ml.repro.ReproRuntimeName
import com.example.feedsense.analysis.ml.repro.SupportedPlatform
import com.example.feedsense.analysis.ml.ModelFormat
import com.example.feedsense.analysis.ml.repro.ModelFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-3 (§16 versioned config, §17 compatibility,
 * §18 determinism, §21 research reproducibility).
 *
 * The preprocessing configuration must be immutable, versioned and
 * fully reproducible. This file proves: construction-time rejection
 * of incoherent configs, canonical-key stability/sensitivity, the
 * export-safe serialization, and the deterministic research
 * metadata that pairs a config with a model artifact/runtime.
 */
class PreprocessingConfigTest {

    // --------------------------------
    // §16 VERSIONED CONFIG
    // --------------------------------

    @Test
    fun `reference config carries the preprocess-v2 version`() {
        val ref = PreprocessingConfig.mobileNetV4ConvReference()
        assertEquals(PreprocessingVersion.V2, ref.version)
        assertEquals(224, ref.inputWidth)
        assertEquals(224, ref.inputHeight)
        assertEquals(BatchHandling.BATCH_1, ref.batchSize)
    }

    @Test
    fun `reference config exposes every contract field explicitly`() {
        val ref = PreprocessingConfig.mobileNetV4ConvReference()
        assertEquals(ColorFormat.RGB, ref.colorFormat)
        assertEquals(PreprocessChannelOrder.RGB, ref.channelOrder)
        assertEquals(AlphaPolicy.DISCARD, ref.alphaPolicy)
        assertEquals(PreprocessingTensorLayout.NHWC, ref.tensorLayout)
        assertEquals(OrientationPolicy.NORMALIZE_TO_0, ref.orientationPolicy)
        assertEquals(AspectRatioPolicy.CENTER_CROP, ref.cropPolicy)
        assertEquals(Interpolation.BILINEAR, ref.interpolation)
        assertEquals(listOf(0.485, 0.456, 0.406), ref.mean)
        assertEquals(listOf(0.229, 0.224, 0.225), ref.std)
        assertEquals(1.0 / 255.0, ref.scale, 1e-12)
    }

    @Test
    fun `pending config records unknown contract honestly`() {
        val pending = PreprocessingConfig.pendingUnverified("mobilenet-v4-conv-s")
        assertEquals(null, pending.inputWidth)
        assertEquals(null, pending.inputHeight)
        assertEquals(PreprocessChannelOrder.UNKNOWN, pending.channelOrder)
        assertEquals(null, pending.channels)
    }

    // --------------------------------
    // CONFIGURATION VALIDATION (§5, §14, §15)
    // --------------------------------

    @Test
    fun `non-positive input width is rejected at construction`() {
        expectRejected {
            PreprocessingConfig(inputWidth = 0, inputHeight = 224)
        }
    }

    @Test
    fun `half-set dimensions are rejected`() {
        expectRejected {
            PreprocessingConfig(inputWidth = 224, inputHeight = null)
        }
    }

    @Test
    fun `mean and std length mismatch is rejected`() {
        expectRejected {
            PreprocessingConfig(
                inputWidth = 4, inputHeight = 4,
                cropPolicy = AspectRatioPolicy.RESIZE,
                mean = listOf(0.5, 0.5, 0.5),
                std = listOf(0.5, 0.5)
            )
        }
    }

    @Test
    fun `pad crop policy without a padding policy is rejected`() {
        expectRejected {
            PreprocessingConfig(
                inputWidth = 4, inputHeight = 4,
                cropPolicy = AspectRatioPolicy.PAD,
                paddingPolicy = PaddingPolicy.NO_PADDING
            )
        }
    }

    @Test
    fun `discard alpha with rgba channel order is rejected`() {
        expectRejected {
            PreprocessingConfig(
                inputWidth = 4, inputHeight = 4,
                channelOrder = PreprocessChannelOrder.RGBA,
                colorFormat = ColorFormat.RGBA,
                alphaPolicy = AlphaPolicy.DISCARD
            )
        }
    }

    @Test
    fun `nchw layout is rejected by the deterministic pipeline`() {
        expectRejected {
            PreprocessingConfig(
                inputWidth = 4, inputHeight = 4,
                tensorLayout = PreprocessingTensorLayout.NCHW
            )
        }
    }

    @Test
    fun `dynamic batch is rejected by the deterministic pipeline`() {
        expectRejected {
            PreprocessingConfig(
                inputWidth = 4, inputHeight = 4,
                batchSize = BatchHandling.DYNAMIC
            )
        }
    }

    // --------------------------------
    // §16 CANONICAL KEY
    // --------------------------------

    @Test
    fun `identical configs produce identical canonical keys`() {
        assertEquals(
            PreprocessingConfig.mobileNetV4ConvReference().canonicalKey,
            PreprocessingConfig.mobileNetV4ConvReference().canonicalKey
        )
    }

    @Test
    fun `canonical key changes when any contract field changes`() {
        val base = PreprocessingConfig.mobileNetV4ConvReference()
        assertNotEquals(base.canonicalKey, base.copy(version = "preprocess-v3"))
        assertNotEquals(base.canonicalKey, base.copy(inputWidth = 320, inputHeight = 320))
        assertNotEquals(base.canonicalKey, base.copy(scale = 1.0 / 128.0))
        assertNotEquals(base.canonicalKey, base.copy(zeroPoint = 1.0))
        assertNotEquals(base.canonicalKey, base.copy(paddingValue = 0xFFFFFFFF.toInt()))
        assertNotEquals(base.canonicalKey, base.copy(cropAnchor = CropAnchor.TOP))
        assertNotEquals(base.canonicalKey, base.copy(channelOrder = PreprocessChannelOrder.BGR))
    }

    // --------------------------------
    // §16 SERIALIZATION (EXPORT-SAFE)
    // --------------------------------

    @Test
    fun `config serialization is byte stable across equal configs`() {
        assertEquals(
            PreprocessingConfigSerializer.serialize(
                PreprocessingConfig.mobileNetV4ConvReference()
            ),
            PreprocessingConfigSerializer.serialize(
                PreprocessingConfig.mobileNetV4ConvReference()
            )
        )
        assertEquals(
            PreprocessingConfigSerializer.sha256Hex(
                PreprocessingConfig.mobileNetV4ConvReference()
            ),
            PreprocessingConfigSerializer.sha256Hex(
                PreprocessingConfig.mobileNetV4ConvReference()
            )
        )
    }

    @Test
    fun `config serialization covers every contract field`() {
        val ref = PreprocessingConfig.mobileNetV4ConvReference()
        val serialized = PreprocessingConfigSerializer.serialize(ref)
        assertTrue(serialized.contains("version=preprocess-v2"))
        assertTrue(serialized.contains("inputWidth=224"))
        assertTrue(serialized.contains("inputHeight=224"))
        assertTrue(serialized.contains("alphaPolicy=DISCARD"))
        assertTrue(serialized.contains("tensorLayout=NHWC"))
        assertTrue(serialized.contains("batchSize=BATCH_1"))
        assertTrue(serialized.contains("scale=" + (1.0 / 255.0)))
        assertTrue(serialized.contains("cropAnchor=CENTER"))
    }

    @Test
    fun `config serialization differs for each field change`() {
        val base = PreprocessingConfig.mobileNetV4ConvReference()
        assertNotEquals(
            PreprocessingConfigSerializer.sha256Hex(base),
            PreprocessingConfigSerializer.sha256Hex(base.copy(mean = listOf(0.0, 0.0, 0.0)))
        )
        assertNotEquals(
            PreprocessingConfigSerializer.sha256Hex(base),
            PreprocessingConfigSerializer.sha256Hex(base.copy(std = listOf(1.0, 1.0, 1.0)))
        )
        assertNotEquals(
            PreprocessingConfigSerializer.sha256Hex(base),
            PreprocessingConfigSerializer.sha256Hex(base.copy(interpolation = Interpolation.NEAREST))
        )
    }

    // --------------------------------
    // §17/§21 RESEARCH METADATA
    // --------------------------------

    @Test
    fun `preprocessing metadata is deterministic across equal inputs`() {
        val config = PreprocessingConfig.mobileNetV4ConvReference()
        val a = PreprocessingMetadata(config = config)
        val b = PreprocessingMetadata(config = config)
        assertEquals(a.canonical, b.canonical)
        assertEquals(a.canonicalHash, b.canonicalHash)
        assertEquals(a.configHash, b.configHash)
    }

    @Test
    fun `preprocessing metadata changes when the artifact identity changes`() {
        val config = PreprocessingConfig.mobileNetV4ConvReference()
        val artifactA = testArtifact(sha = "a" * 64)
        val artifactB = testArtifact(sha = "b" * 64)
        assertNotEquals(
            PreprocessingMetadata(config, artifact = artifactA).canonicalHash,
            PreprocessingMetadata(config, artifact = artifactB).canonicalHash
        )
    }

    @Test
    fun `preprocessing metadata carries no pixel content`() {
        val metadata = PreprocessingMetadata(
            config = PreprocessingConfig.mobileNetV4ConvReference(),
            artifact = testArtifact(sha = "a" * 64),
            runtime = testRuntime(),
            model = testModel()
        )
        val serialized = metadata.canonical
        val forbidden = listOf(
            "pixels", "ocr", "frameBytes", "content", "raw", "floats"
        )
        for (term in forbidden) {
            assertFalse(
                "metadata must not carry $term",
                serialized.contains(term, ignoreCase = true)
            )
        }
        // Sanity: it does carry identity, not content.
        assertTrue(serialized.contains("artifact="))
        assertTrue(serialized.contains("runtime="))
        assertTrue(serialized.contains("model="))
    }

    // --------------------------------
    // HELPERS
    // --------------------------------

    private fun testArtifact(sha: String): ReproArtifactIdentity =
        ReproArtifactIdentity(
            artifactId = "test-artifact",
            fileName = "test.tflite",
            format = ReproArtifactFormat.TFLITE,
            byteSize = 10,
            sha256 = sha,
            sourceReference = "https://example.com/test",
            modelId = "test-model",
            artifactVersion = "<UNSPECIFIED>",
            availability = ArtifactAvailability.AVAILABLE,
            validationStatus = ArtifactValidationStatus.HASH_MATCHES
        )

    private fun testRuntime(): ReproRuntimeIdentity =
        ReproRuntimeIdentity(
            runtimeName = ReproRuntimeName.LITERT,
            runtimeVersion = "0.0.1",
            executionBackend = ExecutionBackend.XNNPACK_CPU,
            supportedPlatform = SupportedPlatform.ANDROID,
            modelFormat = ReproArtifactFormat.TFLITE
        )

    private fun testModel(): ReproModelIdentity =
        ReproModelIdentity(
            modelId = "test-model",
            modelFamily = ModelFamily.MOBILENET_V4,
            modelArchitecture = "Test architecture",
            modelVersion = "v1",
            sourceName = "Test source",
            sourceLocation = "https://example.com/test",
            license = "Apache-2.0",
            artifactFormat = ModelFormat.TFLITE,
            intendedTask = "IMAGE_CLASSIFICATION"
        )

    private fun expectRejected(block: () -> Unit) {
        try {
            block()
            throw AssertionError("invalid configuration must be rejected")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}

private operator fun String.times(n: Int): String = this.repeat(n)