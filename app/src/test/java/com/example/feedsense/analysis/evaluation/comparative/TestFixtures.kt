package com.example.feedsense.analysis.evaluation.comparative

import com.example.feedsense.analysis.evidence.decision.DecisionState
import com.example.feedsense.analysis.evidence.decision.ItemPredictionResult
import com.example.feedsense.analysis.evidence.decision.UncertaintyState
import com.example.feedsense.analysis.evidence.temporal.CoverageState
import com.example.feedsense.analysis.evidence.temporal.ConflictLevel
import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.EvaluationItem
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.GroundTruth

/*
 * Milestone 8B-8 test fixtures.
 *
 * Minimal, deterministic builders for the comparison inputs.
 * Category values use real Catalog keys so normalization and
 * domainOf behave as in production.
 */
object TestFixtures {

    private var counter = 0
    private fun next(): String {
        counter++
        return "fixture-$counter"
    }

    fun item(id: String = next()): EvaluationItem =
        EvaluationItem(
            id = id,
            feedItemId = "feed-$id",
            sessionId = "session-1"
        )

    fun truth(
        itemId: String,
        category: String,
        ambiguity: String = GroundTruth.AMBIGUITY_CLEAR,
        durationSeconds: Int? = 20,
        platform: String? = "reels",
        contentType: String? = FeedItem.CONTENT_SHORT_VIDEO,
        secondary: List<String> = emptyList(),
        id: String = "truth-${itemId}"
    ): GroundTruth = GroundTruth(
        id = id,
        evaluationItemId = itemId,
        category = category,
        categoryDomain = null,
        secondaryCategories = secondary,
        ambiguity = ambiguity,
        platform = platform,
        contentType = contentType,
        durationSeconds = durationSeconds
    )

    fun baselineRecord(
        itemId: String,
        category: String?,
        confidence: Double? = 0.9,
        modelVersion: String = "baseline-v1"
    ): AiPredictionRecord = AiPredictionRecord(
        evaluationItemId = itemId,
        source = "AI",
        modelVersion = modelVersion,
        category = category,
        categoryDomain = null,
        confidence = confidence,
        uncertaintyLevel = FeedItem.UNCERTAINTY_LOW
    )

    fun eightBRecord(
        itemId: String,
        category: String?,
        uncertaintyLevel: String = FeedItem.UNCERTAINTY_LOW
    ): AiPredictionRecord = AiPredictionRecord(
        evaluationItemId = itemId,
        source = "AI_EVIDENCE_AWARE",
        modelVersion = "fusion-v1/item-decision-v1",
        category = category,
        categoryDomain = null,
        confidence = 0.6,
        uncertaintyLevel = uncertaintyLevel
    )

    /**
     * Builds an 8B ItemPredictionResult with selectable evidence
     * characteristics for stratification tests. Uses ItemPredictionResult
     * construction with defaults for unneeded fields.
     */
    fun eightBDecision(
        itemId: String = next(),
        primaryCategory: String?,
        decisionState: DecisionState = DecisionState.DECIDED,
        coverage: CoverageState = CoverageState.HIGH_COVERAGE,
        conflict: ConflictLevel = ConflictLevel.NONE,
        outlier: Boolean? = null,
        evidenceTypes: List<String> = emptyList(),
        uncertainty: UncertaintyState = UncertaintyState.LOW
    ): ItemPredictionResult = ItemPredictionResult(
        sessionId = "s",
        feedItemId = "feed-$itemId",
        primaryCategory = primaryCategory,
        decisionState = decisionState,
        uncertainty = uncertainty,
        supportScore = 0.7,
        decisionMargin = 0.3,
        evidenceCoverage = coverage,
        coverageRatio = if (coverage == CoverageState.HIGH_COVERAGE) 0.9 else 0.1,
        observedDurationMs = 9000,
        itemDurationMs = 10000,
        evidenceQuality = "HIGH",
        sourceDiversity = 3,
        uniqueEvidenceIdentities = 3,
        evidenceTypesPresent = evidenceTypes,
        candidateCategories = emptyList(),
        supportingEvidence = emptyMap(),
        contradictingEvidence = emptyMap(),
        temporalConflict = conflict,
        transitionDetected = false,
        internalTransitionDetected = false,
        secondaryCategory = null,
        uncertaintyReasons = emptyList(),
        representativeFrameOutlier = outlier,
        modelVersion = "fusion-v1",
        fusionVersion = "fusion-v1",
        decisionVersion = "item-decision-v1",
        createdAtMs = 0,
        modelConfidence = 0.7
    )

    /**
     * Convenience: build a full PairedPrediction where the caller
     * controls correctness of each side. A pricipal shortcut: pass
     * the truth category and the predicted category.
     */
    fun pair(
        id: String = next(),
        truthCategory: String,
        baselineCategory: String?,
        eightBCategory: String?,
        ambiguity: String = GroundTruth.AMBIGUITY_CLEAR,
        durationSeconds: Int? = 20,
        platform: String? = "reels",
        contentType: String? = FeedItem.CONTENT_SHORT_VIDEO,
        eightBDecision: ItemPredictionResult? = null,
        truthId: String = "truth-$id"
    ): PairedPrediction {
        val it = item(id)
        val t = truth(
            itemId = id,
            category = truthCategory,
            ambiguity = ambiguity,
            durationSeconds = durationSeconds,
            platform = platform,
            contentType = contentType,
            id = truthId
        )
        val baseline = baselineRecord(id, baselineCategory)
        val eightB = eightBRecord(id, eightBCategory)
        return PairedPredictionBuilder.build(
            item = it,
            truth = t,
            baseline = baseline,
            eightBDecision = eightBDecision
                ?: eightBDecisionFor(eightBCategory),
            eightBRecordOverride = eightB
        )
    }

    fun eightBDecisionFor(
        category: String?
    ): ItemPredictionResult = eightBDecision(
        primaryCategory = category,
        decisionState = if (category == null) {
            DecisionState.INSUFFICIENT_EVIDENCE
        } else {
            DecisionState.DECIDED
        }
    )
}
