package com.example.feedsense.analysis.ml

import com.example.feedsense.analysis.ml.runtime.AdapterRawModelOutput
import com.example.feedsense.analysis.ml.runtime.InferenceExecutionStatus

// --------------------------------
// MODEL OUTPUT DECODER (8B-15-6)
// --------------------------------
//
// Converts raw model tensor output into semantic ranked
// predictions. This is the interpretation layer between
// raw runtime output (8B-15-5) and taxonomy mapping
// (8B-15-7).
//
// Pipeline position:
//
//   AdapterRawModelOutput (8B-15-5)
//        ↓
//   ModelOutputDecoder (8B-15-6)
//        ↓
//   DecodedOutput (List<RankedPrediction>)
//        ↓
//   TaxonomyMappingEngine (8B-15-7)
//
// Determinism rules (section 16):
//   - For identical raw output, identical label set, and
//     identical config, the decoder MUST produce identical
//     ranked predictions.
//   - No randomness.
//   - No unordered iteration determining outcomes.
//   - No timestamps affecting interpretation.
//   - Ties broken by label string ascending.
//
// Research safety (section 22):
//   - The decoder maps tensor indices to label names.
//   - It does NOT modify model output values.
//   - It does NOT assign confidence thresholds.
//   - It does NOT classify, filter, or reject based on
//     confidence.
//   - It does NOT perform taxonomy mapping.
//   - It does NOT make recommendation decisions.
//
// The decoder DOES:
//   - Map tensor element positions to label names
//   - Apply optional output transformation (softmax, etc.)
//   - Rank candidates deterministically
//   - Validate output structure against label count
//   - Handle edge cases explicitly

/*
 * Configuration for how raw output tensors should be
 * interpreted into scored predictions.
 */
data class OutputInterpretationConfig(
    val interpretationVersion: String,
    val labels: List<String>,
    val outputSemantics: OutputSemantics,
    val topK: Int = DEFAULT_TOP_K,
    val confidenceRange: ClosedFloatingPointRange<Double> =
        0.0..1.0
) {

    init {
        require(interpretationVersion.isNotBlank()) {
            "interpretationVersion must be non-blank"
        }
        require(labels.isNotEmpty()) {
            "labels must not be empty"
        }
        require(labels.distinct().size == labels.size) {
            "labels must not contain duplicates"
        }
        require(topK >= 1) { "topK must be >= 1" }
        require(
            confidenceRange.start.isFinite() &&
                confidenceRange.endInclusive.isFinite()
        ) { "confidenceRange must be finite" }
    }

    companion object {
        const val DEFAULT_TOP_K = 5
        const val VERSION = "output-interp-v1"
    }
}

/*
 * How the raw output tensor values should be interpreted.
 *
 * SOFTMAX: apply softmax normalization before ranking.
 *   Use when the model outputs logits, not probabilities.
 *
 * RAW_SCORES: treat values as already-normalized scores.
 *   Use when the model outputs probabilities directly.
 *
 * ARGMAX: return only the top-1 prediction. The single
 *   highest-scoring element determines the class.
 */
enum class OutputSemantics(val label: String) {
    SOFTMAX("SOFTMAX"),
    RAW_SCORES("RAW_SCORES"),
    ARGMAX("ARGMAX")
}

/*
 * The result of decoding raw model output into ranked
 * predictions. Immutable and deterministic.
 */
sealed class DecodedOutput {

    /**
     * Decoding succeeded. Contains the ranked predictions
     * plus provenance about the interpretation.
     */
    data class Success(
        val rankedPredictions: List<RankedPrediction>,
        val interpretationVersion: String,
        val inputTensorElementCount: Int,
        val outputSemanticType: OutputSemantics,
        val labelCount: Int,
        val decodingLatencyMs: Long = 0L
    ) : DecodedOutput() {

        init {
            require(rankedPredictions.isNotEmpty()) {
                "successful decode must produce at least one prediction"
            }
        }
    }

