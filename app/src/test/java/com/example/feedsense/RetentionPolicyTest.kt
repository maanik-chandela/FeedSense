package com.example.feedsense

import com.example.feedsense.analysis.RetentionConfig
import com.example.feedsense.analysis.RetentionPolicy
import com.example.feedsense.analysis.SessionSnapshot
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Milestone 7T.
 *
 * The pure retention decisions: purge old inactive
 * sessions, trim over-cap sessions, and never touch the
 * review/training dataset.
 */
class RetentionPolicyTest {

    private val policy =
        RetentionPolicy()

    private val now =
        LocalDateTime.of(2026, 8, 17, 12, 0)

    private fun snapshot(
        sessionId: String,
        latest: LocalDateTime?,
        count: Int
    ): SessionSnapshot {
        return SessionSnapshot(
            sessionId = sessionId,
            latestCapturedAt = latest,
            frameCount = count
        )
    }

    // --------------------------------
    // PURGE
    // --------------------------------

    @Test
    fun `old inactive session is purged`() {

        val plan =
            policy.evaluate(
                sessions = listOf(
                    snapshot(
                        "old",
                        now.minusDays(40),
                        200
                    )
                ),
                activeSessionIds = emptySet(),
                now = now,
                config = RetentionConfig(
                    retentionDays = 30
                )
            )

        assertTrue("old" in plan.purgeSessions)
    }

    @Test
    fun `recent session is not purged`() {

        val plan =
            policy.evaluate(
                sessions = listOf(
                    snapshot(
                        "recent",
                        now.minusDays(2),
                        50
                    )
                ),
                activeSessionIds = emptySet(),
                now = now,
                config = RetentionConfig(
                    retentionDays = 30
                )
            )

        assertFalse("recent" in plan.purgeSessions)
    }

    @Test
    fun `session exactly at cutoff is not purged`() {

        val cutoff =
            now.minusDays(30)

        val plan =
            policy.evaluate(
                sessions = listOf(
                    snapshot(
                        "boundary",
                        cutoff,
                        10
                    )
                ),
                activeSessionIds = emptySet(),
                now = now,
                config = RetentionConfig(
                    retentionDays = 30
                )
            )

        assertFalse("boundary" in plan.purgeSessions)
    }

    @Test
    fun `active session is never purged even when old`() {

        val plan =
            policy.evaluate(
                sessions = listOf(
                    snapshot(
                        "active",
                        now.minusDays(60),
                        400
                    )
                ),
                activeSessionIds = setOf("active"),
                now = now,
                config = RetentionConfig(
                    retentionDays = 30
                )
            )

        assertFalse("active" in plan.purgeSessions)
    }

    @Test
    fun `session with no frames is not purged`() {

        val plan =
            policy.evaluate(
                sessions = listOf(
                    snapshot(
                        "empty",
                        null,
                        0
                    )
                ),
                activeSessionIds = emptySet(),
                now = now,
                config = RetentionConfig(
                    retentionDays = 30
                )
            )

        assertFalse("empty" in plan.purgeSessions)
    }

    @Test
    fun `recent session over cap is trimmed not purged`() {

        val plan =
            policy.evaluate(
                sessions = listOf(
                    snapshot(
                        "kept-but-big",
                        now.minusDays(45),
                        500
                    )
                ),
                activeSessionIds = emptySet(),
                now = now,
                config = RetentionConfig(
                    retentionDays = 60,
                    maxFramesPerSession = 100
                )
            )

        assertFalse("kept-but-big" in plan.purgeSessions)
        assertEquals(400, plan.trimBySession["kept-but-big"])
    }

    // --------------------------------
    // TRIM
    // --------------------------------

    @Test
    fun `over-cap session is trimmed by exact excess`() {

        val plan =
            policy.evaluate(
                sessions = listOf(
                    snapshot(
                        "big",
                        now.minusDays(1),
                        400
                    )
                ),
                activeSessionIds = emptySet(),
                now = now,
                config = RetentionConfig(
                    maxFramesPerSession = 300
                )
            )

        assertEquals(100, plan.trimBySession["big"])
    }

    @Test
    fun `under-cap session is not trimmed`() {

        val plan =
            policy.evaluate(
                sessions = listOf(
                    snapshot(
                        "small",
                        now.minusDays(1),
                        150
                    )
                ),
                activeSessionIds = emptySet(),
                now = now,
                config = RetentionConfig(
                    maxFramesPerSession = 300
                )
            )

        assertFalse("small" in plan.trimBySession)
    }

    @Test
    fun `session exactly at cap is not trimmed`() {

        val plan =
            policy.evaluate(
                sessions = listOf(
                    snapshot(
                        "at-cap",
                        now.minusDays(1),
                        300
                    )
                ),
                activeSessionIds = emptySet(),
                now = now,
                config = RetentionConfig(
                    maxFramesPerSession = 300
                )
            )

        assertFalse("at-cap" in plan.trimBySession)
    }

    @Test
    fun `purged session is excluded from trim`() {

        val plan =
            policy.evaluate(
                sessions = listOf(
                    snapshot(
                        "purged",
                        now.minusDays(40),
                        600
                    )
                ),
                activeSessionIds = emptySet(),
                now = now,
                config = RetentionConfig(
                    retentionDays = 30,
                    maxFramesPerSession = 100
                )
            )

        assertTrue("purged" in plan.purgeSessions)
        assertFalse("purged" in plan.trimBySession)
    }

    @Test
    fun `keep review frames flag is passed through`() {

        val plan =
            policy.evaluate(
                sessions = listOf(
                    snapshot("s", now, 10)
                ),
                activeSessionIds = emptySet(),
                now = now,
                config = RetentionConfig(
                    keepReviewFrames = false
                )
            )

        assertFalse(plan.keepReviewFrames)
    }
}
