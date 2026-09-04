package com.example.feedsense.analysis.evidence.decision

import com.example.feedsense.analysis.evidence.temporal.CandidateCategoryScore
import com.example.feedsense.analysis.evidence.temporal.ConflictLevel
import com.example.feedsense.analysis.evidence.temporal.CoverageState
import com.example.feedsense.analysis.evidence.temporal.EvidenceReference
import com.example.feedsense.analysis.evidence.temporal.ItemEvidenceSnapshot

/*
 * Milestone 8B-7.
 *
 * Item decision engine.
 *
 * Converts an 8B-6 ItemEvidenceSnapshot into an item-level
 * ItemPredictionResult with an explicit decision state,
 * explicit uncertainty, and an interpretable support /
 * evidence breakdown.
 *
 * Approach:
 *   1. Gather raw evidence from the snapshot.
 *   2. Compute per-category CategorySupport (support,
 *      margin, source diversity, persistence, temporal
 *      coverage, contradiction).
 *   3. Classify the decision state (DECIDED / AMBIGUOUS /
 *      INSUFFICIENT_EVIDENCE / UNKNOWN) using explicit
 *      thresholds from ItemDecisionConfig.
 *   4. Classify the uncertainty state (LOW / MODERATE /
 *      HIGH / UNRESOLVED) from coverage, quality,
 *      diversity, conflict, and margin components.
 *   5. Build an honest, explainable ItemPredictionResult.
 *
 * Design guarantees:
 *   - Deterministic: same snapshot + config -> same result.
 *   - No neural model, no training, no threshold tuning.
 *   - Support score is evidence strength, NOT probability.
 *   - Missing evidence stays missing (never interpolated).
 *   - Honest abstention: insufficient/ambiguous/unknown
 *     are first-class outputs, not failures to hide.
 *   - Representative-frame conflicts and internal
 *     transitions are surfaced as metadata, not silently
 *     folded into a single label.
 *   - Privacy masking / short exposure raise
 *     INSUFFICIENT_EVIDENCE honestly.
 *
 * Baseline is preserved: this engine is the
 * evidence-aware decision path. The existing classifier
 * result remains the baseline and is NEVER overwritten.
 */
object ItemDecisionEngine {

    /*
     * Upper bound for a valid evidence support score.
     * Support scores are normalized into [0,1] by 8B-6;
     * a score well above this indicates corrupted /
     * adversarial data that must not yield a confident
     * decision. A small tolerance is allowed.
     */
    private const val VALID_SUPPORT_UPPER_BOUND = 1.2

