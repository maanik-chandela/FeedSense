package com.example.feedsense.analysis.evaluation

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.analysis.PlatformDetector
import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.EvaluationItem
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.GroundTruth
import java.time.LocalDateTime

// --------------------------------
// DATASET VALIDATOR (Milestone 8A-4)
// --------------------------------
//
// A deterministic validation layer that checks evaluation
// data for structural integrity without silently repairing
// bad data. Returns structured validation results.
//
// Checks:
//   - missing source references
//   - missing AI predictions
//   - missing ground truth
//   - invalid taxonomy values
//   - impossible durations
//   - invalid timestamps
//   - malformed confidence values
//   - duplicate dataset IDs
//   - invalid category / platform / content type values
//   - contradictory fields

object DatasetValidator {

    // --------------------------------
    // VALIDATION SEVERITY
    // --------------------------------

    enum class Severity {
        ERROR,
        WARNING
    }

    // --------------------------------
    // VALIDATION ISSUE
    // --------------------------------

    data class Issue(
        val field: String,
        val severity: Severity,
        val message: String,
        val evaluationItemId: String? = null
    )

    // --------------------------------
    // VALIDATION RESULT
    // --------------------------------

    data class ValidationResult(
        val issues: List<Issue>,
        val validItemCount: Int,
        val invalidItemCount: Int,
        val totalChecked: Int
    ) {
        val isValid: Boolean
            get() = issues.none { it.severity == Severity.ERROR }

        val errorCount: Int
            get() = issues.count { it.severity == Severity.ERROR }

        val warningCount: Int
            get() = issues.count { it.severity == Severity.WARNING }
    }

    // --------------------------------
    // SINGLE ITEM VALIDATION
    // --------------------------------

