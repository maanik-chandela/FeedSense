package com.example.feedsense.model

// --------------------------------
// PERSONALIZATION STATS
// --------------------------------
//
// Milestone 7O.
//
// Cross-session user knowledge, aggregated from the
// VALIDATED references (trusted memory) and the
// model_feedback dataset. It describes what the user
// actually watches and corrects, so future predictions
// can be personalized - WITHOUT ever rewriting
// historical sessions.
//
// Values are counts and simple frequency rankings, not
// claims about the user's identity. Raw texts never
// leave the device and never leak into cloud requests.
//

data class PersonalizationEntry(
    val key: String,
    val count: Int
)

data class PersonalizationStats(
    val totalValidatedExamples: Int,
    val topCategories: List<PersonalizationEntry>,
    val topPlatforms: List<PersonalizationEntry>,
    val topTopics: List<PersonalizationEntry>,
    val topTones: List<PersonalizationEntry>,

    /*
     * Correction counts from model_feedback:
     *
     * userCorrections - rows where a human disagreed
     *                   with the AI prediction.
     * userConfirmations - rows where the human agreed.
     * correctionRate - userCorrections / user rows,
     *                  or 0.0 when there are none yet.
     */
    val userCorrections: Int,
    val userConfirmations: Int,
    val correctionRate: Double,

    /*
     * Honesty note: this reflects only the samples the
     * user happened to review. It is a calibration
     * signal, not a scientific accuracy claim.
     */
    val observedAccuracyPercent: Double
) {

    companion object {

        val EMPTY: PersonalizationStats =
            PersonalizationStats(
                totalValidatedExamples = 0,
                topCategories = emptyList(),
                topPlatforms = emptyList(),
                topTopics = emptyList(),
                topTones = emptyList(),
                userCorrections = 0,
                userConfirmations = 0,
                correctionRate = 0.0,
                observedAccuracyPercent = 0.0
            )
    }
}
