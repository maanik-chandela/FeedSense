package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-4.
 *
 * Privacy sanitization configuration.
 *
 * Immutable configuration object. All policy decisions
 * are explicit and versioned for research reproducibility.
 *
 * Design principles:
 *   - Policy lives in config, not implementation code
 *   - Versioned for reproducibility
 *   - Validated at construction
 *   - Presets for common configurations
 *
 * Limitations:
 *   - Initial implementation supports geometric
 *     regions only
 *   - Automatic content detection is not yet implemented
 */
data class PrivacySanitizationConfig(
    val enabled: Boolean = true,
    val protectedRegions: List<ProtectedRegion> =
        DEFAULT_REGIONS,
    val redactionMode: RedactionMode =
        RedactionMode.REDACT,
    val blurStrength: Int = 25,
    val sanitizationVersion: String =
        SANITIZATION_VERSION,
    val maxUncertainRetries: Int = 0
) {
    init {
        require(blurStrength in 1..100) {
            "blurStrength must be in [1,100], " +
                    "got $blurStrength"
        }
        require(maxUncertainRetries >= 0) {
            "maxUncertainRetries must be >= 0, " +
                    "got $maxUncertainRetries"
        }
    }

    val regionCount: Int
        get() = protectedRegions.size

    val regionLabels: List<String>
        get() = protectedRegions.map { it.label }

    companion object {

        const val SANITIZATION_VERSION = "privacy-v1"

        /*
         * Default regions: status bar + notification
         * area + navigation bar.
         */
        val DEFAULT_REGIONS = listOf(
            ProtectedRegion.STATUS_BAR,
            ProtectedRegion.NOTIFICATION_REGION,
            ProtectedRegion.NAVIGATION_BAR
        )

        /*
         * Production configuration.
         * Enabled with default regions and opaque
         * redaction.
         */
        val DEFAULT = PrivacySanitizationConfig()

        /*
         * Disabled configuration. Passes frames through
         * unchanged. Useful for testing or when privacy
         * protection is temporarily suspended.
         */
        val DISABLED = PrivacySanitizationConfig(
            enabled = false,
            protectedRegions = emptyList()
        )

        /*
         * Conservative configuration. Additional
         * protected regions with stronger redaction.
         */
        val CONSERVATIVE = PrivacySanitizationConfig(
            protectedRegions = listOf(
                ProtectedRegion.STATUS_BAR,
                ProtectedRegion.NOTIFICATION_REGION,
                ProtectedRegion.NAVIGATION_BAR,
                ProtectedRegion(
                    x = 0.0, y = 0.0,
                    width = 1.0, height = 0.20,
                    label = "TOP_EXTENDED"
                ),
                ProtectedRegion(
                    x = 0.0, y = 0.80,
                    width = 1.0, height = 0.20,
                    label = "BOTTOM_EXTENDED"
                )
            ),
            redactionMode = RedactionMode.REDACT,
            blurStrength = 50
        )
    }
}

/*
 * Redaction mode determines how sensitive regions
 * are transformed.
 *
 * REDACT: Opaque solid-color overlay. Text is
 *   substantially unrecoverable.
 *
 * BLUR: Gaussian blur. Strength is configurable
 *   via blurStrength. May not fully prevent text
 *   recovery at low strengths.
 *
 * NONE: No transformation applied. Regions are
 *   marked but not modified. Useful for testing
 *   or audit-only modes.
 */
enum class RedactionMode {
    REDACT,
    BLUR,
    NONE
}
