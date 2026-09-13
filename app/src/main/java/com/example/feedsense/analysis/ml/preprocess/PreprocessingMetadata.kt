package com.example.feedsense.analysis.ml.preprocess

import com.example.feedsense.analysis.ml.repro.ReproArtifactIdentity
import com.example.feedsense.analysis.ml.repro.ReproCanonicalSerializer
import com.example.feedsense.analysis.ml.repro.ReproCompositeIdentity
import com.example.feedsense.analysis.ml.repro.ReproModelIdentity
import com.example.feedsense.analysis.ml.repro.ReproRuntimeIdentity

// --------------------------------
// PREPROCESSING RESEARCH METADATA (8B-15-3 §21)
// --------------------------------
//
// The deterministic, export-safe metadata that makes a produced
// model input reproducible LATER: the exact preprocessing config
// plus the model artifact / runtime identity it was validated
// against.
//
//   PreprocessingMetadata =
//     PreprocessingConfig            (runnable pipeline contract)
//   + ReproArtifactIdentity          (which exact artifact bytes)
//   + ReproRuntimeIdentity           (which runtime will consume it)
//   + ReproModelIdentity            (which model, optional)
//
// Rules honoured (milestone §21):
//   - identifies preprocessing version, model artifact identity,
//     runtime identity, input dimensions, orientation, resize/crop/
//     padding strategy, color/channel strategy, alpha strategy,
//     datatype, normalization and tensor layout - via the embedded
//     config + identities.
//   - deterministic: identical inputs ALWAYS produce identical
//     serialization and hashes (stable field order, explicit labels,
//     Double.toString()).
//   - export-safe: it carries identifiers and contract labels ONLY.
//     No pixels, no OCR, no private coordinates ever enter this
//     structure.
//
// This is pure metadata: it does NOT invoke the pipeline and has no
// dependency on feed items, sessions, or the privacy frame content.

/**
 * Immutable, export-safe research metadata tying one preprocessing
 * contract to the model artifact/runtime it was validated against.
 *
 * [artifact] / [runtime] / [model] are optional so the metadata can
 * be built before an artifact is acquired (8B-15-2 PENDING); the
 * config itself is always required.
 */
data class PreprocessingMetadata(
    val config: PreprocessingConfig,
    val artifact: ReproArtifactIdentity? = null,
    val runtime: ReproRuntimeIdentity? = null,
    val model: ReproModelIdentity? = null
) {

    /*
     * Byte-stable canonical rendering of the config alone (the
     * runnable preprocessing contract). Available before any
     * artifact identity exists.
     */
    val configCanonical: String by lazy {
        PreprocessingConfigSerializer.serialize(config)
    }

    /*
     * SHA-256 of the config canonical form.
     */
    val configHash: String by lazy {
        PreprocessingConfigSerializer.sha256Hex(config)
    }

    /*
     * Byte-stable canonical rendering of the full metadata.
     * Stable component order: config, artifact, runtime, model.
     */
    val canonical: String by lazy {
        val parts = listOf(
            "config=" + configCanonical,
            "artifact=" + (artifact?.let { ReproCanonicalSerializer.serializeArtifact(it) } ?: "null"),
            "runtime=" + (runtime?.let { ReproCanonicalSerializer.serializeRuntime(it) } ?: "null"),
            "model=" + (model?.let { ReproCanonicalSerializer.serializeModel(it) } ?: "null")
        )
        "preprocessingMetadata{" + parts.joinToString("::") + "}"
    }

    /*
     * SHA-256 of the full metadata canonical form: the deterministic
     * identity a research record can cite for reproducibility.
     */
    val canonicalHash: String by lazy {
        ReproCanonicalSerializer.sha256Hex(canonical)
    }
}

/**
 * Builds [PreprocessingMetadata] from an 8B-15-2
 * [ReproCompositeIdentity], bridging the reproducibility contract
 * to the runnable preprocessing config.
 */
object PreprocessingMetadataFactory {

    /**
     * Extracts artifact/runtime/model identities from a composite
     * reproducibility identity and pairs them with the runnable
     * preprocessing config.
     */
    fun fromRepro(
        config: PreprocessingConfig,
        composite: ReproCompositeIdentity
    ): PreprocessingMetadata = PreprocessingMetadata(
        config = config,
        artifact = composite.artifact,
        runtime = composite.runtime,
        model = composite.model
    )
}