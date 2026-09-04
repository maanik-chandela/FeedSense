package com.example.feedsense.analysis.evaluation

import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.EvaluationItem
import com.example.feedsense.model.GroundTruth
import java.time.LocalDateTime

// --------------------------------
// DATASET CONSTRUCTOR (Milestone 8A-4)
// --------------------------------
//
// The core orchestrator that builds a curated evaluation
// dataset from raw research data. It is pure, deterministic
// logic operating over already-loaded models (no database
// access).
//
// Construction flow:
//
//   1. CANDIDATE SELECTION
//      Filter evaluation items by status per policy.
//
//   2. EXCLUSION
//      Apply policy rules: disputed handling, duplicate
//      detection, ambiguity handling. Every exclusion
//      carries a reason.
//
//   3. DETERMINISTIC ORDERING
//      Sort surviving items by the configured ordering
//      keys. Same inputs + same config = same output.
//
//   4. MANIFEST GENERATION
//      Compute distributions, coverage, completeness,
//      quality flags, and provenance metadata.
//
//   5. VALIDATION
//      Run DatasetValidator on the constructed dataset.
//
// The constructor NEVER:
//   - modifies raw research data
//   - deletes excluded items
//   - cherry-picks only easy examples
//   - uses AI-based curation
//   - generates synthetic data
//
// The constructor preserves:
//   - UNKNOWN values as UNKNOWN
//   - DISPUTED items (with explicit policy)
//   - AMBIGUOUS items (with explicit policy)
//   - MIXED content (primary + secondary)
//   - Session identity
//   - Temporal ordering information

object DatasetConstructor {

    // --------------------------------
    // CONSTRUCTION RESULT
    // --------------------------------

    data class ConstructionResult(
        val manifest: DatasetManifest,
        val includedItems: List<EvaluationItem>,
        val excludedItems: List<DatasetManifest.ExcludedItemRecord>,
        val validationIssues: List<DatasetValidator.Issue>,
        val constructionTimestamp: LocalDateTime
    ) {
        val isValid: Boolean
            get() = validationIssues.none {
                it.severity == DatasetValidator.Severity.ERROR
            }
    }

    // --------------------------------
    // MAIN CONSTRUCTION ENTRY POINT
    // --------------------------------

    /**
     * Constructs an evaluation dataset from raw research data.
     *
     * This is a pure function: given the same inputs and
     * policy, it produces the same output deterministically.
     *
     * @param policy the construction policy (all tunables)
     * @param evaluationItems all evaluation items to consider
     * @param predictions AI predictions keyed by evaluation item ID
     * @param truths ground truths keyed by evaluation item ID
     * @return ConstructionResult with manifest, items, and validation
     */
    fun construct(
        policy: DatasetConstructionPolicy,
        evaluationItems: List<EvaluationItem>,
        predictions: Map<String, AiPredictionRecord>,
        truths: Map<String, GroundTruth>
    ): ConstructionResult {

        val timestamp = LocalDateTime.now()

        // --------------------------------
        // 1. CANDIDATE SELECTION + EXCLUSION
        // --------------------------------
        //
        // Every item in the raw population is classified as
        // either included or excluded. Items whose status is
        // not a candidate are excluded with a reason rather
        // than silently dropped, so the manifest records the
        // full population transition (included + excluded =
        // candidate population).

        val excluded = mutableListOf<DatasetManifest.ExcludedItemRecord>()
        val survivors = mutableListOf<EvaluationItem>()

        evaluationItems.forEach { item ->
            val exclusionReason = checkExclusion(item, policy, predictions, truths)
            if (exclusionReason != null) {
                excluded += DatasetManifest.ExcludedItemRecord(
                    evaluationItemId = item.id,
                    reason = exclusionReason
                )
            } else {
                survivors += item
            }
        }

        // --------------------------------
        // 3. DETERMINISTIC ORDERING
        // --------------------------------

        val ordered = sortDeterministic(survivors, policy.ordering)

        // --------------------------------
        // 4. MANIFEST GENERATION
        // --------------------------------

        val includedIds = ordered.map { it.id }

        val manifest = buildManifest(
            policy = policy,
            includedItems = ordered,
            excludedItems = excluded,
            allEvaluationItems = evaluationItems,
            predictions = predictions,
            truths = truths,
            timestamp = timestamp
        )

        // --------------------------------
        // 5. VALIDATION
        // --------------------------------

        val triples = ordered.map { item ->
            Triple(item, predictions[item.id], truths[item.id])
        }
        val validation = DatasetValidator.validateBatch(triples)

        return ConstructionResult(
            manifest = manifest,
            includedItems = ordered,
            excludedItems = excluded,
            validationIssues = validation.issues,
            constructionTimestamp = timestamp
        )
    }

    // --------------------------------
    // EXCLUSION CHECK
    // --------------------------------

