package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-10.
 *
 * Sanitization operating mode.
 */
enum class PrivacyMode(val label: String) {

    /*
     * Default. Conservative, research-safe defaults that
     * protect private information while preserving
     * content-classification evidence.
     */
    RESEARCH_MODE("RESEARCH_MODE"),

    /*
     * Developer diagnostics. Still NEVER logs raw content,
     * but allows relaxed region/discovery toggles.
     */
    DEBUG_MODE("DEBUG_MODE")
}

/*
 * Milestone 8B-10.
 *
 * Versioned privacy policy.
 *
 * A PrivacyPolicy is an immutable, versioned description of
 * how evidence sanitization should behave. Policies are
 * conservative by default:
 *
 *   - sanitize notifications            true
 *   - sanitize system UI                true
 *   - sanitize personal identifiers     true
 *   - sanitize private text             true
 *   - sanitize personal images          true
 *   - sanitize sensitive app UI         true
 *   - sanitize location information     true
 *   - block on sanitization failure     false
 *     (capture continues instead)
 *   - retain raw frames                 true
 *   - retain sanitized frames           true
 *   - export raw research content       false
 *
 * IMPORTANT: the policy NEVER claims that its output is
 * "fully anonymized". See PrivacyPolicyLimitations.
 *
 * The generic form is deliberately simple so it can be
 * serialized and versioned; the RESEARCH preset is the
 * production fallback.
 */
data class PrivacyPolicy(
    val policyVersion: String = PrivacySanitizationVersion.POLICY,
    val mode: PrivacyMode = PrivacyMode.RESEARCH_MODE,
    val policyMode: PrivacyPolicyMode = PrivacyPolicyMode.RESEARCH,
    val sanitizeNotifications: Boolean = true,
    val sanitizeSystemUi: Boolean = true,
    val sanitizePersonalIdentifiers: Boolean = true,
    val sanitizePrivateText: Boolean = true,
    val sanitizePersonalImages: Boolean = true,
    val sanitizeSensitiveAppUi: Boolean = true,
    val sanitizeLocation: Boolean = true,
    val blockOnSanitizationFailure: Boolean = false,
    val retainRawFrames: Boolean = true,
    val retainSanitizedFrames: Boolean = true,
    val allowRawResearchExport: Boolean = false,
    val redactionMode: RedactionMode = RedactionMode.REDACT,
    val blurStrength: Int = 25
) {

    init {
        require(blurStrength in 0..100) {
            "blurStrength must be in [0,100], got $blurStrength"
        }
    }

    /*
     * Whether the given region type falls under this policy.
     * When disabled the region is left untouched and the
     * decision is attributed to the policy (deterministic).
     */
    fun isSanitizationEnabled(
        regionType: PrivacyRegionType
    ): Boolean {
        return when (regionType) {
            PrivacyRegionType.NOTIFICATION -> sanitizeNotifications
            PrivacyRegionType.SYSTEM_UI -> sanitizeSystemUi
            PrivacyRegionType.PERSONAL_IDENTIFIER -> sanitizePersonalIdentifiers
            PrivacyRegionType.PRIVATE_TEXT -> sanitizePrivateText
            PrivacyRegionType.PERSONAL_IMAGE -> sanitizePersonalImages
            PrivacyRegionType.SENSITIVE_APPLICATION_UI -> sanitizeSensitiveAppUi
            PrivacyRegionType.LOCATION_INFORMATION -> sanitizeLocation
            PrivacyRegionType.UNKNOWN_SENSITIVE_REGION -> {
                // Conservative: untrusted regions are handled
                // whenever any text/UI sanitization is active.
                sanitizePrivateText || sanitizeSensitiveAppUi
            }
        }
    }

    companion object {

        val RESEARCH: PrivacyPolicy = PrivacyPolicy()

        val BALANCED: PrivacyPolicy =
            PrivacyPolicy(policyMode = PrivacyPolicyMode.BALANCED)

        val STRICT: PrivacyPolicy =
            PrivacyPolicy(policyMode = PrivacyPolicyMode.STRICT)

        val DEBUG: PrivacyPolicy =
            PrivacyPolicy(mode = PrivacyMode.DEBUG_MODE)

        /*
         * Fully disabled policy: every region class is left
         * untouched. Used as the "privacy disabled" control in
         * the 8B-13 benchmark (spec §48).
         */
        val DISABLED: PrivacyPolicy = PrivacyPolicy(
            policyMode = PrivacyPolicyMode.RESEARCH,
            sanitizeNotifications = false,
            sanitizeSystemUi = false,
            sanitizePersonalIdentifiers = false,
            sanitizePrivateText = false,
            sanitizePersonalImages = false,
            sanitizeSensitiveAppUi = false,
            sanitizeLocation = false
        )
    }
}

/*
 * Honest limitations of privacy sanitization. These are
 * surfaced in documentation and logs so FeedSense never
 * overstates its privacy guarantees.
 */
object PrivacyPolicyLimitations {

    /*
     * Sanitization is irreversible once applied to a frame;
     * raw frames retained on device are the ONLY lossless
     * copy and must be treated as sensitive.
     */
    val isLossy = true

    /*
     * Detection is image-based; adversarial layouts can hide
     * content from the sanitizer.
     */
    val adversarialContentPossible = true

    /*
     * OCR never has perfect coverage; text missed by OCR is
     * never pattern-matched (and thus never redacted).
     */
    val ocrCoverageIsPartial = true

    /*
     * Sanitization does NOT secure against screen-recording
     * screen share or other OS-level captures.
     */
    val doesNotPreventLegalOscaptures = true
}

/*
 * Production entry point. The RESEARCH preset is the safe
 * default used by capture workers.
 */
fun defaultPrivacyPolicy(): PrivacyPolicy =
    PrivacyPolicy.RESEARCH