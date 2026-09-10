package com.example.feedsense.analysis.ml

// --------------------------------
// MODEL INFERENCE RESULT (8B-14)
// --------------------------------
//
// The stable internal representation of one ML inference,
// framework-agnostic and free of privacy-sensitive payload.
//
//   - rankedPredictions : deterministic top-K ranked
//     candidates (top-1 == primaryCategory)
//   - status            : one of InferenceStatus
//   - latency fields    : preprocessing / inference /
//     total, produced by an injected clock
//   - provenance        : model/preprocess/privacy/input/
//     output versions + evidence id
//   - rawModelMetadata  : SAFE metadata only (no pixels,
//     no OCR text, no private content)
//
// The result NEVER contains the model input image bytes,
// raw screenshots, OCR text, or private content (8B-14
// sections 32, 46). It contains classification metadata that
// is safe to serialize.

data class ModelInferenceResult(
    val modelId: String,
    val modelVersion: String,
    val modelChecksum: String? = null,

    val status: InferenceStatus = InferenceStatus.NOT_RUN,

    val rankedPredictions: List<RankedPrediction> = emptyList(),

    // --------------------------------
    // PROVENANCE
    // --------------------------------

    val preprocessVersion: String? = null,
    val privacyVersion: String? = null,
    val inputSpecVersion: String? = null,
    val outputSpecVersion: String? = null,
    val evidenceId: String? = null,
    val experimentId: String? = null,
    val timestampMs: Long = 0L,

    // --------------------------------
    // LATENCY (injected clock; never fabricated)
    // --------------------------------

    val preprocessingLatencyMs: Long = 0L,
    val inferenceLatencyMs: Long = 0L,

    // --------------------------------
    // SAFE METADATA
    // --------------------------------

    val rawModelMetadata: Map<String, String> = emptyMap(),
    val failure: InferenceFailure? = null
) {

    /*
     * totalLatencyMs = preprocess + inference.
     */
    val totalLatencyMs: Long
        get() = preprocessingLatencyMs + inferenceLatencyMs

    /*
     * Top-1 category, when the model produced one.
     */
    val primaryCategory: String?
        get() = rankedPredictions.firstOrNull()?.category

    /*
     * Top-1 model confidence.
     */
    val primaryConfidence: Double?
        get() = rankedPredictions.firstOrNull()?.confidence

    /*
     * Whether inference fully succeeded.
     */
    val succeeded: Boolean
        get() = status == InferenceStatus.SUCCESS

    /*
     * Whether the model produced any usable prediction.
     */
    val hasPrediction: Boolean
        get() = succeeded && rankedPredictions.isNotEmpty()

    companion object {

        /*
         * Standard explicit non-success result. Identity
         * fields are preserved so the failure is still
         * attributable.
         */
        fun failure(
            modelId: String,
            modelVersion: String,
            modelChecksum: String?,
            status: InferenceStatus,
            phase: String,
            message: String,
            context: InferenceContext,
            inferenceLatencyMs: Long = 0L
        ): ModelInferenceResult {
            return ModelInferenceResult(
                modelId = modelId,
                modelVersion = modelVersion,
                modelChecksum = modelChecksum,
                status = status,
                preprocessVersion = null,
                privacyVersion = context.privacyVersion,
                evidenceId = context.evidenceId,
                experimentId = context.experimentId,
                inferenceLatencyMs = inferenceLatencyMs,
                failure = InferenceFailure(
                    status = status,
                    phase = phase,
                    message = message
                )
            )
        }
    }
}

/*
 * Explicit failure descriptor. The message is SAFE: it may
 * describe the failure cause (load failure, malformed
 * output, resource exhaustion) but never carries content
 * (8B-14 section 59).
 */
data class InferenceFailure(
    val status: InferenceStatus,
    val phase: String,
    val message: String
)