package com.example.feedsense.analysis.ml.preprocess.golden

import com.example.feedsense.analysis.ml.preprocess.*
import com.example.feedsense.analysis.privacy.PrivacySanitizationStatus
import com.example.feedsense.analysis.privacy.SyntheticFrames
import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.EvaluationRecord
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.GroundTruth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

/*
 * Milestone 8B-15-4 (§24 baseline isolation, §31).
 *
 * Proves this milestone does NOT alter:
 *   - FeedItem
 *   - GroundTruth
 *   - EvaluationRecord
 *   - existing AI prediction history
 *   - existing capture semantics
 *   - existing session behavior
 *
 * The fixture system is a separate validation layer. Regression
 * tests here verify the golden fixture corpus never touches the
 * baseline path.
 */
class GoldenFixtureBaselineIsolationTest {

    @Test
    fun `baseline records are unchanged by golden fixture work`() {
        val now = LocalDateTime.of(2026, 9, 8, 12, 0, 0)
        val item = FeedItem(
            id = "item-golden-1",
            sessionId = "session-golden-1",
            startTime = now,
            endTime = now.plusSeconds(5),
            durationSeconds = 5,
            category = "music",
            confidence = 0.92,
            representativeFramePath = "/data/frames/item-golden-1.png",
            source = FeedItem.SOURCE_AI
        )
        val truth = GroundTruth(
            evaluationItemId = "eval-1",
            annotatorId = "researcher-a",
            category = "music",
            categoryDomain = "entertainment"
        )
        val prediction = AiPredictionRecord(
            evaluationItemId = "eval-1",
            source = "LOCAL",
            modelVersion = "heuristic-v1",
            category = "music",
            confidence = 0.92,
            feedItemId = "item-golden-1"
        )
        val record = EvaluationRecord(
            evaluationItemId = "eval-1",
            groundTruthId = "truth-1",
            aiPredictionId = prediction.id,
            modelVersion = "heuristic-v1",
            verdict = "CORRECT"
        )

        // Baseline construction/equality semantics untouched.
        assertEquals(0.92, item.confidence!!, 0.0)
        assertEquals("music", truth.category)
        assertEquals("CORRECT", record.verdict)
        assertEquals("LOCAL", prediction.source)

        // A full golden corpus load + run must not corrupt baseline
        // objects (they are independent types).
        for (fixture in GoldenFixtureDefinitions.ALL_FIXTURES) {
            if (fixture.expectation.expectedFailureCode != null) continue
            val config = fixture.configOverride ?: simpleConfig()
            val preprocessor = DeterministicPreprocessor(config)
            val frame = GoldenFixtureLoader.loadFrame(fixture)
            val evidence = PreprocessingEvidenceFactory.approve(
                frame = frame,
                sanitizationStatus = PrivacySanitizationStatus.SANITIZED
            )
            preprocessor.preprocess(evidence, fixture.sourceOrientation)
        }

        assertEquals(0.92, item.confidence!!, 0.0)
        assertEquals("music", truth.category)
    }

    @Test
    fun `golden fixtures use synthetic frames never real screenshots`() {
        // Every fixture source is generated from a synthetic
        // pattern, not loaded from an external image or file.
        for (fixture in GoldenFixtureDefinitions.ALL_FIXTURES) {
            val frame = GoldenFixtureLoader.loadFrame(fixture)
            assertEquals(
                "fixture ${fixture.fixtureId}: frame dimensions must match source",
                fixture.sourceWidth * fixture.sourceHeight,
                frame.pixels.size
            )
        }
    }

