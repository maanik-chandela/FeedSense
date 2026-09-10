package com.example.feedsense.analysis.ml.taxonomy

import com.example.feedsense.analysis.ml.repro.ReprLabeled

// --------------------------------
// TAXONOMY MAPPING SERIALIZER (8B-15-7)
// --------------------------------
//
// Deterministic, privacy-safe serialization of taxonomy
// mapping results (section 24).
//
// Determinism rules:
//   - STABLE field ordering (fixed, declared order).
//   - STABLE enum representations (.label, never ordinal).
//   - STABLE numeric formatting (toString() only).
//   - STABLE null handling ("null" for absent values).
//   - No unordered-map dependence.
//   - No locale-dependent formatting.
//
// The same mapping result MUST serialize to identical text.
// Hashes, if present, are reproducible.

object TaxonomyMappingSerializer {

    private const val CONFIDENCE_DECIMALS = 6

    // --------------------------------
    // PUBLIC API
    // --------------------------------

    /*
     * Deterministic single-line JSON text of a mapping result.
     */
    fun toJsonText(result: TaxonomyMappingResult): String {
        return toMetadataMap(result)
            .entries
            .sortedBy { it.key }
            .joinToString(
                prefix = "{",
                postfix = "}",
                separator = ","
            ) { (key, value) ->
                "\"${escape(key)}\":\"${escape(value)}\""
            }
    }

    /*
     * Safe metadata map of a mapping result.
     */
    fun toMetadataMap(
        result: TaxonomyMappingResult
    ): Map<String, String> {
        val map = linkedMapOf<String, String>()
        map["resultType"] = resultType(result)
        map["modelLabel"] = result.modelLabel
        map["modelLabelIndex"] = result.modelLabelIndex.toString()
        map["mappingId"] = result.mappingId
        map["mappingTableVersion"] = result.mappingTableVersion
        map["taxonomyVersion"] = result.taxonomyVersion

        when (result) {
            is TaxonomyMappingResult.Mapped -> {
                map["feedSenseTaxonomyKey"] =
                    result.feedSenseTaxonomyKey
                map["feedSenseTaxonomyDisplayName"] =
                    result.feedSenseTaxonomyDisplayName
                map["status"] = result.status.label
                result.mappingStrength?.let {
                    map["mappingStrength"] =
                        formatConfidence(it)
                }
                result.rationale?.let {
                    map["rationale"] = it.label
                }
                map["provenance"] = result.provenance.label
                result.rationaleNotes?.let {
                    map["rationaleNotes"] = it
                }
            }

            is TaxonomyMappingResult.Unmapped -> {
                map["reason"] = result.reason
            }

            is TaxonomyMappingResult.Ambiguous -> {
                map["candidateTaxonomyKeys"] =
                    result.candidateTaxonomyKeys.joinToString("|")
                map["ambiguityReason"] =
                    result.ambiguityReason
            }

            is TaxonomyMappingResult.Invalid -> {
                map["failureReason"] = result.failureReason
                map["failureCode"] = result.failureCode.label
            }
        }

        return map
    }

    /*
     * Deterministic JSON text of a mapping version.
     */
    fun versionToJsonText(
        version: TaxonomyMappingVersion
    ): String {
        val map = linkedMapOf<String, String>()
        map["mappingTableVersion"] =
            version.mappingTableVersion
        map["taxonomyVersion"] = version.taxonomyVersion
        map["modelArtifactId"] = version.modelArtifactId
        map["modelArtifactVersion"] =
            version.modelArtifactVersion
        map["labelCount"] = version.labelCount.toString()
        map["taxonomyKeyCount"] =
            version.taxonomyKeyCount.toString()

        return map.entries
            .joinToString(
                prefix = "{",
                postfix = "}",
                separator = ","
            ) { (key, value) ->
                "\"${escape(key)}\":\"${escape(value)}\""
            }
    }

    // --------------------------------
    // INTERNAL
    // --------------------------------

    private fun resultType(
        result: TaxonomyMappingResult
    ): String {
        return when (result) {
            is TaxonomyMappingResult.Mapped -> "MAPPED"
            is TaxonomyMappingResult.Unmapped -> "UNMAPPED"
            is TaxonomyMappingResult.Ambiguous -> "AMBIGUOUS"
            is TaxonomyMappingResult.Invalid -> "INVALID"
        }
    }

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
    }

    private fun formatConfidence(value: Double): String {
        val factor = Math.pow(10.0, CONFIDENCE_DECIMALS.toDouble())
        return (Math.round(value * factor) / factor).toString()
    }
}
