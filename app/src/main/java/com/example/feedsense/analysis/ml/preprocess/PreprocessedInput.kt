package com.example.feedsense.analysis.ml.preprocess

import com.example.feedsense.analysis.ml.ModelInput
import java.security.MessageDigest

// --------------------------------
// PREPROCESSED INPUT (8B-15-3)
// --------------------------------
//
// The deterministic, privacy-stamped model input produced by the
// preprocessing boundary.
//
// Every preprocessed input is traceable to its source evidence via
// stable identifiers and carries the full preprocessing + privacy
// version chain, WITHOUT storing any sensitive content (no pixels,
// no OCR, no private co-ordinates).

/**
 * Immutable result of one deterministic preprocessing run.
 *
 * @param input             the framework-agnostic tensor (NHWC).
 * @param configVersion     the preprocessing version.
 * @param configKey         canonical configuration key.
 * @param sourceWidth       source frame width (after orientation is
 *     the pre-spatial width).
 * @param sourceHeight      source frame height.
 * @param capturedOrientation the recorded capture orientation.
 * @param privacyVersion    the privacy sanitization version.
 * @param policyMode        the privacy policy mode.
 * @param sanitizationStatus privacy sanitization status label.
 * @param evidenceId        stable evidence identifier.
 * @param sessionId         stable session identifier.
 * @param feedItemId        stable feed item identifier.
 * @param fingerprint       deterministic content-free fingerprint.
 */
data class PreprocessedInput(
    val input: ModelInput,
    val configVersion: String,
    val configKey: String,
    val sourceWidth: Int,
    val sourceHeight: Int,
    val capturedOrientation: FrameOrientation,
    val privacyVersion: String,
    val policyMode: String,
    val sanitizationStatus: String,
    val evidenceId: String?,
    val sessionId: String?,
    val feedItemId: String?,
    val fingerprint: String
) {
    val tensorSize: Int get() = input.tensorSize

    /*
     * Equality includes tensor content + full provenance, so a
     * determinism test can assert two runs are identical without
     * aliasing.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PreprocessedInput) return false
        return fingerprint == other.fingerprint &&
            input == other.input &&
            configVersion == other.configVersion &&
            configKey == other.configKey &&
            sourceWidth == other.sourceWidth &&
            sourceHeight == other.sourceHeight &&
            capturedOrientation == other.capturedOrientation &&
            privacyVersion == other.privacyVersion &&
            policyMode == other.policyMode &&
            sanitizationStatus == other.sanitizationStatus &&
            evidenceId == other.evidenceId &&
            sessionId == other.sessionId &&
            feedItemId == other.feedItemId
    }

    override fun hashCode(): Int {
        var result = fingerprint.hashCode()
        result = 31 * result + input.hashCode()
        result = 31 * result + configVersion.hashCode()
        result = 31 * result + sourceWidth
        result = 31 * result + sourceHeight
        result = 31 * result + capturedOrientation.hashCode()
        result = 31 * result + privacyVersion.hashCode()
        result = 31 * result + (evidenceId?.hashCode() ?: 0)
        return result
    }
}

/**
 * Deterministic, content-free fingerprint of a preprocessing run.
 *
 * Built only from stable identifiers and version labels - never
 * from pixel content, hashCode(), or wall-clock time. Identical
 * inputs/config/privacy ALWAYS produce identical fingerprints.
 */
object PreprocessingFingerprint {

    fun of(
        config: PreprocessingConfig,
        sourceWidth: Int,
        sourceHeight: Int,
        capturedOrientation: FrameOrientation,
        privacyVersion: String,
        policyMode: String,
        sanitizationStatus: String,
        evidenceId: String?,
        sessionId: String?,
        feedItemId: String?
    ): String {
        val parts = listOf(
            "cfg=${config.canonicalKey}",
            "src=${sourceWidth}x$sourceHeight",
            "orient=${capturedOrientation.label}",
            "priv=${privacyVersion}:${policyMode}:${sanitizationStatus}",
            "ev=${evidenceId ?: "null"}",
            "ses=${sessionId ?: "null"}",
            "item=${feedItemId ?: "null"}"
        )
        val canonical = parts.joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }
}