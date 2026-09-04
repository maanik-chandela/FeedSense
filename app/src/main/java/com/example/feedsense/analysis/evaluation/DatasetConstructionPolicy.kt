package com.example.feedsense.analysis.evaluation

import com.example.feedsense.model.EvaluationItem
import com.example.feedsense.model.GroundTruth

// --------------------------------
// DATASET CONSTRUCTION POLICY (Milestone 8A-4)
// --------------------------------
//
// Defines the rules for constructing a curated evaluation
// dataset from raw research data. This is a pure data class
// that captures every tunable decision in one place so
// construction is deterministic, reproducible, and
// auditable.
//
// Key distinction:
//   - RAW RESEARCH DATA = everything in the evaluation tables
//   - EVALUATION DATASET = a curated snapshot selected by
//     these rules
//
// Raw data is NEVER deleted when it fails inclusion.
// Items are marked with exclusion reasons.

data class DatasetConstructionPolicy(

    // --------------------------------
    // VERSION IDENTITY
    // --------------------------------

    /**
     * Human-readable dataset version label.
     * Examples: "evaluation-v1", "evaluation-v2".
     * Must be unique across dataset constructions.
     */
    val datasetVersion: String,

    // --------------------------------
    // STATUS FILTERING
    // --------------------------------

    /**
     * Which evaluation statuses are candidates for inclusion.
     * NOT_EVALUATED is never a candidate.
     */
    val candidateStatuses: Set<String> = setOf(
        EvaluationItem.STATUS_EVALUATED,
        EvaluationItem.STATUS_DISPUTED
    ),

    /**
     * Whether DISPUTED items are included in the dataset.
     * When false, disputed items are EXCLUDED_DISPUTED.
     */
    val includeDisputed: Boolean = false,

    // --------------------------------
    // DUPLICATE FILTERING
    // --------------------------------

    /**
     * Whether to detect and exclude duplicate candidates
     * based on feed item ID, frame fingerprint, or
     * session+category+duration proximity.
     *
     * When false, duplicate detection is skipped and all
     * candidates pass through (documented limitation).
     */
    val enableDuplicateDetection: Boolean = true,

    // --------------------------------
    // UNKNOWN VALUE HANDLING
    // --------------------------------

    /**
     * Maximum allowed fraction of UNKNOWN values for a
     * field before a quality warning is emitted.
     * Range: 0.0 (zero tolerance) to 1.0 (any amount ok).
     */
    val maxUnknownFractionPerField: Double = 0.5,

    // --------------------------------
    // CAPABILITY-SPECIFIC MINIMUM SUPPORT
    // --------------------------------

    /**
     * Minimum number of examples a category must have to
     * be considered "meaningfully supported". Categories
     * below this threshold generate a LOW_CATEGORY_SUPPORT
     * quality warning.
     */
    val minimumCategorySupport: Int = 5,

    /**
     * Minimum number of examples a platform must have to
     * be considered "meaningfully supported".
     */
    val minimumPlatformSupport: Int = 3,

    // --------------------------------
    // DETERMINISTIC ORDERING
    // --------------------------------

    /**
     * Stable ordering key for items within the dataset.
     * Used to ensure deterministic dataset construction:
     * the same source state + config always produces the
     * same dataset membership and ordering.
     *
     * Default: session start time, then item enqueue time,
     * then item ID.
     */
    val ordering: List<OrderKey> = listOf(
        OrderKey.SESSION_START,
        OrderKey.ITEM_ENQUEUE,
        OrderKey.ITEM_ID
    ),

    // --------------------------------
    // AMBIGUITY HANDLING
    // --------------------------------

    /**
     * Whether AMBIGUOUS items are included. Ambiguous items
     * are scientifically valuable for measuring performance
     * on difficult content.
     */
    val includeAmbiguous: Boolean = true,

    /**
     * Whether MIXED items are included.
     */
    val includeMixed: Boolean = true,

    // --------------------------------
    // EXPORT
    // --------------------------------

    /**
     * Whether to expose opaque IDs only (no filesystem
     * paths, no PII) in export representations.
     */
    val privacyPreservingExport: Boolean = true,

    // --------------------------------
    // METADATA
    // --------------------------------

    /**
     * Optional description recorded in the manifest.
     */
    val description: String = ""
) {

    enum class OrderKey {
        SESSION_START,
        SESSION_ID,
        ITEM_ENQUEUE,
        ITEM_START,
        ITEM_ID,
        PROJECT_ID
    }

    companion object {

        /**
         * Default policy for first dataset construction.
         */
        fun default(version: String) = DatasetConstructionPolicy(
            datasetVersion = version,
            description = "Default dataset construction policy"
        )
    }
}
