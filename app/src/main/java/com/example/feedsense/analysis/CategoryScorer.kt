package com.example.feedsense.analysis

// --------------------------------
// CATEGORY SCORER
// --------------------------------
//
// Milestone 7U.
//
// Multi-signal candidate generation. The heuristic
// classifier reports ONE primary category plus a few
// secondaries. This layer answers "what else could this
// be, and by how much?" by scoring EVERY canonical
// category from the cheap, offline signals:
//
//   - visible text          keyword hits (the base),
//   - hashtags              "#sports" is a strong direct
//                           vote for a category,
//   - title                 the dominant line of OCR text
//                           counts as one extra signal,
//   - platform              soft priors from the known
//                           platform->category map,
//   - learned corrections   trusted memory candidates
//                           (validated human labels) get a
//                           support bump (7Y wires the
//                           retrieval; the scoring hook is
//                           here).
//
// Duration and interaction signals are behavioural, not
// categorical: they are handled by the interaction
// layer at the item level, not here.
//
// The output is a transparent, ranked score list:
//
//   - scores     category -> normalized score (top = 1.0),
//   - ranked     categories ordered by score, each with
//                the exact signals that produced it,
//   - evidence   human-readable "signal:category" list,
//   - title      the extracted title line (if any),
//   - hashtags   the extracted hashtag tokens.
//
// Pure and deterministic: unit-testable, no Android, no
// network. It never overrides the classifier - it feeds
// candidates and evidence to it.
//

data class ScoredCategory(
    val category: String,
    val score: Double,
    val hits: Int,
    val signals: List<String>
)

data class CategoryScoreResult(
    val scores: Map<String, Double>,
    val ranked: List<ScoredCategory>,
    val evidence: List<String>,
    val title: String?,
    val hashtags: List<String>
)

class CategoryScorer {

