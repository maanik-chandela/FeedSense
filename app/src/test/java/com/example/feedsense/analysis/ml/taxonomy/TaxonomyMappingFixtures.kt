package com.example.feedsense.analysis.ml.taxonomy

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.analysis.SchemaFreeze

// --------------------------------
// TAXONOMY MAPPING FIXTURES (8B-15-7)
// --------------------------------
//
// Golden deterministic fixture set for taxonomy mapping
// tests (section 26).
//
// Includes:
//   - Direct mappings
//   - Many-to-one mappings
//   - Unmapped labels
//   - Ambiguous mappings
//   - Invalid mappings
//
// Uses synthetic model labels where necessary.
// Does not use real user data.

object TaxonomyMappingFixtures {

    // --------------------------------
    // VERSIONS
    // --------------------------------

    val TAXONOMY_VERSION: String =
        TaxonomyMappingVersion.computeTaxonomyVersion(
            frozenVersion = SchemaFreeze.FREEZE_VERSION,
            sortedCategoryKeys = CategoryCatalog.keys.sorted()
        )

    const val MAPPING_TABLE_VERSION = "mapping-v1"
    const val MODEL_ARTIFACT_ID = "test-mobilenetv4-conv-s"
    const val MODEL_ARTIFACT_VERSION = "v1-test"

    // --------------------------------
    // DIRECT MAPPINGS
    // --------------------------------

    /*
     * Model labels that map 1:1 to FeedSense taxonomy keys.
     * The model label IS the taxonomy key.
     */
    val DIRECT_SPORTS = ModelTaxonomyMapping(
        mappingId = "direct-sports",
        modelArtifactId = MODEL_ARTIFACT_ID,
        modelArtifactVersion = MODEL_ARTIFACT_VERSION,
        modelLabelIndex = 0,
        modelLabel = "sports",
        feedSenseTaxonomyKey = "sports",
        status = MappingStatus.DIRECT,
        rationale = MappingRationale.SEMANTIC_EQUIVALENCE,
        provenance = MappingProvenance.PROJECT_DEFINED,
        mappingTableVersion = MAPPING_TABLE_VERSION,
        taxonomyVersion = TAXONOMY_VERSION
    )

    val DIRECT_ENTERTAINMENT = ModelTaxonomyMapping(
        mappingId = "direct-entertainment",
        modelArtifactId = MODEL_ARTIFACT_ID,
        modelArtifactVersion = MODEL_ARTIFACT_VERSION,
        modelLabelIndex = 1,
        modelLabel = "entertainment",
        feedSenseTaxonomyKey = "entertainment",
        status = MappingStatus.DIRECT,
        rationale = MappingRationale.SEMANTIC_EQUIVALENCE,
        provenance = MappingProvenance.PROJECT_DEFINED,
        mappingTableVersion = MAPPING_TABLE_VERSION,
        taxonomyVersion = TAXONOMY_VERSION
    )

    val DIRECT_EDUCATION = ModelTaxonomyMapping(
        mappingId = "direct-education",
        modelArtifactId = MODEL_ARTIFACT_ID,
        modelArtifactVersion = MODEL_ARTIFACT_VERSION,
        modelLabelIndex = 2,
        modelLabel = "education",
        feedSenseTaxonomyKey = "education",
        status = MappingStatus.DIRECT,
        rationale = MappingRationale.SEMANTIC_EQUIVALENCE,
        provenance = MappingProvenance.PROJECT_DEFINED,
        mappingTableVersion = MAPPING_TABLE_VERSION,
        taxonomyVersion = TAXONOMY_VERSION
    )

    // --------------------------------
    // MANY-TO-ONE MAPPINGS
    // --------------------------------

    /*
     * Multiple model labels map to one FeedSense category.
     */
    val MANY_TO_ONE_MODEL_A = ModelTaxonomyMapping(
        mappingId = "m2o-modelA",
        modelArtifactId = MODEL_ARTIFACT_ID,
        modelArtifactVersion = MODEL_ARTIFACT_VERSION,
        modelLabelIndex = 3,
        modelLabel = "model_sports_variant",
        feedSenseTaxonomyKey = "sports",
        status = MappingStatus.MAPPED,
        rationale = MappingRationale.BROADER_FEEDSENSE_CATEGORY,
        provenance = MappingProvenance.PROJECT_DEFINED,
        mappingTableVersion = MAPPING_TABLE_VERSION,
        taxonomyVersion = TAXONOMY_VERSION,
        rationaleNotes = "Model uses a generic sports label " +
            "that corresponds to the FeedSense sports domain"
    )

