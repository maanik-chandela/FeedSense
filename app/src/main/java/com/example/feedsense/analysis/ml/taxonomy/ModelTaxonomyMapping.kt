package com.example.feedsense.analysis.ml.taxonomy

// --------------------------------
// MODEL TAXONOMY MAPPING (8B-15-7)
// --------------------------------
//
// A structured mapping entry that determines how a single
// model-native label corresponds to the FeedSense research
// taxonomy (sections 5, 9, 10, 11).
//
// Each entry is:
//   - Deterministic: identical inputs always produce the
//     same mapping result.
//   - Immutable: once created, never mutated.
//   - Auditable: carries provenance, rationale, and
//     version identity.
//   - Non-forcing: a model label may legitimately have no
//     FeedSense equivalent (UNMAPPED).
//
// A mapping belongs to a specific model artifact. Do not
// accidentally apply mappings from Model A to Model B even
// if both contain a label with the same string (section 18).

/*
 * One mapping entry from a model-native label to the
 * FeedSense taxonomy.
 */
data class ModelTaxonomyMapping(
    val mappingId: String,

    // --------------------------------
    // MODEL ARTIFACT IDENTITY
    // --------------------------------

    val modelArtifactId: String,
    val modelArtifactVersion: String,

    // --------------------------------
    // MODEL LABEL IDENTITY
    // --------------------------------

    val modelLabelIndex: Int,
    val modelLabel: String,
    val modelLabelVersion: String? = null,

    // --------------------------------
    // FEEDSENSE TAXONOMY TARGET
    // --------------------------------

    /*
     * The FeedSense taxonomy key this label maps to.
     * Null when the status is UNMAPPED or UNSUPPORTED.
     */
    val feedSenseTaxonomyKey: String? = null,

    // --------------------------------
    // MAPPING STATUS
    // --------------------------------

    val status: MappingStatus,

    // --------------------------------
    // MAPPING STRENGTH
    // --------------------------------

    /*
     * Confidence/strength of the taxonomy CORRESPONDENCE,
     * NOT the model prediction confidence (section 7).
     *
     * A value of 1.0 means the mapping is considered exact.
     * A value of 0.5 means the correspondence is weak or
     * provisional. Null when strength is not applicable
     * (e.g. DIRECT mappings).
     */
    val mappingStrength: Double? = null,

    // --------------------------------
    // RATIONALE + PROVENANCE
    // --------------------------------

    val rationale: MappingRationale? = null,
    val provenance: MappingProvenance = MappingProvenance.PROJECT_DEFINED,

    // --------------------------------
    // VERSIONS
    // --------------------------------

    val mappingTableVersion: String,
    val taxonomyVersion: String,

    // --------------------------------
    // NOTES
    // --------------------------------

    val rationaleNotes: String? = null
) {

    init {
        require(mappingId.isNotBlank()) { "mappingId must be non-blank" }
        require(modelArtifactId.isNotBlank()) {
            "modelArtifactId must be non-blank"
        }
        require(modelArtifactVersion.isNotBlank()) {
            "modelArtifactVersion must be non-blank"
        }
        require(modelLabelIndex >= 0) {
            "modelLabelIndex must be >= 0"
        }
        require(modelLabel.isNotBlank()) { "modelLabel must be non-blank" }
        require(mappingTableVersion.isNotBlank()) {
            "mappingTableVersion must be non-blank"
        }
        require(taxonomyVersion.isNotBlank()) {
            "taxonomyVersion must be non-blank"
        }
        mappingStrength?.let {
            require(it in 0.0..1.0) {
                "mappingStrength must be in [0.0, 1.0], was $it"
            }
        }

        // --------------------------------
        // STATUS CONSISTENCY
        // --------------------------------

        when (status) {
            MappingStatus.DIRECT,
            MappingStatus.MAPPED -> {
                require(feedSenseTaxonomyKey != null) {
                    "status=$status requires feedSenseTaxonomyKey"
                }
            }
            MappingStatus.UNMAPPED,
            MappingStatus.UNSUPPORTED,
            MappingStatus.REJECTED -> {
                require(feedSenseTaxonomyKey == null) {
                    "status=$status must not have feedSenseTaxonomyKey"
                }
            }
            MappingStatus.AMBIGUOUS -> {
                // AMBIGUOUS may or may not have a key; the
                // key represents the primary candidate, if any.
            }
        }

        // --------------------------------
        // RATIONALE CONSISTENCY
        // --------------------------------

        when (status) {
            MappingStatus.DIRECT -> {
                require(rationale == null || rationale ==
                    MappingRationale.SEMANTIC_EQUIVALENCE) {
                    "DIRECT mappings should not have a " +
                        "non-SEMANTIC_EQUIVALENCE rationale"
                }
            }
            MappingStatus.MAPPED -> {
                require(rationale != null) {
                    "MAPPED status requires a rationale"
                }
            }
            else -> { /* rationale optional */ }
        }
    }

    /*
     * Stable composite key for this mapping entry.
     */
    val key: String
        get() = "$modelArtifactId:$modelLabelIndex:$modelLabel"

    /*
     * Whether this mapping targets a specific FeedSense
     * taxonomy key.
     */
    val hasTarget: Boolean
        get() = feedSenseTaxonomyKey != null
}
