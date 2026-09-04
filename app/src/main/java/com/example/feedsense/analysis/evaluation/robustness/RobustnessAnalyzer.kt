package com.example.feedsense.analysis.evaluation.robustness

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.analysis.evaluation.comparative.ComparativeConfig
import com.example.feedsense.analysis.evaluation.comparative.PairedPrediction
import com.example.feedsense.analysis.evaluation.comparative.PairedStats
import com.example.feedsense.analysis.evaluation.comparative.PairedStats.mcnemar
import java.time.LocalDateTime
import java.util.UUID

/*
 * Milestone 8B-9.
 *
 * Robustness / sensitivity analysis orchestrator.
 *
 * Pure, deterministic orchestrator: given PairedPredictions and
 * frozen RobustnessConfig, it assembles the full RobustnessReport.
 *
 * It DOES NOT:
 *   - retrain models
 *   - modify model weights
 *   - change classifier thresholds
 *   - change category taxonomy
 *   - change temporal fusion logic
 *   - change 8B-7 decision rules
 *   - modify SessionRepository
 *   - overwrite FeedItem
 *   - modify baseline predictions
 *
 * It consumes already-assembled PairedPredictions (same input
 * as 8B-8 ComparativeEvaluator) and returns an immutable report.
 *
 * The report sits ABOVE 8B-8: it reads the paired outcomes but
 * does not modify any layer below.
 */
object RobustnessAnalyzer {

    data class Input(
        val pairs: List<PairedPrediction>,
        val config: RobustnessConfig = RobustnessConfig.DEFAULT,
        val reportId: String = "rob-${UUID.randomUUID().toString()}",
        val createdAt: LocalDateTime = LocalDateTime.now(),
        val datasetVersion: String? = null,
        val baselineModelVersion: String? = null,
        val fusionVersion: String? = null,
        val decisionVersion: String? = null,
        val diagnosticVersion: String? = null
    )

    fun analyze(input: Input): RobustnessReport {
        val config = input.config
        val pairs = input.pairs.sortedBy { it.item.id }

        val eligiblePairs = pairs.filter { it.eligibleForAccuracy }
        val eligible = eligiblePairs.size

        val counts = PairedCounts.of(pairs)

        val primaryTest = PairedTestResult.fromCounts(
            counts = counts,
            role = AnalysisRole.PRIMARY_HYPOTHESIS,
            subgroupLabel = null,
            alpha = config.alpha,
            minimumDiscordant = config.minimumMcNemarDiscordantPairs,
            minimumForEffect = config.minimumForEffectSize
        )

        val effectSize = AccuracyDifference.fromCounts(
            counts, config.minimumForEffectSize
        )

        val ci = ConfidenceIntervalAnalysis.compute(
            baselineCorrect = counts.baselineCorrect,
            eightBCorrect = counts.eightBCorrect,
            totalEligible = counts.n,
            baselineOnlyCorrect = counts.baselineOnlyCorrect,
            eightBOnlyCorrect = counts.eightBOnlyCorrect,
            minimumForCI = config.minimumForConfidenceInterval
        )

        val bootstrap = BootstrapAnalysis.compute(pairs, config)

        val catSens = categorySensitivity(eligiblePairs, config)
        val loco = leaveOneCategoryOut(eligiblePairs, config)
        val platSens = platformSensitivity(eligiblePairs, config)
        val durSens = durationSensitivity(eligiblePairs, config)
        val evSens = evidenceSensitivity(eligiblePairs, config)
        val abstSens = AbstentionSensitivity.compute(
            eligiblePairs, config.minimumForEffectSize
        )
        val confSens = ConfidenceSensitivity.compute(eligiblePairs, config)

        val hcErrors = HighConfidenceError.compute(eligiblePairs, config)
        val imp = ImprovementAnalysis.compute(
            eligiblePairs, config.minimumForEffectSize
        )
        val reg = RegressionAnalysis.compute(
            eligiblePairs, config.minimumForEffectSize
        )
        val ec = ErrorConcentration.compute(
            eligiblePairs, config.minimumForEffectSize
        )

        val multipleComp = multipleComparisonMetadata(
            catSens, platSens, durSens, evSens, config
        )

        val realDataStatus = if (eligible < config.minimumForEffectSize) {
            StatisticalStatus.INSUFFICIENT_REAL_DATA_FOR_STATISTICAL_CONCLUSIONS
        } else "SUFFICIENT"

        val statStatus = when {
            eligible < config.minimumForEffectSize ->
                StatisticalStatus.INSUFFICIENT_DATA
            primaryTest.status == StatisticalStatus.SUFFICIENT &&
                primaryTest.significant == true ->
                StatisticalStatus.SUFFICIENT
            else -> StatisticalStatus.INSUFFICIENT_DATA
        }

        val conclusion = buildConclusion(
            primaryTest = primaryTest,
            effectSize = effectSize,
            bootstrap = bootstrap,
            abstSens = abstSens,
            config = config,
            eligible = eligible
        )

        return RobustnessReport(
            reportId = input.reportId,
            createdAt = input.createdAt,
            statisticalAnalysisVersion = config.statisticalAnalysisVersion,
            evaluationVersion = config.evaluationVersion,
            datasetVersion = input.datasetVersion,
            baselineModelVersion = input.baselineModelVersion,
            fusionVersion = input.fusionVersion,
            decisionVersion = input.decisionVersion,
            diagnosticVersion = input.diagnosticVersion,
            schemaVersion = RobustnessConfig.SCHEMA_VERSION,
            analysisSeed = config.analysisSeed,
            bootstrapIterations = config.bootstrapIterations,
            alpha = config.alpha,
            totalPaired = pairs.size,
            eligible = eligible,
            ineligible = pairs.size - eligible,
            primaryResult = primaryTest,
            effectSize = effectSize,
            confidenceIntervals = ci,
            bootstrap = bootstrap,
            categorySensitivity = catSens,
            leaveOneCategoryOut = loco,
            platformSensitivity = platSens,
            durationSensitivity = durSens,
            evidenceSensitivity = evSens,
            abstentionSensitivity = abstSens,
            confidenceSensitivity = confSens,
            highConfidenceErrors = hcErrors,
            improvements = imp,
            regressions = reg,
            errorConcentration = ec,
            multipleComparisonMethod = if (config.multipleComparisonBonferroni) "Bonferroni" else null,
            familyOfTests = multipleComp.familySize,
            adjustedAlpha = multipleComp.adjustedAlpha,
            realDataStatus = realDataStatus,
            statisticalStatus = statStatus.name,
            conclusion = conclusion
        )
    }

