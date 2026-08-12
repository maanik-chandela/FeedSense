package com.example.feedsense

import com.example.feedsense.analysis.PerceptualHash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PerceptualHashTest {

    private val hasher =
        PerceptualHash()

    @Test
    fun identicalImages_sameHash_zeroDistance() {

        val white =
            IntArray(64 * 64) { 0xFFFFFFFF.toInt() }

        val first =
            hasher.computeFromPixels(64, 64, white)

        val second =
            hasher.computeFromPixels(64, 64, white)

        assertNotNull(first)
        assertNotNull(second)
        assertEquals(first, second)

        assertEquals(
            0,
            hasher.hammingDistance(first, second)
        )
    }

    @Test
    fun inverseGradientPatterns_largeDistance() {

        val patternA =
            IntArray(64 * 64)

        val patternB =
            IntArray(64 * 64)

        for (y in 0 until 64) {
            for (x in 0 until 64) {

                val dark =
                    x % 2 == 0

                patternA[y * 64 + x] =
                    if (dark) {
                        0xFF101010.toInt()
                    } else {
                        0xFFE0E0E0.toInt()
                    }

                patternB[y * 64 + x] =
                    if (dark) {
                        0xFFE0E0E0.toInt()
                    } else {
                        0xFF101010.toInt()
                    }
            }
        }

        val hashA =
            hasher.computeFromPixels(64, 64, patternA)

        val hashB =
            hasher.computeFromPixels(64, 64, patternB)

        assertNotNull(hashA)
        assertNotNull(hashB)

        val distance =
            hasher.hammingDistance(
                hashA,
                hashB
            )

        assertNotNull(distance)
        assertTrue(
            "expected large distance but was $distance",
            distance!! > 40
        )
    }

    @Test
    fun hammingDistance_countsHexBitDifference() {

        assertEquals(
            2,
            hasher.hammingDistance("abcd", "abce")
        )

        assertEquals(
            0,
            hasher.hammingDistance("abcd", "abcd")
        )

        assertEquals(
            16,
            hasher.hammingDistance("0000", "ffff")
        )
    }

    @Test
    fun hammingDistance_invalidInput_returnsNull() {

        assertNull(
            hasher.hammingDistance(null, "abcd")
        )

        assertNull(
            hasher.hammingDistance("abcd", null)
        )

        assertNull(
            hasher.hammingDistance("abc", "abcd")
        )

        assertNull(
            hasher.hammingDistance("abcz", "abcd")
        )
    }

    @Test
    fun tooSmallImage_returnsNull() {

        assertNull(
            hasher.computeFromPixels(2, 2, IntArray(4))
        )
    }

    @Test
    fun grayscalePattern_deterministic() {

        val leftDark =
            IntArray(64 * 64)

        for (y in 0 until 64) {
            for (x in 0 until 64) {
                leftDark[y * 64 + x] =
                    if (x < 32) {
                        0xFF202020.toInt()
                    } else {
                        0xFFE0E0E0.toInt()
                    }
            }
        }

        val first =
            hasher.computeFromPixels(64, 64, leftDark)

        val second =
            hasher.computeFromPixels(64, 64, leftDark)

        assertEquals(first, second)
        assertNotNull(first)
    }
}
