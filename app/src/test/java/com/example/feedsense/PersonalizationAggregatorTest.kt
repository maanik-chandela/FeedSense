package com.example.feedsense

import com.example.feedsense.analysis.PersonalizationAggregator
import com.example.feedsense.model.LabeledReference
import com.example.feedsense.model.ModelFeedback
import com.example.feedsense.model.PersonalizationStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalizationAggregatorTest {

    private fun reference(
        category: String?,
        platform: String? = null,
        topic: String? = null,
        tone: String? = null,
        validated: Boolean = true
    ): LabeledReference {

        return LabeledReference(
            frameId = "f-${category.hashCode()}-${platform}-${topic}",
            sessionId = "s",
            filePath = "/tmp/x.png",
            feedItemId = null,
            aiCategory = category,
            aiConfidence = 0.5,
            aiSource = "LOCAL",
            modelVersion = "local-v5.1",
            platform = platform,
            topic = topic,
            tone = tone,
            labelSource = "HUMAN",
            validationStatus = if (validated) {
                LabeledReference.VALIDATION_VALIDATED
            } else {
                LabeledReference.VALIDATION_PENDING
            },
            validatedLabel = category
        )
    }

    private fun feedback(
        agreed: Boolean
    ): ModelFeedback {

        return ModelFeedback(
            frameId = "f",
            sessionId = "s",
            feedItemId = null,
            originalCategory = "comedy",
            correctedCategory = if (agreed) "comedy" else "gaming",
            correctionSource = ModelFeedback.SOURCE_USER,
            categoryAgreement = agreed
        )
    }

    @Test
    fun onlyValidatedReferencesCount() {

        val stats =
            PersonalizationAggregator.aggregate(
                validatedReferences = listOf(
                    reference("comedy"),
                    reference("comedy"),
                    reference("gaming"),
                    reference("comedy", validated = false)
                ),
                feedback = emptyList()
            )

        assertEquals(3, stats.totalValidatedExamples)
        assertEquals("comedy", stats.topCategories.first().key)
        assertEquals(2, stats.topCategories.first().count)
    }

    @Test
    fun rankingsTieBreakAlphabetically() {

        val stats =
            PersonalizationAggregator.aggregate(
                validatedReferences = listOf(
                    reference("gaming"),
                    reference("comedy")
                ),
                feedback = emptyList()
            )

        assertEquals(
            listOf("comedy", "gaming"),
            stats.topCategories.map { it.key }
        )
    }

    @Test
    fun platformTopicToneAggregated() {

        val stats =
            PersonalizationAggregator.aggregate(
                validatedReferences = listOf(
                    reference(
                        "comedy",
                        platform = "instagram",
                        topic = "skits",
                        tone = "funny"
                    ),
                    reference(
                        "comedy",
                        platform = "instagram",
                        topic = "skits",
                        tone = "funny"
                    ),
                    reference(
                        "gaming",
                        platform = "youtube",
                        topic = "valorant",
                        tone = "excited"
                    )
                ),
                feedback = emptyList()
            )

        assertEquals("instagram", stats.topPlatforms.first().key)
        assertEquals(2, stats.topPlatforms.first().count)
        assertEquals("skits", stats.topTopics.first().key)
        assertEquals("funny", stats.topTones.first().key)
    }

    @Test
    fun blankAndNullValuesAreIgnored() {

        val stats =
            PersonalizationAggregator.aggregate(
                validatedReferences = listOf(
                    reference("comedy", topic = ""),
                    reference("comedy", topic = "  "),
                    reference("comedy", topic = null)
                ),
                feedback = emptyList()
            )

        assertEquals(3, stats.totalValidatedExamples)
        assertTrue(stats.topTopics.isEmpty())
    }

    @Test
    fun correctionRateFromUserFeedback() {

        val stats =
            PersonalizationAggregator.aggregate(
                validatedReferences = emptyList(),
                feedback = listOf(
                    feedback(agreed = false),
                    feedback(agreed = true),
                    feedback(agreed = false)
                )
            )

        assertEquals(2, stats.userCorrections)
        assertEquals(1, stats.userConfirmations)
        assertEquals(66.7, stats.correctionRate, 0.1)
    }

    @Test
    fun noFeedbackMeansZeroRates() {

        val stats =
            PersonalizationAggregator.aggregate(
                validatedReferences = emptyList(),
                feedback = emptyList()
            )

        assertEquals(0, stats.userCorrections)
        assertEquals(0.0, stats.correctionRate, 0.001)
        assertEquals(
            PersonalizationStats.EMPTY.correctionRate,
            stats.correctionRate,
            0.001
        )
    }

    @Test
    fun cloudFeedbackDoesNotCountAsUserCorrection() {

        val cloudFeedback =
            ModelFeedback(
                frameId = "f",
                sessionId = "s",
                feedItemId = null,
                originalCategory = "comedy",
                correctedCategory = "gaming",
                correctionSource =
                    ModelFeedback.SOURCE_CLOUD_REFERENCE,
                categoryAgreement = false
            )

        val stats =
            PersonalizationAggregator.aggregate(
                validatedReferences = emptyList(),
                feedback = listOf(cloudFeedback)
            )

        assertEquals(0, stats.userCorrections)
    }

    @Test
    fun topCategoriesBoundedToFive() {

        val references =
            (1..8).map {
                reference("category-$it")
            }

        val stats =
            PersonalizationAggregator.aggregate(
                validatedReferences = references,
                feedback = emptyList()
            )

        assertEquals(5, stats.topCategories.size)
    }
}
