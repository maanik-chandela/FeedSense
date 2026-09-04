package com.example.feedsense.analysis

import com.example.feedsense.model.LabeledReference

// --------------------------------
// TRUST LEVEL
// --------------------------------
//
// Milestone 7L (Part 3).
//
// Every trusted example carries a trust level so the
// local memory never treats all examples equally.
// Higher trust must win over lower trust when their
// evidence disagrees.
//
//   USER_CONFIRMED        human confirmed the label
//   CLOUD_VALIDATED       a validated cloud reference
//   LOCAL_HIGH_CONFIDENCE high-confidence local guess
//   LOCAL_LOW_CONFIDENCE  low-confidence local guess
//
// Derivation rules (pure, testable):
//
//   - validated by a human          -> USER_CONFIRMED
//   - validated, came from cloud    -> CLOUD_VALIDATED
//   - not validated but aiSource is
//     LOCAL with high confidence    -> LOCAL_HIGH_CONFIDENCE
//   - anything else                 -> LOCAL_LOW_CONFIDENCE
//

enum class TrustLevel(
    val authority: Int
) {

    USER_CONFIRMED(4),
    CLOUD_VALIDATED(3),
    LOCAL_HIGH_CONFIDENCE(2),
    LOCAL_LOW_CONFIDENCE(1);

    companion object {

        val HIGH_CONFIDENCE_THRESHOLD = 0.8

        /*
         * Derive the trust level of a reference row.
         * PENDING rows are only ever low-confidence
         * guesses - they must never act as trusted
         * memory.
         */
        fun of(
            reference: LabeledReference
        ): TrustLevel {

            val validated =
                reference.validationStatus ==
                    LabeledReference.VALIDATION_VALIDATED

            if (validated) {

                return if (
                    reference.labelSource ==
                    LabeledReference.LABEL_SOURCE_HUMAN
                ) {
                    USER_CONFIRMED
                } else {
                    CLOUD_VALIDATED
                }
            }

            val isLocal =
                reference.aiSource ==
                    AnalysisSource.LOCAL.name

            val confident =
                (reference.aiConfidence ?: 0.0) >=
                    HIGH_CONFIDENCE_THRESHOLD

            return if (isLocal && confident) {
                LOCAL_HIGH_CONFIDENCE
            } else {
                LOCAL_LOW_CONFIDENCE
            }
        }

        /*
         * Authority weight in [0,1] used when combining
         * disagreeing evidence: the highest trust wins.
         */
        fun weight(
            level: TrustLevel
        ): Double {
            return level.authority / 4.0
        }
    }
}
