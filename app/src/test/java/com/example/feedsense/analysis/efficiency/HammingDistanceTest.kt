package com.example.feedsense.analysis.efficiency

import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * Milestone 8B-11.
 *
 * Hamming-distance tests: XOR popcount across word boundaries,
 * and strict refusal to compare incompatible hashes.
 */
class HammingDistanceTest {

    private fun hash(
        bits: LongArray,
        sizeBits: Int,
        algorithm: FrameHashAlgorithm = FrameHashAlgorithm.DHASH
    ): FrameHash = FrameHash(
        bits = bits.toList(),
        algorithm = algorithm,
        sizeBits = sizeBits,
        version = PerceptualHashVersion.DHASH_VERSION
    )

    @Test
    fun equalBitPatterns_distanceZero() {
        assertEquals(0, HammingDistance.between(
            hash(longArrayOf(0b1100L), 4),
            hash(longArrayOf(0b1100L), 4)
        ))
    }

    @Test
    fun singleBitFlips_countedAcrossWordBoundary() {
        // Two 256-bit hashes packed into four 64-bit words.
        val a = hash(LongArray(4), 256)
        val b = hash(
            longArrayOf(
                0L, 0L, 0L,
                1L shl 63  // single bit in the last word
            ),
            256
        )
        assertEquals(1, HammingDistance.between(a, b))
    }

    @Test
    fun multiBitDifferencesSumCorrectly() {
        val a = hash(longArrayOf(0b1010_1100L), 8)
        val b = hash(longArrayOf(0b0101_0011L), 8)
        // 8 differing bits.
        assertEquals(8, HammingDistance.between(a, b))
    }

    @Test
    fun bitsPackLsbFirst_andToHexIsStable() {
        // 64-bit hash: bit 0 set -> least-significant bit.
        val h = hash(longArrayOf(1L), 64)
        assertEquals("0000000000000001", h.toHex())
        val h2 = hash(longArrayOf((-1L) shl 24), 64)
        assertEquals("ffffffffff000000", h2.toHex())
    }

    @Test
    fun incompatibleHashes_refuseToCompare() {
        val dhash = hash(longArrayOf(0L), 64, FrameHashAlgorithm.DHASH)
        val phash = hash(longArrayOf(0L), 64, FrameHashAlgorithm.PHASH)

        try {
            HammingDistance.between(dhash, phash)
            org.junit.Assert.fail("expected IAE for algorithm mismatch")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        val size16 = hash(longArrayOf(0L), 16)
        val size64 = hash(longArrayOf(0L), 64)
        try {
            HammingDistance.between(size16, size64)
            org.junit.Assert.fail("expected IAE for size mismatch")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun betweenWords_lengthMismatch_rejected() {
        try {
            HammingDistance.betweenWords(
                listOf(1L),
                listOf(1L, 2L)
            )
            org.junit.Assert.fail("expected IAE for length mismatch")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}