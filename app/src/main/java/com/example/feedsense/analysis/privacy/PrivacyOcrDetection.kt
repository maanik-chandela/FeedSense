package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-13.
 *
 * A single OCR line destined for privacy detection: the text and,
 * when the OCR engine could bound it, its normalized geometry.
 * The text is a TRANSIENT value consumed by detection only; it
 * must never be stored beyond the detector call (spec §21).
 */
data class OcrLine(
    val text: String,
    val bounds: ProtectedRegion? = null
)

/*
 * Milestone 8B-13.
 *
 * Bridges the 8B-10 SensitivePatternDetector into the privacy
 * processor WITHOUT duplicating detection logic (spec §27).
 *
 * Detects explicit private forms (email, phone, card, account)
 * inside OCR lines and emits PERSONAL_IDENTIFIER regions for the
 * processor to transform. The detection is line-atomic: a single
 * line with any private pattern yields ONE region, so a subtitle
 * containing an email address is handled as a unit.
 *
 * URL matches are OPT-IN (policy-gated). Research-relevant
 * content such as "IPL", "YouTube" or "Netflix" is never a
 * pattern and never flagged.
 */
object PrivacyOcrDetection {

    private val DEFAULT_DETECTOR = SensitivePatternDetector()

    fun detectPrivateText(
        ocrLines: List<OcrLine>,
        includeUrls: Boolean = false,
        detector: SensitivePatternDetector = DEFAULT_DETECTOR,
        fallbackBounds: ProtectedRegion? = null
    ): List<PrivacyRegion> {
        val regions = mutableListOf<PrivacyRegion>()
        for (line in ocrLines) {
            val matches = detector.findMatches(line.text)
            val privateMatch = matches.firstOrNull {
                includeUrls || it.kind != SensitivePatternKind.URL
            }
            if (privateMatch == null) continue
            regions += PrivacyRegion(
                type = PrivacyRegionType.PERSONAL_IDENTIFIER,
                bounds = line.bounds ?: fallbackBounds
                    ?: UNKNOWN_BOUNDS,
                signals = listOf(
                    PrivacyDetectionSignal.OCR_TEXT_PATTERN
                ),
                confidence = 1.0
            )
        }
        return regions
    }

    /*
     * When OCR cannot bound a line, the conservative fallback is
     * the entire frame: masking it still protects the identifier
     * without leaking position.
     */
    private val UNKNOWN_BOUNDS = ProtectedRegion(
        x = 0.0, y = 0.0, width = 1.0, height = 1.0,
        label = "UNKNOWN_OCR_EXTENT"
    )
}