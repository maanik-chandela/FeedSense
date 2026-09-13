package com.example.feedsense.analysis.ml.selection

// --------------------------------
// ARCHITECTURE DECISION RECORD (Milestone 8B-15-1)
// --------------------------------
//
// Versioned, change-aware record of a research-phase engineering
// decision. The rule for this phase: a decision is NEVER silently
// changed. Any future revision must bump `version` and set
// `supersedes` to the previous version, leaving the older record
// intact (see also docs/adrs).

/*
 * Lifecycle of a decision record.
 */
enum class AdrStatus(val label: String) {
    PROPOSED("PROPOSED"),
    ACCEPTED("ACCEPTED"),
    SUPERSEDED("SUPERSEDED");

    companion object {
        fun fromLabel(label: String): AdrStatus? =
            entries.firstOrNull { it.label == label }
    }
}

/*
 * A declared risk of the decision, each with its own evidence
 * level and a mitigation. Determinate scales are qualitative
 * (LOW/MEDIUM/HIGH/UNKNOWN) to avoid fabricated numbers.
 */
enum class ImpactLevel(val label: String) {
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH"),
    UNKNOWN("UNKNOWN")
}

data class AdrRisk(
    val description: String,
    val likelihood: ImpactLevel,
    val impact: ImpactLevel,
    val evidence: EvidenceLevel,
    val mitigation: String
) {
    init {
        require(description.isNotBlank()) { "risk description must be non-blank" }
        require(mitigation.isNotBlank()) { "risk mitigation must be non-blank" }
    }
}

/*
 * Immutable architecture decision record.
 *
 *   - adrId        : stable, e.g. "adr-0001".
 *   - version      : semantic-ish record version; the FIRST
 *                    version is always "1.0".
 *   - supersedes   : adrId:version replaced by this record, or
 *                    null for the initial version.
 *   - date          : ISO-8601 date of ACCEPTANCE.
 *   - milestone-id  : e.g. "8B-15-1".
 *   - context       : why the decision was reached.
 *   - decision      : the decision, worded so it survives
 *                     without reference to version drift.
 *   - alternatives  : candidates reviewed and rejected/held, with
 *                     the reason captured per entry.
 *   - consequences  : effects of the decision.
 *   - risks         : declared risks + mitigations.
 *   - futureValidation: what an experiment must confirm before
 *                     promotion (8B-15-2+ work items).
 *   - relatedDocuments: pointers into docs/ (files or anchors).
 */
data class ArchitectureDecisionRecord(
    val adrId: String,
    val title: String,
    val status: AdrStatus = AdrStatus.PROPOSED,
    val version: String = "1.0",
    val supersedes: String? = null,
    val date: String,
    val milestoneId: String,
    val context: List<String>,
    val decision: String,
    val alternatives: List<DecisionAlternative>,
    val consequences: List<String>,
    val risks: List<AdrRisk>,
    val futureValidation: List<String>,
    val relatedDocuments: List<String> = emptyList()
) {
    init {
        require(adrId.isNotBlank()) { "adrId must be non-blank" }
        require(title.isNotBlank()) { "title must be non-blank" }
        require(version.matches(Regex("\\d+\\.\\d+"))) {
            "version must be <major>.<minor>, got '$version'"
        }
        require(date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) {
            "date must be ISO-8601 yyyy-mm-dd, got '$date'"
        }
        require(milestoneId.isNotBlank()) { "milestoneId must be non-blank" }
        require(decision.isNotBlank()) { "decision must be non-blank" }
        require(context.isNotEmpty()) { "context must not be empty" }
        if (status == AdrStatus.SUPERSEDED) {
            require(!supersedes.isNullOrBlank()) {
                "SUPERSEDED record must name the version it replaces via supersedes"
            }
        }
    }

    /*
     * Stable identity string: "<adrId> v<version>".
     */
    val identityKey: String
        get() = "$adrId v$version"
}

/*
 * An alternative considered and not promoted to the decision,
 * with the documented reason it was not.
 */
data class DecisionAlternative(
    val candidateId: String,
    val name: String,
    val reasonRejected: String
) {
    init {
        require(candidateId.isNotBlank()) { "candidateId must be non-blank" }
        require(name.isNotBlank()) { "name must be non-blank" }
        require(reasonRejected.isNotBlank()) { "reasonRejected must be non-blank" }
    }
}