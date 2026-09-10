package com.example.feedsense.analysis.ml.repro

import java.security.MessageDigest

// --------------------------------
// CANONICAL SERIALIZER (8B-15-2)
// --------------------------------
//
// Byte-deterministic rendering of the composite reproducibility
// identity and its components.
//
// Determinism rules (8B-15-2 section 16):
//   - STABLE field ordering (fixed, declared; NOT reflection
//     order, NOT map-iteration order).
//   - STABLE enum representations (explicit .label, never
//     ordinal or toString()).
//   - STABLE numeric formatting (always the identity's own
//     toString(); never locale-formatted).
//   - STABLE null handling (an absent optional field serializes
//     as "null"; missing is never collapsed to "0"/"").
//   - STABLE collection ordering (lists keep their declared
//     order; NEVER ordered by Object.hashCode()).
//   - No unordered-JSON-map dependence: output is a flat,
//     ordered key list.
//   - No locale-dependent formatting.
//
// If this canonical form is hashed (see sha256Hex), the CANONICAL
// representation is what is hashed - never arbitrary debug output.

/*
 * Common interface so every enum participating in canonical
 * serialization exposes an explicit, stable label.
 */
interface ReprLabeled {
    val label: String
}

object ReproCanonicalSerializer {

    /* -------------- primitive rendering -------------- */

    private fun render(value: String?): String = value ?: "null"

    private fun render(value: Int?): String = value?.toString() ?: "null"

    private fun render(value: Long?): String = value?.toString() ?: "null"

    private fun render(value: Double?): String =
        value?.let {
            require(it.isFinite()) { "canonical serialization requires finite numbers" }
            it.toString()
        } ?: "null"

    private fun renderLabel(value: ReprLabeled?): String =
        value?.label ?: "null"

    private fun renderList(values: List<String>): String =
        values.joinToString(",", "[", "]") { render(it) }

    private fun renderDoubleList(values: List<Double>): String =
        values.joinToString(",", "[", "]") { render(it) }

    private fun line(key: String, value: String): String = "$key=$value"

    /* -------------- component renderers -------------- */

    fun serializeModel(model: ReproModelIdentity): String {
        val lines = mutableListOf<String>()
        lines.add(line("modelId", render(model.modelId)))
        lines.add(line("modelFamily", renderLabel(model.modelFamily)))
        lines.add(line("modelArchitecture", render(model.modelArchitecture)))
        lines.add(line("modelVersion", render(model.modelVersion)))
        lines.add(line("sourceName", render(model.sourceName)))
        lines.add(line("sourceLocation", render(model.sourceLocation)))
        lines.add(line("publisher", render(model.publisher)))
        lines.add(line("license", render(model.license)))
        lines.add(line("artifactFormat", model.artifactFormat.label))
        lines.add(line("parametersMillions", render(model.parametersMillions)))
        lines.add(line("intendedTask", render(model.intendedTask)))
        lines.add(line("releaseInfo", render(model.releaseInfo)))
        lines.add(line("identityVersion", render(model.identityVersion)))
        return "model{" + lines.joinToString("|") + "}"
    }

    fun serializeArtifact(artifact: ReproArtifactIdentity): String {
        val lines = mutableListOf<String>()
        lines.add(line("artifactId", render(artifact.artifactId)))
        lines.add(line("fileName", render(artifact.fileName)))
        lines.add(line("format", renderLabel(artifact.format)))
        lines.add(line("byteSize", render(artifact.byteSize)))
        lines.add(line("sha256", render(artifact.sha256)))
        lines.add(line("sourceReference", render(artifact.sourceReference)))
        lines.add(line("modelId", render(artifact.modelId)))
        lines.add(line("artifactVersion", render(artifact.artifactVersion)))
        lines.add(line("acquisitionTimestamp", render(artifact.acquisitionTimestamp)))
        lines.add(line("availability", renderLabel(artifact.availability)))
        lines.add(line("validationStatus", renderLabel(artifact.validationStatus)))
        return "artifact{" + lines.joinToString("|") + "}"
    }

    fun serializeRuntime(runtime: ReproRuntimeIdentity): String {
        val lines = mutableListOf<String>()
        lines.add(line("runtimeName", renderLabel(runtime.runtimeName)))
        lines.add(line("runtimeVersion", render(runtime.runtimeVersion)))
        lines.add(line("executionBackend", renderLabel(runtime.executionBackend)))
        lines.add(line("supportedPlatform", renderLabel(runtime.supportedPlatform)))
        lines.add(line("modelFormat", renderLabel(runtime.modelFormat)))
        lines.add(line("runtimeConfiguration", render(runtime.runtimeConfiguration)))
        return "runtime{" + lines.joinToString("|") + "}"
    }

