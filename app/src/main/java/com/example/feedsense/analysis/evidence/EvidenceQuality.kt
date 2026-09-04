package com.example.feedsense.analysis.evidence

/*
 * Milestone 8B-5.
 *
 * Evidence quality model.
 *
 * Represents the reliability of an extracted evidence
 * signal. This is NOT the same as AI confidence.
 *
 * A heuristic classifier can be highly confident while
 * being wrong. Evidence quality reflects the strength
 * of the underlying signal, not the classifier's belief.
 *
 * Examples:
 *   HIGH: Clear platform text "Instagram" on screen
 *   MEDIUM: Several OCR keywords supporting sports
 *   LOW: Single ambiguous OCR token
 *   NONE: No useful evidence extracted
 */
enum class EvidenceQuality {
    NONE,
    LOW,
    MEDIUM,
    HIGH
}
