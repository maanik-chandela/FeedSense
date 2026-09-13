package com.example.feedsense.analysis.scheduling

/*
 * Milestone 8B-12.
 *
 * Controlled, documented reasons for every sampling decision.
 * Research analysis depends on this stable vocabulary rather
 * than arbitrary strings.
 */
enum class SamplingReason(val label: String) {

    /* The first candidate of a session; always analyzed. */
    FIRST_FRAME("FIRST_FRAME"),

    /* Candidate arrived inside the minimum analysis interval. */
    MIN_INTERVAL("MIN_INTERVAL"),

    /* A strong visual change (reel/ad/comments transition)
     * became eligible once the minimum interval elapsed. */
    VISUAL_CHANGE("VISUAL_CHANGE"),

    /* Safety ceiling: the maximum analysis interval elapsed and
     * the candidate was forced through for temporal coverage. */
    MAX_INTERVAL("MAX_INTERVAL"),

    /* Content has been static/low-change; the adaptive interval
     * is longer and this candidate is not yet due. */
    STATIC_CONTENT("STATIC_CONTENT"),

    /* A content transition was signalled (8B-11 UNIQUE with a
     * large distance, or an explicit suspicion flag). */
    TRANSITION_SIGNAL("TRANSITION_SIGNAL"),

    /* An interaction event (like/comment/share/save/follow/
     * skip/pause) made the candidate a priority. Scheduling
     * signal only; never modifies AI predictions. */
    INTERACTION_SIGNAL("INTERACTION_SIGNAL"),

    /* The adaptive cadence was reached without a special signal
     * (regular temporal refresh). */
    SCHEDULED_SAMPLE("SCHEDULED_SAMPLE"),

    /* Scheduling was disabled; documented pass-through (every
     * candidate analyzed). */
    DISABLED_PASSTHROUGH("DISABLED_PASSTHROUGH"),

    /* Invalid/incomplete context; the documented safe fallback
     * analyzed the frame rather than silently discarding
     * evidence. */
    UNAVAILABLE_FALLBACK("UNAVAILABLE_FALLBACK")
}