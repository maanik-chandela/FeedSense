package com.example.feedsense.analysis.ml.repro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-2.
 *
 * ARTIFACT identity + SHA-256:
 *   - same bytes -> same hash
 *   - changed bytes -> changed hash
 *   - missing artifact -> explicit PENDING/NOT_AVAILABLE state,
 *     never a fabricated checksum.
 */
class ReproArtifactIdentityTest {

    @Test
    fun `same bytes produce same sha256`() {
        val bytes = "feed sense artifact".toByteArray()
        assertEquals(ArtifactSha256.hash(bytes), ArtifactSha256.hash(bytes))
        assertTrue(ArtifactSha256.verify(bytes, ArtifactSha256.hash(bytes)))
    }

    @Test
    fun `changed bytes produce changed sha256`() {
        val original = "feed sense artifact".toByteArray()
        val changed = "feed sense artifacT".toByteArray()
        assertNotEquals(ArtifactSha256.hash(original), ArtifactSha256.hash(changed))
        assertFalse(ArtifactSha256.verify(changed, ArtifactSha256.hash(original)))
    }

    @Test
    fun `sha256 of empty bytes matches the published empty-string digest`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            ArtifactSha256.hash(ByteArray(0))
        )
    }

    @Test
    fun `sha256 output format is lowercase hex`() {
        val hash = ArtifactSha256.hash(byteArrayOf(1, 2, 3))
        assertTrue(hash.length == 64)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `changed single byte changes sha256`() {
        val a = byteArrayOf(64, 65, 66, 67, 68)
        val b = a.copyOf()
        b[0] = 63
        assertNotEquals(ArtifactSha256.hash(a), ArtifactSha256.hash(b))
    }

    @Test
    fun `available artifact requires a checksum`() {
        try {
            ReproFix.artifact.copy(sha256 = null)
            throw AssertionError("AVAILABLE artifact without checksum must be rejected")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `checksum may only be present when artifact is available`() {
        try {
            ReproFix.artifact.copy(
                availability = ArtifactAvailability.PENDING,
                validationStatus = ArtifactValidationStatus.NOT_APPLICABLE
            )
            throw AssertionError("PENDING artifact carrying a sha256 must be rejected")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `missing artifact is explicitly pending and has no checksum`() {
        val pending = ReproArtifactIdentity(
            artifactId = "future-artifact",
            format = ReproArtifactFormat.TFLITE,
            modelId = "future-model",
            artifactVersion = REPRO_UNSPECIFIED,
            availability = ArtifactAvailability.PENDING,
            validationStatus = ArtifactValidationStatus.NOT_APPLICABLE
        )
        assertEquals(ArtifactAvailability.PENDING, pending.availability)
        assertNull(pending.sha256)
        assertNull(pending.byteSize)
    }

    @Test
    fun `missing artifact is explicitly not available`() {
        val na = ReproArtifactIdentity(
            artifactId = "none",
            format = ReproArtifactFormat.UNKNOWN,
            modelId = "none-model",
            artifactVersion = REPRO_UNSPECIFIED,
            availability = ArtifactAvailability.NOT_AVAILABLE,
            validationStatus = ArtifactValidationStatus.NOT_APPLICABLE
        )
        assertEquals(ArtifactAvailability.NOT_AVAILABLE, na.availability)
        assertNull(na.sha256)
    }

    @Test
    fun `same artifact identity compares equal`() {
        assertEquals(ReproFix.artifact, ReproFix.artifact.copy())
    }

    @Test
    fun `different artifact checksum is a different identity`() {
        val other = ReproFix.artifact.copy(
            sha256 = "a" + ReproFix.artifact.sha256!!.substring(1)
        )
        assertNotEquals(ReproFix.artifact, other)
    }

    @Test
    fun `sha256 hex validator accepts only 64 lowercase hex chars`() {
        assertTrue(ReproArtifactIdentity.isSha256Hex("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"))
        assertFalse(ReproArtifactIdentity.isSha256Hex("short"))
        assertFalse(ReproArtifactIdentity.isSha256Hex("E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855"))
    }

    @Test
    fun `malformed checksum is rejected`() {
        try {
            ReproFix.artifact.copy(sha256 = "not-a-real-hash")
            throw AssertionError("malformed checksum must be rejected")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}