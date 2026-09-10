package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-13.
 *
 * Frame-level privacy RISK (spec §10, §14).
 *
 * A risk level aggregates the detected sensitive region types and
 * OCR availability. It is a RESEARCH diagnostic, not a claim of
 * perfect PII detection, and it is independent of any AI
 * prediction confidence.
 *
 *   NONE    - no sensitive content detected and OCR was seen,
 *             so "nothing to protect" is a defensible claim.
 *   LOW     - only low-sensitivity chrome (system UI) detected.
 *   MEDIUM  - notifications / personal images / location.
 *   HIGH    - private text, identifiers, sensitive app UI, or an
 *             unclassifiable region.
 *   UNKNOWN - we could not determine risk confidently (OCR
 *             unavailable with no other signal). Handled
 *             conservatively; never treated as safe.
 */
enum class PrivacyRisk(val label: String) {

    NONE("NONE"),
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH"),
    UNKNOWN("UNKNOWN");

    val isHighRisk: Boolean
        get() = this == HIGH || this == UNKNOWN
}

/*
 * Milestone 8B-13.
 *
 * Deterministic risk aggregation.
 */
object PrivacyRiskAggregator {

    fun compute(
        regions: List<PrivacyRegion>,
        ocrAvailability: OcrAvailability
    ): PrivacyRisk {
        if (regions.isEmpty()) {
            return when (ocrAvailability) {
                OcrAvailability.OCR_AVAILABLE -> PrivacyRisk.NONE
                OcrAvailability.OCR_UNAVAILABLE -> PrivacyRisk.UNKNOWN
            }
        }

        var highest = PrivacyRisk.LOW
        for (region in regions) {
            val risk = riskFor(region.type)
            if (risk.severity > highest.severity) {
                highest = risk
            }
        }

        // OCR-seen high-risk types stay HIGH; a virtually
        // empty-but-unverified capture surfaces as UNKNOWN only
        // when there is NO signal at all.
        if (highest == PrivacyRisk.UNKNOWN &&
            ocrAvailability == OcrAvailability.OCR_AVAILABLE
        ) {
            return PrivacyRisk.HIGH
        }
        return highest
    }

    /*
     * Baseline risk by region type (controlled, documented).
     */
    private fun riskFor(type: PrivacyRegionType): PrivacyRisk {
        return when (type) {
            PrivacyRegionType.SYSTEM_UI -> PrivacyRisk.LOW
            PrivacyRegionType.NOTIFICATION -> PrivacyRisk.MEDIUM
            PrivacyRegionType.PRIVATE_TEXT -> PrivacyRisk.HIGH
            PrivacyRegionType.PERSONAL_IDENTIFIER -> PrivacyRisk.HIGH
            PrivacyRegionType.PERSONAL_IMAGE -> PrivacyRisk.MEDIUM
            PrivacyRegionType.SENSITIVE_APPLICATION_UI -> PrivacyRisk.HIGH
            PrivacyRegionType.LOCATION_INFORMATION -> PrivacyRisk.MEDIUM
            PrivacyRegionType.UNKNOWN_SENSITIVE_REGION ->
                PrivacyRisk.HIGH
        }
    }

    private val PrivacyRisk.severity: Int
        get() = when (this) {
            PrivacyRisk.NONE -> 0
            PrivacyRisk.LOW -> 1
            PrivacyRisk.MEDIUM -> 2
            PrivacyRisk.HIGH -> 3
            PrivacyRisk.UNKNOWN -> 4
        }
}