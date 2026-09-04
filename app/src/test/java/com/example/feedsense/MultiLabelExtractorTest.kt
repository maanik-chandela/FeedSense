package com.example.feedsense

import com.example.feedsense.analysis.MultiLabelExtractor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// --------------------------------
// MILESTONE 7W: MULTI-LABEL EXTRACTOR
// --------------------------------
//
// Turns per-category scores into an explicit, scored
// multi-label set. Every retained label keeps its
// confidence; weak or far-behind labels are dropped; a
// label dangerously close to the primary flags the item
// as genuinely mixed/uncertain.

class MultiLabelExtractorTest {

    private val extractor =
        MultiLabelExtractor()

    @Test
    fun extractReturnsScoredLabels() {

        val result =
            extractor.extract(
                primary = "comedy",
                scores = mapOf(
                    "comedy" to 0.8,
                    "sports" to 0.5,
                    "news" to 0.1
                )
            )

        assertEquals(
            listOf("sports"),
            result.labels.map { it.category }
        )
        assertEquals(
            0.5,
            result.labels.first().score,
            0.0001
        )
    }

    @Test
    fun extractDropsWeakLabelsBelowFloor() {

        val result =
            extractor.extract(
                primary = "comedy",
                scores = mapOf(
                    "comedy" to 0.8,
                    "news" to 0.1,
                    "finance" to 0.2
                )
            )

        assertTrue(result.labels.isEmpty())
        assertFalse(result.uncertain)
        assertNull(result.reason)
    }

    @Test
    fun extractDropsLabelsFarBelowPrimary() {

        val result =
            extractor.extract(
                primary = "comedy",
                scores = mapOf(
                    "comedy" to 0.9,
                    "sports" to 0.2,
                    "music" to 0.4
                )
            )

        // 0.4 >= max(0.25, 0.9 * 0.5 = 0.45)? No.
        // 0.2 < 0.45 too -> both dropped.
        assertTrue(result.labels.isEmpty())
    }

    @Test
    fun extractIsUncertainWhenSecondLabelIsClose() {

        val result =
            extractor.extract(
                primary = "comedy",
                scores = mapOf(
                    "comedy" to 0.9,
                    "sports" to 0.8
                )
            )

        assertEquals(
            listOf("sports"),
            result.labels.map { it.category }
        )
        assertTrue(result.uncertain)
    }

    @Test
    fun extractIsNotUncertainWhenSecondLabelIsFar() {

        val result =
            extractor.extract(
                primary = "comedy",
                scores = mapOf(
                    "comedy" to 0.9,
                    "sports" to 0.5
                )
            )

        assertEquals(
            listOf("sports"),
            result.labels.map { it.category }
        )
        assertFalse(result.uncertain)
    }

    @Test
    fun extractCapsLabelCount() {

        val scores =
            buildMap {
                put("comedy", 1.0)
                listOf(
                    "sports", "news", "music",
                    "finance", "travel"
                ).forEachIndexed { index, category ->
                    put(
                        category,
                        0.9 - (index * 0.05)
                    )
                }
            }

        val result =
            extractor.extract(
                primary = "comedy",
                scores = scores
            )

        assertTrue(
            result.labels.size <=
                MultiLabelExtractor.MAX_LABELS
        )
    }

    @Test
    fun extractWithNoPrimaryIsEmpty() {

        val result =
            extractor.extract(
                primary = null,
                scores = mapOf(
                    "comedy" to 0.8,
                    "sports" to 0.7
                )
            )

        assertTrue(result.labels.isEmpty())
        assertFalse(result.uncertain)
    }

    @Test
    fun extractWithEmptyScoresIsEmpty() {

        val result =
            extractor.extract(
                primary = "comedy",
                scores = emptyMap()
            )

        assertTrue(result.labels.isEmpty())
        assertFalse(result.uncertain)
    }

    @Test
    fun extractReasonDescribesMultiLabelSet() {

        val result =
            extractor.extract(
                primary = "comedy",
                scores = mapOf(
                    "comedy" to 0.8,
                    "sports" to 0.5
                )
            )

        assertEquals(
            "multi-label:comedy+sports",
            result.reason
        )
    }

    @Test
    fun extractLabelsAreRankedByScore() {

        val result =
            extractor.extract(
                primary = "comedy",
                scores = mapOf(
                    "comedy" to 0.9,
                    "news" to 0.5,
                    "sports" to 0.7
                )
            )

        assertEquals(
            listOf("sports", "news"),
            result.labels.map { it.category }
        )
    }
}
