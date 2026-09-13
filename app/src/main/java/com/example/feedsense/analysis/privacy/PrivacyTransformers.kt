package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-13.
 *
 * Deterministic pixel transformations (spec §11).
 *
 * Every transform is a pure function on (frame, bounds); it
 * reads and writes only the supplied PrivacyFrame and uses no
 * randomness, no system state and no timing. Identical inputs
 * always yield identical output.
 *
 * Colour-format note (spec §6): FeedSense evidence is ARGB
 * unknown-rendering; these transforms operate on the raw ARGB
 * integer independent of a particular draw implementation, so
 * they stay deterministic and testable in the pure-JVM core.
 */
object PrivacyTransformers {

    const val PIXEL_CELL = 16
    const val MASK_STRIPE = 8

    /*
     * Box blur over a 3x3 neighbourhood, clamped at the region
     * edges (no out-of-bounds reads). Average-ARGB rounding keeps
     * output deterministic.
     */
    fun blur(
        frame: PrivacyFrame,
        bounds: PixelBounds
    ) {
        if (!bounds.isValid) return
        val snapshotWidth = bounds.width
        val snapshot = IntArray(snapshotWidth * bounds.height)
        var idx = 0
        for (y in bounds.top until bounds.bottom) {
            val rowStart = y * frame.width + bounds.left
            System.arraycopy(
                frame.pixels, rowStart, snapshot, idx, snapshotWidth
            )
            idx += snapshotWidth
        }
        var i = 0
        for (y in bounds.top until bounds.bottom) {
            for (x in bounds.left until bounds.right) {
                frame.setPixel(x, y, boxAverage(
                    snapshot, snapshotWidth, bounds,
                    x - bounds.left, y - bounds.top
                ))
                i++
            }
        }
    }

    private fun boxAverage(
        src: IntArray,
        snapshotWidth: Int,
        bounds: PixelBounds,
        localX: Int,
        localY: Int
    ): Int {
        var ar = 0
        var ag = 0
        var ab = 0
        var count = 0
        for (dy in -1..1) {
            for (dx in -1..1) {
                val nx = localX + dx
                val ny = localY + dy
                if (nx < 0 || ny < 0 ||
                    nx >= snapshotWidth || ny >= bounds.height
                ) {
                    continue
                }
                val pixel = src[ny * snapshotWidth + nx]
                ar += (pixel shr 16) and 0xFF
                ag += (pixel shr 8) and 0xFF
                ab += pixel and 0xFF
                count++
            }
        }
        if (count == 0) return src[localY * snapshotWidth + localX]
        val a = ((ar / count) shl 16) or
            ((ag / count) shl 8) or
            (ab / count)
        return a or 0xFF000000.toInt()
    }

    /*
     * Fixed-cell mosaic. Each cell is filled with the average of
     * its pixels, so the region becomes crisp unreadable squares.
     */
    fun pixelate(
        frame: PrivacyFrame,
        bounds: PixelBounds
    ) {
        if (!bounds.isValid) return
        val cell = PIXEL_CELL.coerceAtMost(bounds.height)
            .coerceAtLeast(1)
        var y = bounds.top
        while (y < bounds.bottom) {
            var x = bounds.left
            while (x < bounds.right) {
                val average = cellAverage(
                    frame, bounds, x, y, cell
                )
                val xEnd = (x + cell).coerceAtMost(bounds.right)
                val yEnd = (y + cell).coerceAtMost(bounds.bottom)
                for (patchY in y until yEnd) {
                    for (patchX in x until xEnd) {
                        frame.setPixel(patchX, patchY, average)
                    }
                }
                x = xEnd
            }
            y += cell
        }
    }

