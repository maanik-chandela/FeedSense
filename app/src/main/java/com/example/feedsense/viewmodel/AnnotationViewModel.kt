package com.example.feedsense.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.feedsense.analysis.AnnotationValidator
import com.example.feedsense.model.EvaluationItem
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.GroundTruth
import com.example.feedsense.repository.AnnotationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

// --------------------------------
// ANNOTATION VIEW MODEL (Milestone 8A-2)
// --------------------------------
//
// Drives both the annotation QUEUE and the single-item
// annotation FORM.
//
// A human's ground truth is ALWAYS an independent record.
// This ViewModel never writes to feed_items / ai_predictions;
// edits to the form only go to ground_truths (and recompute
// the evaluation result over the FROZEN AI snapshot).
//
// Annotator identity is pseudonymous: a random opaque id per
// ViewModel instance, with no profile data attached, so a
// human's labels can't be tied to a real person.

class AnnotationViewModel(
    private val repository: AnnotationRepository
) : ViewModel() {

    data class QueueRow(
        val item: EvaluationItem,
        val feedItem: FeedItem?
    )

    /*
     * The pseudonymous annotator. Generated once per session.
     */
    val annotatorId: String =
        "annotator-${UUID.randomUUID().toString().take(8)}"

    private val _queue = MutableStateFlow<List<QueueRow>>(emptyList())
    val queue: StateFlow<List<QueueRow>> = _queue.asStateFlow()

    private val _queueStatus =
        MutableStateFlow<String>(EvaluationItem.STATUS_NOT_EVALUATED)
    val queueStatus: StateFlow<String> = _queueStatus.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _evidence =
        MutableStateFlow<AnnotationRepository.Evidence?>(null)
    val evidence: StateFlow<AnnotationRepository.Evidence?> =
        _evidence.asStateFlow()

    private val _draft =
        MutableStateFlow<GroundTruth>(emptyDraft())
    val draft: StateFlow<GroundTruth> = _draft.asStateFlow()

    private val _saveState = MutableStateFlow("")
    val saveState: StateFlow<String> = _saveState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private fun emptyDraft(): GroundTruth = GroundTruth(
        evaluationItemId = "",

        /*
         * Every interaction defaults to UNKNOWN (null). The
         * workflow NEVER auto-fills UNKNOWN with false - the
         * evaluator must explicitly mark evidence-supported
         * signals as true/false.
         */
        ambiguity = GroundTruth.AMBIGUITY_CLEAR,
        contentType = GroundTruth.CONTENT_TYPE_UNKNOWN
    )

    fun loadQueue(
        status: String,
        sessionId: String? = null
    ) {
        viewModelScope.launch {
            _loading.value = true
            _queueStatus.value = status
            try {
                _queue.value = repository
                    .queue(status, sessionId = sessionId)
                    .map { (item, feed) -> QueueRow(item, feed) }
            } finally {
                _loading.value = false
            }
        }
    }

    /*
     * Loads a single item's evidence and pre-fills the draft
     * from any EXISTING human ground truth (re-edit).
     */
    fun loadItem(evaluationItemId: String) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val evidence =
                    repository.loadEvidence(evaluationItemId, annotatorId)
                _evidence.value = evidence
                _draft.value =
                    evidence?.currentTruth ?: emptyDraft()
            } catch (t: Throwable) {
                _error.value = t.message ?: "Failed to load item"
            } finally {
                _loading.value = false
            }
        }
    }

    // --------------------------------
    // DRAFT EDITORS
    // --------------------------------

    fun setCategory(value: String?) {
        updateDraft { it.copy(category = value) }
    }

    fun setSecondaryCategories(value: List<String>) {
        updateDraft { it.copy(secondaryCategories = value) }
    }

    fun toggleSecondaryCategory(value: String) {
        updateDraft { draft ->
            val current = draft.secondaryCategories.toMutableSet()
            if (value in current) {
                current.remove(value)
            } else {
                current.add(value)
            }
            draft.copy(secondaryCategories = current.toList())
        }
    }

    fun setAmbiguity(value: String) {
        updateDraft { it.copy(ambiguity = value) }
    }

    fun setPlatform(value: String) {
        updateDraft { it.copy(platform = value) }
    }

    fun setContentType(value: String) {
        updateDraft { it.copy(contentType = value) }
    }

    fun setSkipped(value: Boolean?) {
        updateDraft { it.copy(skipped = value) }
    }

    fun setTone(value: String) {
        updateDraft { it.copy(tone = value) }
    }

    fun setTopic(value: String) {
        updateDraft { it.copy(topic = value) }
    }

    fun setNotes(value: String) {
        updateDraft { it.copy(notes = value) }
    }

    fun setInteraction(signal: String, value: Boolean?) {
        updateDraft { draft ->
            when (signal) {
                GroundTruth.INTERACTION_LIKED -> draft.copy(liked = value)
                GroundTruth.INTERACTION_COMMENTED -> draft.copy(commented = value)
                GroundTruth.INTERACTION_SHARED -> draft.copy(shared = value)
                GroundTruth.INTERACTION_SAVED -> draft.copy(saved = value)
                GroundTruth.INTERACTION_FOLLOWED -> draft.copy(followed = value)
                GroundTruth.INTERACTION_PAUSED -> draft.copy(paused = value)
                GroundTruth.INTERACTION_PLAYING -> draft.copy(playing = value)
                else -> draft
            }
        }
    }

    fun cycleInteraction(signal: String) {
        updateDraft { draft ->
            val current = currentInteraction(draft, signal)
            val next = when (current) {
                null -> true
                true -> false
                false -> null
            }
            setInteractionInternal(draft, signal, next)
        }
    }

    private fun setInteractionInternal(
        draft: GroundTruth,
        signal: String,
        value: Boolean?
    ): GroundTruth {
        return when (signal) {
            GroundTruth.INTERACTION_LIKED -> draft.copy(liked = value)
            GroundTruth.INTERACTION_COMMENTED -> draft.copy(commented = value)
            GroundTruth.INTERACTION_SHARED -> draft.copy(shared = value)
            GroundTruth.INTERACTION_SAVED -> draft.copy(saved = value)
            GroundTruth.INTERACTION_FOLLOWED -> draft.copy(followed = value)
            GroundTruth.INTERACTION_PAUSED -> draft.copy(paused = value)
            GroundTruth.INTERACTION_PLAYING -> draft.copy(playing = value)
            else -> draft
        }
    }

    private fun currentInteraction(
        draft: GroundTruth,
        signal: String
    ): Boolean? {
        return when (signal) {
            GroundTruth.INTERACTION_LIKED -> draft.liked
            GroundTruth.INTERACTION_COMMENTED -> draft.commented
            GroundTruth.INTERACTION_SHARED -> draft.shared
            GroundTruth.INTERACTION_SAVED -> draft.saved
            GroundTruth.INTERACTION_FOLLOWED -> draft.followed
            GroundTruth.INTERACTION_PAUSED -> draft.paused
            GroundTruth.INTERACTION_PLAYING -> draft.playing
            else -> null
        }
    }

    private fun updateDraft(transform: (GroundTruth) -> GroundTruth) {
        _draft.value = transform(_draft.value)
    }

    // --------------------------------
    // SAVE / DISPUTE
    // --------------------------------

    fun save() {
        val item = _evidence.value ?: return
        viewModelScope.launch {
            _saveState.value = ""
            _error.value = null
            try {
                repository.save(
                    evaluationItemId = item.evaluationItem.id,
                    draft = _draft.value.copy(
                        evaluationItemId = item.evaluationItem.id
                    ),
                    annotatorId = annotatorId
                )
                _saveState.value =
                    "Saved as the current ground truth for this item."
                // Refresh to re-read the evaluation result.
                loadItem(item.evaluationItem.id)
            } catch (e: AnnotationValidator.ValidationException) {
                _error.value = e.errors.firstOrNull()?.message
                    ?: "Cannot save: the annotation is incomplete."
            } catch (t: Throwable) {
                _error.value = t.message ?: "Save failed"
            }
        }
    }

    fun markDisputed() {
        val item = _evidence.value ?: return
        viewModelScope.launch {
            _error.value = null
            try {
                repository.markDisputed(item.evaluationItem.id)
                _saveState.value = "Marked DISPUTED for review."
                loadItem(item.evaluationItem.id)
            } catch (t: Throwable) {
                _error.value = t.message ?: "Could not mark disputed"
            }
        }
    }

    fun clearFeedback() {
        _saveState.value = ""
        _error.value = null
    }

    /*
     * Maps the stored evaluation status to the 8A-2
     * annotation-status vocabulary.
     *
     *   NOT_EVALUATED       -> UNREVIEWED
     *   PARTIALLY_EVALUATED -> IN_PROGRESS
     *   EVALUATED           -> REVIEWED
     *   DISPUTED            -> DISPUTED
     */
    fun annotationStatusLabel(
        status: String?
    ): String {
        return when (status) {
            EvaluationItem.STATUS_NOT_EVALUATED -> "UNREVIEWED"
            EvaluationItem.STATUS_PARTIALLY_EVALUATED -> "IN_PROGRESS"
            EvaluationItem.STATUS_EVALUATED -> "REVIEWED"
            EvaluationItem.STATUS_DISPUTED -> "DISPUTED"
            else -> "UNREVIEWED"
        }
    }
}