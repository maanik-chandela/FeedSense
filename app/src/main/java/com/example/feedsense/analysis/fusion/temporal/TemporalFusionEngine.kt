package com.example.feedsense.analysis.fusion.temporal

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.analysis.fusion.CandidateGenerator
import com.example.feedsense.analysis.fusion.EvidenceContext
import com.example.feedsense.analysis.fusion.EvidenceFamily
import com.example.feedsense.analysis.fusion.EvidenceNormalizer
import com.example.feedsense.analysis.fusion.EvidenceRecord
import com.example.feedsense.analysis.fusion.EvidenceStrength
import com.example.feedsense.analysis.fusion.FusionConfig
import com.example.feedsense.analysis.fusion.FusionEngine
import com.example.feedsense.analysis.fusion.FusionPrediction
import com.example.feedsense.analysis.fusion.FrameSignals
import com.example.feedsense.analysis.fusion.TextEvidenceProvider

// --------------------------------
// TEMPORAL FUSION ENGINE (Milestone 8B-2)
// --------------------------------
//
// Pure, deterministic orchestrator that builds on 8B-1's
// FusionEngine to add temporal reasoning across frames.
//
// The engine follows the deterministic fusion order (8B-2
// section 39):
//
//   1. collect frame evidence
//   2. normalize
//   3. assess frame quality
//   4. deduplicate
//   5. build timeline
//   6. aggregate evidence
//   7. detect transitions
//   8. evaluate temporal consistency
//   9. generate candidates
//  10. fuse candidates
//  11. calculate uncertainty
//  12. validate representative frame
//  13. produce final prediction
//  14. produce diagnostic trace
//
// FeedItem remains the bounded temporal reasoning unit (8B-2
// section 7). The engine does not process the entire research
// session.
//
// This is NOT a trained temporal neural network (8B-2 section
// 93). It is deterministic temporal evidence reasoning.

