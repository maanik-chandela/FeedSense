package com.example.feedsense.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.util.UUID

@Entity(tableName = "research_sessions")
data class ResearchSession(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val projectId: String,

    val title: String,

    val startedAt: LocalDateTime = LocalDateTime.now(),

    val endedAt: LocalDateTime? = null,

    val observationCount: Int = 0,

    val notes: String = "",

    val active: Boolean = true
)