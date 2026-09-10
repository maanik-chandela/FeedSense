package com.example.feedsense.analysis.evidence.decision

/*
 * Milestone 8B-8.
 *
 * Evidence-aware comparison result.
 *
 * An immutable, structured, auditable comparison between
 * the authoritative production baseline AI prediction and
 * the experimental 8B evidence-aware decision for the
 * SAME FeedItem.
 *
 * Architecture:
 *
 *   FeedItem (production, immutable)
 *       │
 *       ├──────────────────► baseline (read verbatim)
 *       │
 *       └──► ReadOnlyEvidenceAwareAdapter
 *                  │
 *                  ▼
 *          ItemDecisionEngine (8B-7)
 *                  │
 *                  ▼
 *      EvidenceAwareComparisonResult (THIS)
 *                  │
 *                  ▼
 *         DecisionEvaluationBridge
 *                  │
 *                  ▼
 *          8A evaluation layer
 *
 * IMPORTANT — this is NOT an accuracy measurement.
 *
 *   baselineConfidence = 0.92
 *   does NOT mean
 *   baselineAccuracy = 92%
 *
 *   evidenceStrength = STRONG
 *   does NOT mean
 *   predictionCorrect = TRUE
 *
 *   Only ground truth can establish correctness.
 *
 * Design:
 *   - Fully immutable (data class, nothing mutated).
 *   - Baseline fields are read verbatim from the
 *     existing FeedItem; they are NEVER overwritten or
 *     re-interpreted.
 *   - Experimental fields come from the 8B-7 decision
 *     layer via the ReadOnlyEvidenceAwareAdapter.
 *   - Both paths remain independently identifiable.
 *   - No privacy-sensitive payload is exported or
 *     persisted (no screenshots, OCR text, captions,
 *     messages, notifications, personal names).
 *   - Version metadata is propagated from all layers.
 *   - Deterministic: same inputs → same result.
 *   - Auditable: every version and state is explicit.
 *
 * Privacy guarantee:
 *   - No raw OCR payloads
 *   - No screenshot paths
 *   - No private captions
 *   - No message contents
 *   - No personal names
 *   - No notification contents
 *   - No arbitrary screen text
 *   - No private URLs
 *   - No personal identifiers
 *   Only structured IDs, category labels, and metadata.
 */
data class EvidenceAwareComparisonResult(

    // --------------------------------
    // IDENTITY
    // --------------------------------

    val feedItemId: String,
    val sessionId: String? = null,
    val evaluationItemId: String? = null,
    val analysisId: String? = null,

    // --------------------------------
    // VERSIONING
    // --------------------------------

    val datasetVersion: String? = null,
    val baselineModelVersion: String? = null,
    val evidenceAwareModelVersion: String? = null,
    val decisionVersion: String? = null,
    val adapterVersion: String,
    val diagnosticVersion: String? = null,

    // --------------------------------
    // AUTHORITATIVE BASELINE
    // --------------------------------

    val baselineCategory: String?,
    val baselineConfidence: Double?,
    val baselineContentType: String? = null,
    val baselinePlatform: String? = null,
    val baselineUncertainty: String? = null,
    val baselineNeedsReview: Boolean = false,

    // --------------------------------
    // EVIDENCE-AWARE RESULT
    // --------------------------------

    val evidenceAwareCategory: String?,
    val evidenceAwareConfidence: Double?,
    val evidenceAwareContentType: String? = null,
    val evidenceAwarePlatform: String? = null,
    val evidenceAwareDecision: DecisionState,
    val evidenceAwareUncertainty: UncertaintyState,
    val evidenceAwareNeedsReview: Boolean = false,

    // --------------------------------
    // COMPARISON (descriptive, NOT correctness)
    // --------------------------------

    val categoryChanged: Boolean,
    val confidenceChanged: Boolean,
    val contentTypeChanged: Boolean,
    val platformChanged: Boolean,
    val reviewStatusChanged: Boolean,
    val decisionAgreement: Boolean,
    val comparisonState: ComparisonState,
    val differenceReasons: List<DifferenceReason>,

    // --------------------------------
    // EVIDENCE
    // --------------------------------

    val evidenceAvailable: Boolean,
    val evidenceStrength: EvidenceStrength,
    val evidenceTypesUsed: List<String>,
    val rootCauseAvailable: Boolean = false,
    val decisionExplanation: String? = null,

    // --------------------------------
    // AUDIT
    // --------------------------------

    val timestampMs: Long,
    val fusionVersion: String? = null,

    // --------------------------------
    // PRIVACY (8B-10)
    //
    // Surfaces sanitization context WITHOUT raw content so
    // consumers can reason about evidence strength honestly.
    // --------------------------------

    val privacyPolicyVersion: String? = null,
    val privacyStatusLabel: String? = null,
    val privacyEvidenceLossLabels: List<String> = emptyList(),

    // --------------------------------
    // 8A BRIDGE
    // --------------------------------

    val bridgeCompatible: Boolean = true
) {

    /*
     * Whether the evidence-aware system decided on a
     * primary category.
     */
    val evidenceAwareDecided: Boolean
        get() = evidenceAwareDecision == DecisionState.DECIDED

    /*
     * Whether the evidence-aware system abstained.
     */
    val evidenceAwareAbstained: Boolean
        get() = evidenceAwareDecision != DecisionState.DECIDED

    /*
     * Whether human review is recommended.
     */
    val humanReviewRecommended: Boolean
        get() = comparisonState == ComparisonState.REVIEW_REQUIRED ||
                comparisonState == ComparisonState.CATEGORY_DISAGREEMENT

    /*
     * Whether confidence values differ meaningfully
     * (beyond floating-point tolerance).
     */
    val confidenceDifferMeaningfully: Boolean
        get() {
            if (baselineConfidence == null ||
                evidenceAwareConfidence == null) {
                return baselineConfidence !=
                    evidenceAwareConfidence
            }
            return kotlin.math.abs(
                baselineConfidence - evidenceAwareConfidence
            ) > CONFIDENCE_DIFFERENCE_THRESHOLD
        }

    companion object {

        /*
         * Adapter version for 8B-8. Increment when
         * comparison logic changes materially.
         */
        const val ADAPTER_VERSION =
            "evidence-aware-production-adapter-v1"

        /*
         * Diagnostic version for root-cause analysis
         * participation.
         */
        const val DIAGNOSTIC_VERSION = "diagnostic-v1"

        /*
         * Threshold above which two confidence values
         * are considered meaningfully different.
         */
        const val CONFIDENCE_DIFFERENCE_THRESHOLD = 0.01
    }
}

/*
 * Evidence strength assessment.
 *
 * A controlled vocabulary for evidence quality
 * independent of AI confidence or prediction
 * correctness.
 *
 * IMPORTANT:
 *   - Evidence strength ≠ prediction correctness
 *   - STRONG evidence does NOT mean correct prediction
 *   - NONE evidence does NOT mean incorrect prediction
 *   - Only ground truth establishes correctness
 */
enum class EvidenceStrength(val label: String) {

    /*
     * No usable evidence available.
     */
    NONE("NONE"),

    /*
     * Very limited evidence; decision carries
     * substantial risk.
     */
    WEAK("WEAK"),

    /*
     * Moderate evidence with some gaps or limitations.
     */
    MODERATE("MODERATE"),

    /*
     * Strong, diverse, well-covered evidence.
     * Still NOT a claim of correctness.
     */
    STRONG("STRONG")
}
