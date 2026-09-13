package com.example.feedsense.analysis.ml

// --------------------------------
// MODEL OUTPUT SPECIFICATION (8B-14)
// --------------------------------
//
// The explicit contract describing what a model produces.
//
//   - labels: the model's class labels. In FeedSense these
//     MUST map onto the existing category taxonomy through an
//     adapter; the taxonomy itself is never modified here.
//   - maxTopK: how many ranked alternatives may be surfaced.
//   - confidenceRange: the [min, max] interval a valid
//     confidence may occupy.

data class ModelOutputSpec(
    val specVersion: String,
    val labels: List<String>,
    val maxTopK: Int = DEFAULT_MAX_TOP_K,
    val confidenceRange: ClosedFloatingPointRange<Double> =
        0.0..1.0
) {

    init {
        require(specVersion.isNotBlank()) { "specVersion must be non-blank" }
        require(labels.isNotEmpty()) { "labels must not be empty" }
        require(labels.distinct().size == labels.size) {
            "labels must not contain duplicates"
        }
        require(maxTopK >= 1) { "maxTopK must be >= 1" }
        require(
            confidenceRange.start.isFinite() &&
                confidenceRange.endInclusive.isFinite()
        ) { "confidenceRange must be finite" }
    }

    /*
     * Direct category lookup for a machine label.
     */
    fun containsLabel(label: String): Boolean =
        label in labels

    companion object {
        const val DEFAULT_MAX_TOP_K = 5
    }
}