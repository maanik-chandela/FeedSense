package com.example.feedsense.analysis

import com.example.feedsense.analysis.ReferenceMemory.ReferenceMatch

// --------------------------------
// REFERENCE-BASED CONFIDENCE
// --------------------------------
//
// Milestone 7G.
//
// Uses the local reference memory to refine (not
// replace) the heuristic classifier's confidence.
//
//   - When VALIDATED references support the predicted
//     category, the item becomes slightly more
//     trustworthy: confidence is nudged up by a fixed
//     small amount per supporting reference (capped).
//   - When VALIDATED references support a DIFFERENT
//     category as strongly as the prediction, that is
//     a conflict: the item stays uncertain and is sent
//     for human review. Evidence cannot override a
//     disagreement - it must surface it.
//   - The raw classifier confidence is always kept;
//     nothing is ever overwritten or fabricated.
//
// The original prediction is preserved elsewhere
// (aiCategory on the reference row); this class only
// decides whether to mark an item as boosted/conflicted
// and what final confidence to display.
//

class ReferenceConfidence {

    data class BoostResult(
        val confidence: Double?,
        val boosted: Boolean,
        val conflict: Boolean,
        val supportingReferences: Int
    )

    fun apply(
        predicted: String?,
        candidates: List<String>,
        confidence: Double?,
        matches: List<ReferenceMatch>
    ): BoostResult {

        val strongMatches =
            matches.filter {
                it.similarity >= MATCH_SIMILARITY
            }

        if (strongMatches.isEmpty()) {
            return BoostResult(
                confidence = confidence,
                boosted = false,
                conflict = false,
                supportingReferences = 0
            )
        }

        val predictedKey =
            CategoryCatalog.normalize(predicted)

        val supportByLabel =
            strongMatches
                .groupingBy {
                    CategoryCatalog.normalize(
                        it.reference.validatedLabel
                    )
                }
                .eachCount()

        val primarySupport =
            predictedKey?.let { supportByLabel[it] ?: 0 } ?: 0

        val otherSupport =
            supportByLabel
                .filterKeys { it != predictedKey }
                .maxOfOrNull { it.value }
                ?: 0

        val conflict =
            otherSupport >= 1 && otherSupport >= primarySupport

        if (conflict) {

            return BoostResult(
                confidence = confidence,
                boosted = false,
                conflict = true,
                supportingReferences = primarySupport
            )
        }

        if (
            confidence != null &&
            primarySupport > 0
        ) {
            val capped =
                minOf(primarySupport, MAX_BOOST_REFS)

            val boostedConfidence =
                (confidence + capped * BOOST_PER_REF)
                    .coerceIn(0.0, 1.0)

            return BoostResult(
                confidence = boostedConfidence,
                boosted = true,
                conflict = false,
                supportingReferences = primarySupport
            )
        }

        return BoostResult(
            confidence = confidence,
            boosted = false,
            conflict = false,
            supportingReferences = primarySupport
        )
    }

    companion object {

        const val MATCH_SIMILARITY = 0.5

        const val MAX_BOOST_REFS = 3

        const val BOOST_PER_REF = 0.05
    }
}
