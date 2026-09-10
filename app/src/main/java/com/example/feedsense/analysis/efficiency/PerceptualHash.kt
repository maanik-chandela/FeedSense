package com.example.feedsense.analysis.efficiency

/*
 * Milestone 8B-11.
 *
 * Perceptual hashing primitives.
 *
 * Perceptual hashes map VISUALLY SIMILAR images to SIMILAR
 * hash values, so visual similarity can be estimated with a
 * small Hamming distance. This is the opposite of a
 * cryptographic hash (SHA-256), where a single-pixel change
 * produces a completely different digest. The distinction is
 * documented in docs/perceptual-deduplication.md and asserted
 * by unit tests.
 */

/*
 * Supported perceptual hash algorithms.
 *
 *   DHASH  - difference hash: compares adjacent-pixel
 *            luminance gradients. Cheapest robust form.
 *   PHASH  - DCT-based hash (canonical 32x32 -> low-frequency
 *            coefficients -> median threshold). More
 *            invariant to small shifts/scale, more compute.
 *
 * dHash is the production default (cheapest); pHash is
 * available for RQ4 ("dHash vs pHash") experiments.
 */
enum class FrameHashAlgorithm(val label: String) {
    DHASH("DHASH"),
    PHASH("PHASH")
}

/*
 * Per-component version identifiers. Bump the relevant
 * constant whenever that algorithm's behavior changes; every
 * FrameHash carries the exact version that produced it so an
 * experiment can always be attributed (see §20 of the 8B-11
 * spec).
 */
object PerceptualHashVersion {
    const val DHASH_VERSION = "dhash-v1"
    const val PHASH_VERSION = "phash-v1"
    const val HAMMING_DISTANCE_VERSION = "hamming-v1"
}

/*
 * An immutable perceptual hash.
 *
 * `bits` holds sizeBits bits, word-packed LSB-first:
 * word i holds bits [i*64, (i+1)*64).
 *
 * A FrameHash is a lossy, NON-REVERSIBLE summary of a frame.
 * It contains no pixel data and cannot be used to reconstruct
 * the screen content. It is safe to report for research.
 */
data class FrameHash(
    val bits: List<Long>,
    val algorithm: FrameHashAlgorithm,
    val sizeBits: Int,
    val version: String
) {

    init {
        require(sizeBits > 0) { "sizeBits must be > 0" }
        val expectedWords = (sizeBits + 63) / 64
        require(bits.size == expectedWords) {
            "bits.size ${bits.size} != expected words $expectedWords"
        }
    }

    /*
     * Bit `index` (0-based, LSB of word 0 first).
     */
    fun bitAt(index: Int): Boolean {
        require(index in 0 until sizeBits) {
            "index $index out of range [0, $sizeBits)"
        }
        val word = bits[index / 64]
        return ((word shr (index % 64)) and 1L) == 1L
    }

    /*
     * Compact hex string for diagnostics and versioned
     * reports. Never contains screenshots or text.
     */
    fun toHex(): String {
        return buildString(bits.size * 16) {
            bits.forEach { word ->
                append(
                    java.lang.Long.toHexString(word)
                        .padStart(16, '0')
                )
            }
        }
    }

    override fun toString(): String =
        "$algorithm(v$version,${sizeBits}b:${toHex()})"
}

/*
 * Pure Hamming-distance comparison. Lower = more similar;
 * 0 means the two hashes are identical.
 */
object HammingDistance {

    const val VERSION = PerceptualHashVersion.HAMMING_DISTANCE_VERSION

    /*
     * Number of differing bits between two hashes.
     *
     * Requires the same algorithm and the same bit width;
     * hashes from different algorithms/configs are NOT
     * comparable and throw rather than silently comparing.
     */
    fun between(a: FrameHash, b: FrameHash): Int {
        require(a.algorithm == b.algorithm) {
            "cannot compare ${a.algorithm} with ${b.algorithm}"
        }
        require(a.sizeBits == b.sizeBits) {
            "cannot compare ${a.sizeBits}-bit hash with ${b.sizeBits}-bit hash"
        }
        return betweenWords(a.bits, b.bits)
    }

