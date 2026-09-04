package com.example.feedsense.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feedsense.model.CapturedFrame
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.ResearchObservation
import com.example.feedsense.model.ResearchSession
import com.example.feedsense.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.UUID

class SessionViewModel(
    private val repository: SessionRepository
) : ViewModel() {

    // -------------------------
    // SESSIONS
    // -------------------------

    fun getSessionsForProject(
        projectId: String
    ): Flow<List<ResearchSession>> {

        return repository
            .getSessionsForProject(projectId)
    }

    fun startSession(
        projectId: String,
        title: String
    ) {

        viewModelScope.launch {

            val activeSession =
                repository
                    .getActiveSessionForProject(
                        projectId
                    )

            if (activeSession != null) {
                return@launch
            }

            val session =
                ResearchSession(
                    projectId = projectId,
                    title = title
                )

            repository.insert(
                session
            )
        }
    }

    fun endSession(
        sessionId: String
    ) {

        viewModelScope.launch {

            repository.endSession(
                sessionId
            )
        }
    }

    fun reopenSession(
        sessionId: String
    ) {

        viewModelScope.launch {

            repository.reopenSession(
                sessionId
            )
        }
    }

    // -------------------------
    // OBSERVATIONS
    // -------------------------

    fun getObservationsForSession(
        sessionId: String
    ): Flow<List<ResearchObservation>> {

        return repository
            .getObservationsForSession(
                sessionId
            )
    }

    fun addObservation(
        sessionId: String,
        text: String
    ) {

        if (text.isBlank()) {
            return
        }

        viewModelScope.launch {

            val observation =
                ResearchObservation(
                    sessionId = sessionId,
                    text = text.trim()
                )

            repository.insertObservation(
                observation
            )

            repository.incrementObservationCount(
                sessionId
            )
        }
    }

    // -------------------------
    // CAPTURED FRAMES
    // -------------------------

    fun getFramesForSession(
        sessionId: String
    ): Flow<List<CapturedFrame>> {

        return repository
            .getFramesForSession(
                sessionId
            )
    }

    fun addCapturedFrame(
        sessionId: String,
        filePath: String
    ) {

        viewModelScope.launch {

            val frame =
                CapturedFrame(
                    sessionId = sessionId,
                    filePath = filePath
                )

            repository.insertCapturedFrame(
                frame
            )
        }
    }

    /*
     * Kept for cases where we specifically need
     * the numeric count.
     */
    fun getFrameCount(
        sessionId: String,
        onResult: (Int) -> Unit
    ) {

        viewModelScope.launch {

            val count =
                repository.getFrameCount(
                    sessionId
                )

            onResult(count)
        }
    }

    // -------------------------
    // FEED ITEMS
    // -------------------------

    fun getFeedItemsForSession(
        sessionId: String
    ): Flow<List<FeedItem>> {

        return repository
            .getFeedItemsForSession(
                sessionId
            )
    }

    // --------------------------------
    // RESEARCH LOG (filtered)
    // --------------------------------
    //
    // Returns only non-chrome items for the research
    // log display. Chrome-only items are kept in the
    // database for audit but hidden from the UI.

    fun getResearchLogItems(
        sessionId: String
    ): Flow<List<FeedItem>> {

        return repository
            .getFeedItemsForSession(
                sessionId
            )
            .map { items ->
                items.filter { !it.chromeOnly }
            }
    }

    // -------------------------
    // MANUAL OBSERVATIONS (8)
    // -------------------------

    /*
     * Creates a FeedItem with source=MANUAL to keep
     * manual observations in the unified research log.
     * AI predictions and researcher observations never
     * overwrite each other.
     */
    fun addManualObservation(
        sessionId: String,
        category: String? = null,
        topic: String? = null,
        notes: String? = null,
        liked: Boolean = false,
        commented: Boolean = false,
        shared: Boolean = false,
        saved: Boolean = false
    ) {
        viewModelScope.launch {
            val item = FeedItem(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                startTime = LocalDateTime.now(),
                source = FeedItem.SOURCE_MANUAL,
                category = category,
                topic = topic,
                researcherCategory = category,
                researcherTopic = topic,
                researcherNotes = notes,
                researcherLiked = liked,
                researcherCommented = commented,
                researcherShared = shared,
                researcherSaved = saved,
                representativeFramePath = "",
                contentType = FeedItem.CONTENT_UNKNOWN,
                confidence = null,
                frameCount = 0
            )
            repository.insertFeedItem(item)
        }
    }

    /*
     * Toggles a boolean researcher flag on an existing
     * FeedItem. Used for the like button and interaction
     * toggles in the research log.
     */
    fun toggleResearcherFlag(
        itemId: String,
        field: String,
        value: Boolean
    ) {
        viewModelScope.launch {
            val item = repository.getFeedItemById(itemId) ?: return@launch
            val updated = when (field) {
                "researcherLiked" -> item.copy(researcherLiked = value)
                "researcherSkipped" -> item.copy(researcherSkipped = value)
                "researcherCommented" -> item.copy(researcherCommented = value)
                "researcherShared" -> item.copy(researcherShared = value)
                "researcherSaved" -> item.copy(researcherSaved = value)
                "researcherFollowed" -> item.copy(researcherFollowed = value)
                "researcherPaused" -> item.copy(researcherPaused = value)
                "researcherReplayed" -> item.copy(researcherReplayed = value)
                else -> return@launch
            }
            repository.updateFeedItem(updated)
        }
    }

    // -------------------------
    // FEED ITEM CORRECTION
    // -------------------------

    fun submitCorrection(
        feedItemId: String,
        category: String,
        topic: String? = null,
        tone: String? = null,
        notes: String? = null,
        liked: Boolean? = null,
        commented: Boolean? = null,
        shared: Boolean? = null,
        saved: Boolean? = null,
        skipped: Boolean? = null,
        followed: Boolean? = null,
        paused: Boolean? = null,
        replayed: Boolean? = null
    ) {
        viewModelScope.launch {
            repository.submitFeedItemCorrection(
                feedItemId = feedItemId,
                label = category,
                correctedTopic = topic,
                correctedTone = tone,
                notes = notes,
                researcherLiked = liked,
                researcherCommented = commented,
                researcherShared = shared,
                researcherSaved = saved,
                researcherSkipped = skipped,
                researcherFollowed = followed,
                researcherPaused = paused,
                researcherReplayed = replayed
            )
        }
    }

    // -------------------------
    // SESSION ANALYTICS
    // -------------------------

    fun getSessionAnalytics(
        sessionId: String,
        onResult: (SessionAnalytics) -> Unit
    ) {
        viewModelScope.launch {
            val result = repository.computeSessionAnalytics(sessionId)
            onResult(result)
        }
    }
}

data class SessionAnalytics(
    val totalItems: Int = 0,
    val aiItems: Int = 0,
    val manualItems: Int = 0,
    val totalWatchSeconds: Int = 0,
    val activeWatchSeconds: Int = 0,
    val needsReview: Int = 0,
    val likedCount: Int = 0,
    val commentedCount: Int = 0,
    val sharedCount: Int = 0,
    val savedCount: Int = 0,
    val skippedCount: Int = 0,
    val manualCorrections: Int = 0,
    val categoryBreakdown: Map<String, Int> = emptyMap(),
    val platformBreakdown: Map<String, Int> = emptyMap(),
    val contentTypeBreakdown: Map<String, Int> = emptyMap(),
    val uncertaintyBreakdown: Map<String, Int> = emptyMap()
)