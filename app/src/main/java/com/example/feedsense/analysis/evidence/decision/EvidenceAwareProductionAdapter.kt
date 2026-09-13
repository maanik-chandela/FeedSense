package com.example.feedsense.analysis.evidence.decision

import com.example.feedsense.analysis.evidence.temporal.CoverageState
import com.example.feedsense.analysis.evidence.temporal.ItemEvidenceSnapshot
import com.example.feedsense.analysis.privacy.PrivacyEvidenceLoss
import com.example.feedsense.model.FeedItem

/*
 * Milestone 8B-8.
 *
 * Evidence-aware production adapter.
 *
 * A production-safe, read-only adapter that takes an
 * existing FeedItem and its available 8B-6 evidence
 * snapshot, runs the 8B-7 evidence-aware decision, and
 * returns a structured, immutable comparison between:
 *
 *   1. the existing baseline AI result, and
 *   2. the new evidence-aware result.
 *
 * Architecture:
 *
 *   FeedItem (production, immutable)
 *       │
 *       ├──────────────────► baseline (read verbatim)
 *       │
 *       └──► EvidenceAwareProductionAdapter (THIS)
 *                  │
 *                  ▼
 *          ReadOnlyEvidenceAwareAdapter (8B-7)
 *                  │
 *                  ▼
 *          ItemDecisionEngine (8B-7)
 *                  │
 *                  ▼
 *      EvidenceAwareComparisonResult (8B-8)
 *                  │
 *                  ▼
 *         DecisionEvaluationBridge (8B-7)
 *                  │
 *                  ▼
 *              8A evaluation
 *
 * Guarantees:
 *   - READ-ONLY: the FeedItem is never modified.
 *   - No Room entity is written.
 *   - No baseline prediction is overwritten.
 *   - No AiPredictionRecord is mutated.
 *   - No session state is changed.
 *   - No privacy-sensitive content is exported.
 *   - Deterministic: same inputs → same result.
 *   - Bounded: batch operations have explicit limits.
 *   - Fails safely: adapter failures do not modify
 *     production state.
 *   - Independent from SessionRepository.buildFeedItem().
 *
 * Non-goals:
 *   - Replacing baseline predictions
 *   - Modifying FeedItem schema
 *   - Introducing cloud AI
 *   - Performing self-learning
 *   - Redesigning the UI
 *   - Changing production thresholds
 *   - Fabricating evaluation metrics
 *
 * Design:
 *   - Composes the existing 8B-7
 *     ReadOnlyEvidenceAwareAdapter rather than
 *     duplicating its logic.
 *   - Adds structured comparison state, difference
 *     reasons, evidence strength, and version tracking
 *     on top of the 8B-7 comparison.
 *   - Maps to the 8A evaluation architecture via the
 *     existing DecisionEvaluationBridge.
 *   - Works with the existing category schema (frozen
 *     for this milestone).
 *
 * Usage:
 *   val adapter = EvidenceAwareProductionAdapter()
 *   val result = adapter.compareFeedItem(feedItem, snapshot)
 *
 *   // or batch:
 *   val results = adapter.compareBatch(items, limit = 50)
 */
