package com.example.feedsense.analysis

// --------------------------------
// MIXED CONTENT ANALYZER
// --------------------------------
//
// Milestone 7P.
//
// Decides whether a FeedItem blends multiple categories
// and, when it does, WHICH categories. Mixed content is
// the honest representation of items like a comedy skit
// with sports cameos - they are not "one thing".
//
// Signals:
//
//   frameCategoryCounts - how many frames fell into each
//                         category (after reference
//                         refinement).
//   frameSecondaryCandidates - per-frame OCR/classifier
//                         secondary categories.
//   confidence           - average AI confidence.
//
// Rules (deterministic):
//
//   1. A secondary category qualifies when its frame
//      support is at least MIXED_CONTENT_RATIO of the
//      primary's support (e.g. gaming 3 vs comedy 4).
//   2. A frame-level secondary candidate also counts
//      when the item's confidence is below the
//      MIXED_CONFIDENCE_THRESHOLD.
//   3. No primary category -> nothing can be mixed.
//   4. Everything else -> clean single-category item.
//
// Output is a ranked list (max MAX_SECONDARY_CATEGORIES)
// and a reason string for the audit trail. Pure and
// deterministic so it is unit-testable.
//

class MixedContentAnalyzer {

    data class Decision(
        val mixedContent: Boolean,
        val secondaryCategories: List<String>,
        val reason: String?
    )

    fun analyze(
        primaryCategory: String?,
        confidence: Double?,
        frameCategoryCounts: Map<String, Int>,
        frameSecondaryCandidates: List<String>
    ): Decision {

        if (primaryCategory == null) {
            return Decision(
                mixedContent = false,
                secondaryCategories = emptyList(),
                reason = null
            )
        }

        val others =
            frameCategoryCounts
                .filterKeys {
                    it != primaryCategory
                }
                .filterValues {
                    it > 0
                }

        val primaryCount =
            frameCategoryCounts[primaryCategory] ?: 0

        val strongOthers =
            others
                .filterValues {
                    primaryCount == 0 ||
                        it * 2 >= primaryCount
                }
                .entries
                .sortedWith(
                    compareByDescending<Map.Entry<String, Int>> {
                        it.value
                    }.thenBy {
                        it.key
                    }
                )
                .map {
                    it.key
                }

        val ocrSecondary =
            frameSecondaryCandidates
                .filter {
                    it != primaryCategory
                }
                .distinct()

        val secondaryCategories =
            buildList {

                addAll(
                    strongOthers
                        .take(
                            MAX_SECONDARY_CATEGORIES
                        )
                )

                if (
                    addOcrSecondary(
                        confidence = confidence,
                        ocr = ocrSecondary,
                        strong = strongOthers
                    )
                ) {
                    add(ocrSecondary.first())
                }
            }
                .distinct()
                .take(MAX_SECONDARY_CATEGORIES)

        if (secondaryCategories.isEmpty()) {
            return Decision(
                mixedContent = false,
                secondaryCategories = emptyList(),
                reason = null
            )
        }

        return Decision(
            mixedContent = true,
            secondaryCategories = secondaryCategories,
            reason =
                listOf(
                    "mixed-content",
                    listOfNotNull(primaryCategory)
                        .plus(secondaryCategories)
                        .joinToString("+")
                ).joinToString(":")
        )
    }

    /*
     * A frame-level secondary category is only added when
     * the item is NOT already strongly mixed (would be
     * redundant) and the confidence is low enough to
     * believe the classifier's secondary guess.
     */
    private fun addOcrSecondary(
        confidence: Double?,
        ocr: List<String>,
        strong: List<String>
    ): Boolean {

        if (ocr.isEmpty() || strong.isNotEmpty()) {
            return false
        }

        val effectiveConfidence =
            confidence ?: 0.0

        return effectiveConfidence <
            MIXED_CONFIDENCE_THRESHOLD
    }

    companion object {

        const val MIXED_CONTENT_RATIO = 0.5

        const val MIXED_CONFIDENCE_THRESHOLD = 0.6

        const val MAX_SECONDARY_CATEGORIES = 2
    }
}
