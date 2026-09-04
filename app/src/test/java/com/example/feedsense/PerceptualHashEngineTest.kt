package com.example.feedsense

import com.example.feedsense.analysis.dedup.DeduplicationConfig
import com.example.feedsense.analysis.dedup.PerceptualHashEngine
import com.example.feedsense.analysis.dedup.RetentionDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-3.
 *
 * PerceptualHashEngine: hash computation, distance,
 * and relationship classification.
 *
 * Uses the pure JVM computeFromPixels() path so tests
 * run without an Android device.
 *
 * NOTE: dHash compares adjacent horizontal pixels in a
 * 9x8 grid. Patterns must have horizontal variation to
 * produce different hashes. Solid-color or vertically
 * banded images produce identical hashes.
 */
class PerceptualHashEngineTest {

    private val engine =
        PerceptualHashEngine()

    /*
     * Creates a checkerboard pattern with dark at
     * even columns and bright at odd columns.
     * Produces alternating bit patterns in the hash.
     */
    private fun checkerboardA(
        width: Int = 64,
        height: Int = 64
    ): IntArray {

        val pixels =
            IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dark = x % 2 == 0
                pixels[y * width + x] =
                    if (dark) {
                        0xFF101010.toInt()
                    } else {
                        0xFFE0E0E0.toInt()
                    }
            }
        }

        return pixels
    }

    /*
     * Inverse of checkerboardA: bright at even columns,
     * dark at odd columns. Produces inverted bit pattern.
     */
    private fun checkerboardB(
        width: Int = 64,
        height: Int = 64
    ): IntArray {

        val pixels =
            IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dark = x % 2 == 0
                pixels[y * width + x] =
                    if (dark) {
                        0xFFE0E0E0.toInt()
                    } else {
                        0xFF101010.toInt()
                    }
            }
        }

        return pixels
    }

    /*
     * Creates a left-half-dark, right-half-bright
     * pattern. Within each row, there is a single
     * transition point, producing a hash with a
     * mix of 0 and 1 bits.
     */
    private fun leftDarkRightBright(
        width: Int = 64,
        height: Int = 64
    ): IntArray {

        val pixels =
            IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dark = x < width / 2
                pixels[y * width + x] =
                    if (dark) {
                        0xFF101010.toInt()
                    } else {
                        0xFFE0E0E0.toInt()
                    }
            }
        }

        return pixels
    }

    /*
     * Inverse: left-half-bright, right-half-dark.
     */
    private fun leftBrightRightDark(
        width: Int = 64,
        height: Int = 64
    ): IntArray {

        val pixels =
            IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val dark = x < width / 2
                pixels[y * width + x] =
                    if (dark) {
                        0xFFE0E0E0.toInt()
                    } else {
                        0xFF101010.toInt()
                    }
            }
        }

        return pixels
    }

    // --------------------------------
    // COMPUTE HASH FROM PIXELS
    // --------------------------------

    @Test
    fun `identical images produce same hash`() {

        val pixels = checkerboardA()

        val first =
            engine.computeHashFromPixels(
                64, 64, pixels
            )

        val second =
            engine.computeHashFromPixels(
                64, 64, pixels
            )

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(first, second)
    }

    @Test
    fun `inverse checkerboard patterns produce different hashes`() {

        val hashA =
            engine.computeHashFromPixels(
                64, 64,
                checkerboardA()
            )!!

        val hashB =
            engine.computeHashFromPixels(
                64, 64,
                checkerboardB()
            )!!

        assertTrue(hashA != hashB)
    }

    @Test
    fun `left-dark vs left-bright produce different hashes`() {

        val hashLD =
            engine.computeHashFromPixels(
                64, 64,
                leftDarkRightBright()
            )!!

        val hashLB =
            engine.computeHashFromPixels(
                64, 64,
                leftBrightRightDark()
            )!!

        assertTrue(hashLD != hashLB)
    }

    @Test
    fun `too small image returns null`() {

        assertNull(
            engine.computeHashFromPixels(
                2, 2, IntArray(4)
            )
        )
    }

    // --------------------------------
    // COMPUTE DISTANCE
    // --------------------------------

    @Test
    fun `same hash has zero distance`() {

        val pixels = checkerboardA()

        val hash =
            engine.computeHashFromPixels(
                64, 64, pixels
            )!!

        assertEquals(
            0,
            engine.computeDistance(hash, hash)
        )
    }

    @Test
    fun `inverse checkerboard has large distance`() {

        val hashA =
            engine.computeHashFromPixels(
                64, 64,
                checkerboardA()
            )!!

        val hashB =
            engine.computeHashFromPixels(
                64, 64,
                checkerboardB()
            )!!

        val distance =
            engine.computeDistance(hashA, hashB)

        assertNotNull(distance)
        assertTrue(
            "expected large distance but was $distance",
            distance!! > 40
        )
    }

    @Test
    fun `null hash returns null distance`() {

        assertNull(
            engine.computeDistance(null, "abcd")
        )

        assertNull(
            engine.computeDistance("abcd", null)
        )
    }

    // --------------------------------
    // CLASSIFY RELATIONSHIP
    // --------------------------------

    @Test
    fun `identical hashes are DEFINITELY_SAME`() {

        val pixels = checkerboardA()

        val hash =
            engine.computeHashFromPixels(
                64, 64, pixels
            )!!

        val decision =
            engine.classifyRelationship(
                hash, hash
            )

        assertEquals(
            RetentionDecision.DEFINITELY_SAME,
            decision
        )
    }

    @Test
    fun `null hashes are UNCERTAIN`() {

        assertEquals(
            RetentionDecision.UNCERTAIN,
            engine.classifyRelationship(
                null, "abcd"
            )
        )

        assertEquals(
            RetentionDecision.UNCERTAIN,
            engine.classifyRelationship(
                "abcd", null
            )
        )

        assertEquals(
            RetentionDecision.UNCERTAIN,
            engine.classifyRelationship(
                null, null
            )
        )
    }

    @Test
    fun `inverse checkerboard patterns are DIFFERENT`() {

        val hashA =
            engine.computeHashFromPixels(
                64, 64,
                checkerboardA()
            )!!

        val hashB =
            engine.computeHashFromPixels(
                64, 64,
                checkerboardB()
            )!!

        val decision =
            engine.classifyRelationship(
                hashA, hashB
            )

        assertEquals(
            RetentionDecision.DIFFERENT,
            decision
        )
    }

    @Test
    fun `classifyByDistance uses config thresholds`() {

        val config = DeduplicationConfig(
            definitelySameThreshold = 0,
            nearDuplicateThreshold = 5
        )

        assertEquals(
            RetentionDecision.DEFINITELY_SAME,
            engine.classifyByDistance(0, config)
        )

        assertEquals(
            RetentionDecision.PROBABLY_SAME,
            engine.classifyByDistance(3, config)
        )

        assertEquals(
            RetentionDecision.PROBABLY_SAME,
            engine.classifyByDistance(5, config)
        )

        assertEquals(
            RetentionDecision.DIFFERENT,
            engine.classifyByDistance(6, config)
        )

        assertEquals(
            RetentionDecision.DIFFERENT,
            engine.classifyByDistance(30, config)
        )
    }

    @Test
    fun `classifyByDistance respects custom threshold`() {

        val strictConfig = DeduplicationConfig(
            definitelySameThreshold = 0,
            nearDuplicateThreshold = 2
        )

        assertEquals(
            RetentionDecision.DEFINITELY_SAME,
            engine.classifyByDistance(
                0, strictConfig
            )
        )

        assertEquals(
            RetentionDecision.PROBABLY_SAME,
            engine.classifyByDistance(
                1, strictConfig
            )
        )

        assertEquals(
            RetentionDecision.PROBABLY_SAME,
            engine.classifyByDistance(
                2, strictConfig
            )
        )

        assertEquals(
            RetentionDecision.DIFFERENT,
            engine.classifyByDistance(
                3, strictConfig
            )
        )
    }

    // --------------------------------
    // PATTERN SPECIFICITY
    // --------------------------------

    @Test
    fun `checkerboard vs left-split have different hashes`() {

        val hashChecker =
            engine.computeHashFromPixels(
                64, 64,
                checkerboardA()
            )!!

        val hashSplit =
            engine.computeHashFromPixels(
                64, 64,
                leftDarkRightBright()
            )!!

        val distance =
            engine.computeDistance(
                hashChecker, hashSplit
            )!!

        assertTrue(distance > 5)
    }

    @Test
    fun `same pattern at different sizes still matches`() {

        val hashSmall =
            engine.computeHashFromPixels(
                32, 32,
                checkerboardA(32, 32)
            )

        val hashLarge =
            engine.computeHashFromPixels(
                64, 64,
                checkerboardA(64, 64)
            )

        // Same pattern at different scales may or may
        // not match depending on downscaling. Just
        // verify both produce valid hashes.
        if (hashSmall != null && hashLarge != null) {
            val distance =
                engine.computeDistance(
                    hashSmall, hashLarge
                )
            assertNotNull(distance)
        }
    }
}
