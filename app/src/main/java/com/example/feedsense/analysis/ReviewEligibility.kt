package com.example.feedsense.analysis

// --------------------------------
// REVIEW ELIGIBILITY
// --------------------------------
//
// Milestone 7K (Part 2).
//
// Decides whether a FeedItem should be surfaced for
// human review. An item is eligible when it is genuinely
// uncertain - never for every frame. Signals:
//
//   baseUncertain   frame-level uncertainty OR average
//                   confidence below the accept threshold
//   conflict        validated reference evidence
//                   contradicts the prediction (7G)
//   category-tie    two categories have equal frame
//                   support (no clear winner)
//   mixed-content   a secondary category was detected
//   topic-ambiguous multiple topics with no clear
//                   majority
//   tone-ambiguous  multiple tones with no clear
//                   majority
//   platform-conflict frames in the same item come from
//                   different apps (detection vs visual/
//                   text evidence conflict)
//
// Pure and deterministic so it can be unit-tested and
// reused by any future classification path.
//

class ReviewEligibility {

    data class Decision(
        val needsReview: Boolean,
        val reasons: List<String>
    )

    fun decide(
        baseUncertain: Boolean,
        conflict: Boolean,
        categories: List<String?>,
        secondaryCategory: String?,
        topics: List<String?>,
        tones: List<String?>,
        applications: List<String?>,
        /*
         * Milestone 7P. An item whose primary category,
         * topic or tone could not be determined at all is
         * honest-to-unknown and belongs in the review
         * queue just as much as a low-confidence one.
         */
        unknownCategory: Boolean = false,
        unknownTopic: Boolean = false,
        unknownTone: Boolean = false
    ): Decision {

        val reasons =
            mutableListOf<String>()

        if (baseUncertain) {
            reasons += REASON_LOW_CONFIDENCE
        }

        if (conflict) {
            reasons += REASON_CONFLICTING_SIGNALS
        }

        if (unknownCategory) {
            reasons += REASON_UNKNOWN_CATEGORY
        }

        if (unknownTopic) {
            reasons += REASON_UNKNOWN_TOPIC
        }

        if (unknownTone) {
            reasons += REASON_UNKNOWN_TONE
        }

        if (isCategoryTie(categories)) {
            reasons += REASON_CATEGORY_TIE
        }

        if (secondaryCategory != null) {
            reasons += REASON_MIXED_CONTENT
        }

        if (isAmbiguous(topics)) {
            reasons += REASON_TOPIC_AMBIGUOUS
        }

        if (isAmbiguous(tones)) {
            reasons += REASON_TONE_AMBIGUOUS
        }

        if (isPlatformConflict(applications)) {
            reasons += REASON_PLATFORM_CONFLICT
        }

        return Decision(
            needsReview = reasons.isNotEmpty(),
            reasons = reasons
        )
    }

    /*
     * Two or more distinct categories whose top support
     * is shared (a genuine tie).
     */
    private fun isCategoryTie(
        categories: List<String?>
    ): Boolean {

        val counts =
            categories
                .filterNotNull()
                .groupingBy { it }
                .eachCount()

        if (counts.size < 2) {
            return false
        }

        val top =
            counts.values.maxOrNull() ?: return false

        return counts.values.count {
            it == top
        } >= 2
    }

    /*
     * Two or more distinct values where no single value
     * holds a strict majority.
     */
    private fun isAmbiguous(
        values: List<String?>
    ): Boolean {

        val present =
            values.filterNotNull()

        if (present.size < 2) {
            return false
        }

        val counts =
            present
                .groupingBy { it }
                .eachCount()

        if (counts.size < 2) {
            return false
        }

        val top =
            counts.values.maxOrNull() ?: return false

        return top * 2 <= present.size
    }

    /*
     * Frames inside one item belong to different
     * applications: the platform signal itself is
     * conflicted, which usually means the visual/text
     * evidence disagrees with the detection.
     */
    private fun isPlatformConflict(
        applications: List<String?>
    ): Boolean {

        return applications
            .filterNotNull()
            .distinct()
            .size >= 2
    }

    companion object {

        const val REASON_LOW_CONFIDENCE =
            "low-confidence"

        /*
         * Milestone 7P. Canonical name for contradictory
         * validated-reference signals; kept compatible
         * with the historical "reference-conflict" value.
         */
        const val REASON_CONFLICTING_SIGNALS =
            "conflicting-signals"

        const val REASON_REFERENCE_CONFLICT =
            "reference-conflict"

        const val REASON_CATEGORY_TIE =
            "category-tie"

        const val REASON_MIXED_CONTENT =
            "mixed-content"

        const val REASON_TOPIC_AMBIGUOUS =
            "topic-ambiguous"

        const val REASON_TONE_AMBIGUOUS =
            "tone-ambiguous"

        const val REASON_PLATFORM_CONFLICT =
            "platform-conflict"

        /*
         * Milestone 7P. The classifier produced no
         * category/topic/tone at all - the item is honest
         * about not knowing.
         */
        const val REASON_UNKNOWN_CATEGORY =
            "unknown-category"

        const val REASON_UNKNOWN_TOPIC =
            "unknown-topic"

        const val REASON_UNKNOWN_TONE =
            "unknown-tone"
    }
}
