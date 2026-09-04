package com.example.feedsense.analysis.evidence.temporal

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.analysis.evidence.Evidence
import com.example.feedsense.analysis.evidence.EvidenceQuality
import com.example.feedsense.analysis.evidence.weight

/*
 * Milestone 8B-6.
 *
 * Temporal evidence fusion engine.
 *
 * Deterministic, structured-evidence-first engine that
 * aggregates evidence across a FeedItem's temporal window
 * to produce item-level predictions.
 *
 * Architecture:
 *   Evidence[] → TemporalEvidencePoint[]
 *             → ItemEvidenceTimeline
 *             → temporal fusion
 *             → ItemEvidenceSnapshot
 *
 * Design principles:
 *   - Deterministic: same evidence → same result
 *   - Honest: missing data remains missing
 *   - No semantic interpolation across gaps
 *   - No fake probability claims
 *   - No independence inflation
 *   - Structured evidence only (no raw images)
 *   - Bounded memory: one item at a time
 *
 * Research principle:
 *   The content item, not an individual screenshot, is
 *   the fundamental unit of semantic interpretation.
 *
 * Limitations:
 *   - Weights are heuristic, not learned
 *   - No neural fusion model
 *   - No automatic threshold tuning
 *   - No self-learning from mistakes
 *   - No cloud processing
 *   - Coverage ≠ prediction quality
 */
