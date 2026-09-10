package com.example.feedsense.analysis.ml.preprocess

import com.example.feedsense.analysis.privacy.PrivacyFrame

// --------------------------------
// SPATIAL TRANSFORM (8B-15-3)
// --------------------------------
//
// Orientation handling, resizing, cropping and padding. Every
// operation is pure and deterministic:
//
//   - the same source pixels + the same policy + the same target
//     dimensions ALWAYS produce the same output pixels;
//   - no dependence on device, display rotation, locale, timezone
//     or unordered collections;
//   - no random augmentation (this is inference preprocessing).
//
// Coordinate conventions (documented so a future change is
// attributable, not silent):
//   - NEAREST sampling uses integer floor mapping
//       sx = ox * srcW / dstW      (matches 8B-14 preprocess-v1)
//   - BILINEAR uses half-pixel centers
//       sx = (ox + 0.5) * srcW / dstW - 0.5
//     which matches the default TFLite RESIZE_BILINEAR convention
//     (align_corners = false), i.e. the convention the actual
//     runtime family (LiteRT/TFLite) uses.
//
// Allocation policy (performance boundary, 8B-15-7):
//   each operation produces exactly one output buffer; the
//   pipeline reuses them downstream. No unbounded queues, no
//   retained large frames, no unbounded intermediate copies.

/**
 * How many clockwise quarter-turns the capture metadata says the
 * frame is rotated. The exact source of this metadata is the
 * capture/layout orientation recorded with the evidence; Android
 * display rotation is NEVER assumed to equal image rotation
 * (milestone §6).
 */
enum class FrameOrientation(val label: String) {
    DEG_0("0"),
    DEG_90("90"),
    DEG_180("180"),
    DEG_270("270")
}

object OrientationTransform {

    /*
     * Rotates a frame clockwise by the given number of quarter
     * turns. Deterministic. Dimensions swap for 90/270.
     */
    fun rotateClockwise(
        frame: PrivacyFrame,
        quarterTurns: Int
    ): PrivacyFrame {
        val turns = ((quarterTurns % 4) + 4) % 4
        if (turns == 0) {
            return PrivacyFrame(frame.width, frame.height, frame.pixels.copyOf())
        }
        if (turns == 2) {
            return rotate180(frame)
        }
        return rotate90Cw(frame, turns)
    }

    /*
     * Rotates to the orientation required by the configuration
     * target (the model consumes upright frames; the incoming
     * capture metadata may say otherwise).
     *
     * NORMALIZE_TO_0 rotates the frame to upright regardless of
     * the recorded layout orientation, so feed evidence always
     * reaches the model with the same optical orientation.
     */
    fun orient(
        frame: PrivacyFrame,
        captured: FrameOrientation,
        policy: OrientationPolicy
    ): PrivacyFrame {
        val rotateCwTurns = when (policy) {
            OrientationPolicy.NORMALIZE_TO_0 ->
                when (captured) {
                    FrameOrientation.DEG_0 -> 0
                    FrameOrientation.DEG_90 -> 3
                    FrameOrientation.DEG_180 -> 2
                    FrameOrientation.DEG_270 -> 1
                }
            OrientationPolicy.ROTATE_90 -> 1
            OrientationPolicy.ROTATE_180 -> 2
            OrientationPolicy.ROTATE_270 -> 3
            OrientationPolicy.UNKNOWN -> 0
        }
        return rotateClockwise(frame, rotateCwTurns)
    }

    private fun rotate180(frame: PrivacyFrame): PrivacyFrame {
        val w = frame.width
        val h = frame.height
        val out = IntArray(frame.pixels.size)
        var outIndex = 0
        for (y in 0 until h) {
            val srcRow = (h - 1 - y) * w
            val start = srcRow + w - 1
            for (x in 0 until w) {
                out[outIndex++] = frame.pixels[start - x]
            }
        }
        return PrivacyFrame(w, h, out)
    }

    /*
     * Single 90-degree clockwise rotation.
     */
    private fun rotate90Cw(frame: PrivacyFrame, turns: Int): PrivacyFrame {
        var current = frame
        repeat(turns) {
            current = rotate90CwOnce(current)
        }
        return current
    }

    private fun rotate90CwOnce(frame: PrivacyFrame): PrivacyFrame {
        val srcW = frame.width
        val srcH = frame.height
        // dstWidth = srcH, dstHeight = srcW
        val dstW = srcH
        val dstH = srcW
        val out = IntArray(srcW * srcH)
        val src = frame.pixels
        var outIndex = 0
        for (y in 0 until dstH) {
            for (x in 0 until dstW) {
                val sx = y
                val sy = srcH - 1 - x
                out[outIndex++] = src[sy * srcW + sx]
            }
        }
        return PrivacyFrame(dstW, dstH, out)
    }
}

object SpatialTransform {

