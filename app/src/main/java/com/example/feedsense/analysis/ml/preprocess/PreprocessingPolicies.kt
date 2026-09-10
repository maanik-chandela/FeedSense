package com.example.feedsense.analysis.ml.preprocess

// --------------------------------
// PREPROCESSING POLICIES (8B-15-3)
// --------------------------------
//
// The vocabulary of choices a PreprocessingConfig can make.
//
// Every enum carries an explicit, stable label so a canonical
// configuration representation can render without relying on
// ordinal order or locale formatting (mirrors the 8B-15-2
// ReprLabeled convention).

/**
 * Stable label contract for preprocessing polities, mirroring
 * the 8B-15-2 reproducibility convention.
 */
interface PreprocessLabeled {
    val label: String
}

/*
 * How the source image is resized to the model tensor size.
 */
enum class ResizePolicy(override val label: String) : PreprocessLabeled {
    NEAREST_NEIGHBOR("NEAREST_NEIGHBOR"),
    BILINEAR("BILINEAR"),
    AREA("AREA"),
    UNKNOWN("UNKNOWN")
}

/*
 * Interpolation method used by the resize step. Deterministic
 * and device-independent by construction (pure arithmetic).
 */
enum class Interpolation(override val label: String) : PreprocessLabeled {
    NEAREST("NEAREST"),
    BILINEAR("BILINEAR"),
    UNKNOWN("UNKNOWN")
}

/*
 * Where the crop window is anchored when the source aspect
 * ratio differs from the target.
 *
 * FeedSense evidence is vertical short-form content that can
 * carry research-relevant captions / subtitles / creator UI at
 * the TOP and BOTTOM of the frame. A naive CENTER crop can
 * remove both. The 8B-15-3 spatial policy chooses the anchor
 * and justifies it (§7, §8 of the milestone).
 */
enum class CropAnchor(override val label: String) : PreprocessLabeled {
    CENTER("CENTER"),
    TOP("TOP"),
    BOTTOM("BOTTOM"),
    UNKNOWN("UNKNOWN")
}

/*
 * Whole-frame spatial strategy when the source and target
 * aspect ratios differ (milestone §7).
 *
 *   STRETCH       - direct resize; distorts the image.
 *   CENTER_CROP   - crop to target aspect then resize; loses
 *                   content outside the crop window.
 *   PAD           - preserve content, pad to target aspect,
 *                   then resize; introduces borders of the
 *                   configured padding value.
 *   RESIZE        - pure resize when aspect ratios already
 *                   match (no crop, no pad).
 */
enum class AspectRatioPolicy(override val label: String) : PreprocessLabeled {
    STRETCH("STRETCH"),
    CENTER_CROP("CENTER_CROP"),
    PAD("PAD"),
    RESIZE("RESIZE"),
    UNKNOWN("UNKNOWN")
}

/*
 * How the frame's capture orientation is resolved before any
 * spatial transform.
 *
 * Android display rotation is NOT image rotation: a portrait
 * feed can be captured with the sensor or layout rotated.
 * Orientation handling is explicit and deterministic.
 */
enum class OrientationPolicy(override val label: String) : PreprocessLabeled {
    NORMALIZE_TO_0("NORMALIZE_TO_0"),
    ROTATE_90("ROTATE_90"),
    ROTATE_180("ROTATE_180"),
    ROTATE_270("ROTATE_270"),
    UNKNOWN("UNKNOWN")
}

/*
 * Final channel / pixel representation of the model input.
 */
enum class ColorFormat(override val label: String) : PreprocessLabeled {
    RGB("RGB"),
    RGBA("RGBA"),
    BGR("BGR"),
    GRAYSCALE("GRAYSCALE"),
    UNKNOWN("UNKNOWN")
}

/*
 * Channel ordering of the model input tensor. Mirrors the
 * 8B-15-2 ChannelOrder vocabulary.
 */
enum class PreprocessChannelOrder(override val label: String) : PreprocessLabeled {
    RGB("RGB"),
    BGR("BGR"),
    RGBA("RGBA"),
    GRAYSCALE("GRAYSCALE"),
    UNKNOWN("UNKNOWN")
}

/*
 * What happens to the alpha channel (milestone §12).
 * Source frames are ARGB; the model input is normally RGB, so
 * alpha is discarded by a defined policy - never silently.
 */
enum class AlphaPolicy(override val label: String) : PreprocessLabeled {
    DISCARD("DISCARD"),
    COMPOSITE("COMPOSITE"),
    PRESERVE("PRESERVE"),
    UNKNOWN("UNKNOWN")
}

/*
 * Numeric type of the model input tensor (milestone §13).
 */
enum class PreprocessingTensorType(override val label: String) : PreprocessLabeled {
    FLOAT32("FLOAT32"),
    FLOAT16("FLOAT16"),
    INT8("INT8"),
    UINT8("UINT8"),
    UNKNOWN("UNKNOWN")
}

/*
 * Tensor layout (milestone §15).
 */
enum class PreprocessingTensorLayout(override val label: String) : PreprocessLabeled {
    NHWC("NHWC"),
    NCHW("NCHW"),
    UNKNOWN("UNKNOWN")
}

/*
 * Batch handling (milestone §16).
 *
 * FeedSense processes individual evidence frames. Batch size 1
 * is the default and the recommended research choice.
 */
enum class BatchHandling(override val label: String) : PreprocessLabeled {
    BATCH_1("BATCH_1"),
    DYNAMIC("DYNAMIC"),
    UNKNOWN("UNKNOWN")
}