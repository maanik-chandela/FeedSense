package com.example.feedsense.analysis.evidence.temporal

import com.example.feedsense.analysis.evidence.Evidence
import com.example.feedsense.analysis.evidence.EvidenceQuality
import com.example.feedsense.analysis.evidence.EvidenceType

/*
 * Milestone 8B-6.
 *
 * Temporal evidence point.
 *
 * A lightweight, temporally contextualized representation
 * of a single piece of evidence within an item's timeline.
 *
 * This is NOT a duplicate of Evidence. It contextualizes
 * an existing Evidence within the temporal dimension:
 *   - relative time within the item
 *   - evidence identity for deduplication
 *   - temporal position for ordering
 *
 * Design:
 *   - Lightweight: no image data, no raw OCR payloads
 *   - Deterministic: same input → same point
 *   - Immutable: once created, never modified
 *   - Serializable: for audit and reproducibility
 *
 * The evidence identity is derived from the Evidence's
 * source + value hash, enabling persistence detection
 * without inflating independent evidence count.
 *
 * Important:
 *   - TemporalEvidencePoint does NOT create new evidence
 *   - It contextualizes existing evidence temporally
 *   - Same extractor output across frames is one
 *     persistent evidence source, not multiple
 */
data class TemporalEvidencePoint(
    val pointId: String,
    val evidenceType: EvidenceType,
    val extractorName: String,
    val extractorVersion: String,
    val quality: EvidenceQuality,
    val value: String,
    val valueSummary: String,
    val evidenceIdentityHash: String,
    val timestampMs: Long,
    val relativeTimeMs: Long,
    val frameIndex: Int,
    val frameId: String,
    val supportingCategories: List<String>,
    val contradictingCategories: List<String>,
    val privacyState: String,
    val metadata: Map<String, String> = emptyMap()
) {
    val hasSupport: Boolean
        get() = supportingCategories.isNotEmpty()

    val hasContradiction: Boolean
        get() = contradictingCategories.isNotEmpty()

    val isUsable: Boolean
        get() = quality != EvidenceQuality.NONE

    companion object {

        const val VERSION = "temporal-evidence-point-v1"

        /*
         * Creates a TemporalEvidencePoint from an
         * existing Evidence, contextualizing it within
         * the item's temporal dimension.
         *
         * @param evidence The original evidence.
         * @param relativeTimeMs Time relative to item start.
         * @param frameIndex Position in the frame sequence.
         * @param frameId Frame identifier for ordering tie-break.
         * @return A new TemporalEvidencePoint.
         */
        fun fromEvidence(
            evidence: Evidence,
            relativeTimeMs: Long,
            frameIndex: Int,
            frameId: String
        ): TemporalEvidencePoint {
            val identityHash =
                computeIdentityHash(evidence)

            val summary = truncateSummary(evidence.value)

            return TemporalEvidencePoint(
                pointId = "tep_${frameIndex}_${evidence.type.name}",
                evidenceType = evidence.type,
                extractorName =
                    evidence.source.extractorName,
                extractorVersion =
                    evidence.source.extractorVersion,
                quality = evidence.quality,
                value = evidence.value,
                valueSummary = summary,
                evidenceIdentityHash = identityHash,
                timestampMs = evidence.timestampMs,
                relativeTimeMs = relativeTimeMs,
                frameIndex = frameIndex,
                frameId = frameId,
                supportingCategories =
                    evidence.supportingCategories
                        .sorted(),
                contradictingCategories =
                    evidence.contradictingCategories
                        .sorted(),
                privacyState =
                    evidence.privacyState.name,
                metadata = evidence.metadata
            )
        }

        /*
         * Computes a deterministic identity hash from
         * evidence source + value. Evidence with the
         * same extractor and similar value will have
         * the same identity, enabling persistence
         * detection without inflating independence.
         */
        fun computeIdentityHash(
            evidence: Evidence
        ): String {
            val normalizedValue =
                evidence.value.trim()
                    .lowercase()
            return "${evidence.source.extractorName}:" +
                    "${normalizedValue.hashCode()}"
        }

        private fun truncateSummary(
            value: String
        ): String {
            return if (value.length <= 64) {
                value
            } else {
                value.take(61) + "..."
            }
        }
    }
}
