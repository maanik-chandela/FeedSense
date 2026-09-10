package com.example.feedsense.analysis.efficiency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 8B-11.
 *
 * FrameDeduplicator behaviour tests.
 *
 * The hasher is pinned to hashSize=1 so every frame maps to an
 * exactly-predictable 1-bit hash (0 for a uniform frame, 1 for
 * a dark/light split), giving precise control over the Hamming
 * distance (0 or 1) without any mocking framework.
 */
class FrameDeduplicatorTest {

    private var now = 0L

    private fun zeroFrame(): RawPixelFrame =
        TestFrames.solid(20, 1, 200)

    private fun oneFrame(): RawPixelFrame =
        TestFrames.halfSplit(20)

    private fun dedup(
        threshold: Int,
        minIntervalMs: Long = 0L,
        maxForwardMs: Long = Long.MAX_VALUE,
        enabled: Boolean = true
    ): FrameDeduplicator {
        now = 0L
        return FrameDeduplicator(
            FrameDeduplicationConfig(
                enabled = enabled,
                hashSize = 1,
                maxHammingDistance = threshold,
                minimumFrameIntervalMs = minIntervalMs,
                maximumForwardIntervalMs = maxForwardMs
            ),
            hasher = PerceptualHasher(
                FrameHashAlgorithm.DHASH,
                hashSize = 1
            ),
            nowMs = { now }
        )
    }

    @Test
    fun firstFrame_isAlwaysForwarded() {
        val d = dedup(threshold = 0)
        val r = d.evaluate(zeroFrame(), frameId = "frame-1")

        assertEquals(FrameDecision.UNIQUE, r.decision)
        assertEquals(FrameEvaluationReason.FIRST_FRAME, r.reason)
        assertTrue(r.forwards)
        assertEquals(1L, r.sequenceIndex)
        assertEquals("frame-1", r.frameId)
        assertNotNull(r.hash)
        assertNull(r.referenceHash)
        assertNull(r.hammingDistance)
        assertEquals(0, r.threshold)
        assertEquals(FrameDedupVersion.DEDUP_VERSION, r.configVersion)
        assertEquals(
            PerceptualHashVersion.DHASH_VERSION,
            r.algorithmVersion
        )
        assertFalse(r.forcedForward)

        val stats = d.snapshot()
        assertEquals(1L, stats.framesSeen)
        assertEquals(1L, stats.hashComputations)
        assertEquals(1L, stats.framesAccepted)
        assertEquals(0L, stats.framesRejected)
        assertEquals(1, d.retainedReferenceCount())
    }

    @Test
    fun identicalFrame_rejectedAsDuplicate() {
        val d = dedup(threshold = 0)
        val first = d.evaluate(zeroFrame())
        val second = d.evaluate(zeroFrame())

        assertEquals(FrameDecision.DUPLICATE, second.decision)
        assertEquals(FrameEvaluationReason.COMPARED, second.reason)
        assertFalse(second.forwards)
        assertEquals(0, second.hammingDistance)
        assertEquals(first.hash, second.referenceHash)
        assertEquals(second.hash, second.referenceHash)
    }

    @Test
    fun similarFrame_rejectedWithoutReplacingReference() {
        val d = dedup(threshold = 1)
        d.evaluate(zeroFrame())

        val similar = d.evaluate(oneFrame())
        assertEquals(FrameDecision.SIMILAR, similar.decision)
        assertEquals(FrameEvaluationReason.COMPARED, similar.reason)
        assertFalse(similar.forwards)
        assertEquals(1, similar.hammingDistance)

        // Reference was NOT replaced: a further identical frame
        // still compares against the original zero frame and
        // stays in the SIMILAR band (distance 1 each time).
        val again = d.evaluate(oneFrame())
        assertEquals(FrameDecision.SIMILAR, again.decision)
        assertEquals(1, again.hammingDistance)
        assertEquals(
            similar.referenceHash,
            again.referenceHash
        )
    }

