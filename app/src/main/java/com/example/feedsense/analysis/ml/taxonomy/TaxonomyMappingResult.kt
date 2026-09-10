package com.example.feedsense.analysis.ml.taxonomy

import com.example.feedsense.analysis.ml.ModelInferenceResult
import com.example.feedsense.analysis.ml.RankedPrediction

// --------------------------------
// TAXONOMY MAPPING RESULT (8B-15-7)
// --------------------------------
//
// The deterministic output of mapping a single model-native
// label to the FeedSense taxonomy (section 20).
//
// Result states are mutually exclusive and exhaustive.
// Do not collapse all states into nullable strings.
//
// A successful MAPPED result means:
//   "the model output has a defined correspondence to a
//    FeedSense category"
//
// It does NOT mean:
//   - the model prediction is correct
//   - the category is semantically perfect
//   - the model is accurate
//   - the taxonomy correspondence is empirically validated
//
// This distinction is critical for research safety
// (section 22).

/*
 * Sealed hierarchy of taxonomy mapping results.
 */
sealed interface TaxonomyMappingResult {

    // --------------------------------
    // COMMON PROPERTIES
    // --------------------------------

    val modelLabel: String
    val modelLabelIndex: Int
    val mappingId: String
    val mappingTableVersion: String
    val taxonomyVersion: String

    // --------------------------------
    // MAPPED
    // --------------------------------

    /*
     * The model label was successfully mapped to a single
     * FeedSense taxonomy key.
     */
    data class Mapped(
        override val modelLabel: String,
        override val modelLabelIndex: Int,
        val feedSenseTaxonomyKey: String,
        val feedSenseTaxonomyDisplayName: String,
        val status: MappingStatus,
        val mappingStrength: Double? = null,
        val rationale: MappingRationale? = null,
        val provenance: MappingProvenance =
            MappingProvenance.PROJECT_DEFINED,
        override val mappingId: String,
        override val mappingTableVersion: String,
        override val taxonomyVersion: String,
        val rationaleNotes: String? = null
    ) : TaxonomyMappingResult {

        init {
            require(feedSenseTaxonomyKey.isNotBlank()) {
                "feedSenseTaxonomyKey must be non-blank"
            }
            require(status == MappingStatus.DIRECT ||
                status == MappingStatus.MAPPED) {
                "Mapped result status must be DIRECT or MAPPED, " +
                    "was ${status.label}"
            }
        }
    }

    // --------------------------------
    // UNMAPPED
    // --------------------------------

    /*
     * The model label has no FeedSense taxonomy equivalent.
     * This is an intentional, documented absence - not an
     * error (section 12).
     */
    data class Unmapped(
        override val modelLabel: String,
        override val modelLabelIndex: Int,
        val reason: String,
        override val mappingId: String,
        override val mappingTableVersion: String,
        override val taxonomyVersion: String
    ) : TaxonomyMappingResult {

        init {
            require(reason.isNotBlank()) { "reason must be non-blank" }
        }
    }

    // --------------------------------
    // AMBIGUOUS
    // --------------------------------

    /*
     * The model label maps to more than one FeedSense
     * taxonomy key with no defined resolution rule
     * (section 11).
     *
     * Do not arbitrarily choose one candidate.
     */
    data class Ambiguous(
        override val modelLabel: String,
        override val modelLabelIndex: Int,
        val candidateTaxonomyKeys: List<String>,
        val ambiguityReason: String,
        override val mappingId: String,
        override val mappingTableVersion: String,
        override val taxonomyVersion: String
    ) : TaxonomyMappingResult {

        init {
            require(candidateTaxonomyKeys.isNotEmpty()) {
                "AMBIGUOUS result must have >= 1 candidate, " +
                    "had ${candidateTaxonomyKeys.size}"
            }
            require(ambiguityReason.isNotBlank()) {
                "ambiguityReason must be non-blank"
            }
        }
    }

    // --------------------------------
    // INVALID
    // --------------------------------

    /*
     * Mapping failed validation or compatibility checks
     * (section 17).
     */
    data class Invalid(
        override val modelLabel: String,
        override val modelLabelIndex: Int,
        val failureReason: String,
        val failureCode: MappingFailureCode,
        override val mappingId: String,
        override val mappingTableVersion: String,
        override val taxonomyVersion: String
    ) : TaxonomyMappingResult {

        init {
            require(failureReason.isNotBlank()) {
                "failureReason must be non-blank"
            }
        }
    }
}

/*
 * Structured failure codes for INVALID mapping results.
 */
enum class MappingFailureCode(val label: String) {
    INVALID_MODEL_INDEX("INVALID_MODEL_INDEX"),
    MISSING_TAXONOMY_KEY("MISSING_TAXONOMY_KEY"),
    INVALID_TAXONOMY_KEY("INVALID_TAXONOMY_KEY"),
    OBSOLETE_TAXONOMY_VERSION("OBSOLETE_TAXONOMY_VERSION"),
    WRONG_ARTIFACT_IDENTITY("WRONG_ARTIFACT_IDENTITY"),
    DUPLICATE_MAPPING("DUPLICATE_MAPPING"),
    CONFLICTING_MAPPING("CONFLICTING_MAPPING"),
    MAPPING_VERSION_MISMATCH("MAPPING_VERSION_MISMATCH"),
    INCOMPATIBLE_MAPPING("INCOMPATIBLE_MAPPING"),
    VALIDATION_ERROR("VALIDATION_ERROR")
}
