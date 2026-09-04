package com.example.feedsense.analysis.evaluation

// --------------------------------
// METRIC VALUE (Milestone 8A-3)
// --------------------------------
//
// A single computed metric result that is HONEST about its
// own state. 8A-3 forbids presenting a fake precision when
// there are no (or too few) valid samples, so every metric
// carries an explicit state plus the exact counts that
// produced it.
//
//   DEFINED                  - computed from a valid sample
//                              set
//   INSUFFICIENT_SAMPLE_SIZE - too few valid samples to
//                              report a meaningful estimate
//                              (>= 0 but below the config
//                              minimum)
//   NOT_APPLICABLE           - the metric is not defined for
//                              this configuration (e.g. a
//                              binary precision when the
//                              positive class is absent)
//   UNDEFINED                - the metric has no valid
//                              meaning at all (zero
//                              denominator, or the inputs do
//                              not support it, e.g. ECE on an
//                              empty / single-confidence
//                              dataset)
//
// numerator / denominator are ALWAYS surfaced so a reader can
// reproduce or audit the value. Nothing is ever silently
// dropped or averaged into a misleading figure.

data class MetricValue(
    val state: State,
    val value: Double? = null,
    val numerator: Int = 0,
    val denominator: Int = 0,
    val confidenceInterval: ConfidenceInterval? = null,
    val note: String? = null
) {

    val defined: Boolean
        get() = state == State.DEFINED && value != null

    override fun toString(): String {
        return when (state) {
            State.DEFINED ->
                "${value?.let { format(it) } ?: "n/a"} " +
                    "(n=$numerator/$denominator)"

            State.INSUFFICIENT_SAMPLE_SIZE ->
                "INSUFFICIENT_SAMPLE_SIZE (n=$numerator/$denominator)"

            State.NOT_APPLICABLE ->
                "NOT_APPLICABLE (n=$numerator/$denominator)"

            State.UNDEFINED -> "UNDEFINED (n=$numerator/$denominator)"
        }
    }

    enum class State {
        DEFINED,
        INSUFFICIENT_SAMPLE_SIZE,
        NOT_APPLICABLE,
        UNDEFINED
    }

    data class ConfidenceInterval(
        val lower: Double,
        val upper: Double,
        val confidenceLevel: Double
    ) {
        override fun toString(): String {
            return "${format(lower)}–${format(upper)} " +
                "(${(confidenceLevel * 100).toInt()}% CI)"
        }
    }

    companion object {

        fun defined(
            value: Double,
            numerator: Int,
            denominator: Int,
            confidenceInterval: ConfidenceInterval? = null,
            note: String? = null
        ): MetricValue {
            return MetricValue(
                state = State.DEFINED,
                value = value,
                numerator = numerator,
                denominator = denominator,
                confidenceInterval = confidenceInterval,
                note = note
            )
        }

        fun insufficientSample(
            numerator: Int,
            denominator: Int,
            note: String? = null
        ): MetricValue {
            return MetricValue(
                state = State.INSUFFICIENT_SAMPLE_SIZE,
                numerator = numerator,
                denominator = denominator,
                note = note ?: "fewer than the configured minimum samples"
            )
        }

        fun notApplicable(
            numerator: Int = 0,
            denominator: Int = 0,
            note: String? = null
        ): MetricValue {
            return MetricValue(
                state = State.NOT_APPLICABLE,
                numerator = numerator,
                denominator = denominator,
                note = note ?: "metric not defined for this configuration"
            )
        }

        fun undefined(
            numerator: Int = 0,
            denominator: Int = 0,
            note: String? = null
        ): MetricValue {
            return MetricValue(
                state = State.UNDEFINED,
                denominator = denominator,
                numerator = numerator,
                note = note ?: "cannot be computed (no supporting samples)"
            )
        }

        private fun format(value: Double): String {
            return String.format("%.4f", value)
        }
    }
}