package com.example.feedsense.analysis.ml

import com.example.feedsense.analysis.privacy.PrivacyFrame

// --------------------------------
// ON-DEVICE INFERENCE ENGINE (8B-14)
// --------------------------------
//
// The composition / entry point that runs the full path:
//
//   safeFrame (privacy-approved)
//        ↓
//   ModelPreprocessor
//        ↓
//   ModelInput
//        ↓
//   OnDeviceModel (RuntimeAdapter behind)
//        ↓
//   ModelInferenceResult
//        ↓            \
//   MlEvaluationBridge  BaselineMlComparison
//        ↓                 ↓
//   8A evaluation      research comparison
//
// The engine guarantees:
//   - the model consumes ONLY the privacy-approved frame
//   - preprocessing and inference latencies are measured
//     separately with the injected clock
//   - one ML failure NEVER crashes the analysis pipeline:
//     every failure becomes an explicit result while the
//     baseline remains available (8B-14 sections 31, 34)
//   - provenance (evidence + privacy + preprocess + model
//     versions) is stamped onto every result
//
// This class never mutates a FeedItem, an AiPredictionRecord
// or any baseline object (sections 21, 56).

class OnDeviceInferenceEngine(
    val model: OnDeviceModel,
    private val preprocessor: ModelPreprocessor,
    private val clock: InferenceClock = SystemInferenceClock
) {

    init {
        require(preprocessor.inputSpec == model.metadata.inputSpec) {
            "preprocessor input spec must match the model input spec"
        }
    }

    /*
     * Runs the complete inference path for one
     * privacy-approved frame.
     *
     * @param safeFrame the frame AFTER the 8B-13 privacy
     *   pipeline. This engine has no path for raw frames.
     * @param evidenceId optional frame/evidence identifier
     * @param privacyVersion version of the privacy processing
     *   that produced the safe frame
     * @param experimentId optional experiment identity
     */
    fun analyzeSafeFrame(
        safeFrame: PrivacyFrame,
        evidenceId: String? = null,
        privacyVersion: String? = null,
        experimentId: String? = null
    ): ModelInferenceResult {
        val startMs = clock.nowMs()

        val context = InferenceContext(
            evidenceId = evidenceId,
            privacyVersion = privacyVersion,
            experimentId = experimentId
        )

        // --------------------------------
        // PREPROCESSING
        // --------------------------------

        val preprocessing = preprocessor.preprocess(safeFrame)

        val preprocessingResult = when (preprocessing) {
            is PreprocessingResult.Failure -> {
                return ModelInferenceResult(
                    modelId = model.metadata.modelId,
                    modelVersion = model.metadata.modelVersion,
                    modelChecksum = model.metadata.checksum,
                    status = preprocessing.status,
                    privacyVersion = privacyVersion,
                    evidenceId = evidenceId,
                    experimentId = experimentId,
                    timestampMs = clock.nowMs(),
                    preprocessingLatencyMs = preprocessing.latencyMs,
                    failure = InferenceFailure(
                        status = preprocessing.status,
                        phase = "preprocess",
                        message = preprocessing.message
                    )
                )
            }
            is PreprocessingResult.Success -> preprocessing
        }

        // --------------------------------
        // MODEL READINESS
        // --------------------------------

        if (!model.isReady() && model.state != ModelState.FAILED) {
            // Load once, reused for many frames.
            runCatching { model.load() }
        }

        if (!model.isReady()) {
            return ModelInferenceResult(
                modelId = model.metadata.modelId,
                modelVersion = model.metadata.modelVersion,
                modelChecksum = model.metadata.checksum,
                status = InferenceStatus.MODEL_UNAVAILABLE,
                preprocessVersion = preprocessor.version,
                privacyVersion = privacyVersion,
                evidenceId = evidenceId,
                experimentId = experimentId,
                timestampMs = clock.nowMs(),
                preprocessingLatencyMs = preprocessingResult.latencyMs,
                inferenceLatencyMs = clock.nowMs() - startMs - preprocessingResult.latencyMs,
                failure = InferenceFailure(
                    status = InferenceStatus.MODEL_UNAVAILABLE,
                    phase = "load",
                    message = "model cannot be loaded (state=${model.state.label})"
                )
            )
        }

        // --------------------------------
        // INFERENCE
        // --------------------------------

        val inference = model.infer(
            input = preprocessingResult.input,
            context = context
        )

        // --------------------------------
        // STAMP PROVENANCE + LATENCY
        // --------------------------------

        return inference.copy(
            preprocessVersion = preprocessor.version,
            preprocessingLatencyMs = preprocessingResult.latencyMs,
            timestampMs = if (inference.timestampMs == 0L) {
                clock.nowMs()
            } else {
                inference.timestampMs
            }
        )
    }
}