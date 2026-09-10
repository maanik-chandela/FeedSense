package com.example.feedsense.analysis.evaluation.experiment

import com.example.feedsense.analysis.evaluation.comparative.PairedPrediction
import com.example.feedsense.model.GroundTruth

/**
 * Milestone 8B-9.
 *
 * Dataset quality report for a controlled experiment.
 *
 * Describes the composition and quality of the selected observation
 * population so a reader can judge how informative (not just how
 * large) the experiment really is. Reports only counts and
 * controlled metadata - never raw content.
 */
data class DatasetQualityReport(
    val candidateItems: Int,
    val paired: Int,
    val eligibleForAccuracy: Int,
    val excludedFromAccuracy: Int,
    val truthAmbiguityCounts: Map<String, Int>,
    val contentByPlatform: Map<String, Int>,
    val contentByType: Map<String, Int>,
    val durationBucketCounts: Map<String, Int>,
    val distinctFeedItems: Int,
    val note: String
) {
    val eligibleRatio: Double?
        get() = if (paired == 0) null else
            eligibleForAccuracy.toDouble() / paired

    companion object {

        /**
         * Builds the report from the selected pairs plus the
         * pre-cap candidate count.
         */
        fun build(
            pairs: List<PairedPrediction>,
            candidateItems: Int
        ): DatasetQualityReport {
            val ambiguities = linkedMapOf<String, Int>()
            val platforms = linkedMapOf<String, Int>()
            val contentTypes = linkedMapOf<String, Int>()
            val durations = linkedMapOf<String, Int>()
            val feedIds = mutableSetOf<String>()

            var eligible = 0
            for (p in pairs) {
                if (p.eligibleForAccuracy) eligible++
                val amb = p.truth.ambiguity
                    ?: GroundTruth.AMBIGUITY_UNKNOWN
                ambiguities[amb] = (ambiguities[amb] ?: 0) + 1
                val plat = p.truth.platform ?: "UNKNOWN"
                platforms[plat] = (platforms[plat] ?: 0) + 1
                val ct = p.truth.contentType ?: "UNKNOWN"
                contentTypes[ct] = (contentTypes[ct] ?: 0) + 1
                val bucket = durationBucket(p.truth.durationSeconds)
                durations[bucket] = (durations[bucket] ?: 0) + 1
                p.item.feedItemId?.let { feedIds += it }
            }

            val note = if (eligible == 0) {
                "No paired observation has a comparable ground truth; " +
                    "no accuracy claim is possible."
            } else {
                "${eligible}/${pairs.size} paired observations have a " +
                    "comparable ground truth and contribute to accuracy."
            }

            return DatasetQualityReport(
                candidateItems = candidateItems,
                paired = pairs.size,
                eligibleForAccuracy = eligible,
                excludedFromAccuracy = pairs.size - eligible,
                truthAmbiguityCounts = ambiguities.toSortedMap(),
                contentByPlatform = platforms.toSortedMap(),
                contentByType = contentTypes.toSortedMap(),
                durationBucketCounts = durations.toSortedMap(),
                distinctFeedItems = feedIds.size,
                note = note
            )
        }

        private fun durationBucket(
            seconds: Int?
        ): String = when {
            seconds == null -> "UNKNOWN"
            seconds < 15 -> "under_15s"
            seconds < 30 -> "15_30s"
            seconds < 60 -> "30_60s"
            else -> "over_60s"
        }
    }
}