    /**
     * Decoding failed with a structured reason. The raw
     * output is preserved for diagnostics; no semantic
     * prediction is produced.
     */
    data class Failure(
        val status: InferenceStatus,
        val message: String,
        val interpretationVersion: String,
        val inputTensorElementCount: Int = 0,
        val expectedLabelCount: Int = 0,
        val decodingLatencyMs: Long = 0L
    ) : DecodedOutput()
}

/**
 * Deterministic model output decoder.
 *
 * Maps raw tensor element positions to model-native labels,
 * applies optional transformation (softmax), and ranks
 * candidates by score.
 *
 * The decoder never throws: all failures become explicit
 * DecodedOutput.Failure results.
 */
object ModelOutputDecoder {

    /**
     * Decode a raw model output into ranked predictions.
     *
     * This is the primary entry point. It:
     *   1. Validates the adapter output is successful
     *   2. Extracts the primary output tensor
     *   3. Validates tensor length matches label count
     *   4. Applies output semantics transformation
     *   5. Maps positions to labels
     *   6. Ranks deterministically
     *   7. Caps at top-K
     *
     * @param rawOutput the raw adapter output from 8B-15-5
     * @param config interpretation configuration
     * @param clock injectable clock for latency measurement
     * @return deterministic decoded output
     */
    fun decode(
        rawOutput: AdapterRawModelOutput,
        config: OutputInterpretationConfig,
        clock: InferenceClock = SystemInferenceClock
    ): DecodedOutput {
        val startMs = clock.nowMs()

        // 1. Validate adapter output success
        if (rawOutput.failed) {
            return DecodedOutput.Failure(
                status = InferenceStatus.INFERENCE_FAILURE,
                message = "adapter output failed: " +
                    "${rawOutput.failure?.code?.label}: " +
                    "${rawOutput.failure?.message}",
                interpretationVersion = config.interpretationVersion,
                inputTensorElementCount = 0,
                expectedLabelCount = config.labels.size,
                decodingLatencyMs = clock.nowMs() - startMs
            )
        }

        // 2. Extract primary output tensor
        val primaryTensor = rawOutput.primaryOutput()
        if (primaryTensor == null || primaryTensor.isEmpty()) {
            return DecodedOutput.Failure(
                status = InferenceStatus.OUTPUT_INVALID,
                message = "no output tensor values available",
                interpretationVersion = config.interpretationVersion,
                inputTensorElementCount = 0,
                expectedLabelCount = config.labels.size,
                decodingLatencyMs = clock.nowMs() - startMs
            )
        }

        // 3. Validate tensor length matches label count
        if (primaryTensor.size != config.labels.size) {
            return DecodedOutput.Failure(
                status = InferenceStatus.OUTPUT_INVALID,
                message = "output tensor size ${primaryTensor.size} " +
                    "does not match label count ${config.labels.size}",
                interpretationVersion = config.interpretationVersion,
                inputTensorElementCount = primaryTensor.size,
                expectedLabelCount = config.labels.size,
                decodingLatencyMs = clock.nowMs() - startMs
            )
        }

        // 4. Check for non-finite values
        for ((i, v) in primaryTensor.withIndex()) {
            if (!v.isFinite()) {
                return DecodedOutput.Failure(
                    status = InferenceStatus.OUTPUT_INVALID,
                    message = "non-finite value at index $i: $v",
                    interpretationVersion = config.interpretationVersion,
                    inputTensorElementCount = primaryTensor.size,
                    expectedLabelCount = config.labels.size,
                    decodingLatencyMs = clock.nowMs() - startMs
                )
            }
        }

        // 5. Apply output semantics transformation
        val scores = when (config.outputSemantics) {
            OutputSemantics.SOFTMAX -> softmax(primaryTensor)
            OutputSemantics.RAW_SCORES -> primaryTensor.copyOf()
            OutputSemantics.ARGMAX -> primaryTensor.copyOf()
        }

        // 6. Map positions to labels
        val candidates = scores.mapIndexed { index, score ->
            RankedPrediction(
                category = config.labels[index],
                confidence = score.toDouble()
            )
        }

        // 7. Deterministic ranking
        val ranked = RankedPrediction.sortDeterministic(candidates)

        // 8. Cap at top-K
        val topK = ranked.take(config.topK)

        val latencyMs = clock.nowMs() - startMs

        return DecodedOutput.Success(
            rankedPredictions = topK,
            interpretationVersion = config.interpretationVersion,
            inputTensorElementCount = primaryTensor.size,
            outputSemanticType = config.outputSemantics,
            labelCount = config.labels.size,
            decodingLatencyMs = latencyMs
        )
    }

