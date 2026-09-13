package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-10.
 *
 * Privacy region taxonomy.
 *
 * A controlled vocabulary of the categories of sensitive
 * content that FeedSense may encounter on a smartphone
 * screen. The taxonomy is intentionally small:
 *
 *   - SYSTEM_UI
 *   - NOTIFICATION
 *   - PRIVATE_TEXT
 *   - PERSONAL_IDENTIFIER
 *   - PERSONAL_IMAGE
 *   - SENSITIVE_APPLICATION_UI
 *   - LOCATION_INFORMATION
 *   - UNKNOWN_SENSITIVE_REGION
 *
 * Design rules:
 *   - A region type describes WHAT is sensitive, not WHY.
 *   - Multiple signals may contribute to a single region
 *     (see PrivacyRegion.signals).
 *   - PRIVATE information is distinct from RESEARCH-RELEVANT
 *     content; the sanitizer must never blindly treat all
 *     OCR text as sensitive (e.g. "IPL"/"YouTube"/"Netflix"
 *     are research content, not private data).
 *   - UNKNOWN_SENSITIVE_REGION is the conservative fallback:
 *     a region appears potentially sensitive but cannot be
 *     classified confidently.
 *
 * Privacy status is metadata, NOT a content category. This
 * taxonomy never touches the FeedSense category schema.
 */
enum class PrivacyRegionType(
    val label: String,
    val contentClass: PrivacyContentClass
) {

    /*
     * Status bar, notification shade, system overlays,
     * navigation/system UI chrome.
     */
    SYSTEM_UI(
        "SYSTEM_UI",
        PrivacyContentClass.PRIVATE_INFORMATION
    ),

    /*
     * Notification previews, banners, incoming messages,
     * heads-up cards.
     */
    NOTIFICATION(
        "NOTIFICATION",
        PrivacyContentClass.PRIVATE_INFORMATION
    ),

    /*
     * Chat text, private comments, direct messages,
     * personal correspondence visible on screen.
     */
    PRIVATE_TEXT(
        "PRIVATE_TEXT",
        PrivacyContentClass.PRIVATE_INFORMATION
    ),

    /*
     * Email addresses, phone numbers, usernames where
     * appropriate, account identifiers, payment-card-like
     * sequences.
     */
    PERSONAL_IDENTIFIER(
        "PERSONAL_IDENTIFIER",
        PrivacyContentClass.PRIVATE_INFORMATION
    ),

    /*
     * Profile photographs, private photographs, faces in
     * personal context.
     */
    PERSONAL_IMAGE(
        "PERSONAL_IMAGE",
        PrivacyContentClass.PRIVATE_INFORMATION
    ),

    /*
     * Banking, password interfaces, payment entry, auth
     * screens.
     */
    SENSITIVE_APPLICATION_UI(
        "SENSITIVE_APPLICATION_UI",
        PrivacyContentClass.PRIVATE_INFORMATION
    ),

    /*
     * Maps, location labels, street addresses, GPS-derived
     * screen content.
     */
    LOCATION_INFORMATION(
        "LOCATION_INFORMATION",
        PrivacyContentClass.PRIVATE_INFORMATION
    ),

    /*
     * Conservative fallback: the region appears potentially
     * sensitive but cannot be classified confidently.
     */
    UNKNOWN_SENSITIVE_REGION(
        "UNKNOWN_SENSITIVE_REGION",
        PrivacyContentClass.PRIVATE_INFORMATION
    );

    companion object {

        /*
         * All valid region types. Use this in validators so
         * the taxonomy is a single source of truth.
         */
        val ALL: Set<PrivacyRegionType> =
            entries.toSet()

        private val BY_LABEL: Map<String, PrivacyRegionType> =
            entries.associateBy { it.label }

        /*
         * Parses a stored label back into a region type.
         * Unknown labels are mapped to the conservative
         * UNKNOWN_SENSITIVE_REGION fallback rather than
         * throwing, so version migration never crashes.
         */
        fun fromLabel(label: String?): PrivacyRegionType {
            return BY_LABEL[label] ?: UNKNOWN_SENSITIVE_REGION
        }
    }
}

/*
 * Whether a detected region is private information or
 * research-relevant content.
 *
 * The sanitizer's goal is:
 *   REMOVE UNRELATED PRIVATE INFORMATION
 * while preserving:
 *   CONTENT CLASSIFICATION EVIDENCE
 */
enum class PrivacyContentClass {
    PRIVATE_INFORMATION,
    RESEARCH_RELEVANT_CONTENT,
    UNKNOWN
}