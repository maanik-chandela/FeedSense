package com.example.feedsense.analysis.evaluation

// --------------------------------
// TOPIC / TONE METRICS (Milestone 8A-3)
// --------------------------------
//
// Topic and tone are free-form short labels. The 8A-1 layer
// already records an exact-match agreement flag per item
// (topicAgreement / toneAgreement, null when the human did not
// provide a value for that dimension).
//
// A robust free-text metric is not feasible here (no frozen
// topic ontolology exists), which 8A-3 explicitly allows as a
// documented limitation. What we DO report honestly:
//
//   - agreement rate over items where the human supplied a
//     value (exact, case-insensitive match, as chosen by the
//     8A-1 comparison)
//   - how many items were UNKNOWN (human gave no value) so the
//     reader sees the metric only covers a subset
//
// This is an exact-match agreement, NOT a semantic-similarity
// measure; the distinction is stated in the note.

data class TopicToneMetrics(
    val dimension: String,
    val decided: Int,
    val unknown: Int,
    val total: Int,
    val agreement: MetricValue,
    val limitation: String = "exact string match only; " +
        "free-text similarity is not evaluated"
) {

    companion object {

        fun compute(
            // (agreement flag, wasTruthProvided) pairs
            pairs: List<Pair<Boolean, Boolean>>,
            dimension: String,
            minimumSample: Int = 1,
            confidenceLevel: Double = 0.95
        ): TopicToneMetrics {

            val decided =
                pairs.count { it.second }
            val unknown =
                pairs.count { !it.second }
            val agreements =
                pairs.count { it.second && it.first }

            val agreementValue =
                if (decided == 0) {
                    MetricValue.notApplicable(
                        note = "no item had a human $dimension value"
                    )
                } else if (decided < minimumSample) {
                    MetricValue.insufficientSample(
                        numerator = agreements,
                        denominator = decided
                    )
                } else {
                    MetricValue.defined(
                        value = agreements.toDouble() / decided,
                        numerator = agreements,
                        denominator = decided,
                        confidenceInterval =
                            WilsonInterval.forProportion(
                                agreements,
                                decided,
                                confidenceLevel
                            )
                    )
                }

            return TopicToneMetrics(
                dimension = dimension,
                decided = decided,
                unknown = unknown,
                total = pairs.size,
                agreement = agreementValue
            )
        }
    }
}