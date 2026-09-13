package com.example.feedsense.analysis.ml

import com.example.feedsense.analysis.privacy.PrivacyFrame

// --------------------------------
// MODEL PREPROCESSOR (8B-14)
// --------------------------------
//
// Separates frame -> tensor conversion from the model class
// itself. Different models can use different preprocessing
// pipelines without touching the model or the rest of
// FeedSense (8B-14 section 15).
//
//   safeFrame (privacy-approved)
//        ↓
//   ModelPreprocessor
//        ↓
//   ModelInput
//        ↓
//   OnDeviceModel
//
// The preprocessor consumes the PRIVACY-APPROVED frame
// (PrivacyFrame after the 8B-13 pipeline); there is no path
// where ML receives raw unprotected screenshots (sections
// 16, 17). All processing is in-memory; no raw frame is
// persisted to feed the model.

/*
 * Outcome of one preprocessing run.
 */
sealed class PreprocessingResult {

    abstract val latencyMs: Long

    /*
     * A valid prepared tensor.
     */
    data class Success(
        val input: ModelInput,
        override val latencyMs: Long,
        val sourceWidth: Int,
        val sourceHeight: Int
    ) : PreprocessingResult()

    /*
     * Explicit preprocessing failure. message must be SAFE
     * (never content).
     */
    data class Failure(
        val status: InferenceStatus,
        override val latencyMs: Long,
        val message: String
    ) : PreprocessingResult() {

        init {
            require(status == InferenceStatus.INVALID_INPUT ||
                status == InferenceStatus.PREPROCESSING_FAILURE ||
                status == InferenceStatus.RESOURCE_LIMIT
            ) { "unexpected failure status $status" }
        }
    }
}

interface ModelPreprocessor {

    /*
     * Preprocessing pipeline version, e.g. "preprocess-v1".
     * A historical prediction is attributable to BOTH the
     * model version and this version (8B-14 section 25).
     */
    val version: String

    /*
     * The input contract this preprocessor produces.
     */
    val inputSpec: ModelInputSpec

    /*
     * Converts a privacy-approved frame into a prepared
     * tensor. Never throws; failures are explicit.
     */
    fun preprocess(safeFrame: PrivacyFrame): PreprocessingResult
}