    /**
     * Validates a single evaluation item against its
     * prediction and truth. Returns all issues found.
     */
    fun validateItem(
        item: EvaluationItem,
        prediction: AiPredictionRecord?,
        truth: GroundTruth?
    ): List<Issue> {

        val issues = mutableListOf<Issue>()
        val id = item.id

        // --- Structural checks ---

        if (prediction == null) {
            issues += Issue(
                field = "aiPrediction",
                severity = Severity.ERROR,
                message = "Missing AI prediction snapshot",
                evaluationItemId = id
            )
        }

        if (truth == null) {
            issues += Issue(
                field = "groundTruth",
                severity = Severity.ERROR,
                message = "Missing ground truth",
                evaluationItemId = id
            )
        }

        // --- Item-level checks ---

        if (item.feedItemId.isBlank()) {
            issues += Issue(
                field = "feedItemId",
                severity = Severity.ERROR,
                message = "Empty feedItemId reference",
                evaluationItemId = id
            )
        }

        if (item.sessionId.isBlank()) {
            issues += Issue(
                field = "sessionId",
                severity = Severity.ERROR,
                message = "Empty sessionId reference",
                evaluationItemId = id
            )
        }

        if (item.modelVersion.isNullOrBlank()) {
            issues += Issue(
                field = "modelVersion",
                severity = Severity.WARNING,
                message = "Null or blank modelVersion",
                evaluationItemId = id
            )
        }

        // --- Prediction checks ---

        prediction?.let { p ->
            validateConfidence(p.confidence, id)?.let {
                issues += it
            }

            if (p.durationSeconds < 0) {
                issues += Issue(
                    field = "prediction.durationSeconds",
                    severity = Severity.ERROR,
                    message = "Negative prediction duration: ${p.durationSeconds}",
                    evaluationItemId = id
                )
            }

            if (p.category != null) {
                val normalized = CategoryCatalog.normalize(p.category)
                if (normalized == null) {
                    issues += Issue(
                        field = "prediction.category",
                        severity = Severity.WARNING,
                        message = "Prediction category \"${p.category}\" is not in the catalog",
                        evaluationItemId = id
                    )
                }
            }

            if (p.platform != null) {
                val validPlatforms = PlatformDetector().platformNames()
                if (p.platform !in validPlatforms) {
                    issues += Issue(
                        field = "prediction.platform",
                        severity = Severity.WARNING,
                        message = "Prediction platform \"${p.platform}\" is not in the canonical list",
                        evaluationItemId = id
                    )
                }
            }
        }

        // --- Ground truth checks ---

        truth?.let { t ->
            if (t.category != null) {
                val normalized = CategoryCatalog.normalize(t.category)
                if (normalized == null && t.ambiguity != GroundTruth.AMBIGUITY_UNKNOWN) {
                    issues += Issue(
                        field = "truth.category",
                        severity = Severity.ERROR,
                        message = "Truth category \"${t.category}\" is not in the catalog (and ambiguity is not UNKNOWN)",
                        evaluationItemId = id
                    )
                }
            }

            if (t.ambiguity !in GroundTruth.VALID_AMBIGUITY) {
                issues += Issue(
                    field = "truth.ambiguity",
                    severity = Severity.ERROR,
                    message = "Invalid ambiguity value: \"${t.ambiguity}\"",
                    evaluationItemId = id
                )
            }

            if (t.platform != null) {
                val validPlatforms = PlatformDetector().platformNames()
                if (t.platform !in validPlatforms) {
                    issues += Issue(
                        field = "truth.platform",
                        severity = Severity.WARNING,
                        message = "Truth platform \"${t.platform}\" is not in the canonical list",
                        evaluationItemId = id
                    )
                }
            }

            if (t.contentType != null &&
                t.contentType !in GroundTruth.VALID_CONTENT_TYPES
            ) {
                issues += Issue(
                    field = "truth.contentType",
                    severity = Severity.WARNING,
                    message = "Invalid content type: \"${t.contentType}\"",
                    evaluationItemId = id
                )
            }

            if (t.durationSeconds != null && t.durationSeconds < 0) {
                issues += Issue(
                    field = "truth.durationSeconds",
                    severity = Severity.ERROR,
                    message = "Negative truth duration: ${t.durationSeconds}",
                    evaluationItemId = id
                )
            }

            // Timestamp sanity: recordedAt should not be
            // in the far future (more than 1 day).
            val now = LocalDateTime.now()
            if (t.recordedAt.isAfter(now.plusDays(1))) {
                issues += Issue(
                    field = "truth.recordedAt",
                    severity = Severity.WARNING,
                    message = "recordedAt is in the future: ${t.recordedAt}",
                    evaluationItemId = id
                )
            }
        }

        // --- Cross-field consistency ---

        if (prediction != null && truth != null) {
            // Duration consistency: if truth has a duration,
            // the prediction's duration should be non-negative.
            if (truth.durationSeconds != null &&
                truth.durationSeconds < 0
            ) {
                issues += Issue(
                    field = "cross.duration",
                    severity = Severity.ERROR,
                    message = "Impossible duration: truth duration is negative",
                    evaluationItemId = id
                )
            }
        }

        return issues
    }

    // --------------------------------
    // BATCH VALIDATION
    // --------------------------------

    /**
     * Validates a batch of evaluation items and returns
     * a summary of all issues found.
     */
    fun validateBatch(
        items: List<Triple<EvaluationItem, AiPredictionRecord?, GroundTruth?>>
    ): ValidationResult {

        val allIssues = mutableListOf<Issue>()
        var validCount = 0
        var invalidCount = 0

        items.forEach { (item, prediction, truth) ->
            val issues = validateItem(item, prediction, truth)
            allIssues += issues

            if (issues.any { it.severity == Severity.ERROR }) {
                invalidCount++
            } else {
                validCount++
            }
        }

        // --- Duplicate ID checks ---

        val itemIds = items.map { it.first.id }
        val duplicateIds = itemIds.groupingBy { it }
            .eachCount()
            .filter { it.value > 1 }
            .keys

        duplicateIds.forEach { id ->
            allIssues += Issue(
                field = "id",
                severity = Severity.ERROR,
                message = "Duplicate evaluation item ID: $id",
                evaluationItemId = id
            )
            invalidCount++
            validCount--
        }

        return ValidationResult(
            issues = allIssues,
            validItemCount = validCount,
            invalidItemCount = invalidCount,
            totalChecked = items.size
        )
    }

