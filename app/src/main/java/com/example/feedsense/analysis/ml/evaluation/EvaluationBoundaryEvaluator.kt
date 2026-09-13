package com.example.feedsense.analysis.ml.evaluation

import com.example.feedsense.model.GroundTruth

// --------------------------------
// EVALUATION BOUNDARY EVALUATOR (8B-15-8)
// --------------------------------
//
// The core orchestrator for the taxonomy-mapped prediction
// evaluation boundary.
//
// Pipeline:
//   Model Output
//   -> Output Interpretation
//   -> Taxonomy Mapping
//   -> Eligibility Decision
//   -> Structural Comparison
//
// This evaluator does NOT:
//   - Calculate accuracy, precision, recall, F1
//   - Produce confusion matrices
//   - Compute agreement coefficients
//   - Tune model thresholds
//   - Train or fine-tune anything
//   - Modify GroundTruth
//   - Infer ground truth from model output
//   - Automatically accept predictions as ground truth
//
// This evaluator DOES:
//   - Produce a deterministic eligibility decision
//   - Produce a deterministic structural comparison
//   - Freeze a historical snapshot of all inputs
//   - Maintain the distinction between model prediction
//     and human ground truth
//   - Preserve auditability and determinism

/*
 * Complete evaluation boundary result for a single
 * prediction candidate.
 *
 * Contains the full chain:
 *   candidate snapshot -> eligibility -> comparison
 *
 * Each component is independently auditable.
 */
data class EvaluationBoundaryResult(
    val candidateSnapshot: EvaluationCandidateSnapshot,
    val eligibility: EvaluationEligibility,
    val comparison: EvaluationComparisonResult? = null,
    val evaluationBoundaryVersion: String = VERSION
) {
    companion object {
        const val VERSION = "8B-15-8-v1"
    }
}

/*
 * The evaluation boundary evaluator.
 *
 * Produces deterministic, auditable evaluation boundary
 * results from taxonomy-mapped predictions and ground
 * truth references.
 *
 * Determinism rules:
 *   - For identical candidate snapshot and ground truth
 *     input, the result is identical.
 *   - No randomness, no timestamps affecting logic,
 *     no locale-dependent behavior.
 *
 * Independence rules:
 *   - Model confidence does not determine eligibility.
 *   - Model confidence does not alter ground truth.
 *   - Ground truth is never modified.
 *   - Ground truth is never inferred from model output.
 */
object EvaluationBoundaryEvaluator {

    // --------------------------------
    // PUBLIC API: full evaluation
    // --------------------------------

    /*
     * Evaluate a single prediction candidate against
     * ground truth.
     *
     * Produces:
     *   1. A frozen candidate snapshot
     *   2. An eligibility decision
     *   3. A structural comparison (if eligible)
     *
     * The same inputs always produce the same output.
     */
    fun evaluate(
        snapshot: EvaluationCandidateSnapshot,
        groundTruth: GroundTruth,
        groundTruthTaxonomyVersion: String? = null,
        annotationVersion: String? = null
    ): EvaluationBoundaryResult {

        val eligibility = snapshot.eligibility

        val comparison = if (eligibility.isEligible) {
            val comparisonInput =
                EvaluationComparisonInput.fromCandidateAndTruth(
                    snapshot = snapshot,
                    truth = groundTruth,
                    groundTruthTaxonomyVersion =
                        groundTruthTaxonomyVersion,
                    annotationVersion = annotationVersion
                )
            EvaluationComparison.compare(comparisonInput)
        } else {
            null
        }

        return EvaluationBoundaryResult(
            candidateSnapshot = snapshot,
            eligibility = eligibility,
            comparison = comparison
        )
    }

    // --------------------------------
    // PUBLIC API: eligibility only
    // --------------------------------

    /*
     * Produce only the eligibility decision without
     * performing a comparison. Useful when the ground
     * truth is not yet available.
     */
    fun eligibilityOnly(
        snapshot: EvaluationCandidateSnapshot
    ): EvaluationBoundaryResult {
        return EvaluationBoundaryResult(
            candidateSnapshot = snapshot,
            eligibility = snapshot.eligibility,
            comparison = null
        )
    }

    // --------------------------------
    // PUBLIC API: batch eligibility
    // --------------------------------

    /*
     * Produce eligibility decisions for a batch of
     * candidate snapshots. Each decision is independent.
     */
    fun batchEligibility(
        snapshots: List<EvaluationCandidateSnapshot>
    ): List<EvaluationBoundaryResult> {
        return snapshots.map { eligibilityOnly(it) }
    }
}
