package com.example.feedsense.analysis

// --------------------------------
// CONFIDENCE LEVEL
// --------------------------------
//
// Milestone 7F (Part 6).
//
// Single, centralized uncertainty model shared by the
// interaction detector and the feed item builder.
//
// HIGH    >= 0.8   (ConfidenceGate.DEFAULT_ACCEPT_CONFIDENCE)
// MEDIUM  >= 0.6   (ConfidenceGate.DEFAULT_MEDIUM_CONFIDENCE)
// LOW     < 0.6 or no numeric confidence at all
//
// The same thresholds drive the classification gate, so
// "confident", "medium" and "low" always mean the same
// thing across the whole pipeline.
//

enum class ConfidenceLevel {

    HIGH,
    MEDIUM,
    LOW;

    companion object {

        fun from(
            confidence: Double?
        ): ConfidenceLevel {

            val value =
                confidence
                    ?: return LOW

            return when {
                value >=
                        ConfidenceGate
                            .DEFAULT_ACCEPT_CONFIDENCE ->
                    HIGH

                value >=
                        ConfidenceGate
                            .DEFAULT_MEDIUM_CONFIDENCE ->
                    MEDIUM

                else ->
                    LOW
            }
        }
    }
}