    /*
     * Computes the evidence-aware item prediction.
     */
    fun decide(
        snapshot: ItemEvidenceSnapshot,
        config: ItemDecisionConfig =
            ItemDecisionConfig.DEFAULT
    ): ItemPredictionResult {

        // --------------------------------
        // PRE-CHECKS
        // --------------------------------

        // No usable evidence points at all -> honest
        // abstention. This is INSUFFICIENT_EVIDENCE (we
        // cannot decide on nothing), not UNKNOWN.
        if (snapshot.usableEvidencePoints <= 0) {
            return insufficientEvidence(
                snapshot = snapshot,
                reason = "no_usable_evidence",
                config = config
            )
        }

        // Evidence was collected but never mapped to any
        // candidate category -> UNKNOWN ("evidence present
        // but unmappable").
        if (snapshot.candidateCategories.isEmpty()) {
            return ItemPredictionResult.empty(
                sessionId = snapshot.sessionId,
                feedItemId = snapshot.feedItemId,
                modelVersion = snapshot.fusionVersion,
                fusionVersion = snapshot.fusionVersion,
                modelConfidence = null
            )
        }

        // --------------------------------
        // PER-CATEGORY SUPPORT
        // --------------------------------

        val candidates =
            snapshot.candidateCategories

        val supports =
            candidates.map {
                computeSupport(
                    it,
                    topScore = candidates[0].supportScore
                )
            }
                .sortedByDescending { it.totalSupport }

        val top = supports.first()
        val second = supports.getOrNull(1)
        val secondScore = second?.totalSupport ?: 0.0

        // Relative decision margin ratio.
        val marginRatio =
            if (top.totalSupport > 0) {
                (top.totalSupport - secondScore) /
                        top.totalSupport
            } else {
                0.0
            }

        // --------------------------------
        // COVERAGE / QUALITY / DIVERSITY
        // --------------------------------

        val coverage = snapshot.coverageState
        val coverageRatio = snapshot.coverageRatio
        val sourceDiversity =
            snapshot.evidenceTypesPresent.size
        val quality = evaluateEvidenceQuality(snapshot)

        // --------------------------------
        // CONFLICT
        // --------------------------------

        val conflict = snapshot.conflictLevel
        val contradictionStrength =
            evaluateContradiction(snapshot)

        // --------------------------------
        // DECISION STATE
        // --------------------------------

        val state = classifyDecisionState(
            snapshot = snapshot,
            top = top,
            secondScore = secondScore,
            marginRatio = marginRatio,
            coverageRatio = coverageRatio,
            sourceDiversity = sourceDiversity,
            config = config
        )

        // --------------------------------
        // PRIMARY + SECONDARY
        // --------------------------------

        val primary =
            if (state == DecisionState.DECIDED ||
                state == DecisionState.AMBIGUOUS) {
                top.category
            } else {
                null
            }

        val secondary =
            if (state == DecisionState.DECIDED ||
                state == DecisionState.AMBIGUOUS) {
                second
                    ?.takeIf {
                        it.totalSupport >=
                            config.minSupportForDecided * 0.6
                    }
                    ?.category
            } else {
                null
            }

        // --------------------------------
        // UNCERTAINTY
        // --------------------------------

        val uncertaintyReasons = buildList {
            when (state) {
                DecisionState.INSUFFICIENT_EVIDENCE ->
                    add("insufficient_evidence")
                DecisionState.UNKNOWN ->
                    add("unknown_category")
                DecisionState.AMBIGUOUS ->
                    add("ambiguous_close_candidates")
                DecisionState.DECIDED -> {
                    // fallthrough, may still add components
                }
            }
            if (coverageRatio <
                config.coverageForHighUncertainty) {
                add("low_coverage")
            }
            if (top.sourceCount <=
                config.sourcesForModerateUncertainty) {
                add("low_source_diversity")
            }
            if (contradictionStrength >=
                config.ambiguityConflictThreshold) {
                add("conflicting_evidence")
            }
            if (snapshot.possibleInternalTransitions
                    .isNotEmpty()) {
                add("possible_internal_transition")
            }
            if (snapshot.representativeFrameOutlier == true) {
                add("representative_frame_outlier")
            }
            if (quality < config.minQualityForLowUncertainty) {
                add("lower_evidence_quality")
            }
        }.distinct()

        val uncertainty = classifyUncertainty(
            state = state,
            coverageRatio = coverageRatio,
            coverageState = coverage,
            primarySourceCount = top.sourceCount,
            contradictionStrength = contradictionStrength,
            marginRatio = marginRatio,
            quality = quality,
            representativeFrameOutlier =
                snapshot.representativeFrameOutlier,
            internalTransitionDetected =
                snapshot.possibleInternalTransitions
                    .isNotEmpty(),
            config = config
        )

        // --------------------------------
        // BUILD RESULT
        // --------------------------------

        return ItemPredictionResult(
            sessionId = snapshot.sessionId,
            feedItemId = snapshot.feedItemId,
            primaryCategory = primary,
            decisionState = state,
            uncertainty = uncertainty,
            supportScore = top.totalSupport,
            decisionMargin = top.totalSupport - secondScore,
            evidenceCoverage = coverage,
            coverageRatio = coverageRatio,
            observedDurationMs = snapshot.observedDurationMs,
            itemDurationMs = snapshot.itemDurationMs,
            evidenceQuality = qualityLabel(quality),
            sourceDiversity = sourceDiversity,
            uniqueEvidenceIdentities =
                snapshot.uniqueEvidenceIdentities,
            evidenceTypesPresent =
                snapshot.evidenceTypesPresent,
            candidateCategories = supports,
            supportingEvidence =
                summarizeEvidence(
                    snapshot.supportingEvidenceByCategory
                ),
            contradictingEvidence =
                summarizeEvidence(
                    snapshot.contradictingEvidenceByCategory
                ),
            temporalConflict = conflict,
            transitionDetected = snapshot.transitionDetected,
            internalTransitionDetected =
                snapshot.possibleInternalTransitions
                    .isNotEmpty(),
            secondaryCategory = secondary,
            uncertaintyReasons = uncertaintyReasons,
            representativeFrameOutlier =
                snapshot.representativeFrameOutlier,
            modelVersion = snapshot.fusionVersion,
            fusionVersion = snapshot.fusionVersion,
            decisionVersion = config.decisionVersion,
            createdAtMs = System.currentTimeMillis(),
            modelConfidence = null
        )
    }