class EvidenceAwareProductionAdapter(
    private val internalAdapter:
        ReadOnlyEvidenceAwareAdapter =
            ReadOnlyEvidenceAwareAdapter(),
    private val decisionEngine:
        ItemDecisionEngine =
            ItemDecisionEngine,
    private val config:
        ItemDecisionConfig =
            ItemDecisionConfig.DEFAULT
) {

    /**
     * Compares a single FeedItem's baseline prediction
     * against the evidence-aware decision.
     *
     * @param feedItem The authoritative baseline FeedItem.
     *   Used only as an immutable input; never modified.
     * @param snapshot The 8B-6 ItemEvidenceSnapshot for
     *   the same item.
     * @param evaluationItemId Optional evaluation item ID
     *   for 8A bridge traceability.
     * @param datasetVersion Optional dataset version for
     *   version tracking.
     * @return An immutable [EvidenceAwareComparisonResult].
     *   On failure, returns a safe failure state rather
     *   than throwing.
     */
    fun compareFeedItem(
        feedItem: FeedItem,
        snapshot: ItemEvidenceSnapshot,
        evaluationItemId: String? = null,
        datasetVersion: String? = null
    ): EvidenceAwareComparisonResult {

        return try {
            deriveComparison(
                feedItem = feedItem,
                snapshot = snapshot,
                evaluationItemId = evaluationItemId,
                datasetVersion = datasetVersion
            )
        } catch (exception: Exception) {
            failureResult(
                feedItem = feedItem,
                exception = exception
            )
        }
    }

    /**
     * Compares a batch of FeedItems against their
     * evidence snapshots.
     *
     * @param items FeedItem–snapshot pairs to compare.
     * @param limit Maximum number of items to process.
     *   Must be positive.
     * @param evaluationItemIdProvider Optional function to
     *   provide evaluation item IDs per feed item.
     * @param datasetVersion Optional dataset version.
     * @return A list of comparison results in the same
     *   order as the input pairs, never exceeding [limit].
     */
    fun compareBatch(
        items: List<Pair<FeedItem, ItemEvidenceSnapshot>>,
        limit: Int = MAX_BATCH_SIZE,
        evaluationItemIdProvider:
            ((String) -> String?)? = null,
        datasetVersion: String? = null
    ): List<EvidenceAwareComparisonResult> {

        val boundedLimit = limit.coerceIn(
            1, MAX_BATCH_SIZE
        )

        return items
            .take(boundedLimit)
            .map { (feedItem, snapshot) ->
                compareFeedItem(
                    feedItem = feedItem,
                    snapshot = snapshot,
                    evaluationItemId =
                        evaluationItemIdProvider?.invoke(
                            feedItem.id
                        ),
                    datasetVersion = datasetVersion
                )
            }
    }

    /**
     * Derives a comparison result from a single
     * FeedItem and its evidence snapshot.
     *
     * This is the core deterministic comparison logic.
     * All inputs are treated as immutable.
     */
    private fun deriveComparison(
        feedItem: FeedItem,
        snapshot: ItemEvidenceSnapshot,
        evaluationItemId: String?,
        datasetVersion: String?
    ): EvidenceAwareComparisonResult {

        // --------------------------------
        // BASELINE (read verbatim, never
        // modified)
        // --------------------------------

        val baselineCategory = feedItem.category
        val baselineConfidence = feedItem.confidence
        val baselineContentType = feedItem.contentType
        val baselinePlatform = feedItem.platform
        val baselineUncertainty =
            feedItem.uncertaintyLevel
        val baselineNeedsReview = feedItem.needsReview
        val baselineModelVersion = feedItem.modelVersion

        // --------------------------------
        // 8B-7 EVIDENCE-AWARE DECISION
        // --------------------------------

        val comparison =
            internalAdapter.evaluate(feedItem, snapshot)

        // Run the decision engine directly to get the
        // full ItemPredictionResult for detailed
        // comparison.
        val decision = decisionEngine.decide(
            snapshot = snapshot,
            config = config
        )

        val evidenceAwareCategory =
            comparison.evidenceAwareCategory
        val evidenceAwareConfidence =
            comparison.evidenceAwareSupport
        val evidenceAwareContentType =
            baselineContentType
        val evidenceAwarePlatform =
            baselinePlatform
        val evidenceAwareDecision =
            comparison.decisionState
        val evidenceAwareUncertainty =
            comparison.uncertainty
        val evidenceAwareNeedsReview =
            needsReviewFromDecision(decision)

        // --------------------------------
        // COMPARISON FIELDS
        // --------------------------------

        val categoryChanged =
            comparison.categoryChanged

        val confidenceChanged =
            baselineConfidence != evidenceAwareConfidence

        val contentTypeChanged = false
        // Baseline content type is used as-is; evidence-
        // aware does not produce a separate content type
        // in 8B-8. This stays false unless a future
        // milestone introduces content-type evidence.

        val platformChanged = false
        // Same rationale as contentTypeChanged.

        val reviewStatusChanged =
            baselineNeedsReview !=
                evidenceAwareNeedsReview

        val decisionAgreement =
            baselineCategory !=
                null &&
                evidenceAwareCategory !=
                    null &&
                baselineCategory ==
                    evidenceAwareCategory

        // --------------------------------
        // COMPARISON STATE
        // --------------------------------

        val comparisonState =
            classifyComparisonState(
                baselineCategory =
                    baselineCategory,
                evidenceAwareCategory =
                    evidenceAwareCategory,
                evidenceAwareDecision =
                    evidenceAwareDecision,
                evidenceAwareUncertainty =
                    evidenceAwareUncertainty,
                categoryChanged =
                    categoryChanged,
                reviewStatusChanged =
                    reviewStatusChanged
            )

        // --------------------------------
        // DIFFERENCE REASONS
        // --------------------------------

        val differenceReasons =
            deriveDifferenceReasons(
                snapshot = snapshot,
                decision = decision,
                baselineCategory =
                    baselineCategory,
                evidenceAwareCategory =
                    evidenceAwareCategory,
                categoryChanged =
                    categoryChanged,
                comparisonState =
                    comparisonState
            )

        // --------------------------------
        // PRIVACY (8B-10)
        // --------------------------------
        //
        // Surfaces sanitization context and appends the
        // two privacy reasons ONLY when the snapshot's
        // privacy metadata actually reports them.

        val privacyEvidence = snapshot.privacyEvidence

        val privacyReasons =
            derivePrivacyReasons(
                privacyEvidence = privacyEvidence,
                categoryChanged = categoryChanged
            )

        val allDifferenceReasons =
            (differenceReasons + privacyReasons)
                .distinct()

        val privacyLossLabels =
            when (privacyEvidence.loss) {
                PrivacyEvidenceLoss.NONE ->
                    emptyList()
                else ->
                    listOf(privacyEvidence.loss.label)
            }

        // --------------------------------
        // EVIDENCE ASSESSMENT
        // --------------------------------

        val evidenceAvailable =
            snapshot.usableEvidencePoints > 0

        val evidenceStrength =
            assessEvidenceStrength(
                snapshot = snapshot
            )

        val evidenceTypesUsed =
            snapshot.evidenceTypesPresent

        // --------------------------------
        // RESULT
        // --------------------------------

        return EvidenceAwareComparisonResult(
            // Identity
            feedItemId = feedItem.id,
            sessionId = feedItem.sessionId,
            evaluationItemId = evaluationItemId,

            // Versioning
            datasetVersion = datasetVersion,
            baselineModelVersion =
                baselineModelVersion,
            evidenceAwareModelVersion =
                comparison.fusionVersion,
            decisionVersion =
                comparison.decisionVersion,
            adapterVersion =
                EvidenceAwareComparisonResult
                    .ADAPTER_VERSION,

            // Baseline
            baselineCategory =
                baselineCategory,
            baselineConfidence =
                baselineConfidence,
            baselineContentType =
                baselineContentType,
            baselinePlatform =
                baselinePlatform,
            baselineUncertainty =
                baselineUncertainty,
            baselineNeedsReview =
                baselineNeedsReview,

            // Evidence-aware
            evidenceAwareCategory =
                evidenceAwareCategory,
            evidenceAwareConfidence =
                evidenceAwareConfidence,
            evidenceAwareContentType =
                evidenceAwareContentType,
            evidenceAwarePlatform =
                evidenceAwarePlatform,
            evidenceAwareDecision =
                evidenceAwareDecision,
            evidenceAwareUncertainty =
                evidenceAwareUncertainty,
            evidenceAwareNeedsReview =
                evidenceAwareNeedsReview,

            // Comparison
            categoryChanged =
                categoryChanged,
            confidenceChanged =
                confidenceChanged,
            contentTypeChanged =
                contentTypeChanged,
            platformChanged =
                platformChanged,
            reviewStatusChanged =
                reviewStatusChanged,
            decisionAgreement =
                decisionAgreement,
            comparisonState =
                comparisonState,
            differenceReasons =
                allDifferenceReasons,

            // Evidence
            evidenceAvailable =
                evidenceAvailable,
            evidenceStrength =
                evidenceStrength,
            evidenceTypesUsed =
                evidenceTypesUsed,

            // Audit
            timestampMs =
                System.currentTimeMillis(),
            fusionVersion =
                comparison.fusionVersion,

            // Privacy (8B-10)
            privacyPolicyVersion =
                privacyEvidence.policyVersion,
            privacyStatusLabel =
                privacyEvidence.status.label,
            privacyEvidenceLossLabels =
                privacyLossLabels
        )
    }

    // ========================================
    // COMPARISON STATE CLASSIFICATION
    // ========================================

    /*
     * Classifies the deterministic comparison state from
     * baseline and evidence-aware values.
     */
    private fun classifyComparisonState(
        baselineCategory: String?,
        evidenceAwareCategory: String?,
        evidenceAwareDecision: DecisionState,
        evidenceAwareUncertainty: UncertaintyState,
        categoryChanged: Boolean,
        reviewStatusChanged: Boolean
    ): ComparisonState {

        // Both unknown.
        if (baselineCategory == null &&
            evidenceAwareCategory == null) {
            return ComparisonState.BOTH_UNKNOWN
        }

        // Baseline unknown.
        if (baselineCategory == null) {
            return ComparisonState.BASELINE_UNKNOWN
        }

        // Evidence-aware abstained insufficient.
        if (evidenceAwareDecision ==
            DecisionState.INSUFFICIENT_EVIDENCE) {
            return ComparisonState.INSUFFICIENT_EVIDENCE
        }

        // Evidence-aware unknown.
        if (evidenceAwareCategory == null) {
            return ComparisonState.EVIDENCE_AWARE_UNKNOWN
        }

        // Review required: evidence-aware flagged review
        // or high uncertainty with disagreement.
        if (reviewStatusChanged &&
            evidenceAwareUncertainty !=
                UncertaintyState.LOW) {
            return ComparisonState.REVIEW_REQUIRED
        }

        // Category disagreement.
        if (categoryChanged) {
            return ComparisonState.CATEGORY_DISAGREEMENT
        }

        // Same category.
        return ComparisonState.AGREEMENT
    }

    // ========================================
    // DIFFERENCE REASON DERIVATION
    // ========================================

    /*
     * Derives structured reasons for the difference
     * between baseline and evidence-aware results.
     *
     * Only reasons supported by the actual evidence are
     * included. UNKNOWN is preferred over fabrication.
     */
    private fun deriveDifferenceReasons(
        snapshot: ItemEvidenceSnapshot,
        decision: ItemPredictionResult,
        baselineCategory: String?,
        evidenceAwareCategory: String?,
        categoryChanged: Boolean,
        comparisonState: ComparisonState
    ): List<DifferenceReason> {

        val reasons =
            mutableListOf<DifferenceReason>()

        // INSUFFICIENT_EVIDENCE is reported regardless
        // of whether categories can be compared.
        if (comparisonState ==
            ComparisonState.INSUFFICIENT_EVIDENCE) {
            reasons.add(
                DifferenceReason
                    .INSUFFICIENT_EVIDENCE
            )
        }

        if (!categoryChanged) {
            return reasons.distinct()
        }

        // Representative frame outlier.
        if (snapshot.representativeFrameOutlier ==
            true) {
            reasons.add(
                DifferenceReason
                    .REPRESENTATIVE_FRAME_CONFLICT
            )
        }

        // Temporal context changed decision.
        if (snapshot.usableEvidencePoints > 1 &&
            snapshot.coverageState !=
                CoverageState.NO_COVERAGE) {
            reasons.add(
                DifferenceReason
                    .TEMPORAL_CONTEXT_CHANGED_DECISION
            )
        }

        // OCR evidence influenced decision.
        if (snapshot.evidenceTypesPresent
                .contains("OCR")) {
            reasons.add(
                DifferenceReason
                    .OCR_CHANGED_DECISION
            )
        }

        // Platform evidence present.
        if (snapshot.evidenceTypesPresent
                .contains("PLATFORM")) {
            reasons.add(
                DifferenceReason
                    .PLATFORM_EVIDENCE_CHANGED_DECISION
            )
        }

        // Interaction evidence present.
        if (snapshot.evidenceTypesPresent
                .contains("INTERACTION")) {
            reasons.add(
                DifferenceReason
                    .INTERACTION_EVIDENCE_CHANGED_DECISION
            )
        }

        // Internal transition detected.
        if (snapshot.possibleInternalTransitions
                .isNotEmpty()) {
            reasons.add(
                DifferenceReason
                    .SEGMENTATION_DIFFERENCE
            )
        }

        // Low information evidence.
        if (snapshot.evidenceTypesPresent.size <= 1) {
            reasons.add(
                DifferenceReason
                    .LOW_INFORMATION_EVIDENCE
            )
        }

        // Short interaction.
        if (snapshot.itemDurationMs <
            SHORT_INTERACTION_THRESHOLD_MS) {
            reasons.add(
                DifferenceReason
                    .SHORT_INTERACTION
            )
        }

        // Category boundary (both systems disagree but
        // both are in the same domain).
        if (baselineCategory != null &&
            evidenceAwareCategory != null) {
            val baselineDomain =
                com.example.feedsense.analysis
                    .CategoryCatalog
                    .domainOf(baselineCategory)
            val evidenceAwareDomain =
                com.example.feedsense.analysis
                    .CategoryCatalog
                    .domainOf(evidenceAwareCategory)
            if (baselineDomain != null &&
                evidenceAwareDomain != null &&
                baselineDomain ==
                    evidenceAwareDomain) {
                reasons.add(
                    DifferenceReason
                        .CATEGORY_BOUNDARY
                )
            }
        }

        // Model disagreement (always present on
        // disagreement, but only added when no other
        // specific reason found).
        if (reasons.isEmpty()) {
            reasons.add(
                DifferenceReason
                    .MODEL_DISAGREEMENT
            )
        }

        return reasons.distinct()
    }

    // ========================================
    // PRIVACY REASON DERIVATION (8B-10)
    // ========================================

    /*
     * Appends privacy reasons ONLY when the snapshot's
     * privacy metadata actually supports them. Never
     * fabricated.
     */
    private fun derivePrivacyReasons(
        privacyEvidence:
            com.example.feedsense.analysis.privacy.PrivacyEvidenceMetadata,
        categoryChanged: Boolean
    ): List<DifferenceReason> {

        val reasons = mutableListOf<DifferenceReason>()

        if (privacyEvidence.decisionAffected &&
            categoryChanged) {
            reasons.add(
                DifferenceReason
                    .PRIVACY_REDACTION_AFFECTED_DECISION
            )
        }

        when (privacyEvidence.loss) {
            PrivacyEvidenceLoss.NONE -> Unit
            PrivacyEvidenceLoss.OCR_TEXT_REDACTED,
            PrivacyEvidenceLoss.EVIDENCE_LOST_DUE_TO_SANITIZATION,
            PrivacyEvidenceLoss.CAPTURE_BLOCKED -> {
                reasons.add(
                    DifferenceReason
                        .EVIDENCE_LOST_DUE_TO_SANITIZATION
                )
            }
        }

        return reasons
    }

    // ========================================
    // EVIDENCE STRENGTH ASSESSMENT
    // ========================================

    /*
     * Assesses evidence strength from the snapshot
     * independently of AI confidence or prediction
     * correctness.
     */
    private fun assessEvidenceStrength(
        snapshot: ItemEvidenceSnapshot
    ): EvidenceStrength {

        if (snapshot.usableEvidencePoints <= 0) {
            return EvidenceStrength.NONE
        }

        val sourceDiversity =
            snapshot.evidenceTypesPresent.size
        val coverageRatio =
            snapshot.coverageRatio
        val uniqueIdentities =
            snapshot.uniqueEvidenceIdentities

        // Strong: diverse sources, high coverage,
        // many unique identities.
        if (sourceDiversity >= 3 &&
            coverageRatio >= 0.7 &&
            uniqueIdentities >= 5) {
            return EvidenceStrength.STRONG
        }

        // Moderate: some diversity and coverage.
        if (sourceDiversity >= 2 &&
            coverageRatio >= 0.3 &&
            uniqueIdentities >= 3) {
            return EvidenceStrength.MODERATE
        }

        // Weak: at least some evidence but limited.
        if (snapshot.usableEvidencePoints >= 1 &&
            coverageRatio > 0.0) {
            return EvidenceStrength.WEAK
        }

        return EvidenceStrength.NONE
    }

    // ========================================
    // NEEDS REVIEW
    // ========================================

    /*
     * Derives whether the evidence-aware decision
     * recommends human review.
     */
    private fun needsReviewFromDecision(
        decision: ItemPredictionResult
    ): Boolean {
        return when (decision.decisionState) {
            DecisionState.DECIDED ->
                decision.uncertainty !=
                    UncertaintyState.LOW
            else -> true
        }
    }

    // ========================================
    // FAILURE HANDLING
    // ========================================

    /*
     * Builds a safe failure result when the adapter
     * encounters an error. The failure does NOT modify
     * any production state.
     */
    private fun failureResult(
        feedItem: FeedItem,
        exception: Exception
    ): EvidenceAwareComparisonResult {

        return EvidenceAwareComparisonResult(
            // Identity
            feedItemId = feedItem.id,
            sessionId = feedItem.sessionId,

            // Versioning
            adapterVersion =
                EvidenceAwareComparisonResult
                    .ADAPTER_VERSION,

            // Baseline (verbatim)
            baselineCategory =
                feedItem.category,
            baselineConfidence =
                feedItem.confidence,
            baselineContentType =
                feedItem.contentType,
            baselinePlatform =
                feedItem.platform,
            baselineUncertainty =
                feedItem.uncertaintyLevel,
            baselineNeedsReview =
                feedItem.needsReview,

            // Evidence-aware: abstained on failure
            evidenceAwareCategory = null,
            evidenceAwareConfidence = null,
            evidenceAwareDecision =
                DecisionState.UNKNOWN,
            evidenceAwareUncertainty =
                UncertaintyState.UNRESOLVED,

            // Comparison
            categoryChanged = false,
            confidenceChanged = false,
            contentTypeChanged = false,
            platformChanged = false,
            reviewStatusChanged = false,
            decisionAgreement = false,
            comparisonState =
                ComparisonState.EVIDENCE_AWARE_UNKNOWN,
            differenceReasons =
                listOf(DifferenceReason.UNKNOWN),

            // Evidence
            evidenceAvailable = false,
            evidenceStrength =
                EvidenceStrength.NONE,
            evidenceTypesUsed = emptyList(),

            // Audit
            timestampMs =
                System.currentTimeMillis()
        )
    }

    companion object {

        /*
         * Maximum number of items in a single batch
         * comparison. Prevents unbounded processing.
         */
        const val MAX_BATCH_SIZE = 100

        /*
         * Threshold below which an interaction is
         * considered short (5 seconds).
         */
        const val SHORT_INTERACTION_THRESHOLD_MS =
            5000L
    }
}
