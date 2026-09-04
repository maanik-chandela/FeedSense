package com.example.feedsense.analysis

import com.example.feedsense.model.LabeledReference
import com.example.feedsense.model.ModelFeedback
import com.example.feedsense.model.PersonalizationEntry
import com.example.feedsense.model.PersonalizationStats

// --------------------------------
// PERSONALIZATION AGGREGATOR
// --------------------------------
//
// Milestone 7O.
//
// Pure aggregation of cross-session user knowledge.
// Takes the two datasets the app already owns:
//
//   validated references - what the user actually
//                          watched (trusted memory).
//   model_feedback       - user confirmations and
//                          corrections of the AI.
//
// and produces the PersonalizationStats shown on the
// evaluation screen. Kept pure (no Room, no I/O) so
// the aggregation logic is unit-testable.
//
// Rules:
//
//   - Only VALIDATED references count as knowledge.
//     PENDING/REJECTED/SKIPPED rows are ignored.
//   - Rankings are counts only; ties break by key so
//     output is deterministic.
//   - Corrections are counted from USER rows in
//     model_feedback (categoryAgreement false = the
//     user fixed the AI, true = the user confirmed it).
//

object PersonalizationAggregator {

    fun aggregate(
        validatedReferences: List<LabeledReference>,
        feedback: List<ModelFeedback>
    ): PersonalizationStats {

        /*
         * Pure-layer guard: only VALIDATED references
         * become knowledge, even if a caller passes
         * pending/rejected rows.
         */
        val trusted =
            validatedReferences.filter {
                it.validationStatus ==
                    LabeledReference.VALIDATION_VALIDATED
            }

        val categoryCounts =
            frequencyOf(trusted) {
                it.validatedLabel
            }

        val platformCounts =
            frequencyOf(trusted) {
                it.platform
            }

        val topicCounts =
            frequencyOf(trusted) {
                it.topic
            }

        val toneCounts =
            frequencyOf(trusted) {
                it.tone
            }

        val userFeedback =
            feedback.filter {
                it.correctionSource ==
                    ModelFeedback.SOURCE_USER
            }

        val userCorrections =
            userFeedback.count {
                it.categoryAgreement == false
            }

        val userConfirmations =
            userFeedback.count {
                it.categoryAgreement == true
            }

        val correctionRate =
            if (userFeedback.isEmpty()) {
                0.0
            } else {
                userCorrections.toDouble() * 100 /
                    userFeedback.size
            }

        val observedAccuracyPercent =
            if (userFeedback.isEmpty()) {
                0.0
            } else {
                userConfirmations.toDouble() * 100 /
                    userFeedback.size
            }

        return PersonalizationStats(
            totalValidatedExamples =
                trusted.size,
            topCategories =
                top(categoryCounts),
            topPlatforms =
                top(platformCounts),
            topTopics =
                top(topicCounts),
            topTones =
                top(toneCounts),
            userCorrections = userCorrections,
            userConfirmations = userConfirmations,
            correctionRate = correctionRate,
            observedAccuracyPercent =
                observedAccuracyPercent
        )
    }

    private fun frequencyOf(
        references: List<LabeledReference>,
        value: (LabeledReference) -> String?
    ): Map<String, Int> {

        return references
            .mapNotNull { reference ->
                value(reference)
                    ?.trim()
                    ?.takeIf {
                        it.isNotEmpty()
                    }
            }
            .groupingBy { it }
            .eachCount()
    }

    private fun top(
        counts: Map<String, Int>,
        limit: Int = 5
    ): List<PersonalizationEntry> {

        return counts
            .entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> {
                    it.value
                }.thenBy {
                    it.key
                }
            )
            .take(limit)
            .map {
                PersonalizationEntry(
                    key = it.key,
                    count = it.value
                )
            }
    }
}