    /*
     * Number of differing bits between two word-packed bit
     * sequences of equal length.
     */
    fun betweenWords(a: List<Long>, b: List<Long>): Int {
        require(a.size == b.size) {
            "word lists differ in length: ${a.size} vs ${b.size}"
        }
        var distance = 0
        for (i in a.indices) {
            distance += java.lang.Long.bitCount(a[i] xor b[i])
        }
        return distance
    }
}

/*
 * Perceptual hasher.
 *
 * Deterministic: the same FrameImage and same hasher
 * configuration ALWAYS produce the same FrameHash.
 *
 * Memory: reads the frame's gray matrix once (transient),
 * reduces it to a canonical grid, and retains only
 * sizeBits bits.
 */
class PerceptualHasher(
    private val algorithm: FrameHashAlgorithm =
        FrameHashAlgorithm.DHASH,
    private val hashSize: Int = DEFAULT_HASH_SIZE
) {

    init {
        require(hashSize in MIN_HASH_SIZE..MAX_HASH_SIZE) {
            "hashSize must be in [$MIN_HASH_SIZE, $MAX_HASH_SIZE], got $hashSize"
        }
    }

    val sizeBits: Int get() = hashSize * hashSize

    val version: String
        get() = when (algorithm) {
            FrameHashAlgorithm.DHASH -> PerceptualHashVersion.DHASH_VERSION
            FrameHashAlgorithm.PHASH -> PerceptualHashVersion.PHASH_VERSION
        }

    fun compute(frame: FrameImage): FrameHash {
        val matrix = frame.grayMatrix()
        return when (algorithm) {
            FrameHashAlgorithm.DHASH ->
                computeDHash(matrix, frame.width, frame.height)
            FrameHashAlgorithm.PHASH ->
                computePHash(matrix, frame.width, frame.height)
        }
    }

    // --------------------------------------------------
    // dHash
    // --------------------------------------------------

    /*
     * dHash: box-samples the frame to (hashSize+1) x hashSize
     * luminance grid, then emits one bit per horizontal
     * gradient (right > left). Produces hashSize^2 bits.
     *
     * Box averaging (rather than nearest-neighbour) reduces
     * aliasing so different resolutions of the same content
     * tend to hash identically.
     */
    private fun computeDHash(
        matrix: IntArray,
        width: Int,
        height: Int
    ): FrameHash {
        val gridW = hashSize + 1
        val grid = boxSample(matrix, width, height, gridW, hashSize)

        val words = arrayListOf<Long>()
        var accumulator = 0L
        var bitsInWord = 0

        for (gy in 0 until hashSize) {
            for (gx in 0 until hashSize) {
                val left = grid[gy * gridW + gx]
                val right = grid[gy * gridW + gx + 1]
                accumulator = accumulator or
                    ((if (right > left) 1L else 0L) shl bitsInWord)
                bitsInWord++
                if (bitsInWord == 64) {
                    words.add(accumulator)
                    accumulator = 0L
                    bitsInWord = 0
                }
            }
        }
        if (bitsInWord > 0) {
            words.add(accumulator)
        }

        return FrameHash(
            bits = words,
            algorithm = algorithm,
            sizeBits = sizeBits,
            version = version
        )
    }

    // --------------------------------------------------
    // pHash (DCT-based)
    // --------------------------------------------------

    /*
     * Classic pHash: 32x32 greyscale -> separable 2D DCT ->
     * take the low-frequency hashSize x hashSize block ->
     * threshold at the block median. Produces hashSize^2 bits
     * (64 for the default 8x8 block).
     */
    private fun computePHash(
        matrix: IntArray,
        width: Int,
        height: Int
    ): FrameHash {
        val side = PHASH_INPUT_SIZE
        val grid = boxSample(matrix, width, height, side, side)
        val dct = dct2D(grid, side)

        val block = hashSize
        val values = DoubleArray(block * block)
        for (y in 0 until block) {
            for (x in 0 until block) {
                // Skip the DC term; the median-threshold bit
                // is invariant to the constant term.
                val v = dct[(y + 1) * side + (x + 1)]
                values[y * block + x] = v
            }
        }
        val median = median(values)

        val words = arrayListOf<Long>()
        var accumulator = 0L
        var bitsInWord = 0
        for (i in values.indices) {
            accumulator = accumulator or
                ((if (values[i] > median) 1L else 0L) shl bitsInWord)
            bitsInWord++
            if (bitsInWord == 64) {
                words.add(accumulator)
                accumulator = 0L
                bitsInWord = 0
            }
        }
        if (bitsInWord > 0) {
            words.add(accumulator)
        }

        return FrameHash(
            bits = words,
            algorithm = algorithm,
            sizeBits = sizeBits,
            version = version
        )
    }

    private fun dct2D(input: IntArray, side: Int): DoubleArray {
        val out = DoubleArray(side * side)

        // Horizontal pass.
        val temp = DoubleArray(side * side)
        for (y in 0 until side) {
            for (nu in 0 until side) {
                var sum = 0.0
                for (x in 0 until side) {
                    sum += input[y * side + x] *
                        kotlin.math.cos(
                            ((2 * x + 1) * nu * PI) / (2 * side)
                        )
                }
                temp[y * side + nu] =
                    alpha(nu, side) * sum
            }
        }

        // Vertical pass.
        for (x in 0 until side) {
            for (mv in 0 until side) {
                var sum = 0.0
                for (y in 0 until side) {
                    sum += temp[y * side + x] *
                        kotlin.math.cos(
                            ((2 * y + 1) * mv * PI) / (2 * side)
                        )
                }
                out[mv * side + x] =
                    alpha(mv, side) * sum
            }
        }
        return out
    }

    private fun alpha(u: Int, side: Int): Double {
        return if (u == 0) {
            1.0 / kotlin.math.sqrt(side.toDouble())
        } else {
            kotlin.math.sqrt(2.0 / side.toDouble())
        }
    }

    private fun median(values: DoubleArray): Double {
        val sorted = values.copyOf().apply { sort() }
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        } else {
            sorted[mid]
        }
    }

    // --------------------------------------------------
    // Shared sampling + packing helpers
    // --------------------------------------------------

    /*
     * Box-average downsample from a source gray matrix to a
     * targetW x targetH grid. Deterministic integer-arithmetic
     * (long sums) so results are identical run to run.
     */
    private fun boxSample(
        matrix: IntArray,
        srcW: Int,
        srcH: Int,
        targetW: Int,
        targetH: Int
    ): IntArray {
        require(srcW >= 1 && srcH >= 1)
        val out = IntArray(targetW * targetH)
        for (ty in 0 until targetH) {
            val y0 = (ty.toLong() * srcH) / targetH
            val y1 = (((ty + 1).toLong() * srcH) / targetH)
                .coerceAtLeast(y0 + 1)
                .coerceAtMost(srcH.toLong())
            for (tx in 0 until targetW) {
                val x0 = (tx.toLong() * srcW) / targetW
                val x1 = (((tx + 1).toLong() * srcW) / targetW)
                    .coerceAtLeast(x0 + 1)
                    .coerceAtMost(srcW.toLong())
                var sum = 0L
                var count = 0
                for (y in y0 until y1) {
                    for (x in x0 until x1) {
                        sum += matrix[(y.toInt()) * srcW + x.toInt()]
                        count++
                    }
                }
                out[ty * targetW + tx] =
                    (sum / count).toInt()
            }
        }
        return out
    }

    companion object {
        const val DEFAULT_HASH_SIZE = 8
        const val MIN_HASH_SIZE = 1
        const val MAX_HASH_SIZE = 16
        private const val PHASH_INPUT_SIZE = 32
        private const val PI = kotlin.math.PI
    }
}