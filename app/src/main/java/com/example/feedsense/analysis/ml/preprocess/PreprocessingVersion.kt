package com.example.feedsense.analysis.ml.preprocess

// --------------------------------
// PREPROCESSING VERSIONING (8B-15-3)
// --------------------------------
//
// The reproducibility architecture (8B-15-2) requires every
// preprocessing configuration to be immutable and versioned.
//
// A preprocessing version MUST change whenever any of the
// following changes:
//   - dimensions (inputWidth/inputHeight)
//   - normalization
//   - crop / padding policy
//   - color handling (format, channel order, alpha)
//   - tensor layout
//   - orientation handling
//   - numeric datatype / quantization mapping
//
// Historical experiments remain interpretable only if we NEVER
// silently modify a published version. A change always produces a
// NEW version constant.
//
// Naming convention follows the 8B-14/8B-15-2 lineage:
//   preprocess-v1  -> 8B-14 DefaultModelPreprocessor (nearest-neigh,
//                      ARGB->RGB, [0,1] scale, NHWC, in-memory).
//   preprocess-v2  -> the 8B-15-3 config-driven, validated,
//                      privacy-stamped preprocessing boundary.
//
// preprocess-v2 is the authoritative researched contract for the
// selected model family (MobileNetV4-Conv, INT8 target). It is a
// REFERENCE contract whose exact pinned values (notably the INT8
// input tensor's scale/zeroPoint) are UNKNOWN until the actual
// `.tflite` artifact is acquired (8B-15-2 artifact = PENDING).
object PreprocessingVersion {

    const val V2 = "preprocess-v2"
}
