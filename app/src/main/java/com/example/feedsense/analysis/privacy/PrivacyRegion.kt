package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-10.
 *
 * Detection signal sources.
 *
 * Sanitization uses LAYERED detection rather than a single
 * heuristic. Multiple signals may contribute to a single
 * privacy decision. Each signal is recorded so the audit
 * trail explains WHAT drove the decision without ever
 * storing the sensitive content itself.
 */
enum class PrivacyDetectionSignal(val label: String) {

    /*
     * A fixed / configured screen region (normalized
     * geometry). Cheapest signal; always available.
     */
    FIXED_REGION("FIXED_REGION"),

    /*
     * Known system UI structure (status bar, navigation
     * bar, notification shade geometry).
     */
    SYSTEM_UI_STRUCTURE("SYSTEM_UI_STRUCTURE"),

    /*
     * OCR text matched a privacy-sensitive pattern
     * (email, phone number, url, ...).
     */
    OCR_TEXT_PATTERN("OCR_TEXT_PATTERN"),

    /*
     * OCR text contained a privacy-sensitive keyword
     * (message, password, payment, ...).
     */
    PRIVACY_SENSITIVE_KEYWORD("PRIVACY_SENSITIVE_KEYWORD"),

    /*
     * Layout resembles a notification card / banner /
     * conversation bubble.
     */
    NOTIFICATION_STYLE("NOTIFICATION_STYLE"),

    /*
     * Layout/context resembles a sensitive application UI
     * (banking, password, payment entry).
     */
    SENSITIVE_APP_STRUCTURE("SENSITIVE_APP_STRUCTURE"),

    /*
     * Application/package context was legitimately
     * available (e.g. MediaProjection metadata, existing
     * app chrome detection).
     */
    APPLICATION_CONTEXT("APPLICATION_CONTEXT"),

    /*
     * Conservative fallback when a region looks sensitive
     * but no specific signal is conclusive.
     */
    LOW_CONFIDENCE_FALLBACK("LOW_CONFIDENCE_FALLBACK")
}

/*
 * Milestone 8B-10.
 *
 * A detected privacy region.
 *
 * Combines a normalized geometry with a privacy region
 * type, the detection signals that contributed, and a
 * confidence in [0,1]. Deterministic: for the same frame,
 * policy and sanitizer version the same regions are
 * produced.
 *
 * This is the DETECTION-level object. The existing
 * ProtectedRegion remains the geometric primitive used by
 * the rasterizer; PrivacyRegion can be converted to it.
 */
data class PrivacyRegion(
    val type: PrivacyRegionType,
    val bounds: ProtectedRegion,
    val signals: List<PrivacyDetectionSignal> =
        listOf(PrivacyDetectionSignal.LOW_CONFIDENCE_FALLBACK),
    val confidence: Double = 1.0
) {

    init {
        require(confidence in 0.0..1.0) {
            "confidence must be in [0,1], got $confidence"
        }
    }

    /*
     * The label used by the rasterizer / audit trail.
     * The type name is the stable key.
     */
    val label: String
        get() = type.label

    /*
     * Whether the detection is confident enough to act on.
     * Low-confidence detections are still surfaced but are
     * never the sole basis for a decision.
     */
    val isConfident: Boolean
        get() = confidence >= MIN_CONFIDENCE

    /*
     * Converts to the geometric ProtectedRegion consumed
     * by the existing rasterizer.
     */
    fun toProtectedRegion(): ProtectedRegion {
        return ProtectedRegion(
            x = bounds.x,
            y = bounds.y,
            width = bounds.width,
            height = bounds.height,
            label = label
        )
    }

    companion object {

        /*
         * Below this confidence a region is still recorded
         * but treated as a fallback signal only.
         */
        const val MIN_CONFIDENCE = 0.5

        /*
         * Builds a PrivacyRegion from an existing geometric
         * ProtectedRegion by mapping its label to the
         * taxonomy. Unknown labels fall back to
         * UNKNOWN_SENSITIVE_REGION.
         */
        fun fromProtectedRegion(
            region: ProtectedRegion
        ): PrivacyRegion {
            val type =
                PrivacyRegionType.fromLabel(region.label)

            return PrivacyRegion(
                type = type,
                bounds = region,
                signals = listOf(
                    PrivacyDetectionSignal.FIXED_REGION
                ),
                confidence = 1.0
            )
        }

        /*
         * Maps the well-known geometric presets to typed
         * regions so the default sanitizer output surfaces
         * region TYPES in the audit.
         */
        fun fromRegionType(
            type: PrivacyRegionType,
            bounds: ProtectedRegion,
            signals: List<PrivacyDetectionSignal>,
            confidence: Double = 1.0
        ): PrivacyRegion {
            return PrivacyRegion(
                type = type,
                bounds = bounds,
                signals = signals,
                confidence = confidence
            )
        }
    }
}

/*
 * The legacy geometric preset mapped to a typed region.
 * Preserves 8B-4 behavior while adding taxonomy typing.
 */
fun ProtectedRegion.toPrivacyRegion(): PrivacyRegion {
    return PrivacyRegion.fromProtectedRegion(this)
}

/*
 * Convenience: fixed system UI regions typed for the audit
 * trail (status bar -> SYSTEM_UI, notification ->
 * NOTIFICATION, navigation -> SYSTEM_UI).
 */
val PRIVACY_SYSTEM_UI_REGIONS: List<PrivacyRegion> =
    listOf(
        PrivacyRegion(
            type = PrivacyRegionType.SYSTEM_UI,
            bounds = ProtectedRegion.STATUS_BAR,
            signals = listOf(
                PrivacyDetectionSignal.FIXED_REGION,
                PrivacyDetectionSignal.SYSTEM_UI_STRUCTURE
            )
        ),
        PrivacyRegion(
            type = PrivacyRegionType.NOTIFICATION,
            bounds = ProtectedRegion.NOTIFICATION_REGION,
            signals = listOf(
                PrivacyDetectionSignal.FIXED_REGION,
                PrivacyDetectionSignal.SYSTEM_UI_STRUCTURE
            )
        ),
        PrivacyRegion(
            type = PrivacyRegionType.SYSTEM_UI,
            bounds = ProtectedRegion.NAVIGATION_BAR,
            signals = listOf(
                PrivacyDetectionSignal.FIXED_REGION,
                PrivacyDetectionSignal.SYSTEM_UI_STRUCTURE
            )
        )
    )