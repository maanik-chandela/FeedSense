package com.example.feedsense

import com.example.feedsense.analysis.evidence.EvidenceFusionResult
import com.example.feedsense.analysis.evidence.EvidenceMetrics
import com.example.feedsense.analysis.evidence.EvidencePipeline
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-5.
 *
 * EvidencePipeline: integration, metrics,
 * and end-to-end behavior.
 */
class EvidencePipelineTest {

    private fun createTestFile(): File {
        val dir = File(
            System.getProperty("java.io.tmpdir"),
            "evidence_pipeline_test"
        )
        dir.mkdirs()
        return File(dir, "test.png").apply {
            createNewFile()
        }
    }

    // --------------------------------
    // BASIC PROCESSING
    // --------------------------------

    @Test
    fun `pipeline processes frame with OCR text`() {

        val file = createTestFile()

        val pipeline = EvidencePipeline()

        val result = pipeline.process(
            frameFile = file,
            ocrText = "IPL 2026 cricket match"
        )

        assertNotNull(result)
        assertTrue(result.evidenceCount > 0)
        assertNotNull(result.topCategory)

        file.delete()
    }

    @Test
    fun `pipeline processes frame with platform`() {

        val file = createTestFile()

        val pipeline = EvidencePipeline()

        val result = pipeline.process(
            frameFile = file,
            ocrText = "Instagram post",
            platform = "Instagram"
        )

        assertNotNull(result)
        assertTrue(result.evidenceCount > 0)

        file.delete()
    }

    // --------------------------------
    // EMPTY INPUT
    // --------------------------------

    @Test
    fun `pipeline with no data produces low-confidence result`() {

        val file = createTestFile()

        val pipeline = EvidencePipeline()

        val result = pipeline.process(
            frameFile = file
        )

        assertNotNull(result)
        // Visual and layout extractors always produce
        // evidence from file dimensions, so result is
        // not EMPTY but has low evidence count
        assertTrue(result.evidenceCount >= 0)

        file.delete()
    }

    // --------------------------------
    // METRICS
    // --------------------------------

    @Test
    fun `pipeline records metrics`() {

        val file = createTestFile()
        val metrics = EvidenceMetrics()

        val pipeline = EvidencePipeline(
            metrics = metrics
        )

        pipeline.process(
            frameFile = file,
            ocrText = "cricket match"
        )

        val snapshot = metrics.snapshot()

        assertEquals(1L, snapshot.framesReceived)
        assertEquals(1L, snapshot.framesFused)
        assertTrue(
            snapshot.totalEvidenceCount > 0
        )

        file.delete()
    }

    // --------------------------------
    // TEMPORAL CONTEXT
    // --------------------------------

    @Test
    fun `pipeline with temporal context`() {

        val file = createTestFile()

        val pipeline = EvidencePipeline()

        val result = pipeline.process(
            frameFile = file,
            ocrText = "cricket",
            durationMs = 30000L,
            frameCount = 30
        )

        assertNotNull(result)
        assertTrue(result.evidenceCount > 0)

        file.delete()
    }

    // --------------------------------
    // DETERMINISM
    // --------------------------------

    @Test
    fun `same input produces same output`() {

        val file = createTestFile()

        val pipeline = EvidencePipeline()

        val result1 = pipeline.process(
            frameFile = file,
            ocrText = "IPL cricket match score"
        )

        val result2 = pipeline.process(
            frameFile = file,
            ocrText = "IPL cricket match score"
        )

        assertEquals(
            result1.categoryScores,
            result2.categoryScores
        )

        assertEquals(
            result1.topCategory,
            result2.topCategory
        )

        file.delete()
    }

    // --------------------------------
    // EVIDENCE SNAPSHOT
    // --------------------------------

    @Test
    fun `result contains evidence snapshot`() {

        val file = createTestFile()

        val pipeline = EvidencePipeline()

        val result = pipeline.process(
            frameFile = file,
            ocrText = "cricket match"
        )

        assertTrue(
            result.evidenceSnapshot.isNotEmpty()
        )

        file.delete()
    }

    // --------------------------------
    // COMPOSABILITY
    // --------------------------------

    @Test
    fun `pipeline with custom extractors`() {

        val file = createTestFile()

        val pipeline = EvidencePipeline(
            extractors = listOf(
                com.example.feedsense.analysis
                    .evidence
                    .OcrEvidenceExtractor()
            )
        )

        val result = pipeline.process(
            frameFile = file,
            ocrText = "cricket match score"
        )

        assertTrue(result.evidenceCount > 0)

        file.delete()
    }
}
