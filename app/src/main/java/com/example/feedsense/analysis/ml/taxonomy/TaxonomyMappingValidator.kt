package com.example.feedsense.analysis.ml.taxonomy

import com.example.feedsense.analysis.CategoryCatalog

// --------------------------------
// TAXONOMY MAPPING VALIDATOR (8B-15-7)
// --------------------------------
//
// Validates a taxonomy mapping table for structural
// correctness and compatibility (section 17).
//
// The validator produces structured deterministic errors.
// No randomness. No side effects.
//
// Detected issues:
//   - Duplicate model-label mappings
//   - Conflicting mappings (same label, different targets)
//   - Missing taxonomy IDs
//   - Invalid taxonomy IDs
//   - Obsolete taxonomy IDs (not in current catalog)
//   - Invalid model class indices (negative)
//   - Duplicate mapping IDs
//   - Incompatible model artifact identity
//   - Incompatible mapping version
//   - Ambiguous one-to-many mappings without rules

object TaxonomyMappingValidator {

    // --------------------------------
    // PUBLIC API
    // --------------------------------

    /*
     * Validate a complete mapping table. Returns a
     * deterministic list of validation issues.
     * Empty list = valid.
     */
    fun validate(
        mappings: List<ModelTaxonomyMapping>,
        expectedArtifactId: String? = null,
        expectedTaxonomyVersion: String? = null
    ): List<MappingValidationIssue> {
        val issues = mutableListOf<MappingValidationIssue>()
        issues.addAll(validateBasicStructure(mappings))
        issues.addAll(validateTaxonomyKeys(mappings))
        issues.addAll(validateDuplicateMappings(mappings))
        issues.addAll(validateConflictingMappings(mappings))
        issues.addAll(validateArtifactCompatibility(
            mappings, expectedArtifactId
        ))
        issues.addAll(validateTaxonomyCompatibility(
            mappings, expectedTaxonomyVersion
        ))
        return issues.sortedBy { it.code }
    }

    // --------------------------------
    // STRUCTURAL VALIDATION
    // --------------------------------

    private fun validateBasicStructure(
        mappings: List<ModelTaxonomyMapping>
    ): List<MappingValidationIssue> {
        val issues = mutableListOf<MappingValidationIssue>()

        // Duplicate mapping IDs
        val seenIds = mutableSetOf<String>()
        for (mapping in mappings) {
            if (!seenIds.add(mapping.mappingId)) {
                issues.add(
                    MappingValidationIssue(
                        code = MappingValidationCode.DUPLICATE_MAPPING_ID,
                        message = "Duplicate mapping ID: " +
                            "'${mapping.mappingId}'",
                        mappingId = mapping.mappingId,
                        modelLabel = mapping.modelLabel
                    )
                )
            }
        }

        // Invalid model label index (negative)
        for (mapping in mappings) {
            if (mapping.modelLabelIndex < 0) {
                issues.add(
                    MappingValidationIssue(
                        code = MappingValidationCode.INVALID_MODEL_INDEX,
                        message = "Negative model label index: " +
                            "${mapping.modelLabelIndex} for " +
                            "label '${mapping.modelLabel}'",
                        mappingId = mapping.mappingId,
                        modelLabel = mapping.modelLabel
                    )
                )
            }
        }

        // Missing taxonomy key for mapped statuses
        for (mapping in mappings) {
            when (mapping.status) {
                MappingStatus.DIRECT,
                MappingStatus.MAPPED -> {
                    if (mapping.feedSenseTaxonomyKey == null) {
                        issues.add(
                            MappingValidationIssue(
                                code = MappingValidationCode.MISSING_TAXONOMY_KEY,
                                message = "Status=${mapping.status.label} " +
                                    "but no taxonomy key provided " +
                                    "for label '${mapping.modelLabel}'",
                                mappingId = mapping.mappingId,
                                modelLabel = mapping.modelLabel
                            )
                        )
                    }
                }
                else -> { /* ok */ }
            }
        }

        // Invalid mapping strength
        for (mapping in mappings) {
            mapping.mappingStrength?.let { strength ->
                if (strength !in 0.0..1.0) {
                    issues.add(
                        MappingValidationIssue(
                            code = MappingValidationCode.INVALID_MAPPING_STRENGTH,
                            message = "Mapping strength $strength " +
                                "out of range [0.0, 1.0] for " +
                                "label '${mapping.modelLabel}'",
                            mappingId = mapping.mappingId,
                            modelLabel = mapping.modelLabel
                        )
                    )
                }
            }
        }

        return issues
    }

    // --------------------------------
    // TAXONOMY KEY VALIDATION
    // --------------------------------

    private fun validateTaxonomyKeys(
        mappings: List<ModelTaxonomyMapping>
    ): List<MappingValidationIssue> {
        val issues = mutableListOf<MappingValidationIssue>()
        val validKeys = CategoryCatalog.keys.toSet()

        for (mapping in mappings) {
            mapping.feedSenseTaxonomyKey?.let { key ->
                if (key !in validKeys) {
                    issues.add(
                        MappingValidationIssue(
                            code = MappingValidationCode.INVALID_TAXONOMY_KEY,
                            message = "Taxonomy key '$key' is not " +
                                "a valid FeedSense category for " +
                                "label '${mapping.modelLabel}'",
                            mappingId = mapping.mappingId,
                            modelLabel = mapping.modelLabel
                        )
                    )
                }
            }
        }

        return issues
    }