class TemporalEvidenceFusionEngine(
    private val config:
        TemporalEvidenceFusionConfig =
        TemporalEvidenceFusionConfig.DEFAULT
) {

    /*
     * Fuses temporal evidence for a single FeedItem.
     *
     * This is the primary entry point for 8B-6 temporal
     * fusion. It takes evidence that has been extracted
     * by 8B-5 extractors and produces an item-level
     * evidence snapshot.
     *
     * @param sessionId Research session identifier.
     * @param feedItemId Item identifier.
     * @param itemStartTimeMs Item start in epoch ms.
     * @param itemDurationMs Item duration in ms.
     * @param evidence Evidence extracted from frames.
     * @param representativeFrameCategory Category from
     *   the representative frame (may be null).
     * @param representativeFrameAgreement Whether the
     *   representative frame agrees with temporal
     *   evidence (may be null).
     * @return ItemEvidenceSnapshot with complete temporal
     *   reasoning result.
     */
    fun fuse(
        sessionId: String,
        feedItemId: String?,
        itemStartTimeMs: Long,
        itemDurationMs: Long,
        evidence: List<Evidence>,
        representativeFrameCategory: String? = null,
        representativeFrameAgreement: Boolean? = null
    ): ItemEvidenceSnapshot {

        val startTimeNs = System.nanoTime()

        // ---- Step 1: Build timeline ----------------
        val timeline = ItemEvidenceTimeline.build(
            sessionId = sessionId,
            feedItemId = feedItemId,
            itemStartTimeMs = itemStartTimeMs,
            itemDurationMs = itemDurationMs,
            evidence = evidence
        )

        // ---- Step 2: Compute candidate scores -------
        val candidates =
            computeCandidateScores(timeline)

        // ---- Step 3: Build supporting/contradicting --
        val supporting =
            buildSupportingEvidenceMap(
                timeline, candidates
            )
        val contradicting =
            buildContradictingEvidenceMap(
                timeline, candidates
            )

        // ---- Step 4: Detect conflicts ---------------
        val conflictResult =
            detectConflicts(candidates)

        // ---- Step 5: Compute ambiguity --------------
        val ambiguityResult =
            computeAmbiguity(candidates)

        // ---- Step 6: Detect transitions -------------
        val transitionResult =
            detectTransitions(timeline)

        // ---- Step 7: Assess limitations -------------
        val limitations =
            assessLimitations(
                timeline, candidates,
                ambiguityResult, conflictResult
            )

        // ---- Step 8: Determine insufficient evidence -
        val insufficientResult =
            assessInsufficientEvidence(
                timeline, candidates
            )

        // ---- Step 9: Build snapshot -----------------
        val elapsedNs =
            System.nanoTime() - startTimeNs

        return buildSnapshot(
            timeline = timeline,
            candidates = candidates,
            supporting = supporting,
            contradicting = contradicting,
            conflictResult = conflictResult,
            ambiguityResult = ambiguityResult,
            transitionResult = transitionResult,
            limitations = limitations,
            insufficientResult = insufficientResult,
            representativeFrameCategory =
                representativeFrameCategory,
            representativeFrameAgreement =
                representativeFrameAgreement,
            fusionDurationNs = elapsedNs
        )
    }

    // ========================================
    // CANDIDATE SCORING
    // ========================================

    /*
     * Computes support scores for each candidate
     * category using temporal evidence.
     *
     * The score combines:
     *   - evidence quality
     *   - temporal persistence
     *   - coverage within the item
     *   - source diversity
     *   - temporal recency
     *
     * Persistence is tracked by unique evidence identity
     * hash, NOT by raw frame count. This prevents
     * inflation from identical OCR across many frames.
     */
    private fun computeCandidateScores(
        timeline: ItemEvidenceTimeline
    ): List<CandidateCategoryScore> {

        if (timeline.points.isEmpty()) {
            return emptyList()
        }

        // Group points by category.
        val categoryPoints =
            mutableMapOf<String,
                    MutableList<TemporalEvidencePoint>>()

        for (point in timeline.points) {
            for (category in point.supportingCategories) {
                categoryPoints
                    .getOrPut(category) {
                        mutableListOf()
                    }
                    .add(point)
            }
        }

        // Compute raw support for each category.
        val rawScores =
            mutableMapOf<String, Double>()

        for ((category, points) in categoryPoints) {
            val score = computeCategorySupport(
                category, points, timeline
            )
            rawScores[category] = score
        }

        // Apply contradiction penalties.
        val penalizedScores =
            applyContradictionPenalties(
                rawScores, timeline
            )

        // Normalize to [0, 1].
        val maxScore =
            penalizedScores.values.maxOrNull() ?: 1.0
        val normalized =
            if (maxScore > 0) {
                penalizedScores.mapValues {
                    (it.value / maxScore)
                        .coerceIn(0.0, 1.0)
                }
            } else {
                penalizedScores
            }

        // Build candidate category scores.
        return normalized.entries
            .sortedByDescending { it.value }
            .map { (category, score) ->
                val points =
                    categoryPoints[category]
                        ?: emptyList()

                val contradictingCount =
                    timeline.points.count {
                        it.contradictingCategories
                            .contains(category)
                    }

                val types = points
                    .map { it.evidenceType.name }
                    .distinct()

                val persistenceLevel =
                    computePersistenceLevel(points)

                val firstSeen = points
                    .minOfOrNull {
                        it.relativeTimeMs
                    } ?: 0L
                val lastSeen = points
                    .maxOfOrNull {
                        it.relativeTimeMs
                    } ?: 0L

                CandidateCategoryScore(
                    category = category,
                    supportScore = score,
                    supportingCount = points.size,
                    contradictingCount =
                        contradictingCount,
                    evidenceTypes = types,
                    persistenceLevel =
                        persistenceLevel,
                    firstSeenRelativeMs = firstSeen,
                    lastSeenRelativeMs = lastSeen,
                    temporalSpreadMs =
                        lastSeen - firstSeen
                )
            }
    }

    /*
     * Computes the support score for a single category
     * from its evidence points.
     */
    private fun computeCategorySupport(
        category: String,
        points: List<TemporalEvidencePoint>,
        timeline: ItemEvidenceTimeline
    ): Double {
        if (points.isEmpty()) return 0.0

        // 1. Quality component: average quality weight.
        val qualityComponent =
            points.map { it.quality.weight }
                .average()

        // 2. Persistence component: unique identity
        //    count with dampening.
        val uniqueIdentities = points
            .map { it.evidenceIdentityHash }
            .distinct()
        val persistenceComponent =
            computePersistenceScore(
                uniqueIdentities.size, points.size
            )

        // 3. Coverage component: temporal spread
        //    relative to item duration.
        val coverageComponent =
            if (timeline.itemDurationMs > 0) {
                val spread = if (points.size >= 2) {
                    points.maxOf { it.relativeTimeMs } -
                            points.minOf { it.relativeTimeMs }
                } else {
                    0L
                }
                (spread.toDouble() /
                        timeline.itemDurationMs)
                    .coerceIn(0.0, 1.0)
            } else {
                0.0
            }

        // 4. Diversity component: number of distinct
        //    evidence types supporting this category.
        val evidenceTypes = points
            .map { it.evidenceType }
            .distinct()
        val diversityComponent =
            (evidenceTypes.size.toDouble() /
                    EvidenceTypeCount)
                .coerceIn(0.0, 1.0)

        // 5. Recency component: how recently the
        //    category was seen relative to item end.
        val maxRelative = points
            .maxOfOrNull { it.relativeTimeMs } ?: 0L
        val recencyComponent = if (
            timeline.itemDurationMs > 0
        ) {
            (maxRelative.toDouble() /
                    timeline.itemDurationMs)
                .coerceIn(0.0, 1.0)
        } else {
            0.0
        }

        // Weighted combination.
        val raw = config.qualityWeight * qualityComponent +
                config.persistenceWeight *
                persistenceComponent +
                config.coverageWeight *
                coverageComponent +
                config.diversityWeight *
                diversityComponent +
                config.recencyWeight *
                recencyComponent

        return raw.coerceIn(0.0, 1.0)
    }

    /*
     * Computes persistence score from unique identities
     * and total count.
     *
     * Key: repeated identical evidence is persistent
     * but NOT fully independent. The dampening factor
     * ensures that 10 frames of identical OCR does not
     * score like 10 independent sources.
     */
    private fun computePersistenceScore(
        uniqueCount: Int,
        totalCount: Int
    ): Double {
        if (uniqueCount == 0) return 0.0

        // Base: unique identity count matters more than
        // total count.
        val uniqueComponent =
            (uniqueCount.toDouble() /
                    config.minUniqueIdentities)
                .coerceIn(0.0, 1.0)

        // Persistence boost: repeated evidence from the
        // same identity increases confidence, but with
        // diminishing returns.
        val redundancy =
            totalCount.toDouble() /
                    uniqueCount.toDouble()
        val persistenceBoost =
            1.0 + ((redundancy - 1.0) /
                    config.persistenceDampening)
                .coerceIn(
                    0.0,
                    config.maxPersistenceBoost - 1.0
                )

        return (uniqueComponent * persistenceBoost)
            .coerceIn(0.0, config.maxPersistenceBoost)
    }

    /*
     * Applies contradiction penalties to raw scores.
     */
    private fun applyContradictionPenalties(
        rawScores: Map<String, Double>,
        timeline: ItemEvidenceTimeline
    ): Map<String, Double> {

        val result =
            mutableMapOf<String, Double>()

        for ((category, score) in rawScores) {
            val contradictionCount =
                timeline.points.count {
                    it.contradictingCategories
                        .contains(category)
                }

            val penalty = if (contradictionCount > 0) {
                val penaltyFactor =
                    (contradictionCount.toDouble() /
                            timeline.points.size)
                        .coerceIn(0.0, 1.0)
                1.0 - (config.contradictionPenalty *
                        penaltyFactor)
            } else {
                1.0
            }

            result[category] =
                score * penalty.coerceIn(0.0, 1.0)
        }

        return result
    }

    /*
     * Computes a human-readable persistence level.
     */
    private fun computePersistenceLevel(
        points: List<TemporalEvidencePoint>
    ): String {
        val uniqueCount = points
            .map { it.evidenceIdentityHash }
            .distinct()
            .size

        val spread = if (points.size >= 2) {
            points.maxOf { it.relativeTimeMs } -
                    points.minOf { it.relativeTimeMs }
        } else {
            0L
        }

        return when {
            uniqueCount == 0 -> "NONE"
            uniqueCount >= 4 && spread >= 5000L ->
                "DOMINANT"
            uniqueCount >= 3 && spread >= 3000L ->
                "SUSTAINED"
            uniqueCount >= 2 || spread >= 1000L ->
                "MODERATE"
            else -> "BRIEF"
        }
    }

    // ========================================
    // SUPPORTING / CONTRADICTING EVIDENCE
    // ========================================

    private fun buildSupportingEvidenceMap(
        timeline: ItemEvidenceTimeline,
        candidates: List<CandidateCategoryScore>
    ): Map<String, List<EvidenceReference>> {

        val result =
            mutableMapOf<String,
                    List<EvidenceReference>>()

        for (candidate in candidates) {
            val category = candidate.category
            val refs = timeline.points
                .filter {
                    it.supportingCategories
                        .contains(category)
                }
                .map { pointToReference(it) }
            result[category] = refs
        }

        return result
    }

    private fun buildContradictingEvidenceMap(
        timeline: ItemEvidenceTimeline,
        candidates: List<CandidateCategoryScore>
    ): Map<String, List<EvidenceReference>> {

        val result =
            mutableMapOf<String,
                    List<EvidenceReference>>()

        for (candidate in candidates) {
            val category = candidate.category
            val refs = timeline.points
                .filter {
                    it.contradictingCategories
                        .contains(category)
                }
                .map { pointToReference(it) }
            result[category] = refs
        }

        return result
    }

    private fun pointToReference(
        point: TemporalEvidencePoint
    ): EvidenceReference {
        return EvidenceReference(
            pointId = point.pointId,
            evidenceType = point.evidenceType.name,
            extractorName = point.extractorName,
            quality = point.quality.name,
            valueSummary = point.valueSummary,
            relativeTimeMs = point.relativeTimeMs,
            frameIndex = point.frameIndex
        )
    }

    // ========================================
    // CONFLICT DETECTION
    // ========================================

    private fun detectConflicts(
        candidates: List<CandidateCategoryScore>
    ): ConflictResult {

        if (candidates.isEmpty()) {
            return ConflictResult(
                level = ConflictLevel.NONE,
                description = null,
                contradictingCount = 0
            )
        }

        val leader = candidates[0]
        val contradictingTotal = candidates
            .sumOf { it.contradictingCount }

        if (contradictingTotal == 0) {
            return ConflictResult(
                level = ConflictLevel.NONE,
                description = null,
                contradictingCount = 0
            )
        }

        // Compute the ratio of the leading category's
        // contradicting evidence to the total evidence.
        val leaderContradictions =
            leader.contradictingCount
        val totalSupport =
            candidates.sumOf { it.supportingCount }

        val contradictionRatio = if (totalSupport > 0) {
            leaderContradictions.toDouble() /
                    totalSupport.toDouble()
        } else {
            0.0
        }

        val level = when {
            contradictionRatio >=
                    config.conflictHighRatio ->
                ConflictLevel.HIGH
            contradictionRatio >=
                    config.conflictMediumRatio ->
                ConflictLevel.MEDIUM
            contradictingTotal > 0 ->
                ConflictLevel.LOW
            else -> ConflictLevel.NONE
        }

        val description = when (level) {
            ConflictLevel.NONE -> null
            ConflictLevel.LOW ->
                "${leader.category} has " +
                        "$leaderContradictions " +
                        "contradicting evidence points"
            ConflictLevel.MEDIUM ->
                "${leader.category} has " +
                        "significant contradicting " +
                        "evidence ($leaderContradictions " +
                        "points, ratio " +
                        "%.2f)".format(
                            contradictionRatio
                        )
            ConflictLevel.HIGH ->
                "${leader.category} has " +
                        "high contradicting evidence " +
                        "($leaderContradictions points, " +
                        "ratio " +
                        "%.2f)".format(
                            contradictionRatio
                        )
        }

        return ConflictResult(
            level = level,
            description = description,
            contradictingCount = contradictingTotal
        )
    }

    // ========================================
    // AMBIGUITY COMPUTATION
    // ========================================

    private fun computeAmbiguity(
        candidates: List<CandidateCategoryScore>
    ): AmbiguityResult {

        if (candidates.size < 2) {
            return AmbiguityResult(
                score = 0.0,
                isAmbiguous = false,
                reason = null
            )
        }

        val top = candidates[0]
        val second = candidates[1]

        val gap = if (top.supportScore > 0) {
            top.supportScore - second.supportScore
        } else {
            0.0
        }

        val isAmbiguous =
            gap < config.ambiguityGapRatio

        val reason = if (isAmbiguous) {
            "Close competition between " +
                    "'${top.category}' " +
                    "(%.3f) and '${second.category}' " +
                    "(%.3f)".format(
                        top.supportScore,
                        second.supportScore
                    )
        } else {
            null
        }

        // Ambiguity score: 1.0 = fully ambiguous,
        // 0.0 = fully unambiguous.
        val score = if (top.supportScore > 0) {
            1.0 - (gap / top.supportScore)
        } else {
            1.0
        }

        return AmbiguityResult(
            score = score.coerceIn(0.0, 1.0),
            isAmbiguous = isAmbiguous,
            reason = reason
        )
    }

    // ========================================
    // TRANSITION DETECTION
    // ========================================

    private fun detectTransitions(
        timeline: ItemEvidenceTimeline
    ): TransitionDetectionResult {

        if (timeline.points.size < 2) {
            return TransitionDetectionResult(
                transitions = emptyList(),
                detected = false,
                internalTransitions = emptyList()
            )
        }

        // Build category sequence along the timeline.
        val categorySequence =
            buildCategorySequence(timeline)

        if (categorySequence.size < 2) {
            return TransitionDetectionResult(
                transitions = emptyList(),
                detected = false,
                internalTransitions = emptyList()
            )
        }

        // Detect transitions.
        val transitions =
            mutableListOf<DetectedTransition>()
        val internalTransitions =
            mutableListOf<PossibleInternalTransition>()

        var prevCategory = categorySequence[0].second
        var segmentStartMs =
            categorySequence[0].first
        var segmentCategory = prevCategory
        var consecutiveCount = 1

        for (i in 1 until categorySequence.size) {
            val (relativeMs, category) =
                categorySequence[i]

            if (category != prevCategory) {
                consecutiveCount++

                if (consecutiveCount >=
                    config.transitionMinConsecutive
                ) {
                    // Check if this is a sharp transition
                    // (gap-based) or a smooth one.
                    val gapMs =
                        relativeMs -
                                categorySequence[i - 1].first
                    val isSharp =
                        gapMs >
                                config.transitionGapThresholdMs

                    val signals =
                        mutableListOf<String>()
                    if (isSharp) {
                        signals += "GAP_BASED"
                    }

                    transitions += DetectedTransition(
                        fromCategory = segmentCategory,
                        toCategory = category,
                        atRelativeTimeMs = relativeMs,
                        atFrameIndex = i,
                        isSharp = isSharp,
                        gapBetweenSegments = isSharp,
                        signals = signals
                    )

                    // Check if this is a possible internal
                    // transition (exposed, not auto-split).
                    val segmentDuration =
                        relativeMs - segmentStartMs
                    if (segmentDuration >=
                        config
                            .internalTransitionMinDurationMs
                    ) {
                        internalTransitions +=
                            PossibleInternalTransition(
                                fromCategory =
                                    segmentCategory,
                                toCategory = category,
                                atRelativeTimeMs =
                                    relativeMs,
                                durationInSegmentMs =
                                    segmentDuration,
                                evidenceCount =
                                    consecutiveCount,
                                reason =
                                    "category_changed"
                            )
                    }

                    segmentStartMs = relativeMs
                    segmentCategory = category
                    consecutiveCount = 1
                }
            } else {
                consecutiveCount = 1
            }

            prevCategory = category
        }

        return TransitionDetectionResult(
            transitions = transitions,
            detected = transitions.isNotEmpty(),
            internalTransitions = internalTransitions
        )
    }

    /*
     * Builds a category sequence from the timeline.
     * Each entry is (relativeTimeMs, category).
     *
     * Uses the highest-support category for each point.
     * If a point has no supporting categories, it is
     * skipped (unknown evidence is not invented).
     */
    private fun buildCategorySequence(
        timeline: ItemEvidenceTimeline
    ): List<Pair<Long, String>> {

        return timeline.points
            .filter { it.supportingCategories.isNotEmpty() }
            .map { point ->
                val primaryCategory =
                    point.supportingCategories.first()
                Pair(
                    point.relativeTimeMs,
                    primaryCategory
                )
            }
    }

    // ========================================
    // LIMITATIONS
    // ========================================

    private fun assessLimitations(
        timeline: ItemEvidenceTimeline,
        candidates: List<CandidateCategoryScore>,
        ambiguity: AmbiguityResult,
        conflict: ConflictResult
    ): List<String> {

        val limitations =
            mutableListOf<String>()

        // Coverage limitations.
        when (timeline.coverage.coverageState) {
            CoverageState.NO_COVERAGE ->
                limitations +=
                    "INSUFFICIENT_TEMPORAL_COVERAGE"
            CoverageState.LOW_COVERAGE ->
                limitations +=
                    "LOW_TEMPORAL_COVERAGE"
            CoverageState.PARTIAL_COVERAGE ->
                limitations +=
                    "PARTIAL_TEMPORAL_COVERAGE"
            CoverageState.HIGH_COVERAGE -> {
                // No coverage limitation.
            }
        }

        // Gap limitations.
        if (timeline.hasGap) {
            limitations +=
                "TEMPORAL_GAPS_DETECTED"
        }

        // Ambiguity limitations.
        if (ambiguity.isAmbiguous) {
            limitations +=
                "TEMPORAL_AMBIGUITY"
        }

        // Conflict limitations.
        if (conflict.level == ConflictLevel.HIGH) {
            limitations +=
                "CONFLICTING_TEMPORAL_EVIDENCE"
        }

        // Transition limitations.
        if (timeline.points.size >= 2) {
            val categorySequence =
                buildCategorySequence(timeline)
            val uniqueCategories =
                categorySequence
                    .map { it.second }
                    .distinct()
            if (uniqueCategories.size > 1) {
                limitations +=
                    "RAPID_CONTENT_CHANGE"
            }
        }

        // Single-frame limitation.
        if (timeline.points.size < 2) {
            limitations +=
                "REPRESENTATIVE_FRAME_MISLEADING"
        }

        return limitations
    }

    // ========================================
    // INSUFFICIENT EVIDENCE
    // ========================================

    private fun assessInsufficientEvidence(
        timeline: ItemEvidenceTimeline,
        candidates: List<CandidateCategoryScore>
    ): InsufficientResult {

        // No usable evidence at all.
        if (timeline.points.isEmpty()) {
            return InsufficientResult(
                isInsufficient = true,
                reason = "no_usable_evidence"
            )
        }

        // Too few unique identities.
        if (timeline.uniqueSourceCount <
            config.minUniqueIdentities
        ) {
            return InsufficientResult(
                isInsufficient = true,
                reason =
                    "insufficient_unique_sources_" +
                            "(${timeline.uniqueSourceCount}" +
                            "<${config.minUniqueIdentities})"
            )
        }

        // Coverage too low.
        if (timeline.coverage.coverageRatio <
            config.minCoverageForConfidence
        ) {
            return InsufficientResult(
                isInsufficient = true,
                reason =
                    "insufficient_coverage_" +
                            "(%.3f<%.3f)".format(
                                timeline.coverage
                                    .coverageRatio,
                                config
                                    .minCoverageForConfidence
                            )
            )
        }

        // No candidates.
        if (candidates.isEmpty()) {
            return InsufficientResult(
                isInsufficient = true,
                reason = "no_candidate_categories"
            )
        }

        return InsufficientResult(
            isInsufficient = false,
            reason = null
        )
    }

    // ========================================
    // SNAPSHOT BUILDER
    // ========================================

    private fun buildSnapshot(
        timeline: ItemEvidenceTimeline,
        candidates: List<CandidateCategoryScore>,
        supporting: Map<String,
                List<EvidenceReference>>,
        contradicting: Map<String,
                List<EvidenceReference>>,
        conflictResult: ConflictResult,
        ambiguityResult: AmbiguityResult,
        transitionResult: TransitionDetectionResult,
        limitations: List<String>,
        insufficientResult: InsufficientResult,
        representativeFrameCategory: String?,
        representativeFrameAgreement: Boolean?,
        fusionDurationNs: Long
    ): ItemEvidenceSnapshot {

        // Check representative frame agreement.
        val repAgreement = if (
            representativeFrameCategory != null &&
            candidates.isNotEmpty()
        ) {
            val primaryCat = candidates[0].category
            representativeFrameCategory ==
                    primaryCat ||
                    CategoryCatalog.domainOf(
                        representativeFrameCategory
                    ) == CategoryCatalog.domainOf(
                        primaryCat
                    )
        } else {
            representativeFrameAgreement
        }

        val repOutlier = if (
            representativeFrameCategory != null &&
            candidates.isNotEmpty()
        ) {
            val agreement = repAgreement ?: false
            !agreement
        } else {
            null
        }

        return ItemEvidenceSnapshot(
            sessionId = timeline.sessionId,
            feedItemId = timeline.feedItemId,
            snapshotVersion =
                ItemEvidenceSnapshot.VERSION,
            fusionVersion = config.engineVersion,
            fusionConfigVersion =
                config.configVersion,

            totalEvidencePoints =
                timeline.pointCount,
            usableEvidencePoints =
                timeline.points.count { it.isUsable },
            analyzedFrameCount =
                timeline.points
                    .map { it.frameIndex }
                    .distinct()
                    .size,
            totalFrameCount =
                timeline.points.size,

            itemDurationMs =
                timeline.itemDurationMs,
            coverageState =
                timeline.coverage.coverageState,
            coverageRatio =
                timeline.coverage.coverageRatio,
            observedDurationMs =
                timeline.coverage.observedDurationMs,
            hasGap = timeline.hasGap,
            gapCount = timeline.coverage.gapCount,
            gapTotalDurationMs =
                timeline.coverage.gapTotalDurationMs,

            uniqueEvidenceIdentities =
                timeline.uniqueSourceCount,
            evidenceTypesPresent =
                timeline.evidenceTypesPresent
                    .toList()
                    .sorted(),
            extractorNames =
                timeline.points
                    .map { it.extractorName }
                    .distinct()
                    .sorted(),

            candidateCategories = candidates,
            supportingEvidenceByCategory =
                supporting,
            contradictingEvidenceByCategory =
                contradicting,

            conflictLevel =
                conflictResult.level,
            conflictDescription =
                conflictResult.description,
            contradictingCategoryCount =
                conflictResult.contradictingCount,

            ambiguityScore =
                ambiguityResult.score,
            isAmbiguous =
                ambiguityResult.isAmbiguous,
            ambiguityReason =
                ambiguityResult.reason,

            isInsufficientEvidence =
                insufficientResult.isInsufficient,
            insufficientEvidenceReason =
                insufficientResult.reason,

            detectedTransitions =
                transitionResult.transitions,
            transitionDetected =
                transitionResult.detected,
            possibleInternalTransitions =
                transitionResult.internalTransitions,

            limitations = limitations,

            representativeFrameAgreement =
                repAgreement,
            representativeFrameOutlier =
                repOutlier,

            createdAtMs =
                System.currentTimeMillis(),
            fusionDurationMs =
                fusionDurationNs / 1_000_000
        )
    }

    // ========================================
    // INTERNAL RESULT TYPES
    // ========================================

    private data class ConflictResult(
        val level: ConflictLevel,
        val description: String?,
        val contradictingCount: Int
    )

    private data class AmbiguityResult(
        val score: Double,
        val isAmbiguous: Boolean,
        val reason: String?
    )

    private data class TransitionDetectionResult(
        val transitions: List<DetectedTransition>,
        val detected: Boolean,
        val internalTransitions:
            List<PossibleInternalTransition>
    )

    private data class InsufficientResult(
        val isInsufficient: Boolean,
        val reason: String?
    )
}

/*
 * Number of evidence types for diversity calculation.
 */
private const val EvidenceTypeCount = 7.0
