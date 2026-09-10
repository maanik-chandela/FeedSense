package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-10.
 *
 * Sensitive pattern kinds.
 *
 * Only explicitly private forms are treated as sensitive.
 * RESEARCH-RELEVANT content (e.g. "IPL", "RCB", "YouTube",
 * "Netflix") is never a pattern.
 */
enum class SensitivePatternKind(val label: String) {
    EMAIL_ADDRESS("EMAIL_ADDRESS"),
    PHONE_NUMBER("PHONE_NUMBER"),
    PAYMENT_CARD("PAYMENT_CARD"),
    ACCOUNT_ID("ACCOUNT_ID"),
    URL("URL")
}

/*
 * A single pattern match within an OCR string. Immutable and
 * deterministic (matches are returned in text order).
 */
data class SensitivePatternMatch(
    val kind: SensitivePatternKind,
    val start: Int,
    val end: Int
) {

    val matchLength: Int
        get() = end - start
}

/*
 * Milestone 8B-10.
 *
 * Deterministic privacy-pattern detector for OCR text.
 *
 * Conservative by design:
 *   - Only explicit identifier forms are matched (email,
 *     phone-number-like, payment-card-like, account IDs,
 *     and OPT-IN urls).
 *   - Contests such as "IPL 2026", "RCB", "YouTube",
 *     "Netflix" are NOT matched, because they are research
 *     content, not private identifiers.
 *   - The detector returns positions, never the matched
 *     text, so callers that only need statistics never
 *     touch sensitive content.
 *
 * This detector is deliberately NOT an OCR-free guarantee:
 * content OCR misses is content this detector never sees.
 */
class SensitivePatternDetector {

    /*
     * Emails: x@y.tld. The safest, most unambiguous private
     * identifier form on a screen.
     */
    private val emailRegex = Regex(
        "[A-Z0-9._%+\\-]+@[A-Z0-9.\\-]+\\.[A-Z]{2,}",
        RegexOption.IGNORE_CASE
    )

    /*
     * Telephone-like: at least one leading digit, digits and
     * separators, at least 6 digits total. Kept conservative
     * so year strings like "2026" are NOT phone numbers.
     */
    private val phoneRegex = Regex(
        "\\+?[0-9][0-9 (){}\\.+\\-]{6,}[0-9]"
    )

    /*
     * Payment-card-like: 13-19 consecutive digits, or groups
     * of 3-4 digits with separators (' ', '-'), >12 digits.
     */
    private val cardRegex = Regex(
        "\\b(?:[0-9]{4}[ -]?){3,}[0-9]{0,3}\\b"
    )

    /*
     * Account-id-like: heavily conservative. Only used as a
     * fallback signal, never the sole basis for a decision.
     */
    private val accountRegex = Regex(
        "(?i)\\b(?:acc\\s?no|account\\s*\\#)\\s*:?\\s*[A-Z0-9\\-]{4,}"
    )

    /*
     * URLs. NOT matched by default: profile-less URLs are
     * frequently research-relevant evidence (e.g. "youtube").
     * Redaction of URLs requires the policy to explicitly
     * enable them.
     */
    private val urlRegex = Regex(
        "\\b(?:https?://|www\\.)?[a-z0-9\\-]+\\.[a-z]{2,}[\\w./?=&#%\\-]*",
        RegexOption.IGNORE_CASE
    )

    private val detectors: List<Pair<SensitivePatternKind, Regex>> =
        listOf(
            SensitivePatternKind.EMAIL_ADDRESS to emailRegex,
            SensitivePatternKind.PHONE_NUMBER to phoneRegex,
            SensitivePatternKind.PAYMENT_CARD to cardRegex,
            SensitivePatternKind.ACCOUNT_ID to accountRegex,
            SensitivePatternKind.URL to urlRegex
        )

    /*
     * Returns all matched spans in text order. Deterministic.
     */
    fun findMatches(text: String): List<SensitivePatternMatch> {
        val matches = mutableListOf<SensitivePatternMatch>()
        for ((kind, regex) in detectors) {
            regex.findAll(text).forEach { result ->
                matches += SensitivePatternMatch(
                    kind = kind,
                    start = result.range.first,
                    end = result.range.last + 1
                )
            }
        }
        matches.sortBy { it.start }
        return matches
    }

    /*
     * Returns the first private-kind match index, or -1.
     */
    fun firstMatchIndex(text: String): Int {
        return findMatches(text)
            .firstOrNull { it.kind.isPrivateDefault() }
            ?.start
            ?: -1
    }

    private fun SensitivePatternKind.isPrivateDefault(): Boolean {
        return this != SensitivePatternKind.URL
    }
}