    // ========================================
    // INSUFFICIENT EVIDENCE RESULTS
    // ========================================

    /*
     * Builds an INSUFFICIENT_EVIDENCE result for an item
     * with no usable evidence points. Honest abstention:
     * we cannot decide on nothing, and we do not fabricate
     * a category.
     */
    private fun insufficientEvidence(
        snapshot: ItemEvidenceSnapshot,
        reason: String,
        config: ItemDecisionConfig
    ): ItemPredictionResult {
        return ItemPredictionResult(
            sessionId = snapshot.sessionId,
            feedItemId = snapshot.feedItemId,
            primaryCategory = null,
            decisionState =
                DecisionState.INSUFFICIENT_EVIDENCE,
            uncertainty =
                UncertaintyState.UNRESOLVED,
            supportScore = 0.0,
            decisionMargin = 0.0,
            evidenceCoverage = snapshot.coverageState,
            coverageRatio = snapshot.coverageRatio,
            observedDurationMs = snapshot.observedDurationMs,
            itemDurationMs = snapshot.itemDurationMs,
            evidenceQuality = "NONE",
            sourceDiversity =
                snapshot.evidenceTypesPresent.size,
            uniqueEvidenceIdentities =
                snapshot.uniqueEvidenceIdentities,
            evidenceTypesPresent =
                snapshot.evidenceTypesPresent,
            candidateCategories = emptyList(),
            supportingEvidence = emptyMap(),
            contradictingEvidence = emptyMap(),
            temporalConflict = snapshot.conflictLevel,
            transitionDetected = snapshot.transitionDetected,
            internalTransitionDetected =
                snapshot.possibleInternalTransitions
                    .isNotEmpty(),
            secondaryCategory = null,
            uncertaintyReasons = listOf(reason),
            representativeFrameOutlier =
                snapshot.representativeFrameOutlier,
            modelVersion = snapshot.fusionVersion,
            fusionVersion = snapshot.fusionVersion,
            decisionVersion = config.decisionVersion,
            createdAtMs = System.currentTimeMillis(),
            modelConfidence = null
        )
    }

    // ========================================
    // SUPPORT COMPUTATION
    // ========================================

    /*
     * Computes the interpretable CategorySupport for one
     * candidate category.
     */
    private fun computeSupport(
        candidate: CandidateCategoryScore,
        topScore: Double
    ): CategorySupport {
        val margin =
            candidate.supportScore - topScore

        return CategorySupport(
            category = candidate.category,
            totalSupport = candidate.supportScore,
            decisionMargin = margin,
            sourceCount = candidate.evidenceTypes.size,
            observationCount =
                candidate.supportingCount,
            temporalCoverage = clamp01(
                candidate.temporalSpreadMs.toDouble() /
                    maxOf(1L, candidate.lastSeenRelativeMs)
            ),
            persistence = candidate.persistenceLevel,
            contradictionCount =
                candidate.contradictingCount,
            contradictionStrength =
                contradictionStrengthOf(candidate),
            supportingSignals =
                candidate.evidenceTypes,
            contradictingSignals =
                emptyList()
        )
    }

    /*
     * Approximates a category's contradiction strength
     * from its own contradicting count relative to its
     * supporting count.
     */
    private fun contradictionStrengthOf(
        candidate: CandidateCategoryScore
    ): Double {
        val total =
            candidate.supportingCount +
                    candidate.contradictingCount
        if (total <= 0) {
            return 0.0
        }
        return clamp01(
            candidate.contradictingCount.toDouble() /
                    total.toDouble()
        )
    }

