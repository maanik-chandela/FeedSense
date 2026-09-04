package com.example.feedsense.model

import androidx.room.ColumnInfo
import java.time.LocalDateTime

/*
 * Milestone 7T.
 *
 * Aggregate view of one session's captured frames,
 * returned by CaptureDao.getSessionFrameStats() so the
 * retention worker can decide which sessions to purge or
 * trim without loading every frame.
 */
data class SessionFrameStats(

    val sessionId: String,

    @ColumnInfo(name = "latestCapturedAt")
    val latestCapturedAt: LocalDateTime?,

    @ColumnInfo(name = "frameCount")
    val frameCount: Int
)
