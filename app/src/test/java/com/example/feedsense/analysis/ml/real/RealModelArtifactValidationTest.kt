package com.example.feedsense.analysis.ml.real

import com.example.feedsense.analysis.ml.OutputSemantics
import com.example.feedsense.analysis.ml.preprocess.PreprocessResult
import com.example.feedsense.analysis.ml.repro.ArtifactAvailability
import com.example.feedsense.analysis.ml.runtime.litert.LabelVerificationStatus
import com.example.feedsense.analysis.ml.runtime.litert.MobileNetV2Artifact
import com.example.feedsense.analysis.ml.runtime.litert.MobileNetV2Labels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// --------------------------------
// REAL MODEL ARTIFACT VALIDATION TEST (8B-15-9, Phase 13)
// --------------------------------
//
// JVM tests for the real model artifact identity and
// infrastructure. These tests do NOT import or instantiate
// the TFLite runtime. They validate the contract, metadata,
// and configuration of the real model artifact.

class RealModelArtifactValidationTest {

    // --------------------------------
    // ARTIFACT IDENTITY
    // --------------------------------

    @Test
    fun `artifact identity has required fields`() {
        val identity = MobileNetV2Artifact.artifactIdentity()

        assertEquals(MobileNetV2Artifact.ARTIFACT_ID, identity.artifactId)
        assertEquals(MobileNetV2Artifact.ARTIFACT_FILE_NAME, identity.fileName)
        assertEquals("TFLITE", identity.format.label)
        assertEquals(MobileNetV2Artifact.MODEL_ID, identity.modelId)
        assertEquals(MobileNetV2Artifact.ARTIFACT_VERSION, identity.artifactVersion)
    }

    @Test
    fun `artifact identity without availability is PENDING`() {
        val identity = MobileNetV2Artifact.artifactIdentity()

        assertEquals(ArtifactAvailability.PENDING, identity.availability)
        assertNull(identity.sha256)
        assertNull(identity.byteSize)
    }

    @Test
    fun `artifact identity with availability requires sha256`() {
        val identity = MobileNetV2Artifact.artifactIdentity(
            sha256 = "a".repeat(64),
            byteSize = 1024L,
            available = true
        )

        assertEquals(ArtifactAvailability.AVAILABLE, identity.availability)
        assertEquals("a".repeat(64), identity.sha256)
        assertEquals(1024L, identity.byteSize)
    }

    @Test
    fun `artifact identity key is deterministic`() {
        val id1 = MobileNetV2Artifact.artifactIdentity()
        val id2 = MobileNetV2Artifact.artifactIdentity()

        assertEquals(id1.key, id2.key)
    }

    @Test
    fun `artifact identity key includes sha256 when available`() {
        val pending = MobileNetV2Artifact.artifactIdentity()
        val available = MobileNetV2Artifact.artifactIdentity(
            sha256 = "a".repeat(64),
            available = true
        )

        assertTrue(pending.key.contains("unavailable"))
        assertTrue(available.key.contains("a".repeat(64)))
    }

    // --------------------------------
    // MODEL METADATA
    // --------------------------------

    @Test
    fun `model metadata has required fields`() {
        val metadata = MobileNetV2Artifact.modelMetadata()

        assertEquals(MobileNetV2Artifact.MODEL_ID, metadata.modelId)
        assertEquals(MobileNetV2Artifact.MODEL_VERSION, metadata.modelVersion)
        assertEquals("ON_DEVICE", metadata.runtime.label)
        assertEquals("TFLITE", metadata.format.label)
        assertEquals("IMAGE_CLASSIFICATION", metadata.taskType.label)
    }

    @Test
    fun `model metadata identity key is deterministic`() {
        val m1 = MobileNetV2Artifact.modelMetadata()
        val m2 = MobileNetV2Artifact.modelMetadata()

        assertEquals(m1.identityKey, m2.identityKey)
    }

