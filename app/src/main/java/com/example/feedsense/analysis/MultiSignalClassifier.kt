package com.example.feedsense.analysis

// --------------------------------
// MULTI-SIGNAL CLASSIFIER
// --------------------------------
//
// Milestone 7M.
//
// The stronger local classifier. It is an EXTENSION of
// the heuristic analyzer, not a replacement:
//
//   1. The text heuristic always runs first and stays
//      authoritative for category/topic/tone.
//   2. Platform priors nudge confidence up for the
//      categories a platform is known for, and down when
//      the platform evidence conflicts with the text.
//   3. Trusted-example (memory) support nudges confidence
//      when this content was seen and confirmed before.
//   4. Confidence is calibrated: high ambiguity caps the
//      confidence so the item reaches the review queue
//      instead of being over-confident.
//
// The safe fallback: when no signal improves the
// heuristic result, the heuristic result is returned
// untouched (modelVersion included).
//
// modelVersion: local-v6.0 identifies this logic.
// Milestone 7U: the catalog it scores against grew to
// the full 62-key taxonomy (heuristic-v6); platform
// priors and memory hooks are unchanged.
//

class MultiSignalClassifier {

    data class SignalSet(
        val text: String?,
        val platform: String?,
        val heuristic: ClassificationResult,
        val memoryCandidates: Set<String> = emptySet(),
        val memorySupport: Double = 0.0
    )

    fun classify(
        signals: SignalSet
    ): ClassificationResult {

        val heuristic =
            signals.heuristic

        val predicted =
            heuristic.primaryCategory

        val normalizedPlatform =
            signals.platform
                ?.trim()
                ?.lowercase()

        val platformPriors =
            normalizedPlatform
                ?.let {
                    PLATFORM_PRIORS[it]
                }
                ?: emptyMap()

        val memoryCandidates =
            signals.memoryCandidates
                .mapNotNull {
                    CategoryCatalog.normalize(it)
                }
                .toSet()

        // --------------------------------
        // 1. PLATFORM-AWARE CATEGORY
        // --------------------------------
        //
        // If the text found nothing but the platform has
        // a strong prior, offer it as a modest-confidence
        // guess instead of "none".

        var primary =
            predicted

        var secondary =
            heuristic.secondaryCategories.toMutableList()

        val evidence =
            mutableListOf<String>()

        if (primary == null) {

            platformPriors
                .maxByOrNull { it.value }
                ?.takeIf {
                    it.value >= PLATFORM_PRIOR_MIN
                }
                ?.let { (category, prior) ->
                    primary = category
                    evidence += "platform-guess:$category"
                }
        }

        if (primary == null) {
            return heuristic
        }

        // --------------------------------
        // 2. CONFIDENCE CALIBRATION
        // --------------------------------

        val base =
            heuristic.confidence ?: 0.0

        // Platform agreement bonus.
        val platformFactor =
            platformPriors[primary] ?: 0.0

        if (platformFactor > 0.0) {
            evidence += "platform:$normalizedPlatform"
        }

        // Platform conflict: a different category is the
        // strong platform prior -> reduce confidence and
        // surface the conflict.
        val platformConflict =
            platformPriors
                .maxByOrNull { it.value }
                ?.takeIf {
                    it.key != primary &&
                    it.value >= PLATFORM_CONFLICT_MIN
                }

        if (platformConflict != null) {
            evidence += "platform-conflict:${platformConflict.key}"
        }

        // Memory support for the prediction.
        val memoryFactor =
            if (primary in memoryCandidates) {
                signals.memorySupport
            } else {
                0.0
            }

        // Memory conflict: a DIFFERENT category was
        // confirmed before and is present in the
        // candidates.
        val memoryConflict =
            memoryCandidates.any {
                it != primary &&
                it in secondary
            }

        if (memoryConflict) {
            evidence += "memory-conflict"
        }

        var confidence =
            base +
                    (1.0 - base) *
                    (PLATFORM_WEIGHT * platformFactor +
                            MEMORY_WEIGHT * memoryFactor)

        if (platformConflict != null) {
            confidence -= PLATFORM_CONFLICT_PENALTY
        }

        if (memoryConflict) {
            confidence -= MEMORY_CONFLICT_PENALTY
        }

        // Ambiguity penalty: when the text itself cannot
        // separate the top categories, cap confidence so
        // the item is queued for review instead of
        // reported as certain.
        val ambiguity =
            heuristic.ambiguityScore ?: 0.0

        confidence -= ambiguity * AMBIGUITY_PENALTY

        if (ambiguity >= AMBIGUITY_CAP_THRESHOLD) {
            confidence =
                confidence.coerceAtMost(MEDIUM_CONFIDENCE_CAP)
        }

        confidence =
            confidence
                .coerceIn(0.0, 0.98)

        // --------------------------------
        // 3. RESULT
        // --------------------------------

        val refinedReason =
            buildString {

                heuristic.reason?.let {
                    append(it)
                }

                if (evidence.isNotEmpty()) {
                    if (heuristic.reason != null) {
                        append(" ")
                    }
                    append(
                        evidence.joinToString(",")
                    )
                }
            }

        return ClassificationResult(
            primaryCategory = primary,
            secondaryCategories =
                secondary.distinct(),
            confidence = confidence,
            ambiguityScore =
                heuristic.ambiguityScore,
            topic = heuristic.topic,
            tone = heuristic.tone,
            reason =
                refinedReason
                    .takeIf { it.isNotBlank() }
        )
    }

