package com.example.feedsense.analysis.efficiency

/*
 * Milestone 8B-11.
 *
 * Minimal grayscale pixel source for perceptual hashing.
 *
 * A FrameImage is the ONLY input the efficiency layer needs.
 * It deliberately exposes NO color and NO file handle so the
 * hasher can never be tempted to store or log the raw frame:
 * callers hand over a heavily reduced luminance matrix that is
 * consumed in memory and discarded. No raw frame is persisted
 * for hashing purposes (see docs/perceptual-deduplication.md,
 * "Privacy implications").
 */
interface FrameImage {

    val width: Int

    val height: Int

    /*
     * Row-major grayscale luminance values in [0, 255],
     * size == width * height.
     *
     * May return a shared/immutable array; callers MUST NOT
     * mutate it. Always invoked at most once per hash so the
     * transient peak memory for a large frame is bounded by
     * one width*height IntArray, which is reclaimed after the
     * hash is computed.
     */
    fun grayMatrix(): IntArray
}

/*
 * Array-backed FrameImage usable on the JVM (unit tests,
 * benchmarks, and controlled engineering corpora) without any
 * Android dependency.
 */
class RawPixelFrame(
    override val width: Int,
    override val height: Int,
    private val grayValues: IntArray
) : FrameImage {

    init {
        require(width > 0 && height > 0) {
            "frame dimensions must be >= 1x1, got ${width}x$height"
        }
        require(grayValues.size == width * height) {
            "gray matrix size ${grayValues.size} != ${width}x$height"
        }
        val invalid = grayValues.firstOrNull { it !in 0..255 }
        require(invalid == null) {
            "grayscale value $invalid outside [0,255]"
        }
    }

    override fun grayMatrix(): IntArray = grayValues

    /*
     * Opaque summary for diagnostics; deliberately returns no
     * pixel data.
     */
    override fun toString(): String =
        "RawPixelFrame(${width}x$height)"
}