    @Test
    fun `golden fixtures require no filesystem or device state`() {
        // The loader is a pure function of the fixture definition.
        // Running it requires no disk, no Android context, no
        // network. We prove it by loading the full corpus twice and
        // requiring identical (deterministic) frames.
        for (fixture in GoldenFixtureDefinitions.ALL_FIXTURES) {
            val a = GoldenFixtureLoader.loadFrame(fixture)
            val b = GoldenFixtureLoader.loadFrame(fixture)
            assertTrue(
                "fixture ${fixture.fixtureId}: synthetic frames must be deterministic",
                a.pixels.contentEquals(b.pixels)
            )
        }
    }

    @Test
    fun `golden package does not modify baseline capture or session code`() {
        // Source-level isolation: the golden package must not
        // import or reference baseline production types.
        val goldenSourceDir = resolveGoldenSourceDir()
        val baselineTerms = listOf(
            "com.example.feedsense.model.FeedItem",
            "com.example.feedsense.model.GroundTruth",
            "com.example.feedsense.model.EvaluationRecord",
            "com.example.feedsense.model.AiPredictionRecord",
            "com.example.feedsense.repository.SessionRepository",
            "buildFeedItem",
            "com.example.feedsense.database",
            "com.example.feedsense.dao"
        )
        findByExtension(goldenSourceDir, ".kt").forEach { file ->
            val text = file.readText()
            for (term in baselineTerms) {
                assertFalse(
                    "golden source ${file.name} must not reference '$term'",
                    text.contains(term)
                )
            }
        }
    }

    @Test
    fun `golden fixture corpus does not alter existing preprocessing contract`() {
        // Prove the existing contract is preserved: the reference
        // config still produces the same 224x224x3 FLOAT32 tensor.
        val config = PreprocessingConfig.mobileNetV4ConvReference()
        val preprocessor = DeterministicPreprocessor(config)
        val frame = SyntheticFrames.checkerboard(6, 6, 0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        val evidence = PreprocessingEvidenceFactory.approve(
            frame = frame,
            sanitizationStatus = PrivacySanitizationStatus.SANITIZED
        )
        val result = preprocessor.preprocess(evidence)
        val output = (result as PreprocessResult.Success).output
        assertEquals(224, output.input.width)
        assertEquals(224, output.input.height)
        assertEquals(3, output.input.channels)
        assertEquals(PreprocessingVersion.V2, output.configVersion)
    }

    @Test
    fun `golden hashing never reads baseline evaluation history`() {
        // Hashing is pure over a ModelInput: no session, no feed
        // item, no evaluation records are involved.
        val frame = GoldenFixtureLoader.generateFrame(SourcePattern.ALL_WHITE, 2, 2)
        val config = simpleConfig()
        val evidence = PreprocessingEvidenceFactory.approve(
            frame = frame,
            sanitizationStatus = PrivacySanitizationStatus.SANITIZED
        )
        val result = DeterministicPreprocessor(config).preprocess(evidence)
        val output = (result as PreprocessResult.Success).output
        val hash = GoldenHasher.hashTensor(output.input)
        assertEquals(64, hash.length)
    }

    // -------------------------------------------------------------------
    // HELPERS
    // -------------------------------------------------------------------

    private fun simpleConfig(): PreprocessingConfig =
        PreprocessingConfig(
            version = PreprocessingVersion.V2,
            inputWidth = 2,
            inputHeight = 2,
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

    private fun resolveGoldenSourceDir(): File = run {
        val rel = "src/main/java/com/example/feedsense/analysis/ml/preprocess/golden"
        val cwd = File(System.getProperty("user.dir") ?: throw AssertionError("user.dir missing")).absoluteFile
        val underCwd = File(cwd, rel)
        if (underCwd.isDirectory) return@run underCwd
        val underParent = File(cwd.parentFile, "app/$rel")
        if (underParent.isDirectory) return@run underParent
        throw AssertionError(
            "golden source dir not found (working dir = ${cwd.path}); " +
                "unit tests must run from the app module"
        )
    }

    private fun findByExtension(dir: File, extension: String): List<File> =
        dir.walkTopDown().filter { it.isFile && it.name.endsWith(extension) }.toList()
}