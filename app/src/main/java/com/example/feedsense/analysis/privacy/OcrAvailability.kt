package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-13.
 *
 * OCR availability (spec §8, §27).
 *
 * OCR unavailable must be an EXPLICIT privacy state. A frame that
 * could not be matched for text patterns is never silently
 * treated as "safe" - the processor marks the resulting
 * confidence/risk accordingly (conservative unknown-risk policy).
 */
enum class OcrAvailability(val label: String) {

    OCR_AVAILABLE("OCR_AVAILABLE"),

    OCR_UNAVAILABLE("OCR_UNAVAILABLE")
}