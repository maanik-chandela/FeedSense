package com.example.feedsense.analysis.efficiency

import android.graphics.Bitmap

/*
 * Milestone 8B-11.
 *
 * Android adapter: exposes the luminance matrix of a single
 * in-memory Bitmap as a FrameImage.
 *
 * PRIVACY (§17, §18): this adapter performs ONE native pixel
 * read into a transient IntArray, reduces it to grayscale in
 * memory, and retains NOTHING. No raw frame is written to
 * disk for hashing purposes. The hash (a lossy, non-reversible
 * summary) is the only thing the deduplicator keeps.
 */
class BitmapFrameImage(
    private val bitmap: Bitmap
) : FrameImage {

    override val width: Int
        get() = bitmap.width

    override val height: Int
        get() = bitmap.height

    override fun grayMatrix(): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(
            pixels,
            0,
            bitmap.width,
            0,
            0,
            bitmap.width,
            bitmap.height
        )
        val gray = IntArray(pixels.size)
        for (i in pixels.indices) {
            gray[i] = luminance(pixels[i])
        }
        return gray
    }

    /*
     * BT.709-style luminance, rounded to integer [0,255].
     */
    private fun luminance(pixel: Int): Int {
        val red = (pixel shr 16) and 0xFF
        val green = (pixel shr 8) and 0xFF
        val blue = pixel and 0xFF
        val weighted =
            (Red * red + Green * green + Blue * blue + 500) / 1000
        return weighted.coerceIn(0, 255)
    }

    override fun toString(): String =
        "BitmapFrameImage(${bitmap.width}x${bitmap.height})"

    private companion object {
        const val Red = 212
        const val Green = 715
        const val Blue = 72
    }
}