package com.example.feedsense.analysis.privacy

/*
 * Milestone 8B-10.
 *
 * Representative frame resolution.
 *
 * The existing models store a single representativeFramePath
 * (originally the RAW frame). 8B-10 adds an explicit
 * resolution that prefers the SANITIZED frame for analysis
 * while keeping raw frames available for on-device review.
 *
 * Deciding which file a consumer should read:
 *
 *   RAW_PATH          - written by the capture pipeline.
 *   SANITIZED_PATH    - written by EvidenceSanitizer.
 *   STORAGE STATE     - policy metadata.
 */
data class RepresentativeFrame(
    val frameId: String,
    val rawFilePath: String? = null,
    val sanitizedFilePath: String? = null,
    val storageState: FrameStorageState =
        FrameStorageState.RAW
) {

    /*
     * The path analysis should read: sanitized when present,
     * otherwise raw (only when raw is still a legal source).
     */
    fun analysisViewPath(): String? {
        return when {
            sanitizedFilePath != null -> sanitizedFilePath
            storageState == FrameStorageState.SANITIZED_ONLY ->
                null
            else -> rawFilePath
        }
    }

    /*
     * The path human review should read. Raw is preferred for
     * review because it is lossless; sanitized only when raw
     * is unavailable. Raw is NEVER handed to exports via this
     * resolver (see PrivacyExportPolicy).
     */
    fun reviewViewPath(): String? {
        return rawFilePath ?: sanitizedFilePath
    }
}

/*
 * Resolves a representative frame given its raw/sanitized
 * files and the retention policy. Always returns the
 * institutionally correct view; never throws.
 */
object RepresentativeFrameResolver {

    /*
     * Analysis view: sanitized preferred. When only raw
     * exists the caller passes null for sanitizedPath and
     * raw is used ONLY if the policy still retains raw
     * frames (default true).
     */
    fun forAnalysis(
        rawPath: String?,
        sanitizedPath: String?,
        policy: PrivacyPolicy = defaultPrivacyPolicy()
    ): RepresentativeFrame {
        val storage = when {
            sanitizedPath != null -> FrameStorageState.SANITIZED
            policy.retainRawFrames -> FrameStorageState.RAW
            else -> FrameStorageState.SANITIZED_ONLY
        }
        return RepresentativeFrame(
            frameId = "",
            rawFilePath = rawPath,
            sanitizedFilePath = sanitizedPath,
            storageState = storage
        )
    }
}