class TemporalFusionEngine(
    private val temporalConfig: TemporalConfig = TemporalConfig.DEFAULT,
    private val fusionConfig: FusionConfig = FusionConfig.DEFAULT,
    private val fingerprintProvider: VisualFingerprintProvider =
        UnavailableFingerprintProvider()
) {

    private val baseEngine = FusionEngine(fusionConfig)

    /**
     * Runs the full temporal fusion over a multi-frame context.
     * This is the primary entry point for temporal reasoning.
     */
    fun predict(context: EvidenceContext): TemporalFusionPrediction {
        // ---- Step 1-4: Use base engine for per-frame evidence --
        // The base 8B-1 engine handles evidence extraction,
        // normalization, quality assessment, and deduplication.
        val basePrediction = baseEngine.predict(context)

        // ---- Step 5: Build deterministic timeline --------------
        val timeline = FrameTimeline.build(
            context.frames, temporalConfig
        )

        // ---- Step 6: Aggregate evidence across time ------------
        val categoryFrames = assignCategoriesPerFrame(context, timeline)
        val temporalSupports = TemporalPersistence.calculateAll(
            timeline, categoryFrames, temporalConfig
        )

        // ---- Step 7: Detect transitions ------------------------
        val transitionResult = TemporalTransitionDetector.detect(
            timeline, categoryFrames, temporalConfig
        )

        // ---- Step 8: Evaluate temporal consistency -------------
        val leadingCategory = temporalSupports.firstOrNull()?.category
            ?: basePrediction.primaryCategory

        val consistency = if (leadingCategory != null) {
            TemporalConsistencyAssessor.assess(
                timeline, categoryFrames, leadingCategory,
                temporalConfig
            )
        } else {
            TemporalConsistency.NONE
        }

        val stability = if (leadingCategory != null) {
            val trajectory = temporalSupports
                .firstOrNull { it.category == leadingCategory }
            if (trajectory != null) {
                // Build a CategoryTrajectory for stability
                // assessment.
                val catTraj = CategoryTrajectory(
                    category = leadingCategory,
                    supportDurationMs = trajectory.totalDurationMs,
                    informativeFrameCount =
                        trajectory.informativeFrameCount,
                    continuousSupport = !trajectory.hasGaps,
                    maxConsecutiveFrames =
                        trajectory.totalFrameCount,
                    averageScore = 0.0,
                    scoreTrajectory = emptyList()
                )
                TemporalConsistencyAssessor.assessStability(
                    catTraj, timeline.totalDurationMs,
                    temporalConfig
                )
            } else {
                CategoryStability.UNKNOWN
            }
        } else {
            CategoryStability.UNKNOWN
        }

        val conflictLevel = TemporalConsistencyAssessor
            .assessConflictLevel(
                timeline, categoryFrames, temporalConfig
            )

        // ---- Step 9-10: Fuse temporal candidates ---------------
        val temporalResult = fuseTemporalCandidates(
            context, timeline, categoryFrames, temporalSupports,
            basePrediction
        )

        // ---- Step 11: Calculate uncertainty --------------------
        val uncertaintyResult = calculateTemporalUncertainty(
            context, timeline, categoryFrames, temporalResult,
            consistency, conflictLevel, transitionResult,
            basePrediction
        )

        // ---- Step 12: Validate representative frame ------------
        val repFrameValidation = validateRepresentativeFrame(
            context, temporalResult.category
        )

        // ---- Step 13: Produce final prediction -----------------
        val prediction = buildPrediction(
            temporalResult, uncertaintyResult, consistency,
            stability, conflictLevel, transitionResult,
            repFrameValidation, timeline, context
        )

        return prediction
    }

    /**
     * Runs temporal fusion on a single-frame context. Gracefully
     * degrades (8B-2 section 33): classification is possible but
     * confidence must respect limited evidence.
     */
    fun predictSingle(frame: FrameSignals): TemporalFusionPrediction {
        val context = EvidenceContext.single(frame)
        return predict(context)
    }

    /**
     * Incrementally updates temporal state as new frames arrive
     * (8B-2 section 37). Returns an updated prediction.
     */
    fun predictIncremental(
        previousFrames: List<FrameSignals>,
        newFrame: FrameSignals,
        previousPrediction: TemporalFusionPrediction? = null
    ): TemporalFusionPrediction {
        // Bounded memory: only keep up to
        // maxInMemoryFrameSummaries frames.
        val allFrames = (previousFrames + newFrame)
            .takeLast(temporalConfig.maxInMemoryFrameSummaries)
        return predict(EvidenceContext(allFrames, fusionConfig))
    }

    // ========================================
    // INTERNAL PIPELINE
    // ========================================

    /**
     * Assigns a category to each frame based on OCR text
     * analysis. This is the per-frame classification that feeds
     * into temporal reasoning.
     */
    private fun assignCategoriesPerFrame(
        context: EvidenceContext,
        timeline: FrameTimeline
    ): Map<Int, String> {
        val result = mutableMapOf<Int, String>()

        for (entry in timeline.entries) {
            val frame = entry.frame
            val text = frame.ocrText

            if (!text.isNullOrBlank() && text.length >=
                temporalConfig.minInformativeDurationSeconds.toInt()
            ) {
                val candidates = CandidateGenerator
                    .candidatesForText(text, "temporal", frame.frameId)
                val leader = candidates
                    .maxByOrNull { it.vote }
                if (leader != null) {
                    val normalized = CategoryCatalog
                        .normalize(leader.category)
                    if (normalized != null) {
                        result[entry.index] = normalized
                        continue
                    }
                }
            }

            // Fall back to legacy classifier if available.
            frame.existingCategory?.let { cat ->
                CategoryCatalog.normalize(cat)?.let {
                    result[entry.index] = it
                }
            }
        }

        return result
    }

    /**
     * Fuses temporal candidate scores using persistence,
     * continuity and evidence quality (8B-2 section 47).
     */
    private fun fuseTemporalCandidates(
        context: EvidenceContext,
        timeline: FrameTimeline,
        categoryFrames: Map<Int, String>,
        temporalSupports: List<TemporalSupport>,
        basePrediction: FusionPrediction
    ): TemporalFusionResult {
        if (temporalSupports.isEmpty()) {
            return TemporalFusionResult(
                category = basePrediction.primaryCategory,
                score = basePrediction.confidence,
                secondaryCategories =
                    basePrediction.secondaryCategories,
                allScores = emptyMap(),
                mixedContent = false
            )
        }

        val totalDuration = timeline.totalDurationMs
            .coerceAtLeast(1L)

        // Score each category by combining temporal support
        // quality with the base prediction's candidate scores.
        // Temporal dominance is measured by informative frame
        // count AND continuous duration, so a single transient
        // overlay frame cannot outvote multiple stable frames of
        // the true content (8B-2 section 46).
        val scores = mutableMapOf<String, Double>()
        val baseScores = basePrediction.trace.candidates
            .associate { it.category to it.score }

        val totalInformative = timeline.informativeFrameCount
            .coerceAtLeast(1)

        // Deduplicated frame counts: identical OCR across many
        // frames is redundant evidence and must not linearly
        // inflate the temporal share (8B-2 section 21 + 45).
        val uniqueOcrByFrame = mutableMapOf<Int, String>()
        timeline.entries.forEach { e ->
            val t = e.frame.ocrText?.trim().orEmpty()
            if (t.isNotEmpty()) {
                uniqueOcrByFrame[e.index] = t
            }
        }
        val distinctOcrCount = uniqueOcrByFrame.values
            .distinct()
            .size
            .coerceAtLeast(1)
        val redundancyFactor = (totalInformative.toDouble() /
            distinctOcrCount).coerceIn(1.0, 5.0)

        for (support in temporalSupports) {
            val temporalScore = support.supportQuality
            val baseScore = baseScores[support.category] ?: 0.0

            // Frame-share bonus: a category observed in a larger
            // fraction of informative frames is more likely the
            // true, stable content. This is a temporal dominance
            // signal, not mere frame counting (8B-2 section 45).
            // The share is dampened by OCR redundancy so
            // repeating identical frames does not inflate it.
            val frameShare = (support.totalFrameCount.toDouble() /
                totalInformative) / redundancyFactor

            // Combine:
            //   50% temporal support quality (time-aware)
            //   30% frame share across informative frames
            //   20% base candidate score from 8B-1 fusion
            // Temporal evidence dominates because the whole point
            // of 8B-2 is that repeated, persistent evidence across
            // time is stronger than the aggregated single-frame
            // view (8B-2 sections 46/47).
            val combined = 0.5 * temporalScore +
                0.3 * frameShare +
                0.2 * baseScore
            scores[support.category] = combined
        }

        // If the base prediction suggests a category with no
        // temporal support, keep its base score as a floor.
        basePrediction.primaryCategory?.let { baseCat ->
            if (CategoryCatalog.normalize(baseCat) != null &&
                scores.keys.none {
                    CategoryCatalog.normalize(it) ==
                        CategoryCatalog.normalize(baseCat)
                }
            ) {
                val norm = CategoryCatalog.normalize(baseCat)
                scores[norm!!] =
                    (scores[norm] ?: 0.0) + basePrediction.confidence * 0.3
            }
        }

        // Normalize scores to sum to 1.0.
        val totalScore = scores.values.sum().coerceAtLeast(1e-9)
        val normalized = scores.mapValues { it.value / totalScore }
            .toSortedMap()

        val leader = normalized.maxByOrNull { it.value }
        val primary = leader?.key
            ?: basePrediction.primaryCategory

        // Detect mixed content (8B-2 section 25).
        val sortedScores = normalized.entries
            .sortedByDescending { it.value }
        val secondaryCats = sortedScores
            .filter {
                it.key != primary &&
                    it.value >= temporalConfig.secondaryCategoryMinRatio
            }
            .map { it.key }
            .take(3)

        val mixedContent = secondaryCats.isNotEmpty()

        return TemporalFusionResult(
            category = primary,
            score = leader?.value ?: 0.0,
            secondaryCategories = secondaryCats,
            allScores = normalized,
            mixedContent = mixedContent
        )
    }

    /**
     * Calculates temporal uncertainty (8B-2 section 48-51).
     */
    private fun calculateTemporalUncertainty(
        context: EvidenceContext,
        timeline: FrameTimeline,
        categoryFrames: Map<Int, String>,
        temporalResult: TemporalFusionResult,
        consistency: TemporalConsistency,
        conflictLevel: TemporalConflictLevel,
        transitionResult: TransitionResult,
        basePrediction: FusionPrediction
    ): UncertaintyResult {
        val informativeCount = timeline.informativeFrameCount
        val totalFrames = timeline.frameCount

        // INSUFFICIENT_EVIDENCE: no useful temporal evidence.
        if (informativeCount == 0 || temporalResult.category == null) {
            return UncertaintyResult(
                uncertainty = "INSUFFICIENT_EVIDENCE",
                reason = "no_informative_frames",
                confidence = 0.0
            )
        }

        // INSUFFICIENT: too few frames for temporal reasoning.
        if (informativeCount < 2 &&
            timeline.totalDurationSeconds <
            temporalConfig.veryShortContentThresholdSeconds
        ) {
            // Still classify, but with reduced confidence.
            val confidence = (basePrediction.confidence * 0.7)
                .coerceIn(0.0, 1.0)
            return UncertaintyResult(
                uncertainty = if (confidence >= temporalConfig.bandMedium)
                    "LOW_CONFIDENCE" else "INSUFFICIENT_EVIDENCE",
                reason = "very_short_content",
                confidence = confidence
            )
        }

        // CONFLICTING_EVIDENCE (8B-2 section 51).
        if (conflictLevel == TemporalConflictLevel.HIGH) {
            return UncertaintyResult(
                uncertainty = "CONFLICTING_EVIDENCE",
                reason = "high_temporal_conflict",
                confidence = temporalResult.score
            )
        }

        // CONFLICTING: sharp transition detected.
        if (transitionResult.transitionDetected &&
            consistency == TemporalConsistency.CONFLICTING
        ) {
            return UncertaintyResult(
                uncertainty = "CONFLICTING_EVIDENCE",
                reason = "content_transition_detected",
                confidence = temporalResult.score
            )
        }

        // CROSS-SOURCE CONFLICT: OCR-derived category and the
        // legacy 8B-1 classifier verdict disagree about the same
        // content (8B-2 section 43). A platform difference never
        // resolves a category conflict, so it is preserved.
        val ocrLeader = temporalResult.category
        val classifierLeaders = context.frames
            .mapNotNull { it.existingCategory }
            .mapNotNull(CategoryCatalog::normalize)
            .distinct()

        if (ocrLeader != null && classifierLeaders.isNotEmpty()) {
            val classifierAgrees = classifierLeaders.any {
                CategoryCatalog.domainOf(it) ==
                    CategoryCatalog.domainOf(ocrLeader)
            }
            if (!classifierAgrees) {
                return UncertaintyResult(
                    uncertainty = "CONFLICTING_EVIDENCE",
                    reason = "ocr_classifier_conflict",
                    confidence = temporalResult.score
                )
            }
        }

        // AMBIGUOUS (8B-2 section 50).
        val sortedScores = temporalResult.allScores.entries
            .sortedByDescending { it.value }
        if (sortedScores.size >= 2) {
            val leader = sortedScores[0].value
            val runnerUp = sortedScores[1].value
            val gap = if (leader > 0) {
                (leader - runnerUp) / leader
            } else {
                1.0
            }
            if (gap <= temporalConfig.ambiguityGapRatio) {
                return UncertaintyResult(
                    uncertainty = "AMBIGUOUS",
                    reason = "close_candidates",
                    confidence = temporalResult.score
                )
            }
        }

        // LOW_CONFIDENCE.
        if (temporalResult.score < temporalConfig.bandLow) {
            return UncertaintyResult(
                uncertainty = "LOW_CONFIDENCE",
                reason = "low_temporal_support",
                confidence = temporalResult.score
            )
        }

        // CONFIDENT.
        return UncertaintyResult(
            uncertainty = "CONFIDENT",
            reason = null,
            confidence = temporalResult.score
        )
    }

    /**
     * Validates the representative frame against temporal
     * evidence (8B-2 section 28).
     */
    private fun validateRepresentativeFrame(
        context: EvidenceContext,
        temporalCategory: String?
    ): RepresentativeFrameResult {
        if (temporalCategory == null) {
            return RepresentativeFrameResult(
                agreement = false,
                outlier = true,
                reason = "no_temporal_category"
            )
        }

        // Check if any frame in the context was classified to
        // a different category by the base engine.
        val baseContext = EvidenceContext(context.frames, fusionConfig)
        val basePrediction = baseEngine.predict(baseContext)
        val baseCategory = basePrediction.primaryCategory

        if (baseCategory == null) {
            return RepresentativeFrameResult(
                agreement = false,
                outlier = true,
                reason = "base_prediction_null"
            )
        }

        // Compare base (single-frame-like) prediction with
        // temporal prediction.
        val sameCategory = CategoryCatalog.normalize(baseCategory) ==
            CategoryCatalog.normalize(temporalCategory)

        val domainAgree = CategoryCatalog.domainOf(baseCategory) ==
            CategoryCatalog.domainOf(temporalCategory)

        return RepresentativeFrameResult(
            agreement = sameCategory || domainAgree,
            outlier = !domainAgree,
            reason = if (sameCategory) "category_match" else
                if (domainAgree) "domain_match" else "outlier"
        )
    }

    /**
     * Builds the final temporal prediction with all components.
     */
    private fun buildPrediction(
        temporalResult: TemporalFusionResult,
        uncertaintyResult: UncertaintyResult,
        consistency: TemporalConsistency,
        stability: CategoryStability,
        conflictLevel: TemporalConflictLevel,
        transitionResult: TransitionResult,
        repFrameResult: RepresentativeFrameResult,
        timeline: FrameTimeline,
        context: EvidenceContext
    ): TemporalFusionPrediction {
        // OCR summary.
        val ocrSummary = TemporalOcrAggregator.aggregate(
            timeline.entries, temporalConfig
        )

        // OCR consistency assessment.
        val ocrConsistency = assessOcrConsistency(
            ocrSummary, consistency
        )

        // Build trajectory summaries.
        val trajectories = buildTrajectorySummaries(
            timeline, temporalResult
        )

        // Build trace.
        val trace = TemporalFusionTrace(
            framesConsidered = timeline.frameCount,
            informativeFrames = timeline.informativeFrameCount,
            temporalDurationSeconds = timeline.totalDurationSeconds,
            dominantCategory = temporalResult.category,
            temporalConsistency = consistency.label,
            categoryStability = stability.label,
            categoryConflict = conflictLevel !=
                TemporalConflictLevel.NONE,
            temporalConflictLevel = conflictLevel.label,
            transitionDetected = transitionResult.transitionDetected,
            transitionCount = transitionResult.transitions.size,
            segmentBoundaries = transitionResult.segmentBoundaries,
            representativeFrameAgreement = repFrameResult.agreement,
            representativeFrameOutlier = repFrameResult.outlier,
            ocrConsistency = ocrConsistency,
            ocrDeduplicatedCount = ocrSummary.deduplicatedCount,
            ocrTotalCount = ocrSummary.totalCount,
            ocrEvolutionChains = ocrSummary.evolutionChains.size,
            visualConsistency =
                TemporalFusionTrace.VISUAL_UNAVAILABLE,
            uncertainty = uncertaintyResult.uncertainty,
            uncertaintyReason = uncertaintyResult.reason,
            modelVersion = temporalConfig.modelVersion,
            informationQuality = buildInfoQualityNote(timeline),
            mixedContent = temporalResult.mixedContent,
            secondaryCategories = temporalResult.secondaryCategories
        )

        // Platform from context.
        val platform = context.frames
            .mapNotNull { it.platform }
            .distinct()
            .firstOrNull()

        // Interaction from base prediction.
        val basePred = baseEngine.predict(
            EvidenceContext(context.frames, fusionConfig)
        )

        // Confidence band.
        val confidence = uncertaintyResult.confidence
        val band = when {
            confidence >= temporalConfig.bandHigh -> "HIGH"
            confidence >= temporalConfig.bandMedium -> "MEDIUM"
            confidence >= temporalConfig.bandLow -> "LOW"
            else -> "UNKNOWN"
        }

        return TemporalFusionPrediction(
            primaryCategory = temporalResult.category,
            secondaryCategories = temporalResult.secondaryCategories,
            confidence = confidence,
            uncertainty = uncertaintyResult.uncertainty,
            topic = basePred.topic,
            tone = basePred.tone,
            contentType = basePred.contentType,
            platform = platform,
            interactionState = basePred.interactionState,
            temporalConsistency = consistency,
            categoryStability = stability,
            temporalConflictLevel = conflictLevel,
            mixedContentIndicator = temporalResult.mixedContent,
            transitionDetected = transitionResult.transitionDetected,
            transitionCount = transitionResult.transitions.size,
            representativeFrameOutlier = repFrameResult.outlier,
            categoryTrajectories = trajectories,
            trace = trace,
            modelVersion = temporalConfig.modelVersion,
            configVersion = temporalConfig.configVersion,
            modelState = "EXPERIMENTAL"
        )
    }

    private fun assessOcrConsistency(
        ocrSummary: TemporalOcrSummary,
        overallConsistency: TemporalConsistency
    ): String {
        if (ocrSummary.totalCount == 0) {
            return TemporalFusionTrace.TEMPORAL_INSUFFICIENT
        }
        if (ocrSummary.deduplicatedCount <= 1) {
            return TemporalFusionTrace.TEMPORAL_CONSISTENT
        }
        return when (overallConsistency) {
            TemporalConsistency.STRONG ->
                TemporalFusionTrace.TEMPORAL_CONSISTENT
            TemporalConsistency.MODERATE ->
                TemporalFusionTrace.TEMPORAL_CONSISTENT
            TemporalConsistency.CONFLICTING ->
                TemporalFusionTrace.TEMPORAL_CONFLICT
            else ->
                TemporalFusionTrace.TEMPORAL_INSUFFICIENT
        }
    }

    private fun buildTrajectorySummaries(
        timeline: FrameTimeline,
        temporalResult: TemporalFusionResult
    ): Map<String, CategoryTrajectorySummary> {
        return temporalResult.allScores.mapValues { (cat, score) ->
            CategoryTrajectorySummary(
                category = cat,
                supportDurationSeconds =
                    timeline.totalDurationSeconds * score,
                frameCount = (timeline.frameCount * score).toInt(),
                persistenceLevel = when {
                    score >= 0.8 -> "DOMINANT"
                    score >= 0.5 -> "SUSTAINED"
                    score >= 0.2 -> "MODERATE"
                    else -> "BRIEF"
                },
                averageScore = score,
                stable = score >= 0.7
            )
        }
    }

    private fun buildInfoQualityNote(timeline: FrameTimeline): String {
        return when {
            timeline.frameCount == 0 -> "NO_FRAMES"
            timeline.informativeFrameCount == 0 ->
                "NO_INFORMATIVE_FRAMES"
            timeline.informativeFrameCount < timeline.frameCount ->
                "PARTIAL_INFORMATIVE"
            else -> "ALL_INFORMATIVE"
        }
    }

    // Internal result types.
    private data class TemporalFusionResult(
        val category: String?,
        val score: Double,
        val secondaryCategories: List<String>,
        val allScores: Map<String, Double>,
        val mixedContent: Boolean
    )

    private data class UncertaintyResult(
        val uncertainty: String,
        val reason: String?,
        val confidence: Double
    )

    private data class RepresentativeFrameResult(
        val agreement: Boolean,
        val outlier: Boolean,
        val reason: String
    )
}
