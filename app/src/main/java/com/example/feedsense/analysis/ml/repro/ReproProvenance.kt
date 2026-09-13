package com.example.feedsense.analysis.ml.repro

// --------------------------------
// PROVENANCE (8B-15-2)
// --------------------------------
//
// Records the full provenance chain of an artifact WITHOUT
// collapsing it into a single "modelVersion" field. Each
// transition is distinguishable:
//
//   original source model
//       -> downloaded artifact
//       -> conversion
//       -> quantization
//       -> deployment artifact
//
// Conversion provenance records a single source->destination
// transition. ArtifactProvenance is an ordered list of such
// steps plus the terminal artifact, preserving the whole chain.

/*
 * The role of an artifact within the provenance chain.
 */
enum class ProvenanceRole(override val label: String) : ReprLabeled {
    ORIGINAL("ORIGINAL"),
    DOWNLOADED("DOWNLOADED"),
    CONVERTED("CONVERTED"),
    QUANTIZED("QUANTIZED"),
    DEPLOYMENT("DEPLOYMENT"),
    OTHER("OTHER")
}

/*
 * One source -> destination transition.
 */
data class ReproConversionRecord(
    val sourceFormat: ReproArtifactFormat,
    val destinationFormat: ReproArtifactFormat,
    val conversionTool: String,
    val conversionToolVersion: String? = null,
    val conversionConfiguration: String? = null,
    val operatorCompatibilityAssumption: String? = null,
    val conversionTimestamp: String? = null,
    val sourceArtifactHash: String? = null,
    val destinationArtifactHash: String? = null
) {

    init {
        require(conversionTool.isNotBlank()) { "conversionTool must be non-blank" }
        sourceArtifactHash?.let { s ->
            require(ReproArtifactIdentity.isSha256Hex(s)) { "sourceArtifactHash must be SHA-256 hex" }
        }
        destinationArtifactHash?.let { s ->
            require(ReproArtifactIdentity.isSha256Hex(s)) { "destinationArtifactHash must be SHA-256 hex" }
        }
        require(sourceFormat != destinationFormat) {
            "conversion must change format ($sourceFormat -> $destinationFormat)"
        }
    }

    val key: String
        get() = "${sourceFormat.label}->${destinationFormat.label}:${conversionTool}:${conversionToolVersion ?: "-"}"
}

/*
 * One step in the chain: an artifact plus the transition that
 * produced it (if any).
 */
data class ReproProvenanceStep(
    val role: ProvenanceRole,
    val artifactId: String,
    val conversion: ReproConversionRecord? = null
) {
    init {
        require(artifactId.isNotBlank()) { "step artifactId must be non-blank" }
    }
}

/*
 * A controlled, ordered provenance chain from original source to
 * a terminal (deployment) artifact.
 *
 * Format-changing transitions (CONVERTED role) MUST carry an
 * explicit ReproConversionRecord. Same-format transformations
 * (e.g. quantization) are captured by the composite identity's
 * quantizatization component - a QUANTIZED step records the
 * lineage but the quantization details live in
 * ReproQuantizationIdentity.
 */
data class ReproArtifactProvenance(
    val steps: List<ReproProvenanceStep>,
    val terminalArtifactId: String
) {

    init {
        require(steps.isNotEmpty()) { "provenance must have at least one step" }
        require(terminalArtifactId.isNotBlank()) { "terminalArtifactId must be non-blank" }
        require(steps.any { it.artifactId == terminalArtifactId }) {
            "terminalArtifactId must name one of the steps"
        }
        // A format-changing CONVERTED step requires an explicit
        // conversion record.
        for (step in steps) {
            if (step.role == ProvenanceRole.CONVERTED) {
                require(step.conversion != null) {
                    "step ${step.artifactId} (CONVERTED) must carry a conversion record"
                }
            }
            if (step.conversion != null) {
                require(step.conversion.destinationArtifactHash == null ||
                    step.conversion.destinationArtifactHash.isNotBlank()) {
                    "conversion destination must be present when recorded"
                }
            }
        }
    }

    val key: String
        get() = steps.joinToString("->") { it.artifactId }
}
