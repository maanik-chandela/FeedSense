package com.example.feedsense.analysis.evidence

/*
 * Milestone 8B-5.
 *
 * Evidence fusion result.
 *
 * Contains the output of evidence fusion:
 *   - Candidate category scores
 *   - Supporting evidence per category
 *   - Contradicting evidence per category
 *   - Evidence density metrics
 *   - Ambiguity assessment
 *   - Conflict detection
 *   - Fusion version for reproducibility
 *
 * Design:
 *   - Deterministic: same evidence → same result
 *   - Serializable for audit/reproducibility
 *   - Separated from final classification
 *   - Compatible with future ML models
 *
 * Important:
 *   - This is NOT the final classification
 *   - The existing classifier can still select
 *     its final category
 *   - Multiple categories may be possible
 *   - Ambiguity is explicit, not hidden
 */
data class EvidenceFusionResult(
    val categoryScores: Map<String, Double>,
    val rankedCategories: List<RankedCategory>,
    val supportingEvidence:
        Map<String, List<Evidence>>,
    val contradictingEvidence:
        Map<String, List<Evidence>>,
    val evidenceCount: Int,
    val independentSources: Int,
    val hasConflict: Boolean,
    val conflictDescription: String?,
    val ambiguityScore: Double,
    val fusionVersion: String,
    val evidenceSnapshot: List<Evidence>
) {
    val topCategory: String?
        get() = rankedCategories
            .firstOrNull()?.category

    val topScore: Double
        get() = rankedCategories
            .firstOrNull()?.score ?: 0.0

    val isAmbiguous: Boolean
        get() = ambiguityScore > AMBIGUITY_THRESHOLD

    val hasInsufficientEvidence: Boolean
        get() = evidenceCount < MIN_EVIDENCE_COUNT

    companion object {

        const val FUSION_VERSION = "fusion-v1"

        /*
         * Ambiguity threshold. When the top two
         * categories are within this margin, the
         * result is considered ambiguous.
         */
        const val AMBIGUITY_THRESHOLD = 0.15

        /*
         * Minimum evidence count for confident
         * predictions. Below this, the result is
         * considered to have insufficient evidence.
         */
        const val MIN_EVIDENCE_COUNT = 2

        /*
         * Empty result for cases with no evidence.
         */
        val EMPTY = EvidenceFusionResult(
            categoryScores = emptyMap(),
            rankedCategories = emptyList(),
            supportingEvidence = emptyMap(),
            contradictingEvidence = emptyMap(),
            evidenceCount = 0,
            independentSources = 0,
            hasConflict = false,
            conflictDescription = null,
            ambiguityScore = 1.0,
            fusionVersion = FUSION_VERSION,
            evidenceSnapshot = emptyList()
        )
    }
}

/*
 * A category with its fusion score and supporting
 * evidence count.
 */
data class RankedCategory(
    val category: String,
    val score: Double,
    val supportingCount: Int,
    val contradictingCount: Int,
    val evidenceTypes: List<EvidenceType>
)
