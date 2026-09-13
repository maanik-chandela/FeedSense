package com.example.feedsense.analysis.ml.evaluation

import java.security.MessageDigest

// --------------------------------
// EVALUATION BOUNDARY SERIALIZER (8B-15-8)
// --------------------------------
//
// Deterministic, privacy-safe serialization of evaluation
// boundary types.
//
// Determinism rules:
//   - STABLE field ordering (fixed, declared order).
//   - STABLE enum representations (.label, never ordinal).
//   - STABLE numeric formatting (toString() only).
//   - STABLE null handling ("null" for absent values).
//   - No unordered-map dependence.
//   - No locale-dependent formatting.
//
// The same logical input MUST produce byte-identical
// serialized output.

object EvaluationBoundarySerializer {

    private const val CONFIDENCE_DECIMALS = 6

    // --------------------------------
    // CANDIDATE SNAPSHOT SERIALIZATION
    // --------------------------------

    /*
     * Deterministic single-line JSON text of a candidate
     * snapshot.
     */
    fun serializeSnapshot(
        snapshot: EvaluationCandidateSnapshot
    ): String {
        val map = snapshotMetadataMap(snapshot)
        return map.entries
            .sortedBy { it.key }
            .joinToString(
                prefix = "{",
                postfix = "}",
                separator = ","
            ) { (key, value) ->
                "\"${escape(key)}\":\"${escape(value)}\""
            }
    }

    /*
     * Deterministic metadata map of a candidate snapshot.
     */
    fun snapshotMetadataMap(
        snapshot: EvaluationCandidateSnapshot
    ): Map<String, String> {
        val map = linkedMapOf<String, String>()
        map["candidateId"] = snapshot.candidateId
        map["modelId"] = snapshot.modelId
        map["modelVersion"] = snapshot.modelVersion
        snapshot.modelChecksum?.let {
            map["modelChecksum"] = it
        }
        snapshot.runtimeVersion?.let {
            map["runtimeVersion"] = it
        }
        snapshot.preprocessingVersion?.let {
            map["preprocessingVersion"] = it
        }
        snapshot.outputInterpretationVersion?.let {
            map["outputInterpretationVersion"] = it
        }
        map["modelNativeLabel"] = snapshot.modelNativeLabel
        map["modelNativeLabelIndex"] =
            snapshot.modelNativeLabelIndex.toString()
        map["predictionRank"] =
            snapshot.predictionRank.toString()
        snapshot.predictionScore?.let {
            map["predictionScore"] = formatDouble(it)
        }
        snapshot.inferenceStatus?.let {
            map["inferenceStatus"] = it
        }
        map["mappingId"] = snapshot.mappingId
        map["mappingTableVersion"] =
            snapshot.mappingTableVersion
        map["taxonomyVersion"] = snapshot.taxonomyVersion
        map["taxonomyIdentity"] = snapshot.taxonomyIdentity
        map["mappingStatus"] =
            snapshot.mappingStatus.label
        snapshot.mappedTaxonomyKey?.let {
            map["mappedTaxonomyKey"] = it
        }
        snapshot.mappedTaxonomyDisplayName?.let {
            map["mappedTaxonomyDisplayName"] = it
        }
        snapshot.mappingStrength?.let {
            map["mappingStrength"] = formatDouble(it)
        }
        snapshot.mappingProvenance?.let {
            map["mappingProvenance"] = it
        }
        snapshot.evidenceId?.let {
            map["evidenceId"] = it
        }
        snapshot.sessionId?.let {
            map["sessionId"] = it
        }
        map["eligibilityStatus"] =
            snapshot.eligibility.status.label
        map["eligibilityReason"] =
            snapshot.eligibility.reason.label
        snapshot.eligibility.reasonDetail?.let {
            map["eligibilityReasonDetail"] = it
        }
        map["deterministicCreationHash"] =
            snapshot.deterministicCreationHash
        map["creationTimestampMs"] =
            snapshot.creationTimestampMs.toString()
        return map
    }

    // --------------------------------
    // ELIGIBILITY SERIALIZATION
    // --------------------------------

    /*
     * Deterministic JSON text of an eligibility decision.
     */
    fun serializeEligibility(
        eligibility: EvaluationEligibility
    ): String {
        val map = eligibilityMetadataMap(eligibility)
        return map.entries
            .sortedBy { it.key }
            .joinToString(
                prefix = "{",
                postfix = "}",
                separator = ","
            ) { (key, value) ->
                "\"${escape(key)}\":\"${escape(value)}\""
            }
    }

