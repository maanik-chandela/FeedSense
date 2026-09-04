package com.example.feedsense

import com.example.feedsense.analysis.AnnotationValidator
import com.example.feedsense.model.GroundTruth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8A-2. Pure validation + normalization rules for
 * the human-annotation workflow:
 *
 *   1. primary-or-explicit-unknown (P1/P2)
 *   2. ambiguity required
 *   3. multi-label normalize + dedup, primary never repeated
 *   4. canonical platform / content-type / tone membership
 *   5. interaction tri-state derivation (UNKNOWN never
 *      auto-filled with false)
 */
class AnnotationValidatorTest {

    private fun draft(
        category: String? = null,
        secondary: List<String> = emptyList(),
        ambiguity: String = GroundTruth.AMBIGUITY_CLEAR,
        platform: String? = null,
        contentType: String = GroundTruth.CONTENT_TYPE_SHORT_VIDEO,
        tone: String? = null,
        liked: Boolean? = null,
        commented: Boolean? = null,
        paused: Boolean? = null
    ) = GroundTruth(
        evaluationItemId = "item-1",
        category = category,
        secondaryCategories = secondary,
        ambiguity = ambiguity,
        platform = platform,
        contentType = contentType,
        tone = tone,
        liked = liked,
        commented = commented,
        paused = paused
    )

    private fun run(d: GroundTruth) = AnnotationValidator.validate(d)

    // --------------------------------
    // P1/P2: primary or explicit unknown
    // --------------------------------

    @Test
    fun primaryCategoryIsAccepted_withClearAmbiguity() {
        val result = run(
            draft(category = "cricket", ambiguity = GroundTruth.AMBIGUITY_CLEAR)
        )
        assertTrue(result.isValid)
        assertEquals("cricket", result.normalized?.category)
    }

    @Test
    fun unknownIsLegitimate_whenExplicitlyMarkedUnknown() {
        val result = run(
            draft(category = null, ambiguity = GroundTruth.AMBIGUITY_UNKNOWN)
        )
        assertTrue(
            "UNKNOWN is a human judgment, not an AI error",
            result.isValid
        )
        assertNull(result.normalized?.category)
    }

    @Test
    fun missingPrimary_withoutUnknown_isRejected() {
        val result = run(
            draft(category = null, ambiguity = GroundTruth.AMBIGUITY_CLEAR)
        )
        assertFalse(result.isValid)
        assertTrue(
            result.errors.any { it.field == AnnotationValidator.FIELD_PRIMARY_CATEGORY }
        )
    }

    @Test
    fun blankAmbiguity_isRejected() {
        val result = run(draft(category = "sports", ambiguity = "  "))
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == AnnotationValidator.FIELD_AMBIGUITY })
    }

    // --------------------------------
    // P3: multi-label normalize + dedup
    // --------------------------------

    @Test
    fun secondaryCategories_areNormalizedAndDeDuplicated() {
        val result = run(
            draft(
                category = "sports",
                secondary = listOf(
                    "comedy",
                    "COMEDY",
                    "Sports" // duplicate of primary - dropped
                )
            )
        )
        assertTrue(result.isValid)
        assertEquals(
            listOf("comedy"),
            result.normalized?.secondaryCategories
        )
    }

    @Test
    fun unknownSecondaryNormalizesToNull_andIsDropped() {
        val result = run(
            draft(
                category = "sports",
                secondary = listOf("not-a-category")
            )
        )
        assertTrue(result.isValid)
        assertTrue(result.normalized!!.secondaryCategories.isEmpty())
    }

    // --------------------------------
    // P4-P6: canonical taxonomy membership
    // --------------------------------

    @Test
    fun nonCanonicalPlatform_isRejected() {
        val result = run(
            draft(category = "sports", platform = "MySpace")
        )
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == AnnotationValidator.FIELD_PLATFORM })
    }

    @Test
    fun canonicalPlatform_isAccepted() {
        val result = run(
            draft(category = "sports", platform = "Instagram")
        )
        assertTrue(result.isValid)
        assertEquals("Instagram", result.normalized?.platform)
    }

    @Test
    fun nonCanonicalContentType_isRejected() {
        val result = run(
            draft(contentType = "CAROUSEL")
        )
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == AnnotationValidator.FIELD_CONTENT_TYPE })
    }

    @Test
    fun otherContentType_isAccepted_asGroundTruth() {
        val result = run(
            draft(
                category = "sports",
                contentType = GroundTruth.CONTENT_TYPE_OTHER
            )
        )
        assertTrue(result.isValid)
    }

    @Test
    fun nonCanonicalTone_isRejected() {
        val result = run(
            draft(tone = "serious")
        )
        assertFalse(result.isValid)
        assertTrue(result.errors.any { it.field == AnnotationValidator.FIELD_TONE })
    }

    // --------------------------------
    // P7: interaction tri-state derivation
    // --------------------------------

    @Test
    fun onlyInteractionsMarkedTrue_appearInSignals() {
        val result = run(
            draft(
                category = "sports",
                liked = true,
                commented = null, // UNKNOWN
                paused = false
            )
        )
        assertTrue(result.isValid)
        assertEquals(
            listOf(GroundTruth.INTERACTION_LIKED),
            result.normalized?.interactionSignals
        )
    }

    @Test
    fun unknownIsNeverAutoFilledWithFalse() {
        val result = run(
            draft(category = "sports", liked = null)
        )
        val normalized = result.normalized
        assertNotNull(normalized)
        // UNKNOWN stays null; false is only ever an explicit choice.
        assertEquals(null, normalized!!.liked)
        assertFalse(
            normalized.interactionSignals.contains(GroundTruth.INTERACTION_LIKED)
        )
    }
}