    /*
     * Resizes a frame to (width, height). NEAREST uses integer
     * floor sampling; BILINEAR uses half-pixel centers with per-
     * channel interpolation. Produces exactly one output buffer.
     */
    fun resize(
        frame: PrivacyFrame,
        width: Int,
        height: Int,
        interpolation: Interpolation
    ): PrivacyFrame {
        require(width > 0 && height > 0) { "target dimensions must be positive" }
        val out = IntArray(width * height)
        val srcW = frame.width
        val srcH = frame.height
        val src = frame.pixels

        if (srcW == width && srcH == height) {
            return PrivacyFrame(width, height, frame.pixels.copyOf())
        }

        when (interpolation) {
            Interpolation.NEAREST -> {
                for (y in 0 until height) {
                    val srcY = y * srcH / height
                    for (x in 0 until width) {
                        val srcX = x * srcW / width
                        out[y * width + x] = src[srcY * srcW + srcX]
                    }
                }
            }
            Interpolation.BILINEAR -> {
                for (y in 0 until height) {
                    val fy = mapHalfPixel(y, srcH, height)
                    val y0 = fy.first
                    val wy = fy.second
                    for (x in 0 until width) {
                        val fx = mapHalfPixel(x, srcW, width)
                        val x0 = fx.first
                        val wx = fx.second
                        out[y * width + x] = sampleBilinear(
                            src, srcW, srcH, x0, y0, wx, wy
                        )
                    }
                }
            }
            Interpolation.UNKNOWN -> {
                throw IllegalArgumentException("cannot resize with UNKNOWN interpolation")
            }
        }
        return PrivacyFrame(width, height, out)
    }

    /*
     * The full aspect-ratio-aware spatial stage.
     *
     * After orientation normalization the source is mapped to the
     * model's spatial size according to the configured policy.
     *
     *   STRETCH / RESIZE : direct resize (aspect distortion for
     *     STRETCH; RESIZE is the same operation used when the
     *     aspect already matches).
     *   CENTER_CROP      : crop a window of the target aspect,
     *     anchored per cropAnchor, then resize. For vertical feed
     *     frames this crops top/center/bottom bands - anchor
     *     choice decides which research content survives.
     *   PAD              : fit the source into the target aspect
     *     (no crop, no distortion) and pad the borders with the
     *     configured padding value.
     */
    fun applyAspectPolicy(
        frame: PrivacyFrame,
        config: PreprocessingConfig,
        sourceOrientation: FrameOrientation
    ): PrivacyFrame {
        val width = config.inputWidth ?: return frame
        val height = config.inputHeight ?: return frame
        val oriented = OrientationTransform.orient(frame, sourceOrientation, config.orientationPolicy)

        return when (config.cropPolicy) {
            AspectRatioPolicy.STRETCH, AspectRatioPolicy.RESIZE ->
                resize(oriented, width, height, config.interpolation)
            AspectRatioPolicy.CENTER_CROP ->
                centerCropThenResize(oriented, width, height, config.interpolation, config.cropAnchor)
            AspectRatioPolicy.PAD ->
                padToFit(oriented, width, height, config.interpolation, config.paddingValue)
            AspectRatioPolicy.UNKNOWN -> throw IllegalArgumentException(
                "cannot spatially transform with UNKNOWN aspect-ratio policy"
            )
        }
    }

    /*
     * Crop a window with the target aspect ratio, anchored, then
     * resize the window to the target size.
     *
     * Vertical FeedSense frames: the window is full-width and
     * height-limited (cropping top/bottom). Horizontal frames: the
     * window is full-height and width-limited; TOP/BOTTOM fall back
     * to horizontal CENTER for the width placement.
     */
    fun centerCropThenResize(
        frame: PrivacyFrame,
        targetW: Int,
        targetH: Int,
        interpolation: Interpolation,
        anchor: CropAnchor
    ): PrivacyFrame {
        val srcW = frame.width
        val srcH = frame.height
        var cw = srcW
        var ch = srcH

        // Cross-multiplication form of "source is proportionally
        // TALLER than the target":
        //     srcW / srcH < targetW / targetH   <=>   srcW * targetH < targetW * srcH
        // (A wider source satisfies the opposite inequality and is
        // cropped horizontally in the else branch.)
        val sourceAspectTaller =
            srcW * targetH.toLong() < targetW.toLong() * srcH

        if (sourceAspectTaller) {
            // Source is proportionally taller: crop vertically.
            ch = (srcW.toLong() * targetH / targetW).toInt().coerceIn(1, srcH)
        } else {
            // Source is proportionally wider: crop horizontally.
            cw = (srcH.toLong() * targetW / targetH).toInt().coerceIn(1, srcW)
        }

        val (xOffset, yOffset) = computeCropOffset(
            srcW, srcH, cw, ch, anchor
        )
        val cropped = crop(frame, xOffset, yOffset, cw, ch)
        return resize(cropped, targetW, targetH, interpolation)
    }

    /*
     * Crop a window out of a frame (one output buffer).
     */
    fun crop(
        frame: PrivacyFrame,
        x: Int,
        y: Int,
        width: Int,
        height: Int
    ): PrivacyFrame {
        require(x >= 0 && y >= 0) { "crop origin must be non-negative" }
        require(width > 0 && height > 0) { "crop size must be positive" }
        require(x + width <= frame.width && y + height <= frame.height) {
            "crop window must fit inside the frame"
        }
        val out = IntArray(width * height)
        val srcW = frame.width
        var outIndex = 0
        for (row in y until y + height) {
            val base = row * srcW + x
            for (col in 0 until width) {
                out[outIndex++] = frame.pixels[base + col]
            }
        }
        return PrivacyFrame(width, height, out)
    }

