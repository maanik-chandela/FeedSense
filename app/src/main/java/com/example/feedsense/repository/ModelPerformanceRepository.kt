package com.example.feedsense.repository

import com.example.feedsense.dao.CaptureDao
import com.example.feedsense.dao.CloudUsageDao
import com.example.feedsense.dao.FeedItemDao
import com.example.feedsense.dao.ModelFeedbackDao
import com.example.feedsense.dao.ReferenceDao
import com.example.feedsense.analysis.AccuracyCalculator
import com.example.feedsense.analysis.ModelRateCalculator
import com.example.feedsense.model.CategoryPerformance
import com.example.feedsense.model.ConfusionPair
import com.example.feedsense.model.LabeledReference
import com.example.feedsense.model.ModelPerformanceStats
import com.example.feedsense.model.ModelVersionPerformance
import com.example.feedsense.model.PlatformPerformance

// --------------------------------
// MODEL PERFORMANCE REPOSITORY
// --------------------------------
//
// Milestone 7G.
//
// Aggregates how the local model is doing from the
// tables that already exist. Everything is computed on
// demand (no background work, no schema) so the
// evaluation screen costs nothing when it is not open.
//
// Ground truth = human-reviewed references:
//
//   VALIDATED + agreement   -> correct (AI agreed with
//                              the human label)
//   VALIDATED - agreement   -> corrected (AI was wrong;
//                              the human label becomes
//                              the new ground truth)
//   REJECTED                -> rejected (not classifiable)
//
// Confusion pairs track which predicted category the
// human most often corrected away from, so future
// milestones can tune the classifier there.
//

