package com.example.feedsense.analysis.ml

import com.example.feedsense.analysis.privacy.SyntheticFrames

/*
 * Shared fixtures for the 8B-14 test suite.
 *
 * The fake model labels intentionally mirror FeedSense
 * category taxonomy keys (sports / entertainment / meme /
 * advertisement) so the ML-to-taxonomy adapter is exercised
 * with canonical values.
 */
object MlTestFixtures {

    val INPUT_SPEC: ModelInputSpec = ModelInputSpec(
        specVersion = "input-v1",
        width = 4,
        height = 4,
        channels = 3,
        pixelFormat = ModelPixelFormat.RGB,
        tensorType = ModelTensorType.FLOAT32,
        scale = 1.0 / 255.0
    )

    val OUTPUT_SPEC: ModelOutputSpec = ModelOutputSpec(
        specVersion = "output-v1",
        labels = listOf(
            "sports", "entertainment", "meme", "advertisement"
        ),
        maxTopK = 3
    )

    fun metadata(
        modelId: String = "feedsense-category-model",
        modelVersion: String = "ml-v1",
        checksum: String? = "checksum-test-1",
        inputSpec: ModelInputSpec = INPUT_SPEC,
        outputSpec: ModelOutputSpec = OUTPUT_SPEC
    ): ModelMetadata {
        return ModelMetadata(
            modelId = modelId,
            modelVersion = modelVersion,
            format = ModelFormat.DETERMINISTIC,
            quantization = QuantizationType.NONE,
            checksum = checksum,
            inputSpec = inputSpec,
            outputSpec = outputSpec
        )
    }

    fun successScores(
        primary: String = "sports",
        primaryConfidence: Double = 0.82,
        second: String = "entertainment",
        secondConfidence: Double = 0.11,
        third: String = "meme",
        thirdConfidence: Double = 0.04
    ): Map<String, Double> {
        return linkedMapOf(
            primary to primaryConfidence,
            second to secondConfidence,
            third to thirdConfidence
        )
    }

    fun frame(width: Int = 16, height: Int = 16) =
        SyntheticFrames.checkerboard(
            width, height, 0xFF101010.toInt(), 0xFFE0E0E0.toInt()
        )
}