package com.example.feedsense

import com.example.feedsense.analysis.ReferenceConfidence
import com.example.feedsense.analysis.ReferenceMemory
import com.example.feedsense.model.LabeledReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReferenceConfidenceTest {

    private val memory =
        ReferenceMemory()

    private val confidence =
        ReferenceConfidence()

    // --------------------------------
    // HELPERS
    // --------------------------------

    private fun ref(
        label: String
    ): LabeledReference {

        return LabeledReference(
            frameId = "frame_$label",
            sessionId = "session_1",
            filePath = "path",
            aiCategory = null,
            aiConfidence = null,
            aiSource = "LOCAL",
            modelVersion = "heuristic-v5",
            labelSource =
                LabeledReference.LABEL_SOURCE_HUMAN,
            validationStatus =
                LabeledReference.VALIDATION_VALIDATED,
            validatedLabel = label
        )
    }

    private fun matches(
        vararg references: LabeledReference
    ): List<ReferenceMemory.ReferenceMatch> {

        return memory.similarReferences(
            references = references.toList(),
            categoryCandidates =
                references.mapNotNull {
                    it.validatedLabel
                }.toSet(),
            platform = null,
            topic = null,
            visibleTexts = emptyList(),
            fingerprints = emptyList()
        )
    }

    // --------------------------------
    // TESTS
    // --------------------------------

    @Test
    fun supportingReference_boostsConfidence() {

        val result =
            confidence.apply(
                predicted = "comedy",
                candidates = listOf("comedy"),
                confidence = 0.6,
                matches = matches(
                    ref("comedy"),
                    ref("comedy")
                )
            )

        assertTrue(result.boosted)
        assertFalse(result.conflict)

        // 0.60 + 2 * 0.05 = 0.70
        assertEquals(
            0.70,
            result.confidence!!,
            0.001
        )

        assertEquals(2, result.supportingReferences)
    }

    @Test
    fun noMatches_leavesConfidenceUnchanged() {

        val result =
            confidence.apply(
                predicted = "comedy",
                candidates = listOf("comedy"),
                confidence = 0.6,
                matches = emptyList()
            )

        assertFalse(result.boosted)
        assertFalse(result.conflict)

        assertEquals(
            0.6,
            result.confidence!!,
            0.001
        )
    }

    @Test
    fun conflictingCategory_keepsItemUncertain() {

        val result =
            confidence.apply(
                predicted = "comedy",
                candidates =
                    listOf("comedy", "music"),
                confidence = 0.9,
                matches = matches(
                    ref("comedy"),
                    ref("music")
                )
            )

        assertTrue(result.conflict)
        assertFalse(result.boosted)

        // The classifier's number is never overwritten,
        // but the conflict is surfaced so the item stays
        // in the review queue.
        assertEquals(
            0.9,
            result.confidence!!,
            0.001
        )
    }

    @Test
    fun boostIsCappedAtOne() {

        val result =
            confidence.apply(
                predicted = "comedy",
                candidates = listOf("comedy"),
                confidence = 0.95,
                matches = matches(
                    ref("comedy"),
                    ref("comedy"),
                    ref("comedy"),
                    ref("comedy")
                )
            )

        assertTrue(result.boosted)

        // Only MAX_BOOST_REFS = 3 count.
        assertEquals(
            1.0,
            result.confidence!!,
            0.001
        )
    }

    @Test
    fun nullConfidence_neverBecomesNotNull() {

        val result =
            confidence.apply(
                predicted = "comedy",
                candidates = listOf("comedy"),
                confidence = null,
                matches = matches(
                    ref("comedy")
                )
            )

        assertNull(result.confidence)
        assertFalse(result.boosted)
    }

    @Test
    fun weakerOpposingEvidence_isNotAConflict() {

        val result =
            confidence.apply(
                predicted = "comedy",
                candidates =
                    listOf("comedy", "music"),
                confidence = 0.6,
                matches = matches(
                    ref("comedy"),
                    ref("comedy"),
                    ref("music")
                )
            )

        assertFalse(result.conflict)
        assertTrue(result.boosted)

        // 2 supporting refs beat the single music ref.
        assertEquals(
            0.70,
            result.confidence!!,
            0.001
        )
    }
}
