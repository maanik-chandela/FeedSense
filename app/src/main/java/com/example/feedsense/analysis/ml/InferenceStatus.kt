package com.example.feedsense.analysis.ml

// --------------------------------
// INFERENCE STATUS (8B-14)
// --------------------------------
//
// Controlled inference outcomes. These are the documented
// vocabulary for "what happened during this inference".
//
// The taxonomy deliberately mirrors the milestone list
// (SUCCESS / MODEL_UNAVAILABLE / INVALID_INPUT /
// PREPROCESSING_FAILURE / INFERENCE_FAILURE /
// OUTPUT_INVALID / RESOURCE_LIMIT / NOT_RUN) so the rest of
// FeedSense can reason about ML failures without inspecting
// runtime-specific exceptions.
//
// "model unavailable" is NEVER coerced into category
// "other": inference absence is explicit, baseline remains
// available, and evaluation can continue (8B-14 section 13,
// 34, 35).

enum class InferenceStatus(val label: String) {
    SUCCESS("SUCCESS"),
    MODEL_UNAVAILABLE("MODEL_UNAVAILABLE"),
    INVALID_INPUT("INVALID_INPUT"),
    PREPROCESSING_FAILURE("PREPROCESSING_FAILURE"),
    INFERENCE_FAILURE("INFERENCE_FAILURE"),
    OUTPUT_INVALID("OUTPUT_INVALID"),
    RESOURCE_LIMIT("RESOURCE_LIMIT"),
    NOT_RUN("NOT_RUN")
}

/*
 * Resource-failure subtypes. Prepared for real device
 * benchmarking later; this milestone never fabricates
 * measurements (8B-14 section 29).
 */
enum class ResourceFailureType(val label: String) {
    OUT_OF_MEMORY("OUT_OF_MEMORY"),
    UNSUPPORTED_DEVICE("UNSUPPORTED_DEVICE"),
    UNKNOWN("UNKNOWN")
}