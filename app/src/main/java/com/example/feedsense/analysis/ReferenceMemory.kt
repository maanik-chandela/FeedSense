package com.example.feedsense.analysis

import com.example.feedsense.model.LabeledReference

// --------------------------------
// REFERENCE MEMORY
// --------------------------------
//
// Milestone 7G.
//
// Local, project-scoped reference memory. Given a set
// of validated references and the cheap signals of a
// new content piece, it returns the most similar
// previous examples ranked by a transparent similarity
// score.
//
// This is the "local retrieval" half of 7G:
//
//   1. Candidate filtering uses the cheap local
//      signals only (platform, category, topic, OCR
//      text) - no cloud call, no per-frame cost.
//   2. Visual similarity compares perceptual
//      fingerprints when they exist.
//   3. Only the top-K examples are returned so the
//      downstream confidence step stays cheap.
//
// Safety (7G Part 15):
//
//   Only VALIDATED references may act as memory.
//   Cloud labels that have never been confirmed by a
//   human are ignored here.
//
// The score is a weighted sum (capped at 1.0):
//
//   category  0.60  validated label matches a candidate
//   platform  0.15  same platform as the query
//   topic     0.10  Jaccard overlap of topic words
//   text      0.10  best Jaccard overlap of visible text
//   visual    0.15  1 - hamming/64 between fingerprints
//
// A category match alone (0.60) already counts as a
// supporting reference; the extra signals only rank
// the matches.
//

class ReferenceMemory {

    data class ReferenceMatch(
        val reference: LabeledReference,
        val similarity: Double,
        val evidence: List<String>
    )

    fun similarReferences(
        references: List<LabeledReference>,
        categoryCandidates: Set<String>,
        platform: String?,
        topic: String?,
        visibleTexts: List<String>,
        fingerprints: List<String>,
        limit: Int = TOP_K
    ): List<ReferenceMatch> {

        val candidates =
            categoryCandidates
                .mapNotNull { CategoryCatalog.normalize(it) }
                .toSet()

        val queryPlatform =
            platform?.trim()?.lowercase()

        val queryTopicWords =
            words(topic)

        val queryTextWordSets =
            visibleTexts.mapNotNull { words(it) }

        val queryFingerprints =
            fingerprints.filterNotNull()

        val hash =
            PerceptualHash()

        return references
            .asSequence()
            .filter {
                it.validationStatus ==
                    LabeledReference.VALIDATION_VALIDATED
            }
            .mapNotNull { reference ->

                val evidence =
                    mutableListOf<String>()

                var score =
                    0.0

                // Category match (the strongest signal).
                val labelKey =
                    CategoryCatalog.normalize(
                        reference.validatedLabel
                    )

                if (labelKey != null && labelKey in candidates) {
                    score += CATEGORY_WEIGHT
                    evidence += "category:$labelKey"
                }

                // Platform match.
                val refPlatform =
                    reference.platform
                        ?.trim()
                        ?.lowercase()

                if (
                    refPlatform != null &&
                    queryPlatform != null &&
                    refPlatform == queryPlatform
                ) {
                    score += PLATFORM_WEIGHT
                    evidence += "platform:$refPlatform"
                }

                // Topic overlap.
                val refTopicWords =
                    words(reference.topic)

                if (
                    queryTopicWords != null &&
                    refTopicWords != null
                ) {
                    val overlap =
                        jaccard(queryTopicWords, refTopicWords)

                    if (overlap > 0.0) {
                        score += TOPIC_WEIGHT * overlap
                        evidence += "topic:${"%.2f".format(overlap)}"
                    }
                }

                // Visible-text overlap (best pair).
                val refTextWords =
                    words(reference.visibleText)

                if (
                    refTextWords != null &&
                    queryTextWordSets.isNotEmpty()
                ) {
                    val best =
                        queryTextWordSets.maxOfOrNull {
                            jaccard(it, refTextWords)
                        }

                    if (best != null && best > 0.0) {
                        score += TEXT_WEIGHT * best
                        evidence += "text:${"%.2f".format(best)}"
                    }
                }

                // Visual fingerprint similarity.
                if (
                    reference.frameFingerprint != null &&
                    queryFingerprints.isNotEmpty()
                ) {
                    val distance =
                        queryFingerprints
                            .mapNotNull {
                                hash.hammingDistance(
                                    reference.frameFingerprint,
                                    it
                                )
                            }
                            .minOrNull()

                    if (distance != null) {
                        val visual =
                            1.0 - distance / 64.0

                        if (visual > 0.0) {
                            score += VISUAL_WEIGHT * visual
                            evidence += "visual:${"%.2f".format(visual)}"
                        }
                    }
                }

                if (evidence.isEmpty()) {
                    return@mapNotNull null
                }

                ReferenceMatch(
                    reference = reference,
                    similarity = score.coerceAtMost(1.0),
                    evidence = evidence
                )
            }
            .sortedByDescending {
                it.similarity
            }
            .take(limit)
            .toList()
    }

    /*
     * Lowercased, non-alphanumeric-tokenized words,
     * dropping single characters.
     */
    private fun words(
        text: String?
    ): Set<String>? {

        if (text.isNullOrBlank()) {
            return null
        }

        return text
            .lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 1 }
            .toSet()
            .takeIf { it.isNotEmpty() }
    }

    private fun jaccard(
        first: Set<String>,
        second: Set<String>
    ): Double {

        val union =
            (first + second).size

        if (union == 0) {
            return 0.0
        }

        val intersection =
            first.intersect(second).size

        return intersection.toDouble() / union
    }

    companion object {

        const val TOP_K = 10

        const val CATEGORY_WEIGHT = 0.60

        const val PLATFORM_WEIGHT = 0.15

        const val TOPIC_WEIGHT = 0.10

        const val TEXT_WEIGHT = 0.10

        const val VISUAL_WEIGHT = 0.15
    }
}
