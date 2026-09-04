package com.example.feedsense.analysis.evidence

/*
 * Milestone 8B-5.
 *
 * Evidence type taxonomy.
 *
 * Categorizes the source domain of an evidence signal.
 * This is NOT the final content category.
 *
 * Example:
 *   OCR_TEXT: "IPL 2026" → evidence type is OCR_TEXT
 *   PLATFORM: "Instagram" → evidence type is PLATFORM
 *   VISUAL: "sports-like visual cues" → evidence type is VISUAL
 *
 * Evidence types are independent of final categories.
 * Multiple evidence types can support or contradict
 * the same category.
 */
enum class EvidenceType {
    OCR_TEXT,
    PLATFORM,
    VISUAL,
    LAYOUT,
    INTERACTION,
    TEMPORAL,
    CONTENT_METADATA
}
