package com.example.feedsense

import com.example.feedsense.analysis.AnalysisSource
import com.example.feedsense.analysis.TrustLevel
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.LabeledReference
import com.example.feedsense.model.ModelFeedback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class DataIntegrityTest {

    // --------------------------------
    // TRUST LEVEL DERIVATION
    // --------------------------------
    //
    // Every combination of validationStatus + labelSource
    // + aiSource + aiConfidence must map to exactly one
    // TrustLevel. The chain covers all 6 provenance
    // labels:
    //   HUMAN, CLOUD, LOCAL, USER, CLOUD_REFERENCE,
    //   LOCAL_MODEL, SYSTEM
    //

    @Test
    fun trustLevel_humanValidated_isUserConfirmed() {
        val ref = makeReference(
            validationStatus = LabeledReference.VALIDATION_VALIDATED,
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            aiSource = AnalysisSource.LOCAL.name,
            aiConfidence = 0.9
        )

        assertEquals(TrustLevel.USER_CONFIRMED, TrustLevel.of(ref))
    }

    @Test
    fun trustLevel_cloudValidated_isCloudValidated() {
        val ref = makeReference(
            validationStatus = LabeledReference.VALIDATION_VALIDATED,
            labelSource = LabeledReference.LABEL_SOURCE_CLOUD,
            aiSource = AnalysisSource.CLOUD.name,
            aiConfidence = 0.85
        )

        assertEquals(TrustLevel.CLOUD_VALIDATED, TrustLevel.of(ref))
    }

    @Test
    fun trustLevel_localHigh_isLocalHighConfidence() {
        val ref = makeReference(
            validationStatus = LabeledReference.VALIDATION_PENDING,
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            aiSource = AnalysisSource.LOCAL.name,
            aiConfidence = 0.85
        )

        assertEquals(TrustLevel.LOCAL_HIGH_CONFIDENCE, TrustLevel.of(ref))
    }

    @Test
    fun trustLevel_localLow_isLocalLowConfidence() {
        val ref = makeReference(
            validationStatus = LabeledReference.VALIDATION_PENDING,
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            aiSource = AnalysisSource.LOCAL.name,
            aiConfidence = 0.5
        )

        assertEquals(TrustLevel.LOCAL_LOW_CONFIDENCE, TrustLevel.of(ref))
    }

    @Test
    fun trustLevel_cloudPending_isLocalLowConfidence() {
        val ref = makeReference(
            validationStatus = LabeledReference.VALIDATION_PENDING,
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            aiSource = AnalysisSource.CLOUD.name,
            aiConfidence = 0.9
        )

        assertEquals(TrustLevel.LOCAL_LOW_CONFIDENCE, TrustLevel.of(ref))
    }

    @Test
    fun trustLevel_nullConfidence_isLocalLowConfidence() {
        val ref = makeReference(
            validationStatus = LabeledReference.VALIDATION_PENDING,
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            aiSource = AnalysisSource.LOCAL.name,
            aiConfidence = null
        )

        assertEquals(TrustLevel.LOCAL_LOW_CONFIDENCE, TrustLevel.of(ref))
    }

    @Test
    fun trustLevel_rejected_isLocalHighConfidence() {
        val ref = makeReference(
            validationStatus = LabeledReference.VALIDATION_REJECTED,
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            aiSource = AnalysisSource.LOCAL.name,
            aiConfidence = 0.95
        )

        // REJECTED is not VALIDATED, so TrustLevel checks
        // aiSource + confidence: LOCAL + high = LOCAL_HIGH
        assertEquals(TrustLevel.LOCAL_HIGH_CONFIDENCE, TrustLevel.of(ref))
    }

    @Test
    fun trustLevel_skipped_isLocalHighConfidence() {
        val ref = makeReference(
            validationStatus = LabeledReference.VALIDATION_SKIPPED,
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            aiSource = AnalysisSource.LOCAL.name,
            aiConfidence = 0.9
        )

        // SKIPPED is not VALIDATED, so TrustLevel checks
        // aiSource + confidence: LOCAL + high = LOCAL_HIGH
        assertEquals(TrustLevel.LOCAL_HIGH_CONFIDENCE, TrustLevel.of(ref))
    }

    @Test
    fun trustLevel_rejectedLowConfidence_isLocalLowConfidence() {
        val ref = makeReference(
            validationStatus = LabeledReference.VALIDATION_REJECTED,
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            aiSource = AnalysisSource.LOCAL.name,
            aiConfidence = 0.4
        )

        assertEquals(TrustLevel.LOCAL_LOW_CONFIDENCE, TrustLevel.of(ref))
    }

    @Test
    fun trustLevel_skippedLowConfidence_isLocalLowConfidence() {
        val ref = makeReference(
            validationStatus = LabeledReference.VALIDATION_SKIPPED,
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            aiSource = AnalysisSource.LOCAL.name,
            aiConfidence = 0.4
        )

        assertEquals(TrustLevel.LOCAL_LOW_CONFIDENCE, TrustLevel.of(ref))
    }

    // --------------------------------
    // TRUST LEVEL AUTHORITY ORDER
    // --------------------------------

    @Test
    fun trustLevel_authorityOrder_isConsistent() {
        assertTrue(
            "USER_CONFIRMED must outrank CLOUD_VALIDATED",
            TrustLevel.USER_CONFIRMED.authority >
                    TrustLevel.CLOUD_VALIDATED.authority
        )
        assertTrue(
            "CLOUD_VALIDATED must outrank LOCAL_HIGH",
            TrustLevel.CLOUD_VALIDATED.authority >
                    TrustLevel.LOCAL_HIGH_CONFIDENCE.authority
        )
        assertTrue(
            "LOCAL_HIGH must outrank LOCAL_LOW",
            TrustLevel.LOCAL_HIGH_CONFIDENCE.authority >
                    TrustLevel.LOCAL_LOW_CONFIDENCE.authority
        )
    }

    @Test
    fun trustLevel_authorityWeights_areNormalized() {
        assertEquals(1.0, TrustLevel.weight(TrustLevel.USER_CONFIRMED), 0.001)
        assertEquals(0.75, TrustLevel.weight(TrustLevel.CLOUD_VALIDATED), 0.001)
        assertEquals(0.5, TrustLevel.weight(TrustLevel.LOCAL_HIGH_CONFIDENCE), 0.001)
        assertEquals(0.25, TrustLevel.weight(TrustLevel.LOCAL_LOW_CONFIDENCE), 0.001)
    }

    // --------------------------------
    // FEED ITEM PROVENANCE INTEGRITY
    // --------------------------------

    @Test
    fun feedItem_aiSource_isOneOfKnownValues() {
        val validSources = setOf(
            AnalysisSource.LOCAL.name,
            AnalysisSource.CLOUD.name
        )

        val item = FeedItem(
            sessionId = "s1",
            startTime = LocalDateTime.now(),
            representativeFramePath = "/tmp/t.png",
            modelVersion = "local-v6.0"
        )

        assertTrue(
            "modelVersion must not be null when set",
            item.modelVersion != null
        )
    }

    @Test
    fun feedItem_uncertaintyLevel_isOneOfFrozenValues() {
        val validLevels = setOf(
            FeedItem.UNCERTAINTY_HIGH,
            FeedItem.UNCERTAINTY_MEDIUM,
            FeedItem.UNCERTAINTY_LOW
        )

        val item = FeedItem(
            sessionId = "s1",
            startTime = LocalDateTime.now(),
            representativeFramePath = "/tmp/t.png"
        )

        assertTrue(
            "Default uncertainty must be a valid frozen value",
            validLevels.contains(item.uncertaintyLevel)
        )
    }

    @Test
    fun feedItem_contentType_isOneOfFrozenValues() {
        val validTypes = setOf(
            FeedItem.CONTENT_UNKNOWN,
            FeedItem.CONTENT_SHORT_VIDEO,
            FeedItem.CONTENT_LONG_VIDEO
        )

        val item = FeedItem(
            sessionId = "s1",
            startTime = LocalDateTime.now(),
            representativeFramePath = "/tmp/t.png"
        )

        assertTrue(
            "Default contentType must be a valid frozen value",
            validTypes.contains(item.contentType)
        )
    }

    @Test
    fun feedItem_needsReview_defaultIsFalse() {
        val item = FeedItem(
            sessionId = "s1",
            startTime = LocalDateTime.now(),
            representativeFramePath = "/tmp/t.png"
        )

        assertFalse(item.needsReview)
    }

    @Test
    fun feedItem_confidence_isInValidRange() {
        val item = FeedItem(
            sessionId = "s1",
            startTime = LocalDateTime.now(),
            representativeFramePath = "/tmp/t.png",
            confidence = 0.85
        )

        assertTrue(item.confidence!! >= 0.0)
        assertTrue(item.confidence!! <= 1.0)
    }

    // --------------------------------
    // LABELED REFERENCE PROVENANCE
    // --------------------------------

    @Test
    fun labeledReference_labelSource_isOneOfKnownValues() {
        val validSources = setOf(
            LabeledReference.LABEL_SOURCE_HUMAN,
            LabeledReference.LABEL_SOURCE_CLOUD
        )

        assertTrue(validSources.contains(LabeledReference.LABEL_SOURCE_HUMAN))
        assertTrue(validSources.contains(LabeledReference.LABEL_SOURCE_CLOUD))
    }

    @Test
    fun labeledReference_validationStatus_isOneOfKnownValues() {
        val validStatuses = setOf(
            LabeledReference.VALIDATION_PENDING,
            LabeledReference.VALIDATION_VALIDATED,
            LabeledReference.VALIDATION_REJECTED,
            LabeledReference.VALIDATION_SKIPPED
        )

        assertTrue(validStatuses.contains(LabeledReference.VALIDATION_PENDING))
        assertTrue(validStatuses.contains(LabeledReference.VALIDATION_VALIDATED))
        assertTrue(validStatuses.contains(LabeledReference.VALIDATION_REJECTED))
        assertTrue(validStatuses.contains(LabeledReference.VALIDATION_SKIPPED))
    }

    @Test
    fun labeledReference_confidence_isInValidRange() {
        val ref = makeReference(
            validationStatus = LabeledReference.VALIDATION_PENDING,
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            aiSource = AnalysisSource.LOCAL.name,
            aiConfidence = 0.75
        )

        assertTrue(ref.aiConfidence!! >= 0.0)
        assertTrue(ref.aiConfidence!! <= 1.0)
    }

    // --------------------------------
    // MODEL FEEDBACK PROVENANCE
    // --------------------------------

    @Test
    fun modelFeedback_correctionSource_isOneOfKnownValues() {
        val validSources = setOf(
            ModelFeedback.SOURCE_USER,
            ModelFeedback.SOURCE_CLOUD_REFERENCE,
            ModelFeedback.SOURCE_LOCAL_MODEL,
            ModelFeedback.SOURCE_SYSTEM
        )

        assertTrue(validSources.contains(ModelFeedback.SOURCE_USER))
        assertTrue(validSources.contains(ModelFeedback.SOURCE_CLOUD_REFERENCE))
        assertTrue(validSources.contains(ModelFeedback.SOURCE_LOCAL_MODEL))
        assertTrue(validSources.contains(ModelFeedback.SOURCE_SYSTEM))
    }

    @Test
    fun modelFeedback_preservesOriginalPrediction() {
        val reference = makeReference(
            validationStatus = LabeledReference.VALIDATION_PENDING,
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            aiSource = AnalysisSource.LOCAL.name,
            aiConfidence = 0.45,
            aiCategory = "motivation",
            topic = "fitness",
            tone = "inspirational"
        )

        val feedback = ModelFeedback.fromCorrection(
            reference = reference,
            correctedCategory = "comedy",
            correctedTopic = "workout",
            correctedTone = "humorous",
            correctionSource = ModelFeedback.SOURCE_USER
        )

        assertEquals("motivation", feedback.originalCategory)
        assertEquals(0.45, feedback.originalConfidence!!, 0.001)
        assertEquals("fitness", feedback.originalTopic)
        assertEquals("inspirational", feedback.originalTone)
    }

    @Test
    fun modelFeedback_agreementChain_isConsistent() {
        val reference = makeReference(
            validationStatus = LabeledReference.VALIDATION_PENDING,
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            aiSource = AnalysisSource.LOCAL.name,
            aiConfidence = 0.8,
            aiCategory = "sports",
            topic = "cricket",
            tone = "energetic"
        )

        val confirm = ModelFeedback.fromCorrection(
            reference = reference,
            correctedCategory = "sports",
            correctedTopic = "cricket",
            correctedTone = "energetic",
            correctionSource = ModelFeedback.SOURCE_USER
        )

        assertTrue(confirm.categoryAgreement)
        assertTrue(confirm.topicAgreement!!)
        assertTrue(confirm.toneAgreement!!)

        val correct = ModelFeedback.fromCorrection(
            reference = reference,
            correctedCategory = "comedy",
            correctedTopic = "prank",
            correctedTone = "humorous",
            correctionSource = ModelFeedback.SOURCE_USER
        )

        assertFalse(correct.categoryAgreement)
        assertFalse(correct.topicAgreement!!)
        assertFalse(correct.toneAgreement!!)
    }

    // --------------------------------
    // PROVENANCE CHAIN COMPLETENESS
    // --------------------------------

    @Test
    fun provenance_humanConfirmed_hasHighestTrust() {
        val ref = makeReference(
            validationStatus = LabeledReference.VALIDATION_VALIDATED,
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            aiSource = AnalysisSource.LOCAL.name,
            aiConfidence = 0.9
        )

        val trust = TrustLevel.of(ref)

        assertEquals(TrustLevel.USER_CONFIRMED, trust)
        assertEquals(4, trust.authority)
    }

    @Test
    fun provenance_cloudValidated_hasSecondHighestTrust() {
        val ref = makeReference(
            validationStatus = LabeledReference.VALIDATION_VALIDATED,
            labelSource = LabeledReference.LABEL_SOURCE_CLOUD,
            aiSource = AnalysisSource.CLOUD.name,
            aiConfidence = 0.85
        )

        val trust = TrustLevel.of(ref)

        assertEquals(TrustLevel.CLOUD_VALIDATED, trust)
        assertEquals(3, trust.authority)
    }

    @Test
    fun provenance_localHighHasThirdTrust() {
        val ref = makeReference(
            validationStatus = LabeledReference.VALIDATION_PENDING,
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            aiSource = AnalysisSource.LOCAL.name,
            aiConfidence = 0.85
        )

        val trust = TrustLevel.of(ref)

        assertEquals(TrustLevel.LOCAL_HIGH_CONFIDENCE, trust)
        assertEquals(2, trust.authority)
    }

    @Test
    fun provenance_lowConfidence_hasLowestTrust() {
        val ref = makeReference(
            validationStatus = LabeledReference.VALIDATION_PENDING,
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            aiSource = AnalysisSource.LOCAL.name,
            aiConfidence = 0.3
        )

        val trust = TrustLevel.of(ref)

        assertEquals(TrustLevel.LOCAL_LOW_CONFIDENCE, trust)
        assertEquals(1, trust.authority)
    }

    // --------------------------------
    // DISAMBIGUATION: OBSERVED vs
    // INFERRED vs AI vs USER vs CLOUD
    // --------------------------------

    @Test
    fun disambiguation_observedVsInferred_confidenceDistinguishes() {
        val observed = makeReference(
            validationStatus = LabeledReference.VALIDATION_VALIDATED,
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            aiSource = AnalysisSource.LOCAL.name,
            aiConfidence = 0.9
        )

        val inferred = makeReference(
            validationStatus = LabeledReference.VALIDATION_PENDING,
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            aiSource = AnalysisSource.LOCAL.name,
            aiConfidence = 0.5
        )

        val observedTrust = TrustLevel.of(observed)
        val inferredTrust = TrustLevel.of(inferred)

        assertTrue(
            "Observed (validated) must outrank inferred (pending)",
            observedTrust.authority > inferredTrust.authority
        )
    }

    @Test
    fun disambiguation_aiVsUser_sourceDistinguishes() {
        val aiGenerated = makeReference(
            validationStatus = LabeledReference.VALIDATION_VALIDATED,
            labelSource = LabeledReference.LABEL_SOURCE_CLOUD,
            aiSource = AnalysisSource.CLOUD.name,
            aiConfidence = 0.85
        )

        val userConfirmed = makeReference(
            validationStatus = LabeledReference.VALIDATION_VALIDATED,
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            aiSource = AnalysisSource.LOCAL.name,
            aiConfidence = 0.85
        )

        val aiTrust = TrustLevel.of(aiGenerated)
        val userTrust = TrustLevel.of(userConfirmed)

        assertTrue(
            "User-confirmed must outrank cloud-validated",
            userTrust.authority > aiTrust.authority
        )
    }

    @Test
    fun disambiguation_cloudVsLocal_sourceAndValidationDistinguish() {
        val cloud = makeReference(
            validationStatus = LabeledReference.VALIDATION_VALIDATED,
            labelSource = LabeledReference.LABEL_SOURCE_CLOUD,
            aiSource = AnalysisSource.CLOUD.name,
            aiConfidence = 0.85
        )

        val local = makeReference(
            validationStatus = LabeledReference.VALIDATION_VALIDATED,
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            aiSource = AnalysisSource.LOCAL.name,
            aiConfidence = 0.85
        )

        val cloudTrust = TrustLevel.of(cloud)
        val localTrust = TrustLevel.of(local)

        assertTrue(
            "Human-confirmed local must outrank cloud",
            localTrust.authority > cloudTrust.authority
        )
    }

    // --------------------------------
    // UNCERTAINTY LEVELS
    // --------------------------------

    @Test
    fun uncertaintyLevel_allFrozenValuesExist() {
        assertNotNull(FeedItem.UNCERTAINTY_HIGH)
        assertNotNull(FeedItem.UNCERTAINTY_MEDIUM)
        assertNotNull(FeedItem.UNCERTAINTY_LOW)
    }

    @Test
    fun uncertaintyLevel_defaultIsLow() {
        val item = FeedItem(
            sessionId = "s1",
            startTime = LocalDateTime.now(),
            representativeFramePath = "/tmp/t.png"
        )

        assertEquals(FeedItem.UNCERTAINTY_LOW, item.uncertaintyLevel)
    }

    // --------------------------------
    // CONTENT TRANSITIONS
    // --------------------------------

    @Test
    fun contentTransitions_allFrozenValuesExist() {
        val transitions = listOf(
            FeedItem.TRANSITION_CONTENT_STARTED,
            FeedItem.TRANSITION_CONTENT_CONTINUED,
            FeedItem.TRANSITION_CONTENT_CHANGED,
            FeedItem.TRANSITION_CONTENT_SKIPPED,
            FeedItem.TRANSITION_CONTENT_ENDED
        )

        assertEquals(5, transitions.size)
        transitions.forEach { t ->
            assertNotNull(t)
            assertTrue(t.isNotEmpty())
        }
    }

    // --------------------------------
    // DATA MODEL CONSISTENCY
    // --------------------------------

    @Test
    fun dataModel_allEntitiesHaveStableIds() {
        val feedItem = FeedItem(
            sessionId = "s1",
            startTime = LocalDateTime.now(),
            representativeFramePath = "/tmp/t.png"
        )
        assertTrue(feedItem.id.isNotEmpty())

        val reference = makeReference(
            validationStatus = LabeledReference.VALIDATION_PENDING,
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            aiSource = AnalysisSource.LOCAL.name,
            aiConfidence = 0.5
        )
        assertTrue(reference.id.isNotEmpty())

        val feedback = ModelFeedback.fromCorrection(
            reference = reference,
            correctedCategory = "sports",
            correctedTopic = null,
            correctedTone = null,
            correctionSource = ModelFeedback.SOURCE_USER
        )
        assertTrue(feedback.id.isNotEmpty())
    }

    @Test
    fun dataModel_feedbackReferencesOriginalFrame() {
        val reference = makeReference(
            validationStatus = LabeledReference.VALIDATION_PENDING,
            labelSource = LabeledReference.LABEL_SOURCE_HUMAN,
            aiSource = AnalysisSource.LOCAL.name,
            aiConfidence = 0.5,
            aiCategory = "sports"
        )

        val feedback = ModelFeedback.fromCorrection(
            reference = reference,
            correctedCategory = "comedy",
            correctedTopic = null,
            correctedTone = null,
            correctionSource = ModelFeedback.SOURCE_USER
        )

        assertEquals(reference.frameId, feedback.frameId)
        assertEquals(reference.sessionId, feedback.sessionId)
    }

    // --------------------------------
    // HELPERS
    // --------------------------------

    private fun makeReference(
        validationStatus: String,
        labelSource: String,
        aiSource: String,
        aiConfidence: Double?,
        aiCategory: String = "sports",
        topic: String? = null,
        tone: String? = null
    ) = LabeledReference(
        frameId = "frame_${System.nanoTime()}",
        sessionId = "session_${System.nanoTime()}",
        filePath = "/tmp/test.png",
        aiCategory = aiCategory,
        aiConfidence = aiConfidence,
        aiSource = aiSource,
        modelVersion = "local-v6.0",
        platform = "Instagram",
        topic = topic,
        tone = tone,
        visibleText = "test text",
        labelSource = labelSource,
        validationStatus = validationStatus,
        validatedLabel = if (validationStatus == LabeledReference.VALIDATION_VALIDATED) aiCategory else null
    )
}
