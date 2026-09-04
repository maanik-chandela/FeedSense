package com.example.feedsense.analysis.evaluation.comparative

/*
 * Milestone 8B-8.
 *
 * Duration stratification.
 *
 * Content duration is a known confounder for a feed-sensing
 * system (shorter content is harder to classify and evidence
 * signals are sparser). Both systems are compared within the
 * SAME frozen buckets so any duration-skew in accuracy is
 * visible rather than averaged away.
 *
 * Buckets (seconds), inclusive lower / exclusive upper:
 *   <= 5       : L5   ("very short")
 *   5 < d <=10 : M10  ("short")
 *   10< d <=30 : M30  ("brief")
 *   30< d <=90 : L90  ("medium")
 *   > 90       : XL   ("long")
 *
 * Duration comes from GroundTruth.durationSeconds (human truth),
 * which is the authoritative duration used by 8A.
 */
object DurationBuckets {

    enum class Bucket(val label: String) {
        L5("<=5s"),
        M10("5-10s"),
        M30("10-30s"),
        L90("30-90s"),
        XL(">90s"),
        UNKNOWN("UNKNOWN")
    }

    fun of(durationSeconds: Int?): Bucket {
        if (durationSeconds == null) return Bucket.UNKNOWN
        return when {
            durationSeconds <= 5 -> Bucket.L5
            durationSeconds <= 10 -> Bucket.M10
            durationSeconds <= 30 -> Bucket.M30
            durationSeconds <= 90 -> Bucket.L90
            else -> Bucket.XL
        }
    }
}
