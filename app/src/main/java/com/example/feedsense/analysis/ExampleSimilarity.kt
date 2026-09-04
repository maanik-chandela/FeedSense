package com.example.feedsense.analysis

import com.example.feedsense.model.LabeledReference

// --------------------------------
// EXAMPLE SIMILARITY
// --------------------------------
//
// Milestone 7L (Parts 2 and 5).
//
// A lightweight, fully local similarity layer built on
// top of the validated reference rows:
//
//   - normalized text similarity (token Jaccard),
//   - perceptual fingerprint similarity (dHash
//     Hamming distance),
//   - platform match,
//   - duplicate detection via fingerprint + normalized
//     text so the same content is not stored twice.
//
// No embedding model is downloaded. If a real embedding
// model becomes practical later, it plugs in here.
//
// One incorrect example must never contaminate the whole
// memory, so every match is returned with:
//
//   - its TrustLevel (USER_CONFIRMED > CLOUD_VALIDATED >
//     LOCAL_HIGH_CONFIDENCE > LOCAL_LOW_CONFIDENCE),
//   - its trust weight,
//   - the evidence that produced the match.
//

class ExampleSimilarity {

    data class ExampleMatch(
        val reference: LabeledReference,
        val trustLevel: TrustLevel,
        val trustWeight: Double,
        val textSimilarity: Double,
        val visualSimilarity: Double,
        val samePlatform: Boolean,
        val similarity: Double,
        val evidence: List<String>
    )

    data class DuplicateCheck(
        val isDuplicate: Boolean,
        val matchedExampleId: String?,
        val evidence: List<String>
    )

    // --------------------------------
    // SIMILARITY LOOKUP
    // --------------------------------

    fun findSimilar(
        examples: List<LabeledReference>,
        visibleTexts: List<String>,
        fingerprints: List<String>,
        platform: String?,
        limit: Int = MAX_MATCHES
    ): List<ExampleMatch> {

        val queryTextWordSets =
            visibleTexts.mapNotNull { words(it) }

        val queryFingerprints =
            fingerprints.filterNotNull()

        val hash =
            PerceptualHash()

        return examples
            .asSequence()
            .filter {
                it.validationStatus ==
                    LabeledReference.VALIDATION_VALIDATED
            }
            .mapNotNull { reference ->

                val evidence =
                    mutableListOf<String>()

                // --- normalized text similarity -----
                val refTextWords =
                    words(reference.visibleText)

                var textSimilarity = 0.0

                if (
                    refTextWords != null &&
                    queryTextWordSets.isNotEmpty()
                ) {
                    textSimilarity =
                        queryTextWordSets.maxOfOrNull {
                            jaccard(it, refTextWords)
                        } ?: 0.0

                    if (textSimilarity > 0.0) {
                        evidence += "text:${"%.2f".format(textSimilarity)}"
                    }
                }

                // --- visual similarity --------------
                var visualSimilarity = 0.0

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
                        visualSimilarity =
                            1.0 - distance / 64.0

                        if (visualSimilarity > 0.0) {
                            evidence += "visual:${"%.2f".format(visualSimilarity)}"
                        }
                    }
                }

                // --- platform -----------------------
                val samePlatform =
                    reference.platform
                        ?.trim()
                        ?.lowercase()
                        ?.let {
                            platform?.trim()?.lowercase() == it
                        } == true

                if (samePlatform) {
                    evidence += "platform:${reference.platform}"
                }

                if (
                    textSimilarity <= 0.0 &&
                    visualSimilarity <= 0.0 &&
                    !samePlatform
                ) {
                    return@mapNotNull null
                }

                val combined =
                    textSimilarity + visualSimilarity

                val trustLevel =
                    TrustLevel.of(reference)

                ExampleMatch(
                    reference = reference,
                    trustLevel = trustLevel,
                    trustWeight =
                        TrustLevel.weight(trustLevel),
                    textSimilarity = textSimilarity,
                    visualSimilarity = visualSimilarity,
                    samePlatform = samePlatform,
                    similarity = combined.coerceAtMost(1.0),
                    evidence = evidence
                )
            }
            .sortedWith(
                compareByDescending<ExampleMatch> {
                    it.trustWeight
                }.thenByDescending {
                    it.similarity
                }
            )
            .take(limit)
            .toList()
    }

    // --------------------------------
    // DUPLICATE DETECTION (Part 5)
    // --------------------------------

    /*
     * True when the incoming content is the same as an
     * already-stored example. Two fingerprints within the
     * tight hash distance OR identical normalized text on
     * the same platform both count as duplicates.
     */
    fun findDuplicate(
        examples: List<LabeledReference>,
        visibleTexts: List<String>,
        fingerprints: List<String>,
        platform: String?
    ): DuplicateCheck {

        val queryFingerprints =
            fingerprints.filterNotNull()

        val queryTextWordSets =
            visibleTexts.mapNotNull { words(it) }

        val hash =
            PerceptualHash()

        var best: DuplicateCheck? = null

        for (reference in examples) {

            val evidence =
                mutableListOf<String>()

            var matched = false

            if (queryFingerprints.isNotEmpty()) {

                val distance =
                    queryFingerprints
                        .mapNotNull {
                            hash.hammingDistance(
                                reference.frameFingerprint,
                                it
                            )
                        }
                        .minOrNull()

                if (
                    distance != null &&
                    distance <= DUPLICATE_HASH_DISTANCE
                ) {
                    matched = true
                    evidence += "visual:distance=$distance"
                }
            }

            if (!matched && queryTextWordSets.isNotEmpty()) {

                val refTextWords =
                    words(reference.visibleText)

                if (refTextWords != null) {

                    val bestOverlap =
                        queryTextWordSets.maxOfOrNull {
                            jaccard(it, refTextWords)
                        } ?: 0.0

                    val platformMatches =
                        reference.platform
                            ?.trim()
                            ?.lowercase()
                            ?.let {
                                platform?.trim()?.lowercase() == it
                            } == true

                    if (
                        bestOverlap >=
                        DUPLICATE_TEXT_SIMILARITY &&
                        platformMatches
                    ) {
                        matched = true
                        evidence += "text:${"%.2f".format(bestOverlap)}"
                    }
                }
            }

            if (!matched) {
                continue
            }

            val check =
                DuplicateCheck(
                    isDuplicate = true,
                    matchedExampleId = reference.id,
                    evidence = evidence
                )

            /*
             * Prefer the strongest duplicate hit: the one
             * with the higher trust authority. Never let a
             * low-trust example shadow a confirmed one.
             */
            if (
                best == null ||
                TrustLevel.of(reference).authority >
                TrustLevel.of(
                    best.matchedExampleId
                        ?.let { id ->
                            examples.firstOrNull {
                                it.id == id
                            }
                        }
                        ?: reference
                ).authority
            ) {
                best = check
            }
        }

        return best ?: DuplicateCheck(
            isDuplicate = false,
            matchedExampleId = null,
            evidence = emptyList()
        )
    }

    // --------------------------------
    // TOKENIZATION HELPERS
    // --------------------------------

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

        const val MAX_MATCHES = 10

        const val DUPLICATE_HASH_DISTANCE = 8

        const val DUPLICATE_TEXT_SIMILARITY = 0.8
    }
}
