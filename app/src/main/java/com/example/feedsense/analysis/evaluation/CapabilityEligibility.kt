package com.example.feedsense.analysis.evaluation

import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.EvaluationItem
import com.example.feedsense.model.GroundTruth

// --------------------------------
// CAPABILITY ELIGIBILITY (Milestone 8A-4)
// --------------------------------
//
// Different evaluation tasks require different subsets of
// data. An item may have a valid category but unknown
// liked status - it can still be used for category
// evaluation but not for like evaluation.
//
// This prevents unnecessary data loss: rather than marking
// an item "invalid" because one field is unknown, we
// determine eligibility per-capability.
//
// IMPORTANT: This does NOT create a single universal
// valid/invalid flag. Each evaluation task has its own
// requirements.

object CapabilityEligibility {

    // --------------------------------
    // CAPABILITY DEFINITIONS
    // --------------------------------

    enum class Capability(val displayName: String) {
        CATEGORY("Category"),
        PLATFORM("Platform"),
        CONTENT_TYPE("Content Type"),
        DURATION("Duration"),
        SKIP("Skip"),
        LIKE("Like"),
        COMMENT("Comment"),
        SHARE("Share"),
        SAVE("Save"),
        FOLLOW("Follow"),
        PAUSE("Pause"),
        PLAYING("Playing"),
        TOPIC("Topic"),
        TONE("Tone"),
        MULTI_LABEL("Multi-label"),
        CALIBRATION("Calibration")
    }

    /**
     * Result of checking whether an item is eligible for
     * a specific capability evaluation.
     */
    data class EligibilityResult(
        val capability: Capability,
        val eligible: Boolean,
        val reason: String? = null
    )

    /**
     * Full eligibility report for one item across all
     * capabilities.
     */
    data class ItemEligibilityReport(
        val evaluationItemId: String,
        val results: Map<Capability, EligibilityResult>
    ) {
        fun isEligibleFor(capability: Capability): Boolean {
            return results[capability]?.eligible == true
        }

        fun eligibleCapabilities(): Set<Capability> {
            return results.filter { it.value.eligible }.keys
        }

        fun ineligibleCapabilities(): Set<Capability> {
            return results.filter { !it.value.eligible }.keys
        }
    }

    // --------------------------------
    // ELIGIBILITY CHECKS
    // --------------------------------

    /**
     * Determines whether an evaluation item is eligible
     * for each evaluation capability.
     *
     * The item must already be in EVALUATED status with
     * an AI prediction and ground truth available. The
     * caller assembles these from the database.
     */
    fun checkItem(
        item: EvaluationItem,
        prediction: AiPredictionRecord?,
        truth: GroundTruth?
    ): ItemEligibilityReport {

        val results = mutableMapOf<Capability, EligibilityResult>()

        // Prerequisite: item must be evaluated
        if (item.evaluationStatus != EvaluationItem.STATUS_EVALUATED &&
            item.evaluationStatus != EvaluationItem.STATUS_DISPUTED
        ) {
            Capability.entries.forEach { cap ->
                results[cap] = EligibilityResult(
                    capability = cap,
                    eligible = false,
                    reason = "Item status is ${item.evaluationStatus}, not EVALUATED"
                )
            }
            return ItemEligibilityReport(item.id, results)
        }

        // Prerequisite: prediction must exist
        if (prediction == null) {
            Capability.entries.forEach { cap ->
                results[cap] = EligibilityResult(
                    capability = cap,
                    eligible = false,
                    reason = "No AI prediction available"
                )
            }
            return ItemEligibilityReport(item.id, results)
        }

        // Prerequisite: truth must exist
        if (truth == null) {
            Capability.entries.forEach { cap ->
                results[cap] = EligibilityResult(
                    capability = cap,
                    eligible = false,
                    reason = "No ground truth available"
                )
            }
            return ItemEligibilityReport(item.id, results)
        }

        // Per-capability checks
        results[Capability.CATEGORY] = checkCategory(truth)
        results[Capability.PLATFORM] = checkPlatform(truth)
        results[Capability.CONTENT_TYPE] = checkContentType(truth)
        results[Capability.DURATION] = checkDuration(truth)
        results[Capability.SKIP] = checkSkip(truth)
        results[Capability.LIKE] = checkInteraction(truth.liked, "liked")
        results[Capability.COMMENT] = checkInteraction(truth.commented, "commented")
        results[Capability.SHARE] = checkInteraction(truth.shared, "shared")
        results[Capability.SAVE] = checkInteraction(truth.saved, "saved")
        results[Capability.FOLLOW] = checkInteraction(truth.followed, "followed")
        results[Capability.PAUSE] = checkInteraction(truth.paused, "paused")
        results[Capability.PLAYING] = checkInteraction(truth.playing, "playing")
        results[Capability.TOPIC] = checkTopic(truth)
        results[Capability.TONE] = checkTone(truth)
        results[Capability.MULTI_LABEL] = checkMultiLabel(truth)
        results[Capability.CALIBRATION] = checkCalibration(prediction, truth)

        return ItemEligibilityReport(item.id, results)
    }

