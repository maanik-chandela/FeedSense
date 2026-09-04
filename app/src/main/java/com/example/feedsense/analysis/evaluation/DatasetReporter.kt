package com.example.feedsense.analysis.evaluation

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.EvaluationItem
import com.example.feedsense.model.GroundTruth

// --------------------------------
// DATASET REPORTER (Milestone 8A-4)
// --------------------------------
//
// Generates comprehensive dataset profiles and quality
// assessments from the curated evaluation dataset. Reports
// distributions, coverage, completeness, and quality
// warnings. Does NOT create a fake composite quality score.
//
// The reporter operates on already-assembled data: it
// never queries the database itself.

object DatasetReporter {

    // --------------------------------
    // DURATION BUCKETS
    // --------------------------------

    val DURATION_BUCKETS = listOf(
        DurationBucket(0, 5, "0-5s"),
        DurationBucket(5, 15, "5-15s"),
        DurationBucket(15, 30, "15-30s"),
        DurationBucket(30, 60, "30-60s"),
        DurationBucket(60, 120, "60-120s"),
        DurationBucket(120, Int.MAX_VALUE, "120s+")
    )

    data class DurationBucket(
        val lowerSeconds: Int,
        val upperSeconds: Int,
        val label: String
    )

    // --------------------------------
    // CONFIDENCE BUCKETS
    // --------------------------------

    val CONFIDENCE_BUCKETS = (0..9).map { i ->
        ConfidenceBucket(
            lower = i * 0.1,
            upper = (i + 1) * 0.1,
            label = "${i * 10}-${(i + 1) * 10}%"
        )
    }

    data class ConfidenceBucket(
        val lower: Double,
        val upper: Double,
        val label: String
    )

    // --------------------------------
    // FULL REPORT
    // --------------------------------

    data class DatasetReport(
        val totalItems: Int,

        // Distributions
        val categoryDistribution: List<DatasetManifest.CountEntry>,
        val platformDistribution: List<DatasetManifest.CountEntry>,
        val contentTypeDistribution: List<DatasetManifest.CountEntry>,
        val durationDistribution: List<DatasetManifest.CountEntry>,
        val skipDistribution: List<DatasetManifest.CountEntry>,
        val interactionDistribution: Map<String, List<DatasetManifest.CountEntry>>,
        val confidenceDistribution: List<DatasetManifest.CountEntry>,
        val modelVersionDistribution: List<DatasetManifest.CountEntry>,

        // Coverage
        val totalCategoriesAvailable: Int,
        val categoriesRepresented: Int,
        val categoriesWithMeaningfulSupport: Int,
        val totalPlatformsSupported: Int,
        val platformsRepresented: Int,
        val platformsWithMeaningfulSupport: Int,

        // Annotation completeness
        val annotationCompleteness: Map<String, Double>,

        // Quality
        val qualityFlags: List<DatasetManifest.QualityFlag>,

        // Ambiguity / disputed
        val ambiguousItemCount: Int,
        val mixedItemCount: Int,
        val disputedItemCount: Int,
        val unknownCategoryCount: Int,

        // Duplicate risk
        val duplicateCandidateCount: Int,

        // Temporal
        val temporalEarliestItem: String?,
        val temporalLatestItem: String?
    )