    val MANY_TO_ONE_MODEL_B = ModelTaxonomyMapping(
        mappingId = "m2o-modelB",
        modelArtifactId = MODEL_ARTIFACT_ID,
        modelArtifactVersion = MODEL_ARTIFACT_VERSION,
        modelLabelIndex = 4,
        modelLabel = "model_athletics",
        feedSenseTaxonomyKey = "sports",
        status = MappingStatus.MAPPED,
        rationale = MappingRationale.BROADER_FEEDSENSE_CATEGORY,
        provenance = MappingProvenance.PROJECT_DEFINED,
        mappingTableVersion = MAPPING_TABLE_VERSION,
        taxonomyVersion = TAXONOMY_VERSION,
        rationaleNotes = "Model athletics label maps to " +
            "FeedSense sports domain"
    )

    // --------------------------------
    // UNMAPPED LABELS
    // --------------------------------

    /*
     * A valid model label with no FeedSense equivalent.
     */
    val UNMAPPED_LABEL = ModelTaxonomyMapping(
        mappingId = "unmapped-weather",
        modelArtifactId = MODEL_ARTIFACT_ID,
        modelArtifactVersion = MODEL_ARTIFACT_VERSION,
        modelLabelIndex = 5,
        modelLabel = "weather_forecast",
        status = MappingStatus.UNMAPPED,
        provenance = MappingProvenance.PROJECT_DEFINED,
        mappingTableVersion = MAPPING_TABLE_VERSION,
        taxonomyVersion = TAXONOMY_VERSION,
        rationaleNotes = "Weather forecast has no equivalent " +
            "in the FeedSense content taxonomy"
    )

    // --------------------------------
    // AMBIGUOUS MAPPINGS
    // --------------------------------

    /*
     * A model label that could map to multiple taxonomy keys.
     */
    val AMBIGUOUS_LABEL = ModelTaxonomyMapping(
        mappingId = "ambiguous-lifestyle",
        modelArtifactId = MODEL_ARTIFACT_ID,
        modelArtifactVersion = MODEL_ARTIFACT_VERSION,
        modelLabelIndex = 6,
        modelLabel = "daily_vlog",
        status = MappingStatus.AMBIGUOUS,
        provenance = MappingProvenance.PROVISIONAL,
        mappingTableVersion = MAPPING_TABLE_VERSION,
        taxonomyVersion = TAXONOMY_VERSION,
        rationaleNotes = "daily_vlog could map to lifestyle " +
            "or entertainment; no resolution rule defined"
    )

    // --------------------------------
    // UNSUPPORTED LABEL
    // --------------------------------

    val UNSUPPORTED_LABEL = ModelTaxonomyMapping(
        mappingId = "unsupported-unknown",
        modelArtifactId = MODEL_ARTIFACT_ID,
        modelArtifactVersion = MODEL_ARTIFACT_VERSION,
        modelLabelIndex = 7,
        modelLabel = "unknown_model_class",
        status = MappingStatus.UNSUPPORTED,
        provenance = MappingProvenance.PROJECT_DEFINED,
        mappingTableVersion = MAPPING_TABLE_VERSION,
        taxonomyVersion = TAXONOMY_VERSION,
        rationaleNotes = "Label not in the model's declared output set"
    )

    // --------------------------------
    // REJECTED MAPPING
    // --------------------------------

    val REJECTED_LABEL = ModelTaxonomyMapping(
        mappingId = "rejected-politics",
        modelArtifactId = MODEL_ARTIFACT_ID,
        modelArtifactVersion = MODEL_ARTIFACT_VERSION,
        modelLabelIndex = 8,
        modelLabel = "model_political_commentary",
        status = MappingStatus.REJECTED,
        provenance = MappingProvenance.HUMAN_REVIEWED,
        mappingTableVersion = MAPPING_TABLE_VERSION,
        taxonomyVersion = TAXONOMY_VERSION,
        rationaleNotes = "Rejected: model political commentary " +
            "label does not align with FeedSense politics category"
    )

