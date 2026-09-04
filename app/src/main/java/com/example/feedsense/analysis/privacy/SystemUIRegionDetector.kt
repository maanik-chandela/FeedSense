package com.example.feedsense.analysis.privacy

import java.io.File

/*
 * Milestone 8B-4.
 *
 * System UI region detector.
 *
 * Identifies common Android system UI regions based
 * on known geometry and configurable proportions.
 *
 * Protected areas:
 *   - Status bar (top system region)
 *   - Navigation bar (bottom system region)
 *   - Notification area (below status bar)
 *
 * This detector uses geometry only, not OCR.
 * The initial system-region protection is based on:
 *   - Known system/UI geometry
 *   - Configurable regions
 *   - Capture metadata where available
 *
 * Do NOT make privacy protection dependent on OCR.
 * If OCR receives the frame before sanitization,
 * the privacy boundary is already broken.
 *
 * Limitations:
 *   - Assumes standard Android navigation bar position
 *   - Does not detect app-specific overlays
 *   - Does not detect floating windows
 *   - Portrait/landscape not distinguished
 *   - Device-specific nav bar height varies
 */
class SystemUIRegionDetector(
    private val statusBarHeightFraction: Double =
        0.04,
    private val notificationHeightFraction: Double =
        0.12,
    private val navigationHeightFraction: Double =
        0.05,
    private val includeStatusBar: Boolean = true,
    private val includeNotification: Boolean = true,
    private val includeNavigation: Boolean = true
) : PrivacyRegionDetector {

    override fun detect(
        frameFile: File,
        frameWidth: Int,
        frameHeight: Int
    ): List<ProtectedRegion> {

        val regions =
            mutableListOf<ProtectedRegion>()

        if (includeStatusBar) {
            regions.add(
                ProtectedRegion(
                    x = 0.0,
                    y = 0.0,
                    width = 1.0,
                    height = statusBarHeightFraction,
                    label = "STATUS_BAR"
                )
            )
        }

        if (includeNotification) {
            regions.add(
                ProtectedRegion(
                    x = 0.0,
                    y = statusBarHeightFraction,
                    width = 1.0,
                    height =
                        notificationHeightFraction,
                    label = "NOTIFICATION"
                )
            )
        }

        if (includeNavigation) {
            regions.add(
                ProtectedRegion(
                    x = 0.0,
                    y = 1.0 -
                            navigationHeightFraction,
                    width = 1.0,
                    height = navigationHeightFraction,
                    label = "NAVIGATION"
                )
            )
        }

        return regions
    }
}