    // ========================================
    // CONTEXT EVALUATION
    // ========================================

    /*
     * Evaluates the overall evidence quality as a value
     * in [0, 1] from coverage, source diversity, and
     * usable evidence.
     */
    private fun evaluateEvidenceQuality(
        snapshot: ItemEvidenceSnapshot
    ): Double {
        // Coverage component.
        val coverageComponent =
            snapshot.coverageRatio.coerceIn(0.0, 1.0)

        // Diversity component.
        val diversityComponent = clamp01(
            snapshot.evidenceTypesPresent.size / 5.0
        )

        // Volume component (usable points).
        val volumeComponent = clamp01(
            snapshot.usableEvidencePoints / 10.0
        )

        // Weighted average.
        return clamp01(
            coverageComponent * 0.5 +
                    diversityComponent * 0.3 +
                    volumeComponent * 0.2
        )
    }

    private fun evaluateContradiction(
        snapshot: ItemEvidenceSnapshot
    ): Double {
        return when (snapshot.conflictLevel) {
            ConflictLevel.NONE -> 0.1
            ConflictLevel.LOW -> 0.35
            ConflictLevel.MEDIUM -> 0.6
            ConflictLevel.HIGH -> 0.9
        }
    }

    // ========================================
    // DECISION STATE CLASSIFICATION
    // ========================================

    /*
     * Classifies the decision state using explicit,
     * versioned thresholds.
     *
     * Order of evaluation matters (first matching rule
     * wins, and later rules override only when strictly
     * more severe).
     */
    private fun classifyDecisionState(
        snapshot: ItemEvidenceSnapshot,
        top: CategorySupport,
        secondScore: Double,
        marginRatio: Double,
        coverageRatio: Double,
        sourceDiversity: Int,
        config: ItemDecisionConfig
    ): DecisionState {

        // Tier 1: Insufficient evidence takes priority.
        //
        // Coverage below the absolute floor means we
        // cannot honestly decide anything. This is the
        // honest abstention path (short exposure, privacy
        // masking, heavy gaps).
        if (coverageRatio <
            config.minCoverageForAnyDecision) {
            return DecisionState.INSUFFICIENT_EVIDENCE
        }

        // An unreasonable / non-finite support score is
        // not valid evidence. Do not translate an
        // out-of-range or NaN score into a confident
        // category. This is UNKNOWN (evidence is present
        // but cannot be mapped to a supported decision).
        if (top.totalSupport.isNaN() ||
            top.totalSupport > VALID_SUPPORT_UPPER_BOUND ||
            top.totalSupport < 0.0) {
            return DecisionState.UNKNOWN
        }

        // No usable candidate after coverage -> UNKNOWN.
        // Distinct from INSUFFICIENT_EVIDENCE: evidence is
        // present but unmappable.
        if (top.totalSupport <= 0.0) {
            return DecisionState.UNKNOWN
        }

        // Tier 2: UNKNOWN when the leading support is too
        // weak to map to any supported category, even
        // though there may be coverage.
        if (top.totalSupport < config.minSupportForDecided) {
            return DecisionState.UNKNOWN
        }

        // Tier 3: Ambiguity.
        //
        // Two or more candidates are close on a relative
        // margin basis, OR conflict is high enough to make
        // a clean decision defensible only weakly.
        val ambiguousByMargin =
            marginRatio < config.minMarginRatioForDecided

        val ambiguousByConflict =
            sourceDiversity >=
                config.sourcesForModerateUncertainty &&
                snapshot.conflictLevel ==
                    ConflictLevel.HIGH

        if (ambiguousByMargin || ambiguousByConflict) {
            return DecisionState.AMBIGUOUS
        }

        // Tier 4: Coverage / diversity too low for a
        // confident DECIDED state.
        if (coverageRatio < config.minCoverageForDecided ||
            top.sourceCount < config.minSourcesForDecided ||
            snapshot.uniqueEvidenceIdentities <
                config.minUniqueIdentitiesForDecided) {
            return DecisionState.AMBIGUOUS
        }

        // Tier 5: A clear leader with sufficient
        // coverage, diversity, and no decisive conflict.
        return DecisionState.DECIDED
    }

    // ========================================
    // UNCERTAINTY CLASSIFICATION
    // ========================================

