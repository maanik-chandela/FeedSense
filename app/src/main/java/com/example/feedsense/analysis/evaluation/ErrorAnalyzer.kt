package com.example.feedsense.analysis.evaluation

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.EvaluationRecord
import com.example.feedsense.model.GroundTruth

// --------------------------------
// ERROR ANALYZER (Milestone 8A-5)
// --------------------------------
//
// Deterministic, automatic detection of *which* error types a
// single evaluation unit exhibits, per capability. It reads
// only the already-stored evaluation triplet (prediction,
// truth, result) and NEVER mutates them.
//
// Honesty rules enforced here:
//   - UNKNOWN ground truth is NEVER treated as an error. If a
//     truth dimension is null / UNKNOWN, that dimension is
//     simply not judged.
//   - UNCOMPARABLE units are reported as such, not as model
//     failures.
//   - EVIDENCE_ERROR is only ever a conservative OBSERVED proxy
//     (missing recorded confidence), never a ground-truth claim.
//   - Error counts are derived FROM the same EvaluationRecord
//     fields the 8A-3 engine uses, so 8A-5 never conflicts with
//     8A-3 headline metrics.

object ErrorAnalyzer {

    /*
     * Frozen confidence cutoffs for the calibration analysis.
     * "Overconfident" here means the model is confident
     * (>= 0.8) yet wrong; "underconfident" means it is
     * correct yet unsure (<= 0.5). These are RESEARCH labels,
     * not retraining thresholds.
     */
    const val OVERCONFIDENCE_THRESHOLD = 0.8
    const val UNDERCONFIDENCE_THRESHOLD = 0.5

    // --------------------------------
    // DETECTED ERROR
    // --------------------------------

    data class DetectedError(
        val evaluationItemId: String,
        val capability: String,
        val errorType: String,
        val severity: String,
        val sourceEvidence: String,      // CAUSE_OBSERVED / CAUSE_HYPOTHESIS
        val rootCause: String?,
        val researchImpact: Boolean,
        val overconfident: Boolean,
        val details: Map<String, String> = emptyMap()
    )