    /**
     * Filters a batch of items to those eligible for a
     * specific capability, returning both eligible and
     * excluded items with reasons.
     */
    fun filterByCapability(
        items: List<Triple<EvaluationItem, AiPredictionRecord?, GroundTruth?>>,
        capability: Capability
    ): CapabilityFilterResult {

        val eligible = mutableListOf<String>()
        val excluded = mutableListOf<Exclusion>()

        items.forEach { (item, prediction, truth) ->
            val report = checkItem(item, prediction, truth)
            val result = report.results[capability]
                ?: return@forEach

            if (result.eligible) {
                eligible.add(item.id)
            } else {
                excluded.add(
                    Exclusion(
                        evaluationItemId = item.id,
                        capability = capability,
                        reason = result.reason ?: "Unknown"
                    )
                )
            }
        }

        return CapabilityFilterResult(
            capability = capability,
            eligibleItemIds = eligible,
            excluded = excluded
        )
    }

    // --------------------------------
    // PRIVATE CHECKS
    // --------------------------------

    private fun checkCategory(truth: GroundTruth): EligibilityResult {
        val normalized = com.example.feedsense.analysis.CategoryCatalog.normalize(truth.category)
        return if (normalized != null) {
            EligibilityResult(Capability.CATEGORY, eligible = true)
        } else if (truth.ambiguity == GroundTruth.AMBIGUITY_UNKNOWN) {
            EligibilityResult(
                Capability.CATEGORY,
                eligible = false,
                reason = "Ground truth ambiguity is UNKNOWN"
            )
        } else {
            EligibilityResult(
                Capability.CATEGORY,
                eligible = false,
                reason = "No valid category ground truth"
            )
        }
    }

    private fun checkPlatform(truth: GroundTruth): EligibilityResult {
        return if (!truth.platform.isNullOrBlank()) {
            EligibilityResult(Capability.PLATFORM, eligible = true)
        } else {
            EligibilityResult(
                Capability.PLATFORM,
                eligible = false,
                reason = "No platform ground truth"
            )
        }
    }

    private fun checkContentType(truth: GroundTruth): EligibilityResult {
        return if (!truth.contentType.isNullOrBlank() &&
            truth.contentType in com.example.feedsense.model.GroundTruth.VALID_CONTENT_TYPES
        ) {
            EligibilityResult(Capability.CONTENT_TYPE, eligible = true)
        } else {
            EligibilityResult(
                Capability.CONTENT_TYPE,
                eligible = false,
                reason = "No valid content type ground truth"
            )
        }
    }

    private fun checkDuration(truth: GroundTruth): EligibilityResult {
        return if (truth.durationSeconds != null && truth.durationSeconds >= 0) {
            EligibilityResult(Capability.DURATION, eligible = true)
        } else if (truth.durationSeconds != null && truth.durationSeconds < 0) {
            EligibilityResult(
                Capability.DURATION,
                eligible = false,
                reason = "Negative duration in ground truth"
            )
        } else {
            EligibilityResult(
                Capability.DURATION,
                eligible = false,
                reason = "No duration ground truth"
            )
        }
    }

