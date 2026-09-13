package com.example.feedsense.analysis.ml.repro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/*
 * Milestone 8B-15-2.
 *
 * PREPROCESSING identity: versioned contract. Same metadata ->
 * equal; changed version/configuration -> different. Unset input
 * dimensions are an explicit "unverified", not a fabricated size.
 */
class ReproPreprocessingIdentityTest {

    @Test
    fun `same preprocessing produces equal identity`() {
        assertEquals(ReproFix.preprocessing, ReproFix.preprocessing.copy())
    }

    @Test
    fun `changed preprocessing version produces different identity`() {
        assertNotEquals(
            ReproFix.preprocessing,
            ReproFix.preprocessing.copy(preprocessingVersion = "pp-v2")
        )
    }

    @Test
    fun `changed input size produces different identity`() {
        assertNotEquals(
            ReproFix.preprocessing,
            ReproFix.preprocessing.copy(inputWidth = 320, inputHeight = 320)
        )
    }

    @Test
    fun `changed channel order produces different identity`() {
        assertNotEquals(
            ReproFix.preprocessing,
            ReproFix.preprocessing.copy(channelOrder = ChannelOrder.BGR)
        )
    }

    @Test
    fun `changed normalization produces different identity`() {
        assertNotEquals(
            ReproFix.preprocessing,
            ReproFix.preprocessing.copy(
                normalization = NormalizationParams(
                    mean = listOf(0.0, 0.0, 0.0),
                    std = listOf(1.0, 1.0, 1.0)
                )
            )
        )
    }

    @Test
    fun `same preprocessing produces identical canonical serialization`() {
        assertEquals(
            ReproCanonicalSerializer.serializePreprocessing(ReproFix.preprocessing),
            ReproCanonicalSerializer.serializePreprocessing(ReproFix.preprocessing.copy())
        )
    }

    @Test
    fun `unset input dimensions are explicitly null`() {
        val unverified = ReproPreprocessingIdentity(
            preprocessingVersion = "pp-v0",
            resizeMethod = ResizeMethod.UNKNOWN,
            inputWidth = null,
            inputHeight = null,
            aspectRatioBehavior = AspectRatioBehavior.UNKNOWN,
            colorFormat = null,
            channelOrder = ChannelOrder.UNKNOWN,
            normalization = NormalizationParams(),
            alphaHandling = null,
            orientationHandling = null
        )
        assertEquals(null, unverified.inputWidth)
        assertEquals(null, unverified.inputHeight)
        assertEquals(ResizeMethod.UNKNOWN, unverified.resizeMethod)
    }

    @Test
    fun `blank version is rejected`() {
        try {
            ReproFix.preprocessing.copy(preprocessingVersion = "")
            throw AssertionError("blank preprocessing version must be rejected")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `mismatched width and height are rejected`() {
        try {
            ReproFix.preprocessing.copy(inputWidth = null, inputHeight = 224)
            throw AssertionError("half-set dimensions must be rejected")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `non-positive input size is rejected`() {
        try {
            ReproFix.preprocessing.copy(inputWidth = -1, inputHeight = 224)
            throw AssertionError("negative input size must be rejected")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }
}