    fun score(
        text: String?,
        platform: String? = null,
        memoryCandidates: Set<String> = emptySet(),
        memorySupport: Double = 0.0
    ): CategoryScoreResult {

        val normalized =
            text?.lowercase() ?: ""

        // --------------------------------
        // 1. BASE: TEXT KEYWORD HITS
        // --------------------------------

        val textHits: Map<String, Int> =
            CategoryCatalog.keywordMap.mapValues { (_, keywords) ->
                keywords.count { keyword ->
                    normalized.contains(keyword)
                }
            }

        // --------------------------------
        // 2. TITLE SIGNAL
        // --------------------------------
        //
        // The dominant line of OCR text (the biggest
        // surface area of a content title/caption).
        // If it contains a category keyword, that line is
        // strong evidence for the category.

        val title =
            dominantLine(normalized)

        val titleCategories: Set<String> =
            title
                ?.let { line ->
                    CategoryCatalog.keywordMap
                        .filterValues { keywords ->
                            keywords.any { keyword ->
                                line.contains(keyword)
                            }
                        }
                        .keys
                }
                ?: emptySet()

        // --------------------------------
        // 3. HASHTAG SIGNAL
        // --------------------------------
        //
        // "#sports" or "#meme" is a direct authoring hint.
        // A hashtag maps onto a category when the tag is
        // itself a canonical key or matches one of the
        // category's keywords.

        val hashtags =
            HASHTAG_REGEX
                .findAll(normalized)
                .map { it.groupValues[1].lowercase() }
                .toList()

        val hashtagCategories =
            hashtags
                .flatMap { tag ->
                    categoryOfHashtag(tag)
                }
                .groupingBy { it }
                .eachCount()

        // --------------------------------
        // 4. PLATFORM PRIOR
        // --------------------------------

        val platformPrior =
            platform
                ?.trim()
                ?.lowercase()
                ?.let {
                    MultiSignalClassifier.PLATFORM_PRIORS[it]
                }
                ?: emptyMap()

        // --------------------------------
        // 5. MEMORY (LEARNED CORRECTIONS)
        // --------------------------------

        val memoryKeys =
            memoryCandidates
                .mapNotNull { CategoryCatalog.normalize(it) }
                .toSet()

        // --------------------------------
        // COMBINE
        // --------------------------------

        val scored =
            CategoryCatalog.keys.mapNotNull { category ->

                val signals =
                    mutableListOf<String>()

                val hitCount =
                    textHits[category] ?: 0

                var raw =
                    HIT_WEIGHT * hitCount

                if (hitCount > 0) {
                    signals += "text:$category"
                }

                if (category in titleCategories) {
                    raw += TITLE_WEIGHT
                    signals += "title:$category"
                }

                val hashtagCount =
                    hashtagCategories[category] ?: 0

                if (hashtagCount > 0) {
                    raw +=
                        HASHTAG_WEIGHT * hashtagCount
                    signals += "hashtag:$category"
                }

                val prior =
                    platformPrior[category] ?: 0.0

                if (prior > 0.0) {
                    raw += PLATFORM_WEIGHT * prior
                    signals += "platform:$category"
                }

                if (category in memoryKeys) {
                    raw += MEMORY_WEIGHT * memorySupport
                    signals += "memory:$category"
                }

                if (raw <= 0.0) {
                    return@mapNotNull null
                }

                category to CategoryAccumulator(
                    raw = raw,
                    hits = hitCount,
                    signals = signals
                )
            }

        if (scored.isEmpty()) {
            return CategoryScoreResult(
                scores = emptyMap(),
                ranked = emptyList(),
                evidence = emptyList(),
                title = title,
                hashtags = hashtags
            )
        }

        val maxRaw =
            scored.maxOf { it.second.raw }

        val ranked =
            scored
                .sortedByDescending {
                    it.second.raw
                }
                .map { (category, accumulator) ->
                    ScoredCategory(
                        category = category,
                        score =
                            (accumulator.raw / maxRaw)
                                .coerceIn(0.0, 1.0),
                        hits = accumulator.hits,
                        signals = accumulator.signals
                    )
                }

        val scores =
            ranked.associate {
                it.category to it.score
            }

        val evidence =
            ranked
                .flatMap { it.signals }
                .distinct()

        return CategoryScoreResult(
            scores = scores,
            ranked = ranked,
            evidence = evidence,
            title = title,
            hashtags = hashtags
        )
    }

    private data class CategoryAccumulator(
        val raw: Double,
        val hits: Int,
        val signals: List<String>
    )

    /*
     * The dominant line of visible text: the first line
     * long enough to plausibly be a title/caption.
     * Returns null when nothing qualifies.
     */
    private fun dominantLine(
        normalized: String
    ): String? {

        return normalized
            .lineSequence()
            .map { it.trim() }
            .firstOrNull {
                it.length >= MIN_TITLE_LENGTH
            }
    }

    /*
     * A hashtag votes for a category when it is a
     * canonical key itself, matches a keyword verbatim,
     * or contains a category keyword as a token.
     */
    private fun categoryOfHashtag(
        tag: String
    ): List<String> {

        val canonical =
            CategoryCatalog.normalize(tag)
                ?.let { listOf(it) }
                ?: emptyList()

        if (canonical.isNotEmpty()) {
            return canonical
        }

        return CategoryCatalog.keywordMap
            .filterValues { keywords ->
                keywords.any { keyword ->
                    keyword == tag ||
                        tag.contains(keyword)
                }
            }
            .keys
            .toList()
    }

    companion object {

        private val HASHTAG_REGEX =
            Regex("#([a-zA-Z0-9_]+)")

        private const val MIN_TITLE_LENGTH = 15

        /*
         * Signal weights. Text is the base; the others
         * are modest nudges so no single auxiliary signal
         * can dominate a category decision.
         */
        const val HIT_WEIGHT = 1.0

        const val TITLE_WEIGHT = 0.6

        const val HASHTAG_WEIGHT = 0.5

        const val PLATFORM_WEIGHT = 0.4

        const val MEMORY_WEIGHT = 0.4
    }
}
