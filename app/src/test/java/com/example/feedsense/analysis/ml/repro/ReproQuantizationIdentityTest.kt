package com.example.feedsense.analysis.ml.repro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/*
 * Milestone 8B-15-2.
 *
 * QUANTIZATION identity: explicit depth/method/scope/tool. Same
 * configuration -> equal; changed depth/tool -> different. Does
 * not claim accuracy or quality.
 */
class ReproQuantizationIdentityTest {

    @Test
    fun `same quantization produces equal identity`() {
        assertEquals(ReproFix.quantization, ReproFix.quantization.copy())
    }

    @Test
    fun `changed depth produces different identity`() {
        assertNotEquals(
            ReproFix.quantization,
            ReproFix.quantization.copy(depth = QuantizationDepth.FP16)
        )
    }

    @Test
    fun `changed tool version produces different identity`() {
        val withTool = ReproFix.quantization.copy(
            toolName = "tf-converter",
            toolVersion = "2.20"
        )
        assertNotEquals(ReproFix.quantization, withTool)
    }

    @Test
    fun `none depth cannot carry a non-none method`() {
        try {
            ReproFix.quantization.copy(
                depth = QuantizationDepth.NONE,
                method = QuantizationMethod.POST_TRAINING_INTEGER
            )
            throw AssertionError("NONE depth with a method must be rejected")
        } catch (expected: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun `same quantization produces identical canonical serialization`() {
        assertEquals(
            ReproCanonicalSerializer.serializeQuantization(ReproFix.quantization),
            ReproCanonicalSerializer.serializeQuantization(ReproFix.quantization.copy())
        )
    }
}