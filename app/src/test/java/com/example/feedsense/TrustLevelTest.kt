package com.example.feedsense

import com.example.feedsense.analysis.AnalysisSource
import com.example.feedsense.analysis.TrustLevel
import com.example.feedsense.model.LabeledReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustLevelTest {

    private fun reference(
        validationStatus: String,
        labelSource: String,
        aiSource: String = AnalysisSource.LOCAL.name,
        aiConfidence: Double? = null
    ): LabeledReference {

        return LabeledReference(
            frameId = "frame",
            sessionId = "session",
            filePath = "path",
            aiCategory = "comedy",
            aiConfidence = aiConfidence,
            aiSource = aiSource,
            modelVersion = "heuristic-v5",
            labelSource = labelSource,
            validationStatus = validationStatus
        )
    }

    @Test
    fun validatedHumanLabelIsUserConfirmed() {

        val trust =
            TrustLevel.of(
                reference(
                    validationStatus =
                        LabeledReference.VALIDATION_VALIDATED,
                    labelSource =
                        LabeledReference.LABEL_SOURCE_HUMAN
                )
            )

        assertEquals(TrustLevel.USER_CONFIRMED, trust)
    }

    @Test
    fun validatedCloudLabelIsCloudValidated() {

        val trust =
            TrustLevel.of(
                reference(
                    validationStatus =
                        LabeledReference.VALIDATION_VALIDATED,
                    labelSource =
                        LabeledReference.LABEL_SOURCE_CLOUD
                )
            )

        assertEquals(TrustLevel.CLOUD_VALIDATED, trust)
    }

    @Test
    fun pendingHighConfidenceLocalIsHighTrust() {

        val trust =
            TrustLevel.of(
                reference(
                    validationStatus =
                        LabeledReference.VALIDATION_PENDING,
                    labelSource =
                        LabeledReference.LABEL_SOURCE_HUMAN,
                    aiSource = AnalysisSource.LOCAL.name,
                    aiConfidence = 0.9
                )
            )

        assertEquals(TrustLevel.LOCAL_HIGH_CONFIDENCE, trust)
    }

    @Test
    fun pendingLowConfidenceIsLowTrust() {

        val trust =
            TrustLevel.of(
                reference(
                    validationStatus =
                        LabeledReference.VALIDATION_PENDING,
                    labelSource =
                        LabeledReference.LABEL_SOURCE_HUMAN,
                    aiSource = AnalysisSource.LOCAL.name,
                    aiConfidence = 0.4
                )
            )

        assertEquals(TrustLevel.LOCAL_LOW_CONFIDENCE, trust)
    }

    @Test
    fun pendingCloudGuessIsNeverTrusted() {

        val trust =
            TrustLevel.of(
                reference(
                    validationStatus =
                        LabeledReference.VALIDATION_PENDING,
                    labelSource =
                        LabeledReference.LABEL_SOURCE_CLOUD,
                    aiSource = AnalysisSource.CLOUD.name,
                    aiConfidence = 0.95
                )
            )

        assertEquals(TrustLevel.LOCAL_LOW_CONFIDENCE, trust)
    }

    @Test
    fun authorityOrderMatchesSpec() {

        assertTrue(
            TrustLevel.USER_CONFIRMED.authority >
                TrustLevel.CLOUD_VALIDATED.authority
        )

        assertTrue(
            TrustLevel.CLOUD_VALIDATED.authority >
                TrustLevel.LOCAL_HIGH_CONFIDENCE.authority
        )

        assertTrue(
            TrustLevel.LOCAL_HIGH_CONFIDENCE.authority >
                TrustLevel.LOCAL_LOW_CONFIDENCE.authority
        )
    }

    @Test
    fun weightScalesWithAuthority() {

        assertEquals(1.0, TrustLevel.weight(TrustLevel.USER_CONFIRMED), 0.001)
        assertEquals(0.75, TrustLevel.weight(TrustLevel.CLOUD_VALIDATED), 0.001)
        assertEquals(0.5, TrustLevel.weight(TrustLevel.LOCAL_HIGH_CONFIDENCE), 0.001)
        assertEquals(0.25, TrustLevel.weight(TrustLevel.LOCAL_LOW_CONFIDENCE), 0.001)
    }
}
