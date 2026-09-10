package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-13.
 *
 * Independent privacy-detection confidence.
 *
 * Privacy confidence describes how certain the privacy layer is
 * that a region really is sensitive. It is DELIBERATELY
 * independent of AI prediction confidence (spec §9): a privacy
 * decision must never inherit a classifier's self-assessment.
 *
 * A score in [0,1] is mapped to a controlled label with fixed
 * thresholds so identical evidence always maps to the same
 * label (determinism).
 *
 *   NONE   0.0                  - no privacy-relevant signal.
 *   LOW    (0.0, 0.4]           - weak/fallback signal only.
 *   MEDIUM (0.4, 0.7]           - plausible but not conclusive.
 *   HIGH   (0.7, 1.0]           - strong, versioned detection.
 */
enum class PrivacyConfidence(val label: String) {

    NONE("NONE"),
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH");

    companion object {

        const val HIGH_AT = 0.7
        const val MEDIUM_AT = 0.4

        /*
         * Maps a deterministic score in [0,1] to a label.
         * Out-of-range scores are rejected; the caller is
         * expected to have verified the bounds.
         */
        fun fromScore(score: Double): PrivacyConfidence {
            require(score in 0.0..1.0) {
                "score must be in [0,1], got $score"
            }
            return when {
                score <= 0.0 -> NONE
                score <= MEDIUM_AT -> LOW
                score <= HIGH_AT -> MEDIUM
                else -> HIGH
            }
        }

        /*
         * The confidence implied by a single detection signal
         * (spec §8). Fixed-region / system-UI geometry is
         * deterministic and cheap, so it is treated as high;
         * OCR text patterns are strong for unambiguous forms
         * (email, phone) and moderate for account-like forms;
         * a keyword or low-confidence fallback is weaker.
         */
        fun forSignal(signal: PrivacyDetectionSignal): PrivacyConfidence {
            return when (signal) {
                PrivacyDetectionSignal.FIXED_REGION,
                PrivacyDetectionSignal.SYSTEM_UI_STRUCTURE -> HIGH
                PrivacyDetectionSignal.OCR_TEXT_PATTERN -> MEDIUM
                PrivacyDetectionSignal.PRIVACY_SENSITIVE_KEYWORD -> MEDIUM
                PrivacyDetectionSignal.NOTIFICATION_STYLE -> MEDIUM
                PrivacyDetectionSignal.SENSITIVE_APP_STRUCTURE -> MEDIUM
                PrivacyDetectionSignal.APPLICATION_CONTEXT -> HIGH
                PrivacyDetectionSignal.LOW_CONFIDENCE_FALLBACK -> LOW
            }
        }
    }
}

/*
 * Milestone 8B-13.
 *
 * Aggregate privacy confidence of a frame: the strongest
 * per-region confidence of all detected regions. No regions and
 * no OCR is NONE; a single strong detection dominates.
 */
object PrivacyConfidenceAggregator {

    fun aggregate(
        regions: List<PrivacyRegion>,
        ocrAvailability: OcrAvailability
    ): PrivacyConfidence {
        if (regions.isEmpty()) {
            return when (ocrAvailability) {
                OcrAvailability.OCR_AVAILABLE -> PrivacyConfidence.NONE
                OcrAvailability.OCR_UNAVAILABLE -> PrivacyConfidence.NONE
            }
        }
        val strongest = regions.maxOf {
            PrivacyConfidence.forSignal(it.signals.firstOrDefault())
        }
        return strongest
    }

    /*
     * The signal that will be reported if a region lists
     * several; ordering is fixed by the region author.
     */
    private fun List<PrivacyDetectionSignal>.firstOrDefault() =
        firstOrNull() ?: PrivacyDetectionSignal.LOW_CONFIDENCE_FALLBACK
}