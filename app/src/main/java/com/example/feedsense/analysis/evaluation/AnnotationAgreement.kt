package com.example.feedsense.analysis.evaluation

import com.example.feedsense.model.GroundTruth

// --------------------------------
// ANNOTATION AGREEMENT (Milestone 8A-6)
// --------------------------------
//
// Inter-annotator agreement over the PRIMARY category label.
// Two metrics are computed:
//
//   - percent (crude) agreement  p0 = agreeingPairs / totalPairs
//   - Cohen's kappa (chance-corrected) over the label set
//     present in the truths
//
// Guard: when fewer than 2 annotators are present, the metrics
// are undefined and the caller MUST treat them as
// INSUFFICIENT_DATA_FOR_INTER_ANNOTATOR_STATISTICS. The
// analyzer never manufactures agreement where no second rater
// exists.

object AnnotationAgreement {

    const val INSUFFICIENT_DATA_FOR_INTER_ANNOTATOR_STATISTICS =
        "INSUFFICIENT_DATA_FOR_INTER_ANNOTATOR_STATISTICS"

    data class Agreement(
        val annotatorCount: Int,
        val percentAgreement: Double?,   // p0, null when < 2 raters
        val kappa: Double?,              // Cohen's kappa, null when < 2
        val level: String
    )

    /**
     * Agreement over the primary category of the given truths.
     * ```
     *   >= 2 raters -> percentAgreement + kappa computed
     *   <  2 raters -> both null, level =
     *                  INSUFFICIENT_DATA_FOR_INTER_ANNOTATOR_STATISTICS
     * ```
     */
    fun ofPrimaryCategory(truths: List<GroundTruth>): Agreement {
        val ratings = truths.map {
            CategoryCatalogNormalize.normalize(it.category)
        }
        return Agreement(
            annotatorCount = truths.size,
            percentAgreement =
                if (truths.size < 2) null else crudeAgreement(ratings),
            kappa = if (truths.size < 2) null else kappa(ratings),
            level = agreementLevel(truths)
        )
    }

    /**
     * The coarse level used by candidate-cause generation.
     * Only a value when >= 2 annotators are present.
     */
    fun agreementLevel(truths: List<GroundTruth>): String {
        if (truths.size < 2) {
            return INSUFFICIENT_DATA_FOR_INTER_ANNOTATOR_STATISTICS
        }
        val p0 = crudeAgreement(
            truths.map { CategoryCatalogNormalize.normalize(it.category) }
        )
        return when {
            p0 == 1.0 -> RootCauseTypes.AGREEMENT_FULL
            p0 >= 0.5 -> RootCauseTypes.AGREEMENT_PARTIAL
            else -> RootCauseTypes.AGREEMENT_DISAGREEMENT
        }
    }

    // --------------------------------
    // METRICS
    // --------------------------------

    /**
     * Proportion of annotator pairs agreeing on the primary label.
     * With N raters there are N*(N-1)/2 pairs; a pair agrees when
     * both picks are equal and non-null (two "missing" labels are
     * not counted as agreement).
     */
    private fun crudeAgreement(ratings: List<String?>): Double {
        var agreeing = 0.0
        var pairs = 0.0
        for (i in ratings.indices) {
            for (j in i + 1 until ratings.size) {
                val a = ratings[i]
                val b = ratings[j]
                if (a == null || b == null) continue
                pairs++
                if (a == b) agreeing++
            }
        }
        return if (pairs == 0.0) 0.0 else agreeing / pairs
    }

    /**
     * Cohen's kappa over the label categories observed.
     * Treats every annotator as a rater on the SAME item and
     * returns null (+ the insufficient-data marker downstream)
     * when there are fewer than 2 raters.
     */
    private fun kappa(ratings: List<String?>): Double? {
        val categories = ratings.filterNotNull().distinct()
        if (categories.size < 2) return null

        val n = ratings.size
        val obs = IntArray(categories.size)
        for (r in ratings) {
            val idx = categories.indexOf(r)
            if (idx >= 0) {
                obs[idx]++
            }
        }

        val po = obs.max().toDouble() / n  // one-item agreement
        var pe = 0.0
        obs.forEach { k -> pe += (k.toDouble() / n) * (k.toDouble() / n) }

        val denom = 1.0 - pe
        return if (denom == 0.0) 0.0 else (po - pe) / denom
    }
}

// Local alias to avoid importing CategoryCatalog into this
// lightweight, unit-testable agreement module.
private object CategoryCatalogNormalize {
    fun normalize(c: String?): String? {
        return c?.trim()?.takeIf { it.isNotEmpty() }
    }
}
