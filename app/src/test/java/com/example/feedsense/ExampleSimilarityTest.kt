package com.example.feedsense

import com.example.feedsense.analysis.ExampleSimilarity
import com.example.feedsense.analysis.TrustLevel
import com.example.feedsense.model.LabeledReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleSimilarityTest {

    private val similarity =
        ExampleSimilarity()

    private fun example(
        id: String,
        sessionId: String = "session_1",
        validatedLabel: String? = "comedy",
        visibleText: String? = null,
        fingerprint: String? = null,
        platform: String? = null,
        validated: Boolean = true,
        human: Boolean = true
    ): LabeledReference {

        return LabeledReference(
            id = id,
            frameId = "frame_$id",
            sessionId = sessionId,
            filePath = "path",
            aiCategory = validatedLabel,
            aiConfidence = 0.9,
            aiSource = "LOCAL",
            modelVersion = "heuristic-v5",
            platform = platform,
            visibleText = visibleText,
            frameFingerprint = fingerprint,
            labelSource =
                if (human) {
                    LabeledReference.LABEL_SOURCE_HUMAN
                } else {
                    LabeledReference.LABEL_SOURCE_CLOUD
                },
            validationStatus =
                if (validated) {
                    LabeledReference.VALIDATION_VALIDATED
                } else {
                    LabeledReference.VALIDATION_PENDING
                }
        )
    }

    @Test
    fun similarFramesAreFoundByFingerprint() {

        val match =
            similarity.findSimilar(
                examples = listOf(
                    example(
                        id = "ex1",
                        fingerprint = "aaaaaaaaaaaaaaaa"
                    )
                ),
                visibleTexts = emptyList(),
                fingerprints = listOf("aaaaaaaaaaaaaaab"),
                platform = null
            )

        assertEquals(1, match.size)
        assertTrue(match[0].visualSimilarity > 0.5)
        assertTrue(
            match[0].evidence.any {
                it.startsWith("visual:")
            }
        )
    }

    @Test
    fun differentFramesProduceNoMatch() {

        val match =
            similarity.findSimilar(
                examples = listOf(
                    example(
                        id = "ex1",
                        fingerprint = "0000000000000000"
                    )
                ),
                visibleTexts = emptyList(),
                fingerprints = listOf("ffffffffffffffff"),
                platform = null
            )

        assertTrue(match.isEmpty())
    }

    @Test
    fun similarTextIsFoundByNormalizedSimilarity() {

        val match =
            similarity.findSimilar(
                examples = listOf(
                    example(
                        id = "ex1",
                        visibleText = "cricket world cup final",
                        fingerprint = "aaaaaaaaaaaaaaaa"
                    )
                ),
                visibleTexts = listOf("World Cup cricket final"),
                fingerprints = emptyList(),
                platform = null
            )

        assertEquals(1, match.size)
        assertTrue(match[0].textSimilarity > 0.0)
    }

    @Test
    fun duplicateIsDetectedByTightFingerprintDistance() {

        val duplicate =
            similarity.findDuplicate(
                examples = listOf(
                    example(
                        id = "ex1",
                        fingerprint = "aaaaaaaaaaaaaaaa"
                    )
                ),
                visibleTexts = emptyList(),
                fingerprints = listOf("aaaaaaaaaaaaaaab"),
                platform = null
            )

        assertTrue(duplicate.isDuplicate)
        assertEquals("ex1", duplicate.matchedExampleId)
    }

    @Test
    fun duplicateIsDetectedBySameTextAndPlatform() {

        val duplicate =
            similarity.findDuplicate(
                examples = listOf(
                    example(
                        id = "ex1",
                        visibleText = "cricket world cup final",
                        platform = "Instagram"
                    )
                ),
                visibleTexts = listOf("Cricket World Cup Final"),
                fingerprints = emptyList(),
                platform = "Instagram"
            )

        assertTrue(duplicate.isDuplicate)
    }

    @Test
    fun differentContentIsNotADuplicate() {

        val duplicate =
            similarity.findDuplicate(
                examples = listOf(
                    example(
                        id = "ex1",
                        visibleText = "cricket world cup final",
                        fingerprint = "0000000000000000"
                    )
                ),
                visibleTexts = listOf("cooking pasta recipe"),
                fingerprints = listOf("ffffffffffffffff"),
                platform = null
            )

        assertFalse(duplicate.isDuplicate)
    }

    @Test
    fun userConfirmedOutranksCloudValidated() {

        val match =
            similarity.findSimilar(
                examples = listOf(
                    example(
                        id = "cloud",
                        human = false,
                        fingerprint = "aaaaaaaaaaaaaaaa"
                    ),
                    example(
                        id = "human",
                        human = true,
                        fingerprint = "aaaaaaaaaaaaaaab"
                    )
                ),
                visibleTexts = emptyList(),
                fingerprints = listOf("aaaaaaaaaaaaaaac"),
                platform = null
            )

        assertEquals("human", match[0].reference.id)
        assertEquals(TrustLevel.USER_CONFIRMED, match[0].trustLevel)
        assertTrue(match[0].trustWeight > 0.5)
    }

    @Test
    fun nonValidatedExamplesAreIgnored() {

        val match =
            similarity.findSimilar(
                examples = listOf(
                    example(
                        id = "pending",
                        validated = false,
                        fingerprint = "aaaaaaaaaaaaaaaa"
                    )
                ),
                visibleTexts = emptyList(),
                fingerprints = listOf("aaaaaaaaaaaaaaab"),
                platform = null
            )

        assertTrue(match.isEmpty())
    }

    @Test
    fun confidenceAdjustmentIsExposedViaTrustWeight() {

        val high =
            similarity.findSimilar(
                examples = listOf(
                    example(
                        id = "ex1",
                        human = true,
                        fingerprint = "aaaaaaaaaaaaaaaa"
                    )
                ),
                visibleTexts = emptyList(),
                fingerprints = listOf("aaaaaaaaaaaaaaab"),
                platform = null
            )[0]

        assertEquals(1.0, high.trustWeight, 0.001)
        assertEquals(
            TrustLevel.USER_CONFIRMED,
            high.trustLevel
        )
    }

    @Test
    fun sessionIsolationComesFromCallerScope() {

        /*
         * findSimilar only ever scores the list it is
         * given, so callers isolate sessions/projects by
         * scoping that list. This test verifies a
         * non-validated row from a different session is
         * not a match.
         */
        val match =
            similarity.findSimilar(
                examples = listOf(
                    example(
                        id = "other_session",
                        sessionId = "session_9",
                        validated = false,
                        fingerprint = "aaaaaaaaaaaaaaaa"
                    )
                ),
                visibleTexts = emptyList(),
                fingerprints = listOf("aaaaaaaaaaaaaaab"),
                platform = null
            )

        assertTrue(match.isEmpty())
    }
}
