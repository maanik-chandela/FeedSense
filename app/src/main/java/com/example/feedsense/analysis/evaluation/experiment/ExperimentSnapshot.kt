package com.example.feedsense.analysis.evaluation.experiment

import java.time.LocalDateTime
import java.util.UUID

/**
 * Milestone 8B-9.
 *
 * Experiment snapshot and audit trail.
 *
 * A frozen, immutable record of an experiment run: the definition,
 * the exact selected population (item ids), the versions in effect,
 * and an idempotency signature. The signature is a deterministic
 * SHA-256 over the definition-relevant plus population-relevant
 * inputs, so re-running the identical experiment on the identical
 * population yields the identical signature - enabling callers to
 * detect duplicate or drifted runs without persisting anything new.
 */
data class ExperimentSnapshot(
    val snapshotId: String = UUID.randomUUID().toString(),
    val experimentId: String,
    val experimentName: String,
    val definition: ExperimentDefinition,
    val selectedItemIds: Set<String>,
    val pairedCount: Int,
    val createdAt: LocalDateTime = LocalDateTime.now(),

    // --------------------------------
    // VERSION PROVENANCE
    // --------------------------------
    val baselineModelVersion: String?,
    val evidenceAwareModelVersion: String?,
    val decisionVersion: String?,
    val experimentVersion: String,

    // --------------------------------
    // AUDIT TRAIL (never modified after construction)
    // --------------------------------
    val auditEvents: List<AuditEvent>
) {
    /**
     * A single immutable audit event on the experiment run.
     */
    data class AuditEvent(
        val at: LocalDateTime,
        val action: String,
        val detail: String
    )

    /**
     * Deterministic idempotency signature. Built from the parts of the
     * run that determine the result: the definition and the exact
     * selected population (ordered by item id).
     */
    val idempotencySignature: String
        get() = Signature.sha256(signaturePayload())

    private fun signaturePayload(): String = buildString {
        append(definition.experimentVersion).append('|')
        append(definition.datasetMode.label).append('|')
        append(definition.sessionId ?: "").append('|')
        append(definition.itemIds.sorted().joinToString(",")).append('|')
        append(definition.startDate ?: "").append('|')
        append(definition.endDate ?: "").append('|')
        append(definition.datasetVersion ?: "").append('|')
        append(definition.annotatorId ?: "").append('|')
        append(definition.maxItems).append('|')
        append(selectedItemIds.sorted().joinToString(",")).append('|')
        append(definition.comparativeConfig.evaluationVersion).append('|')
        append(definition.comparativeConfig.diagnosticVersion)
    }

    /** Surface an audit event by returning a copy carrying an extra event. */
    fun withAuditEvent(action: String, detail: String): ExperimentSnapshot =
        copy(
            auditEvents = auditEvents + AuditEvent(
                at = LocalDateTime.now(),
                action = action,
                detail = detail
            )
        )

    private object Signature {
        fun sha256(input: String): String {
            try {
                val md = java.security.MessageDigest
                    .getInstance("SHA-256")
                val bytes = md.digest(input.toByteArray(Charsets.UTF_8))
                return bytes.joinToString("") { "%02x".format(it) }
            } catch (e: java.security.NoSuchAlgorithmException) {
                // Absolute fallback: never throws at runtime.
                return "unavailable-${input.hashCode()}"
            }
        }
    }
}
