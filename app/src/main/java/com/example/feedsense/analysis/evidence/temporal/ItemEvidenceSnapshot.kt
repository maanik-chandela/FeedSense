package com.example.feedsense.analysis.evidence.temporal

/*
 * Milestone 8B-6.
 *
 * Item evidence snapshot.
 *
 * An immutable, serializable representation of the
 * complete temporal evidence fusion result for a single
 * FeedItem. This is the audit-ready output of 8B-6.
 *
 * Design:
 *   - Immutable: once created, never modified
 *   - Serializable: for audit, reproducibility, and
 *     future storage
 *   - No embedded screenshots or raw OCR payloads
 *   - Compact: structured fields only
 *
 * The snapshot captures:
 *   - item identity and versioning
 *   - evidence statistics
 *   - candidate categories with support scores
 *   - supporting and contradicting evidence
 *   - conflict and ambiguity states
 *   - detected transitions
 *   - coverage assessment
 *   - limitations and uncertainty reasons
 *
 * Important:
 *   - supportScore ≠ probability (unless calibrated)
 *   - Missing evidence remains missing
 *   - The snapshot is a record, not a prediction
 *   - The existing classifier can still make the
 *     final category decision
 */
data class ItemEvidenceSnapshot(
    // --------------------------------
    // IDENTITY & VERSIONING
    // --------------------------------

    val sessionId: String,
    val feedItemId: String?,
    val snapshotVersion: String,
    val fusionVersion: String,
    val fusionConfigVersion: String,

    // --------------------------------
    // EVIDENCE STATISTICS
    // --------------------------------

    val totalEvidencePoints: Int,
    val usableEvidencePoints: Int,
    val analyzedFrameCount: Int,
    val totalFrameCount: Int,

    // --------------------------------
    // TEMPORAL COVERAGE
    // --------------------------------

    val itemDurationMs: Long,
    val coverageState: CoverageState,
    val coverageRatio: Double,
    val observedDurationMs: Long,
    val hasGap: Boolean,
    val gapCount: Int,
    val gapTotalDurationMs: Long,

    // --------------------------------
    // EVIDENCE SOURCE DIVERSITY
    // --------------------------------

    val uniqueEvidenceIdentities: Int,
    val evidenceTypesPresent: List<String>,
    val extractorNames: List<String>,

    // --------------------------------
    // CANDIDATE CATEGORIES
    // --------------------------------

    val candidateCategories: List<CandidateCategoryScore>,

    // --------------------------------
    // SUPPORTING / CONTRADICTING
    // --------------------------------

    val supportingEvidenceByCategory:
        Map<String, List<EvidenceReference>>,
    val contradictingEvidenceByCategory:
        Map<String, List<EvidenceReference>>,

    // --------------------------------
    // CONFLICT
    // --------------------------------

    val conflictLevel: ConflictLevel,
    val conflictDescription: String?,
    val contradictingCategoryCount: Int,

    // --------------------------------
    // AMBIGUITY
    // --------------------------------

    val ambiguityScore: Double,
    val isAmbiguous: Boolean,
    val ambiguityReason: String?,

    // --------------------------------
    // INSUFFICIENT EVIDENCE
    // --------------------------------

    val isInsufficientEvidence: Boolean,
    val insufficientEvidenceReason: String?,

    // --------------------------------
    // TRANSITIONS
    // --------------------------------

    val detectedTransitions: List<DetectedTransition>,
    val transitionDetected: Boolean,
    val possibleInternalTransitions:
        List<PossibleInternalTransition>,

    // --------------------------------
    // LIMITATIONS
    // --------------------------------

    val limitations: List<String>,

    // --------------------------------
    // REPRESENTATIVE FRAME
    // --------------------------------

    val representativeFrameAgreement: Boolean?,
    val representativeFrameOutlier: Boolean?,

    // --------------------------------
    // METADATA
    // --------------------------------

    val createdAtMs: Long,
    val fusionDurationMs: Long
) {
    /*
     * Primary candidate category (highest support).
     */
    val primaryCategory: String?
        get() = candidateCategories
            .firstOrNull()?.category

    /*
     * Support score of the primary category.
     */
    val primarySupportScore: Double
        get() = candidateCategories
            .firstOrNull()?.supportScore ?: 0.0

    /*
     * Secondary categories with significant support.
     */
    val secondaryCategories: List<String>
        get() = candidateCategories
            .drop(1)
            .filter { it.supportScore > 0.1 }
            .map { it.category }

    /*
     * Whether the snapshot has any meaningful content
     * for temporal reasoning.
     */
    val hasTemporalReasoning: Boolean
        get() = usableEvidencePoints >= 2 &&
                coverageState != CoverageState.NO_COVERAGE

    companion object {

        const val VERSION = "item-evidence-snapshot-v1"

        /*
         * Creates an empty snapshot for items with no
         * usable evidence.
         */
        fun empty(
            sessionId: String,
            feedItemId: String?
        ): ItemEvidenceSnapshot {
            return ItemEvidenceSnapshot(
                sessionId = sessionId,
                feedItemId = feedItemId,
                snapshotVersion = VERSION,
                fusionVersion =
                    TemporalEvidenceFusionConfig
                        .ENGINE_VERSION,
                fusionConfigVersion =
                    TemporalEvidenceFusionConfig
                        .CONFIG_VERSION,
                totalEvidencePoints = 0,
                usableEvidencePoints = 0,
                analyzedFrameCount = 0,
                totalFrameCount = 0,
                itemDurationMs = 0,
                coverageState =
                    CoverageState.NO_COVERAGE,
                coverageRatio = 0.0,
                observedDurationMs = 0,
                hasGap = false,
                gapCount = 0,
                gapTotalDurationMs = 0,
                uniqueEvidenceIdentities = 0,
                evidenceTypesPresent = emptyList(),
                extractorNames = emptyList(),
                candidateCategories = emptyList(),
                supportingEvidenceByCategory =
                    emptyMap(),
                contradictingEvidenceByCategory =
                    emptyMap(),
                conflictLevel = ConflictLevel.NONE,
                conflictDescription = null,
                contradictingCategoryCount = 0,
                ambiguityScore = 1.0,
                isAmbiguous = true,
                ambiguityReason = "no_evidence",
                isInsufficientEvidence = true,
                insufficientEvidenceReason =
                    "no_usable_evidence",
                detectedTransitions = emptyList(),
                transitionDetected = false,
                possibleInternalTransitions =
                    emptyList(),
                limitations = listOf("no_evidence"),
                representativeFrameAgreement = null,
                representativeFrameOutlier = null,
                createdAtMs = System.currentTimeMillis(),
                fusionDurationMs = 0
            )
        }
    }
}

