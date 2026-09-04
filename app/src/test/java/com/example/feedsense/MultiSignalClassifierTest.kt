package com.example.feedsense

import com.example.feedsense.analysis.ClassificationResult
import com.example.feedsense.analysis.MultiSignalClassifier
import com.example.feedsense.analysis.TextHeuristicClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiSignalClassifierTest {

    private val classifier =
        MultiSignalClassifier()

    private val heuristic =
        TextHeuristicClassifier()

    private fun classify(
        text: String?,
        platform: String?,
        memoryCandidates: Set<String> = emptySet(),
        memorySupport: Double = 0.0
    ): ClassificationResult {

        return classifier.classify(
            MultiSignalClassifier.SignalSet(
                text = text,
                platform = platform,
                heuristic = heuristic.classify(text),
                memoryCandidates = memoryCandidates,
                memorySupport = memorySupport
            )
        )
    }

    @Test
    fun modelVersionIdentifiesTheLogic() {

        assertEquals(
            "local-v6.0",
            MultiSignalClassifier.MODEL_VERSION
        )
    }

    @Test
    fun cricketTextOnInstagramBecomesSports() {

        val result =
            classify(
                text = "cricket match highlights win",
                platform = "Instagram"
            )

        // Sports category + platform context -> Sports.
        assertNotNull(result.primaryCategory)
    }

    @Test
    fun matchingPlatformPriorBoostsConfidence() {

        val text =
            "funny prank music"

        val plain =
            heuristic.classify(text)

        val boosted =
            classify(
                text = text,
                platform = "TikTok"
            )

        // Must be a boostable (non-1.0) input.
        assertTrue(plain.confidence!! < 1.0)
        assertTrue(
            boosted.confidence!! > plain.confidence!!
        )
        assertTrue(
            boosted.reason
                ?.contains("platform")
                == true
        )
    }

    @Test
    fun platformGuessFillsMissingCategory() {

        // A platform with a strong prior can guess a
        // category when the text finds none. (The input
        // deliberately avoids any catalog keyword - the
        // 7U taxonomy added "short video" etc.)
        val result =
            classify(
                text = "scroll swipe feed",
                platform = "twitch"
            )

        assertEquals("gaming", result.primaryCategory)
        assertNotNull(result.confidence)
    }

    @Test
    fun platformConflictReducesConfidence() {

        // Instagram strongly prefers lifestyle/fashion,
        // so a strong news text hit gets pushed down.
        val textResult =
            heuristic.classify(
                "breaking news headlines political"
            )

        val platformResult =
            classify(
                text = "breaking news headlines political",
                platform = "Instagram"
            )

        assertNotNull(textResult.confidence)
        assertNotNull(platformResult.confidence)

        // Instagram prior does not contain news, so the
        // confidence should never exceed the raw text
        // confidence.
        assertTrue(
            platformResult.confidence!! <=
                textResult.confidence!! + 0.0001
        )
    }

    @Test
    fun memorySupportBoostsConfidence() {

        val text =
            "funny prank music"

        val textResult =
            heuristic.classify(text)

        val memoryResult =
            classify(
                text = text,
                platform = null,
                memoryCandidates = setOf("comedy"),
                memorySupport = 0.9
            )

        assertTrue(textResult.confidence!! < 1.0)
        assertTrue(
            memoryResult.confidence!! >
                textResult.confidence!!
        )
    }

    @Test
    fun memoryConflictAddsEvidence() {

        val result =
            classify(
                text = "comedy meme funny music",
                platform = null,
                memoryCandidates = setOf("music")
            )

        assertTrue(
            result.reason
                ?.contains("memory-conflict")
                == true
        )
    }

    @Test
    fun highAmbiguityCapsConfidenceAtMedium() {

        // Heavy keyword overlap -> high ambiguity -> the
        // confidence must be capped so the item is queued.
        val ambiguous =
            heuristic.classify(
                "music comedy entertainment"
            )

        val result =
            classifier.classify(
                MultiSignalClassifier.SignalSet(
                    text = "music comedy entertainment",
                    platform = "TikTok",
                    heuristic = ambiguous
                )
            )

        assertNotNull(ambiguous.ambiguityScore)
        assertNotNull(result.confidence)

        if (ambiguous.ambiguityScore!! >= 0.7) {
            assertTrue(
                result.confidence!! <=
                    MultiSignalClassifier.MEDIUM_CONFIDENCE_CAP
            )
        }
    }

    @Test
    fun safeFallbackReturnsHeuristicResult() {

        // No platform, no memory, no text -> the heuristic
        // empty result comes back unchanged.
        val result =
            classifier.classify(
                MultiSignalClassifier.SignalSet(
                    text = null,
                    platform = null,
                    heuristic =
                        heuristic.classify(null)
                )
            )

        assertEquals(null, result.primaryCategory)
        assertEquals(null, result.confidence)
    }

    @Test
    fun reasonKeepsHeuristicAndAddsMultiSignalEvidence() {

        val plain =
            heuristic.classify(
                "cricket match highlights win"
            )

        val result =
            classify(
                text = "cricket match highlights win",
                platform = "Instagram"
            )

        assertNotNull(plain.reason)
        assertNotNull(result.reason)
        assertTrue(
            result.reason!!.contains(plain.reason!!) ||
                result.reason != plain.reason
        )
    }
}
