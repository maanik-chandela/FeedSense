package com.example.feedsense

import com.example.feedsense.analysis.CategoryCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryCatalogTest {

    @Test
    fun catalog_containsExpectedCategories() {

        assertTrue(
            "sports" in CategoryCatalog.keys
        )
        assertTrue(
            "comedy" in CategoryCatalog.keys
        )
        assertTrue(
            "motivation" in CategoryCatalog.keys
        )
        assertTrue(
            "education" in CategoryCatalog.keys
        )
        assertTrue(
            "news" in CategoryCatalog.keys
        )
        assertTrue(
            "finance" in CategoryCatalog.keys
        )
        assertTrue(
            "lifestyle" in CategoryCatalog.keys
        )
        assertTrue(
            "other" in CategoryCatalog.keys
        )
    }

    @Test
    fun catalog_keysAreLowercaseAndUnique() {

        assertEquals(
            CategoryCatalog.keys.size,
            CategoryCatalog.keys.toSet().size
        )

        CategoryCatalog.keys.forEach { key ->
            assertEquals(
                key,
                key.lowercase()
            )
        }
    }

    @Test
    fun keywordMap_isExposedForClassifier() {

        assertEquals(
            CategoryCatalog.keys.size,
            CategoryCatalog.keywordMap.size
        )

        assertNotNull(
            CategoryCatalog.keywordMap["sports"]
        )
        assertTrue(
            CategoryCatalog.keywordMap["sports"]!!.isNotEmpty()
        )
    }

    @Test
    fun displayName_returnsHumanName() {

        assertEquals(
            "Sports",
            CategoryCatalog.displayName("sports")
        )
        assertEquals(
            "Finance",
            CategoryCatalog.displayName("finance")
        )
        assertEquals(
            "not_a_category",
            CategoryCatalog.displayName("not_a_category")
        )
        assertEquals(
            "Unknown",
            CategoryCatalog.displayName(null)
        )
    }

    @Test
    fun normalize_mapsKeysAndAliases() {

        assertEquals(
            "sports",
            CategoryCatalog.normalize("sports")
        )
        assertEquals(
            "technology",
            CategoryCatalog.normalize("Tech")
        )
        assertEquals(
            "sports",
            CategoryCatalog.normalize("sport")
        )
        assertEquals(
            "food",
            CategoryCatalog.normalize("Food & Cooking")
        )
        assertEquals(
            "health",
            CategoryCatalog.normalize("Health")
        )
        assertEquals(
            "health",
            CategoryCatalog.normalize("health")
        )
        assertEquals(
            "cooking",
            CategoryCatalog.normalize("Cooking")
        )
    }

    @Test
    fun normalize_returnsNullForUnknown() {

        assertNull(
            CategoryCatalog.normalize("xyzzy")
        )
        assertNull(
            CategoryCatalog.normalize(null)
        )
        assertNull(
            CategoryCatalog.normalize("")
        )
    }
}