class ModelPerformanceRepository(
    private val referenceDao: ReferenceDao,
    private val feedItemDao: FeedItemDao,
    private val captureDao: CaptureDao,
    private val feedbackDao: ModelFeedbackDao,
    private val cloudUsageDao: CloudUsageDao,
    private val userKnowledgeRepository: UserKnowledgeRepository
) {

    suspend fun computeStats(): ModelPerformanceStats {

        val totalClassifications =
            feedItemDao.getTotalItemCount()

        val highConfidence =
            feedItemDao.getHighConfidenceItemCount()

        val uncertain =
            feedItemDao.getNeedsReviewItemCount() +
                    captureDao.getNeedsReviewFrameCount()

        val reviewed =
            referenceDao.getReviewedReferences()

        val validated =
            reviewed.filter {
                it.validationStatus ==
                    LabeledReference.VALIDATION_VALIDATED
            }

        val rejected =
            reviewed.count {
                it.validationStatus ==
                    LabeledReference.VALIDATION_REJECTED
            }

        val correct =
            validated.count {
                it.agreement == true
            }

        val corrected =
            validated.count {
                it.agreement == false
            }

        val accuracy =
            if (validated.isEmpty()) {
                0.0
            } else {
                correct.toDouble() * 100 / validated.size
            }

        val byModelVersion =
            reviewed
                .groupBy {
                    it.modelVersion ?: "unknown"
                }
                .map { (version, rows) ->

                    val versionValidated =
                        rows.count {
                            it.validationStatus ==
                                LabeledReference.VALIDATION_VALIDATED
                        }

                    val versionCorrect =
                        rows.count {
                            it.validationStatus ==
                                LabeledReference.VALIDATION_VALIDATED &&
                                it.agreement == true
                        }

                    ModelVersionPerformance(
                        modelVersion = version,
                        reviewed = versionValidated,
                        correct = versionCorrect,
                        accuracy = if (versionValidated == 0) {
                            0.0
                        } else {
                            versionCorrect.toDouble() * 100 /
                                versionValidated
                        }
                    )
                }
                .sortedByDescending {
                    it.reviewed
                }

        val confusion =
            referenceDao
                .getConfusionPairs()
                .map {
                    ConfusionPair(
                        predicted = it.predicted,
                        actual = it.actual,
                        count = it.count
                    )
                }

        // --------------------------------
        // CATEGORY-LEVEL ACCURACY (7G PART 5)
        // --------------------------------

        val byCategory =
            validated
                .groupBy {
                    it.validatedLabel ?: "unknown"
                }
                .map { (category, rows) ->

                    val categoryCorrect =
                        rows.count {
                            it.agreement == true
                        }

                    CategoryPerformance(
                        category = category,
                        reviewed = rows.size,
                        correct = categoryCorrect,
                        accuracy = if (rows.isEmpty()) {
                            0.0
                        } else {
                            categoryCorrect.toDouble() * 100 /
                                rows.size
                        }
                    )
                }
                .sortedByDescending {
                    it.reviewed
                }

        // --------------------------------
        // PLATFORM-LEVEL ACCURACY (7G PART 5)
        // --------------------------------

        val byPlatform =
            validated
                .groupBy {
                    it.platform?.takeIf { p ->
                        p.isNotBlank()
                    } ?: "unknown"
                }
                .map { (platform, rows) ->

                    val platformCorrect =
                        rows.count {
                            it.agreement == true
                        }

                    PlatformPerformance(
                        platform = platform,
                        reviewed = rows.size,
                        correct = platformCorrect,
                        accuracy = if (rows.isEmpty()) {
                            0.0
                        } else {
                            platformCorrect.toDouble() * 100 /
                                rows.size
                        }
                    )
                }
                .sortedByDescending {
                    it.reviewed
                }

        // --------------------------------
        // 7K PART 6: OBSERVED VALIDATION STATS
        // --------------------------------
        //
        // Computed from the model_feedback dataset: how
        // often a human agreed with the AI's topic/tone,
        // and how large the uncertain share of all
        // classifications is. Calibration signals, not
        // scientific accuracy claims.

        val feedback =
            feedbackDao.getAll()

        val topicAccuracy =
            AccuracyCalculator.topicAccuracy(feedback)

        val toneAccuracy =
            AccuracyCalculator.toneAccuracy(feedback)

        val uncertaintyRate =
            AccuracyCalculator.uncertaintyRate(
                uncertain = uncertain,
                total = totalClassifications
            )

        /*
         * Milestone 7O. Cross-session personalization.
         * Computed on demand from validated references
         * and the feedback dataset; never written back
         * into historical sessions.
         */
        val personalization =
            userKnowledgeRepository
                .computePersonalizationStats()

        /*
         * Milestone 7R. Cloud-teacher economics + honest
         * pipeline rates. Every number is derived from
         * tables the app already owns.
         */
        val cloudFallbackCount =
            referenceDao.getCloudSourceCount()

        val estimatedCloudCostRupees =
            cloudUsageDao.getTotalEstimatedCost()

        val cloudFallbackRate =
            ModelRateCalculator.cloudFallbackRate(
                cloudFallback = cloudFallbackCount,
                totalClassifications =
                    totalClassifications
            )

        val localAcceptanceRate =
            ModelRateCalculator.localAcceptanceRate(
                highConfidence = highConfidence,
                totalClassifications =
                    totalClassifications
            )

        val reviewRate =
            ModelRateCalculator.reviewRate(
                needsReview = uncertain,
                totalClassifications =
                    totalClassifications
            )

        val correctionRate =
            ModelRateCalculator.correctionRate(
                corrections =
                    personalization.userCorrections,
                confirmations =
                    personalization.userConfirmations
            )

        return ModelPerformanceStats(
            totalClassifications = totalClassifications,
            highConfidence = highConfidence,
            uncertain = uncertain,
            reviewed = reviewed.size,
            validated = validated.size,
            correct = correct,
            corrected = corrected,
            rejected = rejected,
            accuracy = accuracy,
            byModelVersion = byModelVersion,
            byCategory = byCategory,
            byPlatform = byPlatform,
            confusion = confusion,
            topConfusion = confusion.maxByOrNull { it.count },
            topicAccuracy = topicAccuracy,
            toneAccuracy = toneAccuracy,
            uncertaintyRate = uncertaintyRate,
            feedbackCount = feedback.size,
            personalization = personalization,
            cloudFallbackCount = cloudFallbackCount,
            cloudFallbackRate = cloudFallbackRate,
            estimatedCloudCostRupees =
                estimatedCloudCostRupees,
            localAcceptanceRate = localAcceptanceRate,
            reviewRate = reviewRate,
            correctionRate = correctionRate,
            interactionAccuracy = null
        )
    }
}
