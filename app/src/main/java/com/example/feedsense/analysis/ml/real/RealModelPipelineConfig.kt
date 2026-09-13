package com.example.feedsense.analysis.ml.real

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.analysis.SchemaFreeze
import com.example.feedsense.analysis.ml.FullPipelineConfig
import com.example.feedsense.analysis.ml.OutputInterpretationConfig
import com.example.feedsense.analysis.ml.OutputSemantics
import com.example.feedsense.analysis.ml.runtime.litert.MobileNetV2Artifact
import com.example.feedsense.analysis.ml.runtime.litert.MobileNetV2Labels
import com.example.feedsense.analysis.ml.taxonomy.MappingProvenance
import com.example.feedsense.analysis.ml.taxonomy.MappingRationale
import com.example.feedsense.analysis.ml.taxonomy.MappingStatus
import com.example.feedsense.analysis.ml.taxonomy.ModelTaxonomyMapping
import com.example.feedsense.analysis.ml.taxonomy.TaxonomyMappingVersion

// --------------------------------
// REAL MODEL PIPELINE CONFIG (8B-15-9, Phase 6-8)
// --------------------------------
//
// Creates the full pipeline configuration for the real
// MobileNetV2 model, connecting through 8B-15-6 (decoder),
// 8B-15-7 (taxonomy mapping), and 8B-15-8 (evaluation).
//
// This config bridges the real model output to the existing
// research pipeline without modifying any existing contracts.

/**
 * Factory for creating the full pipeline configuration
 * for the real MobileNetV2 model.
 */
object RealModelPipelineFactory {

    /**
     * Creates the FullPipelineConfig for the real model.
     *
     * Uses the representative test label subset for initial
     * pipeline validation. The full 1001-label config should
     * be used once the complete label file is loaded.
     *
     * @param labels the label list to use (defaults to representative subset)
     * @return the full pipeline configuration
     */
    fun create(
        labels: List<String> = MobileNetV2Labels.REPRESENTATIVE_TEST_LABELS
    ): FullPipelineConfig {
        val taxonomyVersion = TaxonomyMappingVersion.computeTaxonomyVersion(
            frozenVersion = SchemaFreeze.FREEZE_VERSION,
            sortedCategoryKeys = CategoryCatalog.keys.sorted()
        )

        val mappingTableVersion = "mobilenet-v2-mapping-v1"

        return FullPipelineConfig(
            modelId = MobileNetV2Artifact.MODEL_ID,
            modelVersion = MobileNetV2Artifact.MODEL_VERSION,
            modelChecksum = null,
            interpretationConfig = OutputInterpretationConfig(
                interpretationVersion = "mobilenet-v2-interp-v1",
                labels = labels,
                outputSemantics = if (MobileNetV2Artifact.OUTPUT_IS_SOFTMAX) {
                    OutputSemantics.RAW_SCORES
                } else {
                    OutputSemantics.SOFTMAX
                },
                topK = 5
            ),
            taxonomyMappings = createMappings(labels, mappingTableVersion, taxonomyVersion),
            taxonomyVersion = taxonomyVersion,
            mappingTableVersion = mappingTableVersion
        )
    }

