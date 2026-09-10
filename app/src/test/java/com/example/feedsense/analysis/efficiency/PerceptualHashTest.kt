package com.example.feedsense.analysis.efficiency

import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-11.
 *
 * Perceptual-hash tests: determinism, configuration bounds,
 * resolution invariance, edge dimensions, and the crucial
 * distinction from cryptographic hashing.
 */
class PerceptualHashTest {

    private val d8 = PerceptualHasher()
    private val p8 = PerceptualHasher(FrameHashAlgorithm.PHASH)

    @Test
    fun deterministic_sameFrame_sameHash() {
        val frame = TestFrames.solid(60, 40, 120)
        for (algorithm in FrameHashAlgorithm.values()) {
            val hasher = PerceptualHasher(algorithm)
            assertEquals(
                hasher.compute(frame),
                hasher.compute(frame)
            )
        }
    }

    @Test
    fun defaultHash_is64Bits_inOneWord() {
        val frame = TestFrames.solid(64, 64, 100)
        val hash = d8.compute(frame)
        assertEquals(64, hash.sizeBits)
        assertEquals(1, hash.bits.size)
        assertEquals(FrameHashAlgorithm.DHASH, hash.algorithm)
        assertEquals(PerceptualHashVersion.DHASH_VERSION, hash.version)
    }

    @Test
    fun hashSizeValue_producesSquareBitLengths() {
        val frame = TestFrames.solid(80, 80, 90)
        for (size in 1..16) {
            val hasher = PerceptualHasher(FrameHashAlgorithm.DHASH, size)
            val hash = hasher.compute(frame)
            assertEquals(size * size, hash.sizeBits)
        }
    }

    @Test
    fun hashSize16_spansFourWords() {
        val frame = TestFrames.solid(80, 80, 90)
        val hash = PerceptualHasher(
            FrameHashAlgorithm.DHASH, 16
        ).compute(frame)
        assertEquals(256, hash.sizeBits)
        assertEquals(4, hash.bits.size)
        assertEquals(
            "0".repeat(16) + "0".repeat(16) +
                "0".repeat(16) + "0".repeat(16),
            hash.toHex()
        )
    }

    @Test
    fun invalidHashSize_rejected() {
        for (bad in intArrayOf(0, 17)) {
            try {
                PerceptualHasher(FrameHashAlgorithm.DHASH, bad)
                org.junit.Assert.fail("expected IAE for hashSize=$bad")
            } catch (e: IllegalArgumentException) {
                // expected
            }
        }
    }

    @Test
    fun rawFrame_validatesDimensionsAndValues() {
        try {
            TestFrames.gridFrame(2, 2, 1, intArrayOf(0, 0, 0))
            org.junit.Assert.fail("expected IAE for zero height")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        try {
            RawPixelFrame(2, 2, IntArray(3))
            org.junit.Assert.fail("expected IAE for wrong length")
        } catch (e: IllegalArgumentException) {
            // expected
        }

        try {
            RawPixelFrame(2, 1, intArrayOf(0, 256))
            org.junit.Assert.fail("expected IAE for out-of-range value")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun edgeDimensions_1x1AndGrayscaleGradient() {
        val onePixel = TestFrames.solid(1, 1, 50)
        val hash = d8.compute(onePixel)
        assertEquals(64, hash.sizeBits)
        // A single-pixel frame has no gradient information at
        // all -> every bit reads 0.
        assertTrue(hash.bits.all { it == 0L })
    }

    @Test
    fun uniformFrames_haveNoGradients() {
        val black = d8.compute(TestFrames.solid(50, 50, 0))
        val white = d8.compute(TestFrames.solid(50, 50, 255))
        assertEquals(0, HammingDistance.between(black, white))
        assertTrue(black.bits.all { it == 0L })
    }

    @Test
    fun resolutionInvariance_sameLayoutDifferentPixelDensity() {
        val cols = PerceptualHasher.DEFAULT_HASH_SIZE + 1
        val rows = PerceptualHasher.DEFAULT_HASH_SIZE
        val cells = IntArray(cols * rows) { i ->
            (i * 37 + 11) % 256
        }
        val small = TestFrames.gridFrame(cols, rows, 3, cells)
        val large = TestFrames.gridFrame(cols, rows, 11, cells)
        assertEquals(
            d8.compute(small),
            d8.compute(large)
        )
    }

    @Test
    fun perceptualVsCryptographicHash_behaveOppositely() {
        val base = TestFrames.solid(40, 40, 128)
        val shifted = RawPixelFrame(
            40, 40,
            base.grayMatrix().copyOf().apply { this[0] = 0 }
        )

        val perceptual = HammingDistance.between(
            d8.compute(base),
            d8.compute(shifted)
        )

        val shaBase = sha256(base.grayMatrix())
        val shaShifted = sha256(shifted.grayMatrix())

        // One-pixel change: SHA-256 digests are completely
        // different...
        assertNotEquals(shaBase, shaShifted)

        // ...but the perceptual distance stays tiny.
        assertTrue(
            "expected small perceptual distance, got $perceptual",
            perceptual < 4
        )
    }

    @Test
    fun phash_isDeterministicAnd64Bit() {
        val frame = TestFrames.solid(64, 64, 120)
        val a = p8.compute(frame)
        val b = p8.compute(frame)
        assertEquals(a, b)
        assertEquals(64, a.sizeBits)
        assertEquals(FrameHashAlgorithm.PHASH, a.algorithm)
        assertEquals(PerceptualHashVersion.PHASH_VERSION, a.version)
    }

    @Test
    fun toHex_encodesAllWords() {
        val hash = PerceptualHasher(
            FrameHashAlgorithm.DHASH, 16
        ).compute(TestFrames.solid(80, 80, 90))
        assertEquals(4 * 16, hash.toHex().length)
        assertEquals("0".repeat(64), hash.toHex())
    }

    private fun sha256(ints: IntArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return ints.fold(digest) { d, v ->
            d.update((v ushr 24).toByte())
            d.update((v ushr 16).toByte())
            d.update((v ushr 8).toByte())
            d.update(v.toByte())
            d
        }.digest().joinToString("") { "%02x".format(it) }
    }

    @Test
    fun hammingBetween_identicalHashes_isZero() {
        val frame = TestFrames.solid(32, 32, 99)
        val a = d8.compute(frame)
        assertEquals(0, HammingDistance.between(a, a))
    }
}