    // --------------------------------
    // CATEGORY SENSITIVITY
    // --------------------------------

    private fun categorySensitivity(
        pairs: List<PairedPrediction>,
        config: RobustnessConfig
    ): List<SubgroupResult> {
        val grouped = pairs.groupBy {
            CategoryCatalog.normalize(it.truth.category) ?: "UNKNOWN"
        }
        return grouped.toSortedMap().map { (cat, catPairs) ->
            val counts = PairedCounts.of(catPairs)
            val test = PairedTestResult.fromCounts(
                counts = counts,
                role = AnalysisRole.EXPLORATORY_ANALYSIS,
                subgroupLabel = cat,
                alpha = config.alpha,
                minimumDiscordant = config.minimumMcNemarDiscordantPairs,
                minimumForEffect = config.minimumCategorySupport
            )
            SubgroupResult.fromTest(test)
        }
    }

    // --------------------------------
    // LEAVE-ONE-CATEGORY-OUT
    // --------------------------------

    private fun leaveOneCategoryOut(
        pairs: List<PairedPrediction>,
        config: RobustnessConfig
    ): List<RobustnessReport.LeaveOneCategoryOutResult> {
        val categories = pairs
            .map { CategoryCatalog.normalize(it.truth.category) ?: "UNKNOWN" }
            .toSortedSet()
            .filter { cat ->
                pairs.count {
                    (CategoryCatalog.normalize(it.truth.category)
                        ?: "UNKNOWN") == cat
                } >= config.minimumCategorySupport
            }

        return categories.map { cat ->
            val remaining = pairs.filter {
                val c = CategoryCatalog.normalize(it.truth.category)
                    ?: "UNKNOWN"
                c != cat
            }
            val remainingEligible = remaining.count { it.eligibleForAccuracy }
            if (remainingEligible < config.minimumForEffectSize) {
                RobustnessReport.LeaveOneCategoryOutResult(
                    excludedCategory = cat,
                    remainingSupport = remaining.size,
                    remainingEligible = remainingEligible,
                    test = null,
                    accuracyDifference = null,
                    sufficient = false,
                    guardReason = "remaining_eligible($remainingEligible) < " +
                        "minimum(${config.minimumForEffectSize})"
                )
            } else {
                val counts = PairedCounts.of(remaining)
                val test = PairedTestResult.fromCounts(
                    counts = counts,
                    role = AnalysisRole.SECONDARY_ANALYSIS,
                    subgroupLabel = "without_$cat",
                    alpha = config.alpha,
                    minimumDiscordant = config.minimumMcNemarDiscordantPairs,
                    minimumForEffect = config.minimumForEffectSize
                )
                val accDiff = if (counts.n > 0) {
                    (counts.eightBCorrect.toDouble() / counts.n) -
                        (counts.baselineCorrect.toDouble() / counts.n)
                } else null
                RobustnessReport.LeaveOneCategoryOutResult(
                    excludedCategory = cat,
                    remainingSupport = remaining.size,
                    remainingEligible = remainingEligible,
                    test = test,
                    accuracyDifference = accDiff,
                    sufficient = true
                )
            }
        }
    }

