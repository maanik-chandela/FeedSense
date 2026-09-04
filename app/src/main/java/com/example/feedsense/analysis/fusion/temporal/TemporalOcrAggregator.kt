package com.example.feedsense.analysis.fusion.temporal

import com.example.feedsense.analysis.fusion.FrameSignals

// --------------------------------
// TEMPORAL OCR AGGREGATOR (Milestone 8B-2)
// --------------------------------
//
// Aggregates OCR text across time, deduplicates repeated text,
// detects OCR evolution (partial -> more complete text), and
// produces semantic topic summaries (8B-2 sections 20-22).

object TemporalOcrAggregator {

    /**
     * Aggregates OCR text from the timeline into a compact
     * temporal summary.
     *
     * @param entries Ordered timeline entries.
     * @param config Temporal configuration.
     * @return Aggregated OCR summary.
     */
    fun aggregate(
        entries: List<TimelineEntry>,
        config: TemporalConfig
    ): TemporalOcrSummary {
        val texts = entries
            .filter { it.isInformative }
            .mapNotNull { it.frame.ocrText?.trim()?.takeIf(String::isNotEmpty) }
            .distinct()

        val totalCount = entries.count { it.isInformative }

        // Deduplicate similar texts.
        val uniqueTexts = deduplicateTexts(texts, config)

        // Detect evolution chains.
        val evolutionChains = detectEvolutionChains(uniqueTexts, config)

        // Extract semantic topics.
        val topics = extractTopics(uniqueTexts)

        return TemporalOcrSummary(
            uniqueTexts = uniqueTexts,
            evolutionChains = evolutionChains,
            deduplicatedCount = uniqueTexts.size,
            totalCount = totalCount,
            semanticTopics = topics
        )
    }

    /**
     * Deduplicates OCR texts that are nearly identical (8B-2
     * section 21). Same text appearing in multiple frames is
     * counted once, not N times.
     */
    fun deduplicateTexts(
        texts: List<String>,
        config: TemporalConfig
    ): List<String> {
        val unique = mutableListOf<String>()

        for (text in texts) {
            val isDuplicate = unique.any { existing ->
                similarity(text, existing) >=
                    config.ocrDuplicateSimilarityThreshold
            }
            if (!isDuplicate) {
                unique += text
            }
        }

        return unique
    }

    /**
     * Detects OCR evolution chains where partial text grows into
     * more complete text over time (8B-2 section 22).
     *
     * Example: "IPL" -> "IPL FINAL" -> "IPL FINAL HIGHLIGHTS"
     */
    fun detectEvolutionChains(
        texts: List<String>,
        config: TemporalConfig
    ): List<List<String>> {
        if (texts.size < 2) return emptyList()

        val chains = mutableListOf<MutableList<String>>()
        val used = mutableSetOf<Int>()

        for (i in texts.indices) {
            if (i in used) continue

            val chain = mutableListOf(texts[i])
            used += i

            for (j in i + 1 until texts.size) {
                if (j in used) continue

                val last = chain.last()
                val candidate = texts[j]

                if (isEvolution(last, candidate, config)) {
                    chain += candidate
                    used += j
                }
            }

            if (chain.size >= 2) {
                chains += chain
            }
        }

        return chains
    }

    /**
     * Determines if 'candidate' is an evolutionary extension of
     * 'base' (i.e. contains all tokens of base plus more).
     */
    private fun isEvolution(
        base: String,
        candidate: String,
        config: TemporalConfig
    ): Boolean {
        val baseTokens = tokenize(base)
        val candTokens = tokenize(candidate)

        if (baseTokens.isEmpty() || candTokens.isEmpty()) return false
        if (candTokens.size <= baseTokens.size) return false

        val overlap = baseTokens.intersect(candTokens.toSet())
        val ratio = overlap.size.toDouble() / baseTokens.size

        return ratio >= config.ocrEvolutionPrefixRatio
    }

    /**
     * Tokenizes text into lowercase words.
     */
    private fun tokenize(text: String): List<String> {
        return text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 2 }
    }

    /**
     * Token-level similarity between two texts (Jaccard-like).
     */
    private fun similarity(a: String, b: String): Double {
        val tokensA = tokenize(a).toSet()
        val tokensB = tokenize(b).toSet()
        if (tokensA.isEmpty() && tokensB.isEmpty()) return 1.0
        if (tokensA.isEmpty() || tokensB.isEmpty()) return 0.0
        val intersection = tokensA.intersect(tokensB)
        val union = tokensA.union(tokensB)
        return intersection.size.toDouble() / union.size
    }

    /**
     * Extracts semantic topic labels from OCR texts.
     */
    private fun extractTopics(texts: List<String>): List<String> {
        val wordCounts = mutableMapOf<String, Int>()
        for (text in texts) {
            for (token in tokenize(text)) {
                if (token.length >= 3) {
                    wordCounts[token] =
                        (wordCounts[token] ?: 0) + 1
                }
            }
        }

        return wordCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }
    }
}
