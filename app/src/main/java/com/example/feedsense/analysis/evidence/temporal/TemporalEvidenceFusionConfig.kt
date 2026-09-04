package com.example.feedsense.analysis.evidence.temporal

/*
 * Milestone 8B-6.
 *
 * Temporal evidence fusion configuration.
 *
 * Centralized, frozen configuration for the 8B-6
 * temporal evidence fusion engine. Every tunable
 * parameter is:
 *   - named
 *   - documented
 *   - versioned
 *   - covered by tests
 *
 * Design:
 *   - Frozen: no automatic tuning
 *   - Deterministic: same config → same results
 *   - Versioned: historical predictions reproducible
 *
 * Important:
 *   - Weights are heuristic, not learned
 *   - No automatic threshold optimization
 *   - Thresholds must be justified by research rationale
 *   - Changes bump CONFIG_VERSION
 */
data class TemporalEvidenceFusionConfig(
    // --------------------------------
    // VERSIONING
    // --------------------------------

    val engineVersion: String = ENGINE_VERSION,
    val configVersion: String = CONFIG_VERSION,

    // --------------------------------
    // TEMPORAL WEIGHTING
    // --------------------------------

    /*
     * Weight for evidence quality in the support score.
     * Higher quality evidence contributes more to
     * category support.
     */
    val qualityWeight: Double = 0.3,

    /*
     * Weight for temporal persistence in the support
     * score. Evidence that persists across time is
     * stronger than transient evidence.
     */
    val persistenceWeight: Double = 0.25,

    /*
     * Weight for temporal coverage in the support score.
     * Better coverage means more reliable reasoning.
     */
    val coverageWeight: Double = 0.2,

    /*
     * Weight for evidence source diversity. Multiple
     * independent source types supporting a category
     * is stronger.
     */
    val diversityWeight: Double = 0.15,

    /*
     * Weight for temporal recency. More recent evidence
     * within the item may be slightly more representative.
     */
    val recencyWeight: Double = 0.1,

    // --------------------------------
    // PERSISTENCE HANDLING
    // --------------------------------

    /*
     * Maximum persistence boost factor. Repeated
     * identical evidence from the same extractor is
     * persistent but NOT fully independent. This factor
     * limits how much persistence can boost a category.
     */
    val maxPersistenceBoost: Double = 1.5,

    /*
     * Persistence dampening: repeated evidence from the
     * same identity hash gets diminishing returns.
     * The Nth occurrence contributes:
     *   1 / (persistenceDampening * N)
     */
    val persistenceDampening: Double = 2.0,

    // --------------------------------
    // CONTRADICTION HANDLING
    // --------------------------------

    /*
     * Penalty factor applied to a category's support
     * when contradicting evidence exists.
     */
    val contradictionPenalty: Double = 0.3,

    // --------------------------------
    // AMBIGUITY DETECTION
    // --------------------------------

    /*
     * When the top two category support scores are
     * within this ratio, the item is AMBIGUOUS.
     */
    val ambiguityGapRatio: Double = 0.15,

    // --------------------------------
    // INSUFFICIENT EVIDENCE
    // --------------------------------

    /*
     * Minimum number of unique evidence identities
     * required for a confident prediction.
     */
    val minUniqueIdentities: Int = 2,

    /*
     * Minimum coverage ratio required for a confident
     * prediction. Below this, coverage is insufficient.
     */
    val minCoverageForConfidence: Double = 0.1,

    // --------------------------------
    // TRANSITION DETECTION
    // --------------------------------

    /*
     * Minimum number of consecutive evidence points
     * with a new category before a transition is
     * flagged.
     */
    val transitionMinConsecutive: Int = 2,

    /*
     * Gap duration (ms) above which adjacent evidence
     * is considered to be from different temporal
     * segments, not a smooth transition.
     */
    val transitionGapThresholdMs: Long = 5000L,

    // --------------------------------
    // CATEGORY TRANSITION EXPOSURE
    // --------------------------------

    /*
     * Minimum duration (ms) a secondary category must
     * persist before it is exposed as a possible
     * internal transition.
     */
    val internalTransitionMinDurationMs: Long = 1000L,

    // --------------------------------
    // CONFLICT LEVEL THRESHOLDS
    // --------------------------------

    /*
     * When the contradicting category support exceeds
     * this ratio of the leading category support, the
     * conflict level is HIGH.
     */
    val conflictHighRatio: Double = 0.6,

    /*
     * When the contradicting category support exceeds
     * this ratio of the leading category support, the
     * conflict level is MEDIUM.
     */
    val conflictMediumRatio: Double = 0.3,

    // --------------------------------
    // COVERAGE STATE THRESHOLDS
    // --------------------------------

    /*
     * Coverage ratio above which evidence coverage is
     * considered HIGH for prediction purposes.
     */
    val effectiveCoverageHigh: Double = 0.7,

    /*
     * Coverage ratio above which evidence coverage is
     * considered PARTIAL.
     */
    val effectiveCoveragePartial: Double = 0.3
) {
    companion object {

        const val ENGINE_VERSION = "temporal-fusion-v1"
        const val CONFIG_VERSION = "temporal-fusion-config-v1"

        val DEFAULT =
            TemporalEvidenceFusionConfig()

        val CONSERVATIVE =
            TemporalEvidenceFusionConfig(
                qualityWeight = 0.35,
                persistenceWeight = 0.20,
                coverageWeight = 0.25,
                diversityWeight = 0.15,
                recencyWeight = 0.05,
                maxPersistenceBoost = 1.3,
                persistenceDampening = 3.0,
                contradictionPenalty = 0.4,
                ambiguityGapRatio = 0.20,
                minUniqueIdentities = 3,
                minCoverageForConfidence = 0.15
            )

        val EXPERIMENTAL =
            TemporalEvidenceFusionConfig(
                qualityWeight = 0.25,
                persistenceWeight = 0.30,
                coverageWeight = 0.20,
                diversityWeight = 0.15,
                recencyWeight = 0.10,
                maxPersistenceBoost = 1.5,
                persistenceDampening = 2.0,
                contradictionPenalty = 0.3,
                ambiguityGapRatio = 0.12,
                minUniqueIdentities = 2,
                minCoverageForConfidence = 0.08
            )
    }

    init {
        require(qualityWeight >= 0.0) {
            "qualityWeight must be non-negative"
        }
        require(persistenceWeight >= 0.0) {
            "persistenceWeight must be non-negative"
        }
        require(coverageWeight >= 0.0) {
            "coverageWeight must be non-negative"
        }
        require(diversityWeight >= 0.0) {
            "diversityWeight must be non-negative"
        }
        require(recencyWeight >= 0.0) {
            "recencyWeight must be non-negative"
        }
        require(ambiguityGapRatio in 0.0..1.0) {
            "ambiguityGapRatio must be in [0, 1]"
        }
        require(conflictHighRatio in 0.0..2.0) {
            "conflictHighRatio must be in [0, 2]"
        }
        require(conflictMediumRatio in 0.0..2.0) {
            "conflictMediumRatio must be in [0, 2]"
        }
    }
}