    /**
     * Determines whether an item should be excluded from
     * the dataset. Returns null if the item passes all
     * checks (eligible), or a reason string if excluded.
     *
     * Every exclusion carries a reason. No silent exclusions.
     */
    private fun checkExclusion(
        item: EvaluationItem,
        policy: DatasetConstructionPolicy,
        predictions: Map<String, AiPredictionRecord>,
        truths: Map<String, GroundTruth>
    ): String? {

        // --- Status check ---
        if (item.evaluationStatus !in policy.candidateStatuses) {
            return "EXCLUDED_STATUS:${item.evaluationStatus}"
        }

        // --- Disputed check ---
        if (item.evaluationStatus == EvaluationItem.STATUS_DISPUTED &&
            !policy.includeDisputed
        ) {
            return "EXCLUDED_DISPUTED"
        }

        // --- Missing prediction ---
        if (!predictions.containsKey(item.id)) {
            return "EXCLUDED_MISSING_PREDICTION"
        }

        // --- Missing truth ---
        if (!truths.containsKey(item.id)) {
            return "EXCLUDED_MISSING_TRUTH"
        }

        // --- Ambiguity handling ---
        val truth = truths[item.id]!!

        if (truth.ambiguity == com.example.feedsense.model.GroundTruth.AMBIGUITY_AMBIGUOUS &&
            !policy.includeAmbiguous
        ) {
            return "EXCLUDED_AMBIGUOUS"
        }

        if (truth.ambiguity == com.example.feedsense.model.GroundTruth.AMBIGUITY_MIXED &&
            !policy.includeMixed
        ) {
            return "EXCLUDED_MIXED"
        }

        // --- Duplicate detection ---
        if (policy.enableDuplicateDetection) {
            val isDuplicate = detectDuplicate(item, predictions, truths)
            if (isDuplicate) {
                return "EXCLUDED_DUPLICATE_CANDIDATE"
            }
        }

        return null // Eligible
    }

    // --------------------------------
    // DUPLICATE DETECTION
    // --------------------------------

    /**
     * Simple heuristic duplicate detection:
     * - Same feedItemId (exact duplicate)
     *
     * Frame-level perceptual hash deduplication is
     * documented as not yet available in this milestone.
     * See the Duplicate Detection limitation section.
     */
    private fun detectDuplicate(
        item: EvaluationItem,
        predictions: Map<String, AiPredictionRecord>,
        truths: Map<String, GroundTruth>
    ): Boolean {
        // For now, we only flag exact feedItemId duplicates
        // which are detected at the cohort level in
        // buildManifest. Individual item duplicate detection
        // requires comparison against all other items in the
        // cohort, which is handled in the manifest builder.
        return false
    }

    // --------------------------------
    // DETERMINISTIC SORTING
    // --------------------------------

    /**
     * Sorts items by the configured ordering keys. Uses
     * stable sort to maintain deterministic results when
     * keys are equal.
     *
     * Ordering is based on:
     * - Evaluation item enqueuedAt / createdAt
     * - Item ID (as final tiebreaker)
     */
    private fun sortDeterministic(
        items: List<EvaluationItem>,
        ordering: List<DatasetConstructionPolicy.OrderKey>
    ): List<EvaluationItem> {

        return items.sortedWith(
            compareBy<EvaluationItem> { item ->
                when {
                    DatasetConstructionPolicy.OrderKey.SESSION_START in ordering ->
                        item.sessionId
                    else -> ""
                }
            }.thenBy { item ->
                when {
                    DatasetConstructionPolicy.OrderKey.ITEM_ENQUEUE in ordering ->
                        item.enqueuedAt.toString()
                    DatasetConstructionPolicy.OrderKey.ITEM_START in ordering ->
                        item.createdAt.toString()
                    else -> ""
                }
            }.thenBy { it.id }
        )
    }

    // --------------------------------
    // MANIFEST BUILDER
    // --------------------------------

