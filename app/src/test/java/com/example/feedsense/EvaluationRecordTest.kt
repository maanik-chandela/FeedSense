package com.example.feedsense

import com.example.feedsense.model.AiPredictionRecord
import com.example.feedsense.model.EvaluationRecord
import com.example.feedsense.model.GroundTruth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8A-1. Verdict logic for the research
 * evaluation layer: immutable AI prediction snapshot is
 * compared against human ground truth to produce an
 * EvaluationRecord. Pure logic - no Room dependency.
 */
class EvaluationRecordTest {

    // --------------------------------
    // VERDICT DISTRIBUTION
    // --------------------------------

    @Test
    fun clearMatch_isCorrect() {

        val result = eval(
            predicted = AiPredictionRecord(
                evaluationItemId = "item-1",
                category = "sports",
                confidence = 0.9
            ),
            truth = GroundTruth(
                evaluationItemId = "item-1",
                category = "sports",
                ambiguity = GroundTruth.AMBIGUITY_CLEAR
            )
        )

        assertEquals(EvaluationRecord.VERDICT_CORRECT, result.verdict)
        assertEquals(true, result.categoryCorrect)
    }

    @Test
    fun clearMismatch_isIncorrect() {

        val result = eval(
            predicted = AiPredictionRecord(
                evaluationItemId = "item-1",
                category = "sports",
                confidence = 0.9
            ),
            truth = GroundTruth(
                evaluationItemId = "item-1",
                category = "comedy",
                ambiguity = GroundTruth.AMBIGUITY_CLEAR
            )
        )

        assertEquals(EvaluationRecord.VERDICT_INCORRECT, result.verdict)
        assertEquals(false, result.categoryCorrect)
    }

    @Test
    fun mixedPrimaryWrongSecondaryRight_isPartial() {

        val result = eval(
            predicted = AiPredictionRecord(
                evaluationItemId = "item-1",
                category = "sports",
                secondaryCategories = listOf("comedy")
            ),
            truth = GroundTruth(
                evaluationItemId = "item-1",
                category = "comedy",
                secondaryCategories = listOf("comedy"),
                ambiguity = GroundTruth.AMBIGUITY_MIXED
            )
        )

        assertEquals(EvaluationRecord.VERDICT_PARTIAL, result.verdict)
        assertEquals(false, result.categoryCorrect)
        assertEquals(true, result.secondaryMatch)
    }

    @Test
    fun mixedNoOverlap_isIncorrect() {        val result = eval(
            predicted = AiPredictionRecord(
                evaluationItemId = "item-1",
                category = "sports",
                secondaryCategories = listOf("fitness")
            ),
            truth = GroundTruth(
                evaluationItemId = "item-1",
                category = "gaming",
                secondaryCategories = listOf("technology"),
                ambiguity = GroundTruth.AMBIGUITY_MIXED
            )
        )

        assertEquals(EvaluationRecord.VERDICT_INCORRECT, result.verdict)
        assertEquals(false, result.secondaryMatch)
    }

    @Test
    fun mixedPredictedSecondaryMatchesTruthPrimary_isPartial() {

        val result = eval(
            predicted = AiPredictionRecord(
                evaluationItemId = "item-1",
                category = "sports",
                secondaryCategories = listOf("comedy")
            ),
            truth = GroundTruth(
                evaluationItemId = "item-1",
                category = "comedy",
                secondaryCategories = emptyList(),
                ambiguity = GroundTruth.AMBIGUITY_MIXED
            )
        )

        assertEquals(EvaluationRecord.VERDICT_PARTIAL, result.verdict)
        assertEquals(true, result.secondaryMatch)
    }

    @Test
    fun unknownAmbiguity_isUnknownVerdict() {

        val result = eval(
            predicted = AiPredictionRecord(
                evaluationItemId = "item-1",
                category = "sports"
            ),
            truth = GroundTruth(
                evaluationItemId = "item-1",
                category = "sports",
                ambiguity = GroundTruth.AMBIGUITY_UNKNOWN
            )
        )

        assertEquals(EvaluationRecord.VERDICT_UNKNOWN, result.verdict)
    }

    // --------------------------------
    // DURATION ACCURACY
    // --------------------------------

    @Test
    fun durationWithinTolerance_isAccurate() {

        val result = eval(
            predicted = AiPredictionRecord(
                evaluationItemId = "item-1",
                category = "sports",
                durationSeconds = 30
            ),
            truth = GroundTruth(
                evaluationItemId = "item-1",
                category = "sports",
                durationSeconds = 33
            )
        )

        assertEquals(false, result.durationInaccurate)
        assertEquals(-3, result.durationErrorSeconds)
    }

    @Test
    fun durationBeyondTolerance_isInaccurate() {

        val result = eval(
            predicted = AiPredictionRecord(
                evaluationItemId = "item-1",
                category = "sports",
                durationSeconds = 20
            ),
            truth = GroundTruth(
                evaluationItemId = "item-1",
                category = "sports",
                durationSeconds = 40
            )
        )

        assertEquals(true, result.durationInaccurate)
        assertEquals(-20, result.durationErrorSeconds)
    }

    // --------------------------------
    // CATEGORY NORMALIZATION
    // --------------------------------

