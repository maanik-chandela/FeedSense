package com.example.feedsense.analysis.ml.taxonomy

import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.analysis.SchemaFreeze
import com.example.feedsense.analysis.ml.ModelInferenceResult
import com.example.feedsense.analysis.ml.RankedPrediction

// --------------------------------
// TAXONOMY MAPPING ENGINE (8B-15-7)
// --------------------------------
//
// Deterministic mapping from model-native predictions to
// the FeedSense research taxonomy.
//
// Determinism rules (section 16):
//   - For identical model artifact, model-native label/index,
//     label version, taxonomy version, and mapping version,
//     the mapping result MUST be identical.
//   - No randomness.
//   - No unordered iteration determining outcomes.
//   - No locale-dependent behavior.
//   - No timestamps affecting mapping.
//
// Research safety (section 22):
//   - A successful mapping does NOT imply the model
//     prediction is correct.
//   - A successful mapping does NOT imply the taxonomy
//     correspondence is empirically validated.
//
// The engine MUST NOT:
//   - Modify FeedItem, GroundTruth, EvaluationRecord,
//     AiPredictionRecord, or any production table.
//   - Silently force unmapped labels into categories.
//   - Renames model-native labels.

object TaxonomyMappingEngine {

    // --------------------------------
    // PUBLIC API
    // --------------------------------

    /*
     * Map a single model inference result against a set of
     * taxonomy mapping entries.
     *
     * Returns a deterministic list of TaxonomyMappingResult,
     * one per ranked prediction in the input.
     *
     * Input: ModelInferenceResult (from 8B-15-6 or earlier)
     * Output: List<TaxonomyMappingResult>
     */
    fun mapPredictions(
        inferenceResult: ModelInferenceResult,
        mappings: List<ModelTaxonomyMapping>,
        taxonomyVersion: String
    ): List<TaxonomyMappingResult> {
        return mapPredictions(
            predictions = inferenceResult.rankedPredictions,
            mappings = mappings,
            taxonomyVersion = taxonomyVersion
        )
    }

    /*
     * Map a list of ranked predictions against a set of
     * taxonomy mapping entries.
     *
     * Core deterministic mapping logic.
     */
    fun mapPredictions(
        predictions: List<RankedPrediction>,
        mappings: List<ModelTaxonomyMapping>,
        taxonomyVersion: String
    ): List<TaxonomyMappingResult> {

        // --------------------------------
        // Build deterministic lookup structures
        // --------------------------------

        val mappingTableVersion = resolveMappingTableVersion(
            mappings
        )
        val byLabelString = buildLabelStringIndex(mappings)
        val validTaxonomyKeys = CategoryCatalog.keys.toSet()

        // --------------------------------
        // Map each prediction
        // --------------------------------

        return predictions.mapIndexed { index, prediction ->
            mapSinglePrediction(
                prediction = prediction,
                predictionIndex = index,
                byLabelString = byLabelString,
                validTaxonomyKeys = validTaxonomyKeys,
                mappingTableVersion = mappingTableVersion,
                taxonomyVersion = taxonomyVersion
            )
        }
    }

    // --------------------------------
    // INTERNAL: single prediction mapping
    // --------------------------------

    private fun mapSinglePrediction(
        prediction: RankedPrediction,
        predictionIndex: Int,
        byLabelString: Map<String, List<ModelTaxonomyMapping>>,
        validTaxonomyKeys: Set<String>,
        mappingTableVersion: String,
        taxonomyVersion: String
    ): TaxonomyMappingResult {

        // --------------------------------
        // 1. Try label-string lookup first
        // --------------------------------
        //
        // Label string is the primary identity because
        // the prediction carries the model-native label,
        // not an index. The prediction's rank position
        // (predictionIndex) is NOT the model class index.

        val labelMappings = byLabelString[prediction.category]

        if (labelMappings != null) {
            if (labelMappings.size == 1) {
                return resolveMapping(
                    mapping = labelMappings.first(),
                    prediction = prediction,
                    predictionIndex = predictionIndex,
                    validTaxonomyKeys = validTaxonomyKeys,
                    mappingTableVersion = mappingTableVersion,
                    taxonomyVersion = taxonomyVersion
                )
            }
            // Multiple mappings for same label => ambiguous
            return TaxonomyMappingResult.Ambiguous(
                modelLabel = prediction.category,
                modelLabelIndex = predictionIndex,
                candidateTaxonomyKeys = labelMappings
                    .mapNotNull { it.feedSenseTaxonomyKey }
                    .distinct()
                    .sorted(),
                ambiguityReason = "Multiple mapping entries " +
                    "for label '${prediction.category}' with " +
                    "different taxonomy targets",
                mappingId = labelMappings.first().mappingId,
                mappingTableVersion = mappingTableVersion,
                taxonomyVersion = taxonomyVersion
            )
        }

        // --------------------------------
        // 2. Check if model label is itself a valid
        //    taxonomy key (implicit DIRECT mapping)
        // --------------------------------

        val normalizedLabel = CategoryCatalog.normalize(
            prediction.category
        )
        if (normalizedLabel != null) {
            return TaxonomyMappingResult.Mapped(
                modelLabel = prediction.category,
                modelLabelIndex = predictionIndex,
                feedSenseTaxonomyKey = normalizedLabel,
                feedSenseTaxonomyDisplayName = CategoryCatalog.displayName(
                    normalizedLabel
                ),
                status = MappingStatus.DIRECT,
                rationale = MappingRationale.SEMANTIC_EQUIVALENCE,
                provenance = MappingProvenance.PROJECT_DEFINED,
                mappingId = "implicit-direct:$normalizedLabel",
                mappingTableVersion = mappingTableVersion,
                taxonomyVersion = taxonomyVersion,
                rationaleNotes = "Model label '${prediction.category}' " +
                    "matches FeedSense taxonomy key " +
                    "'$normalizedLabel' via normalization"
            )
        }

        // --------------------------------
        // 3. No mapping found => UNMAPPED
        // --------------------------------

        return TaxonomyMappingResult.Unmapped(
            modelLabel = prediction.category,
            modelLabelIndex = predictionIndex,
            reason = "No mapping entry found for label " +
                "'${prediction.category}' and label does not " +
                "match any FeedSense taxonomy key",
            mappingId = "unmapped:${predictionIndex}:${prediction.category}",
            mappingTableVersion = mappingTableVersion,
            taxonomyVersion = taxonomyVersion
        )
    }

