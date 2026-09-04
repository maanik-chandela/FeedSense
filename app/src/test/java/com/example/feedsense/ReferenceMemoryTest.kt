package com.example.feedsense

import com.example.feedsense.analysis.PerceptualHash
import com.example.feedsense.analysis.ReferenceMemory
import com.example.feedsense.model.LabeledReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceMemoryTest {

    private val memory =
        ReferenceMemory()

    // --------------------------------
    // HELPERS
    // --------------------------------

    private fun ref(
        label: String? = "comedy",
        status: String =
            LabeledReference.VALIDATION_VALIDATED,
        platform: String? = null,
        topic: String? = null,
        visibleText: String? = null,
        fingerprint: String? = null
    ): LabeledReference {

        return LabeledReference(
            frameId = "frame_${label}_$platform",
            sessionId = "session_1",
            filePath = "path",
            aiCategory = null,
            aiConfidence = null,
            aiSource = "LOCAL",
            modelVersion = "heuristic-v5",
            platform = platform,
            topic = topic,
            visibleText = visibleText,
            frameFingerprint = fingerprint,
            labelSource =
                LabeledReference.LABEL_SOURCE_HUMAN,
            validationStatus = status,
            validatedLabel = label
        )
    }

    @Test
    fun categoryMatch_scoresAboveThreshold() {

        val result =
            memory.similarReferences(
                references =
                    listOf(
                        ref(label = "comedy")
                    ),
                categoryCandidates = setOf("comedy"),
                platform = null,
                topic = null,
                visibleTexts = emptyList(),
                fingerprints = emptyList()
            )

        assertEquals(1, result.size)

        assertEquals(
            "comedy",
            result[0].reference.validatedLabel
        )

        assertTrue(result[0].similarity >= 0.5)

        assertTrue(
            result[0].evidence.any {
                it.startsWith("category:comedy")
            }
        )
    }

    @Test
    fun unmatchedCategory_isExcluded() {

        val result =
            memory.similarReferences(
                references =
                    listOf(
                        ref(label = "music")
                    ),
                categoryCandidates = setOf("comedy"),
                platform = null,
                topic = null,
                visibleTexts = emptyList(),
                fingerprints = emptyList()
            )

        assertTrue(result.isEmpty())
    }

    @Test
    fun onlyValidatedReferencesAreReturned() {

        val result =
            memory.similarReferences(
                references =
                    listOf(
                        ref(
                            label = "comedy",
                            status =
                                LabeledReference.VALIDATION_PENDING
                        ),
                        ref(label = "comedy")
                    ),
                categoryCandidates = setOf("comedy"),
                platform = null,
                topic = null,
                visibleTexts = emptyList(),
                fingerprints = emptyList()
            )

        assertEquals(1, result.size)

        assertEquals(
            LabeledReference.VALIDATION_VALIDATED,
            result[0].reference.validationStatus
        )
    }

    @Test
    fun platformMatch_addsEvidence() {

        val result =
            memory.similarReferences(
                references =
                    listOf(
                        ref(
                            label = "comedy",
                            platform = "instagram"
                        )
                    ),
                categoryCandidates = setOf("comedy"),
                platform = "instagram",
                topic = null,
                visibleTexts = emptyList(),
                fingerprints = emptyList()
            )

        assertEquals(1, result.size)

        assertTrue(result[0].similarity > 0.6)

        assertTrue(
            result[0].evidence.any {
                it.startsWith("platform:instagram")
            }
        )
    }

    @Test
    fun textOverlap_ranksHigher() {

        val plain =
            ref(label = "comedy")

        val textual =
            ref(
                label = "comedy",
                visibleText = "funny skit prank"
            )

        val result =
            memory.similarReferences(
                references =
                    listOf(plain, textual),
                categoryCandidates = setOf("comedy"),
                platform = null,
                topic = null,
                visibleTexts =
                    listOf("funny skit"),
                fingerprints = emptyList()
            )

        assertEquals(2, result.size)

        // The reference whose visible text overlaps the
        // query ranks first.
        assertEquals(
            textual,
            result[0].reference
        )

        assertTrue(result[0].similarity > result[1].similarity)
    }

    @Test
    fun fingerprintSimilarity_boostsScore() {

        val hash =
            PerceptualHash()

        val pixels =
            IntArray(16 * 16)

        for (index in pixels.indices) {
            pixels[index] = 0xFF808080.toInt()
        }

        val base =
            hash.computeFromPixels(16, 16, pixels)!!

        // One pixel changed to a lower value -> tiny
        // visual difference that flips a gradient bit.
        val tweaked =
            pixels.toMutableList()
                .also {
                    it[5] = 0xFF000000.toInt()
                }
                .toIntArray()

        val close =
            hash.computeFromPixels(16, 16, tweaked)!!

        val distance =
            hash.hammingDistance(base, close)!!

        assertTrue(distance < 8)

        val result =
            memory.similarReferences(
                references =
                    listOf(
                        ref(
                            label = "comedy",
                            fingerprint = base
                        )
                    ),
                categoryCandidates = setOf("comedy"),
                platform = null,
                topic = null,
                visibleTexts = emptyList(),
                fingerprints = listOf(close)
            )

        assertEquals(1, result.size)

        assertTrue(result[0].similarity > 0.6)

        assertTrue(
            result[0].evidence.any {
                it.startsWith("visual:")
            }
        )
    }

    @Test
    fun limitCapsResults() {

        val references =
            (1..12).map {
                ref(label = "comedy")
            }

        val result =
            memory.similarReferences(
                references = references,
                categoryCandidates = setOf("comedy"),
                platform = null,
                topic = null,
                visibleTexts = emptyList(),
                fingerprints = emptyList(),
                limit = 3
            )

        assertEquals(3, result.size)
    }

    @Test
    fun conflictingLabels_bothRankedByScore() {

        val result =
            memory.similarReferences(
                references =
                    listOf(
                        ref(label = "comedy"),
                        ref(label = "music")
                    ),
                categoryCandidates =
                    setOf("comedy", "music"),
                platform = null,
                topic = null,
                visibleTexts = emptyList(),
                fingerprints = emptyList()
            )

        assertEquals(2, result.size)

        // Identical scores: either order is fine, but
        // both labels must be present.
        val labels =
            result.map {
                it.reference.validatedLabel
            }

        assertTrue(
            labels.contains("comedy") &&
                    labels.contains("music")
        )
    }
}
