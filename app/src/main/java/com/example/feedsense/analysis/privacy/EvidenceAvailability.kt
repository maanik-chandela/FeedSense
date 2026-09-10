package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-10.
 *
 * Screen-content availability at capture time.
 *
 * Distinguishes "capture was blocked because the OS told us
 * the screen is security-sensitive" from "capture ran but
 * found nothing to sanitize". This distinction is required
 * because FLAG_SECURE surfaces produce NO_TYPE / empty
 * frames that must not be confused with clean frames.
 */
enum class EvidenceAvailability(val label: String) {

    /*
     * The screen contained analyzable content.
     */
    CONTENT_DETECTED("CONTENT_DETECTED"),

    /*
     * Capture produced an empty / unanalyzable frame and no
     * sensitive content was detected.
     */
    NO_CONTENT_DETECTED("NO_CONTENT_DETECTED"),

    /*
     * Capture was blocked by the OS (e.g. FLAG_SECURE
     * surface such as a payment or DRM view).
     */
    CAPTURE_BLOCKED("CAPTURE_BLOCKED"),

    /*
     * The availability could not be determined.
     */
    UNKNOWN_AVAILABILITY("UNKNOWN_AVAILABILITY");

    /*
     * Whether a blocked capture should be treated as a
     * privacy success (nothing sensitive leaked) rather than
     * an empty feed. Only CAPTURE_BLOCKED conveys this.
     */
    val isCaptureIndisposed: Boolean
        get() = this == CAPTURE_BLOCKED

    companion object {
        /*
         * Maps a geometric rasterizer outcome to availability.
         */
        fun fromRasterization(
            changed: Boolean,
            truncated: Boolean
        ): EvidenceAvailability {
            return when {
                truncated -> CAPTURE_BLOCKED
                !changed -> NO_CONTENT_DETECTED
                else -> CONTENT_DETECTED
            }
        }
    }
}