    @Test
    fun categoryComparison_isNormalizedIgnoringCase() {

        val result = eval(
            predicted = AiPredictionRecord(
                evaluationItemId = "item-1",
                category = "Sports"
            ),
            truth = GroundTruth(
                evaluationItemId = "item-1",
                category = "sports",
                ambiguity = GroundTruth.AMBIGUITY_CLEAR
            )
        )

        assertEquals(EvaluationRecord.VERDICT_CORRECT, result.verdict)
    }

    @Test
    fun nullPredictedCategory_isNotCorrect() {

        val result = eval(
            predicted = AiPredictionRecord(
                evaluationItemId = "item-1",
                category = null
            ),
            truth = GroundTruth(
                evaluationItemId = "item-1",
                category = "sports",
                ambiguity = GroundTruth.AMBIGUITY_CLEAR
            )
        )

        assertEquals(EvaluationRecord.VERDICT_INCORRECT, result.verdict)
    }

    // --------------------------------
    // PER-FIELD AGREEMENT
    // --------------------------------

    @Test
    fun topicAndToneAgreement_areComputed() {

        val result = eval(
            predicted = AiPredictionRecord(
                evaluationItemId = "item-1",
                category = "sports",
                topic = "cricket",
                tone = "energetic"
            ),
            truth = GroundTruth(
                evaluationItemId = "item-1",
                category = "sports",
                topic = "cricket",
                tone = "test"
            )
        )

        assertEquals(true, result.topicAgreement)
        assertEquals(false, result.toneAgreement)
    }

    @Test
    fun nullTruthField_leavesNullAgreement() {

        val result = eval(
            predicted = AiPredictionRecord(
                evaluationItemId = "item-1",
                category = "sports",
                topic = "cricket"
            ),
            truth = GroundTruth(
                evaluationItemId = "item-1",
                category = "sports",
                topic = null
            )
        )

        assertNull(result.topicAgreement)
    }

    @Test
    fun skippedMismatch_isRecorded() {

        val result = eval(
            predicted = AiPredictionRecord(
                evaluationItemId = "item-1",
                category = "sports",
                skipped = false
            ),
            truth = GroundTruth(
                evaluationItemId = "item-1",
                category = "sports",
                skipped = true
            )
        )

        assertEquals(false, result.skippedAgreement)
    }

    @Test
    fun interactionSignalDisagreement_isCounted() {

        val result = eval(
            predicted = AiPredictionRecord(
                evaluationItemId = "item-1",
                category = "sports",
                interactionSignals = listOf("like_indicator")
            ),
            truth = GroundTruth(
                evaluationItemId = "item-1",
                category = "sports",
                interactionSignals =
                    listOf("like_indicator", "comment_indicator")
            )
        )

        assertNotNull(result.interactionSignalsDisagreement)
        assertEquals(
            1,
            result.interactionSignalsDisagreement
        )
    }

    // --------------------------------
    // PROVENANCE PRESERVATION
    // --------------------------------

    @Test
    fun resultPreservesModelVersion() {

        val result = eval(
            predicted = AiPredictionRecord(
                evaluationItemId = "item-1",
                category = "sports",
                modelVersion = "local-model-v2.0"
            ),
            truth = GroundTruth(
                evaluationItemId = "item-1",
                category = "sports"
            )
        )

        assertEquals("local-model-v2.0", result.modelVersion)
    }

    // --------------------------------
    // AMBIGUITY CONSTANTS
    // --------------------------------

    @Test
    fun ambiguityConstantSet_containsAllFourStates() {

        assertEquals(
            4,
            GroundTruth.VALID_AMBIGUITY.size
        )
        assertTrue(
            GroundTruth.AMBIGUITY_CLEAR in
                GroundTruth.VALID_AMBIGUITY
        )
        assertTrue(
            GroundTruth.AMBIGUITY_AMBIGUOUS in
                GroundTruth.VALID_AMBIGUITY
        )
        assertTrue(
            GroundTruth.AMBIGUITY_MIXED in
                GroundTruth.VALID_AMBIGUITY
        )
        assertTrue(
            GroundTruth.AMBIGUITY_UNKNOWN in
                GroundTruth.VALID_AMBIGUITY
        )
    }

    // --------------------------------
    // IMMUTABILITY OF THE SNAPSHOT
    // --------------------------------

    @Test
    fun aiPredictionSnapshot_isReadOnlyEvidence() {

        val snapshot = AiPredictionRecord(
            evaluationItemId = "item-1",
            category = "sports",
            confidence = 0.9,
            modelVersion = "local-model-v2.0"
        )

        assertNotNull(snapshot.evaluationItemId)
        assertNotNull(snapshot.id)
        assertEquals("sports", snapshot.category)
        assertEquals("local-model-v2.0", snapshot.modelVersion)
    }

    // --------------------------------
    // HELPER
    // --------------------------------

    private fun eval(
        predicted: AiPredictionRecord,
        truth: GroundTruth
    ): EvaluationRecord {
        // predict with a stable id for linkage
        val prediction = predicted.copy(id = "pred-1")
        val groundTruth = truth.copy(id = "truth-1")
        return EvaluationRecord.fromComponents(
            prediction = prediction,
            truth = groundTruth
        )
    }
}
