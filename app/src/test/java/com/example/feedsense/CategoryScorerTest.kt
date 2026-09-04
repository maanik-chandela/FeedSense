package com.example.feedsense

import com.example.feedsense.analysis.CategoryScorer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// --------------------------------
// MILESTONE 7U: MULTI-SIGNAL CATEGORY SCORER
// --------------------------------
//
// The scorer ranks every canonical category from the
// cheap offline signals (text, hashtag, title, platform,
// memory). These tests prove each signal is a real vote
// and that the scores stay normalized and transparent.

class CategoryScorerTest {

    private val scorer =
        CategoryScorer()

    @Test
    fun textBase_producesRankedCategories() {

        val result =
            scorer.score(
                text = "Cricket ipl match highlights"
            )

        assertTrue(result.ranked.isNotEmpty())
        assertEquals("sports", result.ranked.first().category)
        assertTrue(result.scores.isNotEmpty())
        assertTrue(result.scores.values.all { it > 0.0 })
    }

    @Test
    fun topCategoryScoresAtOne() {

        val result =
            scorer.score(
                text = "Cricket ipl match highlights"
            )

        assertEquals(1.0, result.ranked.first().score, 0.0001)
    }

    @Test
    fun hashtagSignal_isADirectVote() {

        val result =
            scorer.score(
                text = "check out this #meme"
            )

        assertTrue(
            result.ranked.any {
                it.category == "meme"
            }
        )
        assertTrue(
            result.evidence.contains("hashtag:meme")
        )
        assertTrue(
            result.hashtags.contains("meme")
        )
    }

    @Test
    fun hashtagCanRankAboveWeakText() {

        val result =
            scorer.score(
                text = "hilarious clip #meme"
            )

        // meme (text + hashtag + title) should outrank
        // comedy (text only).
        assertTrue(
            result.scores.getValue("meme") >
                result.scores.getValue("comedy")
        )
    }

    @Test
    fun platformPrior_isASignal() {

        val result =
            scorer.score(
                text = "streaming moments",
                platform = "twitch"
            )

        assertTrue(
            result.ranked.any {
                it.category == "gaming" &&
                    "platform:gaming" in it.signals
            }
        )
    }

    @Test
    fun titleSignal_isRecorded() {

        val result =
            scorer.score(
                text =
                    "Ultimate video editing tutorial for beginners"
            )

        assertTrue(
            result.ranked.any {
                it.category == "tutorial" &&
                    "title:tutorial" in it.signals
            }
        )
        assertNotNullTitle(result.title)
    }

    @Test
    fun memoryCandidate_isASignal() {

        val result =
            scorer.score(
                text = "funny prank",
                memoryCandidates = setOf("meme"),
                memorySupport = 0.9
            )

        assertTrue(
            result.ranked.any {
                it.category == "meme" &&
                    "memory:meme" in it.signals
            }
        )
        assertTrue(
            result.evidence.contains("memory:meme")
        )
    }

    @Test
    fun memoryCandidateBoostsScore() {

        val without =
            scorer.score(
                text = "funny prank"
            )

        val with =
            scorer.score(
                text = "funny prank",
                memoryCandidates = setOf("meme"),
                memorySupport = 0.9
            )

        assertTrue(
            with.scores.getOrDefault("meme", 0.0) >
                without.scores.getOrDefault("meme", 0.0)
        )
    }

    @Test
    fun emptySignals_produceEmptyResult() {

        val result =
            scorer.score(
                text = null
            )

        assertTrue(result.ranked.isEmpty())
        assertTrue(result.scores.isEmpty())
        assertTrue(result.hashtags.isEmpty())
    }

    @Test
    fun evidenceIsTransparent() {

        val result =
            scorer.score(
                text = "Cricket ipl match highlights",
                platform = "youtube"
            )

        assertTrue(
            result.evidence.any {
                it.startsWith("text:")
            }
        )

        // A platform that carries a sports-free prior still
        // contributes its own (non-sports) signals.
        val newsResult =
            scorer.score(
                text = "Breaking news headlines",
                platform = "twitter"
            )

        assertTrue(
            newsResult.evidence.contains("platform:news")
        )
    }

    private fun assertNotNullTitle(
        title: String?
    ) {
        assertTrue(title != null && title.isNotBlank())
    }
}
