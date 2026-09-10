package com.example.feedsense.analysis.ml.runtime

import com.example.feedsense.analysis.ml.preprocess.DeterministicPreprocessor
import com.example.feedsense.analysis.ml.preprocess.FrameOrientation
import com.example.feedsense.analysis.ml.preprocess.PreprocessResult
import com.example.feedsense.analysis.ml.preprocess.PreprocessingEvidenceFactory
import com.example.feedsense.analysis.ml.preprocess.PreprocessingVersion
import com.example.feedsense.analysis.ml.preprocess.PreprocessingEvidence
import com.example.feedsense.analysis.ml.preprocess.PreprocessingConfig
import com.example.feedsense.analysis.ml.preprocess.golden.GoldenComparator
import com.example.feedsense.analysis.ml.preprocess.golden.GoldenComparisonResult
import com.example.feedsense.analysis.ml.preprocess.golden.GoldenFixtureDefinitions
import com.example.feedsense.analysis.ml.preprocess.golden.GoldenFixtureLoader
import com.example.feedsense.analysis.privacy.PrivacySanitizationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-5.
 *
 * Golden integration test: exercises the complete boundary:
 *
 *   golden source image
 *     → 8B-15-3 preprocessing
 *     → 8B-15-5 runtime adapter
 *     → test model
 *     → validated raw output
 *
 * Does NOT bypass preprocessing. Establishes the complete
 * boundary without integrating into production FeedSense.
 */
class GoldenAdapterIntegrationTest {

    private fun createAdapter(): ModelRuntimeAdapter {
        val loader = TestArtifactLoader(TestLoaderBehavior.SUCCESS)
        val backend = TestInferenceBackend(
            TestBackendBehavior.FixedOutput(
                floatArrayOf(0.45f, 0.35f, 0.20f),
                listOf(1, 3)
            )
        )
        return ModelRuntimeAdapter(
            config = RuntimeTestFixtures.adapterConfig(),
            artifactLoader = loader,
            backend = backend
        )
    }

    private fun runGoldenFixture(
        fixtureId: String,
        adapter: ModelRuntimeAdapter
    ): GoldenFixtureResult {
        val fixture = GoldenFixtureDefinitions.ALL_FIXTURES.find {
            it.fixtureId == fixtureId
        } ?: throw IllegalArgumentException("fixture not found: $fixtureId")

        // 1. Generate the golden synthetic frame
        val frame = GoldenFixtureLoader.loadFrame(fixture)

        // 2. Create preprocessing evidence (privacy-approved)
        val evidence = PreprocessingEvidenceFactory.approve(
            frame = frame,
            sanitizationStatus = PrivacySanitizationStatus.SANITIZED,
            evidenceId = "golden-$fixtureId",
            sessionId = "golden-session",
            feedItemId = "golden-item"
        )

        // 3. Run preprocessing
        val config = fixture.configOverride ?: GoldenFixtureDefinitions.allFixturesConfig()
        val preprocessor = DeterministicPreprocessor(config)
        val preprocessResult = preprocessor.preprocess(
            evidence,
            capturedOrientation = fixture.sourceOrientation
        )

        // 4. Check preprocessing against golden expectations
        val goldenComparison = GoldenComparator.compare(fixture, preprocessResult)

        // 5. If preprocessing succeeded, run through adapter
        var adapterOutput: AdapterRawModelOutput? = null
        if (preprocessResult is PreprocessResult.Success) {
            adapterOutput = adapter.infer(preprocessResult.output)
        }

        return GoldenFixtureResult(
            fixtureId = fixtureId,
            preprocessResult = preprocessResult,
            goldenComparison = goldenComparison,
            adapterOutput = adapterOutput
        )
    }

    @Test
    fun `basic all-black fixture through full pipeline`() {
        val adapter = createAdapter()
        adapter.initialize()

        val result = runGoldenFixture("basic-all-black-4x4", adapter)

        // Preprocessing should succeed (it's a valid fixture)
        assertTrue(
            "preprocessing should succeed for basic-all-black-4x4",
            result.preprocessResult is PreprocessResult.Success
        )

        // Golden comparison should match
        assertTrue(
            "golden comparison should match for basic-all-black-4x4: " +
                result.diagnostics(),
            result.goldenComparison is GoldenComparisonResult.Match
        )

        // Adapter should succeed
        assertNotNull("adapter output should not be null", result.adapterOutput)
        assertTrue(
            "adapter inference should succeed",
            result.adapterOutput!!.succeeded
        )

        // Output should have expected structure
        assertEquals(1, result.adapterOutput!!.outputTensorCount)
        assertNotNull(result.adapterOutput!!.primaryOutput())
        assertEquals(3, result.adapterOutput!!.primaryOutput()!!.size)

        // Output values should be in valid range
        for (v in result.adapterOutput!!.primaryOutput()!!) {
            assertTrue("output value $v should be finite", v.isFinite())
        }
    }