    /*
     * Classifies the uncertainty level from interpretable
     * components.
     */
    private fun classifyUncertainty(
        state: DecisionState,
        coverageRatio: Double,
        coverageState: CoverageState,
        primarySourceCount: Int,
        contradictionStrength: Double,
        marginRatio: Double,
        quality: Double,
        representativeFrameOutlier: Boolean?,
        internalTransitionDetected: Boolean,
        config: ItemDecisionConfig
    ): UncertaintyState {

        // Abstained states are inherently high/ unresolved
        // uncertainty.
        return when (state) {
            DecisionState.INSUFFICIENT_EVIDENCE ->
                UncertaintyState.UNRESOLVED
            DecisionState.UNKNOWN ->
                UncertaintyState.HIGH
            DecisionState.AMBIGUOUS ->
                UncertaintyState.HIGH
            DecisionState.DECIDED -> {
                classifyDecidedUncertainty(
                    coverageRatio = coverageRatio,
                    coverageState = coverageState,
                    primarySourceCount = primarySourceCount,
                    contradictionStrength =
                        contradictionStrength,
                    marginRatio = marginRatio,
                    quality = quality,
                    representativeFrameOutlier =
                        representativeFrameOutlier,
                    internalTransitionDetected =
                        internalTransitionDetected,
                    config = config
                )
            }
        }
    }

    private fun classifyDecidedUncertainty(
        coverageRatio: Double,
        coverageState: CoverageState,
        primarySourceCount: Int,
        contradictionStrength: Double,
        marginRatio: Double,
        quality: Double,
        representativeFrameOutlier: Boolean?,
        internalTransitionDetected: Boolean,
        config: ItemDecisionConfig
    ): UncertaintyState {

        // HIGH: any strong stressor.
        if (
            coverageRatio < config.coverageForHighUncertainty ||
            quality < config.minQualityForLowUncertainty ||
            contradictionStrength >= config.conflictThreshold ||
            coverageState == CoverageState.LOW_COVERAGE
        ) {
            return UncertaintyState.HIGH
        }

        // MODERATE: a mild stressor, partial coverage, a
        // representative-frame outlier, an internal
        // transition, or a thin margin.
        val moderateCoverage =
            coverageState ==
                CoverageState.PARTIAL_COVERAGE
        val thinMargin =
            marginRatio <
                MODERATE_MARGIN_RATIO_THRESHOLD
        val singleSource =
            primarySourceCount <=
                config.sourcesForModerateUncertainty

        if (
            moderateCoverage ||
            thinMargin ||
            singleSource ||
            contradictionStrength >=
                config.ambiguityConflictThreshold ||
            representativeFrameOutlier == true ||
            internalTransitionDetected
        ) {
            return UncertaintyState.MODERATE
        }

        return UncertaintyState.LOW
    }

    /*
     * Margin ratio below which a decided item is treated
     * as having a thin, moderate-certainty lead.
     */
    private const val MODERATE_MARGIN_RATIO_THRESHOLD = 0.5

    // ========================================
    // HELPERS
    // ========================================

    /*
     * Summarizes evidence maps into readable lists.
     */
    private fun summarizeEvidence(
        evidence: Map<String, List<*>>
    ): Map<String, List<String>> {
        return evidence.mapValues { (_, refs) ->
            refs.map { ref ->
                val r = ref as? EvidenceReference
                if (r != null) {
                    summarizeReference(r)
                } else {
                    ref.toString()
                }
            }
        }
    }

    private fun summarizeReference(
        r: EvidenceReference
    ): String {
        val base = "${r.evidenceType}@${r.relativeTimeMs}ms"
        val value = r.valueSummary.take(40)
        return if (value.isNotBlank()) {
            "$base[${r.quality}: $value]"
        } else {
            "$base[${r.quality}]"
        }
    }

    private fun qualityLabel(quality: Double): String {
        return when {
            quality >= 0.7 -> "HIGH"
            quality >= 0.4 -> "MEDIUM"
            quality > 0.0 -> "LOW"
            else -> "NONE"
        }
    }

    private fun clamp01(value: Double): Double {
        return value.coerceIn(0.0, 1.0)
    }
}
