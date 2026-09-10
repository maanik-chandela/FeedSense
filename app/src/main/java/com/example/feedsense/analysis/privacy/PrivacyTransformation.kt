package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-13.
 *
 * Explicit privacy transformations (spec §11).
 *
 * Every transformation is chosen by a PrivacyRule on the
 * DECIDE stage - never silently. The severity ordering below is
 * the deterministic precedence used when two regions overlap:
 * the stronger transformation for the point is applied.
 *
 *   NONE       - no change (policy disabled / not enabled).
 *   BLUR       - box blur: approximate visual structure stays,
 *                content becomes unreadable.
 *   PIXELATE   - fixed cell mosaic: crisp squares, content lost.
 *   MASK       - deterministic marking pattern replacing the
 *                content; nothing readable remains.
 *   CROP       - the band is removed from the safe output
 *                entirely (full-width/height bands only).
 *   DROP_FRAME - the whole frame is discarded; only privacy-safe
 *                metadata is emitted.
 *
 * BLUR vs PIXELATE: blur preserves approximate structure (useful
 * around notifications), pixelate gives a crisper, clearly
 * redacted mosaic (useful for usernames/chats).
 */
enum class PrivacyTransformation(
    val label: String,
    val severity: Int,
) {

    NONE("NONE", 0),

    BLUR("BLUR", 1),

    PIXELATE("PIXELATE", 2),

    MASK("MASK", 3),

    CROP("CROP", 4),

    DROP_FRAME("DROP_FRAME", 5)
}