    /**
     * Decode and produce a ModelInferenceResult compatible
     * with the existing 8B-14/8B-15-7 pipeline.
     *
     * This bridges the new 8B-15-5 adapter output to the
     * existing ModelInferenceResult type so TaxonomyMappingEngine
     * and the evaluation boundary can consume it without change.
     */
    fun decodeToInferenceResult(
        rawOutput: AdapterRawModelOutput,
        config: OutputInterpretationConfig,
        modelId: String,
        modelVersion: String,
        modelChecksum: String? = null,
        preprocessVersion: String? = null,
        privacyVersion: String? = null,
        evidenceId: String? = null,
        experimentId: String? = null,
        clock: InferenceClock = SystemInferenceClock
    ): ModelInferenceResult {
        val decoded = decode(rawOutput, config, clock)

        return when (decoded) {
            is DecodedOutput.Failure -> {
                ModelInferenceResult(
                    modelId = modelId,
                    modelVersion = modelVersion,
                    modelChecksum = modelChecksum,
                    status = decoded.status,
                    rankedPredictions = emptyList(),
                    preprocessVersion = preprocessVersion,
                    privacyVersion = privacyVersion,
                    evidenceId = evidenceId,
                    experimentId = experimentId,
                    timestampMs = clock.nowMs(),
                    inferenceLatencyMs = rawOutput.inferenceDurationMs,
                    rawModelMetadata = mapOf(
                        "adapter.preprocessingVersion" to rawOutput.preprocessingVersion,
                        "adapter.artifactId" to rawOutput.artifactIdentity.artifactId
                    ),
                    failure = InferenceFailure(
                        status = decoded.status,
                        phase = "decode",
                        message = decoded.message
                    )
                )
            }
            is DecodedOutput.Success -> {
                ModelInferenceResult(
                    modelId = modelId,
                    modelVersion = modelVersion,
                    modelChecksum = modelChecksum,
                    status = InferenceStatus.SUCCESS,
                    rankedPredictions = decoded.rankedPredictions,
                    preprocessVersion = preprocessVersion,
                    privacyVersion = privacyVersion,
                    evidenceId = evidenceId,
                    experimentId = experimentId,
                    timestampMs = clock.nowMs(),
                    preprocessingLatencyMs = 0L,
                    inferenceLatencyMs = rawOutput.inferenceDurationMs,
                    rawModelMetadata = mapOf(
                        "adapter.preprocessingVersion" to rawOutput.preprocessingVersion,
                        "adapter.artifactId" to rawOutput.artifactIdentity.artifactId,
                        "adapter.deterministicHash" to (rawOutput.deterministicHash ?: "none"),
                        "interpretation.version" to decoded.interpretationVersion,
                        "interpretation.tensorSize" to (rawOutput.primaryOutput()?.size?.toString() ?: "0")
                    )
                )
            }
        }
    }

    // --------------------------------
    // INTERNAL: softmax transformation
    // --------------------------------

    /**
     * Deterministic softmax: exp(x_i) / sum(exp(x_j)).
     * Applied in-place conceptually; a new array is returned.
     *
     * Numerically stable: subtracts the max before exp.
     */
    internal fun softmax(logits: FloatArray): FloatArray {
        if (logits.isEmpty()) return floatArrayOf()

        val max = logits.max()
        val exps = FloatArray(logits.size) { i ->
            kotlin.math.exp((logits[i] - max).toDouble()).toFloat()
        }
        val sum = exps.sum()
        if (sum == 0f || !sum.isFinite()) {
            // Degenerate case: uniform distribution
            val uniform = 1.0f / logits.size
            return FloatArray(logits.size) { uniform }
        }
        for (i in exps.indices) {
            exps[i] /= sum
        }
        return exps
    }
}
