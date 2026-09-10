package com.example.feedsense.analysis.ml.repro

import java.security.MessageDigest

// --------------------------------
// ARTIFACT IDENTITY + SHA-256 (8B-15-2)
// --------------------------------
//
// The identity of an exact model ARTIFACT (a file / set of
// bytes), distinct from MODEL identity.
//
// The SHA-256 is computed over the exact artifact bytes and is
// the content identity. Two artifacts that claim the same model
// identity are still distinguishable by their checksums.
//
// Honest availability states:
//
//   - ArtifactAvailability.AVAILABLE   -> a real artifact hash is
//     recorded and verified against actual bytes.
//   - ArtifactAvailability.PENDING     -> the artifact is expected
//     but not yet physically present. NO checksum may be
//     fabricated; artifactId is a declared identifier only.
//   - ArtifactAvailability.NOT_AVAILABLE -> no artifact is present
//     or expected right now.
//
// Never invent a checksum. If the artifact is not available,
// represent PENDING/NOT_AVAILABLE rather than a fabricated hash.

/*
 * Whether the exact artifact is physically present and verified.
 */
enum class ArtifactAvailability(override val label: String) : ReprLabeled {
    AVAILABLE("AVAILABLE"),
    PENDING("PENDING"),
    NOT_AVAILABLE("NOT_AVAILABLE")
}

/*
 * Provenance / validation status of an artifact.
 */
enum class ArtifactValidationStatus(override val label: String) : ReprLabeled {
    NOT_CHECKED("NOT_CHECKED"),
    HASH_MATCHES("HASH_MATCHES"),
    HASH_MISMATCH("HASH_MISMATCH"),
    NOT_APPLICABLE("NOT_APPLICABLE")
}

/*
 * The artifact format, reusing the 8B-14 vocabulary where it
 * applies. Additional formats may be introduced here without
 * touching runtime code.
 */
enum class ReproArtifactFormat(override val label: String) : ReprLabeled {
    TFLITE("TFLITE"),
    ONNX("ONNX"),
    EXECUTORCH("EXECUTORCH"),
    OTHER("OTHER"),
    UNKNOWN("UNKNOWN")
}

/*
 * Immutable identity of one artifact.
 *
 * artifactId is a declared identifier; sha256 is the verification
 * that makes that identifier meaningful. When the artifact is not
 * physically available, sha256 MUST be null and availability must
 * be PENDING / NOT_AVAILABLE.
 */
data class ReproArtifactIdentity(
    val artifactId: String,
    val fileName: String? = null,
    val format: ReproArtifactFormat,
    val byteSize: Long? = null,
    val sha256: String? = null,
    val sourceReference: String? = null,
    val modelId: String,
    val artifactVersion: String,
    val acquisitionTimestamp: String? = null,
    val availability: ArtifactAvailability,
    val validationStatus: ArtifactValidationStatus =
        ArtifactValidationStatus.NOT_CHECKED
) {

    init {
        require(artifactId.isNotBlank()) { "artifactId must be non-blank" }
        require(format != ReproArtifactFormat.UNKNOWN || availability == ArtifactAvailability.PENDING
            || availability == ArtifactAvailability.NOT_AVAILABLE) {
            "unknown artifact format requires an unavailable state"
        }
        require(modelId.isNotBlank()) { "modelId must be non-blank" }
        require(artifactVersion.isNotBlank()) { "artifactVersion must be non-blank" }
        byteSize?.let { require(it >= 0) { "byteSize must be >= 0" } }
        sha256?.let { s ->
            require(isSha256Hex(s)) { "sha256 must be 64 lowercase hex chars" }
        }
        require(!(availability != ArtifactAvailability.AVAILABLE && sha256 != null)) {
            "sha256 may only be set when an artifact is actually AVAILABLE"
        }
        require(!(availability == ArtifactAvailability.AVAILABLE && sha256 == null)) {
            "an AVAILABLE artifact must carry a verified sha256"
        }
        require(validationStatus != ArtifactValidationStatus.HASH_MATCHES || sha256 != null) {
            "HASH_MATCHES validation requires a recorded sha256"
        }
        require(validationStatus != ArtifactValidationStatus.HASH_MISMATCH || sha256 != null) {
            "HASH_MISMATCH validation requires a recorded sha256"
        }
    }

    /*
     * Stable content key: artifactId + (sha256 when available).
     * Two artifacts with the same id but different bytes diverge
     * here.
     */
    val key: String
        get() = "$artifactId:${sha256 ?: "unavailable"}"

    companion object {
        fun isSha256Hex(value: String): Boolean =
            value.length == 64 && value.all {
                it in '0'..'9' || it in 'a'..'f'
            }
    }
}

/*
 * SHA-256 support over model artifact bytes.
 *
 *   - hash(bytes)      : SHA-256 hex of an artifact byte array.
 *   - verify(bytes,hex): constant-faithful comparison of a
 *     recorded hex vs a freshly computed one.
 *
 * Rules honoured:
 *   - same bytes -> same hash
 *   - different bytes -> different hash (overwhelmingly)
 *   - the hash is computed over EXACT artifact bytes; compression
 *     or container status is caller's responsibility and should be
 *     recorded in the provenance (isCompressed / isHashedBefore
 *     extraction).
 */
object ArtifactSha256 {

    const val ALGORITHM = "SHA-256"

    fun hash(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance(ALGORITHM).digest(bytes)
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    fun verify(bytes: ByteArray, recordedHex: String): Boolean {
        val computed = hash(bytes)
        return constantTimeEquals(computed, recordedHex)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}