    private fun cellAverage(
        frame: PrivacyFrame,
        bounds: PixelBounds,
        xStart: Int,
        yStart: Int,
        cell: Int
    ): Int {
        val xEnd = (xStart + cell).coerceAtMost(bounds.right)
        val yEnd = (yStart + cell).coerceAtMost(bounds.bottom)
        var ar = 0L
        var ag = 0L
        var ab = 0L
        var count = 0L
        for (y in yStart until yEnd) {
            for (x in xStart until xEnd) {
                val p = frame.getPixel(x, y)
                ar += (p shr 16) and 0xFF
                ag += (p shr 8) and 0xFF
                ab += p and 0xFF
                count++
            }
        }
        if (count == 0L) return 0xFF000000.toInt()
        val r = (ar / count).toInt()
        val g = (ag / count).toInt()
        val b = (ab / count).toInt()
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /*
     * Deterministic marking pattern: diagonal stripes whose tones
     * derive from the region's average colour and a stable hash
     * of the region geometry. No randomness - content is fully
     * replaced.
     */
    fun mask(
        frame: PrivacyFrame,
        bounds: PixelBounds
    ) {
        if (!bounds.isValid) return
        val base = cellAverage(
            frame, bounds, bounds.left, bounds.top, bounds.height
        )
        val seed = stableSeed(bounds)
        val toneA = ToneShade.shade(base, seed and 0xFF)
        val toneB = ToneShade.shade(base, (seed shr 8) and 0xFF)
        for (y in bounds.top until bounds.bottom) {
            for (x in bounds.left until bounds.right) {
                val stripe = (x + y) / MASK_STRIPE
                frame.setPixel(
                    x, y,
                    if (stripe and 1 == 0) toneA else toneB
                )
            }
        }
    }

    /*
     * Deterministic 32-bit hash of the region geometry and label;
     * stable across runs and platforms.
     */
    private fun stableSeed(bounds: PixelBounds): Int {
        var h = 1125899906842597L
        val parts = longArrayOf(
            bounds.left.toLong(),
            bounds.top.toLong(),
            bounds.right.toLong(),
            bounds.bottom.toLong()
        )
        for (part in parts) {
            h = (h xor part) * 1099511628211L
        }
        h = (h xor bounds.label.hashCode().toLong()) * 1099511628211L
        h = (h xor (h ushr 62))
        return (h and 0xFFFFFFFFL).toInt()
    }

    /*
     * Removes a full-width (or full-height) band from the frame,
     * returning a NEW frame without that band. Rows above the
     * bounds are kept, the band is dropped, rows below are
     * shifted up.
     *
     * Only bands spanning the full width (or full height) may be
     * cropped; narrow regions cannot be cropped because cropping
     * only removes whole rows/columns.
     */
    fun cropBand(
        frame: PrivacyFrame,
        bounds: PixelBounds
    ): PrivacyFrame {
        val fullWidth = bounds.left == 0 && bounds.right == frame.width
        val fullHeight = bounds.top == 0 && bounds.bottom == frame.height

        if (fullWidth) {
            val band = (bounds.bottom - bounds.top).coerceAtLeast(0)
            val newHeight = frame.height - band
            if (newHeight <= 0) return frame
            val out = IntArray(frame.width * newHeight)
            for (y in 0 until frame.height) {
                if (y in bounds.top until bounds.bottom) continue
                val dstY = if (y < bounds.top) y else y - band
                val dstOffset = dstY * frame.width
                val srcOffset = y * frame.width
                System.arraycopy(
                    frame.pixels, srcOffset,
                    out, dstOffset, frame.width
                )
            }
            return PrivacyFrame(frame.width, newHeight, out)
        }

        if (fullHeight) {
            val band = (bounds.right - bounds.left).coerceAtLeast(0)
            val newWidth = frame.width - band
            if (newWidth <= 0) return frame
            val out = IntArray(newWidth * frame.height)
            for (y in 0 until frame.height) {
                var dstX = 0
                for (x in 0 until frame.width) {
                    if (x in bounds.left until bounds.right) continue
                    out[y * newWidth + dstX] = frame.pixels[y * frame.width + x]
                    dstX++
                }
            }
            return PrivacyFrame(newWidth, frame.height, out)
        }

        // Partial stripe: not representable as a crop; return
        // frame unchanged - the caller falls back to MASK.
        return frame
    }
}

/*
 * Deterministic tone shading used by the mask pattern: shifts
 * the brightness of a base colour by a fixed amount based on a
 * stable seed, keeping alpha opaque.
 */
object ToneShade {

    fun shade(base: Int, shift: Int): Int {
        val r = (((base shr 16) and 0xFF) + (shift % 48) - 24)
            .coerceIn(0, 255)
        val g = (((base shr 8) and 0xFF) + ((shift shr 1) % 48) - 24)
            .coerceIn(0, 255)
        val b = ((base and 0xFF) + ((shift shr 2) % 48) - 24)
            .coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}