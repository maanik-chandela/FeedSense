package com.example.feedsense.analysis.evidence

/*
 * Milestone 8B-5.
 *
 * Evidence model.
 *
 * Represents a single piece of extracted evidence
 * from a captured frame. Evidence is the atomic unit
 * of the evidence pipeline.
 *
 * Architecture:
 *   frame → extractors → Evidence[] → fusion → prediction
 *
 * Every evidence item is:
 *   - traceable to its source (provenance)
 *   - quality-assessed
 *   - typed by domain
 *   - associated with supporting categories
 *   - privacy-aware
 *
 * Important:
 *   - Evidence quality ≠ AI confidence
 *   - Unknown state remains UNKNOWN (never converted
 *     to negative fact)
 *   - Platform ≠ category
 *   - Single keywords do not force categories
 *
 * Limitations:
 *   - Evidence is frame-level, not sequence-level
 *   - Visual evidence is interface-only in v1
 *   - No automatic content detection yet
 */
data class Evidence(
    val type: EvidenceType,
    val source: EvidenceSource,
    val quality: EvidenceQuality,
    val value: String,
    val supportingCategories: List<String> =
        emptyList(),
    val contradictingCategories: List<String> =
        emptyList(),
    val metadata: Map<String, String> =
        emptyMap(),
    val timestampMs: Long = System.currentTimeMillis(),
    val frameId: String? = null,
    val privacyState: PrivacyState =
        PrivacyState.UNKNOWN
) {
    val hasSupport: Boolean
        get() = supportingCategories.isNotEmpty()

    val hasContradiction: Boolean
        get() = contradictingCategories.isNotEmpty()

    val isUsable: Boolean
        get() = quality != EvidenceQuality.NONE
}

/*
 * Privacy state of the evidence.
 *
 * Tracks whether the evidence was extracted from
 * a sanitized or raw frame.
 */
enum class PrivacyState {
    SANITIZED,
    RAW,
    UNKNOWN
}
