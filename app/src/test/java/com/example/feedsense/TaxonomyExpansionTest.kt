package com.example.feedsense

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.analysis.TextHeuristicClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// --------------------------------
// MILESTONE 7U: TAXONOMY EXPANSION
// --------------------------------
//
// The catalog grows from 14 categories to the full
// 62-key taxonomy. These tests pin the canonical key
// set and prove the new categories are actually
// detected by the text heuristic.
//
// MILESTONE 7V: 8 hierarchy subcategory leaves are
// added (cricket, football, basketball, tennis,
// other_sport, school, university, product_promotion)
// bringing the canonical set to 70 keys.

class TaxonomyExpansionTest {

    private val classifier =
        TextHeuristicClassifier()

    private fun classify(
        text: String
    ): String? {
        return classifier
            .classify(text)
            .primaryCategory
    }

    @Test
    fun catalog_hasTheFullTaxonomy() {

        val expected = listOf(
            "sports", "news", "politics", "education",
            "technology", "science", "health", "fitness",
            "food", "cooking", "travel", "finance",
            "business", "productivity", "motivation",
            "self_improvement", "comedy", "meme",
            "entertainment", "movie_clip", "series_clip",
            "music", "music_video", "edit", "creator_edit",
            "youtuber_edit", "gaming", "gameplay", "esports",
            "anime", "animation", "documentary", "podcast",
            "interview", "ranking", "top_list", "tutorial",
            "how_to", "review", "product_review",
            "advertisement", "sponsored_content",
            "influencer_content", "lifestyle", "fashion",
            "beauty", "relationships", "motivational_speech",
            "storytelling", "horror", "crime", "drama",
            "romance", "action", "reaction", "commentary",
            "discussion", "live_stream", "short_video",
            "long_video", "other", "unknown",
            "cricket", "football", "basketball", "tennis",
            "other_sport", "school", "university",
            "product_promotion"
        )

        assertEquals(expected.size, CategoryCatalog.keys.size)
        assertEquals(expected.toSet(), CategoryCatalog.keys.toSet())
        assertEquals(
            CategoryCatalog.keys.size,
            CategoryCatalog.keys.toSet().size
        )

        expected.forEach { key ->
            assertTrue(key in CategoryCatalog.keys)
        }
    }

    @Test
    fun everyCategoryHasKeywordsOrIsAnExplicitFallback() {

        CategoryCatalog.keys.forEach { key ->
            if (key != "other" && key != "unknown") {
                assertTrue(
                    "category '$key' needs keywords",
                    CategoryCatalog
                        .keywordMap[key]!!
                        .isNotEmpty()
                )
            }
        }
    }

    @Test
    fun normalize_resolvesNewAliases() {

        assertEquals(
            "movie_clip",
            CategoryCatalog.normalize("Movie Clip")
        )
        assertEquals(
            "music_video",
            CategoryCatalog.normalize("music video")
        )
        assertEquals(
            "live_stream",
            CategoryCatalog.normalize("Live Stream")
        )
        assertEquals(
            "sponsored_content",
            CategoryCatalog.normalize("sponsored content")
        )
        assertEquals(
            "self_improvement",
            CategoryCatalog.normalize("self improvement")
        )
        assertEquals(
            "product_review",
            CategoryCatalog.normalize("product review")
        )
        assertEquals(
            "how_to",
            CategoryCatalog.normalize("How To")
        )
    }

    @Test
    fun displayName_coversNewCategories() {

        assertEquals(
            "Movie Clip",
            CategoryCatalog.displayName("movie_clip")
        )
        assertEquals(
            "Self Improvement",
            CategoryCatalog.displayName("self_improvement")
        )
        assertEquals(
            "Unknown",
            CategoryCatalog.displayName("unknown")
        )
    }

    @Test
    fun memeText_classifiesMeme() {

        assertEquals(
            "meme",
            classify(
                "dank meme template compilation, too relatable"
            )
        )
    }

    @Test
    fun adText_classifiesAdvertisement() {

        assertEquals(
            "advertisement",
            classify(
                "Buy now, limited offer, shop now with promo code"
            )
        )
    }

    @Test
    fun movieClipText_classifiesMovieClip() {

        assertEquals(
            "movie_clip",
            classify(
                "New movie trailer out, film teaser and release date"
            )
        )
    }

    @Test
    fun rankingText_classifiesRanking() {

        assertEquals(
            "ranking",
            classify(
                "Top 10 best of cricket moments ranking"
            )
        )
    }

    @Test
    fun creatorEditText_classifiesCreatorEdit() {

        assertEquals(
            "creator_edit",
            classify(
                "Creator edit with after effects vfx and transition"
            )
        )
    }

    @Test
    fun motivationalSpeechText_classifiesMotivationalSpeech() {

        assertEquals(
            "motivational_speech",
            classify(
                "Powerful motivational speech and motivational quotes"
            )
        )
    }

    @Test
    fun shortSkippedContent_classifiesShortVideo() {

        assertEquals(
            "short_video",
            classify(
                "Vertical short video reels tiktok video"
            )
        )
    }

    @Test
    fun longWatchedContent_classifiesLongVideo() {

        assertEquals(
            "long_video",
            classify(
                "Full long video watch till end complete video"
            )
        )
    }

    @Test
    fun newCategoriesRetainTopicDetection() {

        val result =
            classifier.classify(
                "Cricket ipl score: batsman hits a six, wicket falls"
            )

        assertEquals("sports", result.primaryCategory)
        assertEquals("cricket", result.topic)
        assertNotNull(result.confidence)
    }
}
