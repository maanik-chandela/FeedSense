package com.example.feedsense

import com.example.feedsense.analysis.TextHeuristicClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextHeuristicClassifierTest {

    private val classifier =
        TextHeuristicClassifier()

    @Test
    fun blankText_returnsEmptyResult() {

        val result =
            classifier.classify(null)

        assertNull(result.primaryCategory)
        assertNull(result.confidence)
        assertNull(result.topic)
        assertNull(result.tone)
        assertTrue(result.secondaryCategories.isEmpty())
    }

    @Test
    fun sportsText_classifiesCategory() {

        val result =
            classifier.classify(
                "Watch the ipl match live, six and wicket highlights"
            )

        assertEquals(
            "sports",
            result.primaryCategory
        )
        assertNotNull(result.confidence)
        assertTrue(result.confidence!! > 0.5)
    }

    @Test
    fun sportsText_extractsCricketTopic() {

        val result =
            classifier.classify(
                "Cricket ipl score: batsman hits a six, wicket falls"
            )

        assertEquals(
            "sports",
            result.primaryCategory
        )
        assertEquals(
            "cricket",
            result.topic
        )
    }

    @Test
    fun educationalText_detectsEducationalTone() {

        val result =
            classifier.classify(
                "Learn how to solve this math equation - tutorial"
            )

        assertEquals(
            TextHeuristicClassifier.TONE_EDUCATIONAL,
            result.tone
        )
    }

    @Test
    fun motivationalText_detectsInspirationalTone() {

        val result =
            classifier.classify(
                "Never give up, keep hustling and discipline wins"
            )

        assertEquals(
            TextHeuristicClassifier.TONE_INSPIRATIONAL,
            result.tone
        )
    }

    @Test
    fun mixedCategories_reportsAmbiguity() {

        val result =
            classifier.classify(
                "Funny comedy prank on a footballer during the match"
            )

        assertNotNull(result.primaryCategory)
        assertNotNull(result.ambiguityScore)
        assertTrue(result.secondaryCategories.isNotEmpty())
    }

    @Test
    fun techText_extractsTopic() {

        val result =
            classifier.classify(
                "New iphone review, android smartphone comparison"
            )

        assertEquals(
            "technology",
            result.primaryCategory
        )
        assertEquals(
            "smartphones",
            result.topic
        )
    }

    @Test
    fun financeText_classifiesCategory() {

        val result =
            classifier.classify(
                "Invest in mutual funds and sip, nifty hits record"
            )

        assertEquals(
            "finance",
            result.primaryCategory
        )
        assertEquals(
            "investing",
            result.topic
        )
    }

    @Test
    fun lifestyleText_classifiesCategory() {

        val result =
            classifier.classify(
                "My morning routine and daily productivity vlog"
            )

        assertEquals(
            "lifestyle",
            result.primaryCategory
        )
        assertEquals(
            "daily routine",
            result.topic
        )
    }

    @Test
    fun classifiedText_includesReason() {

        val result =
            classifier.classify(
                "Cricket ipl score: batsman hits a six, wicket falls"
            )

        assertNotNull(result.reason)
        assertTrue(result.reason!!.contains("hits:"))
        assertTrue(result.reason!!.contains("sports="))
    }

    @Test
    fun blankText_reasonIsNoText() {

        val result =
            classifier.classify(null)

        assertEquals(
            "no_text",
            result.reason
        )
    }
}