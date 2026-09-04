package com.example.feedsense

import com.example.feedsense.analysis.MixedContentAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MixedContentAnalyzerTest {

    private val analyzer =
        MixedContentAnalyzer()

    private fun decide(
        primary: String?,
        confidence: Double?,
        counts: Map<String, Int>,
        ocrSecondaries: List<String> = emptyList()
    ): MixedContentAnalyzer.Decision {

        return analyzer.analyze(
            primaryCategory = primary,
            confidence = confidence,
            frameCategoryCounts = counts,
            frameSecondaryCandidates = ocrSecondaries
        )
    }

    @Test
    fun cleanSingleCategoryIsNotMixed() {

        val decision =
            decide(
                primary = "comedy",
                confidence = 0.8,
                counts = mapOf("comedy" to 10)
            )

        assertFalse(decision.mixedContent)
        assertTrue(decision.secondaryCategories.isEmpty())
    }

    @Test
    fun comedySkitWithSportsCameoIsMixed() {

        val decision =
            decide(
                primary = "comedy",
                confidence = 0.65,
                counts = mapOf("comedy" to 4, "sports" to 3)
            )

        assertTrue(decision.mixedContent)
        assertEquals(
            listOf("sports"),
            decision.secondaryCategories
        )
        assertTrue(decision.reason!!.contains("comedy+sports"))
    }

    @Test
    fun dominantPrimaryIsNotMixed() {

        val decision =
            decide(
                primary = "comedy",
                confidence = 0.8,
                counts = mapOf("comedy" to 10, "sports" to 1)
            )

        assertFalse(decision.mixedContent)
    }

    @Test
    fun rankingOrderedBySupportThenName() {

        val decision =
            decide(
                primary = "comedy",
                confidence = 0.5,
                counts = mapOf(
                    "comedy" to 5,
                    "sports" to 4,
                    "gaming" to 3
                )
            )

        assertEquals(
            listOf("sports", "gaming"),
            decision.secondaryCategories
        )
    }

    @Test
    fun noPrimaryCategoryIsNeverMixed() {

        val decision =
            decide(
                primary = null,
                confidence = 0.3,
                counts = mapOf(
                    "comedy" to 3,
                    "sports" to 3
                )
            )

        assertFalse(decision.mixedContent)
        assertTrue(decision.secondaryCategories.isEmpty())
    }

    @Test
    fun lowConfidenceOcrSecondaryMakesItMixed() {

        val decision =
            decide(
                primary = "gaming",
                confidence = 0.4,
                counts = mapOf("gaming" to 5),
                ocrSecondaries = listOf("comedy")
            )

        assertTrue(decision.mixedContent)
        assertEquals(
            listOf("comedy"),
            decision.secondaryCategories
        )
    }

    @Test
    fun highConfidenceOcrSecondaryIsNotPromoted() {

        val decision =
            decide(
                primary = "gaming",
                confidence = 0.8,
                counts = mapOf("gaming" to 5),
                ocrSecondaries = listOf("comedy")
            )

        assertFalse(decision.mixedContent)
    }

    @Test
    fun secondaryCategoriesBoundedToTwo() {

        val decision =
            decide(
                primary = "comedy",
                confidence = 0.4,
                counts = mapOf(
                    "comedy" to 5,
                    "sports" to 4,
                    "gaming" to 4,
                    "music" to 4
                )
            )

        assertEquals(2, decision.secondaryCategories.size)
    }

    @Test
    fun emptyCountsIsNotMixed() {

        val decision =
            decide(
                primary = "comedy",
                confidence = 0.5,
                counts = emptyMap()
            )

        assertFalse(decision.mixedContent)
    }
}
