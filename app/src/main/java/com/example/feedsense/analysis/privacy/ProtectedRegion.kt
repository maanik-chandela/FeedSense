package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-4.
 *
 * Normalized privacy region.
 *
 * Represents a rectangular area using coordinates in [0,1]
 * so the same rule works across different resolutions,
 * aspect ratios, and orientations.
 *
 * The actual pixel bounds are computed at sanitization time
 * based on the frame dimensions.
 *
 * Usage:
 *   val region = ProtectedRegion(
 *       x = 0.0, y = 0.0,
 *       width = 1.0, height = 0.05,
 *       label = "STATUS_BAR"
 *   )
 *   val pixels = region.toPixelBounds(
 *       frameWidth = 1080, frameHeight = 2400
 *   )
 *
 * Limitations:
 *   - Normalized coordinates cannot represent sub-pixel
 *     precision
 *   - Regions are axis-aligned (no rotation support)
 *   - Overlapping regions are allowed but not merged
 */
data class ProtectedRegion(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val label: String
) {
    init {
        require(x in 0.0..1.0) {
            "x must be in [0,1], got $x"
        }
        require(y in 0.0..1.0) {
            "y must be in [0,1], got $y"
        }
        require(width in 0.0..1.0) {
            "width must be in [0,1], got $width"
        }
        require(height in 0.0..1.0) {
            "height must be in [0,1], got $height"
        }
        require(x + width <= 1.0 + EPSILON) {
            "x + width must be <= 1.0, " +
                    "got ${x + width}"
        }
        require(y + height <= 1.0 + EPSILON) {
            "y + height must be <= 1.0, " +
                    "got ${y + height}"
        }
        require(label.isNotBlank()) {
            "label must not be blank"
        }
    }

    /*
     * Convert normalized coordinates to pixel bounds
     * for a given frame size.
     *
     * Returns PixelBounds with integer coordinates
     * clamped to frame dimensions.
     */
    fun toPixelBounds(
        frameWidth: Int,
        frameHeight: Int
    ): PixelBounds {

        val left =
            (x * frameWidth).toInt()
                .coerceIn(0, frameWidth)

        val top =
            (y * frameHeight).toInt()
                .coerceIn(0, frameHeight)

        val right =
            ((x + width) * frameWidth).toInt()
                .coerceIn(0, frameWidth)

        val bottom =
            ((y + height) * frameHeight).toInt()
                .coerceIn(0, frameHeight)

        return PixelBounds(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            label = label
        )
    }

    companion object {

        private const val EPSILON = 1e-9

        /*
         * Top status bar: typically top 4-5% of screen.
         */
        val STATUS_BAR = ProtectedRegion(
            x = 0.0,
            y = 0.0,
            width = 1.0,
            height = 0.04,
            label = "STATUS_BAR"
        )

        /*
         * Notification shade: below status bar.
         * Covers approximately top 8-15% when expanded.
         * Conservative default for notification content.
         */
        val NOTIFICATION_REGION = ProtectedRegion(
            x = 0.0,
            y = 0.04,
            width = 1.0,
            height = 0.12,
            label = "NOTIFICATION"
        )

        /*
         * Bottom navigation / system gesture area.
         */
        val NAVIGATION_BAR = ProtectedRegion(
            x = 0.0,
            y = 0.95,
            width = 1.0,
            height = 0.05,
            label = "NAVIGATION"
        )
    }
}

/*
 * Integer pixel bounds for a protected region.
 */
data class PixelBounds(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    val label: String
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    val isValid: Boolean
        get() = left < right && top < bottom

    /*
     * Check if this region overlaps with another.
     */
    fun overlaps(other: PixelBounds): Boolean {
        return left < other.right &&
                right > other.left &&
                top < other.bottom &&
                bottom > other.top
    }
}
