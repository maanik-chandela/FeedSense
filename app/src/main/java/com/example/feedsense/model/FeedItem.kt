package com.example.feedsense.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.util.UUID

// --------------------------------
// FEED ITEM
// --------------------------------
//
// Milestone 7C.
//
// One content piece watched during a session.
//
// Captured frames are grouped into feed items by
// the segmentation logic in SessionRepository.
//
// A Reel shown for ~2 seconds and skipped still
// becomes one item, so extremely short interactions
// are not missed.
//
// Fields answer the core research questions:
//
// - category       : what type of content
// - contentType    : SHORT_VIDEO / LONG_VIDEO
// - durationSeconds: how long it was on screen
// - skipped        : whether it was a short glance
//

/*
 * Milestone 7T. sessionId drives per-session lookups;
 * the (sessionId, updatedAt) pair powers the 7O
 * immutability check that compares latest frame time
 * against latest item build time.
 */
@Entity(
    tableName = "feed_items",
    indices = [
        Index(
            name = "idx_feed_items_session",
            value = ["sessionId"]
        ),
        Index(
            name = "idx_feed_items_session_updated",
            value = ["sessionId", "updatedAt"]
        )
    ]
)
data class FeedItem(

    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val sessionId: String,

    val startTime: LocalDateTime,

    val endTime: LocalDateTime? = null,

    val durationSeconds: Int = 0,

    val category: String? = null,

    /*
     * Milestone 7V.
     *
     * Top-level domain of the category ("sports",
     * "entertainment", ...). The category column stores
     * the specific leaf ("cricket") while this keeps the
     * parent domain for roll-ups and filtering.
     */
    val categoryDomain: String? = null,

    val confidence: Double? = null,

    val topic: String? = null,

    val tone: String? = null,

    val contentType: String = CONTENT_UNKNOWN,

    val skipped: Boolean = false,

    val representativeFramePath: String,

    val frameCount: Int = 0,

    val interactionSignals: List<String> = emptyList(),

    val modelVersion: String? = null,

    val frameFingerprint: String? = null,

    /*
     * Milestone 7D.
     *
     * True when the local classification of this item
     * is uncertain (medium confidence or ambiguous).
     * The item is kept - a short Reel must never
     * disappear - but it is queued for review and its
     * category must not be treated as ground truth.
     */
    val needsReview: Boolean = false,

    /*
     * Milestone 7D.
     *
     * Candidate categories for uncertain items, shown
     * to the reviewer so they never have to type from
     * scratch.
     */
    val candidateCategories: List<String> = emptyList(),

    /*
     * Milestone 7D.
     *
     * Human/audit-readable evidence: keyword hits,
     * temporal lifecycle states, fingerprint distances
     * and reference-based refinements that produced
     * this item.
     */
    val classificationReason: String? = null,

    val updatedAt: LocalDateTime = LocalDateTime.now(),

    /*
     * Milestone 7F (Part 2).
     *
     * Content lifecycle signals for this item, e.g.
     * CONTENT_STARTED, CONTENT_CONTINUED, CONTENT_CHANGED,
     * CONTENT_SKIPPED, CONTENT_ENDED.
     */
    val contentTransitions: List<String> = emptyList(),

    /*
     * Milestone 7F (Part 3).
     *
     * Auditable interaction evidence, one entry per
     * detected signal encoded as
     * "signal|confidence|evidence".
     */
    val interactionEvidence: List<String> = emptyList(),

    /*
     * Milestone 7F (Part 5).
     *
     * Second-most common category across the item's
     * frames, so mixed content (e.g. a comedy skit with
     * sports cameos) is not collapsed into one label.
     */
    val secondaryCategory: String? = null,

    /*
     * Milestone 7P.
     *
     * ALL secondary categories the item plausibly
     * belongs to, ranked by frame support. More honest
     * than a single secondary label: mixed content is
     * represented as a ranked set, not one slot.
     */
    val secondaryCategories: List<String> = emptyList(),

    /*
     * Milestone 7W.
     *
     * Scored multi-label confidence (category -> score)
     * aggregated from the item's frames. The primary
     * label stays authoritative in `category`; this keeps
     * the full scored label set so mixed content is
     * represented with evidence, not just names.
     */
    val categoryScores: Map<String, Double> = emptyMap(),

    /*
     * Milestone 7P.
     *
     * True when the item blends content of more than one
     * category (or a frame-level secondary category
     * appeared under low confidence). Mixed items keep
     * their primary label but are flagged for review so
     * a single category is never silently assumed.
     */
    val mixedContent: Boolean = false,

    /*
     * Milestone 7F (Part 4).
     *
     * Estimated time the content was visible but paused
     * (frame gaps well beyond the normal capture
     * interval). Conserved: only intra-item gaps count.
     */
    val pausedDurationSeconds: Int = 0,

    /*
     * Milestone 7F (Part 4).
     *
     * durationSeconds minus estimated paused time. The
     * closest honest proxy for active watching we can
     * derive from captured frames alone.
     */
    val activeWatchDurationSeconds: Int = 0,

    /*
     * Milestone 7F (Part 6).
     *
     * Centralized uncertainty level (HIGH / MEDIUM /
     * LOW) derived from the aggregated confidence.
     */
    val uncertaintyLevel: String = UNCERTAINTY_LOW,

    /*
     * Milestone 8. Source distinguishes AI vs manual.
     *   AI     - auto-generated by frame analysis
     *   MANUAL - researcher manually recorded
     */
    val source: String = SOURCE_AI,

    /*
     * Milestone 8. Platform where this content was observed.
     */
    val platform: String? = null,

    /*
     * Milestone 8. Researcher override fields.
     * These NEVER overwrite AI predictions; they
     * coexist so the researcher can correct or verify.
     */
    val researcherCategory: String? = null,
    val researcherTopic: String? = null,
    val researcherNotes: String? = null,

    /*
     * Milestone 8. Researcher interaction flags.
     */
    val researcherLiked: Boolean = false,
    val researcherSkipped: Boolean = false,
    val researcherCommented: Boolean = false,
    val researcherShared: Boolean = false,
    val researcherSaved: Boolean = false,
    val researcherFollowed: Boolean = false,
    val researcherPaused: Boolean = false,
    val researcherReplayed: Boolean = false,

    /*
     * App context: which app was on screen when this
     * item was captured. Stable key ("youtube",
     * "instagram", ...). Set by AppChromeDetector
     * during frame analysis.
     */
    val appContext: String? = null,

    /*
     * When true this item was derived entirely from
     * app chrome frames (status bar, nav bar, app
     * header) and has no research content value.
     * Chrome-only items are filtered out of the
     * research log but kept in the database for
     * audit trail.
     */
    val chromeOnly: Boolean = false
) {

    companion object {

        const val SOURCE_AI = "AI"
        const val SOURCE_MANUAL = "MANUAL"

        const val CONTENT_UNKNOWN =
            "UNKNOWN"

        const val CONTENT_SHORT_VIDEO =
            "SHORT_VIDEO"

        const val CONTENT_LONG_VIDEO =
            "LONG_VIDEO"

        const val UNCERTAINTY_HIGH =
            "HIGH"

        const val UNCERTAINTY_MEDIUM =
            "MEDIUM"

        const val UNCERTAINTY_LOW =
            "LOW"

        // --------------------------------
        // CONTENT TRANSITIONS (7F Part 2)
        // --------------------------------

        const val TRANSITION_CONTENT_STARTED =
            "CONTENT_STARTED"

        const val TRANSITION_CONTENT_CONTINUED =
            "CONTENT_CONTINUED"

        const val TRANSITION_CONTENT_CHANGED =
            "CONTENT_CHANGED"

        const val TRANSITION_CONTENT_SKIPPED =
            "CONTENT_SKIPPED"

        const val TRANSITION_CONTENT_ENDED =
            "CONTENT_ENDED"
    }
}
