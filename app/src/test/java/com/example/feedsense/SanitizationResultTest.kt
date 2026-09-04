package com.example.feedsense

import com.example.feedsense.analysis.privacy.SanitizationResult
import com.example.feedsense.analysis.privacy.SanitizationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-4.
 *
 * SanitizationResult: states, safety checks,
 * and metadata.
 */
class SanitizationResultTest {

    // --------------------------------
    // SANITIZED STATUS
    // --------------------------------

    @Test
    fun `SANITIZED result is safe for downstream`() {

        val result = SanitizationResult(
            status = SanitizationStatus.SANITIZED,
            regionsProcessed = listOf("STATUS_BAR"),
            regionCount = 1,
            sanitizationVersion = "privacy-v1",
            transformedFramePath = "/tmp/sanitized.png"
        )

        assertTrue(result.isSafeForDownstream)
        assertTrue(result.wasTransformed)
    }

    @Test
    fun `SANITIZED result has correct fields`() {

        val result = SanitizationResult(
            status = SanitizationStatus.SANITIZED,
            regionsProcessed =
                listOf("STATUS_BAR", "NAVIGATION"),
            regionCount = 2,
            sanitizationVersion = "privacy-v1",
            transformedFramePath = "/tmp/out.png"
        )

        assertEquals(2, result.regionCount)
        assertEquals(2, result.regionsProcessed.size)
        assertNotNull(result.transformedFramePath)
        assertNull(result.errorMessage)
    }

    // --------------------------------
    // UNCHANGED STATUS
    // --------------------------------

    @Test
    fun `UNCHANGED result is safe for downstream`() {

        val result = SanitizationResult(
            status = SanitizationStatus.UNCHANGED,
            regionsProcessed = emptyList(),
            regionCount = 0,
            sanitizationVersion = "privacy-v1",
            transformedFramePath = null
        )

        assertTrue(result.isSafeForDownstream)
        assertFalse(result.wasTransformed)
    }

    @Test
    fun `UNCHANGED result has no transformed path`() {

        val result = SanitizationResult(
            status = SanitizationStatus.UNCHANGED,
            regionsProcessed = emptyList(),
            regionCount = 0,
            sanitizationVersion = "privacy-v1",
            transformedFramePath = null
        )

        assertNull(result.transformedFramePath)
        assertEquals(0, result.regionCount)
    }

    // --------------------------------
    // UNCERTAIN STATUS
    // --------------------------------

    @Test
    fun `UNCERTAIN result is not safe for downstream`() {

        val result = SanitizationResult(
            status = SanitizationStatus.UNCERTAIN,
            regionsProcessed = emptyList(),
            regionCount = 0,
            sanitizationVersion = "privacy-v1",
            transformedFramePath = null
        )

        assertFalse(result.isSafeForDownstream)
        assertFalse(result.wasTransformed)
    }

    // --------------------------------
    // FAILED STATUS
    // --------------------------------

    @Test
    fun `FAILED result is not safe for downstream`() {

        val result = SanitizationResult(
            status = SanitizationStatus.FAILED,
            regionsProcessed = emptyList(),
            regionCount = 0,
            sanitizationVersion = "privacy-v1",
            transformedFramePath = null,
            errorMessage = "Test error"
        )

        assertFalse(result.isSafeForDownstream)
        assertFalse(result.wasTransformed)
    }

    @Test
    fun `FAILED result has error message`() {

        val result = SanitizationResult(
            status = SanitizationStatus.FAILED,
            regionsProcessed = emptyList(),
            regionCount = 0,
            sanitizationVersion = "privacy-v1",
            transformedFramePath = null,
            errorMessage = "File not found"
        )

        assertNotNull(result.errorMessage)
        assertEquals(
            "File not found",
            result.errorMessage
        )
    }

    // --------------------------------
    // EQUALITY
    // --------------------------------

    @Test
    fun `equal results are equal`() {

        val r1 = SanitizationResult(
            status = SanitizationStatus.SANITIZED,
            regionsProcessed = listOf("STATUS_BAR"),
            regionCount = 1,
            sanitizationVersion = "privacy-v1",
            transformedFramePath = "/tmp/out.png"
        )

        val r2 = SanitizationResult(
            status = SanitizationStatus.SANITIZED,
            regionsProcessed = listOf("STATUS_BAR"),
            regionCount = 1,
            sanitizationVersion = "privacy-v1",
            transformedFramePath = "/tmp/out.png"
        )

        assertEquals(r1, r2)
    }

    @Test
    fun `different status not equal`() {

        val r1 = SanitizationResult(
            status = SanitizationStatus.SANITIZED,
            regionsProcessed = emptyList(),
            regionCount = 0,
            sanitizationVersion = "privacy-v1",
            transformedFramePath = null
        )

        val r2 = SanitizationResult(
            status = SanitizationStatus.UNCHANGED,
            regionsProcessed = emptyList(),
            regionCount = 0,
            sanitizationVersion = "privacy-v1",
            transformedFramePath = null
        )

        assertFalse(r1 == r2)
    }
}