    /**
     * Detects every error type applicable to a single unit.
     * Returns an empty list when the unit is fully correct and
     * comparable (or when every judged dimension agrees).
     */
    fun detect(unit: EvaluationUnit): List<DetectedError> {
        val found = mutableListOf<DetectedError>()
        val prediction = unit.prediction
        val truth = unit.truth
        val result = unit.result
        val id = unit.item.id

        // ---- Comparability guard ----
        if (result.verdict == EvaluationRecord.VERDICT_UNCOMPARABLE ||
            result.comparable == false
        ) {
            found += detected(
                unit = unit,
                capability = ErrorTypes.CAP_SEGMENTATION,
                errorType = ErrorTypes.ERROR_UNCOMPARABLE,
                rootCause = ErrorTypes.CAUSE_SEGMENTATION,
                overconfident = false,
                details = mapOf(
                    "segmentationError" to
                        (result.segmentationError ?: "notComparable")
                )
            )
            return found
        }

        // ---- UNKNOWN truth: never an error ----
        if (result.verdict == EvaluationRecord.VERDICT_UNKNOWN) {
            return found
        }

        val confidence = prediction.confidence
        val overconfident =
            confidence != null &&
                confidence >= OVERCONFIDENCE_THRESHOLD
        val underconfident =
            confidence != null &&
                confidence <= UNDERCONFIDENCE_THRESHOLD &&
                result.verdict == EvaluationRecord.VERDICT_CORRECT

        // Cross-cutting calibration errors are derived first so
        // the per-capability errors can be tagged with whether
        // the model was overconfident while wrong.
        if (result.verdict == EvaluationRecord.VERDICT_INCORRECT &&
            overconfident
        ) {
            found += detected(
                unit = unit,
                capability = ErrorTypes.CAP_CALIBRATION,
                errorType = ErrorTypes.ERROR_OVERCONFIDENT_ERROR,
                rootCause = ErrorTypes.CAUSE_CLASSIFIER_CALIBRATION,
                overconfident = true,
                details = mapOf(
                    "confidence" to String.format("%.3f", confidence)
                )
            )
        }

        if (underconfident) {
            found += detected(
                unit = unit,
                capability = ErrorTypes.CAP_CALIBRATION,
                errorType = ErrorTypes.ERROR_UNDERCONFIDENT_CORRECT,
                rootCause = ErrorTypes.CAUSE_CLASSIFIER_CALIBRATION,
                overconfident = false,
                details = mapOf(
                    "confidence" to String.format("%.3f", confidence)
                )
            )
        }

        // ---- Segmentation error ----
        if (result.segmentationError != null) {
            found += detected(
                unit = unit,
                capability = ErrorTypes.CAP_SEGMENTATION,
                errorType = ErrorTypes.ERROR_SEGMENTATION,
                rootCause = ErrorTypes.CAUSE_SEGMENTATION,
                overconfident = false,
                details = mapOf(
                    "segmentationError" to result.segmentationError
                )
            )
        }

        // ---- Category family ----
        val truthCategories = truthLabelSet(truth)
        val predictedCategories = predictedLabelSet(prediction)
        val truthCategoryDefinitive =
            truth.ambiguity != GroundTruth.AMBIGUITY_UNKNOWN

        if (truthCategoryDefinitive) {
            val wrongClass =
                result.categoryCorrect == false

            if (wrongClass) {
                found += detected(
                    unit = unit,
                    capability = ErrorTypes.CAP_CATEGORY,
                    errorType = ErrorTypes.ERROR_WRONG_CLASS,
                    rootCause = ErrorTypes.CAUSE_DATA_VS_PREDICTION,
                    overconfident = overconfident,
                    details = mapOf(
                        "predicted" to (prediction.category ?: "null"),
                        "truth" to (truth.category ?: "null")
                    )
                )
            }

            // FALSE_POSITIVE: predicted a label not in the truth set.
            val fp = predictedCategories.any { it !in truthCategories }
            if (fp) {
                found += detected(
                    unit = unit,
                    capability = ErrorTypes.CAP_CATEGORY,
                    errorType = ErrorTypes.ERROR_FALSE_POSITIVE,
                    rootCause = ErrorTypes.CAUSE_DATA_VS_PREDICTION,
                    overconfident = overconfident,
                    details = mapOf(
                        "extra" to (predictedCategories - truthCategories)
                            .joinToString(",")
                    )
                )
            }

            // FALSE_NEGATIVE: truth has a label the prediction missed.
            val fn = truthCategories.any { it !in predictedCategories }
            if (fn) {
                found += detected(
                    unit = unit,
                    capability = ErrorTypes.CAP_CATEGORY,
                    errorType = ErrorTypes.ERROR_FALSE_NEGATIVE,
                    rootCause = ErrorTypes.CAUSE_DATA_VS_PREDICTION,
                    overconfident = overconfident,
                    details = mapOf(
                        "missing" to (truthCategories - predictedCategories)
                            .joinToString(",")
                    )
                )
            }

            // MULTI_LABEL_MISMATCH: label sets differ at all.
            if (predictedCategories != truthCategories) {
                found += detected(
                    unit = unit,
                    capability = ErrorTypes.CAP_CATEGORY,
                    errorType = ErrorTypes.ERROR_MULTI_LABEL_MISMATCH,
                    rootCause = ErrorTypes.CAUSE_DATA_VS_PREDICTION,
                    overconfident = overconfident,
                    details = mapOf(
                        "predicted" to predictedCategories.joinToString(","),
                        "truth" to truthCategories.joinToString(",")
                    )
                )
            }

            // MISSED_DETECTION: truth asserts a class but the AI
            // predicted nothing at all.
            if (CategoryCatalog.normalize(truth.category) != null &&
                CategoryCatalog.normalize(prediction.category) == null
            ) {
                found += detected(
                    unit = unit,
                    capability = ErrorTypes.CAP_CATEGORY,
                    errorType = ErrorTypes.ERROR_MISSED_DETECTION,
                    rootCause = ErrorTypes.CAUSE_DATA_VS_PREDICTION,
                    overconfident = overconfident,
                    details = mapOf(
                        "truth" to truth.category.toString()
                    )
                )
            }
        }

        // ---- Ambiguity awareness ----
        if (truth.ambiguity == GroundTruth.AMBIGUITY_AMBIGUOUS ||
            truth.ambiguity == GroundTruth.AMBIGUITY_MIXED
        ) {
            found += detected(
                unit = unit,
                capability = ErrorTypes.CAP_CATEGORY,
                errorType = ErrorTypes.ERROR_AMBIGUOUS_CONTENT,
                rootCause = ErrorTypes.CAUSE_AMBIGUITY,
                overconfident = overconfident,
                details = mapOf(
                    "ambiguity" to truth.ambiguity
                )
            )
        }

        // ---- Platform ----
        if (truth.platform?.isNotBlank() == true) {
            if (result.platformAgreement == false) {
                found += detected(
                    unit = unit,
                    capability = ErrorTypes.CAP_PLATFORM,
                    errorType = ErrorTypes.ERROR_PLATFORM,
                    rootCause = ErrorTypes.CAUSE_PLATFORM_MISDETECT,
                    overconfident = overconfident,
                    details = mapOf(
                        "predicted" to (prediction.platform ?: "null"),
                        "truth" to truth.platform
                    )
                )
            }
        }

        // ---- Content type ----
        if (!truth.contentType.isNullOrBlank()) {
            if (result.contentTypeAgreement == false) {
                found += detected(
                    unit = unit,
                    capability = ErrorTypes.CAP_CONTENT_TYPE,
                    errorType = ErrorTypes.ERROR_CONTENT_TYPE,
                    rootCause = ErrorTypes.CAUSE_DATA_VS_PREDICTION,
                    overconfident = overconfident,
                    details = mapOf(
                        "predicted" to (prediction.contentType ?: "null"),
                        "truth" to truth.contentType
                    )
                )
            }
        }

        // ---- Duration / temporal ----
        if (result.durationInaccurate == true) {
            found += detected(
                unit = unit,
                capability = ErrorTypes.CAP_DURATION,
                errorType = ErrorTypes.ERROR_DURATION,
                rootCause = ErrorTypes.CAUSE_TEMPORAL,
                overconfident = overconfident,
                details = mapOf(
                    "predictedSeconds" to prediction.durationSeconds.toString(),
                    "truthSeconds" to truth.durationSeconds.toString(),
                    "errorSeconds" to
                        (result.durationErrorSeconds ?: 0).toString()
                )
            )
            found += detected(
                unit = unit,
                capability = ErrorTypes.CAP_DURATION,
                errorType = ErrorTypes.ERROR_TEMPORAL,
                rootCause = ErrorTypes.CAUSE_TEMPORAL,
                overconfident = overconfident,
                details = mapOf(
                    "errorSeconds" to
                        (result.durationErrorSeconds ?: 0).toString()
                )
            )
        }

        // ---- Skip ----
        if (truth.skipped != null &&
            result.skippedAgreement == false
        ) {
            found += detected(
                unit = unit,
                capability = ErrorTypes.CAP_SKIP,
                errorType = ErrorTypes.ERROR_SKIP,
                rootCause = ErrorTypes.CAUSE_DATA_VS_PREDICTION,
                overconfident = overconfident,
                details = mapOf(
                    "predicted" to prediction.skipped.toString(),
                    "truth" to truth.skipped.toString()
                )
            )
        }

        // ---- Interactions (per signal) ----
        val interactionDisagreements = interactionDisagreements(prediction, truth)
        if (interactionDisagreements.isNotEmpty()) {
            found += detected(
                unit = unit,
                capability = ErrorTypes.CAP_LIKE,
                errorType = ErrorTypes.ERROR_INTERACTION,
                rootCause = ErrorTypes.CAUSE_DATA_VS_PREDICTION,
                overconfident = overconfident,
                details = mapOf(
                    "signals" to interactionDisagreements.joinToString(",")
                )
            )
        }

        // ---- Topic / tone ----
        if (!truth.topic.isNullOrBlank() &&
            result.topicAgreement == false
        ) {
            found += detected(
                unit = unit,
                capability = ErrorTypes.CAP_TOPIC,
                errorType = ErrorTypes.ERROR_TOPIC,
                rootCause = ErrorTypes.CAUSE_DATA_VS_PREDICTION,
                overconfident = overconfident,
                details = mapOf(
                    "predicted" to (prediction.topic ?: "null"),
                    "truth" to truth.topic
                )
            )
        }

        if (!truth.tone.isNullOrBlank() &&
            result.toneAgreement == false
        ) {
            found += detected(
                unit = unit,
                capability = ErrorTypes.CAP_TONE,
                errorType = ErrorTypes.ERROR_TONE,
                rootCause = ErrorTypes.CAUSE_DATA_VS_PREDICTION,
                overconfident = overconfident,
                details = mapOf(
                    "predicted" to (prediction.tone ?: "null"),
                    "truth" to truth.tone
                )
            )
        }

        // ---- Evidence proxy ----
        if (prediction.confidence == null) {
            found += detected(
                unit = unit,
                capability = ErrorTypes.CAP_CALIBRATION,
                errorType = ErrorTypes.ERROR_EVIDENCE,
                rootCause = ErrorTypes.CAUSE_MISSING_EVIDENCE,
                overconfident = false,
                details = mapOf(
                    "note" to "proxy: no recorded confidence/evidence"
                )
            )
        }

        return found
    }

