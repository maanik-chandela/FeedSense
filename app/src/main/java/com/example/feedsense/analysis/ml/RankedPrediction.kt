package com.example.feedsense.analysis.ml

// --------------------------------
// RANKED PREDICTION (8B-14)
// --------------------------------
//
// One ranked candidate: a category plus its model confidence.
//
// Confidence here is the MODEL's own confidence semantics
// only. It is intentionally kept separate from privacy
// confidence, OCR confidence, heuristic confidence, evidence
// strength, and evaluation confidence (8B-14 section 12).
//
// Ordering helpers are deterministic: ties are broken by
// category name so identical input always yields identical
// ranking.

data class RankedPrediction(
    val category: String,
    val confidence: Double
) {

    init {
        require(category.isNotBlank()) { "category must be non-blank" }
    }

    companion object {

        /*
         * Deterministic ranking: confidence descending,
         * category ascending on ties.
         */
        fun sortDeterministic(
            candidates: List<RankedPrediction>
        ): List<RankedPrediction> {
            return candidates.sortedWith(
                compareByDescending<RankedPrediction> { it.confidence }
                    .thenBy { it.category }
            )
        }

        /*
         * Removes duplicate categories deterministically,
         * keeping the highest confidence per category, then
         * applies deterministic ordering. Used so duplicated
         * or unordered model output never reaches evaluation
         * in an unpredictable shape (8B-14 section 42/44).
         */
        fun deduplicateDeterministic(
            candidates: List<RankedPrediction>
        ): List<RankedPrediction> {
            val best = LinkedHashMap<String, RankedPrediction>()
            for (candidate in candidates) {
                val existing = best[candidate.category]
                if (existing == null ||
                    candidate.confidence > existing.confidence ||
                    (candidate.confidence == existing.confidence &&
                        candidate.category < existing.category)
                ) {
                    best[candidate.category] = candidate
                }
            }
            return sortDeterministic(best.values.toList())
        }
    }
}