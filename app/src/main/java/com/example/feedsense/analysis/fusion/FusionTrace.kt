package com.example.feedsense.analysis.fusion

// --------------------------------
// FUSION TRACE (Milestone 8B-1)
// --------------------------------
//
// Layer G. The compact, structured diagnostic trace attached to
// every prediction. It is intentionally SMALL (no full OCR
// transcripts, no screenshots, no huge JSON) so it can live on a
// frequently queried row without bloating storage or hurting
// performance (8B-1 section 45/46).

data class EvidenceTraceEntry(
    val family: String,
    val type: String,
    val strength: String,
    val frames: Int,
    val source: String
)

data class CandidateTraceEntry(
    val category: String,
    val score: Double,
    val frameAgreement: Int
)

data class FusionTrace(
    val framesConsidered: Int,
    val perProviderStrength: List<EvidenceTraceEntry>,
    val candidates: List<CandidateTraceEntry>,
    val temporalAgreement: String,
    val finalCategory: String?,
    val confidenceBand: String,
    val uncertainty: String,
    val informationQuality: String,
    val modelVersion: String
) {

    companion object {
        const val TEMPORAL_CONSISTENT = "CONSISTENT"
        const val TEMPORAL_INSUFFICIENT = "INSUFFICIENT"
        const val TEMPORAL_CONFLICT = "CONFLICT"
    }
}
