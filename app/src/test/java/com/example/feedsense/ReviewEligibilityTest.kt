package com.example.feedsense

import com.example.feedsense.analysis.ReviewEligibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewEligibilityTest {

    private val eligibility =
        ReviewEligibility()

    private fun decide(
        baseUncertain: Boolean = false,
        conflict: Boolean = false,
        categories: List<String?> = listOf("comedy"),
        secondaryCategory: String? = null,
        topics: List<String?> = listOf("skit"),
        tones: List<String?> = listOf("humorous"),
        applications: List<String?> = listOf("Instagram"),
        unknownCategory: Boolean = false,
        unknownTopic: Boolean = false,
        unknownTone: Boolean = false
    ): ReviewEligibility.Decision {

        return eligibility.decide(
            baseUncertain = baseUncertain,
            conflict = conflict,
            categories = categories,
            secondaryCategory = secondaryCategory,
            topics = topics,
            tones = tones,
            applications = applications,
            unknownCategory = unknownCategory,
            unknownTopic = unknownTopic,
            unknownTone = unknownTone
        )
    }

    @Test
    fun confidentItemNeedsNoReview() {

        val decision = decide()

        assertFalse(decision.needsReview)
        assertTrue(decision.reasons.isEmpty())
    }

    /*
     * Milestone 7P. Honest-to-unknown: an item the
     * classifier could not place in a category/topic/tone
     * is surfaced for review instead of pretending.
     */
    @Test
    fun unknownCategoryNeedsReview() {

        val decision =
            decide(unknownCategory = true)

        assertTrue(decision.needsReview)
        assertTrue(
            ReviewEligibility.REASON_UNKNOWN_CATEGORY
                in decision.reasons
        )
    }

    @Test
    fun unknownTopicNeedsReview() {

        val decision =
            decide(unknownTopic = true)

        assertTrue(decision.needsReview)
        assertTrue(
            ReviewEligibility.REASON_UNKNOWN_TOPIC
                in decision.reasons
        )
    }

    @Test
    fun unknownToneNeedsReview() {

        val decision =
            decide(unknownTone = true)

        assertTrue(decision.needsReview)
        assertTrue(
            ReviewEligibility.REASON_UNKNOWN_TONE
                in decision.reasons
        )
    }

    @Test
    fun allUnknownFlagsCombineIntoOneDecision() {

        val decision =
            decide(
                unknownCategory = true,
                unknownTopic = true,
                unknownTone = true
            )

        assertTrue(decision.needsReview)
        assertEquals(
            setOf(
                ReviewEligibility.REASON_UNKNOWN_CATEGORY,
                ReviewEligibility.REASON_UNKNOWN_TOPIC,
                ReviewEligibility.REASON_UNKNOWN_TONE
            ),
            decision.reasons.toSet()
        )
    }

    @Test
    fun lowConfidenceItemNeedsReview() {

        val decision =
            decide(baseUncertain = true)

        assertTrue(decision.needsReview)
        assertTrue(
            ReviewEligibility.REASON_LOW_CONFIDENCE
                in decision.reasons
        )
    }

    @Test
    fun referenceConflictNeedsReview() {

        val decision =
            decide(conflict = true)

        assertTrue(decision.needsReview)
        assertTrue(
            ReviewEligibility.REASON_CONFLICTING_SIGNALS
                in decision.reasons
        )
    }

    @Test
    fun categoryTieNeedsReview() {

        val decision =
            decide(
                categories = listOf(
                    "comedy",
                    "comedy",
                    "sports",
                    "sports"
                )
            )

        assertTrue(decision.needsReview)
        assertTrue(
            ReviewEligibility.REASON_CATEGORY_TIE
                in decision.reasons
        )
    }

    @Test
    fun clearCategoryMajorityIsNotATie() {

        val decision =
            decide(
                categories = listOf(
                    "comedy",
                    "comedy",
                    "comedy",
                    "sports"
                )
            )

        assertFalse(decision.needsReview)
    }

    @Test
    fun mixedContentNeedsReview() {

        val decision =
            decide(secondaryCategory = "sports")

        assertTrue(decision.needsReview)
        assertTrue(
            ReviewEligibility.REASON_MIXED_CONTENT
                in decision.reasons
        )
    }

    @Test
    fun ambiguousTopicNeedsReview() {

        val decision =
            decide(
                topics = listOf(
                    "cooking",
                    "recipe",
                    "prank"
                )
            )

        assertTrue(decision.needsReview)
        assertTrue(
            ReviewEligibility.REASON_TOPIC_AMBIGUOUS
                in decision.reasons
        )
    }

    @Test
    fun singleTopicIsNotAmbiguous() {

        val decision =
            decide(
                topics = listOf(
                    "cooking",
                    "cooking",
                    "cooking"
                )
            )

        assertFalse(decision.needsReview)
    }

    @Test
    fun platformConflictNeedsReview() {

        val decision =
            decide(
                applications = listOf(
                    "Instagram",
                    "Instagram",
                    "TikTok"
                )
            )

        assertTrue(decision.needsReview)
        assertTrue(
            ReviewEligibility.REASON_PLATFORM_CONFLICT
                in decision.reasons
        )
    }

    @Test
    fun multipleReasonsAreAllRecorded() {

        val decision =
            decide(
                baseUncertain = true,
                conflict = true,
                secondaryCategory = "sports"
            )

        assertTrue(decision.needsReview)
        assertEquals(
            setOf(
                ReviewEligibility.REASON_LOW_CONFIDENCE,
                ReviewEligibility.REASON_CONFLICTING_SIGNALS,
                ReviewEligibility.REASON_MIXED_CONTENT
            ),
            decision.reasons.toSet()
        )
    }
}
