package com.example.feedsense.analysis.evidence.decision

import com.example.feedsense.analysis.evidence.temporal.CoverageState
import com.example.feedsense.analysis.evidence.temporal.ConflictLevel

/*
 * Milestone 8B-7.
 *
 * Item-level prediction result.
 *
 * The explicit, evidence-aware item-level prediction
 * emitted by the 8B-7 decision layer. This is the research
 * output consumed by 8A evaluation and used for future
 * baselines / comparisons.
 *
 * Design:
 *   - Immutable: once created, never modified.
 *   - Serializable: for audit and reproducibility.
 *   - Explainable: exposes support breakdown, supporting
 *     and contradicting evidence, and uncertainty
 *     components.
 *   - Deterministic: same evidence → same result.
 *
 * Key properties:
 *   - primaryCategory : dominant semantic category.
 *   - decisionState   : DECIDED / AMBIGUOUS /
 *                       INSUFFICIENT_EVIDENCE / UNKNOWN.
 *   - uncertainty     : LOW / MODERATE / HIGH / UNRESOLVED.
 *   - supportScore    : evidence support for the primary
 *                       category (NOT a probability).
 *   - decisionMargin  : top support minus second best.
 *
 * Versioning:
 *   - modelVersion   : 8B-6 fusion version.
 *   - fusionVersion  : 8B-6 fusion engine version.
 *   - decisionVersion: this decision layer's version.
 *
 * Important:
 *   - supportScore ≠ probability (no calibration).
 *   - The original classifier confidence is preserved
 *     separately in modelConfidence; it is NOT overwritten.
 *   - Missing evidence remains missing.
 */
data class ItemPredictionResult(
    // --------------------------------
    // IDENTITY
    // --------------------------------

    val sessionId: String,
    val feedItemId: String?,

    // --------------------------------
    // DECISION
    // --------------------------------

    val primaryCategory: String?,
    val decisionState: DecisionState,
    val uncertainty: UncertaintyState,
    val supportScore: Double,
    val decisionMargin: Double,

    // --------------------------------
    // EVIDENCE COVERAGE
    // --------------------------------

    val evidenceCoverage: CoverageState,
    val coverageRatio: Double,
    val observedDurationMs: Long,
    val itemDurationMs: Long,

    // --------------------------------
    // EVIDENCE QUALITY / DIVERSITY
    // --------------------------------

    val evidenceQuality: String,
    val sourceDiversity: Int,
    val uniqueEvidenceIdentities: Int,
    val evidenceTypesPresent: List<String>,

    // --------------------------------
    // CANDIDATE CATEGORIES
    // --------------------------------

    val candidateCategories:
        List<CategorySupport>,

    // --------------------------------
    // SUPPORTING / CONTRADICTING EVIDENCE
    // --------------------------------

    val supportingEvidence: Map<String, List<String>>,
    val contradictingEvidence: Map<String, List<String>>,

    // --------------------------------
    // TEMPORAL CONFLICT / TRANSITIONS
    // --------------------------------

    val temporalConflict: ConflictLevel,
    val transitionDetected: Boolean,
    val internalTransitionDetected: Boolean,
    val secondaryCategory: String?,

    // --------------------------------
    // UNCERTAINTY COMPONENTS
    // --------------------------------

    val uncertaintyReasons: List<String>,

    // --------------------------------
    // REPRESENTATIVE FRAME
    // --------------------------------

    val representativeFrameOutlier: Boolean?,

    // --------------------------------
    // VERSIONING
    // --------------------------------

    val modelVersion: String,
    val fusionVersion: String,
    val decisionVersion: String,

    // --------------------------------
    // METADATA
    // --------------------------------

    val createdAtMs: Long,
    val modelConfidence: Double?
) {
    /*
     * Primary category's support breakdown, when present.
     */
    val primarySupport: CategorySupport?
        get() = candidateCategories
            .firstOrNull()

    /*
     * Whether the decision layer made a usable category
     * decision (DECIDED or AMBIGUOUS with a primary).
     */
    val madeCategoryDecision: Boolean
        get() = decisionState == DecisionState.DECIDED ||
                decisionState == DecisionState.AMBIGUOUS

    /*
     * Whether the system abstained (no decided category).
     */
    val abstained: Boolean
        get() = decisionState != DecisionState.DECIDED

    companion object {

        const val DECISION_VERSION = "item-decision-v1"

        /*
         * Creates an empty/UNKNOWN result for the
         * degenerate case where no decision could be
         * derived at all.
         */
        fun empty(
            sessionId: String,
            feedItemId: String?,
            modelVersion: String,
            fusionVersion: String,
            modelConfidence: Double? = null
        ): ItemPredictionResult {
            return ItemPredictionResult(
                sessionId = sessionId,
                feedItemId = feedItemId,
                primaryCategory = null,
                decisionState = DecisionState.UNKNOWN,
                uncertainty =
                    UncertaintyState.UNRESOLVED,
                supportScore = 0.0,
                decisionMargin = 0.0,
                evidenceCoverage =
                    CoverageState.NO_COVERAGE,
                coverageRatio = 0.0,
                observedDurationMs = 0,
                itemDurationMs = 0,
                evidenceQuality = "NONE",
                sourceDiversity = 0,
                uniqueEvidenceIdentities = 0,
                evidenceTypesPresent = emptyList(),
                candidateCategories = emptyList(),
                supportingEvidence = emptyMap(),
                contradictingEvidence = emptyMap(),
                temporalConflict = ConflictLevel.NONE,
                transitionDetected = false,
                internalTransitionDetected = false,
                secondaryCategory = null,
                uncertaintyReasons =
                    listOf("no_evidence"),
                representativeFrameOutlier = null,
                modelVersion = modelVersion,
                fusionVersion = fusionVersion,
                decisionVersion = DECISION_VERSION,
                createdAtMs =
                    System.currentTimeMillis(),
                modelConfidence = modelConfidence
            )
        }
    }
}
