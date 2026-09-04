package com.example.feedsense.analysis

import com.example.feedsense.model.GroundTruth

// --------------------------------
// ANNOTATION TAXONOMY (Milestone 8A-2)
// --------------------------------
//
// Single source of truth for every dropdown/selector the
// annotation workflow exposes. Each field DELEGATES to an
// already-frozen taxonomy rather than re-declaring values:
//
//   category / subcategory -> CategoryCatalog
//   platform               -> PlatformDetector
//   tone                   -> TextHeuristicClassifier
//   ambiguity              -> GroundTruth.VALID_AMBIGUITY
//   interaction            -> GroundTruth.INTERACTION_SIGNAL_KEYS
//   content type           -> GroundTruth.VALID_CONTENT_TYPES
//
// This guarantees there is no second, conflicting taxonomy
// anywhere in the annotation layer (Step 14 of the 8A-2
// requirements).

object AnnotationTaxonomy {

    fun categoryKeys(): List<String> =
        CategoryCatalog.keys

    fun categoryDisplayName(key: String?): String =
        CategoryCatalog.displayName(key)

    fun primaryCategories(): List<String> =
        CategoryCatalog.keys +
            listOf(GroundTruth.AMBIGUITY_UNKNOWN)

    fun secondaryCategories(): List<String> =
        CategoryCatalog.keys

    fun platforms(): List<String> =
        PlatformDetector().platformNames()

    fun tones(): List<String> =
        TextHeuristicClassifier.TONE_VALUES()

    fun ambiguities(): List<String> =
        GroundTruth.VALID_AMBIGUITY.toList()

    fun interactions(): List<String> =
        GroundTruth.INTERACTION_SIGNAL_KEYS

    fun contentTypes(): List<String> =
        GroundTruth.VALID_CONTENT_TYPES
}
