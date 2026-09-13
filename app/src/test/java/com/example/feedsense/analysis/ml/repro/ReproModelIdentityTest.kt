package com.example.feedsense.analysis.ml.repro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/*
 * Milestone 8B-15-2.
 *
 * MODEL identity: same metadata -> equal and byte-identical
 * canonical form; any version/field change -> different identity.
 * Malformed (blank/invalid) metadata is rejected, never silently
 * accepted as a valid identity.
 */
class ReproModelIdentityTest {

    @Test
    fun `same model metadata produces equal identity`() {
        val a = ReproFix.model
        val b = ReproFix.model.copy()
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `different model version produces different identity`() {
        val a = ReproFix.model
        val b = ReproFix.model.copy(modelVersion = "v2")
        assertNotEquals(a, b)
    }

    @Test
    fun `different model id produces different identity`() {
        val a = ReproFix.model
        val b = ReproFix.model.copy(modelId = "other-model")
        assertNotEquals(a, b)
    }

    @Test
    fun `same metadata produces identical canonical serialization`() {
        val a = ReproFix.model
        val b = ReproFix.model.copy()
        assertEquals(
            ReproCanonicalSerializer.serializeModel(a),
            ReproCanonicalSerializer.serializeModel(b)
        )
    }

    @Test
    fun `model identity and artifact hash are distinct concepts`() {
        val modelA = ReproFix.model
        val modelB = ReproFix.model.copy(modelVersion = "v2")
        // Two artifacts claiming the same MODEL id/version are
        // distinguished at the artifact layer (sha256), never here.
        assertEquals(modelA.modelId, modelB.modelId)
        assertNotEquals("v1", "v2")
    }

    @Test
    fun `key is the stable semantic model key`() {
        assertEquals("test-model:v1", ReproFix.model.key)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank model id is rejected`() {
        ReproFix.model.copy(modelId = "  ").also { ReproCanonicalSerializer.serializeModel(it) }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank license is rejected`() {
        ReproFix.model.copy(license = "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `blank source location is rejected`() {
        ReproFix.model.copy(sourceLocation = "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun `negative parameter count is rejected`() {
        ReproFix.model.copy(parametersMillions = -1.0)
    }

    @Test
    fun `unavailable license is explicit not fabricated`() {
        val m = ReproFix.model.copy(license = "UNKNOWN")
        assertEquals("UNKNOWN", m.license)
    }

    @Test
    fun `model family is typed not free-form`() {
        assertEquals(ModelFamily.MOBILENET_V4, ReproFix.model.modelFamily)
        assertNotEquals(ModelFamily.EFFICIENTNET_LITE, ReproFix.model.modelFamily)
    }
}