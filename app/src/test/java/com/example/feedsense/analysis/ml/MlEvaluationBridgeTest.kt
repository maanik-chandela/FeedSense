package com.example.feedsense.analysis.ml

import com.example.feedsense.model.AiPredictionRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-14.
 *
 * The 8A bridge: produces a NEW evaluation-compatible
 * AiPredictionRecord tagged source=ML_MODEL, never touching
 * the baseline record (sections 21, 22).
 */
class MlEvaluationBridgeTest {

    private fun successResult(): ModelInferenceResult {
        return ModelInferenceResult(
            modelId = "feedsense-category-model",
            modelVersion = "ml-v1",
            modelChecksum = "abc123",
            status = InferenceStatus.SUCCESS,
            rankedPredictions = listOf(
                RankedPrediction("sports", 0.82),
                RankedPrediction("entertainment", 0.11),
                RankedPrediction("meme", 0.04)
            ),
            preprocessVersion = "preprocess-v1",
            inputSpecVersion = "input-v1",
            outputSpecVersion = "output-v1",
            timestampMs = 1234L
        )
    }

    @Test
    fun `successful inference maps to an explicit ml record`() {
        val record = MlEvaluationBridge.toAiPredictionRecord(
            successResult(),
            evaluationItemId = "item-1"
        )
        assertEquals("ML_MODEL", record!!.source)
        assertEquals("ml-v1", record.modelVersion)
        assertEquals("sports", record.category)
        assertEquals("sports", record.categoryDomain)
        assertEquals(0.82, record.confidence ?: -1.0, 1e-9)
        assertEquals(listOf("entertainment", "meme"),
            record.secondaryCategories)
        assertEquals(0.11, record.categoryScores["entertainment"] ?: -1.0, 1e-9)
        assertTrue(record.categoryScores.containsKey("sports"))
    }

    @Test
    fun `non success inference yields no record`() {
        val failed = successResult().copy(
            status = InferenceStatus.MODEL_UNAVAILABLE,
            rankedPredictions = emptyList()
        )
        assertNull(
            MlEvaluationBridge.toAiPredictionRecord(
                failed, evaluationItemId = "item-2"
            )
        )
    }

    @Test
    fun `no prediction yields no record`() {
        val empty = successResult().copy(rankedPredictions = emptyList())
        assertNull(
            MlEvaluationBridge.toAiPredictionRecord(
                empty, evaluationItemId = "item-3"
            )
        )
    }

    @Test
    fun `baseline record is never mutated`() {
        val baseline = AiPredictionRecord(
            evaluationItemId = "item-4",
            source = "LOCAL",
            modelVersion = "local-v6.0",
            category = "sports",
            categoryDomain = "sports",
            confidence = 0.91,
            feedItemId = "feed-1"
        )

        val baselineSnapshot = baseline.copy()
        val mlRecord = MlEvaluationBridge.toAiPredictionRecord(
            successResult(),
            evaluationItemId = "item-4"
        )

        assertEquals(baselineSnapshot, baseline)
        assertEquals("LOCAL", baseline.source)
        assertEquals("local-v6.0", baseline.modelVersion)
        assertEquals(0.91, baseline.confidence ?: -1.0, 1e-9)
        assertEquals("ML_MODEL", mlRecord!!.source)
        assertEquals("ml-v1", mlRecord.modelVersion)
    }
}