package com.example.feedsense.analysis.evaluation

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime

// --------------------------------
// ROOT-CAUSE ASSESSMENT (Milestone 8A-6)
// --------------------------------
//
// The structured representation of one error's root-cause
// assessment. Conceptually mirrors the 8A-6 section-5 model:
//
//   analysisId, evaluationItemId, evaluationRecordId,
//   datasetVersion, modelVersion, capability, errorType,
//   candidateRootCause(supporting PRIMARY + CONTRIBUTING),
//   evidenceStrength, attributionStatus, humanReviewStatus,
//   reviewerId, reviewerNotes, evidenceReferences,
//   createdAt, updatedAt
//
// It is purely logical and serializes to JSON so it can be
// persisted alongside the 8A-3/8A-5 reports in
// evaluation_runs.reportJson without a schema change. It never
// modifies the AI prediction, ground truth, or evaluation
// result - the diagnosis is strictly an additional layer.

data class RootCauseAssessment(
    val analysisId: String,
    val evaluationItemId: String,
    val evaluationRecordId: String?,
    val datasetVersion: String?,
    val modelVersion: String?,
    val diagnosticVersion: String,
    val capability: String,
    val errorType: String,

    // The candidate causes, each with its own status/strength.
    val causes: List<CandidateCause>,

    // Overall review state.
    val attributionStatus: String,
    val evidenceStrength: String,
    val attributionClass: String,
    val attributionConfidence: String?,
    val attributionNote: String,

    // Human review.
    val humanReviewStatus: String,
    val reviewerId: String?,
    val reviewerNotes: String?,
    val reviewedAt: LocalDateTime?,

    val evidenceReferences: List<Evidence.EvidenceRef>,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val auditTrail: List<AuditEntry> = emptyList()
) {

    // A candidate cause (possibly multi-cause; one PRIMARY, rest
    // CONTRIBUTING).
    data class CandidateCause(
        val cause: String,
        val role: String,
        val attributionStatus: String,
        val evidenceStrength: String,
        val attributionClass: String,
        val attributionConfidence: String?,
        val note: String
    )

    // Audit entry preserving previous -> new state + reviewer.
    data class AuditEntry(
        val at: LocalDateTime,
        val reviewerId: String?,
        val previousStatus: String,
        val newStatus: String,
        val note: String?
    )

    // --------------------------------
    // DERIVED HELPERS
    // --------------------------------

    fun primaryCause(): CandidateCause? {
        return causes.firstOrNull { it.role == RootCauseTypes.ROLE_PRIMARY }
    }

    fun contributingCauses(): List<CandidateCause> {
        return causes.filter { it.role == RootCauseTypes.ROLE_CONTRIBUTING }
    }

    fun anyConfirmed(): Boolean {
        return attributionStatus == RootCauseTypes.STATUS_CONFIRMED ||
            causes.any {
                it.attributionStatus == RootCauseTypes.STATUS_CONFIRMED
            }
    }

    /**
     * Clean re-edit semantics: returns an identical assessment
     * but with the trailing audit entry appended (never creates
     * a duplicate active record for the same analysis).
     */
    fun withAudit(
        entry: AuditEntry,
        now: LocalDateTime = LocalDateTime.now()
    ): RootCauseAssessment {
        return copy(
            auditTrail = auditTrail + entry,
            updatedAt = now
        )
    }

    // --------------------------------
    // JSON SERIALIZATION
    // --------------------------------

    fun toJson(): String = toJsonObject().toString(2)

    fun toJsonObject(): JSONObject {
        val root = JSONObject()
        root.put("analysisId", analysisId)
        root.put("evaluationItemId", evaluationItemId)
        root.put("evaluationRecordId", evaluationRecordId ?: "")
        root.put("datasetVersion", datasetVersion ?: "")
        root.put("modelVersion", modelVersion ?: "")
        root.put("diagnosticVersion", diagnosticVersion)
        root.put("capability", capability)
        root.put("errorType", errorType)
        root.put("attributionStatus", attributionStatus)
        root.put("evidenceStrength", evidenceStrength)
        root.put("attributionClass", attributionClass)
        root.put("attributionConfidence", attributionConfidence ?: "")
        root.put("attributionNote", attributionNote)
        root.put("humanReviewStatus", humanReviewStatus)
        root.put("reviewerId", reviewerId ?: "")
        root.put("reviewerNotes", reviewerNotes ?: "")
        root.put("reviewedAt", reviewedAt?.toString() ?: "")
        root.put("createdAt", createdAt.toString())
        root.put("updatedAt", updatedAt.toString())

        val causes = JSONArray()
        this.causes.forEach { c ->
            val o = JSONObject()
            o.put("cause", c.cause)
            o.put("role", c.role)
            o.put("attributionStatus", c.attributionStatus)
            o.put("evidenceStrength", c.evidenceStrength)
            o.put("attributionClass", c.attributionClass)
            o.put("attributionConfidence", c.attributionConfidence ?: "")
            o.put("note", c.note)
            causes.put(o)
        }
        root.put("causes", causes)

        val refs = JSONArray()
        evidenceReferences.forEach { r ->
            val o = JSONObject()
            o.put("type", r.type)
            o.put("referenceId", r.referenceId ?: "")
            o.put("kind", r.kind)
            o.put("note", r.note)
            refs.put(o)
        }
        root.put("evidenceReferences", refs)

        val audit = JSONArray()
        auditTrail.forEach { a ->
            val o = JSONObject()
            o.put("at", a.at.toString())
            o.put("reviewerId", a.reviewerId ?: "")
            o.put("previousStatus", a.previousStatus)
            o.put("newStatus", a.newStatus)
            o.put("note", a.note ?: "")
            audit.put(o)
        }
        root.put("auditTrail", audit)

        return root
    }

    companion object {
        const val DIAGNOSTIC_VERSION = "diagnostic-v1"

        const val REVIEW_UNREVIEWED = "UNREVIEWED"
        const val REVIEW_IN_PROGRESS = "IN_PROGRESS"
        const val REVIEW_COMPLETE = "COMPLETE"
    }
}