    @Test
    fun `basic all-white fixture through full pipeline`() {
        val adapter = createAdapter()
        adapter.initialize()

        val result = runGoldenFixture("basic-all-white-4x4", adapter)

        assertTrue(
            "preprocessing should succeed",
            result.preprocessResult is PreprocessResult.Success
        )
        assertTrue(
            "golden comparison should match: ${result.diagnostics()}",
            result.goldenComparison is GoldenComparisonResult.Match
        )
        assertTrue(
            "adapter should succeed",
            result.adapterOutput!!.succeeded
        )
    }

    @Test
    fun `orientation 0 fixture through full pipeline`() {
        val adapter = createAdapter()
        adapter.initialize()

        val result = runGoldenFixture("orient-0-degrees", adapter)

        assertTrue(
            "preprocessing should succeed",
            result.preprocessResult is PreprocessResult.Success
        )
        assertTrue(
            "golden comparison should match: ${result.diagnostics()}",
            result.goldenComparison is GoldenComparisonResult.Match
        )
        assertTrue(
            "adapter should succeed",
            result.adapterOutput!!.succeeded
        )
    }

    @Test
    fun `normalization std-black fixture through full pipeline`() {
        val adapter = createAdapter()
        adapter.initialize()

        val result = runGoldenFixture("norm-std-black", adapter)

        assertTrue(
            "preprocessing should succeed",
            result.preprocessResult is PreprocessResult.Success
        )
        assertTrue(
            "golden comparison should match: ${result.diagnostics()}",
            result.goldenComparison is GoldenComparisonResult.Match
        )
        assertTrue(
            "adapter should succeed",
            result.adapterOutput!!.succeeded
        )
    }

    @Test
    fun `negative config-not-runnable fixture fails at preprocessing`() {
        val adapter = createAdapter()
        adapter.initialize()

        val result = runGoldenFixture("negative-config-not-runnable", adapter)

        assertTrue(
            "preprocessing should fail for negative fixture",
            result.preprocessResult is PreprocessResult.Failure
        )
        assertTrue(
            "golden comparison should match failure: ${result.diagnostics()}",
            result.goldenComparison is GoldenComparisonResult.Match
        )
        // Adapter should not be called when preprocessing fails
    }

    @Test
    fun `adapter stamps provenance from preprocessing`() {
        val adapter = createAdapter()
        adapter.initialize()

        val result = runGoldenFixture("basic-all-black-4x4", adapter)

        assertTrue(result.adapterOutput!!.succeeded)
        assertEquals(PreprocessingVersion.V2, result.adapterOutput!!.preprocessingVersion)
        assertEquals(
            RuntimeTestFixtures.TEST_ARTIFACT_AVAILABLE.artifactId,
            result.adapterOutput!!.artifactIdentity.artifactId
        )
    }

    @Test
    fun `multiple fixtures produce valid adapter output`() {
        val adapter = createAdapter()
        adapter.initialize()

        val fixtureIds = listOf(
            "basic-all-black-4x4",
            "basic-all-white-4x4",
            "basic-square-4x4",
            "orient-0-degrees",
            "norm-scale-only-black"
        )

        for (fixtureId in fixtureIds) {
            val result = runGoldenFixture(fixtureId, adapter)
            assertTrue(
                "adapter should succeed for $fixtureId",
                result.adapterOutput!!.succeeded
            )
            assertEquals(
                "output should have 1 tensor for $fixtureId",
                1,
                result.adapterOutput!!.outputTensorCount
            )
        }
    }

    // --------------------------------
    // Helper
    // --------------------------------

    data class GoldenFixtureResult(
        val fixtureId: String,
        val preprocessResult: PreprocessResult,
        val goldenComparison: GoldenComparisonResult,
        val adapterOutput: AdapterRawModelOutput?
    ) {
        fun diagnostics(): String = when (goldenComparison) {
            is GoldenComparisonResult.Match -> "MATCH"
            is GoldenComparisonResult.Mismatch ->
                goldenComparison.diagnostics.summary()
        }
    }
}
