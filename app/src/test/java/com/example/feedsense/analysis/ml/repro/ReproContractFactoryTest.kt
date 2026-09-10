package com.example.feedsense.analysis.ml.repro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-2.
 *
 * The reproducibility contract factory must:
 *   - derive from the 8B-15-1 catalog (single source of truth)
 *   - preserve SHORTLISTED (A1) vs CONDITIONAL (A2) without
 *     inventing a final selection
 *   - represent the missing artifact honestly (PENDING, no
 *     fabricated checksum, version UNSPECIFIED)
 */
class ReproContractFactoryTest {

    @Test
    fun `contract version is pinned`() {
        assertEquals("8b-15-2-v1", ReproContractFactory.CONTRACT_VERSION)
    }

    @Test
    fun `selection derives from the 8b-15-1 catalog version`() {
        val selection = ReproContractFactory.selection()
        assertEquals("8b-15-1-v1", selection.catalogVersion)
    }

    @Test
    fun `primary candidate is the shortlisted mobilenet-v4-conv-s`() {
        val selection = ReproContractFactory.selection()
        assertEquals("A1-mobilenet-v4-conv-s", selection.primary.candidateId)
        assertEquals(ReproSelectionStatus.SHORTLISTED, selection.primary.selectionStatus)
        assertEquals("mobilenet-v4-conv-s", selection.primary.identity.model.modelId)
        assertEquals(ModelFamily.MOBILENET_V4, selection.primary.identity.model.modelFamily)
        assertEquals("Apache-2.0", selection.primary.identity.model.license)
    }

    @Test
    fun `fallback candidate is the conditional efficientnet-lite`() {
        val selection = ReproContractFactory.selection()
        val fallback = selection.fallback
        assertNotNull(fallback)
        assertEquals("A2-efficientnet-lite", fallback!!.candidateId)
        assertEquals(ReproSelectionStatus.CONDITIONAL, fallback.selectionStatus)
        assertEquals(ModelFamily.EFFICIENTNET_LITE, fallback.identity.model.modelFamily)
        // 8B-15-1 left the packaged-weight license UNKNOWN; honest
        // preservation, never a guess.
        assertEquals("UNKNOWN", fallback.identity.model.license)
    }

    @Test
    fun `artifact is honestly pending with no checksum`() {
        val selection = ReproContractFactory.selection()
        val artifact = selection.primary.identity.artifact
        assertEquals(ArtifactAvailability.PENDING, artifact.availability)
        assertNull(artifact.sha256)
        assertNull(artifact.byteSize)
        assertEquals(ArtifactValidationStatus.NOT_APPLICABLE, artifact.validationStatus)
    }

    @Test
    fun `no fabricated model version is claimed`() {
        val selection = ReproContractFactory.selection()
        assertEquals(REPRO_UNSPECIFIED, selection.primary.identity.model.modelVersion)
        assertEquals(REPRO_UNSPECIFIED, selection.primary.identity.artifact.artifactVersion)
        assertEquals(REPRO_UNSPECIFIED, selection.primary.identity.runtime.runtimeVersion)
    }

    @Test
    fun `runtime identity records litert target`() {
        val selection = ReproContractFactory.selection()
        val runtime = selection.primary.identity.runtime
        assertEquals(ReproRuntimeName.LITERT, runtime.runtimeName)
        assertEquals(ExecutionBackend.XNNPACK_CPU, runtime.executionBackend)
        assertEquals(ReproArtifactFormat.TFLITE, runtime.modelFormat)
        assertEquals(SupportedPlatform.ANDROID, runtime.supportedPlatform)
    }

    @Test
    fun `quantization direction is int8 as selected by 8b-15-1`() {
        val selection = ReproContractFactory.selection()
        assertEquals(QuantizationDepth.INT8, selection.primary.identity.quantization.depth)
    }

    @Test
    fun `preprocessing contract is versioned and otherwise unresolved`() {
        val selection = ReproContractFactory.selection()
        val pp = selection.primary.identity.preprocessing
        assertEquals("8b-15-2-v1", pp.preprocessingVersion)
        assertNull(pp.inputWidth)
        assertNull(pp.inputHeight)
        assertEquals(ResizeMethod.UNKNOWN, pp.resizeMethod)
    }

    @Test
    fun `output mapping contract is versioned with an unresolved label set`() {
        val selection = ReproContractFactory.selection()
        val mapping = selection.primary.identity.outputMapping
        assertEquals("8b-15-2-v1", mapping.outputMappingVersion)
        assertEquals(listOf(REPRO_UNRESOLVED_LABEL), mapping.modelOutputLabels)
        assertEquals(OutputMappingMode.UNKNOWN, mapping.mappingMode)
    }

    @Test
    fun `privacy contract references the 8b-13 pipeline version`() {
        val selection = ReproContractFactory.selection()
        val privacy = selection.primary.identity.privacy
        assertEquals("8b-13-v1", privacy.privacySanitizationVersion)
        assertEquals(PolicyModeRef.RESEARCH, privacy.policyMode)
        assertEquals(EvidenceSourceType.SAFE_FRAME, privacy.evidenceSourceType)
    }

    @Test
    fun `provenance of the primary contract is source-only and honest`() {
        val selection = ReproContractFactory.selection()
        val provenance = selection.primary.identity.provenance
        assertNotNull(provenance)
        assertEquals("mobilenet-v4-conv-s-source", provenance!!.terminalArtifactId)
        assertEquals(1, provenance.steps.size)
        assertEquals(ProvenanceRole.ORIGINAL, provenance.steps[0].role)
        assertNull(provenance.steps[0].conversion)
    }

    @Test
    fun `fallback contract has no provenance chain yet`() {
        val selection = ReproContractFactory.selection()
        assertNull(selection.fallback!!.identity.provenance)
    }

    @Test
    fun `contract factory is deterministic across calls`() {
        val a = ReproContractFactory.selection()
        val b = ReproContractFactory.selection()
        assertEquals(a.primary.identity.canonical, b.primary.identity.canonical)
        assertEquals(a.primary.identity.canonicalHash, b.primary.identity.canonicalHash)
        assertEquals(a.fallback!!.identity.canonicalHash, b.fallback!!.identity.canonicalHash)
    }

    @Test
    fun `primary and fallback are clearly distinct identities`() {
        val selection = ReproContractFactory.selection()
        assertTrue(
            selection.primary.identity.canonicalHash !=
                selection.fallback!!.identity.canonicalHash
        )
    }
}