    // --------------------------------
    // DATASET VERSION VALIDATION
    // --------------------------------

    /**
     * Validates that a set of items assigned to a dataset
     * version is internally consistent.
     */
    fun validateDatasetCohort(
        items: List<EvaluationItem>,
        expectedVersion: String
    ): List<Issue> {

        val issues = mutableListOf<Issue>()

        // All items should have the expected dataset version
        val mismatched = items.filter {
            it.datasetVersion != expectedVersion
        }

        if (mismatched.isNotEmpty()) {
            issues += Issue(
                field = "datasetVersion",
                severity = Severity.WARNING,
                message = "${mismatched.size} items have datasetVersion " +
                    "!= \"$expectedVersion\""
            )
        }

        // No items should be NOT_EVALUATED
        val unreviewed = items.filter {
            it.evaluationStatus == EvaluationItem.STATUS_NOT_EVALUATED
        }

        if (unreviewed.isNotEmpty()) {
            issues += Issue(
                field = "evaluationStatus",
                severity = Severity.WARNING,
                message = "${unreviewed.size} items in the cohort are " +
                    "NOT_EVALUATED (should not be in a dataset)"
            )
        }

        // Check for duplicate IDs
        val ids = items.map { it.id }
        val dupes = ids.groupingBy { it }.eachCount()
            .filter { it.value > 1 }
            .keys

        if (dupes.isNotEmpty()) {
            issues += Issue(
                field = "id",
                severity = Severity.ERROR,
                message = "Duplicate IDs in cohort: ${dupes.joinToString()}"
            )
        }

        return issues
    }

    // --------------------------------
    // MANIFEST VALIDATION
    // --------------------------------

    /**
     * Validates internal consistency of a dataset manifest.
     */
    fun validateManifest(
        manifest: DatasetManifest
    ): List<Issue> {

        val issues = mutableListOf<Issue>()

        // Counts should be consistent
        val totalFromManifest =
            manifest.includedItemIds.size + manifest.excludedItems.size

        if (totalFromManifest != manifest.totalCandidateItems) {
            issues += Issue(
                field = "totalCandidateItems",
                severity = Severity.ERROR,
                message = "totalCandidateItems (${manifest.totalCandidateItems}) " +
                    "!= included (${manifest.includedItemIds.size}) + " +
                    "excluded (${manifest.excludedItems.size})"
            )
        }

        // No duplicate included IDs
        val includedDupes = manifest.includedItemIds
            .groupingBy { it }
            .eachCount()
            .filter { it.value > 1 }
            .keys

        if (includedDupes.isNotEmpty()) {
            issues += Issue(
                field = "includedItemIds",
                severity = Severity.ERROR,
                message = "Duplicate IDs in included items: ${includedDupes.size}"
            )
        }

        // Category distribution counts should sum correctly
        val categorySum = manifest.categoryDistribution.sumOf { it.count }
        if (categorySum != manifest.includedItemIds.size) {
            issues += Issue(
                field = "categoryDistribution",
                severity = Severity.WARNING,
                message = "Category distribution sum ($categorySum) " +
                    "!= included items (${manifest.includedItemIds.size})"
            )
        }

        return issues
    }

    // --------------------------------
    // HELPERS
    // --------------------------------

    private fun validateConfidence(
        confidence: Double?,
        itemId: String
    ): Issue? {
        if (confidence == null) return null

        return when {
            confidence < 0.0 -> Issue(
                field = "prediction.confidence",
                severity = Severity.ERROR,
                message = "Negative confidence: $confidence",
                evaluationItemId = itemId
            )
            confidence > 1.0 -> Issue(
                field = "prediction.confidence",
                severity = Severity.ERROR,
                message = "Confidence > 1.0: $confidence",
                evaluationItemId = itemId
            )
            else -> null
        }
    }
}
