package com.example.feedsense.analysis.evaluation.comparative

/*
 * Milestone 8B-8.
 *
 * Stratified comparison.
 *
 * Re-runs the comparative metrics within well-defined subgroups
 * of the SAME eligible population. Cells with fewer observations
 * than the config minimum are surfaced as INSUFFICIENT_SUPPORT
 * rather than reported as a meaningful number.
 *
 * Stratifications supported (see Stratifications.Keys):
 *   duration, platform, contentType, evidenceCoverage, hasOcr,
 *   temporalConflict, representativeFrameOutlier,
 *   eightBDecisionState.
 */
object StratifiedComparison {

    data class StratumCell(
        val key: String,
        val pairs: Int,
        val eligible: Int,
        val sufficient: Boolean,
        val metrics: ComparativeMetrics.BothSystems?,
        val tally: ComparativeMetrics.OutcomeTally?,
        val mcnemar: PairedStats.McNemarResult?
    )

    /**
     * Computes a stratified table for a key extractor over the
     * same eligible population.
     */
    fun byKey(
        pairs: List<PairedPrediction>,
        keyExtractor: (PairedPrediction) -> String,
        config: ComparativeConfig
    ): Map<String, StratumCell> {
        val eligible = pairs.filter { it.eligibleForAccuracy }
        val grouped = eligible.groupBy(keyExtractor)
        val result = linkedMapOf<String, StratumCell>()
        for ((key, cellPairs) in grouped.toSortedMap()) {
            val sufficient = cellPairs.size >=
                config.minimumStratumSupport
            result[key] = StratumCell(
                key = key,
                pairs = cellPairs.size,
                eligible = cellPairs.size,
                sufficient = sufficient,
                metrics = if (sufficient) {
                    ComparativeMetrics.bothSystems(
                        cellPairs, config.minimumForConfidenceInterval
                    )
                } else null,
                tally = if (sufficient) {
                    ComparativeMetrics.tally(cellPairs)
                } else null,
                mcnemar = if (sufficient) {
                    PairedStats.mcnemar(
                        baselineOnlyCorrect =
                            ComparativeMetrics.tally(cellPairs)
                                .baselineOnlyCorrect,
                        eightBOnlyCorrect =
                            ComparativeMetrics.tally(cellPairs)
                                .eightBOnlyCorrect,
                        minimumDiscordant =
                            config.minimumMcNemarDiscordantPairs
                    )
                } else null
            )
        }
        return result
    }

    data class Table(
        val duration: Map<String, StratumCell>,
        val platform: Map<String, StratumCell>,
        val contentType: Map<String, StratumCell>,
        val evidenceCoverage: Map<String, StratumCell>,
        val hasOcr: Map<String, StratumCell>,
        val temporalConflict: Map<String, StratumCell>,
        val representativeFrameOutlier: Map<String, StratumCell>,
        val eightBDecisionState: Map<String, StratumCell>
    )

    fun all(
        pairs: List<PairedPrediction>,
        config: ComparativeConfig
    ): Table {
        fun k(extractor: (PairedPrediction) -> String): Map<String, StratumCell> =
            byKey(pairs, extractor, config)

        if (pairs.isEmpty()) {
            return Table(
                duration = emptyMap(), platform = emptyMap(),
                contentType = emptyMap(), evidenceCoverage = emptyMap(),
                hasOcr = emptyMap(), temporalConflict = emptyMap(),
                representativeFrameOutlier = emptyMap(),
                eightBDecisionState = emptyMap()
            )
        }
        return Table(
            duration = k { Stratifications.durationBucket(it.truth).label },
            platform = k { Stratifications.platform(it.truth) },
            contentType = k { Stratifications.contentType(it.truth) },
            evidenceCoverage = k { Stratifications.evidenceCoverage(it.eightBDecision) },
            hasOcr = k { Stratifications.hasOcrEvidence(it.eightBDecision) },
            temporalConflict = k { Stratifications.temporalConflict(it.eightBDecision) },
            representativeFrameOutlier =
                k { Stratifications.representativeFrameOutlier(it.eightBDecision) },
            eightBDecisionState =
                k { Stratifications.eightBDecisionState(it.eightBDecision) }
        )
    }
}
