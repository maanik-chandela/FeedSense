package com.example.feedsense.analysis.efficiency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-11.
 *
 * Controlled engineering corpus (§35).
 *
 * This is NOT a real-world accuracy claim. It is a synthetic,
 * deterministic playground used to study how the dHash(4)
 * similarity layer responds to engineered screen changes.
 *
 * hashSize=4 gives 16-bit hashes; the 6-bit threshold keeps
 * the boundary visible. Every distance below is measured
 * against the SAME base-scene reference so the ordering
 * invariant is meaningful.
 */
class ControlledFrameCorpusTest {

    private val hashSize = 4
    private val cols = hashSize + 1
    private val rows = hashSize
    private val threshold = 6

    private data class Variant(
        val name: String,
        val cells: IntArray,
        val expectedDecision: FrameDecision,
        val note: String = "",
        val documentedRisk: Boolean = false
    )

    private val base =
        TestFrames.seededGrid(cols, rows, seed = 42L)

    private val brightnessShift: Int =
        (255 - base.max()).coerceIn(0, 25)

    private fun newDeduper() = FrameDeduplicator(
        FrameDeduplicationConfig(
            hashAlgorithm = FrameHashAlgorithm.DHASH,
            hashSize = hashSize,
            maxHammingDistance = threshold
        ),
        hasher = PerceptualHasher(FrameHashAlgorithm.DHASH, hashSize),
        nowMs = { 0L }
    )

    private fun frame(cells: IntArray, cellSize: Int = 4) =
        TestFrames.gridFrame(cols, rows, cellSize, cells)

    /*
     * Deterministically picks a second seeded scene provably far
     * enough (Hamming distance > threshold) from the base scene,
     * so the "different reel" entry is guaranteed UNIQUE by
     * construction rather than by luck.
     */
    private fun distinctReelSeed(): Long {
        val hasher = PerceptualHasher(FrameHashAlgorithm.DHASH, hashSize)
        val baseHash = hasher.compute(frame(base))
        return (0L..100L).maxByOrNull { seed ->
            val cells = TestFrames.seededGrid(cols, rows, seed)
            val d = HammingDistance.between(
                baseHash,
                hasher.compute(frame(cells))
            )
            if (d > threshold) d else 0
        } ?: 1L
    }

    @Test
    fun corpus_expectationsAreMet() {

        val still = base.copyOf()
        listOf(4, 5, 9, 10).forEach { still[it] = 230 }
        val moved = base.copyOf()
        listOf(5, 6, 10, 11).forEach { moved[it] = 230 }

        val variants = listOf(
            Variant(
                "same-shot-identical",
                base.copyOf(),
                FrameDecision.DUPLICATE,
                "Exact re-capture."
            ),
            Variant(
                "global-brightness-shift",
                // Uniform upward shift that never clamps, so no
                // gradient order is ever reversed.
                base.map { it + brightnessShift }.toIntArray(),
                FrameDecision.DUPLICATE,
                "A uniform shift changes luminance but never reorders " +
                    "a gradient, so the hash is stable."
            ),
            Variant(
                "ad-interstitial-black",
                IntArray(cols * rows) { 0 },
                FrameDecision.UNIQUE,
                "A blank interstitial is a real transition."
            ),
            Variant(
                "dark-light-inversion",
                base.map { 255 - it }.toIntArray(),
                FrameDecision.UNIQUE,
                "Luminance inversion reverses every gradient, so " +
                    "dHash reports the maximum distance.",
                documentedRisk = true
            ),
            Variant(
                "large-caption-change",
                IntArray(cols * rows) { i ->
                    // Two full grid rows form the text panel;
                    // inverting them guarantees every interior
                    // gradient flips (8 differing bits > threshold).
                    if (i in 10 until 20) 255 - base[i] else base[i]
                },
                FrameDecision.UNIQUE,
                "A full text band appearing is significant."
            ),
            Variant(
                "different-reel",
                TestFrames.seededGrid(
                    cols, rows, seed = distinctReelSeed()
                ),
                FrameDecision.UNIQUE,
                "A completely unrelated screen."
            ),
            Variant(
                "tiny-indicator-change",
                base.copyOf().also { it[7] = 0 },
                FrameDecision.SIMILAR,
                "A single small element is exactly what perceptual " +
                    "hashing tends to miss; recorded as a known " +
                    "limitation, not hidden.",
                documentedRisk = true
            ),
            Variant(
                "minor-block-motion",
                moved,
                FrameDecision.SIMILAR,
                "A bright 2x2 block shifts left by one grid cell."
            )
        )

        val smallDistances = mutableListOf<Int>()
        val majorDistances = mutableListOf<Int>()

        for (v in variants) {
            // Fresh deduper: measure the variant against the
            // SAME base reference every time.
            val dedup = newDeduper()
            dedup.evaluate(frame(base))
            val r = dedup.evaluate(frame(v.cells))

            assertEquals(
                "decision for ${v.name}: ${v.note}",
                v.expectedDecision,
                r.decision
            )

            val distance = r.hammingDistance ?: 0
            when (v.expectedDecision) {
                FrameDecision.SIMILAR -> smallDistances.add(distance)
                FrameDecision.UNIQUE -> majorDistances.add(distance)
                FrameDecision.DUPLICATE -> assertEquals(
                    "stable hash under ${v.name}",
                    0,
                    distance
                )
            }
        }

        /*
         * ORDERING INVARIANT (the property that makes a
         * threshold meaningful): every "small" change must sit
         * BELOW every "major" change in Hamming distance.
         */
        assertTrue("no major distances recorded", majorDistances.isNotEmpty())
        assertTrue(
            "small changes exceeded a major change " +
                "(small=$smallDistances, major=$majorDistances)",
            smallDistances.all { it < majorDistances.min() }
        )

        /*
         * THRESHOLD COHERENCE: SIMILAR means distance in
         * (0, threshold]; UNIQUE means distance > threshold.
         */
        assertTrue(smallDistances.all { it in 1..threshold })
        assertTrue(majorDistances.all { it > threshold })
    }

    @Test
    fun sameContentAtDifferentPixelDensity_hashesIdentically() {
        val small = frame(base.copyOf(), cellSize = 4)
        val large = frame(base.copyOf(), cellSize = 17)

        val dedup = newDeduper()
        val first = dedup.evaluate(small)
        val second = dedup.evaluate(large)

        assertEquals(first.hash, second.hash)
        assertEquals(FrameDecision.DUPLICATE, second.decision)
        assertEquals(0, second.hammingDistance)
    }

    @Test
    fun reCapturesArePerceptualDuplicates() {
        val dedup = newDeduper()
        val first = dedup.evaluate(frame(base))
        val second = dedup.evaluate(frame(base))
        assertEquals(FrameDecision.DUPLICATE, second.decision)
        assertEquals(0, second.hammingDistance)
        assertEquals(first.hash, second.referenceHash)
    }
}