    /**
     * Generates a comprehensive dataset report from
     * assembled evaluation data.
     *
     * @param items evaluation items
     * @param predictions AI predictions (keyed by item ID)
     * @param truths ground truths (keyed by item ID)
     * @param minimumCategorySupport threshold for meaningful support
     * @param minimumPlatformSupport threshold for meaningful platform support
     * @param maxUnknownFraction threshold for unknown rate warnings
     */
    fun generateReport(
        items: List<EvaluationItem>,
        predictions: Map<String, AiPredictionRecord>,
        truths: Map<String, GroundTruth>,
        minimumCategorySupport: Int = 5,
        minimumPlatformSupport: Int = 3,
        maxUnknownFraction: Double = 0.5
    ): DatasetReport {

        val truthItems = items.mapNotNull { item ->
            val truth = truths[item.id] ?: return@mapNotNull null
            item to truth
        }

        val total = items.size

        // --- Category distribution ---
        val categories = truthItems.mapNotNull { (_, t) ->
            CategoryCatalog.normalize(t.category) ?: "unknown"
        }
        val categoryDist = countEntries(categories)

        // --- Platform distribution ---
        val platforms = truthItems.mapNotNull { (_, t) ->
            t.platform?.takeIf { it.isNotBlank() } ?: "unknown"
        }
        val platformDist = countEntries(platforms)

        // --- Content type distribution ---
        val contentTypes = truthItems.mapNotNull { (_, t) ->
            t.contentType?.takeIf { it.isNotBlank() } ?: "unknown"
        }
        val contentTypeDist = countEntries(contentTypes)

        // --- Duration distribution ---
        val durations = truthItems.mapNotNull { (_, t) ->
            t.durationSeconds?.let { bucketForDuration(it) } ?: "unknown"
        }
        val durationDist = countEntries(durations)

        // --- Skip distribution ---
        val skips = truthItems.map { (_, t) ->
            when (t.skipped) {
                true -> "skipped"
                false -> "watched"
                null -> "unknown"
            }
        }
        val skipDist = countEntries(skips)

        // --- Interaction distribution ---
        val interactionDist = mutableMapOf<String, List<DatasetManifest.CountEntry>>()
        GroundTruth.INTERACTION_SIGNAL_KEYS.forEach { signal ->
            val values = truthItems.map { (_, t) ->
                when (getInteractionField(t, signal)) {
                    true -> "true"
                    false -> "false"
                    null -> "unknown"
                }
            }
            interactionDist[signal] = countEntries(values)
        }

        // --- Confidence distribution ---
        val confidences = items.mapNotNull { item ->
            predictions[item.id]?.confidence?.let { bucketForConfidence(it) }
        }
        val confidenceDist = countEntries(confidences)

        // --- Model version distribution ---
        val modelVersions = items.mapNotNull { item ->
            predictions[item.id]?.modelVersion ?: "unknown"
        }
        val modelVersionDist = countEntries(modelVersions)

        // --- Coverage ---
        val allCategories = CategoryCatalog.keys
        val representedCategories = categories.filter { it != "unknown" }.distinct()
        val meaningfulCategories = categoryDist.filter {
            it.key != "unknown" && it.count >= minimumCategorySupport
        }

        val allPlatforms = listOf(
            "Instagram", "YouTube", "TikTok", "Facebook",
            "Snapchat", "Threads", "LinkedIn", "Reddit",
            "Pinterest", "Twitch"
        )
        val representedPlatforms = platforms.filter { it != "unknown" }.distinct()
        val meaningfulPlatforms = platformDist.filter {
            it.key != "unknown" && it.count >= minimumPlatformSupport
        }

        // --- Annotation completeness ---
        val completeness = mutableMapOf<String, Double>()
        completeness["category"] = truthItems.count { (_, t) ->
            t.category != null && t.ambiguity != GroundTruth.AMBIGUITY_UNKNOWN
        }.toDouble() / total.coerceAtLeast(1)

        completeness["platform"] = truthItems.count { (_, t) ->
            !t.platform.isNullOrBlank()
        }.toDouble() / total.coerceAtLeast(1)

        completeness["contentType"] = truthItems.count { (_, t) ->
            !t.contentType.isNullOrBlank()
        }.toDouble() / total.coerceAtLeast(1)

        completeness["duration"] = truthItems.count { (_, t) ->
            t.durationSeconds != null
        }.toDouble() / total.coerceAtLeast(1)

        completeness["skip"] = truthItems.count { (_, t) ->
            t.skipped != null
        }.toDouble() / total.coerceAtLeast(1)

        completeness["topic"] = truthItems.count { (_, t) ->
            !t.topic.isNullOrBlank()
        }.toDouble() / total.coerceAtLeast(1)

        completeness["tone"] = truthItems.count { (_, t) ->
            !t.tone.isNullOrBlank()
        }.toDouble() / total.coerceAtLeast(1)

        GroundTruth.INTERACTION_SIGNAL_KEYS.forEach { signal ->
            completeness[signal] = truthItems.count { (_, t) ->
                getInteractionField(t, signal) != null
            }.toDouble() / total.coerceAtLeast(1)
        }

        // --- Ambiguity / disputed ---
        val ambiguousCount = truthItems.count { (_, t) ->
            t.ambiguity == GroundTruth.AMBIGUITY_AMBIGUOUS
        }
        val mixedCount = truthItems.count { (_, t) ->
            t.ambiguity == GroundTruth.AMBIGUITY_MIXED
        }
        val disputedCount = items.count {
            it.evaluationStatus == EvaluationItem.STATUS_DISPUTED
        }
        val unknownCatCount = truthItems.count { (_, t) ->
            CategoryCatalog.normalize(t.category) == null
        }

        // --- Duplicate risk ---
        val fingerprintCandidates = predictions.values
            .mapNotNull { it.feedItemId }
            .groupingBy { it }
            .eachCount()
            .count { it.value > 1 }

        // --- Quality flags ---
        val flags = mutableListOf<DatasetManifest.QualityFlag>()
        flags += generateQualityFlags(
            categoryDist = categoryDist,
            platformDist = platformDist,
            contentTypeDist = contentTypeDist,
            durationDist = durationDist,
            completeness = completeness,
            ambiguousCount = ambiguousCount,
            disputedCount = disputedCount,
            totalItems = total,
            minimumCategorySupport = minimumCategorySupport,
            minimumPlatformSupport = minimumPlatformSupport,
            maxUnknownFraction = maxUnknownFraction,
            representedCategories = representedCategories.size,
            totalCategories = allCategories.size,
            representedPlatforms = representedPlatforms.size,
            totalPlatforms = allPlatforms.size,
            fingerprintCandidates = fingerprintCandidates
        )

        // --- Temporal ---
        val timestamps = items.map { it.enqueuedAt }
        val earliest = timestamps.minOrNull()?.toString()
        val latest = timestamps.maxOrNull()?.toString()

        return DatasetReport(
            totalItems = total,
            categoryDistribution = categoryDist,
            platformDistribution = platformDist,
            contentTypeDistribution = contentTypeDist,
            durationDistribution = durationDist,
            skipDistribution = skipDist,
            interactionDistribution = interactionDist,
            confidenceDistribution = confidenceDist,
            modelVersionDistribution = modelVersionDist,
            totalCategoriesAvailable = allCategories.size,
            categoriesRepresented = representedCategories.size,
            categoriesWithMeaningfulSupport = meaningfulCategories.size,
            totalPlatformsSupported = allPlatforms.size,
            platformsRepresented = representedPlatforms.size,
            platformsWithMeaningfulSupport = meaningfulPlatforms.size,
            annotationCompleteness = completeness,
            qualityFlags = flags,
            ambiguousItemCount = ambiguousCount,
            mixedItemCount = mixedCount,
            disputedItemCount = disputedCount,
            unknownCategoryCount = unknownCatCount,
            duplicateCandidateCount = fingerprintCandidates,
            temporalEarliestItem = earliest,
            temporalLatestItem = latest
        )
    }

