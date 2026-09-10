package com.example.feedsense.analysis.ml.preprocess

import com.example.feedsense.analysis.ml.ModelInput
import com.example.feedsense.analysis.ml.ModelTensorType

// --------------------------------
// DETERMINISTIC PREPROCESSOR (8B-15-3)
// --------------------------------
//
// The config-driven, deterministic preprocessing boundary:
//
//   PreprocessingEvidence (privacy-approved)
//     -> 1. input validation        (explicit, deterministic)
//     -> 2. configuration gate     (runnable config required)
//     -> 3. orientation handling   (FrameOrientation + policy)
//     -> 4. aspect-ratio policy    (resize / crop / pad / stretch)
//     -> 5. color + alpha          (channel extraction)
//     -> 6. normalization          (scale [0,1], mean/std)
//     -> 7. quantization + layout  (INT8/UINT8, NHWC)
//     -> PreprocessedInput (tensor + provenance + fingerprint)
//
// Determinism guarantees:
//   - same source pixels + same orientation + same config
//     + same privacy version ==> byte-identical input
//   - never depends on time / locale / timezone / random
//     augmentation / unordered collections
//
// This is inference preprocessing; there is NO training
// augmentation anywhere in this path (milestone §18).

/*
 * Explicit failure codes for the preprocessing boundary.
 */
enum class PreprocessFailureCode(val label: String) {
    PRIVACY_REJECTED("PRIVACY_REJECTED"),
    INVALID_INPUT("INVALID_INPUT"),
    INVALID_CONFIG("INVALID_CONFIG"),
    CONFIG_NOT_RUNNABLE("CONFIG_NOT_RUNNABLE"),
    PREPROCESSING_FAILURE("PREPROCESSING_FAILURE")
}

/*
 * Outcome of one preprocessing run. Failures are explicit and
 * deterministic; the boundary never silently substitutes a
 * plausible-looking input.
 */
sealed class PreprocessResult {
    data class Success(val output: PreprocessedInput) : PreprocessResult()

    data class Failure(
        val code: PreprocessFailureCode,
        val message: String
    ) : PreprocessResult()
}

/**
 * The config-driven deterministic preprocessing pipeline.
 *
 * The instance is immutable: it holds one canonical configuration
 * and applies it to every evidence frame. Reusing the instance
 * across frames keeps allocation bounded and the config constant.
 */
class DeterministicPreprocessor(
    val config: PreprocessingConfig
) {

    init {
        // Fail fast: an instance that cannot run must not exist.
        if (config.inputWidth == null || config.inputHeight == null) {
            require(configIsDocumentationOnly(config)) {
                "a DeterministicPreprocessor requires runnable input dimensions; " +
                    "UNKNOWN/UNKNOWN configurations are documentation-only"
            }
        }
    }

    /*
     * Runs the full deterministic pipeline on privacy-approved
     * evidence.
     *
     * @param capturedOrientation the orientation recorded with the
     *   capture (Android display rotation is never assumed to equal
     *   the frame's optical orientation - section §6).
     */
    fun preprocess(
        evidence: PreprocessingEvidence,
        capturedOrientation: FrameOrientation = FrameOrientation.DEG_0
    ): PreprocessResult {

        // 1. INPUT VALIDATION (explicit, deterministic)
        when (val validation = InputValidation.validateEvidence(evidence)) {
            is ValidationOutcome.Invalid -> {
                return if (!evidence.privacy.isSafeForResearchUse) {
                    PreprocessResult.Failure(
                        PreprocessFailureCode.PRIVACY_REJECTED,
                        validation.reason
                    )
                } else {
                    PreprocessResult.Failure(
                        PreprocessFailureCode.INVALID_INPUT,
                        validation.reason
                    )
                }
            }
            is ValidationOutcome.Valid -> { /* proceed */ }
        }

        // 2. CONFIGURATION GATE
        val width = config.inputWidth
        val height = config.inputHeight
        if (width == null || height == null) {
            return PreprocessResult.Failure(
                PreprocessFailureCode.CONFIG_NOT_RUNNABLE,
                "config $configVersion has UNKNOWN input dimensions"
            )
        }

        return try {
            // 3+4. ORIENTATION + ASPECT-RATIO SPATIAL STAGE
            val spatiallyTransformed = SpatialTransform.applyAspectPolicy(
                evidence.frame,
                config,
                capturedOrientation
            )

            // 5+6+7. PIXEL CONVERSION (color / alpha / normalize / quantize / layout)
            val options = PixelConversionStage.optionsFrom(config)
            val modelTensor = PixelConversionStage.toModelInput(
                frame = spatiallyTransformed,
                options = options,
                tensorType = when (config.tensorType) {
                    PreprocessingTensorType.FLOAT32 -> ModelTensorType.FLOAT32
                    PreprocessingTensorType.INT8 -> ModelTensorType.INT8
                    PreprocessingTensorType.UINT8 -> ModelTensorType.UINT8
                    PreprocessingTensorType.FLOAT16,
                    PreprocessingTensorType.UNKNOWN -> throw IllegalArgumentException(
                        "tensor type ${config.tensorType.label} is not supported by the " +
                            "8B-15-3 pipeline"
                    )
                }
            )

            val fingerprint = PreprocessingFingerprint.of(
                config = config,
                sourceWidth = evidence.frame.width,
                sourceHeight = evidence.frame.height,
                capturedOrientation = capturedOrientation,
                privacyVersion = evidence.privacy.sanitizationVersion,
                policyMode = evidence.privacy.policyMode.label,
                sanitizationStatus = evidence.privacy.sanitizationStatus.label,
                evidenceId = evidence.evidenceId,
                sessionId = evidence.sessionId,
                feedItemId = evidence.feedItemId
            )

            PreprocessResult.Success(
                PreprocessedInput(
                    input = modelTensor,
                    configVersion = config.version,
                    configKey = config.canonicalKey,
                    sourceWidth = evidence.frame.width,
                    sourceHeight = evidence.frame.height,
                    capturedOrientation = capturedOrientation,
                    privacyVersion = evidence.privacy.sanitizationVersion,
                    policyMode = evidence.privacy.policyMode.label,
                    sanitizationStatus = evidence.privacy.sanitizationStatus.label,
                    evidenceId = evidence.evidenceId,
                    sessionId = evidence.sessionId,
                    feedItemId = evidence.feedItemId,
                    fingerprint = fingerprint
                )
            )
        } catch (e: IllegalArgumentException) {
            PreprocessResult.Failure(
                PreprocessFailureCode.PREPROCESSING_FAILURE,
                e.message ?: "preprocessing failed"
            )
        } catch (e: Exception) {
            PreprocessResult.Failure(
                PreprocessFailureCode.PREPROCESSING_FAILURE,
                "preprocessing failed: ${e.javaClass.simpleName}"
            )
        }
    }

    private val configVersion: String get() = config.version

    companion object {
        private fun configIsDocumentationOnly(config: PreprocessingConfig): Boolean {
            return config.inputWidth == null && config.inputHeight == null
        }
    }
}