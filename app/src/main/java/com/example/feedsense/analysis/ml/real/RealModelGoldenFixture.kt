package com.example.feedsense.analysis.ml.real

import com.example.feedsense.analysis.ml.preprocess.PreprocessingConfig
import com.example.feedsense.analysis.ml.preprocess.PreprocessingEvidence
import com.example.feedsense.analysis.ml.preprocess.PreprocessingEvidenceFactory
import com.example.feedsense.analysis.ml.preprocess.PreprocessResult
import com.example.feedsense.analysis.ml.preprocess.DeterministicPreprocessor
import com.example.feedsense.analysis.ml.preprocess.FrameOrientation
import com.example.feedsense.analysis.ml.preprocess.golden.GoldenFixtureLoader
import com.example.feedsense.analysis.ml.preprocess.golden.SourcePattern
import com.example.feedsense.analysis.ml.runtime.litert.MobileNetV2Artifact
import com.example.feedsense.analysis.privacy.PrivacySanitizationStatus

// --------------------------------
// REAL MODEL GOLDEN FIXTURE (8B-15-9, Phase 11)
// --------------------------------
//
// Deterministic golden fixture for validating the real model
// pipeline end-to-end.
//
// Uses a synthetic 224x224 PrivacyFrame (known gradient
// pattern) as the golden input. This is deterministic:
// same source pattern + same dimensions + same config =>
// identical preprocessing output.
//
// The fixture records:
//   - input image metadata (source pattern, dimensions)
//   - preprocessing metadata (config, expected shape)
//   - expected model identity and artifact metadata
//   - expected output shape and label count
//   - verification status for each claim

/**
 * Verification status of a golden fixture claim.
 */
enum class FixtureVerificationStatus(val label: String) {
    VERIFIED("VERIFIED"),
    UNVERIFIED("UNVERIFIED"),
    NOT_MEASURED("NOT_MEASURED"),
    BLOCKED("BLOCKED")
}

/**
 * A single golden fixture entry.
 */
data class RealModelGoldenFixtureEntry(
    val fixtureId: String,
    val description: String,
    val sourcePattern: SourcePattern,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val preprocessingConfig: PreprocessingConfig,
    val expectedInputWidth: Int,
    val expectedInputHeight: Int,
    val expectedInputChannels: Int,
    val expectedInputDatatype: String,
    val modelId: String,
    val modelVersion: String,
    val artifactId: String,
    val expectedOutputClassCount: Int,
    val expectedOutputDatatype: String,
    val inputVerification: FixtureVerificationStatus,
    val outputVerification: FixtureVerificationStatus,
    val labelMappingVerification: FixtureVerificationStatus,
    val performanceMeasurement: FixtureVerificationStatus,
    val notes: String
)

/**
 * Golden fixtures for real model pipeline validation.
 */
object RealModelGoldenFixtures {

    /**
     * The canonical golden fixture: a 224x224 known gradient
     * image preprocessed for MobileNetV2.
     */
    val CANONICAL_224_KNOWN_GRADIENT = RealModelGoldenFixtureEntry(
        fixtureId = "real-mobilenetv2-224-known-gradient",
        description = "224x224 known gradient pattern through MobileNetV2 pipeline",
        sourcePattern = SourcePattern.KNOWN_GRADIENT,
        sourceWidth = 224,
        sourceHeight = 224,
        preprocessingConfig = RealModelPreprocessing.config(),
        expectedInputWidth = MobileNetV2Artifact.INPUT_WIDTH,
        expectedInputHeight = MobileNetV2Artifact.INPUT_HEIGHT,
        expectedInputChannels = MobileNetV2Artifact.INPUT_CHANNELS,
        expectedInputDatatype = MobileNetV2Artifact.INPUT_DATATYPE,
        modelId = MobileNetV2Artifact.MODEL_ID,
        modelVersion = MobileNetV2Artifact.MODEL_VERSION,
        artifactId = MobileNetV2Artifact.ARTIFACT_ID,
        expectedOutputClassCount = MobileNetV2Artifact.OUTPUT_CLASS_COUNT,
        expectedOutputDatatype = MobileNetV2Artifact.OUTPUT_DATATYPE,
        inputVerification = FixtureVerificationStatus.VERIFIED,
        outputVerification = FixtureVerificationStatus.NOT_MEASURED,
        labelMappingVerification = FixtureVerificationStatus.UNVERIFIED,
        performanceMeasurement = FixtureVerificationStatus.NOT_MEASURED,
        notes = "Input preprocessing pipeline verified. Model execution pending artifact acquisition."
    )

