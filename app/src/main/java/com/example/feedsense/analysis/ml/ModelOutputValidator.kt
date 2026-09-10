package com.example.feedsense.analysis.ml

// --------------------------------
// MODEL OUTPUT VALIDATOR (8B-14)
// --------------------------------
//
// Guards the boundary between a model's raw output and the
// evaluation layer. Malformed output becomes an explicit
// OUTPUT_INVALID result - never a crash, never a silently
// accepted bad score (8B-14 sections 42-44).
//
// Validation rules:
//   - category must exist in the allowed model mapping
//   - confidence must be finite and within the spec range
//   - duplicate categories are removed deterministically
//     (highest confidence kept)
//   - ordering is deterministic (confidence desc, category
//     asc on ties)
//   - the ranked list is capped at maxTopK
//   - an empty candidate list is a malformed result for an
//     image-classification model
//
// Invalid confidence values (NaN, Infinity, negative, > 1)
// are REJECTED, not silently coerced to a fabricated value.

data class OutputValidation(
    val valid: Boolean,
    val status: InferenceStatus,
    val ranked: List<RankedPrediction>,
    val message: String? = null
)

class ModelOutputValidator(
    private val allowedCategories: Set<String>,
    private val confidenceRange: ClosedFloatingPointRange<Double> =
        0.0..1.0,
    private val maxTopK: Int = ModelOutputSpec.DEFAULT_MAX_TOP_K
) {

    init {
        require(allowedCategories.isNotEmpty()) {
            "allowedCategories must not be empty"
        }
        require(maxTopK >= 1) { "maxTopK must be >= 1" }
    }

    /*
     * Companion factory bound to a model's output spec.
     */
    constructor(spec: ModelOutputSpec) : this(
        allowedCategories = spec.labels.toSet(),
        confidenceRange = spec.confidenceRange,
        maxTopK = spec.maxTopK
    )

    fun validate(
        candidates: List<RankedPrediction>
    ): OutputValidation {

        if (candidates.isEmpty()) {
            return invalid("model produced no predictions")
        }

        for (candidate in candidates) {
            val confidence = candidate.confidence
            if (!confidence.isFinite()) {
                return invalid(
                    "non-finite confidence for ${candidate.category}"
                )
            }
            if (confidence !in confidenceRange) {
                return invalid(
                    "confidence ${candidate.category}=$confidence " +
                        "outside $confidenceRange"
                )
            }
            if (candidate.category !in allowedCategories) {
                return invalid(
                    "category ${candidate.category} not in allowed model mapping"
                )
            }
        }

        val deduplicated =
            RankedPrediction.deduplicateDeterministic(candidates)

        val ranked =
            deduplicated.take(maxTopK)

        return OutputValidation(
            valid = true,
            status = InferenceStatus.SUCCESS,
            ranked = ranked
        )
    }

    private fun invalid(message: String): OutputValidation {
        return OutputValidation(
            valid = false,
            status = InferenceStatus.OUTPUT_INVALID,
            ranked = emptyList(),
            message = message
        )
    }
}