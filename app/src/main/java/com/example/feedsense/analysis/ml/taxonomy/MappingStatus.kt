package com.example.feedsense.analysis.ml.taxonomy

// --------------------------------
// MAPPING STATUS (8B-15-7)
// --------------------------------
//
// Explicit statuses for the relationship between a
// model-native label and the FeedSense taxonomy.
//
// A model label may legitimately have no FeedSense
// equivalent. Forcing every label into a category would
// silently inflate apparent model quality (section 22).

/*
 * The model label maps directly to a FeedSense taxonomy
 * key with no transformation.
 */
enum class MappingStatus(val label: String) {

    /*
     * Model label == FeedSense taxonomy key (case-normalized).
     * Deterministic: the same label always produces DIRECT.
     */
    DIRECT("DIRECT"),

    /*
     * A deliberate mapping was established between the model
     * label and a (possibly different) FeedSense taxonomy key.
     * Provenance and rationale are required.
     */
    MAPPED("MAPPED"),

    /*
     * No FeedSense taxonomy equivalent exists or is desired.
     * The label is intentionally not forced into a category.
     */
    UNMAPPED("UNMAPPED"),

    /*
     * The model label maps to more than one FeedSense
     * taxonomy key and no resolution rule is defined.
     * Do not arbitrarily pick one.
     */
    AMBIGUOUS("AMBIGUOUS"),

    /*
     * The model label is outside the known label set for
     * the model artifact this mapping table was built for.
     */
    UNSUPPORTED("UNSUPPORTED"),

    /*
     * A mapping entry exists but was explicitly rejected
     * (e.g. semantic mismatch confirmed by review).
     */
    REJECTED("REJECTED")
}
