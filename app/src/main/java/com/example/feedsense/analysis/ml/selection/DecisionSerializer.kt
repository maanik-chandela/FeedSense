package com.example.feedsense.analysis.ml.selection

// --------------------------------
// DECISION SERIALIZER (8B-15-1)
// --------------------------------
//
// Deterministic text rendering of the research decision catalog.
// Same rules as ModelResultSerializer (8B-14): keys sorted,
// values escaped deterministically, output byte-stable for equal
// input. Conventions:
//
//   - criterion cells emit THREE keys
//       criteria.<criterionId>.rating
//       criteria.<criterionId>.evidence
//       criteria.<criterionId>.rationale
//     and NEVER a numeric "value"/"score": a numeric precision
//     that was not measured is forbidden in this milestone.
//   - optional fields are omitted when null (missing != "0").
//   - list values serialize as "|"-joined with a fixed order.

object DecisionSerializer {

    /* ---------------- candidates ---------------- */

    fun candidateToMap(candidate: ModelCandidate): Map<String, String> {
        val m = linkedMapOf<String, String>()
        m["candidateId"] = candidate.candidateId
        m["familyId"] = candidate.familyId
        m["familyName"] = candidate.familyName
        m["name"] = candidate.name
        m["architecture"] = candidate.architecture
        m["runtimes"] = candidate.runtimes.joinToString("|") { it.label }
        m["status"] = candidate.status.label
        m["statusReason"] = candidate.statusReason
        m["license"] = candidate.license
        m["licenseEvidence"] = candidate.licenseEvidence.label
        candidate.parametersMillions?.let { m["parametersMillions"] = it.toString() }
        m["parametersEvidence"] = candidate.parametersEvidence.label
        candidate.modelSizeMegabytes?.let { m["modelSizeMegabytes"] = it.toString() }
        m["sizeEvidence"] = candidate.sizeEvidence.label
        candidate.latencyMs?.let { m["latencyMs"] = it.toString() }
        m["latencyEvidence"] = candidate.latencyEvidence.label
        candidate.latencyContext?.let { m["latencyContext"] = it }
        candidate.androidMinApi?.let { m["androidMinApi"] = it.toString() }
        candidate.metrics.forEachIndexed { i, metric ->
            m["metrics.$i.name"] = metric.name
            m["metrics.$i.value"] = metric.value
            m["metrics.$i.evidence"] = metric.evidence.label
            m["metrics.$i.context"] = metric.context
        }
        for (c in EvaluationCriterion.entries) {
            val score = candidate.scores[c] ?: continue
            m["criteria.${c.id}.rating"] = score.rating.label
            m["criteria.${c.id}.evidence"] = score.evidence.label
            m["criteria.${c.id}.rationale"] = score.rationale
        }
        candidate.sources.forEachIndexed { i, s ->
            m["sources.$i.url"] = s.url
            m["sources.$i.title"] = s.title
            m["sources.$i.visitedDate"] = s.visitedDate
        }
        if (candidate.notes.isNotBlank()) m["notes"] = candidate.notes
        return m
    }

    fun candidateToJson(candidate: ModelCandidate): String =
        toJson(candidateToMap(candidate))

    /* ---------------- decision record ---------------- */

    fun decisionToMap(record: ArchitectureDecisionRecord): Map<String, String> {
        val m = linkedMapOf<String, String>()
        m["adrId"] = record.adrId
        m["title"] = record.title
        m["status"] = record.status.label
        m["version"] = record.version
        m["date"] = record.date
        m["milestoneId"] = record.milestoneId
        record.supersedes?.let { m["supersedes"] = it }
        record.context.forEachIndexed { i, line ->
            m["context.$i"] = line
        }
        m["decision"] = record.decision
        record.alternatives.forEachIndexed { i, alt ->
            m["alternatives.$i.candidateId"] = alt.candidateId
            m["alternatives.$i.name"] = alt.name
            m["alternatives.$i.reasonRejected"] = alt.reasonRejected
        }
        record.consequences.forEachIndexed { i, line ->
            m["consequences.$i"] = line
        }
        record.risks.forEachIndexed { i, risk ->
            m["risks.$i.description"] = risk.description
            m["risks.$i.likelihood"] = risk.likelihood.label
            m["risks.$i.impact"] = risk.impact.label
            m["risks.$i.evidence"] = risk.evidence.label
            m["risks.$i.mitigation"] = risk.mitigation
        }
        record.futureValidation.forEachIndexed { i, line ->
            m["futureValidation.$i"] = line
        }
        record.relatedDocuments.forEachIndexed { i, doc ->
            m["relatedDocuments.$i"] = doc
        }
        return m
    }

    fun decisionToJson(record: ArchitectureDecisionRecord): String =
        toJson(decisionToMap(record))

    /* ---------------- catalog ---------------- */

    fun catalogToMap(): Map<String, String> {
        val m = linkedMapOf<String, String>()
        m["catalogVersion"] = ResearchDecisionCatalog.CATALOG_VERSION
        m["candidateCount"] = ResearchDecisionCatalog.candidates.size.toString()
        m["candidateIds"] =
            ResearchDecisionCatalog.candidates.joinToString("|") { it.candidateId }
        m["adrId"] = ResearchDecisionCatalog.decision.adrId
        m["adrVersion"] = ResearchDecisionCatalog.decision.version
        return m
    }

    fun catalogToJson(): String = toJson(catalogToMap())

    /*
     * Repro key: the identity of the emitted decision set. Two
     * runs are comparable iff this key matches.
     */
    fun reproducibilityKey(): String {
        val catalog = catalogToMap()
        val decision = decisionToMap(ResearchDecisionCatalog.decision)
        val candidateIds = ResearchDecisionCatalog.candidates
            .joinToString(",") { it.candidateId }
        return listOf(
            catalog["catalogVersion"] ?: "",
            catalog["adrVersion"] ?: "",
            candidateIds
        ).joinToString("::")
    }

    /* ---------------- core renderer ---------------- */

    private fun toJson(map: Map<String, String>): String {
        return map.entries
            .sortedBy { it.key }
            .joinToString(
                prefix = "{",
                postfix = "}",
                separator = ","
            ) { (key, value) ->
                "\"${escape(key)}\":\"${escape(value)}\""
            }
    }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
    }
}