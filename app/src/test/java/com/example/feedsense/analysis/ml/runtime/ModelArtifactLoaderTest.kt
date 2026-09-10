package com.example.feedsense.analysis.ml.runtime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-5.
 *
 * Model artifact loader: loading, verification, and failure
 * handling.
 */
class ModelArtifactLoaderTest {

    // --------------------------------
    // SUCCESS PATH
    // --------------------------------

    @Test
    fun `successful load returns handle`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.SUCCESS)
        val result = loader.load(RuntimeTestFixtures.TEST_ARTIFACT_AVAILABLE)
        assertTrue(result.succeeded)
        val success = result as ModelLoadAttemptResult.Success
        assertEquals("mobilenetv4-conv-s-test", success.artifactIdentity.artifactId)
        assertNotNull(success.runtimeHandle)
        assertTrue(loader.isLoaded())
        assertNotNull(loader.loadedArtifact())
    }

    @Test
    fun `loaded artifact matches input`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.SUCCESS)
        loader.load(RuntimeTestFixtures.TEST_ARTIFACT_AVAILABLE)
        val loaded = loader.loadedArtifact()
        assertNotNull(loaded)
        assertEquals(
            RuntimeTestFixtures.TEST_ARTIFACT_AVAILABLE.artifactId,
            loaded!!.artifactId
        )
    }

    // --------------------------------
    // FAILURE PATHS
    // --------------------------------

    @Test
    fun `missing artifact fails`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.MISSING)
        val result = loader.load(RuntimeTestFixtures.TEST_ARTIFACT_AVAILABLE)
        assertFalse(result.succeeded)
        val failure = result as ModelLoadAttemptResult.Failure
        assertEquals(RuntimeFailureCode.ARTIFACT_MISSING, failure.failure.code)
        assertFalse(loader.isLoaded())
    }

    @Test
    fun `corrupt artifact fails`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.CORRUPT)
        val result = loader.load(RuntimeTestFixtures.TEST_ARTIFACT_AVAILABLE)
        assertFalse(result.succeeded)
        val failure = result as ModelLoadAttemptResult.Failure
        assertEquals(RuntimeFailureCode.ARTIFACT_CORRUPT, failure.failure.code)
    }

    @Test
    fun `identity mismatch fails`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.IDENTITY_MISMATCH)
        val result = loader.load(RuntimeTestFixtures.TEST_ARTIFACT_AVAILABLE)
        assertFalse(result.succeeded)
        val failure = result as ModelLoadAttemptResult.Failure
        assertEquals(RuntimeFailureCode.ARTIFACT_IDENTITY_MISMATCH, failure.failure.code)
    }

    @Test
    fun `runtime unavailable fails`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.RUNTIME_UNAVAILABLE)
        val result = loader.load(RuntimeTestFixtures.TEST_ARTIFACT_AVAILABLE)
        assertFalse(result.succeeded)
        val failure = result as ModelLoadAttemptResult.Failure
        assertEquals(RuntimeFailureCode.RUNTIME_UNAVAILABLE, failure.failure.code)
    }

    // --------------------------------
    // LIFECYCLE
    // --------------------------------

    @Test
    fun `release clears loaded artifact`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.SUCCESS)
        loader.load(RuntimeTestFixtures.TEST_ARTIFACT_AVAILABLE)
        assertTrue(loader.isLoaded())
        loader.release()
        assertFalse(loader.isLoaded())
        assertNull(loader.loadedArtifact())
    }

    @Test
    fun `release is idempotent`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.SUCCESS)
        loader.release()
        assertFalse(loader.isLoaded())
        loader.release()
        assertFalse(loader.isLoaded())
    }

    @Test
    fun `load count is tracked`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.SUCCESS)
        assertEquals(0, loader.loadCallCount())
        loader.load(RuntimeTestFixtures.TEST_ARTIFACT_AVAILABLE)
        assertEquals(1, loader.loadCallCount())
        loader.load(RuntimeTestFixtures.TEST_ARTIFACT_AVAILABLE)
        assertEquals(2, loader.loadCallCount())
    }

    @Test
    fun `failed load does not set loaded state`() {
        val loader = TestArtifactLoader(TestLoaderBehavior.MISSING)
        loader.load(RuntimeTestFixtures.TEST_ARTIFACT_AVAILABLE)
        assertFalse(loader.isLoaded())
        assertNull(loader.loadedArtifact())
    }
}
