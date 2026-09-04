package com.example.feedsense

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.analysis.ClassificationResult
import com.example.feedsense.analysis.HierarchyClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// --------------------------------
// MILESTONE 7V: HIERARCHY CLASSIFIER
// --------------------------------
//
// The hierarchy layer refines a domain-level prediction
// into a specific subcategory ONLY when the subcategory
// evidence is a clear, strong winner. These tests pin:
//
//   - the catalog hierarchy (parent/children/domain)
//   - when refinement fires and when it refuses
//   - the transparency of the refinement (evidence +
//     secondary list cleanup)

class HierarchyClassifierTest {

    private val classifier =
        HierarchyClassifier()

    private fun result(
        primary: String?,
        secondary: List<String> = emptyList(),
        reason: String? = null
    ): ClassificationResult {
        return ClassificationResult(
            primaryCategory = primary,
            secondaryCategories = secondary,
            confidence = 0.8,
            ambiguityScore = 0.0,
            reason = reason
        )
    }

    @Test
    fun sportsRefinesToCricketWithClearWinner() {

        val refined =
            classifier.refine(
                result(
                    primary = "sports",
                    secondary = listOf("cricket")
                ),
                text = "Cricket ipl match, batsman hits a wicket"
            )

        assertEquals("cricket", refined.primaryCategory)
    }

    @Test
    fun refinementAddsHierarchyEvidence() {

        val refined =
            classifier.refine(
                result(
                    primary = "sports",
                    reason = "hits:sports=6"
                ),
                text = "Cricket ipl match, batsman hits a wicket"
            )

        assertTrue(
            refined.reason!!.contains(
                "hierarchy:sports->cricket"
            )
        )
        assertTrue(
            refined.reason!!.startsWith("hits:sports=6")
        )
    }

    @Test
    fun refinedChildMovesOutOfSecondary() {

        val refined =
            classifier.refine(
                result(
                    primary = "sports",
                    secondary = listOf("cricket", "news")
                ),
                text = "Cricket ipl match, batsman hits a wicket"
            )

        assertEquals("cricket", refined.primaryCategory)
        assertTrue("news" in refined.secondaryCategories)
        assertFalse("cricket" in refined.secondaryCategories)
    }

    @Test
    fun singleKeywordDoesNotRefine() {

        val refined =
            classifier.refine(
                result(primary = "sports"),
                text = "cricket mentioned once"
            )

        assertEquals("sports", refined.primaryCategory)
    }

    @Test
    fun tieBetweenChildrenKeepsDomain() {

        val refined =
            classifier.refine(
                result(primary = "sports"),
                text = "cricket world cup and football world cup"
            )

        // cricket: cricket + world cup = 2
        // football: football + world cup = 2 -> tie.
        assertEquals("sports", refined.primaryCategory)
    }

    @Test
    fun nonDomainPrimaryIsUntouched() {

        val original =
            result(primary = "news")

        assertEquals(
            original,
            classifier.refine(original, "breaking news")
        )
    }

    @Test
    fun nullTextIsUntouched() {

        val original =
            result(primary = "sports")

        assertEquals(
            original,
            classifier.refine(original, null)
        )
    }

    @Test
    fun unknownPrimaryIsUntouched() {

        val original =
            result(primary = null)

        assertEquals(
            original,
            classifier.refine(original, "cricket ipl wicket")
        )
    }

    @Test
    fun schoolRefinesUnderEducation() {

        val refined =
            classifier.refine(
                result(primary = "education"),
                text = "school classroom, the teacher gives homework"
            )

        assertEquals("school", refined.primaryCategory)
    }

    @Test
    fun productPromotionRefinesUnderAdvertising() {

        val refined =
            classifier.refine(
                result(primary = "advertising"),
                text =
                    "product promotion with limited stock " +
                        "launch offer"
            )

        assertEquals(
            "product_promotion",
            refined.primaryCategory
        )
    }

    @Test
    fun childrenOf_returnsLeaves() {

        assertEquals(
            listOf(
                "cricket", "football", "basketball",
                "tennis", "other_sport"
            ),
            CategoryCatalog.childrenOf("sports")
        )

        assertEquals(
            listOf("school", "university"),
            CategoryCatalog.childrenOf("education")
                .filter {
                    it == "school" || it == "university"
                }
        )
    }

    @Test
    fun isDomain_distinguishesDomainsFromLeaves() {

        assertTrue(CategoryCatalog.isDomain("sports"))
        assertTrue(CategoryCatalog.isDomain("entertainment"))
        assertTrue(CategoryCatalog.isDomain("advertising"))
        assertFalse(CategoryCatalog.isDomain("cricket"))
        assertFalse(CategoryCatalog.isDomain("news"))
    }

    @Test
    fun domainOf_walksToRoot() {

        assertEquals(
            "sports",
            CategoryCatalog.domainOf("cricket")
        )
        assertEquals(
            "sports",
            CategoryCatalog.domainOf("sports")
        )
        assertEquals(
            "advertising",
            CategoryCatalog.domainOf("advertisement")
        )
        assertEquals(
            "entertainment",
            CategoryCatalog.domainOf("meme")
        )
        assertEquals(
            "news",
            CategoryCatalog.domainOf("news")
        )
        assertNull(
            CategoryCatalog.domainOf(null)
        )
    }

    @Test
    fun everyChildHasAParentAndEveryParentOwnsChildren() {

        val leaves =
            CategoryCatalog.keys.filter {
                CategoryCatalog.parentOf(it) != null
            }

        assertTrue(leaves.isNotEmpty())

        leaves.forEach { leaf ->
            val parent =
                CategoryCatalog.parentOf(leaf)!!
            assertTrue(
                "parent $parent must be a domain",
                CategoryCatalog.isDomain(parent)
            )
            assertTrue(
                "parent $parent must own leaf $leaf",
                leaf in CategoryCatalog.childrenOf(parent)
            )
        }

        CategoryCatalog.domains.forEach { domain ->
            assertTrue(
                "domain $domain must own at least one child",
                CategoryCatalog.childrenOf(domain).isNotEmpty()
            )
        }
    }
}
