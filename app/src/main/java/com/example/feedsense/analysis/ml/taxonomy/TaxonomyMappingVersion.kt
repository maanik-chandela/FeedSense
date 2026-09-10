package com.example.feedsense.analysis.ml.taxonomy

// --------------------------------
// TAXONOMY MAPPING VERSION (8B-15-7)
// --------------------------------
//
// Versioning for the taxonomy mapping table, independent
// of model version, runtime version, preprocessing version,
// output interpretation version, or fixture version
// (section 14).
//
// A mapping change must be independently identifiable so
// a future mapping update does not retroactively change
// the historical meaning of a previous prediction.

/*
 * Immutable identity + version of a taxonomy mapping table.
 */
data class TaxonomyMappingVersion(
    val mappingTableVersion: String,
    val taxonomyVersion: String,
    val modelArtifactId: String,
    val modelArtifactVersion: String,
    val labelCount: Int,
    val taxonomyKeyCount: Int
) {

    init {
        require(mappingTableVersion.isNotBlank()) {
            "mappingTableVersion must be non-blank"
        }
        require(taxonomyVersion.isNotBlank()) {
            "taxonomyVersion must be non-blank"
        }
        require(modelArtifactId.isNotBlank()) {
            "modelArtifactId must be non-blank"
        }
        require(modelArtifactVersion.isNotBlank()) {
            "modelArtifactVersion must be non-blank"
        }
        require(labelCount >= 0) { "labelCount must be >= 0" }
        require(taxonomyKeyCount >= 0) {
            "taxonomyKeyCount must be >= 0"
        }
    }

    /*
     * Stable composite key. Two versions with the same key
     * are assumed identical; differences are caught by
     * comparing the full data class.
     */
    val key: String
        get() = buildString {
            append(mappingTableVersion)
            append(":")
            append(taxonomyVersion)
            append(":")
            append(modelArtifactId)
            append(":")
            append(modelArtifactVersion)
        }

    companion object {

        /*
         * Compute the taxonomy version string from the
         * frozen version and the current category key set.
         *
         * This is deterministic: the same frozen version
         * and key set always produces the same string.
         */
        fun computeTaxonomyVersion(
            frozenVersion: String,
            sortedCategoryKeys: List<String>
        ): String {
            val keyHash = sortedCategoryKeys.joinToString(",")
                .toByteArray(Charsets.UTF_8)
                .let { bytes ->
                    java.security.MessageDigest
                        .getInstance("SHA-256")
                        .digest(bytes)
                        .joinToString("") { "%02x".format(it) }
                }
            return "taxonomy:$frozenVersion:${sortedCategoryKeys.size}:$keyHash"
        }
    }
}
