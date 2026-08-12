package com.example.feedsense.analysis

import android.graphics.BitmapFactory
import java.io.File

// --------------------------------
// PERCEPTUAL HASH
// --------------------------------
//
// Milestone 7D.
//
// A lightweight difference hash (dHash) used to tell
// whether two captured frames show visually different
// content. This is the foundation for separating
// back-to-back Reels that the time + category rules
// would otherwise merge into one FeedItem.
//
// Design:
//
// - Downscale the frame to 9x8 and convert to
//   luminance.
// - For each of the 8 rows, compare each pixel with
//   the one to its right. 8x8 = 64 bits -> 16 hex
//   characters.
// - hammingDistance() counts how many bits differ.
//   Two identical frames produce 0; two completely
//   different frames produce a large number.
//
// computeFromPixels() is pure JVM so the algorithm
// can be unit-tested without an Android device.
// compute(File) is the on-device entry point.
//

class PerceptualHash {

    /*
     * Hash a frame file on-device.
     *
     * Returns null when the file cannot be decoded
     * or is too small.
     */
    fun compute(
        file: File
    ): String? {

        return try {

            val bounds =
                BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }

            BitmapFactory.decodeFile(
                file.absolutePath,
                bounds
            )

            if (
                bounds.outWidth < HASH_WIDTH ||
                bounds.outHeight < HASH_HEIGHT
            ) {
                return null
            }

            val decodeOptions =
                BitmapFactory.Options().apply {
                    inSampleSize =
                        nearestPowerOfTwoDownsample(
                            bounds.outWidth,
                            bounds.outHeight
                        )
                }

            val bitmap =
                BitmapFactory.decodeFile(
                    file.absolutePath,
                    decodeOptions
                ) ?: return null

            val width =
                bitmap.width

            val height =
                bitmap.height

            if (
                width < HASH_WIDTH ||
                height < HASH_HEIGHT
            ) {
                bitmap.recycle()
                return null
            }

            val pixels =
                IntArray(
                    width * height
                )

            bitmap.getPixels(
                pixels,
                0,
                width,
                0,
                0,
                width,
                height
            )

            bitmap.recycle()

            computeFromPixels(
                width,
                height,
                pixels
            )

        } catch (_: Exception) {

            null
        }
    }

    /*
     * Pure algorithm entry point (unit-testable).
     */
    fun computeFromPixels(
        width: Int,
        height: Int,
        pixels: IntArray
    ): String? {

        if (
            width < HASH_WIDTH ||
            height < HASH_HEIGHT ||
            pixels.size < width * height
        ) {
            return null
        }

        val gridWidth =
            HASH_WIDTH + 1

        val gray =
            IntArray(
                gridWidth * HASH_HEIGHT
            )

        val scaleX =
            width.toDouble() /
                    gridWidth.toDouble()

        val scaleY =
            height.toDouble() /
                    HASH_HEIGHT.toDouble()

        for (gy in 0 until HASH_HEIGHT) {

            for (gx in 0 until gridWidth) {

                val px =
                    ((gx + 0.5) * scaleX)
                        .toInt()
                        .coerceIn(
                            0,
                            width - 1
                        )

                val py =
                    ((gy + 0.5) * scaleY)
                        .toInt()
                        .coerceIn(
                            0,
                            height - 1
                        )

                gray[gy * gridWidth + gx] =
                    luminance(
                        pixels[py * width + px]
                    )
            }
        }

        val hex =
            StringBuilder(
                HASH_WIDTH * HASH_HEIGHT / 4
            )

        var accumulator =
            0

        var bits =
            0

        for (gy in 0 until HASH_HEIGHT) {

            for (gx in 0 until gridWidth - 1) {

                val left =
                    gray[gy * gridWidth + gx]

                val right =
                    gray[gy * gridWidth + gx + 1]

                accumulator =
                    (accumulator shl 1) or
                            (if (right >= left) 1 else 0)

                bits++

                if (bits == 4) {

                    hex.append(
                        Integer.toHexString(
                            accumulator
                        )
                    )

                    accumulator = 0
                    bits = 0
                }
            }
        }

        if (bits > 0) {

            hex.append(
                Integer.toHexString(
                    accumulator shl (4 - bits)
                )
            )
        }

        return hex.toString()
    }

    /*
     * Number of differing bits between two hashes.
     *
     * Returns null when either hash is null or they
     * are not comparable.
     */
    fun hammingDistance(
        first: String?,
        second: String?
    ): Int? {

        if (
            first == null ||
            second == null ||
            first.length != second.length
        ) {
            return null
        }

        var distance =
            0

        for (i in first.indices) {

            val a =
                Character.digit(
                    first[i],
                    16
                )

            val b =
                Character.digit(
                    second[i],
                    16
                )

            if (a < 0 || b < 0) {
                return null
            }

            distance +=
                Integer.bitCount(
                    a xor b
                )
        }

        return distance
    }

    private fun luminance(
        pixel: Int
    ): Int {

        val red =
            (pixel shr 16) and 0xFF

        val green =
            (pixel shr 8) and 0xFF

        val blue =
            pixel and 0xFF

        return (
                red * 299 +
                        green * 587 +
                        blue * 114
                ) / 1000
    }

    private fun nearestPowerOfTwoDownsample(
        width: Int,
        height: Int
    ): Int {

        var sample =
            1

        while (
            width / (sample * 2) >=
            HASH_WIDTH &&
            height / (sample * 2) >=
            HASH_HEIGHT
        ) {
            sample *= 2
        }

        return sample
    }

    companion object {

        /*
         * Grid size. The hash is
         * HASH_WIDTH * HASH_HEIGHT bits long.
         */
        const val HASH_WIDTH = 8

        const val HASH_HEIGHT = 8

        /*
         * Two frames whose hashes differ by at least
         * this many bits are treated as showing
         * different content.
         *
         * 24 of 64 bits is a strong visual change.
         */
        const val SPLIT_DISTANCE = 24
    }
}
