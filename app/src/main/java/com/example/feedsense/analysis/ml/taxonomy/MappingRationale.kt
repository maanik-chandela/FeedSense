package com.example.feedsense.analysis.ml.taxonomy

// --------------------------------
// MAPPING RATIONALE (8B-15-7)
// --------------------------------
//
// Every non-trivial mapping must have an explicit rationale
// explaining WHY the model label corresponds to the chosen
// FeedSense taxonomy key (section 8).
//
// The rationale should be concise and research-auditable.
// It distinguishes semantic equivalence from broader/narrower
// category relationships and domain-specific interpretations.

/*
 * Categorized rationale for a taxonomy mapping.
 */
enum class MappingRationale(val label: String) {

    /*
     * The model label and FeedSense taxonomy key are
     * semantically equivalent (same concept, same scope).
     */
    SEMANTIC_EQUIVALENCE("SEMANTIC_EQUIVALENCE"),

    /*
     * The FeedSense taxonomy key is broader than the model
     * label. The model predicts a narrower concept that
     * falls within the FeedSense category's scope.
     */
    BROADER_FEEDSENSE_CATEGORY("BROADER_FEEDSENSE_CATEGORY"),

    /*
     * The model label is broader than the FeedSense taxonomy
     * key. The FeedSense category is a specialization of what
     * the model predicts.
     */
    NARROWER_MODEL_CATEGORY("NARROWER_MODEL_CATEGORY"),

    /*
     * Mapping is based on domain-specific interpretation
     * (e.g. the model was trained on a specific domain where
     * the label has a particular meaning).
     */
    DOMAIN_SPECIFIC_INTERPRETATION("DOMAIN_SPECIFIC_INTERPRETATION"),

    /*
     * Mapping aligns with the research taxonomy structure
     * (e.g. a model class corresponds to a taxonomy domain).
     */
    RESEARCH_TAXONOMY_ALIGNMENT("RESEARCH_TAXONOMY_ALIGNMENT"),

    /*
     * Mapping was established through human review and
     * explicitly approved by a domain expert.
     */
    HUMAN_REVIEWED_MAPPING("HUMAN_REVIEWED_MAPPING")
}
