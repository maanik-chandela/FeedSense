package com.example.feedsense.analysis.evaluation

// --------------------------------
// ATTRIBUTION DECISION TREE (Milestone 8A-6)
// --------------------------------
//
// A deterministic, documented diagnostic workflow. It classifies
// an error into an ATTRIBUTION CLASS (MODEL / DATA / PIPELINE /
// TAXONOMY / ANNOTATION / UNKNOWN) using only OBSERVED facts,
// and stops at the first decisive condition. It NEVER converts
// a hypothesis into a causal claim - every branch that cannot
// be decided from data resolves to UNKNOWN and defers to human
// review.
//
// Workflow (8A-6 section 25):
//   1. Is ground truth valid?  NO  -> ANNOTATION/DATA issue
//   2. Is evidence sufficient? NO  -> EVIDENCE limitation
//   3. Is segmentation correct?NO  -> SEGMENTATION error
//   4. Are platform/content-type/duration/interactions
//      correctly represented? NO  -> relevant capability error
//   5. Does the AI disagree despite adequate evidence?
//                                   YES -> MODEL candidate
//   6. Is the category itself ambiguous?
//                                   YES -> TAXONOMY/SEMANTIC
//   7. Human review decides final attribution.

object AttributionDecisionTree {

    data class Input(
        val truthValid: Boolean,
        val truthAmbiguous: Boolean,
        val evidenceSufficient: Boolean,
        val segmentationCorrect: Boolean,
        val capabilityRepresentationError: Boolean, // step 4
        val aiDisagreesDespiteEvidence: Boolean,    // step 5
        val categoryAmbiguous: Boolean              // step 6
    )

    data class Result(
        val attributionClass: String,
        val step: Int,
        val note: String
    )

    /**
     * Decides the attribution class deterministically.
     * Returns UNKNOWN when no decisive OBSERVED branch applies.
     */
    fun classify(input: Input): Result {
        // Step 1: valid ground truth?
        if (!input.truthValid && input.truthAmbiguous) {
            return Result(
                attributionClass = RootCauseTypes.CLASS_ANNOTATION_RELATED,
                step = 1,
                note = "Ground truth is not valid/definitive."
            )
        }
        if (!input.truthValid) {
            return Result(
                attributionClass = RootCauseTypes.CLASS_DATA_RELATED,
                step = 1,
                note = "Ground truth is missing/invalid."
            )
        }

        // Step 2: sufficient evidence?
        if (!input.evidenceSufficient) {
            return Result(
                attributionClass = RootCauseTypes.CLASS_PIPELINE_RELATED,
                step = 2,
                note = "Insufficient evidence (missing frames/OCR)."
            )
        }

        // Step 3: segmentation correct?
        if (!input.segmentationCorrect) {
            return Result(
                attributionClass = RootCauseTypes.CLASS_PIPELINE_RELATED,
                step = 3,
                note = "Segmentation produced a wrong boundary."
            )
        }

        // Step 4: other capability representation correct?
        if (input.capabilityRepresentationError) {
            return Result(
                attributionClass = RootCauseTypes.CLASS_PIPELINE_RELATED,
                step = 4,
                note = "A non-category capability was misrepresented."
            )
        }

        // Step 5: AI disagrees despite adequate evidence?
        if (input.aiDisagreesDespiteEvidence) {
            return Result(
                attributionClass = RootCauseTypes.CLASS_MODEL_RELATED,
                step = 5,
                note = "AI disagrees with truth despite adequate evidence."
            )
        }

        // Step 6: category ambiguous?
        if (input.categoryAmbiguous) {
            return Result(
                attributionClass = RootCauseTypes.CLASS_TAXONOMY_RELATED,
                step = 6,
                note = "Category is inherently ambiguous."
            )
        }

        // Step 7: not decisive from data.
        return Result(
            attributionClass = RootCauseTypes.CLASS_UNKNOWN,
            step = 7,
            note = "Not decidable from OBSERVED data; defer to human review."
        )
    }
}
