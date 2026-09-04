package com.example.feedsense.analysis.evidence.decision

/*
 * Milestone 8B-7.
 *
 * Baseline prediction.
 *
 * A lightweight, immutable wrapper around the EXISTING
 * classifier's item-level prediction. This represents the
 * current production / baseline interpretation of an item
 * so it can be compared against the evidence-aware 8B-7
 * decision in a reproducible, unbiased way.
 *
 * Important:
 *   - The baseline is NEVER overwritten or mutated.
 *   - The baseline keeps its own confidence, category,
 *     and uncertainty; these are NOT touched by the
 *     8B-7 evidence-aware decision layer.
 *   - Both pathways remain independently reproducible.
 *
 * Fields are deliberately the minimal surface needed for
 * a fair comparison:
 *   - primary category + its classifier confidence
 *   - secondary / multi-label categories
 *   - the item's own uncertainty / review flags
 */
data class BaselinePrediction(
    val feedItemId: String? = null,

    /*
     * The existing classifier's chosen primary category.
     * May be null when the classifier did not decide.
     */
    val primaryCategory: String? = null,

    /*
     * The classifier's own confidence (0..1).
     * Preserved verbatim; distinct from evidence
     * assessment.
     */
    val classifierConfidence: Double? = null,

    /*
     * The item's existing uncertainty level label
     * (e.g. HIGH / MEDIUM / LOW), if present.
     */
    val uncertaintyLevel: String? = null,

    /*
     * Whether the existing pipeline flagged the item for
     * review.
     */
    val needsReview: Boolean = false,

    /*
     * Secondary categories from the existing classifier.
     */
    val secondaryCategories: List<String> = emptyList(),

    /*
     * Per-category classifier scores (0..1), if present.
     */
    val categoryScores: Map<String, Double> = emptyMap(),

    /*
     * The existing classifier's model version.
     */
    val modelVersion: String? = null,

    /*
     * Platform / contentType from the baseline item, for
     * context in evaluation.
     */
    val platform: String? = null,
    val contentType: String? = null,

    /*
     * Extra carrier signal for provenance of the baseline
     * (e.g. "representative-frame" or "frame-aggregate").
     */
    val provenance: String = "existing-classifier"
)