    // --------------------------------
    // QUALITY FLAG GENERATION
    // --------------------------------

    private fun generateQualityFlags(
        categoryDist: List<DatasetManifest.CountEntry>,
        platformDist: List<DatasetManifest.CountEntry>,
        contentTypeDist: List<DatasetManifest.CountEntry>,
        durationDist: List<DatasetManifest.CountEntry>,
        completeness: Map<String, Double>,
        ambiguousCount: Int,
        disputedCount: Int,
        totalItems: Int,
        minimumCategorySupport: Int,
        minimumPlatformSupport: Int,
        maxUnknownFraction: Double,
        representedCategories: Int,
        totalCategories: Int,
        representedPlatforms: Int,
        totalPlatforms: Int,
        fingerprintCandidates: Int
    ): List<DatasetManifest.QualityFlag> {

        val flags = mutableListOf<DatasetManifest.QualityFlag>()

        if (totalItems == 0) {
            flags += DatasetManifest.QualityFlag(
                flag = "EMPTY_DATASET",
                severity = DatasetManifest.QualityFlag.Severity.CRITICAL,
                message = "Dataset contains zero items"
            )
            return flags
        }

        // INSUFFICIENT_SAMPLE_SIZE
        if (totalItems < 20) {
            flags += DatasetManifest.QualityFlag(
                flag = "INSUFFICIENT_SAMPLE_SIZE",
                severity = DatasetManifest.QualityFlag.Severity.WARNING,
                message = "Dataset has only $totalItems items (recommended: 20+)"
            )
        }

        // LOW_CATEGORY_SUPPORT
        val lowCategories = categoryDist.filter {
            it.key != "unknown" && it.count < minimumCategorySupport
        }
        if (lowCategories.isNotEmpty()) {
            flags += DatasetManifest.QualityFlag(
                flag = "LOW_CATEGORY_SUPPORT",
                severity = DatasetManifest.QualityFlag.Severity.WARNING,
                message = "${lowCategories.size} categories below minimum support " +
                    "($minimumCategorySupport): ${
                        lowCategories.joinToString { "${it.key}(${it.count})" }
                    }"
            )
        }

