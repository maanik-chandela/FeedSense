package com.example.feedsense.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import java.util.UUID

@Entity(tableName = "research_projects")
data class ResearchProject(

    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val title: String,

    val description: String,

    val researchQuestion: String,

    val hypothesis: String,

    val platform: String,

    val owner: String,

    val createdAt: LocalDateTime = LocalDateTime.now(),

    val updatedAt: LocalDateTime = LocalDateTime.now(),

    val sessionCount: Int = 0,

    val feedItemCount: Int = 0,

    val archived: Boolean = false
)