package com.example.feedsense.analysis.ml.taxonomy

// --------------------------------
// MAPPING PROVENANCE (8B-15-7)
// --------------------------------
//
// The origin and trust level of a taxonomy mapping entry.
//
// A mapping that exists in a table is NOT automatically
// validated or empirically confirmed. The provenance makes
// explicit what is known versus assumed (section 21).

/*
 * Origin and trust level of a mapping entry.
 */
enum class MappingProvenance(val label: String) {

    /*
     * Mapping was defined alongside the model artifact by
     * its publisher or the model selection process.
     */
    ARTIFACT_DEFINED("ARTIFACT_DEFINED"),

    /*
     * Mapping was defined by the FeedSense project
     * (researcher, taxonomy maintainer, or project config).
     */
    PROJECT_DEFINED("PROJECT_DEFINED"),

    /*
     * Mapping was reviewed and approved by a human domain
     * expert. This is the strongest provenance.
     */
    HUMAN_REVIEWED("HUMAN_REVIEWED"),

    /*
     * Mapping is provisional and may change. Not yet
     * confirmed by domain review.
     */
    PROVISIONAL("PROVISIONAL"),

    /*
     * Mapping is based on a research assumption (e.g.
     * semantic similarity) without empirical validation.
     */
    RESEARCH_ASSUMPTION("RESEARCH_ASSUMPTION")
}
