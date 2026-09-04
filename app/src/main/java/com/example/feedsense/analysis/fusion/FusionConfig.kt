package com.example.feedsense.analysis.fusion

// --------------------------------
// FUSION CONFIG (Milestone 8B-1)
// --------------------------------
//
// The single central configuration for the local evidence-fusion
// model. It holds EVERY tunable: provider weights, thresholds,
// confidence bands, ambiguity rule, duplicate-evidence policy,
// temporal-consistency settings and model state.
//
// There are no magic numbers scattered through the decision
// logic; the engine reads every constant from here.
//
// This is a FROZEN, versioned, immutable configuration object:
// a prediction is reproducible from
//
//     modelVersion + configurationVersion + input evidence
//
// (8B-1 section 66). Weights are documented initial values
// chosen by reasoning about provider reliability, NOT tuned
// against any frozen test set (8B-1 section 43).

data class FusionConfig(
    val modelVersion: String = MODEL_VERSION,
    val configurationVersion: String = CONFIG_VERSION,

    /*
     * Model lifecycle state. 8B-1 keeps Fusion V1 as
     * EXPERIMENTAL; it must pass evaluation before it may be
     * promoted to PRODUCTION. No auto-promotion (8B-1 section 67).
     */
    val modelState: String = MODEL_STATE_EXPERIMENTAL,

    // --------------------------------
    // PROVIDER WEIGHTS (8B-1 section 43)
    // --------------------------------
    //
    // These are the normalized weights per evidence FAMILY,
    // applied AFTER per-record reliability normalization.
    // They are NOT tuned against a test set; they reflect
    // conservative reasoning:
    //   - TEXT is the primary signal for this offline system
    //     (the current heuristic is text-based), so it carries
    //     the highest weight.
    //   - VISUAL would weigh heavily once a real vision model
    //     exists, but today it is only ever UNAVAILABLE, so its
    //     weight is inert unless concrete visual evidence appears.
    //   - TEMPORAL is a disambiguation/consistency signal, not a
    //     standalone category source.
    val textWeight: Double = 1.0,
    val platformWeight: Double = 0.3,
    val visualWeight: Double = 0.8,
    val temporalWeight: Double = 0.4,
    val interactionWeight: Double = 0.2,

    // --------------------------------
    // NORMALIZATION / RELIABILITY (8B-1 section 20/22)
    // --------------------------------
    //
    // Baseline reliability per family; a provider may override.
    // - OCR/text is reliable for text-heavy content.
    // - Platform detection is reliable when the app name appears.
    //   It is treated as CONTEXT, never as a category vote.
    // - Visual is currently unavailable (reliability unused).
    val textReliability: Double = 0.9,
    val platformReliability: Double = 0.8,
    val visualReliability: Double = 0.0,
    val temporalReliability: Double = 0.6,
    val interactionReliability: Double = 0.5,

    // --------------------------------
    // CANDIDATE / FUSION THRESHOLDS
    // --------------------------------

    /*
     * A candidate is kept for the final ranking only if its
     * fused score is at least this fraction of the leader's
     * score. Prevents long noisy tails.
     */
    val candidateRetainRatio: Double = 0.15,

    /*
     * TIE / AMBIGUITY rule (8B-1 section 33): when the gap
     * between the top two candidates is at most this fraction of
     * the leader, the decision is AMBIGUOUS instead of picking a
     * winner arbitrarily.
     */
    val ambiguityGapRatio: Double = 0.10,

    /*
     * Minimum evidence "mass" (sum of normalized, deduplicated,
     * reliability-scaled, weight-scaled evidence strength) below
     * which the decision is INSUFFICIENT_EVIDENCE.
     */
    val minEvidenceMass: Double = 0.25,

    /*
     * When any two candidates are supported by separate evidence
     * families that disagree, the decision is flagged
     * CONFLICTING_EVIDENCE when the conflict signal is at least
     * this strong.
     */
    val conflictThreshold: Double = 0.4,

    // --------------------------------
    // CONFIDENCE BANDS (8B-1 section 31)
    // --------------------------------

    /*
     * Final model confidence (after fusion + uncertainty
     * adjustment) maps to HIGH / MEDIUM / LOW / UNKNOWN using
     * these central breakpoints.
     */
    val bandHigh: Double = 0.7,
    val bandMedium: Double = 0.45,
    val bandLow: Double = 0.2,

    // --------------------------------
    // DUPLICATE-EVIDENCE POLICY (8B-1 section 19/44)
    // --------------------------------

    /*
     * When true, identical evidence (same family + same value
     * summary) contributes to the candidate score only once,
     * regardless of how many identical frames carried it. This
     * stops 8 duplicate OCR frames from inflating confidence.
     */
    val deduplicateIdentical: Boolean = true,

    // --------------------------------
    // TEMPORAL CONSISTENCY (8B-1 section 34)
    // --------------------------------

    /*
     * Number of distinct frames that must agree on a candidate
     * before a temporal-consistency bonus is applied.
     */
    val temporalAgreeFrames: Int = 2,
    val temporalAgreeBonus: Double = 0.05,

    /*
     * When >= this fraction of informative frames disagree with
     * the leading candidate, a conflict is surfaced.
     */
    val temporalDisagreeRatio: Double = 0.5,

    // --------------------------------
    // INFORMATION QUALITY (8B-1 section 18)
    // --------------------------------

    /*
     * A frame must have at least this much text (chars) to count
     * as text-informative for information-quality scoring.
     */
    val minInformativeTextChars: Int = 6
) {

    companion object {
        // Follows the existing naming convention local-<model>
        // (e.g. local-v6.0). Fusion V1 is the next generation.
        const val MODEL_VERSION = "local-fusion-v1"
        const val MODEL_STATE_EXPERIMENTAL = "EXPERIMENTAL"
        const val MODEL_STATE_VALIDATED = "VALIDATED"
        const val MODEL_STATE_PRODUCTION = "PRODUCTION"

        // Bump whenever the config changes so old predictions
        // are never silently reprocessed with new semantics.
        const val CONFIG_VERSION = "fusion-config-v1"

        /**
         * The frozen default configuration for 8B-1.
         */
        val DEFAULT: FusionConfig = FusionConfig()
    }
}
