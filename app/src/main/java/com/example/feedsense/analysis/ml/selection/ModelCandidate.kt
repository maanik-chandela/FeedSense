package com.example.feedsense.analysis.ml.selection

import com.example.feedsense.analysis.ml.ModelFormat

// --------------------------------
// MODEL CANDIDATE (Milestone 8B-15-1)
// --------------------------------
//
// One researched model-architecture / runtime candidate. A
// candidate is a RECORD of what was found, not a commitment:
// status carries that. Every numeric field is paired with an
// EvidenceLevel so a reader can tell a documented figure (source
// citation) from an estimate and from "we do not know".
//
// No field may hold a fabricated number.

/*
 * Disposition of a candidate after 8B-15-1 research.
 */
enum class CandidateStatus(val label: String) {
    SHORTLISTED("SHORTLISTED"),
    CONDITIONAL("CONDITIONAL"),
    REJECTED("REJECTED"),
    OBSERVED_ONLY("OBSERVED_ONLY");

    companion object {
        fun fromLabel(label: String): CandidateStatus? =
            entries.firstOrNull { it.label == label }
    }
}

/*
 * A named, checkable source backing the candidate record.
 */
data class SourceReference(
    val url: String,
    val title: String,
    val visitedDate: String,
    val claim: String
) {
    init {
        val absolute =
            url.startsWith("https://") ||
                url.startsWith("http://") ||
                url.startsWith("file://")
        require(absolute) {
            "source url must be absolute (https/http/file): $url"
        }
        require(title.isNotBlank()) { "title must be non-blank" }
        require(visitedDate.isNotBlank()) { "visitedDate must be non-blank" }
    }
}

/*
 * A structured published figure. `value` is a STRING on purpose:
 * exact figures belong to their source; we re-print them, we do
 * not re-derive them. Empty = do not include the metric.
 */
data class PublishedMetric(
    val name: String,
    val value: String,
    val evidence: EvidenceLevel,
    val context: String
) {
    init {
        require(name.isNotBlank()) { "metric name must be non-blank" }
        require(value.isNotBlank()) { "metric value must be non-blank" }
    }

    val rendered: String
        get() = "$name: $value ($evidence, $context)"
}

/*
 * Research record of one candidate.
 *
 * Validation rules (each mirrors a 8B-15-1 rule):
 *   - candidateId / name / license must be present.
 *   - license "UNKNOWN" is an explicit caveat, never a shortcut:
 *     a SHORTLISTED candidate is NOT allowed to carry it
 *     (enforced here).
 *   - optional numbers may be absent (null); when present they
 *     must be finite and non-negative. There is always an
 *     accompanying evidence level, so a null figure plus a
 *     `*EvidenceLevel` is honest by construction.
 */
data class ModelCandidate(
    val candidateId: String,
    val familyId: String,
    val familyName: String,
    val name: String,
    val architecture: String,
    val runtimes: List<ModelFormat>,
    val status: CandidateStatus,
    val statusReason: String,
    val license: String,
    val licenseEvidence: EvidenceLevel,

    val parametersMillions: Double? = null,
    val parametersEvidence: EvidenceLevel = EvidenceLevel.UNKNOWN,
    val modelSizeMegabytes: Double? = null,
    val sizeEvidence: EvidenceLevel = EvidenceLevel.UNKNOWN,
    val latencyMs: Double? = null,
    val latencyEvidence: EvidenceLevel = EvidenceLevel.UNKNOWN,
    val latencyContext: String? = null,
    val androidMinApi: Int? = null,

    val metrics: List<PublishedMetric> = emptyList(),
    val scores: Map<EvaluationCriterion, CriterionScore> = emptyMap(),
    val sources: List<SourceReference> = emptyList(),
    val notes: String = ""
) {
    init {
        require(candidateId.isNotBlank()) { "candidateId must be non-blank" }
        require(familyId.isNotBlank()) { "familyId must be non-blank" }
        require(name.isNotBlank()) { "name must be non-blank" }
        require(statusReason.isNotBlank()) { "statusReason must be non-blank" }
        require(license.isNotBlank()) { "license must be non-blank" }
        if (status == CandidateStatus.SHORTLISTED) {
            require(license != "UNKNOWN") {
                "SHORTLISTED candidate $candidateId must have a verified license, got UNKNOWN"
            }
        }
        require(runtimes.isNotEmpty()) { "runtimes must not be empty" }
        require(runtimes.toSet().size == runtimes.size) {
            "duplicate runtimes in $candidateId"
        }
        parametersMillions?.let {
            require(it >= 0.0 && it.isFinite()) { "parametersMillions must be >= 0" }
        }
        modelSizeMegabytes?.let {
            require(it >= 0.0 && it.isFinite()) { "modelSizeMegabytes must be >= 0" }
        }
        latencyMs?.let {
            require(it >= 0.0 && it.isFinite()) { "latencyMs must be >= 0" }
        }
        androidMinApi?.let {
            require(it >= 24) { "androidMinApi must be >= FeedSense minSdk 24, got $it" }
        }
        require(scores.keys.all { key -> key in EvaluationCriterion.entries }) {
            "unknown criterion in $candidateId"
        }
    }

    /*
     * Whether a cell exists in this candidate's score map.
     */
    fun hasScore(criterion: EvaluationCriterion): Boolean =
        scores.containsKey(criterion)
}

/*
 * Convenience: lookup helpers on collections of candidates.
 */
fun List<ModelCandidate>.byId(candidateId: String): ModelCandidate? =
    firstOrNull { it.candidateId == candidateId }

fun List<ModelCandidate>.byFamily(familyId: String): List<ModelCandidate> =
    filter { it.familyId == familyId }.sortedBy { it.candidateId }