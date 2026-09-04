package com.example.feedsense.analysis.evidence

/*
 * Milestone 8B-5.
 *
 * Evidence fusion engine.
 *
 * Combines multiple evidence signals into category
 * predictions with explicit ambiguity and conflict
 * detection.
 *
 * Architecture:
 *   Evidence[] → weighted aggregation → candidate categories
 *              → supporting evidence → contradicting evidence
 *              → final fusion result
 *
 * Design principles:
 *   - Deterministic: same evidence → same result
 *   - Transparent: every weight has a name and version
 *   - Conservative: ambiguity is explicit
 *   - Auditable: evidence snapshot preserved
 *
 * Important:
 *   - Single keywords do not force categories
 *   - Platform ≠ category
 *   - Evidence quality ≠ AI confidence
 *   - Unknown remains unknown
 *   - Conflicts are detected, not silently resolved
 *
 * Limitations:
 *   - Weights are heuristic, not learned
 *   - No automatic tuning in v1
 *   - Visual evidence is interface-only
 *   - No neural fusion model
 */
class EvidenceFusionEngine(
    private val config: FusionConfig =
        FusionConfig.DEFAULT
) {

    /*
     * Fuse evidence into a category prediction.
     *
     * @param evidence List of extracted evidence items.
     * @return EvidenceFusionResult with scores,
     *   supporting/contradicting evidence, and metadata.
     */
    fun fuse(
        evidence: List<Evidence>
    ): EvidenceFusionResult {

        if (evidence.isEmpty()) {
            return EvidenceFusionResult.EMPTY
        }

        // --------------------------------
        // 1. COLLECT USABLE EVIDENCE
        // --------------------------------

        val usableEvidence =
            evidence.filter { it.isUsable }

        if (usableEvidence.isEmpty()) {
            return EvidenceFusionResult.EMPTY
        }

        // --------------------------------
        // 2. COMPUTE CATEGORY SCORES
        // --------------------------------

        val categoryScores =
            computeCategoryScores(usableEvidence)

        // --------------------------------
        // 3. RANK CATEGORIES
        // --------------------------------

        val ranked = categoryScores.entries
            .sortedByDescending { it.value }
            .map { (category, score) ->
                val supporting =
                    usableEvidence.filter {
                        it.supportingCategories
                            .contains(category)
                    }

                val contradicting =
                    usableEvidence.filter {
                        it.contradictingCategories
                            .contains(category)
                    }

                RankedCategory(
                    category = category,
                    score = score,
                    supportingCount =
                        supporting.size,
                    contradictingCount =
                        contradicting.size,
                    evidenceTypes =
                        supporting
                            .map { it.type }
                            .distinct()
                )
            }

        // --------------------------------
        // 4. BUILD EVIDENCE MAPS
        // --------------------------------

        val supportingMap =
            mutableMapOf<String, List<Evidence>>()

        val contradictingMap =
            mutableMapOf<String, List<Evidence>>()

        for (entry in ranked) {
            supportingMap[entry.category] =
                usableEvidence.filter {
                    it.supportingCategories
                        .contains(entry.category)
                }

            contradictingMap[entry.category] =
                usableEvidence.filter {
                    it.contradictingCategories
                        .contains(entry.category)
                }
        }

        // --------------------------------
        // 5. DETECT CONFLICTS
        // --------------------------------

        val conflict =
            detectConflict(usableEvidence, ranked)

        // --------------------------------
        // 6. COMPUTE AMBIGUITY
        // --------------------------------

        val ambiguity =
            computeAmbiguity(ranked)

        // --------------------------------
        // 7. COUNT INDEPENDENT SOURCES
        // --------------------------------

        val independentSources =
            usableEvidence
                .map { it.source.extractorName }
                .distinct()
                .size

        // --------------------------------
        // 8. BUILD RESULT
        // --------------------------------

        return EvidenceFusionResult(
            categoryScores = categoryScores,
            rankedCategories = ranked,
            supportingEvidence = supportingMap,
            contradictingEvidence =
                contradictingMap,
            evidenceCount = usableEvidence.size,
            independentSources = independentSources,
            hasConflict = conflict != null,
            conflictDescription = conflict,
            ambiguityScore = ambiguity,
            fusionVersion =
                EvidenceFusionResult.FUSION_VERSION,
            evidenceSnapshot = usableEvidence
        )
    }

    // --------------------------------
    // CATEGORY SCORING
    // --------------------------------

    private fun computeCategoryScores(
        evidence: List<Evidence>
    ): Map<String, Double> {

        val scores =
            mutableMapOf<String, Double>()

        for (item in evidence) {
            val weight =
                config.weightForType(item.type) *
                        item.quality.weight

            for (category in
                item.supportingCategories
            ) {
                scores[category] =
                    (scores[category] ?: 0.0) +
                            weight
            }
        }

        // Normalize to [0, 1]
        val maxScore =
            scores.values.maxOrNull() ?: 1.0

        if (maxScore > 0) {
            for (category in scores.keys) {
                scores[category] =
                    scores[category]!! / maxScore
            }
        }

        return scores
    }

    // --------------------------------
    // CONFLICT DETECTION
    // --------------------------------

    private fun detectConflict(
        evidence: List<Evidence>,
        ranked: List<RankedCategory>
    ): String? {

        if (ranked.size < 2) return null

        val top = ranked[0]
        val second = ranked[1]

        // Check if top category has contradicting
        // evidence from high-quality sources
        val topContradictions =
            evidence.filter {
                it.contradictingCategories
                    .contains(top.category) &&
                        it.quality !=
                        EvidenceQuality.NONE
            }

        if (topContradictions.isNotEmpty()) {
            val sources = topContradictions
                .map { it.source.extractorName }
                .distinct()
                .joinToString(", ")

            return "Category '${top.category}' " +
                    "has contradicting evidence " +
                    "from: $sources"
        }

        // Check if top two categories are very close
        // (potential ambiguity)
        if (top.score - second.score <
            EvidenceFusionResult
                .AMBIGUITY_THRESHOLD
        ) {
            return "Close competition between " +
                    "'${top.category}' (${top.score}) " +
                    "and '${second.category}' " +
                    "(${second.score})"
        }

        return null
    }

    // --------------------------------
    // AMBIGUITY COMPUTATION
    // --------------------------------

    private fun computeAmbiguity(
        ranked: List<RankedCategory>
    ): Double {

        if (ranked.size < 2) return 0.0

        val top = ranked[0].score
        val second = ranked[1].score

        return if (top > 0) {
            1.0 - (top - second)
        } else {
            1.0
        }
    }
}

