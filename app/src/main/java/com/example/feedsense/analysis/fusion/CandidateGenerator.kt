package com.example.feedsense.analysis.fusion

import com.example.feedsense.analysis.CategoryCatalog

// --------------------------------
// CANDIDATE GENERATION (Milestone 8B-1)
// --------------------------------
//
// Layer C. Reads RAW text evidence and produces INTERPRETATION:
// a set of candidate (category -> vote) triples. This is where
// OCR text becomes candidate categories / topic / tone. It is
// deliberately kept simple and keyword-based using the project's
// frozen CategoryCatalog taxonomy (62 keys, hierarchical).
//
// This is NOT the whole classifier: candidates here are voted on
// later by the fusion engine. The legacy TextHeuristicClassifier
// verdict, when available, is wrapped as CLASSIFIER evidence by
// the caller and joins the same candidate space as an equal
// (weighted) voter, never as the sole authority (8B-1 section 4/5).
//
// Keyword overlap uses the catalog's existing keywordMap. This is
// an evidence EXTRACTOR, not a tuned model, so it does not
// overfit test fixtures (8B-1 section 49).

data class CandidateVote(
    val category: String,     // normalized catalog key
    val vote: Double,         // [0,1] raw vote from this evidence
    val source: String,       // provenance: provider/id
    val frameId: String
)

data class CandidateSet(
    val candidates: List<CandidateVote>,
    val topic: String?,
    val tone: String?,
    val contentTypeHint: String?
)

class CandidateGenerator {

    companion object {
        /**
         * Derives candidates from an OCR text fragment. Varies the
         * vote by the number of distinct matching keywords so a
         * signal that is generically present does not outvote a
         * signal with rich, specific keywords.
         */
        fun candidatesForText(
            text: String,
            source: String,
            frameId: String
        ): List<CandidateVote> {
            val lower = text.lowercase()
            val tokens = lower
                .split(Regex("[^a-z0-9]+"))
                .filter { it.length >= 3 }
                .toSet()

            val matched = linkedMapOf<String, MutableSet<String>>()

            CategoryCatalog.keywordMap.forEach { (category, keywords) ->
                // Collect every catalog keyword that has a real
                // presence in the text, using word-aware matching so
                // a short generic token ("product") does not alias
                // into unrelated categories ("productivity",
                // "product review") via prefix slang.
                val strikes = linkedSetOf<String>()
                keywords.forEach { kw ->
                    val k = kw.lowercase()
                    val phrase = k.contains(' ')
                    val hit = if (phrase) {
                        lower.contains(k)
                    } else {
                        tokenMatches(k, tokens)
                    }
                    if (hit) {
                        strikes += k
                    }
                }
                if (strikes.isNotEmpty()) {
                    CategoryCatalog.normalize(category)?.let { norm ->
                        matched.getOrPut(norm) { linkedSetOf() } += strikes
                    }
                }
            }

            if (matched.isEmpty()) return emptyList()

            return matched.map { (category, kws) ->
                val strike = kws.size.coerceAtMost(3)
                val vote =
                    (0.5 + 0.16 * (strike - 1))
                        .coerceIn(0.0, 1.0)
                CandidateVote(
                    category = category,
                    vote = vote,
                    source = source,
                    frameId = frameId
                )
            }
        }

        private fun tokenMatches(
            kw: String,
            tokens: Set<String>
        ): Boolean {
            // Direct token hit.
            if (kw in tokens) return true
            // Plural handling (sports vs sport).
            if (tokens.contains(kw + "s") || tokens.contains(kw.dropLast(1))) {
                return true
            }
            // Only allow prefix matching for reasonably long
            // keywords, to avoid short-token aliasing.
            if (kw.length >= 5) {
                val c = kw.length
                return tokens.any {
                    it.length >= c &&
                        it.startsWith(kw) &&
                        it.length - c <= 2
                }
            }
            return false
        }

        fun topicFor(text: String): String? {
            val t = text.trim()
            if (t.isBlank()) return null
            // Compact topic label: first few significant words.
            return t.split(Regex("\\s+"))
                .filter { it.length >= 3 }
                .take(3)
                .joinToString(" ")
                .ifBlank { t.take(12) }
        }

        fun toneFor(text: String): String? {
            val lower = text.lowercase()
            return when {
                listOf("amazing", "incredible", "crazy", "hype", "wow", "best")
                    .any { lower.contains(it) } -> "excited"
                listOf("funny", "joke", "lol", "meme", "hilarious")
                    .any { lower.contains(it) } -> "humorous"
                listOf("motivation", "keep going", "never give up", "grind", "hustle")
                    .any { lower.contains(it) } -> "motivational"
                else -> null
            }
        }

        fun contentTypeHint(text: String): String? {
            val lower = text.lowercase()
            return when {
                listOf("episode", "season", "watch full", "series")
                    .any { lower.contains(it) } -> "video"
                listOf("live", "streaming now")
                    .any { lower.contains(it) } -> "live"
                else -> null
            }
        }
    }
}
