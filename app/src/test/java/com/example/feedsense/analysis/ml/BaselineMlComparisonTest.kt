package com.example.feedsense.analysis.ml

import com.example.feedsense.analysis.FrameAnalysisResult
import com.example.feedsense.model.AiPredictionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-14.
 *
 * Baseline vs ML comparison: exposes agreement/disagreement
 * without mutating either prediction (section 20).
 */
class BaselineMlComparisonTest {

    private fun ml(
        status: InferenceStatus = InferenceStatus.SUCCESS,
        category: String? = "advertisement",
        confidence: Double = 0.7,
        version: String = "ml-v1"
    ): ModelInferenceResult {
        return ModelInferenceResult(
            modelId = "model",
            modelVersion = version,
            status = status,
            rankedPredictions = if (category != null && status == InferenceStatus.SUCCESS) {
                listOf(RankedPrediction(category, confidence))
            } else {
                emptyList()
            }
        )
    }

    private val baselineFrame = FrameAnalysisResult(
        status = "OK",
        fileName = "f.png",
        width = 100,
        height = 100,
        fileSizeBytes = 10,
        message = "ok",
        contentCategory = "sports",
        confidence = 0.8,
        modelVersion = "local-v6.0"
    )

    @Test
    fun `agreeing predictions compare as agree`() {
        val comparison = BaselineMlComparison.of(
            baselineFrame,
            ml(category = "sports", confidence = 0.9)
        )
        assertTrue(comparison.categoriesAgree)
        assertEquals(BaselineMlComparisonKind.AGREE, comparison.comparisonKind)
        assertEquals("sports", comparison.baselineCategory)
        assertEquals("sports", comparison.mlCategory)
        assertEquals("ml-v1", comparison.mlModelVersion)
        assertEquals("local-v6.0", comparison.baselineModelVersion)
    }

    @Test
    fun `disagreeing predictions compare as disagree`() {
        val comparison = BaselineMlComparison.of(
            baselineFrame,
            ml(category = "advertisement", confidence = 0.6)
        )
        assertFalse(comparison.categoriesAgree)
        assertEquals(BaselineMlComparisonKind.DISAGREE, comparison.comparisonKind)
        assertEquals("sports", comparison.baselineCategory)
        assertEquals("advertisement", comparison.mlCategory)
    }

    @Test
    fun `baseline decided ml abstained`() {
        val comparison = BaselineMlComparison.of(
            baselineFrame,
            ml(category = null)
        )
        assertEquals(
            BaselineMlComparisonKind.BASELINE_ONLY_DECIDED,
            comparison.comparisonKind
        )
        assertNull(comparison.mlCategory)
        assertEquals(InferenceStatus.SUCCESS, comparison.mlStatus)
    }

    @Test
    fun `ml available while baseline unknown`() {
        val unknown = baselineFrame.copy(contentCategory = null, confidence = null)
        val comparison = BaselineMlComparison.of(unknown, ml(category = "meme"))
        assertEquals(BaselineMlComparisonKind.ML_ONLY_DECIDED, comparison.comparisonKind)
        assertFalse(comparison.categoriesAgree)
    }

    @Test
    fun `ml failure is explicit`() {
        val comparison = BaselineMlComparison.of(
            baselineFrame,
            ml(status = InferenceStatus.INFERENCE_FAILURE)
        )
        assertEquals(BaselineMlComparisonKind.ML_UNAVAILABLE, comparison.comparisonKind)
        assertNull(comparison.mlCategory)
        assertEquals(InferenceStatus.INFERENCE_FAILURE, comparison.mlStatus)
    }

    @Test
    fun `works from the frozen 8a record without mutating it`() {
        val record = AiPredictionRecord(
            evaluationItemId = "e1",
            source = "LOCAL",
            modelVersion = "local-v6.0",
            category = "education",
            confidence = 0.75,
            feedItemId = "feed-1"
        )
        val snapshot = record.copy()
        val comparison = BaselineMlComparison.of(record, ml(category = "education"))
        assertTrue(comparison.categoriesAgree)
        assertEquals(BaselineMlComparisonKind.AGREE, comparison.comparisonKind)
        assertEquals("feed-1", comparison.feedItemId)
        assertEquals("local-v6.0", comparison.baselineModelVersion)
        // untouched
        assertEquals(snapshot, record)
        assertEquals("LOCAL", record.source)
        assertEquals(0.75, record.confidence ?: -1.0, 1e-9)
    }

    @Test
    fun `neither decided when both sides are empty`() {
        val unknown = baselineFrame.copy(contentCategory = null, confidence = null)
        val comparison = BaselineMlComparison.of(
            unknown,
            ml(category = null, confidence = 0.0)
        )
        assertNull(comparison.mlCategory)
        assertEquals(InferenceStatus.SUCCESS, comparison.mlStatus)
        assertFalse(comparison.categoriesAgree)
    }

    @Test
    fun `comparison holds references without copying or mutating input records`() {
        val record = AiPredictionRecord(
            evaluationItemId = "e2",
            source = "LOCAL",
            modelVersion = "local-v6.0",
            category = "comedy",
            confidence = 0.6,
            feedItemId = "feed-2"
        )
        val snapshot = record.copy()
        val mlResult = ml(category = "comedy")
        val mlSnapshot = mlResult.copy()
        val comparison = BaselineMlComparison.of(record, mlResult)
        assertTrue(comparison.categoriesAgree)
        // same versions/scores visible, original objects still equal their snapshots
        assertEquals(0.6, comparison.baselineConfidence ?: -1.0, 1e-9)
        assertEquals(0.7, comparison.mlConfidence ?: -1.0, 1e-9)
        assertEquals(snapshot, record)
        assertEquals(mlSnapshot, mlResult)
        assertSame(mlResult.rankedPredictions, comparison.mlTopK)
    }
}