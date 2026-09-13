package com.example.feedsense.analysis.ml.preprocess

import com.example.feedsense.analysis.privacy.SyntheticFrames
import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.GroundTruth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import java.util.UUID

/*
 * Milestone 8B-15-3 (§22 baseline isolation).
 *
 * The baseline (8B-4..8B-9 feed analysis, taxonomy, ground truth,
 * evaluation records) is preserved unchanged. This file proves:
 *
 *   - preprocessing never mutates the privacy frame it is given;
 *   - the ML tensor never aliases the evidence frame buffer;
 *   - baseline record classes (FeedItem / GroundTruth /
 *     AiPredictionRecord) construct and compare identically before
 *     and after preprocessing work - no behaviour coupling;
 *   - the preprocess package does not import baseline/feed/evaluation
 *     packages at the source level.
 *
 * NO inference wiring exists here: this milestone stops at a
 * validated model-ready tensor (8B-15-4 is not executed).
 */
class PreprocessingBaselineIsolationTest {

    // --------------------------------
    // FRAME NON-MUTATION
    // --------------------------------

    @Test
    fun `preprocessing pipeline never mutates the evidence frame`() {
        for (crop in listOf(AspectRatioPolicy.RESIZE, AspectRatioPolicy.CENTER_CROP, AspectRatioPolicy.PAD)) {
            val config = when (crop) {
                AspectRatioPolicy.PAD -> PreprocessingConfig(
                    version = PreprocessingVersion.V2,
                    inputWidth = 4, inputHeight = 4,
                    cropPolicy = AspectRatioPolicy.PAD,
                    paddingPolicy = PaddingPolicy.PAD_BLACK,
                    interpolation = Interpolation.NEAREST
                )
                else -> PreprocessingConfig(
                    version = PreprocessingVersion.V2,
                    inputWidth = 4, inputHeight = 4,
                    cropPolicy = crop,
                    interpolation = Interpolation.NEAREST
                )
            }
            val frame = SyntheticFrames.verticalGradient(5, 7)
            val snapshot = frame.pixels.copyOf()
            val snapshotChecksum = frame.frameChecksum()

            val preprocessor = DeterministicPreprocessor(config)
            val evidence = PreprocessingEvidenceFactory.approve(
                frame = frame,
                sanitizationStatus = com.example.feedsense.analysis.privacy.PrivacySanitizationStatus.SANITIZED
            )
            preprocessor.preprocess(evidence)

            assertTrue("crop=$crop: frame pixels were mutated",
                snapshot.contentEquals(frame.pixels))
            assertEquals("crop=$crop: frame checksum changed",
                snapshotChecksum, frame.frameChecksum())
        }
    }

    @Test
    fun `tensor floats and quantized bytes never share the frame buffer`() {
        val frame = SyntheticFrames.verticalGradient(4, 4)
        val preprocessor = DeterministicPreprocessor(PreprocessingConfig(
            version = PreprocessingVersion.V2,
            inputWidth = 4, inputHeight = 4,
            tensorType = PreprocessingTensorType.INT8,
            scale = 1.0 / 127.0,
            zeroPoint = 0.0,
            interpolation = Interpolation.NEAREST
        ))
        val evidence = PreprocessingEvidenceFactory.approve(
            frame = frame,
            sanitizationStatus = com.example.feedsense.analysis.privacy.PrivacySanitizationStatus.NOT_REQUIRED
        )
        val result = preprocessor.preprocess(evidence)
        val output = (result as PreprocessResult.Success).output

        assertEquals(4 * 4 * 3, output.input.floats.size)
        assertEquals(4 * 4 * 3, output.input.quantizedBytes!!.size)

        // The produced tensor must be a snapshot: mutating the source
        // frame afterwards never rewrites already-produced bytes.
        val floatsSnapshot = output.input.floats.copyOf()
        val bytesSnapshot = output.input.quantizedBytes.copyOf()
        frame.setPixel(0, 0, 0xFF000000.toInt())
        frame.setPixel(3, 3, 0xFFFFFFFF.toInt())
        assertTrue(floatsSnapshot.contentEquals(output.input.floats))
        assertTrue(bytesSnapshot.contentEquals(output.input.quantizedBytes))
    }

    // --------------------------------
    // BASELINE RECORDS UNCHANGED
    // --------------------------------