    /**
     * A smaller fixture for faster testing.
     */
    val SCALED_DOWN_48_KNOWN_GRADIENT = RealModelGoldenFixtureEntry(
        fixtureId = "real-mobilenetv2-48-scaled-gradient",
        description = "48x48 source scaled to 224x224 through MobileNetV2 pipeline",
        sourcePattern = SourcePattern.KNOWN_GRADIENT,
        sourceWidth = 48,
        sourceHeight = 48,
        preprocessingConfig = RealModelPreprocessing.config(),
        expectedInputWidth = MobileNetV2Artifact.INPUT_WIDTH,
        expectedInputHeight = MobileNetV2Artifact.INPUT_HEIGHT,
        expectedInputChannels = MobileNetV2Artifact.INPUT_CHANNELS,
        expectedInputDatatype = MobileNetV2Artifact.INPUT_DATATYPE,
        modelId = MobileNetV2Artifact.MODEL_ID,
        modelVersion = MobileNetV2Artifact.MODEL_VERSION,
        artifactId = MobileNetV2Artifact.ARTIFACT_ID,
        expectedOutputClassCount = MobileNetV2Artifact.OUTPUT_CLASS_COUNT,
        expectedOutputDatatype = MobileNetV2Artifact.OUTPUT_DATATYPE,
        inputVerification = FixtureVerificationStatus.VERIFIED,
        outputVerification = FixtureVerificationStatus.NOT_MEASURED,
        labelMappingVerification = FixtureVerificationStatus.UNVERIFIED,
        performanceMeasurement = FixtureVerificationStatus.NOT_MEASURED,
        notes = "Tests resize + center crop behavior on non-square source."
    )

    /**
     * All golden fixtures.
     */
    val ALL_FIXTURES = listOf(
        CANONICAL_224_KNOWN_GRADIENT,
        SCALED_DOWN_48_KNOWN_GRADIENT
    )

    /**
     * Creates the deterministic PrivacyFrame for a fixture.
     */
    fun createFrame(fixture: RealModelGoldenFixtureEntry): com.example.feedsense.analysis.privacy.PrivacyFrame {
        return GoldenFixtureLoader.generateFrame(
            pattern = fixture.sourcePattern,
            width = fixture.sourceWidth,
            height = fixture.sourceHeight
        )
    }

    /**
     * Creates the PreprocessingEvidence for a fixture.
     */
    fun createEvidence(fixture: RealModelGoldenFixtureEntry): PreprocessingEvidence {
        val frame = createFrame(fixture)
        return PreprocessingEvidenceFactory.approve(
            frame = frame,
            sanitizationStatus = PrivacySanitizationStatus.SANITIZED,
            evidenceId = fixture.fixtureId,
            sessionId = "golden-fixture-session",
            feedItemId = "golden-fixture-item"
        )
    }

    /**
     * Runs the preprocessing for a fixture and returns the result.
     */
    fun preprocessFixture(fixture: RealModelGoldenFixtureEntry): PreprocessResult {
        val evidence = createEvidence(fixture)
        val preprocessor = DeterministicPreprocessor(fixture.preprocessingConfig)
        return preprocessor.preprocess(
            evidence = evidence,
            capturedOrientation = FrameOrientation.DEG_0
        )
    }
}