    /**
     * Creates taxonomy mappings for the given label list.
     *
     * Only maps labels that correspond to FeedSense taxonomy
     * keys. Unknown labels are explicitly UNMAPPED rather than
     * forced into incorrect categories.
     *
     * The FeedSense taxonomy has ~60 categories. MobileNetV2's
     * ImageNet labels overlap with a subset of these. The
     * mapping below covers the most relevant intersections.
     */
    private fun createMappings(
        labels: List<String>,
        mappingTableVersion: String,
        taxonomyVersion: String
    ): List<ModelTaxonomyMapping> {
        val mappings = mutableListOf<ModelTaxonomyMapping>()
        val catalogKeys = CategoryCatalog.keys.toSet()

        for ((index, label) in labels.withIndex()) {
            val normalizedLabel = label.lowercase().trim()
            val taxonomyKey = findTaxonomyMapping(normalizedLabel, catalogKeys)

            if (taxonomyKey != null) {
                mappings.add(
                    ModelTaxonomyMapping(
                        mappingId = "mobilenet-v2-${index}-${normalizedLabel}",
                        modelArtifactId = MobileNetV2Artifact.ARTIFACT_ID,
                        modelArtifactVersion = MobileNetV2Artifact.ARTIFACT_VERSION,
                        modelLabelIndex = index,
                        modelLabel = normalizedLabel,
                        feedSenseTaxonomyKey = taxonomyKey,
                        status = MappingStatus.MAPPED,
                        rationale = MappingRationale.BROADER_FEEDSENSE_CATEGORY,
                        provenance = MappingProvenance.PROJECT_DEFINED,
                        mappingTableVersion = mappingTableVersion,
                        taxonomyVersion = taxonomyVersion,
                        rationaleNotes = "ImageNet label mapped to FeedSense taxonomy"
                    )
                )
            } else {
                mappings.add(
                    ModelTaxonomyMapping(
                        mappingId = "mobilenet-v2-${index}-${normalizedLabel}",
                        modelArtifactId = MobileNetV2Artifact.ARTIFACT_ID,
                        modelArtifactVersion = MobileNetV2Artifact.ARTIFACT_VERSION,
                        modelLabelIndex = index,
                        modelLabel = normalizedLabel,
                        feedSenseTaxonomyKey = null,
                        status = MappingStatus.UNMAPPED,
                        provenance = MappingProvenance.PROJECT_DEFINED,
                        mappingTableVersion = mappingTableVersion,
                        taxonomyVersion = taxonomyVersion,
                        rationaleNotes = "No FeedSense taxonomy equivalent for ImageNet label"
                    )
                )
            }
        }

        return mappings
    }

    /**
     * Attempts to find a FeedSense taxonomy key that matches
     * the given ImageNet label.
     *
     * This is a heuristic mapping. The mapping should be
     * refined as the model's actual output behavior is
     * understood.
     */
    private fun findTaxonomyMapping(
        label: String,
        catalogKeys: Set<String>
    ): String? {
        // Direct match
        if (label in catalogKeys) return label

        // Common ImageNet -> FeedSense mappings
        val knownMappings = mapOf(
            "tench" to "fishing",
            "goldfish" to "fishing",
            "cock" to "animals",
            "hen" to "animals",
            "ostrich" to "animals",
            "dog" to "animals",
            "cat" to "animals",
            "horse" to "animals",
            "laptop" to "technology",
            "cellular_telephone" to "technology",
            "television" to "technology",
            "monitor" to "technology",
            "computer_keyboard" to "technology",
            "bookcase" to "education",
            "library" to "education",
            "school_bus" to "education",
            "castle" to "architecture",
            "church" to "architecture",
            "mosque" to "architecture",
            "palace" to "architecture",
            "soccer_ball" to "sports",
            "baseball" to "sports",
            "basketball" to "sports",
            "tennis_ball" to "sports",
            "golf_ball" to "sports",
            "football_helmet" to "sports",
            "bikini" to "fashion",
            "bow_tie" to "fashion",
            "necktie" to "fashion",
            "sunglasses" to "fashion",
            "pizza" to "food",
            "ice_cream" to "food",
            "chocolate_sauce" to "food",
            "pretzel" to "food",
            "bagel" to "food",
            "cheeseburger" to "food",
            "hotdog" to "food",
            "guacamole" to "food",
            "espresso" to "food",
            "cup" to "food",
            "wine_bottle" to "food",
            "beer_bottle" to "food",
            "coffee_mug" to "food",
            "plate" to "food",
            "studio_couch" to "furniture",
            "bed" to "furniture",
            "dining_table" to "furniture",
            "desk" to "furniture",
            "toilet" to "bathroom",
            "bathtub" to "bathroom",
            "shower_cap" to "bathroom"
        )

        return knownMappings[label]
    }
}
