package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-10.
 *
 * The token used to replace sensitive text segments in OCR
 * evidence. Never contains a hint of the original value.
 */
const val REDACTION_TOKEN = "[REDACTED]"

/*
 * Result of redacting OCR text. Immutable.
 */
data class TextRedactionResult(
    val redacted: String,
    val segmentsRedacted: Int,
    val redactionVersion: String = PrivacySanitizationVersion.REDACTION
)

/*
 * Milestone 8B-10.
 *
 * Versioned OCR text redactor.
 *
 * Replaces sensitive pattern spans with [REDACTED]. The
 * redactor interacts ONLY with a SensitivePatternDetector;
 * it has no opinion about semantics and never stores raw
 * text.
 *
 * Deterministic: for the same input text and policy the
 * output and segment count are identical.
 *
 * Preservation rule: only explicit identifier forms are
 * redacted. Research-relevant content ("IPL", "RCB",
 * "YouTube", "Netflix") passes through untouched.
 */
class PrivacyTextRedactor(
    private val detector: SensitivePatternDetector =
        SensitivePatternDetector()
) {

    /*
     * Redacts the supplied OCR text per policy. When the
     * policy disables identifier redaction the text is
     * returned unchanged with segmentsRedacted = 0.
     */
    fun redact(
        text: String,
        policy: PrivacyPolicy = PrivacyPolicy.RESEARCH
    ): TextRedactionResult {

        if (!policy.sanitizePersonalIdentifiers) {
            return TextRedactionResult(
                redacted = text,
                segmentsRedacted = 0
            )
        }

        val matches = detector.findMatches(text)
        val enabled = matches.filter { match ->
            policy.isRedactionAllowed(match.kind)
        }

        if (enabled.isEmpty()) {
            return TextRedactionResult(
                redacted = text,
                segmentsRedacted = 0
            )
        }

        val builder = StringBuilder(text.length)
        var cursor = 0
        for (match in enabled) {
            if (match.start < cursor) continue
            builder.append(text, cursor, match.start)
            builder.append(REDACTION_TOKEN)
            cursor = match.end
        }
        builder.append(text, cursor, text.length)

        return TextRedactionResult(
            redacted = builder.toString(),
            segmentsRedacted = enabled.size
        )
    }

    /*
     * Count of sensitive segments in OCR text (statistics
     * without raw content).
     */
    fun countSensitiveSegments(
        text: String,
        policy: PrivacyPolicy = PrivacyPolicy.RESEARCH
    ): Int {
        val matches = detector.findMatches(text)
        return matches.count { policy.isRedactionAllowed(it.kind) }
    }

    /*
     * Whether a given segment kind is redacted under the
     * policy. URLs are redacted only when the policy says so
     * (they are usually research-relevant evidence).
     */
    fun PrivacyPolicy.isRedactionAllowed(
        kind: SensitivePatternKind
    ): Boolean {
        return when (kind) {
            SensitivePatternKind.EMAIL_ADDRESS,
            SensitivePatternKind.PHONE_NUMBER,
            SensitivePatternKind.PAYMENT_CARD,
            SensitivePatternKind.ACCOUNT_ID -> sanitizePersonalIdentifiers
            SensitivePatternKind.URL -> false
        }
    }
}