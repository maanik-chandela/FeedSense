package com.example.feedsense.analysis.ml.preprocess.golden

import com.example.feedsense.analysis.ml.preprocess.*
import com.example.feedsense.analysis.privacy.PrivacySanitizationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-15-4 (§2, §4, §6, §10, §11, §13).
 *
 * Full-corpus golden regression: every fixture in the corpus is
 * run through the REAL preprocessing implementation and compared
 * against its golden expectations via the GoldenComparator.
 *
 * The comparison covers:
 *   - output shape (width/height/channels)
 *   - tensor datatype and layout
 *   - expected failure codes for negative/privacy fixtures
 *   - sanitization status stamping
 *   - deterministic hashes across repeated runs
 *
 * Deliberately NOT auto-updating: a mismatch here FAILS the test.
 */
class GoldenCorpusRegressionTest {

    @Test
    fun `every success fixture matches its golden shape and layout`() {
        val failures = mutableListOf<String>()
        for (fixture in GoldenFixtureDefinitions.ALL_FIXTURES) {
            if (fixture.expectation.expectedFailureCode != null) {
                continue // handled by the failure regression below
            }
            val result = runFixture(fixture)
            if (result is PreprocessResult.Failure) {
                failures += "fixture ${fixture.fixtureId}: expected success but got " +
                    "${result.code} - ${result.message}"
                continue
            }
            val comparison = GoldenComparator.compare(fixture, result)
            if (comparison is GoldenComparisonResult.Mismatch) {
                failures += comparison.diagnostics.summary()
            }
        }
        assertTrue(
            "golden shape regressions:\n${GoldenTestReporter.renderFailures(
                failures.map { GoldenTestOutcome("corpus", false, note = it) })
            }",
            failures.isEmpty()
        )
    }

    @Test
    fun `every failure fixture matches its expected failure code`() {
        val failureFixtures = GoldenFixtureDefinitions.ALL_FIXTURES.filter {
            it.expectation.expectedFailureCode != null
        }
        assertTrue(
            "corpus must contain failure fixtures",
            failureFixtures.isNotEmpty()
        )

        val failures = mutableListOf<String>()
        for (fixture in failureFixtures) {
            val result = if (fixture.category == FixtureCategory.PRIVACY) {
                // Privacy-rejected fixtures cannot be constructed via
                // the approve() factory; we verify the rejection at the
                // evidence layer instead.
                verifyPrivateRejection(fixture)
            } else {
                runFixture(fixture)
            }
            val comparison = GoldenComparator.compare(fixture, result)
            if (comparison is GoldenComparisonResult.Mismatch) {
                failures += comparison.diagnostics.summary()
            }
        }
        assertTrue(
            "golden failure regressions:\n${
                GoldenTestReporter.renderFailures(
                    failures.map { GoldenTestOutcome("corpus", false, note = it) }
                )
            }",
            failures.isEmpty()
        )
    }

    @Test
    fun `golden comparator never reports false success on shape drift`() {
        // Prove the comparator detects when the actual output shape
        // differs from the golden expectation.
        val fixture = GoldenFixtureDefinitions.ALL_BLACK_4x4
        val wrongExpectation = fixture.expectation.copy(
            expectedWidth = 8, expectedHeight = 8
        )
        val wrongFixture = fixture.copy(expectation = wrongExpectation)
        val result = runFixture(fixture)
        val comparison = GoldenComparator.compare(wrongFixture, result)
        assertTrue(
            "comparator must detect shape drift",
            comparison is GoldenComparisonResult.Mismatch
        )
    }

    @Test
    fun `golden comparator detects failure code drift`() {
        val fixture = GoldenFixtureDefinitions.ALL_BLACK_4x4
        val wrongFixture = fixture.copy(
            expectation = fixture.expectation.copy(
                expectedFailureCode = PreprocessFailureCode.INVALID_INPUT
            )
        )
        val result = runFixture(fixture)
        assertTrue(result is PreprocessResult.Success)
        val comparison = GoldenComparator.compare(wrongFixture, result)
        assertTrue(
            "comparator must detect failure-code drift on a success",
            comparison is GoldenComparisonResult.Mismatch
        )
    }

    @Test
    fun `full corpus tensor hashes are stable across two full runs`() {
        val run1 = mutableMapOf<String, String>()
        val run2 = mutableMapOf<String, String>()
        for (fixture in GoldenFixtureDefinitions.ALL_FIXTURES) {
            if (fixture.expectation.expectedFailureCode != null) continue
            val a = runFixture(fixture)
            val b = runFixture(fixture)
            if (a is PreprocessResult.Success && b is PreprocessResult.Success) {
                run1[fixture.fixtureId] = GoldenHasher.hashTensor(a.output.input)
                run2[fixture.fixtureId] = GoldenHasher.hashTensor(b.output.input)
            }
        }
        for (id in run1.keys) {
            assertTrue(
                "fixture $id: tensor hash must be stable across runs (${run1[id]} vs ${run2[id]})",
                run1[id] == run2[id]
            )
        }
    }

    @Test
    fun `cross-run fingerprint stability for the whole corpus`() {
        for (fixture in GoldenFixtureDefinitions.ALL_FIXTURES) {
            if (fixture.expectation.expectedFailureCode != null) continue
            val a = runFixture(fixture)
            val b = runFixture(fixture)
            if (a is PreprocessResult.Success && b is PreprocessResult.Success) {
                val msg = "fixture ${fixture.fixtureId}: fingerprint drift"
                assertTrue(msg, a.output.fingerprint == b.output.fingerprint)
            }
        }
    }

    @Test
    fun `every pinned golden hash resolves in the registry`() {
        val registryIds = GoldenHashRegistry.PINNED_TENSOR_HASH.keys.toSet()
        val corpusIds = GoldenFixtureDefinitions.ALL_FIXTURES.map { it.fixtureId }.toSet()

        // Every registry entry must map to a real fixture.
        val orphanEntries = registryIds - corpusIds
        assertTrue(
            "registry pins hashes for unknown fixtures: $orphanEntries",
            orphanEntries.isEmpty()
        )

        // Every fixture that pins a hash must have an entry (and a
        // fingerprint where a tensor hash exists).
        for (fixture in GoldenFixtureDefinitions.ALL_FIXTURES) {
            val hash = fixture.expectation.expectedTensorHash
            val fingerprint = fixture.expectation.expectedFingerprint
            if (hash != null) {
                assertEquals(
                    "fixture ${fixture.fixtureId} must pin the same hash in the registry",
                    GoldenHashRegistry.PINNED_TENSOR_HASH[fixture.fixtureId],
                    hash
                )
                assertNotNull(
                    "fixture ${fixture.fixtureId} pins a hash but has no registry fingerprint",
                    GoldenHashRegistry.PINNED_FINGERPRINT[fixture.fixtureId]
                )
                if (fingerprint != null) {
                    assertEquals(
                        "fixture ${fixture.fixtureId} must pin the same fingerprint in the registry",
                        GoldenHashRegistry.PINNED_FINGERPRINT[fixture.fixtureId],
                        fingerprint
                    )
                }
            }
        }

        // Pinned hashes must be valid SHA-256 hex digests (64 chars).
        for ((id, hash) in GoldenHashRegistry.PINNED_TENSOR_HASH) {
            assertTrue(
                "registry hash for $id is not a 64-char hex digest: $hash",
                hash.matches(Regex("[0-9a-f]{64}"))
            )
        }
        for ((id, fingerprint) in GoldenHashRegistry.PINNED_FINGERPRINT) {
            assertTrue(
                "registry fingerprint for $id is not a 64-char hex digest: $fingerprint",
                fingerprint.matches(Regex("[0-9a-f]{64}"))
            )
        }
    }

    // -------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------

    private fun runFixture(
        fixture: GoldenFixture,
        status: PrivacySanitizationStatus = sanitizationStatusFor(fixture)
    ): PreprocessResult {
        val config = fixture.configOverride ?: simpleConfig()
        val preprocessor = DeterministicPreprocessor(config)
        val frame = GoldenFixtureLoader.loadFrame(fixture)
        val evidence = PreprocessingEvidenceFactory.approve(
            frame = frame,
            sanitizationStatus = status
        )
        return preprocessor.preprocess(evidence, fixture.sourceOrientation)
    }

    private fun sanitizationStatusFor(fixture: GoldenFixture): PrivacySanitizationStatus {
        return when (fixture.expectation.expectedSanitizationStatus) {
            "SANITIZED" -> PrivacySanitizationStatus.SANITIZED
            "PARTIALLY_SANITIZED" -> PrivacySanitizationStatus.PARTIALLY_SANITIZED
            "NOT_REQUIRED" -> PrivacySanitizationStatus.NOT_REQUIRED
            else -> PrivacySanitizationStatus.SANITIZED
        }
    }

    /**
     * Privacy-rejected fixtures fail at EVIDENCE CONSTRUCTION TIME
     * (that is the intended, deterministic behavior). We verify the
     * rejection has the expected code by simulating the pipeline's
     * response to an unconstructable-but-unsafe evidence: the
     * pipeline would return PRIVACY_REJECTED.
     */
    private fun verifyPrivateRejection(fixture: GoldenFixture): PreprocessResult {
        val constructionRejected = evidenceConstructionRejects(fixture)
        return if (constructionRejected) {
            PreprocessResult.Failure(
                PreprocessFailureCode.PRIVACY_REJECTED,
                "evidence construction rejected unsafe privacy status " +
                    "(fixture ${fixture.fixtureId})"
            )
        } else {
            PreprocessResult.Failure(
                PreprocessFailureCode.PREPROCESSING_FAILURE,
                "fixture ${fixture.fixtureId}: unsafe evidence was unexpectedly accepted"
            )
        }
    }

    private fun evidenceConstructionRejects(fixture: GoldenFixture): Boolean {
        return try {
            PreprocessingEvidenceFactory.approve(
                frame = GoldenFixtureLoader.loadFrame(fixture),
                sanitizationStatus = unsafeStatusFor(fixture)
            )
            false
        } catch (expected: IllegalArgumentException) {
            true
        }
    }

    private fun unsafeStatusFor(fixture: GoldenFixture): PrivacySanitizationStatus {
        return when {
            fixture.fixtureId.contains("failed") ->
                PrivacySanitizationStatus.SANITIZATION_FAILED
            fixture.fixtureId.contains("unavailable") ->
                PrivacySanitizationStatus.SANITIZATION_UNAVAILABLE
            else -> PrivacySanitizationStatus.UNKNOWN
        }
    }

    private fun simpleConfig(): PreprocessingConfig =
        PreprocessingConfig(
            version = PreprocessingVersion.V2,
            inputWidth = 4,
            inputHeight = 4,
            resizePolicy = ResizePolicy.NEAREST_NEIGHBOR,
            cropPolicy = AspectRatioPolicy.RESIZE,
            paddingPolicy = PaddingPolicy.NO_PADDING,
            interpolation = Interpolation.NEAREST,
            orientationPolicy = OrientationPolicy.NORMALIZE_TO_0,
            colorFormat = ColorFormat.RGB,
            channelOrder = PreprocessChannelOrder.RGB,
            alphaPolicy = AlphaPolicy.DISCARD,
            tensorType = PreprocessingTensorType.FLOAT32,
            scale = PreprocessingConfig.DEFAULT_SCALE,
            zeroPoint = 0.0,
            tensorLayout = PreprocessingTensorLayout.NHWC,
            batchSize = BatchHandling.BATCH_1
        )
}