    // --------------------------------
    // PLATFORM SENSITIVITY
    // --------------------------------

    private fun platformSensitivity(
        pairs: List<PairedPrediction>,
        config: RobustnessConfig
    ): List<SubgroupResult> {
        val grouped = pairs.groupBy { p ->
            p.truth.platform?.trim()?.takeIf { it.isNotEmpty() }
                ?: "UNKNOWN"
        }
        return grouped.toSortedMap().map { (plat, platPairs) ->
            val counts = PairedCounts.of(platPairs)
            val test = PairedTestResult.fromCounts(
                counts = counts,
                role = AnalysisRole.EXPLORATORY_ANALYSIS,
                subgroupLabel = plat,
                alpha = config.alpha,
                minimumDiscordant = config.minimumMcNemarDiscordantPairs,
                minimumForEffect = config.minimumSubgroupSupport
            )
            SubgroupResult.fromTest(test)
        }
    }

    // --------------------------------
    // DURATION SENSITIVITY
    // --------------------------------

    private fun durationSensitivity(
        pairs: List<PairedPrediction>,
        config: RobustnessConfig
    ): List<SubgroupResult> {
        val grouped = pairs.groupBy { p ->
            com.example.feedsense.analysis.evaluation.comparative
                .DurationBuckets.of(p.truth.durationSeconds).label
        }
        return grouped.toSortedMap().map { (dur, durPairs) ->
            val counts = PairedCounts.of(durPairs)
            val test = PairedTestResult.fromCounts(
                counts = counts,
                role = AnalysisRole.EXPLORATORY_ANALYSIS,
                subgroupLabel = dur,
                alpha = config.alpha,
                minimumDiscordant = config.minimumMcNemarDiscordantPairs,
                minimumForEffect = config.minimumSubgroupSupport
            )
            SubgroupResult.fromTest(test)
        }
    }

    // --------------------------------
    // EVIDENCE SENSITIVITY
    // --------------------------------

