package com.example.feedsense.analysis.ml.repro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/*
 * Milestone 8B-15-2.
 *
 * COMPOSITE identity + canonical serialization.
 *
 *   - same complete configuration -> byte-identical canonical
 *     serialization and identical canonical hash
 *   - changed artifact / runtime / preprocessing / mapping ->
 *     different composite identity
 *   - determinism: repeated serialization is byte-identical
 *   - canonical serialization carries stable field ordering,
 *     stable enum labels, stable numeric formatting
 */
class ReproCanonicalSerializerTest {

    @Test
    fun `same complete configuration produces identical canonical serialization`() {
        val a = ReproFix.composite()
        val b = ReproFix.composite()
        assertEquals(a.canonical, b.canonical)
        assertEquals(a.canonicalHash, b.canonicalHash)
    }

    @Test
    fun `deterministic serialization across many runs`() {
        val composite = ReproFix.composite()
        val expected = composite.canonical
        repeat(20) {
            assertEquals(expected, composite.canonical)
            assertEquals(expected, ReproFix.composite().canonical)
        }
    }

    @Test
    fun `changed artifact produces different composite identity`() {
        val a = ReproFix.composite()
        val b = ReproFix.composite().copy(
            artifact = a.artifact.copy(
                sha256 = "a" + a.artifact.sha256!!.substring(1)
            )
        )
        assertNotEquals(a.canonicalHash, b.canonicalHash)
    }

    @Test
    fun `changed runtime produces different composite identity`() {
        val a = ReproFix.composite()
        val b = ReproFix.composite().copy(
            runtime = a.runtime.copy(runtimeVersion = "0.0.9")
        )
        assertNotEquals(a.canonicalHash, b.canonicalHash)
    }

    @Test
    fun `changed preprocessing produces different composite identity`() {
        val a = ReproFix.composite()
        val b = ReproFix.composite().copy(
            preprocessing = a.preprocessing.copy(preprocessingVersion = "pp-v9")
        )
        assertNotEquals(a.canonicalHash, b.canonicalHash)
    }

    @Test
    fun `changed output mapping produces different composite identity`() {
        val a = ReproFix.composite()
        val b = ReproFix.composite().copy(
            outputMapping = a.outputMapping.copy(outputMappingVersion = "map-v9")
        )
        assertNotEquals(a.canonicalHash, b.canonicalHash)
    }

    @Test
    fun `changed model version produces different composite identity`() {
        val a = ReproFix.composite()
        val b = ReproFix.composite().copy(
            model = a.model.copy(modelVersion = "v9")
        )
        assertNotEquals(a.canonicalHash, b.canonicalHash)
    }

    @Test
    fun `changed privacy version produces different composite identity`() {
        val a = ReproFix.composite()
        val b = ReproFix.composite().copy(
            privacy = a.privacy.copy(privacySanitizationVersion = "privacy-v9")
        )
        assertNotEquals(a.canonicalHash, b.canonicalHash)
    }

    @Test
    fun `canonical hash is a sha-256 of the canonical representation`() {
        val composite = ReproFix.composite()
        assertEquals(64, composite.canonicalHash.length)
        assertEquals(
            ReproCanonicalSerializer.sha256Hex(composite.canonical),
            composite.canonicalHash
        )
    }

    @Test
    fun `canonical serialization uses stable enum labels`() {
        val composite = ReproFix.composite()
        assert(composite.canonical.contains("runtimeName=LITERT"))
        assert(composite.canonical.contains("executionBackend=XNNPACK_CPU"))
        assert(composite.canonical.contains("depth=INT8"))
        assert(composite.canonical.contains("availability=AVAILABLE"))
    }

    @Test
    fun `canonical serialization uses stable numeric formatting`() {
        val composite = ReproFix.composite()
        assert(composite.canonical.contains("parametersMillions=3.8"))
        assert(composite.canonical.contains("byteSize=100"))
        assert(composite.canonical.contains("inputWidth=224"))
        assert(composite.canonical.contains("inputHeight=224"))
    }

    @Test
    fun `canonical serialization is locale-independent for doubles`() {
        val p = ReproFix.preprocessing.copy(
            normalization = NormalizationParams(
                mean = listOf(0.5, 0.25, 1.0e-2),
                std = listOf(0.5, 0.25, 1.0e-2)
            )
        )
        val rendered = ReproCanonicalSerializer.serializePreprocessing(p)
        assert(rendered.contains("0.5"))
        assert(rendered.contains("0.25"))
        // toString() of 1.0e-2 is "0.01" in Kotlin, never
        // locale-grouped.
        assert(rendered.contains("0.01"))
        assert(!rendered.contains("0,01"))
    }

    @Test
    fun `unset optionals serialize as explicit null`() {
        // The 8B-15-1 contract artifact is PENDING: its sha256,
        // byteSize, fileName and acquisitionTimestamp are absent.
        // The canonical form must render them as explicit null
        // markers - never "", "0", or a fabricated value.
        val pendingArtifact = ReproArtifactIdentity(
            artifactId = "pending",
            format = ReproArtifactFormat.TFLITE,
            modelId = "model",
            artifactVersion = REPRO_UNSPECIFIED,
            availability = ArtifactAvailability.PENDING,
            validationStatus = ArtifactValidationStatus.NOT_APPLICABLE
        )
        val rendered = ReproCanonicalSerializer.serializeArtifact(pendingArtifact)
        assert(rendered.contains("sha256=null"))
        assert(rendered.contains("byteSize=null"))
        assert(rendered.contains("fileName=null"))
        assert(!rendered.contains("sha256=\""))
    }

    @Test
    fun `null provenance serializes explicitly`() {
        val composite = ReproFix.composite().copy(provenance = null)
        assertEquals("provenance=null", ReproCanonicalSerializer.serializeProvenance(null))
        assert(composite.canonical.contains("provenance=null"))
    }

    @Test
    fun `model identity changes propagate to canonical form`() {
        val a = ReproFix.model
        val b = ReproFix.model.copy(modelFamily = ModelFamily.EFFICIENTNET_LITE)
        assertNotEquals(
            ReproCanonicalSerializer.serializeModel(a),
            ReproCanonicalSerializer.serializeModel(b)
        )
    }

    @Test
    fun `rendered output avoids toString-name fragility`() {
        val canonical = ReproFix.composite().canonical
        assert(!canonical.contains("data class"))
    }

    @Test
    fun `factory derived identities differ between primary and fallback`() {
        val selection = ReproContractFactory.selection()
        val primary = selection.primary.identity
        val fallback = selection.fallback!!.identity
        assertNotEquals(primary.canonicalHash, fallback.canonicalHash)
    }
}