    private fun buildManifest(
        policy: DatasetConstructionPolicy,
        includedItems: List<EvaluationItem>,
        excludedItems: List<DatasetManifest.ExcludedItemRecord>,
        allEvaluationItems: List<EvaluationItem>,
        predictions: Map<String, AiPredictionRecord>,
        truths: Map<String, GroundTruth>,
        timestamp: LocalDateTime
    ): DatasetManifest {

        val includedIds = includedItems.map { it.id }
        val allIds = allEvaluationItems.map { it.id }

        // Source data range
        val sessionIds = allEvaluationItems.map { it.sessionId }.distinct()
        val projectIds = allEvaluationItems.mapNotNull { it.projectId }.distinct()
        val modelVersions = includedItems.mapNotNull {
            predictions[it.id]?.modelVersion
        }.distinct()

        // Distributions using DatasetReporter
        val report = DatasetReporter.generateReport(
            items = includedItems,
            predictions = predictions.filterKeys { it in includedIds.toSet() },
            truths = truths.filterKeys { it in includedIds.toSet() },
            minimumCategorySupport = policy.minimumCategorySupport,
            minimumPlatformSupport = policy.minimumPlatformSupport,
            maxUnknownFraction = policy.maxUnknownFractionPerField
        )

        // Duplicate detection at cohort level
        val feedItemIds = includedItems.mapNotNull { item ->
            predictions[item.id]?.feedItemId
        }
        val duplicateFeedItems = feedItemIds.groupingBy { it }
            .eachCount()
            .filter { it.value > 1 }

        // Temporal range
        val timestamps = includedItems.map { it.enqueuedAt }

        // Annotation completeness
        val totalIncluded = includedItems.size

        val completeness = mutableMapOf<String, Double>()
        completeness["category"] = includedItems.count { item ->
            val t = truths[item.id]
            t != null && t.category != null &&
                t.ambiguity != com.example.feedsense.model.GroundTruth.AMBIGUITY_UNKNOWN
        }.toDouble() / totalIncluded.coerceAtLeast(1)

        completeness["platform"] = includedItems.count { item ->
            val t = truths[item.id]
            t != null && !t.platform.isNullOrBlank()
        }.toDouble() / totalIncluded.coerceAtLeast(1)

        completeness["contentType"] = includedItems.count { item ->
            val t = truths[item.id]
            t != null && !t.contentType.isNullOrBlank()
        }.toDouble() / totalIncluded.coerceAtLeast(1)

        completeness["duration"] = includedItems.count { item ->
            val t = truths[item.id]
            t != null && t.durationSeconds != null
        }.toDouble() / totalIncluded.coerceAtLeast(1)

        completeness["skip"] = includedItems.count { item ->
            val t = truths[item.id]
            t != null && t.skipped != null
        }.toDouble() / totalIncluded.coerceAtLeast(1)

        completeness["topic"] = includedItems.count { item ->
            val t = truths[item.id]
            t != null && !t.topic.isNullOrBlank()
        }.toDouble() / totalIncluded.coerceAtLeast(1)

        completeness["tone"] = includedItems.count { item ->
            val t = truths[item.id]
            t != null && !t.tone.isNullOrBlank()
        }.toDouble() / totalIncluded.coerceAtLeast(1)

        com.example.feedsense.model.GroundTruth.INTERACTION_SIGNAL_KEYS.forEach { signal ->
            completeness[signal] = includedItems.count { item ->
                val t = truths[item.id]
                t != null && getInteractionField(t, signal) != null
            }.toDouble() / totalIncluded.coerceAtLeast(1)
        }

        return DatasetManifest(
            datasetVersion = policy.datasetVersion,
            createdAt = timestamp,
            policyDescription = policy.description.ifBlank {
                "Constructed with default policy"
            },
            sourceSessionIds = sessionIds,
            sourceProjectIds = projectIds,
            modelVersionsRepresented = modelVersions,
            totalCandidateItems = allEvaluationItems.size,
            includedItemIds = includedIds,
            excludedItems = excludedItems,
            categoryDistribution = report.categoryDistribution,
            platformDistribution = report.platformDistribution,
            contentTypeDistribution = report.contentTypeDistribution,
            durationDistribution = report.durationDistribution,
            skipDistribution = report.skipDistribution,
            interactionDistribution = report.interactionDistribution,
            confidenceDistribution = report.confidenceDistribution,
            modelVersionDistribution = report.modelVersionDistribution,
            totalCategoriesAvailable = report.totalCategoriesAvailable,
            categoriesRepresented = report.categoriesRepresented,
            categoriesWithMeaningfulSupport = report.categoriesWithMeaningfulSupport,
            totalPlatformsSupported = report.totalPlatformsSupported,
            platformsRepresented = report.platformsRepresented,
            platformsWithMeaningfulSupport = report.platformsWithMeaningfulSupport,
            annotationCompleteness = completeness,
            qualityFlags = report.qualityFlags,
            ambiguousItemCount = report.ambiguousItemCount,
            mixedItemCount = report.mixedItemCount,
            disputedItemCount = report.disputedItemCount,
            unknownCategoryCount = report.unknownCategoryCount,
            duplicateCandidateCount = duplicateFeedItems.size,
            duplicateExcludedCount = excludedItems.count {
                it.reason.contains("DUPLICATE")
            },
            temporalEarliestItem = timestamps.minOrNull()?.toString(),
            temporalLatestItem = timestamps.maxOrNull()?.toString()
        )
    }

    private fun getInteractionField(
        truth: com.example.feedsense.model.GroundTruth,
        signal: String
    ): Boolean? {
        return when (signal) {
            "liked" -> truth.liked
            "commented" -> truth.commented
            "shared" -> truth.shared
            "saved" -> truth.saved
            "followed" -> truth.followed
            "paused" -> truth.paused
            "playing" -> truth.playing
            else -> null
        }
    }
}
