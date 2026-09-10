package com.example.feedsense.analysis.ml.runtime.litert

import com.example.feedsense.analysis.ml.ModelInput
import com.example.feedsense.analysis.ml.ModelTensorType
import com.example.feedsense.analysis.ml.repro.ArtifactAvailability
import com.example.feedsense.analysis.ml.repro.ArtifactSha256
import com.example.feedsense.analysis.ml.repro.ReproArtifactFormat
import com.example.feedsense.analysis.ml.repro.ReproArtifactIdentity
import com.example.feedsense.analysis.ml.runtime.ModelArtifactLoader
import com.example.feedsense.analysis.ml.runtime.ModelLoadAttemptResult
import com.example.feedsense.analysis.ml.runtime.RuntimeFailure
import com.example.feedsense.analysis.ml.runtime.RuntimeFailureFactory
import java.io.File

// --------------------------------
// LITERT ARTIFACT LOADER (8B-15-9)
// --------------------------------
//
// Loads a real TFLite model artifact from local storage.
//
// The loader:
//   1. Locates the artifact file
//   2. Verifies file existence
//   3. Verifies SHA-256 checksum when defined
//   4. Returns the file path as the runtime handle
//   5. Does NOT download models from the internet
//
// The actual native TFLite Interpreter is NOT created here.
// That is the backend's responsibility. The loader only
// validates and provides the file path.

/**
 * A real artifact loader for TFLite model files.
 *
 * Locates and verifies a .tflite file from the filesystem.
 * Returns the file path as the runtime handle for the
 * LiteRtRuntimeBackend to load.
 */
class LiteRtArtifactLoader(
    /**
     * Base directory to search for model artifacts.
     * If null, artifactFileProvider must be set.
     */
    private val artifactDirectory: File? = null,

    /**
     * Optional custom provider for locating the artifact file.
     * Takes precedence over artifactDirectory.
     */
    private val artifactFileProvider: ((ReproArtifactIdentity) -> File?)? = null
) : ModelArtifactLoader {

    private var loadedArtifact: ReproArtifactIdentity? = null
    private var loadedFile: File? = null

    override fun load(artifact: ReproArtifactIdentity): ModelLoadAttemptResult {
        val startMs = System.currentTimeMillis()

        // 1. Check availability
        if (artifact.availability == ArtifactAvailability.NOT_AVAILABLE) {
            return ModelLoadAttemptResult.Failure(
                failure = RuntimeFailureFactory.artifactMissing(
                    artifact.fileName ?: artifact.artifactId
                ),
                loadDurationMs = System.currentTimeMillis() - startMs
            )
        }

        // 2. Locate the file
        val file = locateArtifact(artifact)
        if (file == null || !file.exists()) {
            return ModelLoadAttemptResult.Failure(
                failure = RuntimeFailureFactory.artifactMissing(
                    artifact.fileName ?: artifact.artifactId
                ),
                loadDurationMs = System.currentTimeMillis() - startMs
            )
        }

        if (!file.canRead()) {
            return ModelLoadAttemptResult.Failure(
                failure = RuntimeFailureFactory.artifactCorrupt(
                    file.absolutePath,
                    "file exists but is not readable"
                ),
                loadDurationMs = System.currentTimeMillis() - startMs
            )
        }

        // 3. Verify SHA-256 if defined
        if (artifact.sha256 != null) {
            val fileBytes = file.readBytes()
            val computedHash = ArtifactSha256.hash(fileBytes)
            if (computedHash != artifact.sha256) {
                return ModelLoadAttemptResult.Failure(
                    failure = RuntimeFailureFactory.artifactIdentityMismatch(
                        expected = artifact.key,
                        actual = "hash=$computedHash"
                    ),
                    loadDurationMs = System.currentTimeMillis() - startMs
                )
            }
        }

        // 4. Success
        loadedArtifact = artifact
        loadedFile = file
        val duration = System.currentTimeMillis() - startMs

        return ModelLoadAttemptResult.Success(
            artifactIdentity = artifact.copy(
                availability = ArtifactAvailability.AVAILABLE,
                byteSize = file.length()
            ),
            loadDurationMs = duration,
            runtimeHandle = file.absolutePath
        )
    }

    override fun isLoaded(): Boolean = loadedFile != null

    override fun loadedArtifact(): ReproArtifactIdentity? = loadedArtifact

    override fun release() {
        loadedArtifact = null
        loadedFile = null
    }

    private fun locateArtifact(artifact: ReproArtifactIdentity): File? {
        // Custom provider takes precedence
        artifactFileProvider?.let { provider ->
            return provider(artifact)
        }

        // Search in the artifact directory
        val dir = artifactDirectory ?: return null

        // Try the declared filename first
        artifact.fileName?.let { name ->
            val file = File(dir, name)
            if (file.exists()) return file
        }

        // Try the artifact ID with .tflite extension
        val fallback = File(dir, "${artifact.artifactId}.tflite")
        if (fallback.exists()) return fallback

        return null
    }
}
