package com.example.feedsense.analysis.ml

// --------------------------------
// INFERENCE PROVENANCE (8B-14)
// --------------------------------
//
// Every ML inference result is traceable to:
//
//   - evidence / frame identifier
//   - modelId + modelVersion + checksum
//   - preprocessing version
//   - privacy version
//   - input spec version + output spec version
//   - controlled timestamp
//   - optional experiment identifier
//
// This is what makes historical predictions reproducible:
// a result is attributable to model-v1 + preprocess-v1 +
// privacy-v1, never silently re-interpreted because the
// application was updated (8B-14 sections 24-26, 49).

data class InferenceProvenance(
    val modelId: String,
    val modelVersion: String,
    val modelChecksum: String? = null,
    val preprocessVersion: String,
    val privacyVersion: String? = null,
    val inputSpecVersion: String,
    val outputSpecVersion: String,
    val evidenceId: String? = null,
    val experimentId: String? = null,
    val timestampMs: Long = 0L
) {

    /*
     * Compact experiment key, e.g.
     *
     *   model-v1/preprocess-v1/privacy-v1/input-v1/output-v1
     *
     * Feeding future experiments the ability to compare
     *   Experiment A: model-v1/preprocess-v1/privacy-v1
     *   Experiment B: model-v2/preprocess-v2/privacy-v1
     * (8B-14 section 49).
     */
    fun summaryKey(): String {
        return listOf(
            modelId,
            modelVersion,
            preprocessVersion,
            privacyVersion ?: "-",
            inputSpecVersion,
            outputSpecVersion
        ).joinToString(separator = "/")
    }

    companion object {

        /*
         * Derives a provenance record from a finished
         * inference result. Returns null when the result is
         * missing the version attributes required for a
         * reproducible claim.
         */
        fun from(result: ModelInferenceResult): InferenceProvenance? {
            val preprocessVersion = result.preprocessVersion ?: return null
            val inputSpecVersion = result.inputSpecVersion ?: return null
            val outputSpecVersion = result.outputSpecVersion ?: return null
            return InferenceProvenance(
                modelId = result.modelId,
                modelVersion = result.modelVersion,
                modelChecksum = result.modelChecksum,
                preprocessVersion = preprocessVersion,
                privacyVersion = result.privacyVersion,
                inputSpecVersion = inputSpecVersion,
                outputSpecVersion = outputSpecVersion,
                evidenceId = result.evidenceId,
                experimentId = result.experimentId,
                timestampMs = result.timestampMs
            )
        }
    }
}