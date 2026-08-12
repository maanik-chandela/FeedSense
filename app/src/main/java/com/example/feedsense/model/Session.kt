package com.example.feedsense.model


import java.time.LocalDateTime
import java.util.UUID

data class Session(

    val id: String = UUID.randomUUID().toString(),

    val projectId: String,

    val startTime: LocalDateTime,

    val endTime: LocalDateTime? = null,

    val durationSeconds: Long = 0,

    val totalFeedItems: Int = 0,

    val notes: String = ""

)