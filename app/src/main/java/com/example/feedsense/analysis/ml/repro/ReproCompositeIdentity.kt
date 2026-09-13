package com.example.feedsense.analysis.ml.repro

// --------------------------------
// COMPOSITE REPRODUCIBILITY IDENTITY (8B-15-2)
// --------------------------------
//
// The deterministic identity of a complete ML configuration:
//
//   ReproducibilityIdentity =
//     ModelIdentity
//   + ArtifactIdentity
//   + RuntimeIdentity
//   + QuantizationIdentity
//   + PreprocessingIdentity
//   + OutputMappingIdentity
//   + PrivacyVersion
//   + Provenance
//
// Requirements (8B-15-2):
//   - same configuration  -> same identity
//   - different artifact  -> different identity
//   - different runtime   -> different identity
//   - different preprocess-> different identity
//   - different mapping   -> different identity
//
// CRITICAL: the identity NEVER uses object memory addresses,
// hashCode(), random UUIDs, wall-clock time, or unordered map
// serialization. It is derived from canonical serialization only.

/*
 * Immutable composite identity of the full configuration.
 * Canonical serialization + canonical hash are derived
 * deterministically and are the reproducibility anchors.
 */
data class ReproCompositeIdentity(
    val model: ReproModelIdentity,
    val artifact: ReproArtifactIdentity,
    val runtime: ReproRuntimeIdentity,
    val quantization: ReproQuantizationIdentity,
    val preprocessing: ReproPreprocessingIdentity,
    val outputMapping: ReproOutputMappingIdentity,
    val privacy: ReproPrivacyIdentity,
    val provenance: ReproArtifactProvenance? = null
) : ReproIdentity {

    /*
     * Canonical byte-stable serialization of the full
     * configuration. Two logically identical configurations
     * produce byte-identical output.
     */
    override val canonical: String by lazy {
        ReproCanonicalSerializer.serialize(this)
    }

    /*
     * SHA-256 of the canonical serialization: the deterministic
     * composite identity. Changes whenever any component changes.
     */
    override val canonicalHash: String by lazy {
        ReproCanonicalSerializer.sha256Hex(canonical)
    }
}

/*
 * Marker interface for all reproducibility identity components so
 * they share a canonical representation surface.
 */
interface ReproIdentity {
    val canonical: String
    val canonicalHash: String
}