    private fun evidenceSensitivity(
        pairs: List<PairedPrediction>,
        config: RobustnessConfig
    ): List<SubgroupResult> {
        val results = mutableListOf<SubgroupResult>()

        // By evidence coverage state
        val byCoverage = pairs.groupBy { p ->
            p.eightBDecision?.evidenceCoverage?.name ?: "UNKNOWN"
        }
        for ((key, group) in byCoverage.toSortedMap()) {
            val counts = PairedCounts.of(group)
            val test = PairedTestResult.fromCounts(
                counts = counts,
                role = AnalysisRole.EXPLORATORY_ANALYSIS,
                subgroupLabel = "coverage_$key",
                alpha = config.alpha,
                minimumDiscordant = config.minimumMcNemarDiscordantPairs,
                minimumForEffect = config.minimumSubgroupSupport
            )
            results.add(SubgroupResult.fromTest(test))
        }

        // By temporal conflict
        val byConflict = pairs.groupBy { p ->
            when (p.eightBDecision?.temporalConflict) {
                com.example.feedsense.analysis.evidence.temporal.ConflictLevel.HIGH,
                com.example.feedsense.analysis.evidence.temporal.ConflictLevel.MEDIUM ->
                    "CONFLICT"
                else -> "NO_CONFLICT"
            }
        }
        for ((key, group) in byConflict.toSortedMap()) {
            val counts = PairedCounts.of(group)
            val test = PairedTestResult.fromCounts(
                counts = counts,
                role = AnalysisRole.EXPLORATORY_ANALYSIS,
                subgroupLabel = "conflict_$key",
                alpha = config.alpha,
                minimumDiscordant = config.minimumMcNemarDiscordantPairs,
                minimumForEffect = config.minimumSubgroupSupport
            )
            results.add(SubgroupResult.fromTest(test))
        }

        // By representative frame outlier
        val byOutlier = pairs.groupBy { p ->
            when (p.eightBDecision?.representativeFrameOutlier) {
                true -> "OUTLIER"
                false -> "NOT_OUTLIER"
                null -> "UNKNOWN"
            }
        }
        for ((key, group) in byOutlier.toSortedMap()) {
            val counts = PairedCounts.of(group)
            val test = PairedTestResult.fromCounts(
                counts = counts,
                role = AnalysisRole.EXPLORATORY_ANALYSIS,
                subgroupLabel = "outlier_$key",
                alpha = config.alpha,
                minimumDiscordant = config.minimumMcNemarDiscordantPairs,
                minimumForEffect = config.minimumSubgroupSupport
            )
            results.add(SubgroupResult.fromTest(test))
        }

        // By OCR presence
        val byOcr = pairs.groupBy { p ->
            val types = p.eightBDecision?.evidenceTypesPresent
                ?.joinToString(",") { it.trim() }
                ?.lowercase() ?: ""
            val hasOcr = types.contains("ocr") ||
                (p.eightBDecision?.uncertaintyReasons?.any {
                    it.lowercase().contains("ocr")
                } == true)
            if (hasOcr) "OCR_PRESENT" else "OCR_ABSENT"
        }
        for ((key, group) in byOcr.toSortedMap()) {
            val counts = PairedCounts.of(group)
            val test = PairedTestResult.fromCounts(
                counts = counts,
                role = AnalysisRole.EXPLORATORY_ANALYSIS,
                subgroupLabel = "ocr_$key",
                alpha = config.alpha,
                minimumDiscordant = config.minimumMcNemarDiscordantPairs,
                minimumForEffect = config.minimumSubgroupSupport
            )
            results.add(SubgroupResult.fromTest(test))
        }

        // By 8B decision state
        val byDecisionState = pairs.groupBy { p ->
            p.eightBDecision?.decisionState?.name ?: "UNKNOWN"
        }
        for ((key, group) in byDecisionState.toSortedMap()) {
            val counts = PairedCounts.of(group)
            val test = PairedTestResult.fromCounts(
                counts = counts,
                role = AnalysisRole.EXPLORATORY_ANALYSIS,
                subgroupLabel = "decision_$key",
                alpha = config.alpha,
                minimumDiscordant = config.minimumMcNemarDiscordantPairs,
                minimumForEffect = config.minimumSubgroupSupport
            )
            results.add(SubgroupResult.fromTest(test))
        }

        return results
    }

    // --------------------------------
    // MULTIPLE COMPARISON
    // --------------------------------

    private data class MultipleCompMeta(
        val familySize: Int,
        val adjustedAlpha: Double?
    )

    private fun multipleComparisonMetadata(
        catSens: List<SubgroupResult>,
        platSens: List<SubgroupResult>,
        durSens: List<SubgroupResult>,
        evSens: List<SubgroupResult>,
        config: RobustnessConfig
    ): MultipleCompMeta {
        if (!config.multipleComparisonBonferroni) {
            return MultipleCompMeta(familySize = 0, adjustedAlpha = null)
        }
        val familySize = catSens.size + platSens.size +
            durSens.size + evSens.size
        val adjusted = if (familySize > 0) {
            config.alpha / familySize
        } else null
        return MultipleCompMeta(familySize = familySize, adjustedAlpha = adjusted)
    }

    // --------------------------------
    // CONCLUSION
    // --------------------------------

