package com.example.feedsense.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.util.UUID

// --------------------------------
// FEED ITEM
// --------------------------------
//
// Milestone 7C.
//
// One content piece watched during a session.
//
// Captured frames are grouped into feed items by
// the segmentation logic in SessionRepository.
//
// A Reel shown for ~2 seconds and skipped still
// becomes one item, so extremely short interactions
// are not missed.
//
// Fields answer the core research questions:
//
// - category       : what type of content
// - contentType    : SHORT_VIDEO / LONG_VIDEO
// - durationSeconds: how long it was on screen
// - skipped        : whether it was a short glance
//

@Entity(tableName = "feed_items")
data class FeedItem(

    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val sessionId: String,

    val startTime: LocalDateTime,

    val endTime: LocalDateTime? = null,

    val durationSeconds: Int = 0,

    val category: String? = null,

    val confidence: Double? = null,

    val topic: String? = null,

    val tone: String? = null,

    val contentType: String = CONTENT_UNKNOWN,

    val skipped: Boolean = false,

    val representativeFramePath: String,

    val frameCount: Int = 0,

    val interactionSignals: List<String> = emptyList(),

    val modelVersion: String? = null,

    val frameFingerprint: String? = null,

    val updatedAt: LocalDateTime = LocalDateTime.now()
) {

    companion object {

        const val CONTENT_UNKNOWN =
            "UNKNOWN"

        const val CONTENT_SHORT_VIDEO =
            "SHORT_VIDEO"

        const val CONTENT_LONG_VIDEO =
            "LONG_VIDEO"
    }
}
