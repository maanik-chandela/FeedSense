package com.example.feedsense.analysis.ml.preprocess

import com.example.feedsense.analysis.ml.repro.ReproCanonicalSerializer

// --------------------------------
// PREPROCESSING CONFIG SERIALIZATION (8B-15-3 §16, §18, §21)
// --------------------------------
//
// A byte-stable, export-safe rendering of the COMPLETE
// PreprocessingConfig, so the exact preprocessing contract can be
// reproduced later without relying on live objects or magic
// constants.
//
// Determinism rules mirror the 8B-15-2 canonical convention
// (ReproCanonicalSerializer):
//   - FIXED field ordering (declared here, never reflection order).
//   - STABLE enum representation (explicit .label, never ordinal).
//   - STABLE numeric formatting (Double.toString(), never locale).
//   - STABLE null handling (absent dims render as "null").
//   - STABLE list rendering (declared order).
//
// This serialization covers EVERY field of the config - including
// scale/zeroPoint/paddingValue/cropAnchor - so two logically
// identical configs produce byte-identical output and any drift is
// observable. The SHA-256 over this canonical form is the
// deterministic preprocessing-config identity a research record can
// cite (milestone §16, §18, §21).

/**
 * Byte-deterministic canonical serialization of a
 * [PreprocessingConfig].
 */
object PreprocessingConfigSerializer {

    /**
     * Renders the complete configuration as one deterministic
     * string. Field order is fixed and stable across versions.
     */
    fun serialize(config: PreprocessingConfig): String {
        val lines = listOf(
            "version=" + config.version,
            "inputWidth=" + config.inputWidth?.toString(),
            "inputHeight=" + config.inputHeight?.toString(),
            "resizePolicy=" + config.resizePolicy.label,
            "cropPolicy=" + config.cropPolicy.label,
            "paddingPolicy=" + config.paddingPolicy.label,
            "interpolation=" + config.interpolation.label,
            "orientationPolicy=" + config.orientationPolicy.label,
            "colorFormat=" + config.colorFormat.label,
            "channelOrder=" + config.channelOrder.label,
            "alphaPolicy=" + config.alphaPolicy.label,
            "tensorType=" + config.tensorType.label,
            "scale=" + config.scale.toString(),
            "zeroPoint=" + config.zeroPoint.toString(),
            "mean=" + config.mean.joinToString(",", "[", "]") { it.toString() },
            "std=" + config.std.joinToString(",", "[", "]") { it.toString() },
            "tensorLayout=" + config.tensorLayout.label,
            "batchSize=" + config.batchSize.label,
            "paddingValue=" + config.paddingValue.toString(),
            "cropAnchor=" + config.cropAnchor.label
        )
        // Joining with "|" and "null" for absent dims is deliberate:
        // there is no ambiguity between an absent field and an empty
        // value.
        return "preprocessingConfig{" + lines.joinToString("|") { it } + "}"
    }

    /**
     * Deterministic SHA-256 of the canonical serialization.
     */
    fun sha256Hex(config: PreprocessingConfig): String =
        ReproCanonicalSerializer.sha256Hex(serialize(config))
}