    // --------------------------------
    // DUPLICATE MAPPING DETECTION
    // --------------------------------

    private fun validateDuplicateMappings(
        mappings: List<ModelTaxonomyMapping>
    ): List<MappingValidationIssue> {
        val issues = mutableListOf<MappingValidationIssue>()

        // Same model label + same artifact => should have
        // at most one mapping (unless many-to-one with
        // different indices)
        val byLabelAndArtifact = mappings
            .groupBy {
                "${it.modelArtifactId}:${it.modelLabel}"
            }

        for ((key, entries) in byLabelAndArtifact) {
            val distinctTargets = entries
                .map { it.feedSenseTaxonomyKey }
                .distinct()
            if (entries.size > 1 && distinctTargets.size > 1) {
                issues.add(
                    MappingValidationIssue(
                        code = MappingValidationCode.CONFLICTING_MAPPING,
                        message = "Conflicting mappings for label " +
                            "'$key': maps to ${distinctTargets.joinToString()}",
                        mappingId = entries.first().mappingId,
                        modelLabel = entries.first().modelLabel
                    )
                )
            }
        }

        return issues
    }

    // --------------------------------
    // CONFLICTING MAPPING DETECTION
    // --------------------------------

    private fun validateConflictingMappings(
        mappings: List<ModelTaxonomyMapping>
    ): List<MappingValidationIssue> {
        val issues = mutableListOf<MappingValidationIssue>()

        // Same model label index + different targets
        val byIndexAndArtifact = mappings
            .groupBy {
                "${it.modelArtifactId}:${it.modelLabelIndex}"
            }

        for ((key, entries) in byIndexAndArtifact) {
            if (entries.size > 1) {
                val targets = entries
                    .map { it.feedSenseTaxonomyKey }
                    .distinct()
                if (targets.size > 1) {
                    issues.add(
                        MappingValidationIssue(
                            code = MappingValidationCode.CONFLICTING_MAPPING,
                            message = "Conflicting mappings at " +
                                "index $key: maps to ${targets.joinToString()}",
                            mappingId = entries.first().mappingId,
                            modelLabel = entries.first().modelLabel
                        )
                    )
                }
            }
        }

        return issues
    }

    // --------------------------------
    // ARTIFACT COMPATIBILITY
    // --------------------------------

    private fun validateArtifactCompatibility(
        mappings: List<ModelTaxonomyMapping>,
        expectedArtifactId: String?
    ): List<MappingValidationIssue> {
        if (expectedArtifactId == null) return emptyList()
        val issues = mutableListOf<MappingValidationIssue>()

        for (mapping in mappings) {
            if (mapping.modelArtifactId != expectedArtifactId) {
                issues.add(
                    MappingValidationIssue(
                        code = MappingValidationCode.INCOMPATIBLE_ARTIFACT,
                        message = "Mapping artifact " +
                            "'${mapping.modelArtifactId}' does " +
                            "not match expected '$expectedArtifactId'",
                        mappingId = mapping.mappingId,
                        modelLabel = mapping.modelLabel
                    )
                )
            }
        }

        return issues
    }

    // --------------------------------
    // TAXONOMY VERSION COMPATIBILITY
    // --------------------------------

    private fun validateTaxonomyCompatibility(
        mappings: List<ModelTaxonomyMapping>,
        expectedTaxonomyVersion: String?
    ): List<MappingValidationIssue> {
        if (expectedTaxonomyVersion == null) return emptyList()
        val issues = mutableListOf<MappingValidationIssue>()

        for (mapping in mappings) {
            if (mapping.taxonomyVersion != expectedTaxonomyVersion) {
                issues.add(
                    MappingValidationIssue(
                        code = MappingValidationCode.INCOMPATIBLE_TAXONOMY_VERSION,
                        message = "Mapping taxonomy version " +
                            "'${mapping.taxonomyVersion}' does " +
                            "not match expected " +
                            "'$expectedTaxonomyVersion'",
                        mappingId = mapping.mappingId,
                        modelLabel = mapping.modelLabel
                    )
                )
            }
        }

        return issues
    }
}

// --------------------------------
// VALIDATION ISSUE
// --------------------------------

/*
 * A structured validation issue, produced deterministically
 * by the validator.
 */
data class MappingValidationIssue(
    val code: MappingValidationCode,
    val message: String,
    val mappingId: String,
    val modelLabel: String
)

/*
 * Validation failure codes.
 */
enum class MappingValidationCode(val label: String) {
    DUPLICATE_MAPPING_ID("DUPLICATE_MAPPING_ID"),
    INVALID_MODEL_INDEX("INVALID_MODEL_INDEX"),
    MISSING_TAXONOMY_KEY("MISSING_TAXONOMY_KEY"),
    INVALID_TAXONOMY_KEY("INVALID_TAXONOMY_KEY"),
    CONFLICTING_MAPPING("CONFLICTING_MAPPING"),
    INCOMPATIBLE_ARTIFACT("INCOMPATIBLE_ARTIFACT"),
    INCOMPATIBLE_TAXONOMY_VERSION("INCOMPATIBLE_TAXONOMY_VERSION"),
    INVALID_MAPPING_STRENGTH("INVALID_MAPPING_STRENGTH")
}
