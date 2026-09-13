package com.example.feedsense.analysis.ml.runtime

import com.example.feedsense.analysis.ml.preprocess.PreprocessedInput
import com.example.feedsense.analysis.ml.repro.ReproArtifactIdentity
import com.example.feedsense.analysis.ml.repro.ReproRuntimeIdentity

// --------------------------------
// RUNTIME PROVENANCE (8B-15-5)
// --------------------------------
//
// Privacy provenance preservation through the runtime adapter.
//
// The adapter must:
//   - preserve evidence provenance from preprocessing
//   - establish that input originated from the privacy-sanitized
//     preprocessing boundary
//   - NOT accept arbitrary raw screenshot bytes
//   - NOT bypass privacy metadata
//   - NOT reconstruct unsanitized evidence
//   - NOT weaken anonymization rules
//
// The adapter does NOT modify the anonymization system. It only
// carries the privacy contract forward.

/**
 * Complete provenance chain for one adapter inference. Carries
 * the full identity chain from artifact → runtime →
 * preprocessing → evidence without any private content.
 *
 * Safe for serialization: contains only stable identifiers,
 * version strings, and hashes. Never contains pixels, OCR text,
 * user content, or private coordinates.
 */
data class RuntimeInferenceProvenance(
    /**
     * The model artifact identity.
     */
    val artifactIdentity: ReproArtifactIdentity,

    /**
     * The runtime identity.
     */
    val runtimeIdentity: ReproRuntimeIdentity,

    /**
     * Preprocessing version from the config.
     */
    val preprocessingVersion: String,

    /**
     * Preprocessing canonical config key.
     */
    val preprocessingConfigKey: String,

    /**
     * Privacy sanitization version.
     */
    val privacyVersion: String,

    /**
     * Privacy policy mode.
     */
    val policyMode: String,

    /**
     * Privacy sanitization status.
     */
    val sanitizationStatus: String,

    /**
     * Evidence identifier (stable, non-PII).
     */
    val evidenceId: String?,

    /**
     * Session identifier.
     */
    val sessionId: String?,

    /**
     * Feed item identifier.
     */
    val feedItemId: String?,

    /**
     * Preprocessing fingerprint (deterministic, content-free).
     */
    val preprocessingFingerprint: String
) {
    init {
        require(preprocessingVersion.isNotBlank()) {
            "preprocessingVersion must be non-blank"
        }
        require(preprocessingConfigKey.isNotBlank()) {
            "preprocessingConfigKey must be non-blank"
        }
        require(privacyVersion.isNotBlank()) {
            "privacyVersion must be non-blank"
        }
        require(preprocessingFingerprint.isNotBlank()) {
            "preprocessingFingerprint must be non-blank"
        }
    }

    /**
     * Stable, content-free identity summary.
     */
    val identitySummary: String
        get() = buildString {
            append("artifact=${artifactIdentity.artifactId}")
            append(":runtime=${runtimeIdentity.key}")
            append(":preprocess=$preprocessingVersion")
            append(":config=$preprocessingConfigKey")
            append(":privacy=$privacyVersion")
            if (evidenceId != null) append(":evidence=$evidenceId")
            if (sessionId != null) append(":session=$sessionId")
            if (feedItemId != null) append(":item=$feedItemId")
        }
}

/**
 * Factory for building provenance from a PreprocessedInput and
 * the adapter configuration.
 */
object RuntimeProvenanceFactory {

    /**
     * Builds complete provenance from the preprocessing output
     * and adapter configuration.
     *
     * This is the ONLY approved path for provenance creation.
     * It ensures the evidence chain is preserved without any
     * privacy weakening.
     */
    fun build(
        preprocessed: PreprocessedInput,
        artifact: ReproArtifactIdentity,
        runtime: ReproRuntimeIdentity
    ): RuntimeInferenceProvenance {
        return RuntimeInferenceProvenance(
            artifactIdentity = artifact,
            runtimeIdentity = runtime,
            preprocessingVersion = preprocessed.configVersion,
            preprocessingConfigKey = preprocessed.configKey,
            privacyVersion = preprocessed.privacyVersion,
            policyMode = preprocessed.policyMode,
            sanitizationStatus = preprocessed.sanitizationStatus,
            evidenceId = preprocessed.evidenceId,
            sessionId = preprocessed.sessionId,
            feedItemId = preprocessed.feedItemId,
            preprocessingFingerprint = preprocessed.fingerprint
        )
    }

    /**
     * Verifies that a preprocessed input carries valid privacy
     * provenance for the adapter boundary.
     *
     * The adapter MUST NOT accept inputs that bypass the
     * privacy-sanitized preprocessing boundary.
     */
    fun verifyPrivacyProvenance(
        preprocessed: PreprocessedInput
    ): CompatibilityCheckResult {
        // Privacy version must be present
        if (preprocessed.privacyVersion.isBlank()) {
            return CompatibilityCheckResult.Fail(
                RuntimeFailureFactory.invalidProvenance(
                    "missing privacy version in preprocessed input"
                )
            )
        }

        // Sanitization status must be present
        if (preprocessed.sanitizationStatus.isBlank()) {
            return CompatibilityCheckResult.Fail(
                RuntimeFailureFactory.invalidProvenance(
                    "missing sanitization status in preprocessed input"
                )
            )
        }

        // Policy mode must be present
        if (preprocessed.policyMode.isBlank()) {
            return CompatibilityCheckResult.Fail(
                RuntimeFailureFactory.invalidProvenance(
                    "missing policy mode in preprocessed input"
                )
            )
        }

        // Fingerprint must be present (establishes preprocessing
        // boundary was crossed)
        if (preprocessed.fingerprint.isBlank()) {
            return CompatibilityCheckResult.Fail(
                RuntimeFailureFactory.invalidProvenance(
                    "missing preprocessing fingerprint"
                )
            )
        }

        return CompatibilityCheckResult.Pass(
            artifactId = "<provenance-verified>",
            runtimeKey = "<provenance-verified>",
            preprocessingVersion = preprocessed.configVersion,
            inputSignature = "provenance-verified"
        )
    }
}
