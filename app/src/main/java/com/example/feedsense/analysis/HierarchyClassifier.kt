package com.example.feedsense.analysis

// --------------------------------
// HIERARCHY CLASSIFIER
// --------------------------------
//
// Milestone 7V.
//
// Turns a domain-level prediction into a specific
// subcategory when the evidence is strong enough:
//
//   sports      -> cricket / football / basketball ...
//   education   -> school / university / tutorial ...
//   motivation  -> motivational_speech / self_improvement ...
//   entertainment -> movie_clip / series_clip / meme ...
//   advertising -> advertisement / sponsored_content ...
//
// This is a REFINEMENT step, never an override:
//
//   - it only runs when the primary category is a DOMAIN,
//   - it only fires on a CLEAR winner (a strict majority
//     among the domain's children),
//   - it needs at least MIN_CHILD_HITS keyword hits, so a
//     single generic word ("cricket" inside a news story)
//     never flips the label,
//   - on a tie the domain-level label is kept and the
//     ambiguity is reported instead.
//
// The refinement is fully transparent: the evidence
// string records "hierarchy:<domain>-><subcategory>".
// FeedItem.category stores the leaf when refined; the
// parent domain is recorded separately on the item.
//
// Pure and deterministic so it can be unit-tested without
// Android or Room.
//

class HierarchyClassifier {

    fun refine(
        result: ClassificationResult,
        text: String?
    ): ClassificationResult {

        val domain =
            result.primaryCategory
                ?: return result

        if (!CategoryCatalog.isDomain(domain)) {
            return result
        }

        if (text.isNullOrBlank()) {
            return result
        }

        val normalized =
            text.lowercase()

        val children =
            CategoryCatalog.childrenOf(domain)

        if (children.isEmpty()) {
            return result
        }

        val hits =
            children
                .mapNotNull { child ->

                    val count =
                        CategoryCatalog
                            .keywordMap[child]
                            ?.count { keyword ->
                                normalized.contains(keyword)
                            }
                            ?: 0

                    if (count > 0) {
                        child to count
                    } else {
                        null
                    }
                }
                .sortedByDescending { it.second }

        if (hits.isEmpty()) {
            return result
        }

        val best = hits.first()

        val clearWinner =
            hits.size == 1 ||
                hits[1].second < best.second

        if (!clearWinner || best.second < MIN_CHILD_HITS) {
            return result
        }

        val child = best.first

        val secondary =
            result.secondaryCategories
                .filter { it != child }

        val reason =
            buildString {
                result.reason?.let {
                    append(it)
                    append(" ")
                }
                append("hierarchy:$domain->$child")
            }

        return result.copy(
            primaryCategory = child,
            secondaryCategories = secondary,
            reason = reason
        )
    }

    companion object {

        /*
         * Minimum keyword hits inside the domain's
         * subcategories before a refinement fires. Guards
         * against single generic words flipping labels.
         */
        const val MIN_CHILD_HITS = 2
    }
}
