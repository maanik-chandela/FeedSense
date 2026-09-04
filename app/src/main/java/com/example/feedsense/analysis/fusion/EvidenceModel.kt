package com.example.feedsense.analysis.fusion

// --------------------------------
// EVIDENCE MODEL (Milestone 8B-1)
// --------------------------------
//
// Structured evidence is the core currency of the fusion model.
// Every piece of evidence distinguishes:
//
//   - RAW value        : the literal observed signal
//                        (e.g. OCR text, platform name)
//   - INTERPRETATION   : what the system read INTO the value
//                        (e.g. candidate categories / topic /
//                        tone derived from the OCR text)
//   - CLASSIFICATION   : an upstream classifier's verdict used
//                        as candidate evidence (not authority)
//
// In this model the RAW value lives on EvidenceRecord.value and
// the interpretations (candidate votes) are produced separately
// by CandidateGenerator from each record. Classification output
// from the legacy heuristics is wrapped as a single record whose
// family is CLASSIFIER so the fusion layer can weigh it as one
// signal among many, never as the deciding authority.
//
// Every record carries provenance: which frame(s) produced it,
// a compact timestamp and a named source, so the diagnostic
// trace can explain where a conclusion came from.

// Which broad family the evidence belongs to. Weighting and
// reliability are keyed off this.
enum class EvidenceFamily {
    TEXT,       // OCR/text-derived
    PLATFORM,   // which app is on screen (CONTEXT, not category)
    INTERACTION,// visible interaction affordances
    TEMPORAL,   // consistency across frames
    VISUAL,     // explicit vision signals (currently unavailable)
    CLASSIFIER  // upstream heuristic-classifier verdict used as evidence
}

/**
 * Evidence strength is an INTEGRITY statement about how
 * definitive a piece of evidence is, independent of any
 * prediction confidence. Reuses the 8A-6 design vocabulary.
 */
enum class EvidenceStrength(val value: Double) {
    NONE(0.0),
    WEAK(0.25),
    MODERATE(0.5),
    STRONG(1.0)
}

/**
 * A single structured piece of evidence.
 *
 * @param id         stable identifier so deduplication and
 *                   provenance can reference it.
 * @param family     which family it belongs to.
 * @param type       fine-grained type (e.g. OCR_TEXT,
 *                   PLATFORM_APP, TEMPORAL_CONSISTENCY, ...).
 * @param value      raw value if retained (may be null for
 *                   privacy / when only a summary is kept).
 * @param valueSummary compact human-readable summary of the raw
 *                   value; the ONLY form that may be logged.
 * @param isRaw      true => value/valueSummary is the raw signal;
 *                   false => it is an interpretation or a
 *                   classification verdict.
 * @param confidence raw provider confidence in [0,1] or null.
 * @param strength   integrity strength of the record.
 * @param frameIds   frames that produced/observe this evidence.
 * @param timestamp  compact timestamp label (never a full date).
 * @param source     named source (provider/class).
 * @param reliability provider reliability in [0,1]; resolved
 *                   from centralized config by default.
 * @param isVirtual  true when the evidence denotes ABSENCE/an
 *                   unavailable signal rather than a real
 *                   observation (e.g. VISUAL_SIGNAL_UNAVAILABLE).
 */
data class EvidenceRecord(
    val id: String,
    val family: EvidenceFamily,
    val type: String,
    val value: String?,
    val valueSummary: String,
    val isRaw: Boolean,
    val confidence: Double?,
    val strength: EvidenceStrength,
    val frameIds: List<String>,
    val timestamp: String,
    val source: String,
    val reliability: Double,
    val isVirtual: Boolean = false
) {

    companion object {
        fun id(
            family: EvidenceFamily,
            type: String,
            valueSummary: String
        ): String {
            return "${family.name}:$type:$valueSummary"
        }
    }
}