    fun serializeQuantization(q: ReproQuantizationIdentity): String {
        val lines = mutableListOf<String>()
        lines.add(line("depth", renderLabel(q.depth)))
        lines.add(line("method", renderLabel(q.method)))
        lines.add(line("scope", renderLabel(q.scope)))
        lines.add(line("calibrationMethod", render(q.calibrationMethod)))
        lines.add(line("calibrationDatasetId", render(q.calibrationDatasetId)))
        lines.add(line("toolName", render(q.toolName)))
        lines.add(line("toolVersion", render(q.toolVersion)))
        lines.add(line("sourceArtifactHash", render(q.sourceArtifactHash)))
        lines.add(line("resultingArtifactHash", render(q.resultingArtifactHash)))
        return "quantization{" + lines.joinToString("|") + "}"
    }

    fun serializePreprocessing(p: ReproPreprocessingIdentity): String {
        val lines = mutableListOf<String>()
        lines.add(line("preprocessingVersion", render(p.preprocessingVersion)))
        lines.add(line("resizeMethod", renderLabel(p.resizeMethod)))
        lines.add(line("inputWidth", render(p.inputWidth)))
        lines.add(line("inputHeight", render(p.inputHeight)))
        lines.add(line("aspectRatioBehavior", renderLabel(p.aspectRatioBehavior)))
        lines.add(line("cropBehavior", render(p.cropBehavior)))
        lines.add(line("colorFormat", render(p.colorFormat)))
        lines.add(line("channelOrder", renderLabel(p.channelOrder)))
        lines.add(line("normalization.mean", renderDoubleList(p.normalization.mean)))
        lines.add(line("normalization.std", renderDoubleList(p.normalization.std)))
        lines.add(line("normalization.scaleToZeroOne", p.normalization.scaleToZeroOne.toString()))
        lines.add(line("alphaHandling", render(p.alphaHandling)))
        lines.add(line("orientationHandling", render(p.orientationHandling)))
        return "preprocessing{" + lines.joinToString("|") + "}"
    }

    fun serializeOutputMapping(o: ReproOutputMappingIdentity): String {
        val lines = mutableListOf<String>()
        lines.add(line("outputMappingVersion", render(o.outputMappingVersion)))
        lines.add(line("modelOutputLabels", renderList(o.modelOutputLabels)))
        lines.add(line("labelOrdering", renderList(o.labelOrdering)))
        lines.add(line("feedSenseCategoryMappingVersion", render(o.feedSenseCategoryMappingVersion)))
        lines.add(line("mappingMode", renderLabel(o.mappingMode)))
        lines.add(line("unknownBehavior", renderLabel(o.unknownBehavior)))
        lines.add(line("topK", render(o.topK)))
        lines.add(line("outputSemantics", render(o.outputSemantics)))
        return "outputMapping{" + lines.joinToString("|") + "}"
    }

    fun serializePrivacy(p: ReproPrivacyIdentity): String {
        val lines = mutableListOf<String>()
        lines.add(line("privacySanitizationVersion", render(p.privacySanitizationVersion)))
        lines.add(line("policyMode", renderLabel(p.policyMode)))
        lines.add(line("evidenceSourceType", renderLabel(p.evidenceSourceType)))
        return "privacy{" + lines.joinToString("|") + "}"
    }

    fun serializeProvenance(provenance: ReproArtifactProvenance?): String {
        if (provenance == null) return "provenance=null"
        val stepLines = provenance.steps.mapIndexed { i, step ->
            val conv = step.conversion?.let {
                listOf(
                    line("sourceFormat", renderLabel(it.sourceFormat)),
                    line("destinationFormat", renderLabel(it.destinationFormat)),
                    line("conversionTool", render(it.conversionTool)),
                    line("conversionToolVersion", render(it.conversionToolVersion)),
                    line("conversionConfiguration", render(it.conversionConfiguration)),
                    line("operatorCompatibilityAssumption", render(it.operatorCompatibilityAssumption)),
                    line("conversionTimestamp", render(it.conversionTimestamp)),
                    line("sourceArtifactHash", render(it.sourceArtifactHash)),
                    line("destinationArtifactHash", render(it.destinationArtifactHash))
                ).joinToString(",")
            }
            val body = buildList {
                add(line("role", renderLabel(step.role)))
                add(line("artifactId", render(step.artifactId)))
                if (conv != null) add("conversion{$conv}")
            }
            "step$i{" + body.joinToString("|") + "}"
        }
        val lines = stepLines + line("terminalArtifactId", render(provenance.terminalArtifactId))
        return "provenance{" + lines.joinToString("|") + "}"
    }

    /* -------------- composite -------------- */

    fun serialize(identity: ReproCompositeIdentity): String {
        val parts = listOf(
            serializeModel(identity.model),
            serializeArtifact(identity.artifact),
            serializeRuntime(identity.runtime),
            serializeQuantization(identity.quantization),
            serializePreprocessing(identity.preprocessing),
            serializeOutputMapping(identity.outputMapping),
            serializePrivacy(identity.privacy),
            serializeProvenance(identity.provenance)
        )
        return "repro{" + parts.joinToString("::") + "}"
    }

    /* -------------- canonical hash -------------- */

    fun sha256Hex(canonical: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}
