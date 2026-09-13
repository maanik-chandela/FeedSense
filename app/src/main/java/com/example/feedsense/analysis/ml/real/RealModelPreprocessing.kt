package com.example.feedsense.analysis.ml.real

import com.example.feedsense.analysis.ml.preprocess.PreprocessingConfig
import com.example.feedsense.analysis.ml.preprocess.PreprocessingVersion
import com.example.feedsense.analysis.ml.runtime.litert.MobileNetV2Artifact

// --------------------------------
// REAL MODEL PREPROCESSING (8B-15-9, Phase 3)
// --------------------------------
//
// The preprocessing configuration pinned to the real
// MobileNetV2 artifact.
//
// MobileNetV2 expects:
//   - Input: 224x224x3 RGB FLOAT32
//   - Scale: [0, 1] via 1/255
//   - No mean subtraction (mean = [0,0,0])
//   - No std normalization (std = [1,1,1])
//   - Layout: NHWC
//   - Resize: bilinear
//   - Crop: center crop for aspect ratio preservation
//
// This config is DOCUMENTED_BY_SOURCE (TensorFlow Hub model
// card and standard MobileNetV2 preprocessing). It must be
// EMPIRICALLY VERIFIED by confirming that the same source
// image + same config produces the same tensor values.

/**
 * Creates the preprocessing configuration pinned to the real
 * MobileNetV2 1.0 224 artifact.
 */
object RealModelPreprocessing {

    /**
     * The exact preprocessing config for MobileNetV2.
     *
     * This matches the standard MobileNetV2 preprocessing:
     * - 224x224 spatial dimensions
     * - RGB, NHWC, FLOAT32
     * - Scale [0,1] via 1/255
     * - No ImageNet mean/std subtraction (MobileNetV2
     *   standard preprocessing does NOT subtract mean)
     * - Bilinear resize
     * - Center crop for aspect ratio
     */
    fun config(): PreprocessingConfig = PreprocessingConfig(
        version = PreprocessingVersion.V2,
        inputWidth = MobileNetV2Artifact.INPUT_WIDTH,
        inputHeight = MobileNetV2Artifact.INPUT_HEIGHT,
        resizePolicy = com.example.feedsense.analysis.ml.preprocess.ResizePolicy.BILINEAR,
        cropPolicy = com.example.feedsense.analysis.ml.preprocess.AspectRatioPolicy.CENTER_CROP,
        paddingPolicy = com.example.feedsense.analysis.ml.preprocess.PaddingPolicy.NO_PADDING,
        interpolation = com.example.feedsense.analysis.ml.preprocess.Interpolation.BILINEAR,
        orientationPolicy = com.example.feedsense.analysis.ml.preprocess.OrientationPolicy.NORMALIZE_TO_0,
        colorFormat = com.example.feedsense.analysis.ml.preprocess.ColorFormat.RGB,
        channelOrder = com.example.feedsense.analysis.ml.preprocess.PreprocessChannelOrder.RGB,
        alphaPolicy = com.example.feedsense.analysis.ml.preprocess.AlphaPolicy.DISCARD,
        tensorType = com.example.feedsense.analysis.ml.preprocess.PreprocessingTensorType.FLOAT32,
        scale = MobileNetV2Artifact.INPUT_SCALE,
        zeroPoint = 0.0,
        mean = MobileNetV2Artifact.INPUT_MEAN,
        std = MobileNetV2Artifact.INPUT_STD,
        tensorLayout = com.example.feedsense.analysis.ml.preprocess.PreprocessingTensorLayout.NHWC,
        batchSize = com.example.feedsense.analysis.ml.preprocess.BatchHandling.BATCH_1,
        paddingValue = PreprocessingConfig.DEFAULT_PADDING_VALUE,
        cropAnchor = com.example.feedsense.analysis.ml.preprocess.CropAnchor.CENTER
    )

    /**
     * Preprocessing verification status.
     *
     * DOCUMENTED_BY_SOURCE: config derived from TensorFlow Hub
     * model card and standard MobileNetV2 preprocessing.
     *
     * Should be updated to EMPIRICALLY_VERIFIED after
     * confirming that the same source image + same config
     * produces identical tensor values across runs.
     */
    var verificationStatus: PreprocessingVerificationStatus =
        PreprocessingVerificationStatus.DOCUMENTED_BY_SOURCE
        private set

    fun markEmpiricallyVerified() {
        verificationStatus = PreprocessingVerificationStatus.EMPIRICALLY_VERIFIED
    }
}

enum class PreprocessingVerificationStatus(val label: String) {
    DOCUMENTED_BY_SOURCE("DOCUMENTED_BY_SOURCE"),
    EMPIRICALLY_VERIFIED("EMPIRICALLY_VERIFIED"),
    UNVERIFIED("UNVERIFIED")
}