    /*
     * Fit the source into (targetW, targetH) preserving aspect,
     * then place it centered on a canvas filled with the padding
     * value. Bars are the padding color. Deterministic.
     */
    fun padToFit(
        frame: PrivacyFrame,
        targetW: Int,
        targetH: Int,
        interpolation: Interpolation,
        paddingValue: Int
    ): PrivacyFrame {
        require(targetW > 0 && targetH > 0) { "target dimensions must be positive" }
        val srcW = frame.width
        val srcH = frame.height

        val scale = minOf(
            targetW.toDouble() / srcW,
            targetH.toDouble() / srcH
        )
        val fitW = Math.floor(srcW * scale).toInt().coerceAtLeast(1)
        val fitH = Math.floor(srcH * scale).toInt().coerceAtLeast(1)

        val fitted = if (fitW == srcW && fitH == srcH) frame else resize(frame, fitW, fitH, interpolation)
        val offsetX = (targetW - fitW) / 2
        val offsetY = (targetH - fitH) / 2

        val canvas = IntArray(targetW * targetH) { paddingValue }
        for (row in 0 until fitH) {
            val srcBase = row * fitW
            val dstBase = (offsetY + row) * targetW + offsetX
            System.arraycopy(fitted.pixels, srcBase, canvas, dstBase, fitW)
        }
        return PrivacyFrame(targetW, targetH, canvas)
    }

    private fun computeCropOffset(
        srcW: Int,
        srcH: Int,
        cw: Int,
        ch: Int,
        anchor: CropAnchor
    ): Pair<Int, Int> {
        val xOffset = (srcW - cw) / 2
        val yOffset = when (anchor) {
            CropAnchor.CENTER -> (srcH - ch) / 2
            CropAnchor.TOP -> 0
            CropAnchor.BOTTOM -> (srcH - ch)
            CropAnchor.UNKNOWN -> (srcH - ch) / 2
        }
        return xOffset to yOffset
    }

    /*
     * Half-pixel center mapping: returns the base source index and
     * the fractional blend weight toward the next index.
     */
    private fun mapHalfPixel(
        outCoord: Int,
        srcSize: Int,
        dstSize: Int
    ): Pair<Int, Double> {
        if (srcSize == dstSize) {
            return outCoord to 0.0
        }
        val srcCoord = (outCoord + 0.5) * srcSize / dstSize - 0.5
        val clamped = srcCoord.coerceIn(0.0, srcSize - 1.0)
        val base = Math.floor(clamped).toInt()
        val next = (base + 1).coerceAtMost(srcSize - 1)
        val weight = clamped - base
        return base to if (next == base) 0.0 else weight
    }

    /*
     * Per-channel bilinear sample of an ARGB pixel. Alpha is
     * interpolated like the color channels so a masked region stays
     * masked (no restoration of masked content).
     */
    private fun sampleBilinear(
        src: IntArray,
        srcW: Int,
        srcH: Int,
        x0: Int,
        y0: Int,
        wx: Double,
        wy: Double
    ): Int {
        val x1 = (x0 + 1).coerceAtMost(srcW - 1)
        val y1 = (y0 + 1).coerceAtMost(srcH - 1)
        val p00 = src[y0 * srcW + x0]
        val p10 = src[y0 * srcW + x1]
        val p01 = src[y1 * srcW + x0]
        val p11 = src[y1 * srcW + x1]

        val a = (p00 ushr 24) and 0xFF
        val b = (p00 ushr 16) and 0xFF
        val c = (p00 ushr 8) and 0xFF
        val d = p00 and 0xFF

        val a2 = (p10 ushr 24) and 0xFF
        val b2 = (p10 ushr 16) and 0xFF
        val c2 = (p10 ushr 8) and 0xFF
        val d2 = p10 and 0xFF

        val a3 = (p01 ushr 24) and 0xFF
        val b3 = (p01 ushr 16) and 0xFF
        val c3 = (p01 ushr 8) and 0xFF
        val d3 = p01 and 0xFF

        val a4 = (p11 ushr 24) and 0xFF
        val b4 = (p11 ushr 16) and 0xFF
        val c4 = (p11 ushr 8) and 0xFF
        val d4 = p11 and 0xFF

        val w00 = (1 - wx) * (1 - wy)
        val w10 = wx * (1 - wy)
        val w01 = (1 - wx) * wy
        val w11 = wx * wy

        fun blend(v00: Int, v10: Int, v01: Int, v11: Int): Int =
            Math.round(v00 * w00 + v10 * w10 + v01 * w01 + v11 * w11).toInt()

        return (blend(a, a2, a3, a4) shl 24) or
            (blend(b, b2, b3, b4) shl 16) or
            (blend(c, c2, c3, c4) shl 8) or
            blend(d, d2, d3, d4)
    }
}