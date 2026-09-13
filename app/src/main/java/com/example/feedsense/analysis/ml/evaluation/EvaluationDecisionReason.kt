package com.example.feedsense.analysis.ml.evaluation

// --------------------------------
// EVALUATION DECISION REASON (8B-15-8)
// --------------------------------
//
// A stable, machine-readable reason-code taxonomy for
// eligibility and comparison decisions.
//
// Every nontrivial decision has an explicit reason.
// Free-form strings are used only as supplementary detail,
// never as the primary machine-readable explanation.
//
// The reason codes are:
//   - Stable: adding new reasons never renames old ones.
//   - Exhaustive: every decision path maps to a reason.
//   - Deterministic: the same structural state always
//     produces the same reason code.
//   - Auditable: reason codes can be traced to specific
//     code paths.

/*
 * Deterministic reason codes for evaluation eligibility
 * and comparison decisions.
 */
enum class EvaluationDecisionReason(val label: String) {

    // --------------------------------
    // ELIGIBILITY: VALID
    // --------------------------------

    /*
     * The prediction was directly mapped to a FeedSense
     * taxonomy key with no transformation.
     */
    VALID_DIRECT_MAPPING("VALID_DIRECT_MAPPING"),

    /*
     * The prediction was mapped to a FeedSense taxonomy
     * key via an explicit mapping entry with rationale.
     */
    VALID_MAPPED_MAPPING("VALID_MAPPED_MAPPING"),

    // --------------------------------
    // ELIGIBILITY: NOT ELIGIBLE
    // --------------------------------

    /*
     * No taxonomy mapping entry was found for the model
     * label, and the label does not match any FeedSense
     * taxonomy key.
     */
    UNMAPPED_MODEL_LABEL("UNMAPPED_MODEL_LABEL"),

    /*
     * The model label maps to multiple FeedSense taxonomy
     * keys with no defined resolution rule.
     */
    AMBIGUOUS_MAPPING("AMBIGUOUS_MAPPING"),

    /*
     * The mapping was established but is outside the known
     * label set for this model artifact.
     */
    UNSUPPORTED_MAPPING("UNSUPPORTED_MAPPING"),

    /*
     * The mapping was explicitly rejected (e.g. semantic
     * mismatch confirmed by review).
     */
    REJECTED_MAPPING("REJECTED_MAPPING"),

    /*
     * The mapping failed validation or compatibility checks.
     */
    INVALID_MAPPING("INVALID_MAPPING"),

    /*
     * No model prediction output was available.
     */
    MISSING_PREDICTION("MISSING_PREDICTION"),

    /*
     * No taxonomy version was specified for the candidate.
     */
    MISSING_TAXONOMY_VERSION("MISSING_TAXONOMY_VERSION"),

    /*
     * No taxonomy mapping was provided.
     */
    MISSING_MAPPING("MISSING_MAPPING"),

    /*
     * The mapping taxonomy version does not match the
     * expected taxonomy version.
     */
    TAXONOMY_VERSION_MISMATCH("TAXONOMY_VERSION_MISMATCH"),

    // --------------------------------
    // COMPARISON: STRUCTURAL
    // --------------------------------

    /*
     * Prediction and ground truth refer to the same
     * compatible FeedSense taxonomy identity/version and
     * same taxonomy ID.
     */
    TAXONOMY_ID_MATCH("TAXONOMY_ID_MATCH"),

    /*
     * Both sides are valid and comparable, but taxonomy
     * IDs differ.
     */
    TAXONOMY_ID_MISMATCH("TAXONOMY_ID_MISMATCH"),

    /*
     * The comparison cannot legitimately be made because
     * of structural incompatibility (unmapped prediction,
     * missing ground truth, version mismatch, etc.).
     */
    STRUCTURALLY_UNCOMPARABLE("STRUCTURALLY_UNCOMPARABLE"),

    // --------------------------------
    // COMPARISON: GROUND TRUTH
    // --------------------------------

    /*
     * The ground truth record is missing or in an invalid
     * annotation state.
     */
    INVALID_GROUND_TRUTH_STATE("INVALID_GROUND_TRUTH_STATE"),

    /*
     * The ground truth taxonomy version does not match
     * the prediction taxonomy version.
     */
    GROUND_TRUTH_VERSION_MISMATCH("GROUND_TRUTH_VERSION_MISMATCH"),

    /*
     * The ground truth taxonomy ID is not recognized in
     * the current taxonomy catalog.
     */
    GROUND_TRUTH_UNKNOWN_TAXONOMY_ID("GROUND_TRUTH_UNKNOWN_TAXONOMY_ID")
}
