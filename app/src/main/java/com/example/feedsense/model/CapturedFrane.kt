package com.example.feedsense.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.util.UUID

@Entity(tableName = "captured_frames")
data class CapturedFrame(

    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val sessionId: String,

    val filePath: String,

    val capturedAt: LocalDateTime = LocalDateTime.now(),

    val analysisStatus: String = "PENDING",

    val analysisResult: String? = null,

    val analyzedAt: LocalDateTime? = null
)