    @Test
    fun differentFrame_acceptedAsUnique() {
        val d = dedup(threshold = 0)
        d.evaluate(zeroFrame())
        val different = d.evaluate(oneFrame())
        assertEquals(FrameDecision.UNIQUE, different.decision)
        assertEquals(FrameEvaluationReason.COMPARED, different.reason)
        assertTrue(different.forwards)
        assertEquals(1, different.hammingDistance)

        // The new reference is the accepted one-frame.
        val back = d.evaluate(zeroFrame())
        assertEquals(FrameDecision.UNIQUE, back.decision)
        assertEquals(1, back.hammingDistance)
    }

    @Test
    fun sequence_aaabbc_produces_U_D_D_U_D_U() {
        val d = dedup(threshold = 0)
        val expected = listOf(
            FrameDecision.UNIQUE,
            FrameDecision.DUPLICATE,
            FrameDecision.DUPLICATE,
            FrameDecision.UNIQUE,
            FrameDecision.DUPLICATE,
            FrameDecision.UNIQUE
        )
        val frames = listOf(
            zeroFrame(), zeroFrame(), zeroFrame(),
            oneFrame(), oneFrame(), zeroFrame()
        )
        frames.forEachIndexed { i, frame ->
            val r = d.evaluate(frame)
            assertEquals(
                "frame ${i + 1}",
                expected[i],
                r.decision
            )
        }

        val stats = d.snapshot()
        assertEquals(6L, stats.framesSeen)
        assertEquals(3L, stats.framesAccepted)
        assertEquals(3L, stats.framesRejected)
        assertEquals(1, d.retainedReferenceCount())
    }

    @Test
    fun classifyDistance_boundaryIsInclusiveForReject() {
        val d = dedup(threshold = 10)
        assertEquals(
            FrameDecision.DUPLICATE,
            d.classifyDistance(0, 10)
        )
        assertEquals(FrameDecision.SIMILAR, d.classifyDistance(1, 10))
        assertEquals(
            FrameDecision.SIMILAR,
            d.classifyDistance(10, 10)  // exact boundary: reject
        )
        assertEquals(FrameDecision.UNIQUE, d.classifyDistance(11, 10))
        assertEquals(
            FrameDecision.DUPLICATE,
            d.classifyDistance(0, 0)
        )
        assertEquals(FrameDecision.UNIQUE, d.classifyDistance(1, 0))
    }

    @Test
    fun forcedForward_safetyCeilingRefreshesStaticScreen() {
        val d = dedup(threshold = 0, maxForwardMs = 5000)
        val first = d.evaluate(zeroFrame())

        now = 4_999L
        val gated = d.evaluate(zeroFrame())
        assertEquals(FrameDecision.DUPLICATE, gated.decision)
        assertFalse(gated.forcedForward)

        now = 5_000L
        val forced = d.evaluate(zeroFrame())
        assertEquals(FrameDecision.UNIQUE, forced.decision)
        assertEquals(FrameEvaluationReason.FORCED_FORWARD, forced.reason)
        assertTrue(forced.forcedForward)
        assertEquals(0, forced.hammingDistance)

        val stats = d.snapshot()
        assertEquals(1L, stats.forcedForwardCount)
        assertEquals(2L, stats.framesAccepted)
        assertEquals(1L, stats.framesRejected)
        assertEquals(3L, stats.framesSeen)
        assertEquals(first.hash, forced.referenceHash)
    }

    @Test
    fun minimumInterval_rejectsCheaplyWithoutHashing() {
        val d = dedup(
            threshold = 0,
            minIntervalMs = 5000,
            maxForwardMs = Long.MAX_VALUE
        )
        d.evaluate(zeroFrame())

        now = 1_000L
        val gated = d.evaluate(oneFrame())
        assertEquals(FrameDecision.DUPLICATE, gated.decision)
        assertEquals(FrameEvaluationReason.TIME_WINDOW_GATED, gated.reason)
        assertFalse(gated.forwards)
        assertNull(gated.hash)
        assertNull(gated.hammingDistance)

        // Only the first frame was hashed so far.
        assertEquals(1L, d.snapshot().hashComputations)

        now = 6_000L
        val compared = d.evaluate(oneFrame())
        assertEquals(FrameEvaluationReason.COMPARED, compared.reason)
        assertEquals(FrameDecision.UNIQUE, compared.decision)
        assertEquals(1, compared.hammingDistance)
        assertEquals(2L, d.snapshot().hashComputations)
    }

