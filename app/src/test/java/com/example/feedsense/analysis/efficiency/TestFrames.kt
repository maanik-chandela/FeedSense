package com.example.feedsense.analysis.efficiency

import java.util.Random

/*
 * Milestone 8B-11.
 *
 * Deterministic frame construction helpers for tests and the
 * controlled engineering corpus.
 *
 * These produce ONLY synthetic images; they never touch device
 * screens, files, or Bitmaps.
 */
object TestFrames {

    /*
     * Uniform gray frame.
     */
    fun solid(width: Int, height: Int, value: Int): RawPixelFrame =
        RawPixelFrame(width, height, IntArray(width * height) { value })

    /*
     * Left half dark / right half light split. With height=1
     * and the split aligned to the box-sample boundary, a
     * hashSize=1 hasher sees exactly one gradient bit (0 or 1),
     * giving an exactly controllable 1-bit hash.
     */
    fun halfSplit(
        width: Int,
        dark: Int = 40,
        light: Int = 200
    ): RawPixelFrame {
        val half = width / 2
        return RawPixelFrame(
            width, 1,
            IntArray(width) { i -> if (i < half) dark else light }
        )
    }

    /*
     * Paints a canonical (cols x rows) cell map at the given
     * pixel cell size, mapping cell i to gray `cells[i]`.
     *
     * cols/rows map 1:1 onto the hasher's canonical grid when
     * `cols == hashSize + 1` and `rows == hashSize`, so each
     * gradient bit is exactly controlled by two neighbouring
     * cell values.
     */
    fun gridFrame(
        cols: Int,
        rows: Int,
        cellSize: Int,
        cells: IntArray
    ): RawPixelFrame {
        require(cells.size == cols * rows)
        val pixels = IntArray(cols * cellSize * rows * cellSize)
        for (gy in 0 until rows) {
            for (gx in 0 until cols) {
                val value = cells[gy * cols + gx]
                for (y in 0 until cellSize) {
                    for (x in 0 until cellSize) {
                        val py = gy * cellSize + y
                        val px = gx * cellSize + x
                        pixels[py * (cols * cellSize) + px] = value
                    }
                }
            }
        }
        return RawPixelFrame(
            cols * cellSize,
            rows * cellSize,
            pixels
        )
    }

    /*
     * Deterministic pseudo-random luminance grid (seeded, so it
     * is reproducible) used as a realistic-but-synthetic
     * "screen" for the controlled corpus.
     */
    fun seededGrid(
        cols: Int,
        rows: Int,
        seed: Long
    ): IntArray {
        val random = Random(seed)
        return IntArray(cols * rows) { random.nextInt(256) }
    }
}