package com.example.feedsense.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.util.UUID

// --------------------------------
// GROUND TRUTH (Milestone 8A-1)
// --------------------------------
//
// The human evaluation of an evaluated item: the reference
// truth against which the AI prediction is measured.
//
// One row per (evaluationItem, annotatorId). The annotator
// dimension means multiple raters can independently label
// the same item (inter-annotator agreement / adjudication
// is an 8A-2 concern), while the ambiguity state captures
// cases that are genuinely hard to label honestly.
//
// This table deliberately SEPARATES what the human recorded
// (GroundTruth) from whether the AI was right
// (EvaluationRecord). The two are never merged into a
// single "correct=true" field on the feed item.

@Entity(
    tableName = "ground_truths",
    indices = [
        Index(
            name = "idx_ground_truths_evaluation_item",
            value = ["evaluationItemId"]
        ),
        Index(
            name = "idx_ground_truths_annotator",
            value = ["annotatorId"]
        )
    ]
)
data class GroundTruth(

    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val evaluationItemId: String,

    /*
     * Who recorded this truth. Nullable so a single-rater
     * study can omit it; set to a stable analyst id in
     * multi-rater studies. Never stores PII.
     */
    val annotatorId: String? = null,

    val recordedAt: LocalDateTime = LocalDateTime.now(),

    // --------------------------------
    // PRIMARY TRUTH LABEL
    // --------------------------------

    val category: String? = null,

    val categoryDomain: String? = null,

    // --------------------------------
    // MULTI-LABEL
    // --------------------------------

    val secondaryCategories: List<String> = emptyList(),

    // --------------------------------
    // AMBIGUITY
    // --------------------------------
    //
    // An honest assessment of how labelable the content
    // is. Different from AI uncertainty: this is the
    // human's judgment that the content genuinely resists
    // a single clean label.
    //
    //   CLEAR     - a single category is obvious
    //   AMBIGUOUS - categories overlap; not obvious
    //   MIXED     - genuinely multiple categories present
    //   UNKNOWN   - cannot be determined from the frames
    //

    val ambiguity: String = AMBIGUITY_CLEAR,

    // --------------------------------
    // OTHER TRUTH DIMENSIONS
    // --------------------------------

    val platform: String? = null,

    val contentType: String? = null,

    val durationSeconds: Int? = null,

    /*
     * Skipped ground truth as an explicit tri-state:
     *   true  = the content was skipped
     *   false = it was watched / not skipped
     *   null  = UNKNOWN (there is not enough evidence)
     *
     * Distinct from deriving "skipped" purely from duration:
     * a short item may be watched fully, or a paused long item
     * may not have been skipped at all. The evaluator decides
     * from the actual evidence.
     */
    val skipped: Boolean? = null,

    // --------------------------------
    // INTERACTION GROUND TRUTH
    // --------------------------------
    //
    // Each interaction signal is a tri-state:
    //   true  = evidence the interaction occurred
    //   false = clear evidence it did NOT occur
    //   null  = UNKNOWN (no visible evidence either way)
    //
    // IMPORTANT honesty rule: "no visible evidence of a like"
    // does NOT mean liked = false. It means liked = UNKNOWN
    // (null). The evaluator must explicitly set false only
    // when the evidence supports it. The annotation system
    // NEVER fills UNKNOWN with false.
    //
    // InteractionClassifier signal vocabulary (liked /
    // commented / shared / saved / followed / paused /
    // playing) is used so the ground truth aligns with the
    // AI's interaction vocabulary.

    val liked: Boolean? = null,

    val commented: Boolean? = null,

    val shared: Boolean? = null,

    val saved: Boolean? = null,

    val followed: Boolean? = null,

    val paused: Boolean? = null,

    val playing: Boolean? = null,

    /*
     * Legacy echo of the interaction SIGNALS the evaluator
     * marked as TRUE, stored as a pipe list for backwards
     * compatibility with EvaluationRecord's interaction
     * comparison. Derived from the tri-state fields by the
     * repository (e.g. liked=TRUE adds "liked"). The
     * tri-state fields above are the authoritative source.
     */
    val interactionSignals: List<String> = emptyList(),

    val topic: String? = null,

    val tone: String? = null,

    val notes: String? = null
) {

    companion object {

        /*
         * Human ambiguity judgment. Frozen for 8A.
         */
        const val AMBIGUITY_CLEAR =
            "CLEAR"

        const val AMBIGUITY_AMBIGUOUS =
            "AMBIGUOUS"

        const val AMBIGUITY_MIXED =
            "MIXED"

        const val AMBIGUITY_UNKNOWN =
            "UNKNOWN"

        val VALID_AMBIGUITY = setOf(
            AMBIGUITY_CLEAR,
            AMBIGUITY_AMBIGUOUS,
            AMBIGUITY_MIXED,
            AMBIGUITY_UNKNOWN
        )

        // --------------------------------
        // INTERACTION SIGNAL KEYS
        // --------------------------------
        //
        // Matches the InteractionClassifier vocabulary so
        // ground-truth interaction labels align with the
        // AI's interaction predictions.

        const val INTERACTION_LIKED = "liked"
        const val INTERACTION_COMMENTED = "commented"
        const val INTERACTION_SHARED = "shared"
        const val INTERACTION_SAVED = "saved"
        const val INTERACTION_FOLLOWED = "followed"
        const val INTERACTION_PAUSED = "paused"
        const val INTERACTION_PLAYING = "playing"

        val INTERACTION_SIGNAL_KEYS = listOf(
            INTERACTION_LIKED,
            INTERACTION_COMMENTED,
            INTERACTION_SHARED,
            INTERACTION_SAVED,
            INTERACTION_FOLLOWED,
            INTERACTION_PAUSED,
            INTERACTION_PLAYING
        )

        /*
         * Human annotation content types (8A-2). The AI
         * taxonomy stays SHORT_VIDEO/LONG_VIDEO/UNKNOWN;
         * OTHER is offered as a ground-truth value only and
         * never changes the frozen AI constants.
         */
        const val CONTENT_TYPE_SHORT_VIDEO = "SHORT_VIDEO"
        const val CONTENT_TYPE_LONG_VIDEO = "LONG_VIDEO"
        const val CONTENT_TYPE_OTHER = "OTHER"
        const val CONTENT_TYPE_UNKNOWN = "UNKNOWN"

        val VALID_CONTENT_TYPES = listOf(
            CONTENT_TYPE_SHORT_VIDEO,
            CONTENT_TYPE_LONG_VIDEO,
            CONTENT_TYPE_OTHER,
            CONTENT_TYPE_UNKNOWN
        )

        /*
         * Distribution of the tri-state interaction fields
         * back to a pipe-friendly signal list, so only the
         * interactions the human marked TRUE appear (matching
         * how the AI's interactionSignals is expressed).
         */
        fun signalsMarkedTrue(
            truth: GroundTruth
        ): List<String> {

            val signals = mutableListOf<String>()

            if (truth.liked == true) {
                signals += INTERACTION_LIKED
            }
            if (truth.commented == true) {
                signals += INTERACTION_COMMENTED
            }
            if (truth.shared == true) {
                signals += INTERACTION_SHARED
            }
            if (truth.saved == true) {
                signals += INTERACTION_SAVED
            }
            if (truth.followed == true) {
                signals += INTERACTION_FOLLOWED
            }
            if (truth.paused == true) {
                signals += INTERACTION_PAUSED
            }
            if (truth.playing == true) {
                signals += INTERACTION_PLAYING
            }

            return signals
        }
    }
}