    /*
     * Deterministic metadata map of an eligibility decision.
     */
    fun eligibilityMetadataMap(
        eligibility: EvaluationEligibility
    ): Map<String, String> {
        val map = linkedMapOf<String, String>()
        map["status"] = eligibility.status.label
        map["reason"] = eligibility.reason.label
        eligibility.reasonDetail?.let {
            map["reasonDetail"] = it
        }
        eligibility.mappingStatus?.let {
            map["mappingStatus"] = it.label
        }
        eligibility.expectedTaxonomyVersion?.let {
            map["expectedTaxonomyVersion"] = it
        }
        eligibility.actualTaxonomyVersion?.let {
            map["actualTaxonomyVersion"] = it
        }
        eligibility.failureCode?.let {
            map["failureCode"] = it.label
        }
        return map
    }

    // --------------------------------
    // COMPARISON RESULT SERIALIZATION
    // --------------------------------

    /*
     * Deterministic JSON text of a comparison result.
     */
    fun serializeComparisonResult(
        result: EvaluationComparisonResult
    ): String {
        val map = comparisonResultMetadataMap(result)
        return map.entries
            .sortedBy { it.key }
            .joinToString(
                prefix = "{",
                postfix = "}",
                separator = ","
            ) { (key, value) ->
                "\"${escape(key)}\":\"${escape(value)}\""
            }
    }

    /*
     * Deterministic metadata map of a comparison result.
     */
    fun comparisonResultMetadataMap(
        result: EvaluationComparisonResult
    ): Map<String, String> {
        val map = linkedMapOf<String, String>()
        map["status"] = result.status.label
        map["reasons"] =
            result.reasons.joinToString("|") { it.label }
        result.predictionTaxonomyId?.let {
            map["predictionTaxonomyId"] = it
        }
        result.groundTruthTaxonomyId?.let {
            map["groundTruthTaxonomyId"] = it
        }
        map["taxonomyIdentityCompatible"] =
            result.taxonomyIdentityCompatible.toString()
        map["taxonomyVersionCompatible"] =
            result.taxonomyVersionCompatible.toString()
        map["groundTruthAnnotationValid"] =
            result.groundTruthAnnotationValid.toString()
        map["comparisonHash"] = result.comparisonHash
        return map
    }

    // --------------------------------
    // FULL BOUNDARY RESULT SERIALIZATION
    // --------------------------------

    /*
     * Deterministic JSON text of a complete boundary result.
     */
    fun serializeBoundaryResult(
        result: EvaluationBoundaryResult
    ): String {
        val parts = listOf(
            serializeSnapshot(result.candidateSnapshot),
            serializeEligibility(result.eligibility),
            result.comparison?.let {
                serializeComparisonResult(it)
            } ?: "null"
        )
        return "boundary{" +
            parts.joinToString("::") +
            "}"
    }

    // --------------------------------
    // DETERMINISTIC HASHING
    // --------------------------------

    /*
     * Compute a deterministic hash of a candidate snapshot.
     * The same snapshot always produces the same hash.
     */
    fun computeSnapshotHash(
        snapshot: EvaluationCandidateSnapshot
    ): String {
        val canonical = snapshotMetadataMap(snapshot)
            .entries
            .filter { it.key != "deterministicCreationHash" &&
                it.key != "creationTimestampMs" }
            .sortedBy { it.key }
            .joinToString("|") { (k, v) -> "$k=$v" }
        return sha256Hex(canonical)
    }

    /*
     * Compute a deterministic hash of a comparison result.
     * The same result always produces the same hash.
     */
    fun computeComparisonHash(
        result: EvaluationComparisonResult
    ): String {
        val canonical = comparisonResultMetadataMap(result)
            .entries
            .filter { it.key != "comparisonHash" }
            .sortedBy { it.key }
            .joinToString("|") { (k, v) -> "$k=$v" }
        return sha256Hex(canonical)
    }

    // --------------------------------
    // INTERNAL
    // --------------------------------

    private fun escape(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t")
    }

    private fun formatDouble(value: Double): String {
        val factor = Math.pow(
            10.0,
            CONFIDENCE_DECIMALS.toDouble()
        )
        return (Math.round(value * factor) / factor)
            .toString()
    }

    private fun sha256Hex(canonical: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(
                canonical.toByteArray(Charsets.UTF_8)
            )
        return digest.joinToString("") {
            "%02x".format(it)
        }
    }
}