    companion object {

        const val MODEL_VERSION =
            "local-v6.0"

        const val TEXT_WEIGHT = 0.55

        const val PLATFORM_WEIGHT = 0.25

        const val MEMORY_WEIGHT = 0.20

        const val PLATFORM_PRIOR_MIN = 0.5

        const val PLATFORM_CONFLICT_MIN = 0.5

        const val PLATFORM_CONFLICT_PENALTY = 0.10

        const val MEMORY_CONFLICT_PENALTY = 0.10

        const val AMBIGUITY_PENALTY = 0.10

        const val AMBIGUITY_CAP_THRESHOLD = 0.7

        const val MEDIUM_CONFIDENCE_CAP = 0.6

        /*
         * Platform -> category priors. These are soft
         * contextual nudges, never hard assignments: the
         * text classifier still leads.
         */
        val PLATFORM_PRIORS: Map<String, Map<String, Double>> =
            mapOf(
                "instagram" to mapOf(
                    "lifestyle" to 0.5,
                    "fashion" to 0.5,
                    "food" to 0.4,
                    "fitness" to 0.4,
                    "motivation" to 0.3,
                    "entertainment" to 0.3
                ),
                "youtube" to mapOf(
                    "education" to 0.5,
                    "entertainment" to 0.4,
                    "technology" to 0.4,
                    "gaming" to 0.4,
                    "music" to 0.4,
                    "news" to 0.2
                ),
                "tiktok" to mapOf(
                    "comedy" to 0.5,
                    "music" to 0.4,
                    "entertainment" to 0.4,
                    "lifestyle" to 0.3
                ),
                "facebook" to mapOf(
                    "news" to 0.4,
                    "lifestyle" to 0.3,
                    "entertainment" to 0.3
                ),
                "linkedin" to mapOf(
                    "motivation" to 0.4,
                    "education" to 0.4,
                    "technology" to 0.3,
                    "politics" to 0.2
                ),
                "twitter" to mapOf(
                    "news" to 0.5,
                    "politics" to 0.4,
                    "technology" to 0.3
                ),
                "x" to mapOf(
                    "news" to 0.5,
                    "politics" to 0.4,
                    "technology" to 0.3
                ),
                "reddit" to mapOf(
                    "technology" to 0.3,
                    "news" to 0.3,
                    "gaming" to 0.3
                ),
                "pinterest" to mapOf(
                    "fashion" to 0.5,
                    "food" to 0.4,
                    "lifestyle" to 0.4
                ),
                "twitch" to mapOf(
                    "gaming" to 0.6,
                    "entertainment" to 0.3
                )
            )
    }
}
