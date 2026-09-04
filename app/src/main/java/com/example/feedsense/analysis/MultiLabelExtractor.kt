package com.example.feedsense.analysis

// --------------------------------
// MULTI-LABEL EXTRACTOR
// --------------------------------
//
// Milestone 7W.
//
// An item is often several things at once (a comedy skit
// with sports cameos, a cooking vlog inside a vlog
// format). The multi-signal scorer produces a score for
// every category; this extractor turns those scores into
// an EXPLICIT, SCORED multi-label set.
//
//   - every retained label keeps its confidence score,
//   - weak labels are dropped (score floor + ratio to the
//     primary score, so a 0.3-scoring also-ran never
//     becomes a "second label"),
//   - when a retained label is dangerously close to the
//     primary (>= AMBIGUITY_GAP of the primary score) the
//     set is flagged uncertain: the item really is mixed
//     and must not be squeezed into a single category.
//
// Pure and deterministic so it is unit-testable. The
// decision is additive to the pipeline: the existing
// ConfidenceGate still owns frame-level review routing;
// this owns the multi-label interpretation of scores.
//

class MultiLabelExtractor {

    data class Label(
        val category: String,
        val score: Double
    )

    data class Result(
        val labels: List<Label>,
        val uncertain: Boolean,
        val reason: String?
    )

    fun extract(
        primary: String?,
        scores: Map<String, Double>
    ): Result {

        if (primary == null || scores.isEmpty()) {
            return Result(
                labels = emptyList(),
                uncertain = false,
                reason = null
            )
        }

        val primaryScore =
            scores[primary] ?: 0.0

        if (primaryScore <= 0.0) {
            return Result(
                labels = emptyList(),
                uncertain = false,
                reason = null
            )
        }

        val labels =
            scores
                .filterKeys { it != primary }
                .mapNotNull { (category, score) ->

                    if (
                        score >= MIN_LABEL_SCORE &&
                        score >=
                        primaryScore * SCORE_RATIO
                    ) {
                        Label(
                            category = category,
                            score = score
                        )
                    } else {
                        null
                    }
                }
                .sortedByDescending { it.score }
                .take(MAX_LABELS)

        if (labels.isEmpty()) {
            return Result(
                labels = emptyList(),
                uncertain = false,
                reason = null
            )
        }

        val uncertain =
            labels.any {
                it.score >=
                    primaryScore * AMBIGUITY_GAP
            }

        val reason =
            buildString {
                append("multi-label:")
                append(primary)
                labels.forEach {
                    append("+")
                    append(it.category)
                }
            }

        return Result(
            labels = labels,
            uncertain = uncertain,
            reason = reason
        )
    }

    companion object {

        /*
         * Absolute score floor. Anything below this is
         * noise, not a plausible secondary label.
         */
        const val MIN_LABEL_SCORE = 0.25

        /*
         * A label must score at least this fraction of the
         * primary score to be reported as a real label.
         */
        const val SCORE_RATIO = 0.5

        /*
         * Cap on reported labels so the set stays focused.
         */
        const val MAX_LABELS = 3

        /*
         * A retained label at or above this fraction of the
         * primary score is "dangerously close": the item is
         * genuinely mixed and flagged uncertain.
         */
        const val AMBIGUITY_GAP = 0.85
    }
}
