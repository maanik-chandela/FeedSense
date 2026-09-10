package com.example.feedsense.analysis.ml.repro

import com.example.feedsense.analysis.ml.ModelFormat

// --------------------------------
// MODEL IDENTITY (8B-15-2)
// --------------------------------
//
// The identity of the MODEL, independent of any particular
// artifact file on disk.
//
// MODEL ID is deliberately distinct from ARTIFACT HASH:
//
//   - model identity answers "which model, which version, where
//     did it originate" (semantic).
//   - artifact identity answers "which exact bytes" (content).
//
// Two artifact FILES that claim to be the same model identity
// remain distinguishable by their artifact checksums. This is
// the reproducibility contract that makes a future claim like
// "FeedSense achieved X using model Y" auditable.
//
// A change to ANY identity field means a NEW model identity, not
// a silent mutation of an existing one (immutability, 8B-15-2).

/*
 * Model architecture family (e.g. MobileNetV4). Kept as a small
 * typed set so the vocabulary is stable and serializable.
 */
enum class ModelFamily(override val label: String) : ReprLabeled {
    MOBILENET_V4("MOBILENET_V4"),
    EFFICIENTNET_LITE("EFFICIENTNET_LITE"),
    OTHER("OTHER"),
    UNKNOWN("UNKNOWN")
}

/*
 * Immutable identity of a model. No artifact bytes live here.
 */
data class ReproModelIdentity(
    val modelId: String,
    val modelFamily: ModelFamily,
    val modelArchitecture: String,
    val modelVersion: String,
    val sourceName: String,
    val sourceLocation: String,
    val publisher: String? = null,
    val license: String,
    val artifactFormat: ModelFormat,
    val parametersMillions: Double? = null,
    val intendedTask: String,
    val releaseInfo: String? = null,
    val identityVersion: String = DEFAULT_IDENTITY_VERSION
) {

    init {
        require(modelId.isNotBlank()) { "modelId must be non-blank" }
        require(modelArchitecture.isNotBlank()) { "modelArchitecture must be non-blank" }
        require(modelVersion.isNotBlank()) { "modelVersion must be non-blank" }
        require(sourceName.isNotBlank()) { "sourceName must be non-blank" }
        require(sourceLocation.isNotBlank()) { "sourceLocation must be non-blank" }
        require(license.isNotBlank()) { "license must be non-blank" }
        require(intendedTask.isNotBlank()) { "intendedTask must be non-blank" }
        require(identityVersion.isNotBlank()) { "identityVersion must be non-blank" }
        parametersMillions?.let {
            require(it >= 0.0 && it.isFinite()) { "parametersMillions must be >= 0" }
        }
    }

    /*
     * Stable semantic key "<modelId>:<modelVersion>". Not a
     * content identity; two artifacts may share it yet differ
     * in bytes (artifact layer tracks that).
     */
    val key: String
        get() = "$modelId:$modelVersion"

    companion object {
        const val DEFAULT_IDENTITY_VERSION = "1"
    }
}

/*
 * A named, checkable source reference carried in the model
 * identity, mirroring the 8B-15-1 evidence discipline so no
 * origin claim is unattributed.
 */
data class ModelOriginSource(
    val name: String,
    val reference: String,
    val visitedDate: String? = null
) {
    init {
        require(name.isNotBlank()) { "origin source name must be non-blank" }
        require(reference.isNotBlank()) { "origin source reference must be non-blank" }
    }
}