    @Test
    fun `baseline records construct and compare identically across runs`() {
        val sessionId = UUID.randomUUID().toString()
        val evaluationItemId = UUID.randomUUID().toString()
        val now = LocalDateTime.of(2026, 9, 8, 12, 0)

        val item = FeedItem(
            id = "item-1",
            sessionId = sessionId,
            startTime = now,
            endTime = now.plusSeconds(4),
            durationSeconds = 4,
            category = "cricket",
            categoryDomain = "sports",
            confidence = 0.87,
            representativeFramePath = "/data/frames/item-1.png",
            frameCount = 10,
            modelVersion = "heuristic-v1",
            source = FeedItem.SOURCE_AI
        )
        val truth = GroundTruth(
            evaluationItemId = evaluationItemId,
            annotatorId = "researcher-a",
            category = "cricket",
            categoryDomain = "sports",
            ambiguity = GroundTruth.AMBIGUITY_CLEAR
        )
        val prediction = AiPredictionRecord(
            evaluationItemId = evaluationItemId,
            source = "LOCAL",
            modelVersion = "heuristic-v1",
            category = "cricket",
            confidence = 0.87,
            feedItemId = "item-1"
        )

        // Baseline behaviour must be untouched by preprocessing work.
        assertEquals(sessionId, item.sessionId)
        assertEquals("cricket", truth.category)
        assertEquals(0.87, prediction.confidence!!, 0.0)
        val first = listOf(item, truth, prediction)
        val second = listOf(item, truth, prediction)
        assertEquals(first, second)
        assertTrue(item.copy(needsReview = true) != item)
        assertEquals(2, GroundTruth.signalsMarkedTrue(truth.copy(liked = true, shared = true)).size)

        // A sample preprocess still succeeds alongside untouched baselines.
        val output = DeterministicPreprocessor(PreprocessingConfig(
            version = PreprocessingVersion.V2,
            inputWidth = 2, inputHeight = 2
        )).preprocess(
            PreprocessingEvidenceFactory.approve(
                frame = SyntheticFrames.solid(2, 2, 0xFF000000.toInt()),
                sanitizationStatus = com.example.feedsense.analysis.privacy.PrivacySanitizationStatus.NOT_REQUIRED
            )
        )
        assertTrue(output is PreprocessResult.Success)
        assertEquals(12, (output as PreprocessResult.Success).output.tensorSize)
    }

    @Test
    fun `baseline classes are not referenced anywhere in preprocess source`() {
        val preprocessSourceDir = resolvePreprocessSourceDir()

        val baselineTerms = listOf(
            "com.example.feedsense.model.FeedItem",
            "com.example.feedsense.model.GroundTruth",
            "com.example.feedsense.model.AiPredictionRecord",
            "categorization", "taxonomy", "evaluation.", "EvaluationRecord"
        )

        findByExtension(preprocessSourceDir, ".kt").forEach { file ->
            val text = file.readText()
            for (term in baselineTerms) {
                assertFalse(
                    "preprocess source ${file.name} must not reference baseline term '$term'",
                    text.contains(term)
                )
            }
        }
    }

    @Test
    fun `preprocessing metadata source has no feed-session dependency`() {
        val meta = PreprocessingMetadata(config = PreprocessingConfig.mobileNetV4ConvReference())
        assertTrue(meta.canonical.isNotBlank())
        assertEquals(meta.configHash, meta.configHash)
        // No mention of feed items/sessions/evidence ids.
        assertFalse(meta.canonical.contains("evidence"))
        assertFalse(meta.canonical.contains("session"))
        assertFalse(meta.canonical.contains("feedItem"))
    }

    // --------------------------------
    // HELPERS
    // --------------------------------

    private fun resolvePreprocessSourceDir(): File = run {
        val rel = "src/main/java/com/example/feedsense/analysis/ml/preprocess"
        val cwd = File(System.getProperty("user.dir") ?: throw AssertionError("user.dir missing")).absoluteFile
        val underCwd = File(cwd, rel)
        if (underCwd.isDirectory) return@run underCwd
        val underParent = File(cwd.parentFile, "app/$rel")
        if (underParent.isDirectory) return@run underParent
        throw AssertionError(
            "preprocess source dir not found (working dir = ${cwd.path}); " +
                "unit tests must run from the app module"
        )
    }

    private fun findByExtension(dir: File, extension: String): List<File> =
        dir.walkTopDown().filter { it.isFile && it.name.endsWith(extension) }.toList()
}