    // --------------------------------
    // INTERNAL: resolve a mapping entry
    // --------------------------------

    private fun resolveMapping(
        mapping: ModelTaxonomyMapping,
        prediction: RankedPrediction,
        predictionIndex: Int,
        validTaxonomyKeys: Set<String>,
        mappingTableVersion: String,
        taxonomyVersion: String
    ): TaxonomyMappingResult {

        return when (mapping.status) {
            MappingStatus.DIRECT,
            MappingStatus.MAPPED -> {
                val targetKey = mapping.feedSenseTaxonomyKey!!

                if (targetKey !in validTaxonomyKeys) {
                    return TaxonomyMappingResult.Invalid(
                        modelLabel = prediction.category,
                        modelLabelIndex = predictionIndex,
                        failureReason = "Mapping target " +
                            "'$targetKey' is not a valid " +
                            "FeedSense taxonomy key",
                        failureCode = MappingFailureCode.INVALID_TAXONOMY_KEY,
                        mappingId = mapping.mappingId,
                        mappingTableVersion = mappingTableVersion,
                        taxonomyVersion = taxonomyVersion
                    )
                }

                TaxonomyMappingResult.Mapped(
                    modelLabel = prediction.category,
                    modelLabelIndex = predictionIndex,
                    feedSenseTaxonomyKey = targetKey,
                    feedSenseTaxonomyDisplayName = CategoryCatalog.displayName(
                        targetKey
                    ),
                    status = mapping.status,
                    mappingStrength = mapping.mappingStrength,
                    rationale = mapping.rationale,
                    provenance = mapping.provenance,
                    mappingId = mapping.mappingId,
                    mappingTableVersion = mappingTableVersion,
                    taxonomyVersion = taxonomyVersion,
                    rationaleNotes = mapping.rationaleNotes
                )
            }

            MappingStatus.UNMAPPED -> {
                TaxonomyMappingResult.Unmapped(
                    modelLabel = prediction.category,
                    modelLabelIndex = predictionIndex,
                    reason = mapping.rationaleNotes
                        ?: "Explicitly unmapped",
                    mappingId = mapping.mappingId,
                    mappingTableVersion = mappingTableVersion,
                    taxonomyVersion = taxonomyVersion
                )
            }

            MappingStatus.UNSUPPORTED -> {
                TaxonomyMappingResult.Unmapped(
                    modelLabel = prediction.category,
                    modelLabelIndex = predictionIndex,
                    reason = "Label is outside the known " +
                        "label set for this model artifact",
                    mappingId = mapping.mappingId,
                    mappingTableVersion = mappingTableVersion,
                    taxonomyVersion = taxonomyVersion
                )
            }

            MappingStatus.REJECTED -> {
                TaxonomyMappingResult.Invalid(
                    modelLabel = prediction.category,
                    modelLabelIndex = predictionIndex,
                    failureReason = "Mapping was explicitly " +
                        "rejected: ${mapping.rationaleNotes ?: "no reason given"}",
                    failureCode = MappingFailureCode.INCOMPATIBLE_MAPPING,
                    mappingId = mapping.mappingId,
                    mappingTableVersion = mappingTableVersion,
                    taxonomyVersion = taxonomyVersion
                )
            }

            MappingStatus.AMBIGUOUS -> {
                TaxonomyMappingResult.Ambiguous(
                    modelLabel = prediction.category,
                    modelLabelIndex = predictionIndex,
                    candidateTaxonomyKeys = listOfNotNull(
                        mapping.feedSenseTaxonomyKey
                    ).ifEmpty {
                        listOf("unknown")
                    },
                    ambiguityReason = mapping.rationaleNotes
                        ?: "Explicitly ambiguous mapping",
                    mappingId = mapping.mappingId,
                    mappingTableVersion = mappingTableVersion,
                    taxonomyVersion = taxonomyVersion
                )
            }
        }
    }

    // --------------------------------
    // INTERNAL: helpers
    // --------------------------------

    private fun resolveMappingTableVersion(
        mappings: List<ModelTaxonomyMapping>
    ): String {
        return mappings.firstOrNull()?.mappingTableVersion
            ?: "unknown"
    }

    /*
     * Build a deterministic index from label string to
     * mapping entries, sorted by mappingId for stability.
     */
    private fun buildLabelStringIndex(
        mappings: List<ModelTaxonomyMapping>
    ): Map<String, List<ModelTaxonomyMapping>> {
        return mappings
            .groupBy { it.modelLabel }
            .mapValues { (_, entries) ->
                entries.sortedBy { it.mappingId }
            }
    }

    // --------------------------------
    // PUBLIC: taxonomy version computation
    // --------------------------------

    /*
     * Compute the canonical taxonomy version from the
     * frozen schema version and the current category keys.
     */
    fun computeTaxonomyVersion(): String {
        return TaxonomyMappingVersion.computeTaxonomyVersion(
            frozenVersion = SchemaFreeze.FREEZE_VERSION,
            sortedCategoryKeys = CategoryCatalog.keys.sorted()
        )
    }
}
