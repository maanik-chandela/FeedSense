package com.example.feedsense.analysis.evidence.decision

/*
 * Milestone 8B-7.
 *
 * Evidence-aware comparison (read-only).
 *
 * The head-to-head between the authoritative production
 * baseline (an existing FeedItem) and the experimental
 * 8B evidence-aware decision for the SAME item.
 *
 * IMPORTANT — this is NOT an accuracy measurement.
 *
 *   baselineCategory = SPORTS
 *   evidenceAware= EXPLICIT
 *
 *   -> categoryChanged = true
 *
 *   It does NOT mean either one is "correct". Correctness
 *   requires GroundTruth and belongs to the evaluation
 *   layer (Milestone 4 / 8A).
 *
 * Design:
 *   - Fully immutable (derived object, nothing mutated).
 *   - Baseline fields are read verbatim from the existing
 *     FeedItem; they are NEVER overwritten or re-interpreted.
 *   - Experimental fields come from the 8B-7 decision layer.
 *   - Both paths remain independently identifiable.
 *   - No privacy-sensitive payload (no screenshots, OCR,
 *     captions, or messages) is exported or persisted.
 *
 * This object is a READ-ONLY derivation. It references the
 * baseline FeedItem only as an immutable input and never
 * mutates it.
 */
data class EvidenceAwareComparison(
    val feedItemId: String,
    val sessionId: String? = null,

    // --------------------------------
    // AUTHORITATIVE BASELINE
    // --------------------------------

    val baselineCategory: String?,
    val baselineConfidence: Double?,
    val baselineContentType: String?,
    val baselineSkipped: Boolean,
    val baselineUncertaintyLevel: String?,
    val baselineNeedsReview: Boolean,
    val baselineModelVersion: String?,

    // --------------------------------
    // EXPERIMENTAL 8B RESULT
    // --------------------------------

    val evidenceAwareCategory: String?,
    val evidenceAwareSupport: Double?,
    val decisionState: DecisionState,
    val uncertainty: UncertaintyState,
    val evidenceAwareSecondaryCategory: String?,
    val evidenceAwareSourceDiversity: Int,

    // --------------------------------
    // COMPARISON (descriptive only, NOT correctness)
    // --------------------------------

    /*
     * Whether the two paths named a different primary
     * category. Descriptive only; NOT an error signal.
     */
    val categoryChanged: Boolean,

    /*
     * TRUE when the baseline had no category.
     */
    val baselineWasUnknown: Boolean,

    /*
     * TRUE when the evidence-aware path produced no
     * category (abstained: AMBIGUOUS/INSUFFICIENT/UNKNOWN
     * with no primary).
     */
    val evidenceAwareWasUnknown: Boolean,

    // --------------------------------
    // VERSIONING & PROVENANCE
    // --------------------------------

    val fusionVersion: String,
    val decisionVersion: String
)