    private fun checkSkip(truth: GroundTruth): EligibilityResult {
        return if (truth.skipped != null) {
            EligibilityResult(Capability.SKIP, eligible = true)
        } else {
            EligibilityResult(
                Capability.SKIP,
                eligible = false,
                reason = "Skip ground truth is UNKNOWN"
            )
        }
    }

    private fun signalToCapability(signalName: String): Capability {
        return when (signalName) {
            "liked" -> Capability.LIKE
            "commented" -> Capability.COMMENT
            "shared" -> Capability.SHARE
            "saved" -> Capability.SAVE
            "followed" -> Capability.FOLLOW
            "paused" -> Capability.PAUSE
            "playing" -> Capability.PLAYING
            else -> throw IllegalArgumentException("Unknown signal: $signalName")
        }
    }

    private fun checkInteraction(value: Boolean?, signalName: String): EligibilityResult {
        val capability = signalToCapability(signalName)
        return if (value != null) {
            EligibilityResult(capability, eligible = true)
        } else {
            EligibilityResult(
                capability,
                eligible = false,
                reason = "$signalName ground truth is UNKNOWN"
            )
        }
    }

    private fun checkTopic(truth: GroundTruth): EligibilityResult {
        return if (!truth.topic.isNullOrBlank()) {
            EligibilityResult(Capability.TOPIC, eligible = true)
        } else {
            EligibilityResult(
                Capability.TOPIC,
                eligible = false,
                reason = "No topic ground truth"
            )
        }
    }

    private fun checkTone(truth: GroundTruth): EligibilityResult {
        return if (!truth.tone.isNullOrBlank()) {
            EligibilityResult(Capability.TONE, eligible = true)
        } else {
            EligibilityResult(
                Capability.TONE,
                eligible = false,
                reason = "No tone ground truth"
            )
        }
    }

    private fun checkMultiLabel(truth: GroundTruth): EligibilityResult {
        val primary = com.example.feedsense.analysis.CategoryCatalog.normalize(truth.category)
        return if (primary != null &&
            truth.ambiguity != GroundTruth.AMBIGUITY_UNKNOWN
        ) {
            EligibilityResult(Capability.MULTI_LABEL, eligible = true)
        } else {
            EligibilityResult(
                Capability.MULTI_LABEL,
                eligible = false,
                reason = "No valid category or UNKNOWN ambiguity"
            )
        }
    }

    private fun checkCalibration(
        prediction: AiPredictionRecord,
        truth: GroundTruth
    ): EligibilityResult {
        val primary = com.example.feedsense.analysis.CategoryCatalog.normalize(truth.category)
        return if (primary != null &&
            truth.ambiguity != GroundTruth.AMBIGUITY_UNKNOWN &&
            prediction.confidence != null
        ) {
            EligibilityResult(Capability.CALIBRATION, eligible = true)
        } else {
            EligibilityResult(
                Capability.CALIBRATION,
                eligible = false,
                reason = "Missing category truth, UNKNOWN ambiguity, or no confidence"
            )
        }
    }

    // --------------------------------
    // BATCH SUMMARY
    // --------------------------------

    /**
     * Computes eligibility summary across all capabilities
     * for a batch of items. Useful for dataset profiling.
     */
    fun batchSummary(
        items: List<Triple<EvaluationItem, AiPredictionRecord?, GroundTruth?>>
    ): Map<Capability, CapabilitySummary> {

        val summaries = mutableMapOf<Capability, CapabilitySummary>()

        Capability.entries.forEach { capability ->
            val result = filterByCapability(items, capability)
            summaries[capability] = CapabilitySummary(
                capability = capability,
                eligibleCount = result.eligibleItemIds.size,
                excludedCount = result.excluded.size,
                totalItems = items.size
            )
        }

        return summaries
    }

    data class CapabilitySummary(
        val capability: Capability,
        val eligibleCount: Int,
        val excludedCount: Int,
        val totalItems: Int
    ) {
        val eligibleFraction: Double
            get() = if (totalItems > 0) eligibleCount.toDouble() / totalItems else 0.0
    }

    data class Exclusion(
        val evaluationItemId: String,
        val capability: Capability,
        val reason: String
    )

    data class CapabilityFilterResult(
        val capability: Capability,
        val eligibleItemIds: List<String>,
        val excluded: List<Exclusion>
    )
}