    // --------------------------------
    // HELPERS
    // --------------------------------

    fun orderedBySeverity(errors: List<DetectedError>): List<DetectedError> {
        return errors.sortedWith(
            compareByDescending<DetectedError> {
                ErrorSeverity.severityRank(it.severity)
            }.thenBy { it.capability }
                .thenBy { it.errorType }
        )
    }

    private fun detected(
        unit: EvaluationUnit,
        capability: String,
        errorType: String,
        rootCause: String?,
        overconfident: Boolean,
        details: Map<String, String>
    ): DetectedError {
        val severity = ErrorSeverity.effectiveSeverity(
            error = errorType,
            overconfident = overconfident,
            capability = capability
        )
        return DetectedError(
            evaluationItemId = unit.item.id,
            capability = capability,
            errorType = errorType,
            severity = severity,
            sourceEvidence = ErrorTypes.CAUSE_OBSERVED,
            rootCause = rootCause,
            researchImpact = ErrorSeverity.isResearchImpactful(
                error = errorType,
                effectiveSeverity = severity,
                realDataAvailable = true
            ),
            overconfident = overconfident,
            details = details
        )
    }

    fun predictedLabelSet(prediction: AiPredictionRecord): Set<String> {
        return buildSet {
            CategoryCatalog.normalize(prediction.category)?.let { add(it) }
            prediction.secondaryCategories.forEach {
                CategoryCatalog.normalize(it)?.let { label -> add(label) }
            }
        }
    }

