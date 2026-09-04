package com.example.feedsense.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.feedsense.analysis.CapturedFrameLike
import java.time.LocalDateTime
import java.util.UUID

/*
 * Milestone 7T. Indexes cover the hot query paths:
 * feed item building (sessionId + analysisStatus),
 * pending-frame loading (analysisStatus), and the
 * 7S dedup lookup (sessionId + frameFingerprint).
 */
@Entity(
    tableName = "captured_frames",
    indices = [
        Index(
            name = "idx_captured_frames_session_status",
            value = ["sessionId", "analysisStatus"]
        ),
        Index(
            name = "idx_captured_frames_status",
            value = ["analysisStatus"]
        ),
        Index(
            name = "idx_captured_frames_session_fingerprint",
            value = ["sessionId", "frameFingerprint"]
        )
    ]
)
data class CapturedFrame(
    @PrimaryKey
    override val id: String = UUID.randomUUID().toString(),

    val sessionId: String,

    val filePath: String,

    val capturedAt: LocalDateTime = LocalDateTime.now(),

    val analysisStatus: String = "PENDING",

    override val analysisResult: String? = null,

    val analyzedAt: LocalDateTime? = null,

    /*
     * Milestone 7S.
     *
     * Perceptual fingerprint (64-bit hex) stored as a
     * real column so the worker can detect "same
     * content, same session" WITHOUT parsing every
     * analysisResult JSON. Identical content is
     * analyzed once; later frames reuse the stored
     * classification.
     */
    val frameFingerprint: String? = null
) : CapturedFrameLike