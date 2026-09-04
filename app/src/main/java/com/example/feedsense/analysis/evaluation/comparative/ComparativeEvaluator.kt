package com.example.feedsense.analysis.evaluation.comparative

import java.time.LocalDateTime
import java.util.UUID

/*
 * Milestone 8B-8.
 *
 * Comparative evaluator (orchestrator).
 *
 * Pure, deterministic orchestrator: given the SAME eligible set of
 * PairedPredictions plus frozen version metadata, it assembles the
 * full ComparativeReport.
 *
 * It DOES NOT query the database, retrain anything, or modify the
 * baseline. It consumes already-assembled pairs (see
 * PairedPredictionBuilder for DB assembly) and returns immutable
 * results.
 */
object ComparativeEvaluator {

    data class Input(
        val pairs: List<PairedPrediction>,
        val config: ComparativeConfig = ComparativeConfig.DEFAULT,
        val reportId: String =
            "cmp-${UUID.randomUUID().toString()}",
        val createdAt: LocalDateTime = LocalDateTime.now(),
        val datasetVersion: String? = null,
        val baselineModelVersion: String? = null,
        val fusionVersion: String? = null,
        val decisionVersion: String? = null
    )

    /**
     * Deterministic "is there enough REAL data" flag.
     *
     * The user directive is strict: with no (or negligible) real
     * paired data, DO NOT claim a comparative conclusion. This is
     * derived purely from the eligible population and the McNemar
     * guard.
     */
    private fun realDataSufficient(input: Input): Boolean {
        val eligible = input.pairs.count { it.eligibleForAccuracy }
        return eligible >= input.config.minimumForEffectSize
    }

    fun evaluate(input: Input): ComparativeReport {
        val config = input.config
        val pairs = input.pairs.sortedBy { it.item.id }

        val eligibleCount = pairs.count { it.eligibleForAccuracy }

        val outcome = ComparativeMetrics.tally(pairs)
        val overall = ComparativeMetrics.bothSystems(
            pairs, config.minimumForConfidenceInterval
        )

        val mcnemar = PairedStats.mcnemar(
            baselineOnlyCorrect = outcome.baselineOnlyCorrect,
            eightBOnlyCorrect = outcome.eightBOnlyCorrect,
            minimumDiscordant = config.minimumMcNemarDiscordantPairs
        )

        val effect = PairedStats.effectSize(
            baselineCorrect = overall.baseline.correct,
            eightBCorrect = overall.eightB.correct,
            totalEligible = overall.eligible,
            baselineOnlyCorrect = outcome.baselineOnlyCorrect,
            eightBOnlyCorrect = outcome.eightBOnlyCorrect,
            minimumForEffect = config.minimumForEffectSize
        )

        val perCatBase = ComparativeMetrics.perCategory(
            pairs, true, config.minimumClassSupport
        )
        val perCatEight = ComparativeMetrics.perCategory(
            pairs, false, config.minimumClassSupport
        )

        val baseConfusion = TransitionMatrices.classConfusion(pairs, true)
        val eightConfusion = TransitionMatrices.classConfusion(pairs, false)
        val transition = TransitionMatrices.coverageTransition(pairs)

        val stratified = StratifiedComparison.all(pairs, config)

        val errorRoot = ErrorRootCauseComparison.compare(pairs)

        val conclusion = buildConclusion(
            input = input,
            outcome = outcome,
            mcnemar = mcnemar,
            effect = effect
        )

        return ComparativeReport(
            reportId = input.reportId,
            createdAt = input.createdAt,
            evaluationMethodVersion = ComparativeConfig.SCHEMA_VERSION,
            evaluationVersion = config.evaluationVersion,
            diagnosticVersion = config.diagnosticVersion,
            schemaVersion = ComparativeConfig.SCHEMA_VERSION,
            datasetVersion = input.datasetVersion,
            baselineModelVersion = input.baselineModelVersion,
            fusionVersion = input.fusionVersion,
            decisionVersion = input.decisionVersion,
            totalPaired = pairs.size,
            eligible = eligibleCount,
            ineligible = pairs.size - eligibleCount,
            outcome = outcome,
            overall = overall,
            mcnemar = mcnemar,
            effect = effect,
            perCategoryBaseline = perCatBase,
            perCategoryEightB = perCatEight,
            baselineConfusion = baseConfusion,
            eightBConfusion = eightConfusion,
            coverageTransition = transition,
            stratified = stratified,
            errorRootCause = errorRoot,
            conclusion = conclusion
        )
    }

