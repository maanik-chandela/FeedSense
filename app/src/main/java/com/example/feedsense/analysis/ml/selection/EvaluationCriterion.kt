package com.example.feedsense.analysis.ml.selection

// --------------------------------
// EVALUATION CRITERIA (Milestone 8B-15-1)
// --------------------------------
//
// The 20-criterion decision matrix. Ratings are QUALITATIVE, not
// numeric: a numeric precision that was not measured would be a
// fabricated measurement, which 8B-15-1 explicitly forbids. Each
// score pairs a rating with the EvidenceLevel of its rationale.

/*
 * The 20 evaluation criteria, in stable declaration order.
 *
 * Criteria are intentionally architecture/runtime agnostic so the
 * same matrix is reused verbatim by any later milestone.
 */
enum class EvaluationCriterion(
    val id: String,
    val displayName: String,
    val focus: String
) {
    // --- correctness on FeedSense content -------------------
    TAXONOMY_ALIGNMENT(
        "taxonomy_alignment",
        "Taxonomy alignment",
        "How cleanly the output space maps to CategoryCatalog keys/domains."
    ),
    TEXT_OVERLAY_ROBUSTNESS(
        "text_overlay_robustness",
        "Text / caption overlay robustness",
        "Handling of captions, subtitles and UI overlays that drive short-form categories (memes, ads, clips)."
    ),
    FEED_CONTENT_GENERALIZATION(
        "feed_content_generalization",
        "Generalization to feed content",
        "Expected accuracy on the FeedSense domain; ImageNet/benchmark accuracy is NOT this."
    ),

    // --- size ------------------------------------------------
    MODEL_SIZE_MB(
        "model_size_mb",
        "Model size (MB)",
        "Packaged artifact size delivered with the app."
    ),
    RUNTIME_PACKAGE_SIZE(
        "runtime_package_size",
        "Runtime APK impact",
        "Library/AAR bytes added to the APK per supported ABI."
    ),
    PEAK_RAM(
        "peak_ram",
        "Peak inference RAM",
        "Peak memory during one inference on a mid-range phone."
    ),

    // --- speed & power ----------------------------------------
    INFERENCE_LATENCY(
        "inference_latency",
        "Per-inference latency",
        "Wall-clock cost per single-frame inference (CPU and accelerated)."
    ),
    ENERGY_PER_INFERENCE(
        "energy_per_inference",
        "Energy per inference",
        "Battery cost per inference at the sampling rate used by 8B-12."
    ),
    TEMPORAL_MULTI_FRAME(
        "temporal_multi_frame",
        "Temporal / multi-frame support",
        "Support for option 3/4 architectures (multi-frame, temporal aggregation)."
    ),

    // --- hardware reach --------------------------------------
    CPU_ONLY_FEASIBILITY(
        "cpu_only_feasibility",
        "CPU-only feasibility",
        "Runs acceptably without any accelerator delegation."
    ),
    ACCELERATOR_OPTIONS(
        "accelerator_options",
        "Accelerator options",
        "GPU / NPU / DSP delegation available and maintained."
    ),
    ANDROID_API_COMPATIBILITY(
        "android_api_compatibility",
        "Android API compatibility",
        "Compatibility with FeedSense minSdk 24 given 2026 Android guidance."
    ),

    // --- quantization ------------------------------------------
    FP16_SUPPORT(
        "fp16_support",
        "FP16 quantization",
        "Half-precision weight/activation support."
    ),
    INT8_SUPPORT(
        "int8_support",
        "INT8 quantization",
        "8-bit integer (incl. integer-only) support."
    ),
    FOUR_BIT_SUPPORT(
        "four_bit_support",
        "4-bit / INT4 quantization",
        "4-bit weight-only support (the only 4-bit path that is practical today)."
    ),

    // --- adoption / risk ---------------------------------------
    RUNTIME_MATURITY(
        "runtime_maturity",
        "Runtime maturity & ecosystem",
        "Stable releases, maintained mobile packaging, docs, community."
    ),
    LICENSE_COMPATIBILITY(
        "license_compatibility",
        "License compatibility",
        "Artifact + runtime license permit shipping in FeedSense; unverified == poor."
    ),
    INTEGRATION_EFFORT(
        "integration_effort",
        "Integration effort",
        "Engineering cost to reach the 8B-14 OnDeviceModel contract from this candidate."
    ),
    DETERMINISM_REPRODUCIBILITY(
        "determinism_reproducibility",
        "Determinism & reproducibility",
        "Stable outputs for fixed input + fixed artifact; provenance of results."
    ),

    // --- privacy --------------------------------------------------
    PRIVACY_BOUNDED(
        "privacy_bounded",
        "Privacy-bounded pipeline",
        "Fits FeedSense local-only, no-network, evidence-passed-through-8B-13 constraint."
    )
}

/*
 * Qualitative rating. No numeric precision is attached on
 * purpose: any number would imply a measurement that was not run
 * (8B-15-1 rule: figures that cannot be verified are not emitted).
 */
enum class CriterionRating(val label: String, val sortOrder: Int) {
    STRONG("STRONG", 0),
    ADEQUATE("ADEQUATE", 1),
    WEAK("WEAK", 2),
    UNKNOWN("UNKNOWN", 3),
    NOT_APPLICABLE("NOT_APPLICABLE", 4);

    companion object {
        fun fromLabel(label: String): CriterionRating? =
            entries.firstOrNull { it.label == label }
    }
}

/*
 * One cell of the 20-criterion matrix.
 *
 *   - rating   : qualitative judgment (STRONG..NOT_APPLICABLE).
 *   - evidence : how the rationale was obtained. `UNKNOWN` cells
 *                carry rationale "No evidence in 8B-15-1", never
 *                an invented explanation.
 *   - rationale: why; blank is rejected so a cell can never be
 *                silently empty.
 */
data class CriterionScore(
    val criterion: EvaluationCriterion,
    val rating: CriterionRating,
    val evidence: EvidenceLevel,
    val rationale: String
) {
    init {
        require(rationale.isNotBlank()) { "rationale must be non-blank" }
        val unknownRationales = listOf(
            "No evidence in 8B-15-1. Unknown.",
            "Not applicable."
        )
        val isUnknown =
            rating == CriterionRating.UNKNOWN ||
                evidence == EvidenceLevel.UNKNOWN ||
                rating == CriterionRating.NOT_APPLICABLE
        if (isUnknown) {
            require(rationale.length <= 120) {
                "UNKNOWN/NOT_APPLICABLE cells keep rationale short and honest, got: $rationale"
            }
        }
    }
}