/*
 * A candidate category with its support score and
 * evidence summary.
 *
 * Important: supportScore is NOT a probability unless
 * explicit calibration has been performed.
 */
data class CandidateCategoryScore(
    val category: String,
    val supportScore: Double,
    val supportingCount: Int,
    val contradictingCount: Int,
    val evidenceTypes: List<String>,
    val persistenceLevel: String,
    val firstSeenRelativeMs: Long,
    val lastSeenRelativeMs: Long,
    val temporalSpreadMs: Long
)

/*
 * A reference to a piece of evidence, lightweight
 * without embedding full evidence data.
 */
data class EvidenceReference(
    val pointId: String,
    val evidenceType: String,
    val extractorName: String,
    val quality: String,
    val valueSummary: String,
    val relativeTimeMs: Long,
    val frameIndex: Int
)

/*
 * A detected transition between categories within
 * the item's timeline.
 */
data class DetectedTransition(
    val fromCategory: String?,
    val toCategory: String?,
    val atRelativeTimeMs: Long,
    val atFrameIndex: Int,
    val isSharp: Boolean,
    val gapBetweenSegments: Boolean,
    val signals: List<String>
)

/*
 * A possible internal transition that suggests the
 * FeedItem may contain multiple semantic regions.
 *
 * 8B-6 does NOT automatically split FeedItems. It
 * exposes this for future segmentation experiments.
 */
data class PossibleInternalTransition(
    val fromCategory: String,
    val toCategory: String,
    val atRelativeTimeMs: Long,
    val durationInSegmentMs: Long,
    val evidenceCount: Int,
    val reason: String
)

/*
 * Conflict level for the item's evidence.
 */
enum class ConflictLevel(val label: String) {
    NONE("NONE"),
    LOW("LOW"),
    MEDIUM("MEDIUM"),
    HIGH("HIGH")
}
