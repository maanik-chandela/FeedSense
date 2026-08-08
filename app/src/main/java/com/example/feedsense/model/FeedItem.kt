package com.example.feedsense.model

import java.time.LocalDateTime
import java.util.UUID

data class FeedItem(

    val id: String = UUID.randomUUID().toString(),

    val sessionId: String,

    val timestamp: LocalDateTime,

    val platform: String,

    val watchTimeSeconds: Double,

    val screenshotPath: String,

    val ocrText: String = ""

)