    @Test
    fun `model metadata input spec matches artifact`() {
        val metadata = MobileNetV2Artifact.modelMetadata()

        assertEquals(MobileNetV2Artifact.INPUT_WIDTH, metadata.inputSpec.width)
        assertEquals(MobileNetV2Artifact.INPUT_HEIGHT, metadata.inputSpec.height)
        assertEquals(MobileNetV2Artifact.INPUT_CHANNELS, metadata.inputSpec.channels)
    }

    @Test
    fun `model metadata checksum is null when not provided`() {
        val metadata = MobileNetV2Artifact.modelMetadata()

        assertNull(metadata.checksum)
    }

    // --------------------------------
    // INPUT TENSOR SPECIFICATION
    // --------------------------------

    @Test
    fun `input dimensions are 224x224x3`() {
        assertEquals(224, MobileNetV2Artifact.INPUT_WIDTH)
        assertEquals(224, MobileNetV2Artifact.INPUT_HEIGHT)
        assertEquals(3, MobileNetV2Artifact.INPUT_CHANNELS)
    }

    @Test
    fun `input element count matches dimensions`() {
        val expected = 1 * 224 * 224 * 3
        assertEquals(expected, MobileNetV2Artifact.INPUT_ELEMENT_COUNT)
        assertEquals(150528, MobileNetV2Artifact.INPUT_ELEMENT_COUNT)
    }

    @Test
    fun `input scale is one-over-255`() {
        assertEquals(1.0 / 255.0, MobileNetV2Artifact.INPUT_SCALE, 1e-10)
    }

    @Test
    fun `input layout is NHWC`() {
        assertEquals("NHWC", MobileNetV2Artifact.INPUT_LAYOUT)
    }

    @Test
    fun `input datatype is FLOAT32`() {
        assertEquals("FLOAT32", MobileNetV2Artifact.INPUT_DATATYPE)
    }

    // --------------------------------
    // OUTPUT TENSOR SPECIFICATION
    // --------------------------------

    @Test
    fun `output class count is 1001`() {
        assertEquals(1001, MobileNetV2Artifact.OUTPUT_CLASS_COUNT)
    }

    @Test
    fun `output datatype is FLOAT32`() {
        assertEquals("FLOAT32", MobileNetV2Artifact.OUTPUT_DATATYPE)
    }

    @Test
    fun `output is not softmax by default`() {
        assertFalse(MobileNetV2Artifact.OUTPUT_IS_SOFTMAX)
    }

    // --------------------------------
    // LABEL MAPPING
    // --------------------------------

    @Test
    fun `label count matches output class count`() {
        assertEquals(
            MobileNetV2Artifact.OUTPUT_CLASS_COUNT,
            MobileNetV2Labels.LABEL_COUNT
        )
    }

    @Test
    fun `representative test labels are non-empty`() {
        assertTrue(MobileNetV2Labels.REPRESENTATIVE_TEST_LABELS.isNotEmpty())
    }

