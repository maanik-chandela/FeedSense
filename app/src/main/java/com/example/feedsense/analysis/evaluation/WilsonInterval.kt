package com.example.feedsense.analysis.evaluation

import kotlin.math.sqrt

// --------------------------------
// WILSON SCORE INTERVAL (Milestone 8A-3)
// --------------------------------
//
// A proportion confidence interval computed with the Wilson
// (score) method, which is recommended for small sample sizes
// because it does not collapse to zero when the observed
// proportion is 0 or 1 (as the normal/Wald interval does).
//
// Pure, deterministic, unit-testable. Used to attach honest
// uncertainty to every proportion metric in the report.

object WilsonInterval {

    /*
     * Returns the two-sided Wilson interval for a proportion
     * observed as `successes / total`, at the given confidence
     * level (default 0.95 meaning 95% confidence).
     *
     * Returns null when there is nothing to estimate (total of
     * zero) - callers should surface UNDEFINED in that case.
     */
    fun forProportion(
        successes: Int,
        total: Int,
        confidenceLevel: Double = 0.95
    ): MetricValue.ConfidenceInterval? {

        if (total <= 0) return null

        val n = total.toDouble()
        val p = successes.toDouble() / n

        // Two-tailed z for the requested confidence level.
        // Computed fresh (no table) so arbitrary levels work.
        val z = zScore((1.0 + confidenceLevel) / 2.0)

        val denom = 1.0 + (z * z) / n
        val centre = (p + (z * z) / (2.0 * n)) / denom
        val halfWidth =
            z * sqrt(
                (p * (1.0 - p) + (z * z) / (4.0 * n)) / n
            ) / denom

        val lower = (centre - halfWidth).coerceIn(0.0, 1.0)
        val upper = (centre + halfWidth).coerceIn(0.0, 1.0)

        return MetricValue.ConfidenceInterval(
            lower = lower,
            upper = upper,
            confidenceLevel = confidenceLevel
        )
    }

    /*
     * Inverse standard normal CDF (probit). Uses the Acklam
     * rational approximation which is accurate to ~1e-9 for
     * the probability range used in interval estimates.
     */
    private fun zScore(probability: Double): Double {

        val a = 0.0
        val b = 1.0

        if (probability <= a || probability >= b) {
            throw IllegalArgumentException(
                "probability must be in (0, 1): $probability"
            )
        }

        val p = probability

        val a0 = -3.969683028665376e+01
        val a1 = 2.209460984245205e+02
        val a2 = -2.759285104469687e+02
        val a3 = 1.383577518672690e+02
        val a4 = -3.066479806614716e+01
        val a5 = 2.506628277459239e+00

        val b1 = -5.447609879822406e+01
        val b2 = 1.615858368580409e+02
        val b3 = -1.556989798598866e+02
        val b4 = 6.680131188771972e+01
        val b5 = -1.328068155288572e+01

        val c1 = -7.784894002430293e-03
        val c2 = -3.223964580411365e-01
        val c3 = -2.400758277161838e+00
        val c4 = -2.549732539343734e+00
        val c5 = 4.374664141464968e+00
        val c6 = 2.938163982698783e+00

        val d1 = 7.784695709041462e-03
        val d2 = 3.224671290700398e-01
        val d3 = 2.445134137142996e+00
        val d4 = 3.754408661907416e+00

        val pLow = 0.02425
        val pHigh = 1.0 - pLow

        // Rational approximation for the central region.
        val q: Double
        val r: Double
        if (p < pLow) {
            // Lower tail.
            q = sqrt(-2.0 * ln(p))
            return (((((c1 * q + c2) * q + c3) * q + c4) * q + c5) * q + c6) /
                ((((d1 * q + d2) * q + d3) * q + d4) * q + 1.0)
        } else if (p <= pHigh) {
            // Central region.
            q = p - 0.5
            r = q * q
            return (((((a0 * r + a1) * r + a2) * r + a3) * r + a4) * r + a5) * q /
                (((((b1 * r + b2) * r + b3) * r + b4) * r + b5) * r + 1.0)
        } else {
            // Upper tail.
            q = sqrt(-2.0 * ln(1.0 - p))
            return -(((((c1 * q + c2) * q + c3) * q + c4) * q + c5) * q + c6) /
                ((((d1 * q + d2) * q + d3) * q + d4) * q + 1.0)
        }
    }

    private fun ln(value: Double): Double = kotlin.math.ln(value)
}