    // --------------------------------
    // COMPLETE MAPPING TABLE
    // --------------------------------

    val ALL_MAPPINGS: List<ModelTaxonomyMapping> = listOf(
        DIRECT_SPORTS,
        DIRECT_ENTERTAINMENT,
        DIRECT_EDUCATION,
        MANY_TO_ONE_MODEL_A,
        MANY_TO_ONE_MODEL_B,
        UNMAPPED_LABEL,
        AMBIGUOUS_LABEL,
        UNSUPPORTED_LABEL,
        REJECTED_LABEL
    )

    // --------------------------------
    // MAPPING VERSION
    // --------------------------------

    val MAPPING_VERSION = TaxonomyMappingVersion(
        mappingTableVersion = MAPPING_TABLE_VERSION,
        taxonomyVersion = TAXONOMY_VERSION,
        modelArtifactId = MODEL_ARTIFACT_ID,
        modelArtifactVersion = MODEL_ARTIFACT_VERSION,
        labelCount = ALL_MAPPINGS.size,
        taxonomyKeyCount = CategoryCatalog.keys.size
    )

    // --------------------------------
    // CONFLICTING MAPPING (for validation tests)
    // --------------------------------

    val CONFLICTING_A = ModelTaxonomyMapping(
        mappingId = "conflict-a",
        modelArtifactId = MODEL_ARTIFACT_ID,
        modelArtifactVersion = MODEL_ARTIFACT_VERSION,
        modelLabelIndex = 0,
        modelLabel = "sports",
        feedSenseTaxonomyKey = "sports",
        status = MappingStatus.DIRECT,
        rationale = MappingRationale.SEMANTIC_EQUIVALENCE,
        provenance = MappingProvenance.PROJECT_DEFINED,
        mappingTableVersion = MAPPING_TABLE_VERSION,
        taxonomyVersion = TAXONOMY_VERSION
    )

    val CONFLICTING_B = ModelTaxonomyMapping(
        mappingId = "conflict-b",
        modelArtifactId = MODEL_ARTIFACT_ID,
        modelArtifactVersion = MODEL_ARTIFACT_VERSION,
        modelLabelIndex = 0,
        modelLabel = "sports",
        feedSenseTaxonomyKey = "entertainment",
        status = MappingStatus.MAPPED,
        rationale = MappingRationale.DOMAIN_SPECIFIC_INTERPRETATION,
        provenance = MappingProvenance.PROVISIONAL,
        mappingTableVersion = MAPPING_TABLE_VERSION,
        taxonomyVersion = TAXONOMY_VERSION,
        rationaleNotes = "Conflicting entry for testing"
    )

    // --------------------------------
    // INVALID TAXONOMY KEY (for validation tests)
    // --------------------------------

    val INVALID_TAXONOMY_KEY = ModelTaxonomyMapping(
        mappingId = "invalid-key",
        modelArtifactId = MODEL_ARTIFACT_ID,
        modelArtifactVersion = MODEL_ARTIFACT_VERSION,
        modelLabelIndex = 9,
        modelLabel = "fake_label",
        feedSenseTaxonomyKey = "nonexistent_category",
        status = MappingStatus.MAPPED,
        rationale = MappingRationale.SEMANTIC_EQUIVALENCE,
        provenance = MappingProvenance.PROJECT_DEFINED,
        mappingTableVersion = MAPPING_TABLE_VERSION,
        taxonomyVersion = TAXONOMY_VERSION
    )

    // --------------------------------
    // WRONG ARTIFACT (for compatibility tests)
    // --------------------------------

    val WRONG_ARTIFACT = ModelTaxonomyMapping(
        mappingId = "wrong-artifact",
        modelArtifactId = "different-model",
        modelArtifactVersion = "v1",
        modelLabelIndex = 0,
        modelLabel = "sports",
        feedSenseTaxonomyKey = "sports",
        status = MappingStatus.DIRECT,
        rationale = MappingRationale.SEMANTIC_EQUIVALENCE,
        provenance = MappingProvenance.PROJECT_DEFINED,
        mappingTableVersion = MAPPING_TABLE_VERSION,
        taxonomyVersion = TAXONOMY_VERSION
    )
}
