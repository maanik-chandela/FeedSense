package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-13.
 *
 * Privacy policy MODE (spec §16).
 *
 * A mode selects the deterministic rule table (PrivacyRuleTable)
 * and the frame-drop behaviour. It is deliberately a small,
 * versioned set - three modes, each with documented what-gets-
 * anonymized / retained / dropped behaviour:
 *
 *   RESEARCH : preserve research utility. System chrome and
 *              notification previews are blurred; identifiers
 *              and private text are masked. Never drops.
 *   BALANCED : between the two; some private text is blurred
 *              rather than masked, location is pixelated. Never
 *              drops.
 *   STRICT   : mask all sensitive classes, crop UI bands and
 *              allow DROP_FRAME when risk is high and the
 *              remaining research value is negligible.
 *
 * The MODE is orthogonal to the existing legacy PrivacyMode
 * (RESEARCH_MODE / DEBUG_MODE) which stays for 8B-10
 * compatibility.
 */
enum class PrivacyPolicyMode(val label: String) {

    RESEARCH("RESEARCH"),
    BALANCED("BALANCED"),
    STRICT("STRICT");

    /*
     * Whether DROP_FRAME is permitted under this mode. Only
     * STRICT drops (and only when the risk/value conditions in
     * PrivacyProcessor hold); dropping is measurable metadata,
     * never silent (spec §14).
     */
    val allowsFrameDrop: Boolean
        get() = this == STRICT
}