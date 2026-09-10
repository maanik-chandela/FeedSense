package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-13.
 *
 * Processing-time / allocation benchmark for the privacy layer
 * (spec §48).
 *
 * Pure JVM: synthetic frames, deterministic pixel content, one
 * PrivacyProcessor instance per scenario. Reports
 *
 *   medianProcessingMs  : median wall time of process() per frame.
 *   meanProcessingMs    : mean wall time.
 *   framesPerSecond     : observed only (no battery claims).
 *   framePixelBytes     : estimate of the in-memory ARGB buffer.
 *   allocationsPerFrame : coarse count of top-level allocations in
 *                         the processing path (methodology-level;
 *                         never a profiler claim).
 *
 * The disabled-vs-enabled columns let a research run EXPLAIN the
 * cost of transformation on the same geometry. Every number here
 * is measured on the run's own machine and is not generalized.
 */
data class PrivacyBenchmarkRun(
    val scenario: String,
    val frameWidth: Int,
    val frameHeight: Int,
    val frames: Int,
    val timestampNs: Long,
    val processingMillis: List<Double>,
    val transformationsApplied: Map<String, Int>,
    val regionsSeen: Int,
    val droppedFrames: Int,
    val benchmarkVersion: String
) {

    val medianProcessingMs: Double
        get() = median(processingMillis)

    val meanProcessingMs: Double
        get() = if (processingMillis.isEmpty()) 0.0 else
            processingMillis.average()

    val framesPerSecond: Double
        get() = if (meanProcessingMs <= 0.0) 0.0 else
            1000.0 / meanProcessingMs

    val framePixelBytes: Long
        get() = frameWidth.toLong() * frameHeight.toLong() * 4L

    private fun median(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        }
    }
}

/*
 * Milestone 8B-13.
 *
 * Benchmark runner. Walls timing off a caller-supplied clock
 * (defaults to System.nanoTime) so tests can inject a fixed
 * clock to verify the statistics derivation deterministically.
 */
class PrivacyBenchmark(
    private val clockNs: () -> Long = { System.nanoTime() }
) {

    fun run(
        scenario: PrivacyBenchmarkScenario,
        frames: Int = DEFAULT_FRAMES
    ): PrivacyBenchmarkRun {
        require(frames > 0) { "frames must be > 0" }

        val processor = PrivacyProcessor(scenario.policy)
        val timings = mutableListOf<Double>()
        val transformations = mutableMapOf<String, Int>()
        var regionsSeen = 0
        var dropped = 0

        for (i in 0 until frames) {
            val frame = SyntheticFrames.verticalGradient(
                scenario.frameWidth, scenario.frameHeight
            )
            val start = clockNs()
            val result = processor.process(
                frame,
                scenario.regions,
                OcrAvailability.OCR_AVAILABLE
            )
            val elapsedNs = clockNs() - start
            timings += elapsedNs / 1_000_000.0

            regionsSeen += result.decision.regionsDetected
            if (result.decision.dropped) dropped++
            result.decision.transformations.forEach { applied ->
                transformations.merge(
                    "${applied.regionType.label}:${applied.transformation.label}",
                    1,
                    Int::plus
                )
            }
        }

        return PrivacyBenchmarkRun(
            scenario = scenario.label,
            frameWidth = scenario.frameWidth,
            frameHeight = scenario.frameHeight,
            frames = frames,
            timestampNs = clockNs(),
            processingMillis = timings,
            transformationsApplied = transformations,
            regionsSeen = regionsSeen,
            droppedFrames = dropped,
            benchmarkVersion = PrivacySanitizationVersion.BENCHMARK
        )
    }

    companion object {
        const val DEFAULT_FRAMES = 120
    }
}

/*
 * Milestone 8B-13.
 *
 * A benchmark scenario: policy + the regions it must consider on
 * a synthetic frame.
 */
data class PrivacyBenchmarkScenario(
    val label: String,
    val policy: PrivacyPolicy,
    val frameWidth: Int = 1080,
    val frameHeight: Int = 2400,
    val regions: List<PrivacyRegion>
)

/*
 * Milestone 8B-13.
 *
 * Deterministic synthetic frame generator (no randomness), so
 * repeated benchmark runs process identical pixels.
 */
object SyntheticFrames {

    fun verticalGradient(width: Int, height: Int): PrivacyFrame {
        val pixels = IntArray(width * height)
        var i = 0
        for (y in 0 until height) {
            val g = (y * 255 / height.coerceAtLeast(1))
            val b = ((width + y) * 255 / (width + height).coerceAtLeast(1))
            for (x in 0 until width) {
                val r = (x * 255 / width.coerceAtLeast(1))
                pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                i++
            }
        }
        return PrivacyFrame(width, height, pixels)
    }

    fun solid(width: Int, height: Int, argb: Int): PrivacyFrame {
        val pixels = IntArray(width * height) { argb }
        return PrivacyFrame(width, height, pixels)
    }

    fun checkerboard(
        width: Int, height: Int, a: Int, b: Int, cell: Int = 8
    ): PrivacyFrame {
        val pixels = IntArray(width * height)
        var i = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pattern = ((x / cell) + (y / cell)) and 1
                pixels[i] = if (pattern == 0) a else b
                i++
            }
        }
        return PrivacyFrame(width, height, pixels)
    }
}

/*
 * Milestone 8B-13.
 *
 * The benchmark scenarios: disabled (control), research, strict
 * and a heavy workload.
 */
object PrivacyBenchmarkScenarios {

    private val systemUiRegions = PRIVACY_SYSTEM_UI_REGIONS

    private fun chatRegion(y: Double) = PrivacyRegion(
        type = PrivacyRegionType.PRIVATE_TEXT,
        bounds = ProtectedRegion(0.05, y, 0.9, 0.1, "CHAT_$y"),
        signals = listOf(PrivacyDetectionSignal.OCR_TEXT_PATTERN)
    )

    fun scenarios(
        width: Int = 1080,
        height: Int = 2400
    ): List<PrivacyBenchmarkScenario> {
        return listOf(
            PrivacyBenchmarkScenario(
                label = "disabled",
                policy = PrivacyPolicy.DISABLED,
                frameWidth = width,
                frameHeight = height,
                regions = systemUiRegions
            ),
            PrivacyBenchmarkScenario(
                label = "research",
                policy = PrivacyPolicy.RESEARCH,
                frameWidth = width,
                frameHeight = height,
                regions = systemUiRegions + chatRegion(0.3)
            ),
            PrivacyBenchmarkScenario(
                label = "strict",
                policy = PrivacyPolicy.STRICT,
                frameWidth = width,
                frameHeight = height,
                regions = systemUiRegions + chatRegion(0.3)
            ),
            PrivacyBenchmarkScenario(
                label = "heavy",
                policy = PrivacyPolicy.STRICT,
                frameWidth = width,
                frameHeight = height,
                regions = (0..19).map { i ->
                    val y = 0.06 + i * 0.04
                    chatRegion(y)
                }
            )
        )
    }
}