        // LOW_PLATFORM_SUPPORT
        val lowPlatforms = platformDist.filter {
            it.key != "unknown" && it.count < minimumPlatformSupport
        }
        if (lowPlatforms.isNotEmpty()) {
            flags += DatasetManifest.QualityFlag(
                flag = "LOW_PLATFORM_SUPPORT",
                severity = DatasetManifest.QualityFlag.Severity.WARNING,
                message = "${lowPlatforms.size} platforms below minimum support " +
                    "($minimumPlatformSupport): ${
                        lowPlatforms.joinToString { "${it.key}(${it.count})" }
                    }"
            )
        }

        // HIGH_UNKNOWN_RATE
        completeness.forEach { (field, fraction) ->
            if (fraction > maxUnknownFraction) {
                flags += DatasetManifest.QualityFlag(
                    flag = "HIGH_UNKNOWN_RATE",
                    severity = DatasetManifest.QualityFlag.Severity.WARNING,
                    message = "Field \"$field\" has ${(fraction * 100).toInt()}% " +
                        "completeness (${((1 - fraction) * 100).toInt()}% unknown)",
                    details = mapOf("field" to field, "unknownFraction" to "${1 - fraction}")
                )
            }
        }

        // CATEGORY_IMBALANCE
        if (categoryDist.size >= 2) {
            val counts = categoryDist.map { it.count }
            val max = counts.maxOrNull()!!
            val min = counts.minOrNull()!!
            if (min > 0 && max / min > 10) {
                flags += DatasetManifest.QualityFlag(
                    flag = "CATEGORY_IMBALANCE",
                    severity = DatasetManifest.QualityFlag.Severity.WARNING,
                    message = "Category imbalance ratio: ${
                        String.format("%.1f", max.toDouble() / min)
                    } (max=${categoryDist.first().key}, min=${
                        categoryDist.last().key
                    })"
                )
            }
        }

        // PLATFORM_IMBALANCE
        if (platformDist.size >= 2) {
            val counts = platformDist.filter { it.key != "unknown" }
                .map { it.count }
            if (counts.size >= 2) {
                val max = counts.maxOrNull()!!
                val min = counts.minOrNull()!!
                if (min > 0 && max / min > 10) {
                    flags += DatasetManifest.QualityFlag(
                        flag = "PLATFORM_IMBALANCE",
                        severity = DatasetManifest.QualityFlag.Severity.WARNING,
                        message = "Platform imbalance ratio: ${
                            String.format("%.1f", max.toDouble() / min)
                        }"
                    )
                }
            }
        }

        // CATEGORY_IMBALANCE (coverage)
        if (representedCategories < totalCategories * 0.5) {
            flags += DatasetManifest.QualityFlag(
                flag = "LOW_CATEGORY_COVERAGE",
                severity = DatasetManifest.QualityFlag.Severity.WARNING,
                message = "Only $representedCategories of $totalCategories " +
                    "categories represented"
            )
        }

        // PLATFORM_IMBALANCE (coverage)
        if (representedPlatforms < totalPlatforms * 0.5) {
            flags += DatasetManifest.QualityFlag(
                flag = "LOW_PLATFORM_COVERAGE",
                severity = DatasetManifest.QualityFlag.Severity.WARNING,
                message = "Only $representedPlatforms of $totalPlatforms " +
                    "platforms represented"
            )
        }

        // DUPLICATE_RISK
        if (fingerprintCandidates > 0) {
            flags += DatasetManifest.QualityFlag(
                flag = "DUPLICATE_RISK",
                severity = DatasetManifest.QualityFlag.Severity.WARNING,
                message = "$fingerprintCandidates items have duplicate feed " +
                    "item references (possible duplicate content)"
            )
        }

        // HIGH_AMBIGUITY_RATE
        val ambiguityRate = ambiguousCount.toDouble() / totalItems
        if (ambiguityRate > 0.3) {
            flags += DatasetManifest.QualityFlag(
                flag = "HIGH_AMBIGUITY_RATE",
                severity = DatasetManifest.QualityFlag.Severity.INFO,
                message = "${(ambiguityRate * 100).toInt()}% of items are " +
                    "marked AMBIGUOUS (${ambiguousCount}/${totalItems})"
            )
        }

        // DURATION_IMBALANCE
        val unknownDurations = durationDist.find { it.key == "unknown" }?.count ?: 0
        if (unknownDurations > totalItems * 0.5) {
            flags += DatasetManifest.QualityFlag(
                flag = "DURATION_IMBALANCE",
                severity = DatasetManifest.QualityFlag.Severity.WARNING,
                message = "$unknownDurations of $totalItems items have unknown " +
                    "duration (${(unknownDurations * 100 / totalItems)}%)"
            )
        }

        return flags
    }

    // --------------------------------
    // HELPERS
    // --------------------------------

    private fun countEntries(values: List<String>): List<DatasetManifest.CountEntry> {
        return values.groupingBy { it }
            .eachCount()
            .map { (key, count) -> DatasetManifest.CountEntry(key, count) }
            .sortedWith(
                compareByDescending<DatasetManifest.CountEntry> { it.count }
                    .thenBy { it.key }
            )
    }

    private fun bucketForDuration(seconds: Int): String {
        return DURATION_BUCKETS.firstOrNull {
            seconds >= it.lowerSeconds && seconds < it.upperSeconds
        }?.label ?: "${seconds}s"
    }

    private fun bucketForConfidence(confidence: Double): String {
        return CONFIDENCE_BUCKETS.firstOrNull {
            confidence >= it.lower && confidence < it.upper
        }?.label ?: "100%"
    }

    private fun getInteractionField(
        truth: GroundTruth,
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
