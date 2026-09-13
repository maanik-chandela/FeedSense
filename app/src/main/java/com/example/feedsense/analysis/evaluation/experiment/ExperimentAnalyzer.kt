package com.example.feedsense.analysis.evaluation.experiment

import com.example.feedsense.analysis.evaluation.comparative.PairedPrediction
import com.example.feedsense.analysis.evaluation.comparative.PairedStats

/**
 * Milestone 8B-9.
 *
 * Experiment-level aggregation on top of the reused 8B-8 comparative
 * layer.
 *
 * Computes the experiment's own five-way outcome tally, the
 * McNemar-ready paired contingency (baselineCorrect /
 * evidenceAwareCorrect / discordant), and the real-data status flag.
 *
 * The bulk of the comparative work (overall metrics, per-category,
 * confusion / difference matrices, stratification, root cause) is
 * delegated to the existing 8B-8 [com.example.feedsense.analysis.evaluation.comparative.ComparativeEvaluator]
 * via its [com.example.feedsense.analysis.evaluation.comparative.ComparativeReport];
 * this class only adds the experiment-specific surface.
 */
object ExperimentAnalyzer {

    /** Five-way outcome tally for the experiment population. */
    data class OutcomeTally(
        val paired: Int,
        val eligible: Int,
        val bothCorrect: Int,
        val baselineOnlyCorrect: Int,
        val evidenceAwareOnlyCorrect: Int,
        val bothWrong: Int,
        val incomplete: Int
    ) {
        val discordantPairs: Int
            get() = baselineOnlyCorrect + evidenceAwareOnlyCorrect

        val evidenceAwareImprovements: Int
            get() = evidenceAwareOnlyCorrect

        val evidenceAwareRegressions: Int
            get() = baselineOnlyCorrect
    }

    /** McNemar-ready paired contingency in experiment vocabulary. */
    data class McNemarReady(
        val baselineCorrect: Int,
        val evidenceAwareCorrect: Int,
        val baselineOnlyCorrect: Int,
        val evidenceAwareOnlyCorrect: Int,
        val discordantPairs: Int,
        val pValue: Double?,
        val sufficient: Boolean,
        val guardReason: String?,
        val direction: String
    )

    /**
     * Tally of the five-way outcomes over the paired population,
     * including non-eligible observers as INCOMPLETE.
     */
    fun tally(pairs: List<PairedPrediction>): OutcomeTally {
        var eligible = 0
        var bothCorrect = 0
        var baselineOnly = 0
        var eaOnly = 0
        var bothWrong = 0
        var incomplete = 0
        for (p in pairs) {
            if (!p.eligibleForAccuracy) {
                incomplete++
                continue
            }
            eligible++
            when (EvidenceAwareOutcome.of(p.outcome)) {
                EvidenceAwareOutcome.BOTH_CORRECT -> bothCorrect++
                EvidenceAwareOutcome.BASELINE_ONLY_CORRECT ->
                    baselineOnly++
                EvidenceAwareOutcome.EVIDENCE_AWARE_ONLY_CORRECT ->
                    eaOnly++
                EvidenceAwareOutcome.BOTH_WRONG -> bothWrong++
                EvidenceAwareOutcome.INCOMPLETE -> incomplete++
            }
        }
        return OutcomeTally(
            paired = pairs.size,
            eligible = eligible,
            bothCorrect = bothCorrect,
            baselineOnlyCorrect = baselineOnly,
            evidenceAwareOnlyCorrect = eaOnly,
            bothWrong = bothWrong,
            incomplete = incomplete
        )
    }

    /**
     * Produces the McNemar-ready contingency. The McNemar statistic
     * itself is delegated to the reused 8B-8 [PairedStats] so the
     * exact binomial test and its guards match the comparative layer
     * exactly.
     */
    fun mcnemarReady(
        tally: OutcomeTally,
        minimumDiscordant: Int
    ): McNemarReady {
        val m = PairedStats.mcnemar(
            baselineOnlyCorrect = tally.baselineOnlyCorrect,
            eightBOnlyCorrect = tally.evidenceAwareOnlyCorrect,
            minimumDiscordant = minimumDiscordant
        )
        val direction = PairedStats.direction(
            baselineOnlyCorrect = tally.baselineOnlyCorrect,
            eightBOnlyCorrect = tally.evidenceAwareOnlyCorrect,
            sufficient = m.sufficient
        )
        return McNemarReady(
            baselineCorrect =
                tally.bothCorrect + tally.baselineOnlyCorrect,
            evidenceAwareCorrect =
                tally.bothCorrect + tally.evidenceAwareOnlyCorrect,
            baselineOnlyCorrect = tally.baselineOnlyCorrect,
            evidenceAwareOnlyCorrect = tally.evidenceAwareOnlyCorrect,
            discordantPairs = m.discordantPairs,
            pValue = m.pValue,
            sufficient = m.sufficient,
            guardReason = m.guardReason,
            direction = direction
        )
    }

    /**
     * Deterministic "is there enough REAL data" flag for the
     * experiment. Mirrors the 8B-8 comparative guard: with fewer
     * eligible paired observations than required for an effect size,
     * no comparative conclusion may be claimed.
     */
    fun realDataSufficient(
        eligible: Int,
        minimumForEffectSize: Int
    ): Boolean = eligible >= minimumForEffectSize
}