    private fun buildConclusion(
        input: Input,
        outcome: ComparativeMetrics.OutcomeTally,
        mcnemar: PairedStats.McNemarResult,
        effect: PairedStats.EffectSizeResult
    ): ComparativeReport.Conclusion {
        val realEnough = realDataSufficient(input)
        val notes = mutableListOf<String>()

        if (!realEnough) {
            val headline =
                "INSUFFICIENT REAL DATA FOR COMPARATIVE " +
                    "CONCLUSIONS: eligible pairs " +
                    "${input.pairs.count { it.eligibleForAccuracy }} " +
                    "below required minimum ${input.config.minimumForEffectSize}."
            notes += headline
            notes += "Reported metrics are describing the supplied " +
                "inputs only and must NOT be read as a systemic claim."
            return ComparativeReport.Conclusion(
                verdict = ComparativeReport.Verdict
                    .INSUFFICIENT_REAL_DATA_FOR_COMPARATIVE_CONCLUSIONS,
                headline = headline,
                baselineAccuracy = effect.baselineAccuracy,
                eightBAccuracy = effect.eightBAccuracy,
                pValue = null,
                discordantPairs = mcnemar.discordantPairs,
                improvementEvidenceQualitativeOnly = false,
                notes = notes
            )
        }

        if (!mcnemar.sufficient) {
            val headline = "Paired discordant count " +
                "${mcnemar.discordantPairs} below minimum " +
                "${input.config.minimumMcNemarDiscordantPairs}; " +
                "directionally 8B improved " +
                "${outcome.eightBImprovements} vs regressed " +
                "${outcome.eightBRegressions} but NOT statistically " +
                "conclusive."
            notes += "McNemar guard not met; no p-value is reported."
            notes += "Qualitative (direction-only) evidence: " +
                "8B improvements ${outcome.eightBImprovements}, " +
                "8B regressions ${outcome.eightBRegressions}."
            return ComparativeReport.Conclusion(
                verdict = ComparativeReport.Verdict
                    .NO_SIGNIFICANT_DIFFERENCE,
                headline = headline,
                baselineAccuracy = effect.baselineAccuracy,
                eightBAccuracy = effect.eightBAccuracy,
                pValue = null,
                discordantPairs = mcnemar.discordantPairs,
                improvementEvidenceQualitativeOnly = true,
                notes = notes
            )
        }

        val p = mcnemar.pValue!!
        val sig = p < 0.05
        val improved = outcome.eightBImprovements
        val regressed = outcome.eightBRegressions

        val verdict = when {
            !sig -> ComparativeReport.Verdict.NO_SIGNIFICANT_DIFFERENCE
            improved > regressed -> ComparativeReport.Verdict.EIGHT_B_IMPROVED
            regressed > improved -> ComparativeReport.Verdict.BASELINE_SUPERIOR
            else -> ComparativeReport.Verdict.NO_SIGNIFICANT_DIFFERENCE
        }
        val headline = when (verdict) {
            ComparativeReport.Verdict.EIGHT_B_IMPROVED ->
                "8B evidence-aware decision improves correct " +
                    "primary-category prediction (p=${fmtP(p)})."
            ComparativeReport.Verdict.BASELINE_SUPERIOR ->
                "Baseline superior (p=${fmtP(p)}); " +
                    "8B regressions exceed improvements."
            else ->
                "No statistically significant difference at 95% " +
                    "(p=${fmtP(p)})."
        }
        notes += "McNemar exact, two-sided, on ${
            mcnemar.discordantPairs
        } discordant pairs."
        notes += "8B improvements ${outcome.eightBImprovements}, " +
            "regressions ${outcome.eightBRegressions}."
        return ComparativeReport.Conclusion(
            verdict = verdict,
            headline = headline,
            baselineAccuracy = effect.baselineAccuracy,
            eightBAccuracy = effect.eightBAccuracy,
            pValue = p,
            discordantPairs = mcnemar.discordantPairs,
            improvementEvidenceQualitativeOnly = false,
            notes = notes
        )
    }

    private fun fmtP(p: Double): String =
        "%.4f".format(p)
}