    @Test
    fun `representative test labels are unique`() {
        val labels = MobileNetV2Labels.REPRESENTATIVE_TEST_LABELS
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun `first label is background`() {
        assertEquals("background", MobileNetV2Labels.FIRST_LABEL)
        assertEquals("background", MobileNetV2Labels.REPRESENTATIVE_TEST_LABELS[0])
    }

    @Test
    fun `label verification starts as DOCUMENTED_BY_SOURCE`() {
        assertEquals(
            LabelVerificationStatus.DOCUMENTED_BY_SOURCE,
            MobileNetV2Labels.verificationStatus
        )
    }

    @Test
    fun `label list verification catches wrong count`() {
        val tooFew = listOf("background", "tench")
        assertFalse(MobileNetV2Labels.verifyLabelList(tooFew))
    }

    @Test
    fun `label list verification catches wrong first label`() {
        val wrong = listOf("tench") + List(1000) { "class_$it" }
        assertFalse(MobileNetV2Labels.verifyLabelList(wrong))
    }

    // --------------------------------
    // PREPROCESSING CONFIGURATION
    // --------------------------------

    @Test
    fun `preprocessing config has correct dimensions`() {
        val config = RealModelPreprocessing.config()

        assertEquals(224, config.inputWidth)
        assertEquals(224, config.inputHeight)
    }

    @Test
    fun `preprocessing config is RGB`() {
        val config = RealModelPreprocessing.config()

        assertEquals("RGB", config.channelOrder.label)
    }

    @Test
    fun `preprocessing config is FLOAT32`() {
        val config = RealModelPreprocessing.config()

        assertEquals("FLOAT32", config.tensorType.label)
    }

    @Test
    fun `preprocessing config is NHWC`() {
        val config = RealModelPreprocessing.config()

        assertEquals("NHWC", config.tensorLayout.label)
    }

    @Test
    fun `preprocessing config scale matches artifact`() {
        val config = RealModelPreprocessing.config()

        assertEquals(MobileNetV2Artifact.INPUT_SCALE, config.scale, 1e-10)
    }

    @Test
    fun `preprocessing config has V2 version`() {
        val config = RealModelPreprocessing.config()

        assertEquals("preprocess-v2", config.version)
    }

    @Test
    fun `preprocessing config canonical key is deterministic`() {
        val config1 = RealModelPreprocessing.config()
        val config2 = RealModelPreprocessing.config()

        assertEquals(config1.canonicalKey, config2.canonicalKey)
    }

    @Test
    fun `preprocessing verification starts as DOCUMENTED_BY_SOURCE`() {
        assertEquals(
            PreprocessingVerificationStatus.DOCUMENTED_BY_SOURCE,
            RealModelPreprocessing.verificationStatus
        )
    }

    // --------------------------------
    // PIPELINE CONFIGURATION
    // --------------------------------

    @Test
    fun `pipeline config has correct model identity`() {
        val config = RealModelPipelineFactory.create()

        assertEquals(MobileNetV2Artifact.MODEL_ID, config.modelId)
        assertEquals(MobileNetV2Artifact.MODEL_VERSION, config.modelVersion)
    }

    @Test
    fun `pipeline config uses SOFTMAX semantics`() {
        val config = RealModelPipelineFactory.create()

        assertEquals(
            OutputSemantics.SOFTMAX,
            config.interpretationConfig.outputSemantics
        )
    }

    @Test
    fun `pipeline config labels are non-empty`() {
        val config = RealModelPipelineFactory.create()

        assertTrue(config.interpretationConfig.labels.isNotEmpty())
    }

    @Test
    fun `pipeline config taxonomy version is non-blank`() {
        val config = RealModelPipelineFactory.create()

        assertTrue(config.taxonomyVersion.isNotBlank())
    }

    @Test
    fun `pipeline config mapping table version is non-blank`() {
        val config = RealModelPipelineFactory.create()

        assertTrue(config.mappingTableVersion.isNotBlank())
    }

    @Test
    fun `pipeline config has taxonomy mappings`() {
        val config = RealModelPipelineFactory.create()

        assertTrue(config.taxonomyMappings.isNotEmpty())
    }

    @Test
    fun `pipeline config is deterministic`() {
        val c1 = RealModelPipelineFactory.create()
        val c2 = RealModelPipelineFactory.create()

        assertEquals(c1.modelId, c2.modelId)
        assertEquals(c1.taxonomyVersion, c2.taxonomyVersion)
        assertEquals(c1.mappingTableVersion, c2.mappingTableVersion)
        assertEquals(
            c1.interpretationConfig.labels.size,
            c2.interpretationConfig.labels.size
        )
    }

    // --------------------------------
    // GOLDEN FIXTURES
    // --------------------------------

    @Test
    fun `golden fixtures are defined`() {
        assertTrue(RealModelGoldenFixtures.ALL_FIXTURES.isNotEmpty())
    }

    @Test
    fun `golden fixture creates valid frame`() {
        val fixture = RealModelGoldenFixtures.CANONICAL_224_KNOWN_GRADIENT
        val frame = RealModelGoldenFixtures.createFrame(fixture)

        assertEquals(224, frame.width)
        assertEquals(224, frame.height)
        assertEquals(224 * 224, frame.pixels.size)
    }

    @Test
    fun `golden fixture creates valid evidence`() {
        val fixture = RealModelGoldenFixtures.CANONICAL_224_KNOWN_GRADIENT
        val evidence = RealModelGoldenFixtures.createEvidence(fixture)

        assertEquals(fixture.fixtureId, evidence.evidenceId)
        assertTrue(evidence.privacy.isSafeForResearchUse)
    }

    @Test
    fun `golden fixture preprocessing succeeds`() {
        val fixture = RealModelGoldenFixtures.CANONICAL_224_KNOWN_GRADIENT
        val result = RealModelGoldenFixtures.preprocessFixture(fixture)

        assertTrue("preprocessing should succeed: $result",
            result is PreprocessResult.Success)

        val output = (result as PreprocessResult.Success).output
        assertEquals(224, output.input.width)
        assertEquals(224, output.input.height)
        assertEquals(3, output.input.channels)
        assertEquals(150528, output.tensorSize)
    }

    @Test
    fun `golden fixture preprocessing is deterministic`() {
        val fixture = RealModelGoldenFixtures.CANONICAL_224_KNOWN_GRADIENT
        val r1 = RealModelGoldenFixtures.preprocessFixture(fixture)
        val r2 = RealModelGoldenFixtures.preprocessFixture(fixture)

        assertTrue(r1 is PreprocessResult.Success)
        assertTrue(r2 is PreprocessResult.Success)

        val o1 = (r1 as PreprocessResult.Success).output
        val o2 = (r2 as PreprocessResult.Success).output

        assertEquals(o1.fingerprint, o2.fingerprint)
        assertTrue(o1.input.contentEquals(o2.input))
    }

    @Test
    fun `golden fixture 48x48 creates valid scaled input`() {
        val fixture = RealModelGoldenFixtures.SCALED_DOWN_48_KNOWN_GRADIENT
        val result = RealModelGoldenFixtures.preprocessFixture(fixture)

        assertTrue(result is PreprocessResult.Success)
        val output = (result as PreprocessResult.Success).output

        assertEquals(224, output.input.width)
        assertEquals(224, output.input.height)
        assertEquals(3, output.input.channels)
    }

    // --------------------------------
    // REPRODUCIBILITY
    // --------------------------------

    @Test
    fun `all artifact source references are documented`() {
        assertNotNull(MobileNetV2Artifact.SOURCE_URL)
        assertTrue(MobileNetV2Artifact.SOURCE_URL.startsWith("http"))
        assertNotNull(MobileNetV2Artifact.SOURCE_REFERENCE)
    }

    @Test
    fun `artifact license is documented`() {
        assertEquals("Apache-2.0", MobileNetV2Artifact.MODEL_LICENSE)
    }

    @Test
    fun `runtime metadata is documented`() {
        assertEquals("LITERT", MobileNetV2Artifact.RUNTIME_NAME)
        assertEquals("2.16.1", MobileNetV2Artifact.RUNTIME_VERSION)
        assertEquals("XNNPACK_CPU", MobileNetV2Artifact.EXECUTION_BACKEND)
        assertEquals("ANDROID", MobileNetV2Artifact.SUPPORTED_PLATFORM)
    }

    @Test
    fun `golden fixture input verification is documented`() {
        val fixture = RealModelGoldenFixtures.CANONICAL_224_KNOWN_GRADIENT
        assertEquals(
            FixtureVerificationStatus.VERIFIED,
            fixture.inputVerification
        )
    }

    @Test
    fun `golden fixture output verification is NOT_MEASURED`() {
        val fixture = RealModelGoldenFixtures.CANONICAL_224_KNOWN_GRADIENT
        assertEquals(
            FixtureVerificationStatus.NOT_MEASURED,
            fixture.outputVerification
        )
    }
}
