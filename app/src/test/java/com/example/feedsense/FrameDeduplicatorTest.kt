package com.example.feedsense

import com.example.feedsense.analysis.CapturedFrameLike
import com.example.feedsense.analysis.FrameDeduplicator
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 7S.
 *
 * The pure frame-dedup logic: identical screen content in
 * the same session must be analyzed once, and a reused
 * classification must carry over the classification truth
 * while keeping per-file fields current.
 */
class FrameDeduplicatorTest {

    private val deduplicator =
        FrameDeduplicator()

    private fun priorFrame(
        id: String = "frame-1",
        result: String? = "{}"
    ): CapturedFrameLike {

        return object : CapturedFrameLike {
            override val id: String = id
            override val analysisResult: String? = result
        }
    }

    // --------------------------------
    // IS REUSABLE
    // --------------------------------

    @Test
    fun `null prior is not reusable`() {

        assertFalse(
            deduplicator.isReusable(
                prior = null,
                currentFrameId = "frame-2"
            )
        )
    }

    @Test
    fun `same frame as itself is not reusable`() {

        assertFalse(
            deduplicator.isReusable(
                prior = priorFrame(id = "frame-1"),
                currentFrameId = "frame-1"
            )
        )
    }

    @Test
    fun `blank stored result is not reusable`() {

        assertFalse(
            deduplicator.isReusable(
                prior = priorFrame(result = "   "),
                currentFrameId = "frame-2"
            )
        )
    }

    @Test
    fun `analyzed prior with stored result is reusable`() {

        assertTrue(
            deduplicator.isReusable(
                prior = priorFrame(result = """{"contentCategory":"comedy"}"""),
                currentFrameId = "frame-2"
            )
        )
    }

    // --------------------------------
    // REUSE RESULT JSON
    // --------------------------------

    @Test
    fun `reused json marks status and carries classification`() {

        val prior =
            """
            {
                "status": "ANALYZED",
                "fileName": "frame_old.jpg",
                "width": 720,
                "height": 1280,
                "fileSizeBytes": 90000,
                "message": "local-only",
                "application": "com.instagram.android",
                "visibleText": "standup",
                "confidence": "HIGH",
                "classificationReason": "strong-signals",
                "contentCategory": "comedy",
                "secondaryCategories": ["variety", "sketch"],
                "topic": "standup-comedy",
                "tone": "funny",
                "contentType": "reel",
                "estimatedDurationSeconds": 30,
                "interactionSignals": "watched",
                "interactionEvidence": "watched|HIGH|10s",
                "ambiguityScore": 0.1,
                "modelVersion": "local-v5.1",
                "source": "LOCAL",
                "disposition": "keep",
                "needsReview": false,
                "uncertain": false,
                "frameFingerprint": "aabbcc"
            }
            """.trimIndent()

        val reused =
            deduplicator.reuseResultJson(
                priorResult = prior,
                fileName = "frame_new.jpg",
                fileSizeBytes = 123456,
                fingerprint = "aabbcc"
            )

        assertTrue(reused != null)

        assertEquals(
            FrameDeduplicator.STATUS_REUSED_ANALYSIS,
            reused!!.getString("status")
        )
        assertEquals("frame_new.jpg", reused.getString("fileName"))
        assertEquals(123456L, reused.getLong("fileSizeBytes"))
        assertEquals("aabbcc", reused.getString("frameFingerprint"))
        assertEquals("comedy", reused.getString("contentCategory"))
        assertEquals("standup-comedy", reused.getString("topic"))
        assertEquals("funny", reused.getString("tone"))
        assertEquals("HIGH", reused.getString("confidence"))
        assertEquals("local-v5.1", reused.getString("modelVersion"))
        assertEquals("LOCAL", reused.getString("source"))
        assertEquals("reel", reused.getString("contentType"))
        assertTrue(
            reused.getString("message")
                .contains(FrameDeduplicator.REUSED_MESSAGE_PREFIX)
        )
    }

    @Test
    fun `reused json keeps secondary categories array`() {

        val reused =
            deduplicator.reuseResultJson(
                priorResult =
                    """{"secondaryCategories":["variety","sketch"],"confidence":"MEDIUM"}""",
                fileName = "frame_new.jpg",
                fileSizeBytes = 100,
                fingerprint = "aa"
            )

        assertTrue(reused != null)

        val categories =
            reused!!.getJSONArray("secondaryCategories")

        assertEquals(2, categories.length())
        assertEquals("variety", categories.getString(0))
        assertEquals("sketch", categories.getString(1))
    }

    @Test
    fun `unparseable prior result returns null`() {

        assertNull(
            deduplicator.reuseResultJson(
                priorResult = "not-json{{{",
                fileName = "frame_new.jpg",
                fileSizeBytes = 100,
                fingerprint = "aa"
            )
        )
    }

    @Test
    fun `missing optional fields are omitted not failed`() {

        val reused =
            deduplicator.reuseResultJson(
                priorResult =
                    """{"contentCategory":"sports"}""",
                fileName = "frame_new.jpg",
                fileSizeBytes = 100,
                fingerprint = "bb"
            )

        assertTrue(reused != null)
        assertEquals("sports", reused!!.getString("contentCategory"))
        assertFalse(reused.has("topic"))
    }

    @Test
    fun `nested string list field survives round trip`() {

        val prior =
            JSONObject()
                .put("interactionSignals", "watched|liked")
                .put("confidence", "HIGH")

        val reused =
            deduplicator.reuseResultJson(
                priorResult = prior.toString(),
                fileName = "frame_new.jpg",
                fileSizeBytes = 100,
                fingerprint = "cc"
            )

        assertTrue(reused != null)
        assertEquals(
            "watched|liked",
            reused!!.getString("interactionSignals")
        )
    }
}
