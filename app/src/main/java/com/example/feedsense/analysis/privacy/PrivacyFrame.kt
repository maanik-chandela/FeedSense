package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-13.
 *
 * In-memory privacy frame.
 *
 * A bare ARGB pixel buffer plus dimensions. This is the RAW
 * FRAME BOUNDARY primitive (spec §18): the raw buffer exists
 * only for the lifetime of a PrivacyProcessor.process() call and
 * is transformed IN PLACE, so no second full-frame copy is
 * created on the common path (spec §33). After process() the
 * buffer IS the safe representation; callers must not keep an
 * alias pretending to be the raw capture.
 *
 * The buffer is a flat IntArray laid out row-major. It carries
 * no metadata, no paths and no bytes beyond the pixels, so it is
 * never serialized by the privacy layer.
 */
class PrivacyFrame(
    val width: Int,
    val height: Int,
    val pixels: IntArray
) {

    init {
        require(width > 0) { "width must be > 0, got $width" }
        require(height > 0) { "height must be > 0, got $height" }
        require(pixels.size == width * height) {
            "pixel buffer must hold width*height=${width * height} " +
                "entries, got ${pixels.size}"
        }
    }

    val area: Int get() = width * height

    fun getPixel(x: Int, y: Int): Int =
        pixels[y * width + x]

    fun setPixel(x: Int, y: Int, argb: Int) {
        pixels[y * width + x] = argb
    }

    /*
     * Normalized region -> integer pixel bounds, clamped to the
     * frame (reuses the 8B-4 geometric primitive).
     */
    fun pixelBounds(region: ProtectedRegion): PixelBounds =
        region.toPixelBounds(width, height)

    /*
     * A deterministic, content-free checksum of a rectangular
     * region. Used by the VALIDATE stage to prove a transform
     * changed the region without keeping a second copy.
     */
    fun regionChecksum(bounds: PixelBounds): Long {
        if (!bounds.isValid) return 0L
        var hash = 1469598103934665603L
        for (y in bounds.top until bounds.bottom) {
            var row = 0L
            for (x in bounds.left until bounds.right) {
                row = row * 31L + pixels[y * width + x]
            }
            hash = hash xor row
            hash *= 1099511628211L
        }
        return hash
    }

    /*
     * Full-frame checksum (deterministic, content-free) - used
     * by the VALIDATE stage and the benchmark to detect change.
     */
    fun frameChecksum(): Long = regionChecksum(
        PixelBounds(0, 0, width, height, "FRAME")
    )

    /*
     * Duplicates the buffer. Intentionally explicit: only used
     * where a second buffer is genuinely required (CROP and the
     * disabled-vs-enabled benchmark), never on the common path.
     */
    fun duplicated(): PrivacyFrame {
        return PrivacyFrame(width, height, pixels.copyOf())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PrivacyFrame) return false
        if (width != other.width || height != other.height) return false
        return pixels.contentEquals(other.pixels)
    }

    override fun hashCode(): Int =
        31 * (31 * width + height) + pixels.contentHashCode()
}