    fun truthLabelSet(truth: GroundTruth): Set<String> {
        return buildSet {
            CategoryCatalog.normalize(truth.category)?.let { add(it) }
            truth.secondaryCategories.forEach {
                CategoryCatalog.normalize(it)?.let { label -> add(label) }
            }
        }
    }

    /**
     * Which interaction signals the model got wrong, where the
     * truth is decided (not UNKNOWN). A signal is wrong when:
     *   - truth is true but the model did not predict it, or
     *   - truth is false and the model did predict it.
     */
    fun interactionDisagreements(
        prediction: AiPredictionRecord,
        truth: GroundTruth
    ): List<String> {
        val disagreed = mutableListOf<String>()
        val truthDecided = mapOf(
            GroundTruth.INTERACTION_LIKED to truth.liked,
            GroundTruth.INTERACTION_COMMENTED to truth.commented,
            GroundTruth.INTERACTION_SHARED to truth.shared,
            GroundTruth.INTERACTION_SAVED to truth.saved,
            GroundTruth.INTERACTION_FOLLOWED to truth.followed,
            GroundTruth.INTERACTION_PAUSED to truth.paused,
            GroundTruth.INTERACTION_PLAYING to truth.playing
        )
        val predicted = prediction.interactionSignals.toSet()
        truthDecided.forEach { (signal, decided) ->
            if (decided != null) {
                val aiSays = signal in predicted
                if (aiSays != decided) {
                    disagreed += signal
                }
            }
        }
        return disagreed
    }
}
