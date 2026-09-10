package com.example.feedsense.analysis.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-14.
 *
 * Output validation guards the boundary between raw model
 * output and evaluation (sections 42-44).
 */
class ModelOutputValidatorTest {

    private val spec = MlTestFixtures.OUTPUT_SPEC
    private val validator = ModelOutputValidator(spec)

    @Test
    fun `valid output passes and orders deterministically`() {
        val result = validator.validate(
            listOf(
                RankedPrediction("meme", 0.04),
                RankedPrediction("entertainment", 0.11),
                RankedPrediction("sports", 0.82)
            )
        )
        assertTrue(result.valid)
        assertEquals(InferenceStatus.SUCCESS, result.status)
        assertEquals(listOf("sports", "entertainment", "meme"),
            result.ranked.map { it.category })
        assertEquals(0.82, result.ranked[0].confidence, 1e-9)
    }

    @Test
    fun `ties are broken deterministically by category name`() {
        val result = validator.validate(
            listOf(
                RankedPrediction("sports", 0.50),
                RankedPrediction("advertisement", 0.50)
            )
        )
        assertTrue(result.valid)
        // equal confidence -> alphabetically smaller category first
        assertEquals("advertisement", result.ranked[0].category)
        assertEquals("sports", result.ranked[1].category)
    }

    @Test
    fun `topK is capped to the spec maximum`() {
        val result = validator.validate(
            listOf(
                RankedPrediction("sports", 0.8),
                RankedPrediction("entertainment", 0.1),
                RankedPrediction("meme", 0.05),
                RankedPrediction("advertisement", 0.02)
            )
        )
        assertTrue(result.valid)
        assertEquals(3, result.ranked.size)
    }

    @Test
    fun `nan confidence is rejected`() {
        val result = validator.validate(
            listOf(RankedPrediction("sports", Double.NaN))
        )
        assertFalse(result.valid)
        assertEquals(InferenceStatus.OUTPUT_INVALID, result.status)
    }

    @Test
    fun `infinite confidence is rejected`() {
        val result = validator.validate(
            listOf(RankedPrediction("sports", Double.POSITIVE_INFINITY))
        )
        assertFalse(result.valid)
        assertEquals(InferenceStatus.OUTPUT_INVALID, result.status)
    }

    @Test
    fun `negative confidence is rejected`() {
        val result = validator.validate(
            listOf(RankedPrediction("sports", -0.1))
        )
        assertFalse(result.valid)
        assertEquals(InferenceStatus.OUTPUT_INVALID, result.status)
    }

    @Test
    fun `confidence above one is rejected`() {
        val result = validator.validate(
            listOf(RankedPrediction("sports", 1.01))
        )
        assertFalse(result.valid)
        assertEquals(InferenceStatus.OUTPUT_INVALID, result.status)
    }

    @Test
    fun `unknown category is rejected`() {
        val result = validator.validate(
            listOf(RankedPrediction("not-a-category", 0.5))
        )
        assertFalse(result.valid)
        assertEquals(InferenceStatus.OUTPUT_INVALID, result.status)
    }

    @Test
    fun `duplicate categories are removed deterministically keeping max`() {
        val result = validator.validate(
            listOf(
                RankedPrediction("sports", 0.3),
                RankedPrediction("sports", 0.9),
                RankedPrediction("meme", 0.2)
            )
        )
        assertTrue(result.valid)
        assertEquals(listOf("sports", "meme"),
            result.ranked.map { it.category })
        assertEquals(0.9, result.ranked[0].confidence, 1e-9)
    }

    @Test
    fun `empty output is malformed`() {
        val result = validator.validate(emptyList())
        assertFalse(result.valid)
        assertEquals(InferenceStatus.OUTPUT_INVALID, result.status)
        assertNull(result.message?.takeIf { it.isBlank() })
    }

    @Test
    fun `same input yields identical validation twice`() {
        val candidates = listOf(
            RankedPrediction("meme", 0.04),
            RankedPrediction("sports", 0.82),
            RankedPrediction("entertainment", 0.11)
        )
        val first = validator.validate(candidates)
        val second = validator.validate(candidates)
        assertEquals(first.ranked, second.ranked)
        assertEquals(first.status, second.status)
        assertTrue(first.valid && second.valid)
    }
}