    @Test
    fun disabled_passesEverythingThroughWithoutHashing() {
        val d = dedup(threshold = 1, enabled = false)
        repeat(5) { i ->
            val r = d.evaluate((if (i % 2 == 0) zeroFrame() else oneFrame()))
            assertEquals(FrameDecision.UNIQUE, r.decision)
            assertEquals(
                FrameEvaluationReason.DISABLED_PASSTHROUGH,
                r.reason
            )
            assertTrue(r.forwards)
            assertNull(r.hash)
        }
        val stats = d.snapshot()
        assertEquals(0L, stats.hashComputations)
        assertEquals(5L, stats.framesAccepted)
        assertEquals(0L, stats.framesRejected)
        assertEquals(5L, stats.framesSeen)
    }

    @Test
    fun reset_clearsStateAndRestartsSequence() {
        val d = dedup(threshold = 0)
        d.evaluate(zeroFrame())
        d.evaluate(oneFrame())
        assertEquals(1, d.retainedReferenceCount())

        d.reset()
        assertEquals(0, d.retainedReferenceCount())
        assertEquals(0L, d.snapshot().framesSeen)

        val after = d.evaluate(zeroFrame())
        assertEquals(FrameEvaluationReason.FIRST_FRAME, after.reason)
        assertEquals(1L, after.sequenceIndex)
    }

    @Test
    fun staticSequence_boundedState_andHighDedupRate() {
        val d = dedup(threshold = 0)
        val frame = zeroFrame()
        repeat(10_000) { d.evaluate(frame) }

        assertEquals(1, d.retainedReferenceCount())
        val stats = d.snapshot()
        assertEquals(10_000L, stats.framesSeen)
        assertEquals(1L, stats.framesAccepted)
        assertEquals(9_999L, stats.framesRejected)
        assertEquals(0.9999, stats.deduplicationRate, 1e-9)
        assertEquals(0.0001, stats.forwardRate, 1e-9)
        assertEquals(0.0, stats.averageHashDistance, 1e-9)
    }

    @Test
    fun rollingReference_tracksSlowTransitions() {
        val d = dedup(threshold = 0)
        val decisions = listOf(
            d.evaluate(zeroFrame()).decision,  // U
            d.evaluate(zeroFrame()).decision,  // D
            d.evaluate(zeroFrame()).decision,  // D
            d.evaluate(oneFrame()).decision,   // U (rolls ref)
            d.evaluate(zeroFrame()).decision   // U (compared to one)
        )
        assertEquals(
            listOf(
                FrameDecision.UNIQUE,
                FrameDecision.DUPLICATE,
                FrameDecision.DUPLICATE,
                FrameDecision.UNIQUE,
                FrameDecision.UNIQUE
            ),
            decisions
        )
    }

    @Test
    fun frameIdsArePropagatedToEveryResult() {
        val d = dedup(threshold = 0)
        val first = d.evaluate(zeroFrame(), frameId = "f1")
        val second = d.evaluate(oneFrame(), frameId = "f2")
        assertEquals("f1", first.frameId)
        assertEquals("f2", second.frameId)
        val third = d.evaluate(zeroFrame(), frameId = "f3")
        assertEquals("f3", third.frameId)
    }

    @Test
    fun configValidation_enforcesIntervalOrdering() {
        try {
            FrameDeduplicationConfig(
                minimumFrameIntervalMs = 10_000,
                maximumForwardIntervalMs = 5_000
            )
            org.junit.Assert.fail("expected IAE for min > max")
        } catch (e: IllegalArgumentException) {
            // expected
        }
        try {
            FrameDeduplicationConfig(hashSize = 0)
            org.junit.Assert.fail("expected IAE for hashSize=0")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}