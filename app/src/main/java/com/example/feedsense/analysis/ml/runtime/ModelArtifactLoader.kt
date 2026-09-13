package com.example.feedsense.analysis.ml.runtime

import com.example.feedsense.analysis.ml.repro.ArtifactAvailability
import com.example.feedsense.analysis.ml.repro.ArtifactSha256
import com.example.feedsense.analysis.ml.repro.ReproArtifactIdentity

// --------------------------------
// MODEL ARTIFACT LOADER (8B-15-5)
// --------------------------------
//
// The model-loading abstraction that:
//
//   1. Locates the expected local artifact
//   2. Verifies artifact identity
//   3. Verifies checksum when defined
//   4. Loads the model using the selected runtime
//   5. Exposes model metadata
//   6. Fails deterministically when artifact is
//      missing/corrupt/incompatible
//
// Design rules:
//   - never downloads models from the internet
//   - never adds cloud fallback
//   - never silently downloads replacement artifacts
//   - never fabricates successful loading
//   - fails with structured RuntimeFailure

/*
 * Outcome of a model loading attempt.
 */
sealed class ModelLoadAttemptResult {

    /**
     * The model was loaded successfully. Contains the runtime
     * handle needed for inference.
     */
    data class Success(
        val artifactIdentity: ReproArtifactIdentity,
        val loadDurationMs: Long,
        val runtimeHandle: Any?
    ) : ModelLoadAttemptResult()

    /**
     * The model could not be loaded. Contains a structured
     * failure description.
     */
    data class Failure(
        val failure: RuntimeFailure,
        val loadDurationMs: Long
    ) : ModelLoadAttemptResult()

    val succeeded: Boolean get() = this is Success
}

/**
 * Loads and verifies a model artifact.
 *
 * The loader is responsible for:
 *   - locating the artifact (by path or registry)
 *   - verifying the artifact identity matches expectations
 *   - verifying the SHA-256 checksum when defined
 *   - delegating actual loading to the runtime
 *   - exposing load metadata
 *
 * The loader does NOT:
 *   - download models
 *   - perform inference
 *   - manage preprocessing
 *   - interpret output
 */
interface ModelArtifactLoader {

    /**
     * Attempts to load the model artifact described by the
     * given identity.
     *
     * @param artifact the expected artifact identity
     * @return success with a runtime handle, or failure with
     *   a structured error
     */
    fun load(artifact: ReproArtifactIdentity): ModelLoadAttemptResult

    /**
     * Whether the loader currently holds a loaded artifact.
     */
    fun isLoaded(): Boolean

    /**
     * Returns the identity of the currently loaded artifact,
     * or null if nothing is loaded.
     */
    fun loadedArtifact(): ReproArtifactIdentity?

    /**
     * Releases the loaded artifact and any associated resources.
     */
    fun release()
}

/**
 * A test-only artifact loader for JVM tests. Simulates model
 * loading behavior without any real runtime dependency.
 *
 * TEST_ONLY - not intended for production use.
 */
class TestArtifactLoader(
    private val behavior: TestLoaderBehavior = TestLoaderBehavior.SUCCESS
) : ModelArtifactLoader {

    private var loadedArtifact: ReproArtifactIdentity? = null
    private var loadCount: Int = 0

    override fun load(artifact: ReproArtifactIdentity): ModelLoadAttemptResult {
        val startMs = System.currentTimeMillis()
        loadCount++

        return when (behavior) {
            TestLoaderBehavior.SUCCESS -> {
                loadedArtifact = artifact
                val duration = System.currentTimeMillis() - startMs
                ModelLoadAttemptResult.Success(
                    artifactIdentity = artifact,
                    loadDurationMs = duration,
                    runtimeHandle = TestRuntimeHandle(artifact)
                )
            }
            TestLoaderBehavior.MISSING -> {
                val duration = System.currentTimeMillis() - startMs
                ModelLoadAttemptResult.Failure(
                    failure = RuntimeFailureFactory.artifactMissing(
                        artifact.fileName ?: artifact.artifactId
                    ),
                    loadDurationMs = duration
                )
            }
            TestLoaderBehavior.CORRUPT -> {
                val duration = System.currentTimeMillis() - startMs
                ModelLoadAttemptResult.Failure(
                    failure = RuntimeFailureFactory.artifactCorrupt(
                        artifact.fileName ?: artifact.artifactId,
                        "simulated corruption"
                    ),
                    loadDurationMs = duration
                )
            }
            TestLoaderBehavior.IDENTITY_MISMATCH -> {
                val duration = System.currentTimeMillis() - startMs
                ModelLoadAttemptResult.Failure(
                    failure = RuntimeFailureFactory.artifactIdentityMismatch(
                        expected = artifact.key,
                        actual = "different-artifact-key"
                    ),
                    loadDurationMs = duration
                )
            }
            TestLoaderBehavior.RUNTIME_UNAVAILABLE -> {
                val duration = System.currentTimeMillis() - startMs
                ModelLoadAttemptResult.Failure(
                    failure = RuntimeFailureFactory.runtimeUnavailable("test-runtime"),
                    loadDurationMs = duration
                )
            }
        }
    }

    override fun isLoaded(): Boolean = loadedArtifact != null

    override fun loadedArtifact(): ReproArtifactIdentity? = loadedArtifact

    override fun release() {
        loadedArtifact = null
    }

    /**
     * Returns the number of load() calls made.
     */
    fun loadCallCount(): Int = loadCount
}

/*
 * Configurable behavior for the test artifact loader.
 */
enum class TestLoaderBehavior {
    SUCCESS,
    MISSING,
    CORRUPT,
    IDENTITY_MISMATCH,
    RUNTIME_UNAVAILABLE
}

/*
 * A test-only runtime handle produced by loading. Contains the
 * artifact identity but no actual native resources.
 */
data class TestRuntimeHandle(
    val artifact: ReproArtifactIdentity
)