/*
 * Fusion configuration.
 *
 * Every weight must have:
 *   - a name
 *   - documented purpose
 *   - version
 *   - test coverage
 *
 * Weights are heuristic, not learned. Future versions
 * may allow experimental tuning.
 */
data class FusionConfig(
    val ocrWeight: Double = 1.0,
    val platformWeight: Double = 0.6,
    val visualWeight: Double = 0.3,
    val layoutWeight: Double = 0.4,
    val interactionWeight: Double = 0.5,
    val temporalWeight: Double = 0.3,
    val contentMetadataWeight: Double = 0.2
) {
    fun weightForType(
        type: EvidenceType
    ): Double {
        return when (type) {
            EvidenceType.OCR_TEXT ->
                ocrWeight
            EvidenceType.PLATFORM ->
                platformWeight
            EvidenceType.VISUAL ->
                visualWeight
            EvidenceType.LAYOUT ->
                layoutWeight
            EvidenceType.INTERACTION ->
                interactionWeight
            EvidenceType.TEMPORAL ->
                temporalWeight
            EvidenceType.CONTENT_METADATA ->
                contentMetadataWeight
        }
    }

    companion object {

        val DEFAULT = FusionConfig()

        val DISABLED = FusionConfig(
            ocrWeight = 0.0,
            platformWeight = 0.0,
            visualWeight = 0.0,
            layoutWeight = 0.0,
            interactionWeight = 0.0,
            temporalWeight = 0.0,
            contentMetadataWeight = 0.0
        )

        val CONSERVATIVE = FusionConfig(
            ocrWeight = 1.2,
            platformWeight = 0.4,
            visualWeight = 0.2,
            layoutWeight = 0.3,
            interactionWeight = 0.4,
            temporalWeight = 0.2,
            contentMetadataWeight = 0.1
        )
    }
}

/*
 * Evidence quality weight mapping.
 *
 * Converts quality levels to numeric weights for
 * fusion scoring.
 */
val EvidenceQuality.weight: Double
    get() = when (this) {
        EvidenceQuality.NONE -> 0.0
        EvidenceQuality.LOW -> 0.3
        EvidenceQuality.MEDIUM -> 0.6
        EvidenceQuality.HIGH -> 1.0
    }
