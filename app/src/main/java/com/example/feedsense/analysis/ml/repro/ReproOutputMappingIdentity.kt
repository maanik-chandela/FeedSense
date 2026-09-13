package com.example.feedsense.analysis.ml.repro

// --------------------------------
// OUTPUT MAPPING IDENTITY (8B-15-2)
// --------------------------------
//
// Model outputs must be versioned independently: the mapping
// adapts the model's raw labels to the existing FeedSense
// CategoryCatalog taxonomy WITHOUT redefining FeedSense
// categories.
//
// outputMappingVersion is the reproducibility component. The
// topology captures label ordering / mapping mode / unknown and
// top-K behaviour.

/*
 * Mode of mapping from model labels to FeedSense categories.
 */
enum class OutputMappingMode(override val label: String) : ReprLabeled {
    ONE_TO_ONE_CATEGORY("ONE_TO_ONE_CATEGORY"),
    MANUAL_MAP("MANUAL_MAP"),
    OTHER("OTHER"),
    UNKNOWN("UNKNOWN")
}

/*
 * Behaviour for a model label that has no FeedSense category.
 */
enum class UnknownMappingBehavior(override val label: String) : ReprLabeled {
    MAP_TO_UNKNOWN("MAP_TO_UNKNOWN"),
    REJECT("REJECT"),
    DROP("DROP"),
    OTHER("OTHER"),
    UNKNOWN("UNKNOWN")
}

/*
 * Immutable output-mapping identity.
 */
data class ReproOutputMappingIdentity(
    val outputMappingVersion: String,
    val modelOutputLabels: List<String>,
    val labelOrdering: List<String>,
    val feedSenseCategoryMappingVersion: String,
    val mappingMode: OutputMappingMode = OutputMappingMode.MANUAL_MAP,
    val unknownBehavior: UnknownMappingBehavior = UnknownMappingBehavior.MAP_TO_UNKNOWN,
    val topK: Int = 1,
    val outputSemantics: String? = null
) {

    init {
        require(outputMappingVersion.isNotBlank()) { "outputMappingVersion must be non-blank" }
        require(feedSenseCategoryMappingVersion.isNotBlank()) {
            "feedSenseCategoryMappingVersion must be non-blank"
        }
        require(modelOutputLabels.isNotEmpty()) { "modelOutputLabels must not be empty" }
        require(labelOrdering.isNotEmpty()) { "labelOrdering must not be empty" }
        require(modelOutputLabels.distinct().size == modelOutputLabels.size) {
            "modelOutputLabels must be distinct"
        }
        require(labelOrdering.size == modelOutputLabels.size) {
            "labelOrdering must cover every model output label"
        }
        require(labelOrdering.toSet() == modelOutputLabels.toSet()) {
            "labelOrdering must contain exactly the model output labels"
        }
        require(topK >= 1) { "topK must be >= 1" }
    }

    val key: String
        get() = "$outputMappingVersion:$feedSenseCategoryMappingVersion:$topK"
}