    private fun buildConclusion(
        primaryTest: PairedTestResult,
        effectSize: AccuracyDifference,
        bootstrap: BootstrapAnalysis,
        abstSens: AbstentionSensitivity,
        config: RobustnessConfig,
        eligible: Int
    ): RobustnessReport.Conclusion {
        val notes = mutableListOf<String>()
        val exploratory = mutableListOf<String>()

        if (eligible < config.minimumForEffectSize) {
            return RobustnessReport.Conclusion(
                statisticalStatus = StatisticalStatus.INSUFFICIENT_DATA,
                headline = "INSUFFICIENT REAL DATA FOR " +
                    "STATISTICAL CONCLUSIONS: eligible pairs " +
                    "$eligible below required minimum " +
                    "${config.minimumForEffectSize}.",
                notes = notes + "Reported metrics describe the " +
                    "supplied inputs only and must NOT be read as " +
                    "a systemic claim.",
                exploratoryFindings = exploratory
            )
        }

        if (primaryTest.status != StatisticalStatus.SUFFICIENT) {
            notes += "Primary McNemar guard not met: " +
                (primaryTest.guardReason ?: "unknown")
            return RobustnessReport.Conclusion(
                statisticalStatus = StatisticalStatus.INSUFFICIENT_DATA,
                headline = "No statistically reliable difference " +
                    "can be established from the available " +
                    "evaluation corpus.",
                notes = notes,
                exploratoryFindings = exploratory
            )
        }

        val sig = primaryTest.significant == true
        val dir = if (effectSize.sufficient && effectSize.accuracyDifference != null) {
            if (effectSize.accuracyDifference > 0) "EIGHT_B_FAVORED"
            else if (effectSize.accuracyDifference < 0) "BASELINE_FAVORED"
            else "NO_DIRECTION"
        } else "INSUFFICIENT"

        notes += "McNemar exact, two-sided, on ${
            primaryTest.discordantPairs
        } discordant pairs (p=${primaryTest.pValue?.let { "%.4f".format(it) } ?: "n/a"})."
        notes += "Accuracy difference: ${
            effectSize.accuracyDifference?.let { "%.4f".format(it) } ?: "n/a"
        } (baseline ${effectSize.baselineAccuracy?.let { "%.4f".format(it) } ?: "n/a"}, " +
            "8B ${effectSize.eightBAccuracy?.let { "%.4f".format(it) } ?: "n/a"})."

        if (bootstrap.sufficient) {
            notes += "Bootstrap 95% CI on accuracy difference: " +
                "[${bootstrap.lowerBound?.let { "%.4f".format(it) } ?: "n/a"}, " +
                "${bootstrap.upperBound?.let { "%.4f".format(it) } ?: "n/a"}] " +
                "(${bootstrap.bootstrapIterations} iterations, seed=${bootstrap.seed})."
        } else {
            notes += "Bootstrap: ${bootstrap.guardReason ?: "insufficient data"}."
        }

        val polA = abstSens.policyA
        val polB = abstSens.policyB
        if (polA.sufficient) {
            notes += "UNKNOWN-as-wrong: baseline=${polA.baselineAccuracy?.let { "%.4f".format(it) } ?: "n/a"}, " +
                "8B=${polA.eightBAccuracy?.let { "%.4f".format(it) } ?: "n/a"}."
        }
        if (polB.sufficient) {
            notes += "UNKNOWN-excluded: baseline=${polB.baselineAccuracy?.let { "%.4f".format(it) } ?: "n/a"}, " +
                "8B=${polB.eightBAccuracy?.let { "%.4f".format(it) } ?: "n/a"}."
        }
        if (abstSens.policyC.sufficient) {
            val c = abstSens.policyC
            notes += "Coverage: baseline=${c.baselineCoverage?.let { "%.4f".format(it) } ?: "n/a"}, " +
                "8B=${c.eightBCoverage?.let { "%.4f".format(it) } ?: "n/a"}."
            notes += "Selective accuracy: baseline=${c.baselineSelectiveAccuracy?.let { "%.4f".format(it) } ?: "n/a"}, " +
                "8B=${c.eightBSelectiveAccuracy?.let { "%.4f".format(it) } ?: "n/a"}."
        }

        val headline = when {
            !sig -> "No statistically significant difference " +
                "at alpha=${config.alpha} (p=${
                    primaryTest.pValue?.let { "%.4f".format(it) } ?: "n/a"
                })."
            dir == "EIGHT_B_FAVORED" ->
                "Observed improvement is statistically supported " +
                    "under the configured paired test " +
                    "(p=${primaryTest.pValue?.let { "%.4f".format(it) } ?: "n/a"})."
            dir == "BASELINE_FAVORED" ->
                "Baseline is statistically superior under the " +
                    "configured paired test " +
                    "(p=${primaryTest.pValue?.let { "%.4f".format(it) } ?: "n/a"})."
            else -> "No statistically reliable difference can be " +
                "established from the available evaluation corpus."
        }

        if (!sig && dir != "NO_DIRECTION") {
            exploratory += "Directional trend ($dir) but not " +
                "statistically significant at alpha=${config.alpha}."
        }

        return RobustnessReport.Conclusion(
            statisticalStatus = if (sig) StatisticalStatus.SUFFICIENT
                else StatisticalStatus.INSUFFICIENT_DATA,
            headline = headline,
            notes = notes,
            exploratoryFindings = exploratory
        )
    }
}
