package com.example.feedsense.analysis.evaluation.comparative

import com.example.feedsense.model.GroundTruth

/*
 * Milestone 8B-8.
 *
 * Stratification keys.
 *
 * A stratified comparison re-runs the comparative metrics within
 * well-defined subgroups of the SAME eligible population. Every
 * stratum key derives from the shared item/truth (or the 8B
 * decision), never from the baseline, so the two systems are
 * compared inside identical cells.
 *
 * Supported stratifications:
 *   - duration buckets  (GroundTruth.durationSeconds)
 *   - platform          (GroundTruth.platform, normalized)
 *   - content type      (GroundTruth.contentType)
 *   - evidence condition (from the 8B ItemPredictionResult:
 *       decision state, coverage state, temporal conflict,
 *       representative-frame outlier)
 */
object Stratifications {

    // --------------------------------
    // DURATION
    // --------------------------------

    fun durationBucket(
        truth: GroundTruth
    ): DurationBuckets.Bucket =
        DurationBuckets.of(truth.durationSeconds)

    // --------------------------------
    // PLATFORM
    // --------------------------------

    fun platform(
        truth: GroundTruth
    ): String {
        val p = truth.platform
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return p ?: "UNKNOWN"
    }

    // --------------------------------
    // CONTENT TYPE
    // --------------------------------

    fun contentType(
        truth: GroundTruth
    ): String {
        val c = truth.contentType
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return c ?: "UNKNOWN"
    }

    // --------------------------------
    // EVIDENCE CONDITION (8B side)
    // --------------------------------

    /**
     * Coarse evidence-coverage state for stratification. Uses
     * the 8B decision's CoverageState where present, else
     * UNKNOWN. The baseline is not involved in defining the cell,
     * but this is an 8B-centric view used only to check whether
     * 8B behaves differently under different evidence conditions.
     */
    fun evidenceCoverage(
        decision: com.example.feedsense.analysis.evidence.decision.ItemPredictionResult?
    ): String {
        if (decision == null) return "UNKNOWN"
        return decision.evidenceCoverage.name
    }

    /**
     * Whether the 8B decision observed OCR/scene text via its
     * evidence types. The baseline has no OCR channel; this cell
     * isolates items where OCR is present to test the hypothesis
     * that 8B's OCR channel changes decisions. Elements naming
     * OCR follows the 8B-7 evidence-type vocabulary; if absent we
     * fall back to UNKNOWN rather than guessing.
     */
    fun hasOcrEvidence(
        decision: com.example.feedsense.analysis.evidence.decision.ItemPredictionResult?
    ): String {
        if (decision == null) return "UNKNOWN"
        val typeNames = decision.evidenceTypesPresent
            .joinToString(",") { it.trim() }
            .lowercase()
        val hasOcr = typeNames.contains("ocr") ||
            decision.uncertaintyReasons.any {
                it.lowercase().contains("ocr")
            }
        return if (hasOcr) "OCR_PRESENT" else "OCR_ABSENT"
    }

    /**
     * Temporal-conflict stratification from the 8B decision.
     * High-conflict items are where the timeline flips between
     * categories; the baseline cannot observe this.
     */
    fun temporalConflict(
        decision: com.example.feedsense.analysis.evidence.decision.ItemPredictionResult?
    ): String {
        if (decision == null) return "UNKNOWN"
        return when (decision.temporalConflict) {
            com.example.feedsense.analysis.evidence.temporal.ConflictLevel.HIGH,
            com.example.feedsense.analysis.evidence.temporal.ConflictLevel.MEDIUM ->
                "CONFLICT"
            else -> "NO_CONFLICT"
        }
    }

    /**
     * Representative-frame outlier stratification from the 8B
     * decision. A single frame that disagrees with the corpus is
     * a documented source of spurious single-frame confidence.
     */
    fun representativeFrameOutlier(
        decision: com.example.feedsense.analysis.evidence.decision.ItemPredictionResult?
    ): String {
        if (decision == null) return "UNKNOWN"
        return when (decision.representativeFrameOutlier) {
            true -> "OUTLIER"
            false -> "NOT_OUTLIER"
            null -> "UNKNOWN"
        }
    }

    // --------------------------------
    // 8B ABSTENTION / DECISION STATE
    // --------------------------------

    /**
     * 8B decision state as a stratum label (DECIDED vs
     * ABSTAINED-by-state). Used to inspect whether the agreed
     * cases behaved differently from the cases 8B refused to
     * decide.
     */
    fun eightBDecisionState(
        decision: com.example.feedsense.analysis.evidence.decision.ItemPredictionResult?
    ): String {
        if (decision == null) return "UNKNOWN"
        return decision.decisionState.name
    }

    // --------------------------------
    // COMPLETE KEY SET
    // --------------------------------

    data class Keys(
        val duration: DurationBuckets.Bucket,
        val platform: String,
        val contentType: String,
        val evidenceCoverage: String,
        val hasOcr: String,
        val temporalConflict: String,
        val representativeFrameOutlier: String,
        val eightBDecisionState: String
    )

    fun keys(pair: PairedPrediction): Keys {
        val d = pair.eightBDecision
        return Keys(
            duration = durationBucket(pair.truth),
            platform = platform(pair.truth),
            contentType = contentType(pair.truth),
            evidenceCoverage = evidenceCoverage(d),
            hasOcr = hasOcrEvidence(d),
            temporalConflict = temporalConflict(d),
            representativeFrameOutlier =
                representativeFrameOutlier(d),
            eightBDecisionState = eightBDecisionState(d)
        )
    }
}
