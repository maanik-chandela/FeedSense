package com.example.feedsense.analysis.efficiency

/*
 * Milestone 8B-11.
 *
 * Reproducible hash-cost benchmark (§24 of the 8B-11 spec).
 *
 * Methodology (documented in docs/perceptual-deduplication.md):
 *   - warm-up iterations discard JIT/GC warm-up effects;
 *   - measured iterations use System.nanoTime around a single
 *     PerceptualHasher.compute call;
 *   - report count, average, median, min, max in milliseconds
 *     plus image dimensions and versions.
 *
 * The numbers here are METHODOLOGY, not claims. They depend on
 * CPU, heap, thermal state and image size. Run on a device for
 * device numbers; do not over-interpret a single measurement.
 */
object EfficiencyBenchmark {

    data class BenchmarkResult(
        val iterations: Int,
        val warmupIterations: Int,
        val algorithm: FrameHashAlgorithm,
        val hashSizeBits: Int,
        val width: Int,
        val height: Int,
        val averageMs: Double,
        val medianMs: Double,
        val minMs: Double,
        val maxMs: Double,
        val version: String
    )

    fun run(
        frame: FrameImage,
        iterations: Int = DEFAULT_ITERATIONS,
        warmup: Int = DEFAULT_WARMUP,
        algorithm: FrameHashAlgorithm = FrameHashAlgorithm.DHASH,
        hashSize: Int = PerceptualHasher.DEFAULT_HASH_SIZE
    ): BenchmarkResult {
        require(iterations > 0) { "iterations must be > 0" }
        require(warmup >= 0) { "warmup must be >= 0" }

        val hasher = PerceptualHasher(algorithm, hashSize)

        repeat(warmup) { hasher.compute(frame) }

        val samples = DoubleArray(iterations)
        for (i in 0 until iterations) {
            val start = System.nanoTime()
            hasher.compute(frame)
            val elapsedMs =
                (System.nanoTime() - start) / 1_000_000.0
            samples[i] = elapsedMs
        }

        val sorted = samples.copyOf().apply { sort() }
        val median =
            if (sorted.size % 2 == 0) {
                (sorted[sorted.size / 2 - 1] +
                    sorted[sorted.size / 2]) / 2.0
            } else {
                sorted[sorted.size / 2]
            }

        return BenchmarkResult(
            iterations = iterations,
            warmupIterations = warmup,
            algorithm = algorithm,
            hashSizeBits = hashSize * hashSize,
            width = frame.width,
            height = frame.height,
            averageMs = samples.average(),
            medianMs = median,
            minMs = samples.min(),
            maxMs = samples.max(),
            version = hasher.version
        )
    }

    private const val DEFAULT_ITERATIONS = 200
    private const val DEFAULT_WARMUP = 20
}