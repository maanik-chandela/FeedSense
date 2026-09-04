package com.example.feedsense.analysis.fusion.temporal

// --------------------------------
// TEMPORAL CONFIG (Milestone 8B-2)
// --------------------------------
//
// Centralized configuration for the temporal evidence fusion
// model. Every tunable threshold is named, documented, versioned
// and has a rationale. There are no magic numbers scattered
// through the decision logic (8B-2 section 84/85).
//
// This configuration is FROZEN: predictions are reproducible
// from (modelVersion + configVersion + input evidence). Any
// change to thresholds bumps the config version so historical
// predictions are never silently reprocessed (8B-2 section 83).

data class TemporalConfig(
    // --------------------------------
    // MODEL VERSIONING (8B-2 section 82/83)
    // --------------------------------

    val modelVersion: String = MODEL_VERSION,
    val configVersion: String = CONFIG_VERSION,

    // --------------------------------
    // TEMPORAL WINDOW (8B-2 section 7)
    // --------------------------------

    /**
     * Maximum duration (seconds) to consider within one
     * FeedItem. Frames beyond this are ignored for temporal
     * reasoning. Prevents unbounded memory for very long content.
     */
    val maxTemporalWindowSeconds: Double = 120.0,

    // --------------------------------
    // FRAME INTERVALS (8B-2 section 10)
    // --------------------------------

    /**
     * Maximum gap (seconds) between two consecutive frames
     * before the second frame is treated as a new temporal
     * segment rather than a continuation.
     */
    val maxFrameGapSeconds: Double = 15.0,

    /**
     * Minimum informative duration (seconds) below which
     * temporal persistence is flagged as very short.
     */
    val minInformativeDurationSeconds: Double = 0.5,

    // --------------------------------
    // PERSISTENCE (8B-2 section 11)
    // --------------------------------

    /**
     * Duration (seconds) of continuous evidence support
     * required for STRONG temporal persistence.
     */
    val strongPersistenceDurationSeconds: Double = 3.0,

    /**
     * Duration (seconds) of continuous evidence support
     * required for MODERATE temporal persistence.
     */
    val moderatePersistenceDurationSeconds: Double = 1.0,

    // --------------------------------
    // DUPLICATE REDUCTION (8B-2 section 21)
    // --------------------------------

    /**
     * Minimum similarity ratio between two OCR texts for them
     * to be considered the same evidence. E.g. 0.5 means 50%
     * token overlap.
     */
    val ocrDuplicateSimilarityThreshold: Double = 0.5,

    // --------------------------------
    // OCR EVOLUTION (8B-2 section 22)
    // --------------------------------

    /**
     * When a new OCR text starts with a prefix of an existing
     * text (or vice versa), they are considered evolutionary
     * stages of the same observation rather than separate
     * evidence.
     */
    val ocrEvolutionPrefixRatio: Double = 0.7,

    // --------------------------------
    // TRANSITION DETECTION (8B-2 section 16/26)
    // --------------------------------

    /**
     * Fraction of informative frames that must disagree with
     * the leading category before a transition is flagged.
     */
    val transitionDisagreementRatio: Double = 0.4,

    /**
     * Minimum number of consecutive frames with a new category
     * before it is considered a true transition rather than a
     * transient overlay.
     */
    val transitionMinConsecutiveFrames: Int = 2,

    // --------------------------------
    // TEMPORAL CONSISTENCY (8B-2 section 13)
    // --------------------------------

    /**
     * Ratio of informative frames supporting the leading
     * category required for STRONG consistency.
     */
    val strongConsistencyRatio: Double = 0.8,

    /**
     * Ratio of informative frames supporting the leading
     * category required for MODERATE consistency.
     */
    val moderateConsistencyRatio: Double = 0.5,

    // --------------------------------
    // AMBIGUITY / CONFLICT (8B-2 section 50/51)
    // --------------------------------

    /**
     * When the top two categories are within this ratio of
     * each other, the result is AMBIGUOUS.
     */
    val ambiguityGapRatio: Double = 0.15,

    /**
     * Minimum combined support needed to avoid
     * INSUFFICIENT_EVIDENCE.
     */
    val minEvidenceMass: Double = 0.20,

    // --------------------------------
    // CONFIDENCE BANDS
    // --------------------------------

    val bandHigh: Double = 0.7,
    val bandMedium: Double = 0.45,
    val bandLow: Double = 0.2,

    // --------------------------------
    // REPRESENTATIVE FRAME (8B-2 section 28/29)
    // --------------------------------

    /**
     * Minimum agreement ratio between the representative frame
     * and the temporal evidence before it is flagged as an
     * outlier.
     */
    val representativeFrameOutlierThreshold: Double = 0.3,

    // --------------------------------
    // VISUAL FINGERPRINT (8B-2 section 18)
    // --------------------------------

    /**
     * Similarity threshold below which two frames are
     * considered visually different (indicating a possible
     * content boundary).
     */
    val fingerprintChangeThreshold: Double = 0.6,

    // --------------------------------
    // SHORT CONTENT (8B-2 section 33)
    // --------------------------------

    /**
     * Duration (seconds) below which content is considered
     * "very short". Very short content still gets classified
     * but with reduced temporal confidence.
     */
    val veryShortContentThresholdSeconds: Double = 3.0,

    // --------------------------------
    // MEMORY BOUNDS (8B-2 section 35)
    // --------------------------------

    /**
     * Maximum number of frame evidence summaries to retain
     * in memory during processing. Older frames are compressed
     * into temporal summaries.
     */
    val maxInMemoryFrameSummaries: Int = 50,

    // --------------------------------
    // MULTI-CONTENT (8B-2 section 25)
    // --------------------------------

    /**
     * Minimum evidence ratio for a secondary category to be
     * reported alongside the primary.
     */
    val secondaryCategoryMinRatio: Double = 0.25
) {

    companion object {
        // Follows the existing naming convention local-<model>.
        // Temporal V1 is the next generation after fusion-v1.
        const val MODEL_VERSION = "local-fusion-v1-temporal"
        const val CONFIG_VERSION = "temporal-config-v1"

        /**
         * The frozen default configuration for 8B-2.
         */
        val DEFAULT: TemporalConfig = TemporalConfig()
    }
}
