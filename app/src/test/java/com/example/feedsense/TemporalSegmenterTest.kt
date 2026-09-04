package com.example.feedsense

import com.example.feedsense.analysis.FrameLifecycleState
import com.example.feedsense.analysis.ItemSegment
import com.example.feedsense.analysis.SplitReason
import com.example.feedsense.analysis.TemporalSegmenter
import com.example.feedsense.model.CapturedFrame
import java.time.LocalDateTime
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporalSegmenterTest {

    private val segmenter =
        TemporalSegmenter()

    // --------------------------------
    // HELPERS
    // --------------------------------

    private fun frame(
        at: LocalDateTime,
        category: String? = null,
        fingerprint: String? = null,
        visibleText: String? = null,
        uncertain: Boolean = false,
        platform: String? = null,
        topic: String? = null
    ): CapturedFrame {

        val json =
            JSONObject()

        category?.let {
            json.put("contentCategory", it)
        }

        fingerprint?.let {
            json.put("frameFingerprint", it)
        }

        visibleText?.let {
            json.put("visibleText", it)
        }

        platform?.let {
            json.put("application", it)
        }

        topic?.let {
            json.put("topic", it)
        }

        if (uncertain) {
            json.put("uncertain", true)
        }

        return CapturedFrame(
            id = UUID.randomUUID().toString(),
            sessionId = "test-session",
            filePath = "/tmp/${UUID.randomUUID()}.png",
            capturedAt = at,
            analysisStatus = "ANALYZED",
            analysisResult = json.toString()
        )
    }

    private fun seconds(
        from: LocalDateTime,
        deltaSeconds: Long
    ): LocalDateTime {
        return from.plusSeconds(deltaSeconds)
    }

    private fun frameIds(
        segment: ItemSegment
    ): List<String> {
        return segment.frames.map { it.id }
    }

    // --------------------------------
    // TESTS
    // --------------------------------

    @Test
    fun emptyFrames_produceNoSegments() {

        assertEquals(
            emptyList<ItemSegment>(),
            segmenter.segment(emptyList())
        )
    }

    @Test
    fun singleFrame_isOneSegment() {

        val segments =
            segmenter.segment(
                listOf(
                    frame(
                        LocalDateTime.now(),
                        category = "sports"
                    )
                )
            )

        assertEquals(1, segments.size)
        assertEquals(1, segments[0].frames.size)
        assertEquals(
            FrameLifecycleState.START,
            segments[0].states[0]
        )
        assertNull(segments[0].startReason)
    }

    @Test
    fun sameCategoryCloseGap_mergesIntoOneItem() {

        val start =
            LocalDateTime.now()

        val frames =
            listOf(
                frame(
                    start,
                    category = "sports",
                    fingerprint = "0000000000000000"
                ),
                frame(
                    seconds(start, 2),
                    category = "sports",
                    fingerprint = "0000000000000000"
                ),
                frame(
                    seconds(start, 4),
                    category = "sports",
                    fingerprint = "0000000000000000"
                )
            )

        val segments =
            segmenter.segment(frames)

        assertEquals(1, segments.size)
        assertEquals(3, segments[0].frames.size)
        assertEquals(
            listOf(
                FrameLifecycleState.START,
                FrameLifecycleState.CONTINUATION,
                FrameLifecycleState.END
            ),
            segments[0].states
        )
        assertNull(segments[0].startReason)
    }

    @Test
    fun largeTimeGap_splitsEvenWithSameCategory() {

        val start =
            LocalDateTime.now()

        val frames =
            listOf(
                frame(
                    start,
                    category = "sports"
                ),
                frame(
                    seconds(start, 30),
                    category = "sports"
                )
            )

        val segments =
            segmenter.segment(frames)

        assertEquals(2, segments.size)
        assertTrue(
            segments[1].startReason ==
                    SplitReason.GAP
        )
        assertEquals(
            listOf(
                FrameLifecycleState.START
            ),
            segments[0].states
        )
        assertEquals(
            listOf(
                FrameLifecycleState.START
            ),
            segments[1].states
        )
    }

    @Test
    fun differentCategory_splitsWithCategoryReason() {

        val start =
            LocalDateTime.now()

        val frames =
            listOf(
                frame(
                    start,
                    category = "sports",
                    visibleText = "cricket match today",
                    fingerprint = "0000000000000000"
                ),
                frame(
                    seconds(start, 2),
                    category = "comedy",
                    visibleText = "funny standup clip",
                    fingerprint = "0000000000000000"
                )
            )

        val segments =
            segmenter.segment(frames)

        assertEquals(2, segments.size)
        assertEquals(
            SplitReason.CATEGORY,
            segments[1].startReason
        )
    }

    @Test
    fun differentCategory_sameText_doesNotSplit() {

        val start =
            LocalDateTime.now()

        val frames =
            listOf(
                frame(
                    start,
                    category = "sports",
                    visibleText = "breaking news today update",
                    fingerprint = "0000000000000000"
                ),
                frame(
                    seconds(start, 2),
                    category = "news",
                    visibleText = "breaking news today update",
                    fingerprint = "0000000000000000"
                )
            )

        val segments =
            segmenter.segment(frames)

        assertEquals(1, segments.size)
        assertEquals(2, segments[0].frames.size)
    }

    @Test
    fun differentFingerprint_splitsBackToBackContent() {

        val start =
            LocalDateTime.now()

        val frames =
            listOf(
                frame(
                    start,
                    category = "comedy",
                    fingerprint = "0000000000000000"
                ),
                frame(
                    seconds(start, 1),
                    category = "comedy",
                    fingerprint = "ffffffffffffffff"
                )
            )

        val segments =
            segmenter.segment(frames)

        assertEquals(2, segments.size)
        assertEquals(
            SplitReason.FINGERPRINT,
            segments[1].startReason
        )
    }

    @Test
    fun nullCategoryShortGap_merges() {

        val start =
            LocalDateTime.now()

        val frames =
            listOf(
                frame(start, category = "sports"),
                frame(
                    seconds(start, 2),
                    category = null
                )
            )

        val segments =
            segmenter.segment(frames)

        assertEquals(1, segments.size)
        assertEquals(2, segments[0].frames.size)
    }

    @Test
    fun nullCategoryLongGap_splits() {

        val start =
            LocalDateTime.now()

        val frames =
            listOf(
                frame(start, category = "sports"),
                frame(
                    seconds(start, 6),
                    category = null
                )
            )

        val segments =
            segmenter.segment(frames)

        assertEquals(2, segments.size)
    }

    @Test
    fun threePartSequence_hasCorrectLifecycleStates() {

        val start =
            LocalDateTime.now()

        val frames =
            listOf(
                frame(start, category = "sports"),
                frame(seconds(start, 2), category = "sports"),
                frame(seconds(start, 4), category = "sports"),
                frame(
                    seconds(start, 30),
                    category = "music"
                ),
                frame(
                    seconds(start, 32),
                    category = "music"
                ),
                frame(
                    seconds(start, 34),
                    category = "music"
                )
            )

        val segments =
            segmenter.segment(frames)

        assertEquals(2, segments.size)

        val first = segments[0]
        val second = segments[1]

        assertEquals(3, first.frames.size)
        assertEquals(
            listOf(
                FrameLifecycleState.START,
                FrameLifecycleState.CONTINUATION,
                FrameLifecycleState.END
            ),
            first.states
        )

        assertEquals(3, second.frames.size)
        assertEquals(
            SplitReason.GAP,
            second.startReason
        )
        assertEquals(
            listOf(
                FrameLifecycleState.START,
                FrameLifecycleState.CONTINUATION,
                FrameLifecycleState.END
            ),
            second.states
        )

        assertEquals(
            "frames are disjoint across segments",
            first.frames.none { it.id in frameIds(second) },
            true
        )
    }

    @Test
    fun uncertainFrame_stillSegments() {

        val start =
            LocalDateTime.now()

        val frames =
            listOf(
                frame(start, category = "sports"),
                frame(
                    seconds(start, 2),
                    category = "sports",
                    uncertain = true
                ),
                frame(
                    seconds(start, 4),
                    category = "sports"
                )
            )

        val segments =
            segmenter.segment(frames)

        assertEquals(1, segments.size)
        assertEquals(3, segments[0].frames.size)
        assertNotNull(segments[0].frames[1].analysisResult)
    }

    @Test
    fun platformChange_splitsWithPlatformReason() {

        val start =
            LocalDateTime.now()

        val frames =
            listOf(
                frame(
                    start,
                    category = "sports",
                    fingerprint = "0000000000000000",
                    visibleText = "cricket match today",
                    platform = "Instagram"
                ),
                frame(
                    seconds(start, 2),
                    category = "sports",
                    fingerprint = "0000000000000000",
                    visibleText = "cricket match today",
                    platform = "YouTube"
                )
            )

        val segments =
            segmenter.segment(frames)

        assertEquals(2, segments.size)
        assertEquals(
            SplitReason.PLATFORM,
            segments[1].startReason
        )
    }

    @Test
    fun sameCategory_moderateVisualChangeDifferentText_splits() {

        val start =
            LocalDateTime.now()

        val frames =
            listOf(
                frame(
                    start,
                    category = "sports",
                    fingerprint = "0000000000000000",
                    visibleText = "cricket match today"
                ),
                frame(
                    seconds(start, 2),
                    category = "sports",
                    fingerprint = "0000000000ffff00",
                    visibleText = "recipe for dinner"
                )
            )

        val segments =
            segmenter.segment(frames)

        assertEquals(2, segments.size)
        assertEquals(
            SplitReason.VISUAL_TEXT,
            segments[1].startReason
        )
    }

    @Test
    fun sameCategory_moderateVisualChangeSameText_doesNotSplit() {

        val start =
            LocalDateTime.now()

        val frames =
            listOf(
                frame(
                    start,
                    category = "sports",
                    fingerprint = "0000000000000000",
                    visibleText = "cricket match today"
                ),
                frame(
                    seconds(start, 2),
                    category = "sports",
                    fingerprint = "0000000000ffff00",
                    visibleText = "cricket match today"
                )
            )

        val segments =
            segmenter.segment(frames)

        assertEquals(1, segments.size)
        assertEquals(2, segments[0].frames.size)
    }

    @Test
    fun sameCategory_smallVisualChangeDifferentText_doesNotSplit() {

        val start =
            LocalDateTime.now()

        val frames =
            listOf(
                frame(
                    start,
                    category = "sports",
                    fingerprint = "0000000000000000",
                    visibleText = "cricket match today"
                ),
                frame(
                    seconds(start, 2),
                    category = "sports",
                    fingerprint = "0000000000000f00",
                    visibleText = "recipe for dinner"
                )
            )

        val segments =
            segmenter.segment(frames)

        assertEquals(1, segments.size)
        assertEquals(2, segments[0].frames.size)
    }

    // --------------------------------
    // MILESTONE 7X: CONTINUITY
    // --------------------------------
    //
    // Same category is not the same content. A topic
    // change or a different creator handle starts a new
    // piece; identical captions keep a piece together.

    @Test
    fun sameCategory_differentTopicDifferentText_splitsWithTopicReason() {

        val start =
            LocalDateTime.now()

        val frames =
            listOf(
                frame(
                    start,
                    category = "sports",
                    fingerprint = "0000000000000000",
                    visibleText = "cricket match today highlights",
                    topic = "cricket"
                ),
                frame(
                    seconds(start, 2),
                    category = "sports",
                    fingerprint = "0000000000000000",
                    visibleText = "football match tonight",
                    topic = "football"
                )
            )

        val segments =
            segmenter.segment(frames)

        assertEquals(2, segments.size)
        assertEquals(
            SplitReason.TOPIC,
            segments[1].startReason
        )
    }

    @Test
    fun sameCategory_differentTopicSameText_doesNotSplit() {

        val start =
            LocalDateTime.now()

        val frames =
            listOf(
                frame(
                    start,
                    category = "sports",
                    fingerprint = "0000000000000000",
                    visibleText = "cricket match today",
                    topic = "cricket"
                ),
                frame(
                    seconds(start, 2),
                    category = "sports",
                    fingerprint = "0000000000000000",
                    visibleText = "cricket match today",
                    topic = "football"
                )
            )

        // The text is identical: a single-frame topic
        // wobble must never split a stable piece.
        val segments =
            segmenter.segment(frames)

        assertEquals(1, segments.size)
        assertEquals(2, segments[0].frames.size)
    }

    @Test
    fun sameCategory_differentCreatorHandle_splitsWithCreatorReason() {

        val start =
            LocalDateTime.now()

        val frames =
            listOf(
                frame(
                    start,
                    category = "comedy",
                    fingerprint = "0000000000000000",
                    visibleText = "Follow @creatora daily pranks"
                ),
                frame(
                    seconds(start, 2),
                    category = "comedy",
                    fingerprint = "0000000000000000",
                    visibleText = "Follow @creatorb daily pranks"
                )
            )

        val segments =
            segmenter.segment(frames)

        assertEquals(2, segments.size)
        assertEquals(
            SplitReason.CREATOR,
            segments[1].startReason
        )
    }

    @Test
    fun sameCategory_sameCreatorHandle_doesNotSplit() {

        val start =
            LocalDateTime.now()

        val frames =
            listOf(
                frame(
                    start,
                    category = "comedy",
                    fingerprint = "0000000000000000",
                    visibleText = "Follow @creatora daily pranks"
                ),
                frame(
                    seconds(start, 2),
                    category = "comedy",
                    fingerprint = "0000000000000000",
                    visibleText = "Follow @creatora daily pranks"
                )
            )

        val segments =
            segmenter.segment(frames)

        assertEquals(1, segments.size)
        assertEquals(2, segments[0].frames.size)
    }

    @Test
    fun sameCategory_sameTopic_differentText_stillSplitsOnVisuals() {

        val start =
            LocalDateTime.now()

        val frames =
            listOf(
                frame(
                    start,
                    category = "comedy",
                    fingerprint = "0000000000000000",
                    visibleText = "dank meme compilation",
                    topic = "meme"
                ),
                frame(
                    seconds(start, 2),
                    category = "comedy",
                    fingerprint = "0000000000ffff00",
                    visibleText = "fresh meme drop today",
                    topic = "meme"
                )
            )

        // Same category, same topic - but the visuals
        // changed moderately and the text clearly
        // changed: back-to-back reels still split.
        val segments =
            segmenter.segment(frames)

        assertEquals(2, segments.size)
        assertEquals(
            SplitReason.VISUAL_TEXT,
            segments[1].startReason
        )
    }
}
