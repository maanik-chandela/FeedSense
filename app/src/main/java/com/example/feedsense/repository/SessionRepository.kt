package com.example.feedsense.repository

import android.content.Context
import android.content.Intent
import com.example.feedsense.capture.ScreenCaptureService
import com.example.feedsense.dao.CaptureDao
import com.example.feedsense.dao.FeedItemDao
import com.example.feedsense.dao.ObservationDao
import com.example.feedsense.dao.SessionDao
import com.example.feedsense.model.CapturedFrame
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.LabeledReference
import com.example.feedsense.model.ResearchObservation
import com.example.feedsense.model.ResearchSession
import com.example.feedsense.model.SessionFrameStats
import com.example.feedsense.analysis.AnalysisSource
import com.example.feedsense.analysis.CategoryCatalog
import com.example.feedsense.analysis.ConfidenceLevel
import com.example.feedsense.analysis.FrameLifecycleState
import com.example.feedsense.analysis.InteractionDetector
import com.example.feedsense.analysis.InteractionClassifier
import com.example.feedsense.analysis.InteractionSignal
import com.example.feedsense.analysis.ItemSegment
import com.example.feedsense.analysis.ReferenceConfidence
import com.example.feedsense.analysis.ReferenceMemory
import com.example.feedsense.analysis.MixedContentAnalyzer
import com.example.feedsense.analysis.ReviewEligibility
import com.example.feedsense.analysis.TemporalSegmenter
import com.example.feedsense.worker.FeedItemScheduler
import com.example.feedsense.worker.FrameAnalysisScheduler
import com.example.feedsense.worker.ExportScheduler
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

class SessionRepository(
    private val sessionDao: SessionDao,
    private val observationDao: ObservationDao,
    private val captureDao: CaptureDao,
    private val feedItemDao: FeedItemDao,
    private val referenceRepository: ReferenceRepository,
    private val applicationContext: Context
) {
// --------------------------------
// SESSIONS
// --------------------------------

    fun getSessionsForProject(
        projectId: String
    ): Flow<List<ResearchSession>> {
        return sessionDao.getSessionsForProject(projectId)
    }

    suspend fun getLatestSession(
        projectId: String
    ): ResearchSession? {
        return sessionDao.getLatestSession(projectId)
    }

    suspend fun getActiveSessionForProject(
        projectId: String
    ): ResearchSession? {
        return sessionDao.getActiveSessionForProject(projectId)
    }

    suspend fun insert(
        session: ResearchSession
    ) {
        sessionDao.insert(session)
    }

    suspend fun update(
        session: ResearchSession
    ) {
        sessionDao.update(session)
    }

    suspend fun endSession(
        sessionId: String
    ) {
        sessionDao.endSession(
            sessionId = sessionId,
            endedAt = LocalDateTime.now()
        )

        /*
         * Milestone 7S. A session that has ended must
         * stop capturing immediately - battery and
         * correctness. The capture service receives a
         * stop command; the per-frame active-session
         * guard in the service is the safety net if the
         * command races with an in-flight frame.
         */
        stopCaptureForSession()

        /*
         * Rebuild feed items one final time so the
         * content pieces are complete even if the
         * incremental builder never ran (e.g. analysis
         * was still queued when the session ended).
         */
        FeedItemScheduler.schedule(
            applicationContext
        )

        /*
         * Milestone 7T. One-shot JSON dataset export so
         * the finished session is included in the
         * exported research record.
         */
        ExportScheduler.schedule(
            applicationContext
        )
    }

    private fun stopCaptureForSession() {

        try {

            val stopIntent =
                Intent(
                    applicationContext,
                    ScreenCaptureService::class.java
                ).setAction(
                    ScreenCaptureService.ACTION_STOP
                )

            applicationContext.startService(stopIntent)

        } catch (exception: Exception) {

            /*
             * Stopping capture must never fail the
             * session-end transaction.
             */
            exception.printStackTrace()
        }
    }

    suspend fun reopenSession(
        sessionId: String
    ) {
        sessionDao.reopenSession(
            sessionId = sessionId,
            startedAt = LocalDateTime.now()
        )
    }

// --------------------------------
// OBSERVATIONS
// --------------------------------

    fun getObservationsForSession(
        sessionId: String
    ): Flow<List<ResearchObservation>> {
        return observationDao.getObservationsForSession(
            sessionId
        )
    }

    suspend fun insertObservation(
        observation: ResearchObservation
    ) {
        observationDao.insert(observation)
    }

    suspend fun getObservationCount(
        sessionId: String
    ): Int {
        return observationDao.getObservationCount(
            sessionId
        )
    }

    suspend fun incrementObservationCount(
        sessionId: String
    ) {
        sessionDao.incrementObservationCount(
            sessionId
        )
    }

// --------------------------------
// CAPTURED FRAMES
// --------------------------------

    fun getFramesForSession(
        sessionId: String
    ): Flow<List<CapturedFrame>> {
        return captureDao.getFramesForSession(
            sessionId
        )
    }

    suspend fun insertCapturedFrame(
        frame: CapturedFrame
    ) {

        /*
         * First save the frame metadata.
         *
         * Only after the database insert succeeds
         * do we schedule analysis.
         */
        captureDao.insert(frame)

        FrameAnalysisScheduler.schedule(
            applicationContext
        )
    }

    suspend fun getPendingFrames(
        limit: Int = 10
    ): List<CapturedFrame> {
        return captureDao.getPendingFrames(
            limit
        )
    }

    suspend fun getFrameCount(
        sessionId: String
    ): Int {
        return captureDao.getFrameCount(
            sessionId
        )
    }

    /*
     * Milestone 7S. True while the session is active.
     * The capture service checks this before persisting
     * every frame so nothing is stored after the user
     * ends the session.
     */
    suspend fun isSessionActive(
        sessionId: String
    ): Boolean {
        return sessionDao.isSessionActive(
            sessionId
        ) == true
    }

// --------------------------------
// MILESTONE 7T: RETENTION
// --------------------------------

    suspend fun getSessionFrameStats():
            List<SessionFrameStats> {
        return captureDao.getSessionFrameStats()
    }

    suspend fun getDeletableFramePathsForSession(
        sessionId: String,
        limit: Int
    ): List<String> {
        return captureDao.getDeletableFramePathsForSession(
            sessionId = sessionId,
            limit = limit
        )
    }

    suspend fun getFramePathsForSession(
        sessionId: String
    ): List<String> {
        return captureDao.getFramePathsForSession(
            sessionId
        )
    }

    suspend fun deleteDeletableFramesForSession(
        sessionId: String,
        limit: Int
    ) {
        captureDao.deleteDeletableFramesForSession(
            sessionId = sessionId,
            limit = limit
        )
    }

    suspend fun deleteFramesForSession(
        sessionId: String
    ) {
        captureDao.deleteFramesForSession(
            sessionId
        )
    }

    suspend fun deleteFeedItemsForSession(
        sessionId: String
    ) {
        feedItemDao.deleteForSession(
            sessionId
        )
    }

    suspend fun deleteObservationsForSession(
        sessionId: String
    ) {
        observationDao.deleteAllForSession(
            sessionId
        )
    }

    suspend fun getActiveSessionIds(): List<String> {
        return sessionDao.getActiveSessionIds()
    }

// --------------------------------
// MILESTONE 7T: EXPORT
// --------------------------------

    suspend fun getAllSessions(): List<ResearchSession> {
        return sessionDao.getAllSessions()
    }

    suspend fun getAllItems(): List<FeedItem> {
        return feedItemDao.getAllItems()
    }

    suspend fun getAllObservations(): List<ResearchObservation> {
        return observationDao.getAll()
    }

// --------------------------------
// FRAME ANALYSIS
// --------------------------------

    suspend fun markFrameProcessing(
        frameId: String
    ) {
        captureDao.markProcessing(
            frameId
        )
    }

    suspend fun markFrameAnalyzed(
        frameId: String,
        result: String,
        fingerprint: String? = null
    ) {
        captureDao.markAnalyzed(
            frameId = frameId,
            result = result,
            analyzedAt = LocalDateTime.now(),
            fingerprint = fingerprint
        )
    }

    suspend fun markFrameFailed(
        frameId: String
    ) {
        captureDao.markFailed(
            frameId
        )
    }

    suspend fun markFrameNeedsReview(
        frameId: String,
        result: String,
        fingerprint: String? = null
    ) {
        captureDao.markNeedsReview(
            frameId = frameId,
            result = result,
            analyzedAt = LocalDateTime.now(),
            fingerprint = fingerprint
        )
    }

// --------------------------------
// MILESTONE 8B-3: FRAME FILTERED
// --------------------------------
//
// A frame that was perceptually similar to a recently
// retained frame and was not force-kept. The frame file
// is preserved for research completeness but the
// expensive analysis pipeline is skipped.
//

    suspend fun markFrameFiltered(
        frameId: String,
        result: String,
        fingerprint: String? = null
    ) {
        captureDao.markFiltered(
            frameId = frameId,
            result = result,
            analyzedAt = LocalDateTime.now(),
            fingerprint = fingerprint
        )
    }

    suspend fun resetProcessingFrames() {
        captureDao.resetProcessingFrames()
    }

    /*
     * Milestone 7S. Same content in the same session is
     * analyzed once. Returns the stored classification
     * of a previous frame whose perceptual fingerprint
     * matches, so the worker can reuse it instead of
     * running the pipeline again.
     */
    suspend fun findAnalyzedFrameByFingerprint(
        sessionId: String,
        fingerprint: String
    ): CapturedFrame? {
        return captureDao.getAnalyzedFrameByFingerprint(
            sessionId = sessionId,
            fingerprint = fingerprint
        )
    }

    suspend fun updateFrameAnalysisStatus(
        frameId: String,
        status: String
    ) {
        captureDao.updateAnalysisStatus(
            frameId = frameId,
            status = status
        )
    }

    suspend fun deleteAnalyzedFramesForSession(
        sessionId: String
    ) {
        captureDao.deleteAnalyzedFramesForSession(
            sessionId
        )
    }

    suspend fun getSessionsWithAnalyzedFrames():
            List<String> {
        return captureDao.getSessionsWithAnalyzedFrames()
    }

// --------------------------------
// FEED ITEMS
// --------------------------------
//
// Milestone 7C.
//
// Grouped content pieces built from analyzed frames.
//

    fun getFeedItemsForSession(
        sessionId: String
    ): Flow<List<FeedItem>> {
        return feedItemDao.getItemsForSession(
            sessionId
        )
    }

    /*
     * Rebuild all feed items for a session from its
     * classified frames.
     *
     * Milestone 7D:
     *
     * - Frames are segmented with the TemporalSegmenter
     *   (time + fingerprint + category + OCR continuity)
     *   instead of the old inline heuristics.
     * - NEEDS_REVIEW / REVIEWED frames are included so
     *   short, weakly-classified content never disappears.
     * - Uncertain items are kept and flagged for review;
     *   a pending item-level reference is created so the
     *   human review queue sees them.
     * - Validated references act as reference-based
     *   refinement: a human-confirmed label overrides
     *   the raw AI prediction for the same frame.
     *
     * Idempotent: items and AUTO observations are
     * deleted and regenerated from the frames.
     */
    suspend fun rebuildFeedItemsForSession(
        sessionId: String
    ) {

        val frames =
            captureDao.getFramesForItemBuilding(
                sessionId
            )

        if (frames.isEmpty()) {
            return
        }

        /*
         * Milestone 7O. Historical sessions are immutable:
         * once a session has been built, it is only
         * regenerated when NEW frames were analyzed after
         * the last build. This prevents the growing
         * cross-session memory from silently rewriting
         * already-recorded observations. Personalization
         * only affects future predictions.
         */
        val latestBuiltAt =
            feedItemDao.getLatestItemUpdatedAt(sessionId)

        if (latestBuiltAt != null) {

            val latestAnalyzedAt =
                frames
                    .mapNotNull { it.analyzedAt }
                    .maxOrNull()

            if (
                latestAnalyzedAt != null &&
                !latestAnalyzedAt.isAfter(latestBuiltAt)
            ) {
                return
            }
        }

        // --------------------------------
        // REFERENCE DATA (7D LEVEL 6)
        // --------------------------------

        val validatedByFrame =
            referenceRepository
                .getValidatedForSession(sessionId)
                .mapNotNull { reference ->
                    reference.validatedLabel?.let { label ->
                        reference.frameId to label
                    }
                }
                .toMap()

        val pendingFrameIds =
            referenceRepository
                .getPendingForSession(sessionId)
                .map { it.frameId }
                .toSet()

        /*
         * Milestone 7K (Part 3). Frame ids the reviewer
         * already handled (validated, rejected or
         * skipped). Content that has been handled must
         * not be surfaced in the review queue again.
         */
        val handledFrameIds =
            referenceRepository
                .getHandledFrameIdsForSession(sessionId)
                .toSet()

        /*
         * Milestone 7F (Part 8). Reference memory: a
         * bounded set of the most recently validated
         * labels, used to check whether the content we
         * just built was seen and human-confirmed before.
         * Loaded once per rebuild so the loop stays
         * cheap.
         *
         * Milestone 7G (Part 8). The memory is scoped to
         * THIS research project so validated labels from
         * one project never leak into another. If the
         * session has no project (defensive fallback),
         * the global memory is used.
         */
        val session =
            sessionDao.getSessionById(sessionId)

        val referenceMemory =
            session?.projectId?.let { projectId ->
                referenceRepository
                    .getValidatedForProject(
                        projectId = projectId,
                        limit = REFERENCE_MEMORY_LIMIT
                    )
            } ?: referenceRepository
                .getValidatedReferences(
                    limit = REFERENCE_MEMORY_LIMIT
                )

        /*
         * Item ids change on every rebuild, so stale
         * pending item-level references are removed
         * and recreated for the new uncertain items.
         * Validated references are never deleted.
         */
        referenceRepository
            .deletePendingItemReferencesForSession(
                sessionId
            )

        val segments =
            TemporalSegmenter()
                .segment(frames)

        // --------------------------------
        // DELETE OLD OUTPUT
        // --------------------------------

        feedItemDao.deleteForSession(
            sessionId
        )

        observationDao.deleteAutoForSession(
            sessionId
        )

        // --------------------------------
        // REGENERATE
        // --------------------------------

        for (segment in segments) {

            val platform =
                platformOf(
                    segment.frames
                )

            val item =
                buildFeedItem(
                    sessionId = sessionId,
                    segment = segment,
                    validatedByFrame =
                        validatedByFrame,
                    validatedMemory =
                        referenceMemory,
                    platform = platform
                )

            feedItemDao.insert(item)

            /*
             * Link every pending reference for this
             * item's frames (worker frame-level rows AND
             * builder item-level rows) to the item so a
             * review can correct it.
             */
            referenceRepository.assignFeedItemId(
                feedItemId = item.id,
                frameIds =
                    segment.frames.map {
                        it.id
                    }
            )

            observationDao.insert(
                ResearchObservation(
                    sessionId = sessionId,
                    text = autoObservationText(
                        item = item,
                        platform = platform
                    ),
                    source =
                        ResearchObservation.SOURCE_AUTO
                )
            )

            if (
                item.needsReview &&
                segment.frames.none {
                    it.id in pendingFrameIds
                } &&
                /*
                 * 7K Part 3: only re-queue content whose
                 * frames were not already handled. If every
                 * frame of this item was already reviewed,
                 * a new pending entry would only annoy the
                 * researcher.
                 */
                segment.frames.any {
                    it.id !in handledFrameIds
                }
            ) {

                val representativeFrame =
                    segment.frames.first()

                referenceRepository.addPendingReview(
                    frameId =
                        representativeFrame.id,
                    sessionId = sessionId,
                    filePath =
                        representativeFrame.filePath,
                    aiCategory = item.category,
                    aiConfidence = item.confidence,
                    aiSource =
                        AnalysisSource.LOCAL.name,
                    modelVersion = item.modelVersion,
                    candidateCategories =
                        item.candidateCategories,
                    labelSource =
                        LabeledReference.LABEL_SOURCE_HUMAN,
                    feedItemId = item.id,
                    platform = platform,
                    topic = item.topic,
                    tone = item.tone,
                    visibleText =
                        optString(
                            representativeFrame,
                            "visibleText"
                        ),
                    aiReason = item.classificationReason,
                    interactionSignals =
                        item.interactionSignals,
                    frameFingerprint =
                        fingerprintOf(
                            representativeFrame
                        )
                )
            }
        }

        // --------------------------------
        // RESYNC SESSION COUNT
        // --------------------------------

        sessionDao.setObservationCount(
            sessionId = sessionId,
            count =
                observationDao
                    .getObservationCount(
                        sessionId
                    )
        )
    }

// --------------------------------
// FEED ITEM CORRECTION
// --------------------------------
//
// Milestone 7D. Applies a human-confirmed category to
// a FeedItem (invoked after the reviewer validates an
// item-level reference). The corrected category sticks
// until the next rebuild, where the validated reference
// makes the correction sticky again.
//
// Milestone 7K (Part 3): the reviewer can additionally
// correct the topic and tone of the item, and the
// review-reasons/uncertainty marker is cleared.

    suspend fun getFeedItemById(
        feedItemId: String
    ): FeedItem? {
        return feedItemDao.getItemById(feedItemId)
    }

    suspend fun submitFeedItemCorrection(
        feedItemId: String,
        label: String,
        correctedTopic: String? = null,
        correctedTone: String? = null,
        notes: String? = null,
        researcherLiked: Boolean? = null,
        researcherCommented: Boolean? = null,
        researcherShared: Boolean? = null,
        researcherSaved: Boolean? = null,
        researcherSkipped: Boolean? = null,
        researcherFollowed: Boolean? = null,
        researcherPaused: Boolean? = null,
        researcherReplayed: Boolean? = null
    ) {

        val normalizedLabel =
            com.example.feedsense.analysis.CategoryCatalog
                .normalize(label)
                ?: label

        val item =
            feedItemDao.getItemById(feedItemId)
                ?: return

        feedItemDao.update(
            item.copy(
                category = normalizedLabel,
                researcherCategory = normalizedLabel,
                topic =
                    correctedTopic
                        ?.trim()
                        ?.takeIf {
                            it.isNotEmpty()
                        }
                        ?: item.topic,
                researcherTopic =
                    correctedTopic
                        ?.trim()
                        ?.takeIf {
                            it.isNotEmpty()
                        }
                        ?: item.researcherTopic,
                tone =
                    correctedTone
                        ?.trim()
                        ?.takeIf {
                            it.isNotEmpty()
                        }
                        ?: item.tone,
                researcherNotes =
                    notes
                        ?.trim()
                        ?.takeIf {
                            it.isNotEmpty()
                        }
                        ?: item.researcherNotes,
                researcherLiked =
                    researcherLiked
                        ?: item.researcherLiked,
                researcherCommented =
                    researcherCommented
                        ?: item.researcherCommented,
                researcherShared =
                    researcherShared
                        ?: item.researcherShared,
                researcherSaved =
                    researcherSaved
                        ?: item.researcherSaved,
                researcherSkipped =
                    researcherSkipped
                        ?: item.researcherSkipped,
                researcherFollowed =
                    researcherFollowed
                        ?: item.researcherFollowed,
                researcherPaused =
                    researcherPaused
                        ?: item.researcherPaused,
                researcherReplayed =
                    researcherReplayed
                        ?: item.researcherReplayed,
                needsReview = false,
                uncertaintyLevel =
                    FeedItem.UNCERTAINTY_LOW,
                interactionSignals =
                    item.interactionSignals
                        .toMutableList()
                        .apply {
                            if (
                                "human_validated"
                                !in this
                            ) {
                                add("human_validated")
                            }
                        },
                updatedAt = LocalDateTime.now()
            )
        )
    }

// ========================================
// FEED ITEM BUILDING
// ========================================

    private fun buildFeedItem(
        sessionId: String,
        segment: ItemSegment,
        validatedByFrame: Map<String, String>,
        validatedMemory: List<LabeledReference>,
        platform: String?
    ): FeedItem {

        val frames =
            segment.frames

        val startTime =
            frames.first().capturedAt

        val endTime =
            frames.last().capturedAt

        val durationSeconds =
            (
                    abs(
                        Duration
                            .between(
                                startTime,
                                endTime
                            )
                            .toSeconds()
                    ) + 1
                    ).toInt()

        // --------------------------------
        // EFFECTIVE CATEGORIES
        // --------------------------------
        //
        // A validated reference label overrides the raw
        // AI prediction for that frame (7D reference
        // refinement). Everything else falls back to the
        // stored analysis result.

        val categories =
            frames.mapNotNull { frame ->
                validatedByFrame[frame.id]
                    ?: categoryOf(frame)
            }

        val confidences =
            frames.mapNotNull {
                confidenceOf(it)
            }

        val modelVersions =
            frames.mapNotNull {
                modelVersionOf(it)
            }

        val topics =
            frames.mapNotNull {
                topicOf(it)
            }

        val tones =
            frames.mapNotNull {
                toneOf(it)
            }

        // --------------------------------
        // 7F PART 5: SECONDARY CATEGORY
        // --------------------------------
        //
        // Second-most common category across the frames,
        // so mixed content is represented honestly.

        val secondaryCandidates =
            frames.flatMap {
                secondaryCategoriesOf(it)
            }

        // --------------------------------
        // 7W: MULTI-LABEL SCORES
        // --------------------------------
        //
        // Average per-category confidence across the
        // item's frames so the scored label set survives
        // item-level. Missing scores degrade to an empty
        // map (legacy frames).

        val categoryScores =
            frames
                .flatMap {
                    categoryScoresOf(it).entries
                }
                .groupBy {
                    it.key
                }
                .mapValues { (_, entries) ->
                    entries
                        .map { it.value }
                        .average()
                }
                .takeIf {
                    it.isNotEmpty()
                }
                ?: emptyMap()

        val category =
            categories
                .groupingBy { it }
                .eachCount()
                .maxByOrNull {
                    it.value
                }
                ?.key

        val secondaryCategory =
            secondaryCandidates
                .groupingBy { it }
                .eachCount()
                .maxByOrNull {
                    it.value
                }
                ?.key
                ?.takeIf {
                    it != category
                }

        val confidence =
            confidences
                .takeIf {
                    it.isNotEmpty()
                }
                ?.average()

        /*
         * Milestone 7P. Mixed-content intelligence.
         * Decides whether this item blends multiple
         * categories and, if so, WHICH ones (ranked).
         * Runs before the review decision so mixed items
         * are flagged honestly instead of being squeezed
         * into a single label.
         */
        val mixedContentDecision =
            MixedContentAnalyzer().analyze(
                primaryCategory = category,
                confidence = confidence,
                frameCategoryCounts =
                    categories
                        .groupingBy { it }
                        .eachCount(),
                frameSecondaryCandidates =
                    secondaryCandidates
            )

        val topic =
            topics
                .groupingBy { it }
                .eachCount()
                .maxByOrNull {
                    it.value
                }
                ?.key

        val tone =
            tones
                .groupingBy { it }
                .eachCount()
                .maxByOrNull {
                    it.value
                }
                ?.key

        val skipped =
            durationSeconds <
                    SKIP_THRESHOLD_SECONDS

        val contentType =
            if (
                durationSeconds <=
                LONG_VIDEO_THRESHOLD_SECONDS
            ) {
                FeedItem.CONTENT_SHORT_VIDEO
            } else {
                FeedItem.CONTENT_LONG_VIDEO
            }

        // --------------------------------
        // 7F PART 4: WATCH BEHAVIOUR
        // --------------------------------
        //
        // durationSeconds is the wall-clock span of the
        // item. A gap between consecutive captured frames
        // much longer than the normal capture interval
        // means the screen did not change - the closest
        // honest proxy for a pause. Only intra-item gaps
        // count, and only the excess over the normal
        // interval is attributed to pausing.

        val pausedDurationSeconds =
            frames
                .zipWithNext()
                .sumOf { (previous, current) ->
                    val gapMs =
                        abs(
                            Duration
                                .between(
                                    previous.capturedAt,
                                    current.capturedAt
                                )
                                .toMillis()
                        )

                    if (gapMs > PAUSE_GAP_THRESHOLD_MS) {
                        (
                                gapMs -
                                        NORMAL_FRAME_INTERVAL_MS
                                ).coerceAtLeast(0L)
                    } else {
                        0L
                    }
                }
                .div(1_000L)
                .toInt()

        val activeWatchDurationSeconds =
            (
                    durationSeconds -
                            pausedDurationSeconds
                    ).coerceAtLeast(0)

        // --------------------------------
        // UNCERTAINTY (7D LEVEL 4)
        // --------------------------------
        //
        // The item is uncertain when any of its frames
        // was uncertain OR when the aggregated average
        // confidence is below the accept threshold.
        // Uncertain items are still stored (a short Reel
        // must never disappear) but flagged for review.
        //
        // 7F Part 6: the numeric average also maps to a
        // centralized HIGH / MEDIUM / LOW level.

        val uncertain =
            frames.any { uncertainOf(it) } ||
                    (
                            confidence != null &&
                                    confidence <
                                    ACCEPT_CONFIDENCE_THRESHOLD
                            )

        val candidates =
            buildList {

                frames.forEach { frame ->
                    addAll(
                        candidateCategoriesOf(frame)
                    )
                }

                if (
                    category != null &&
                    category !in this
                ) {
                    add(category)
                }
            }
                .distinct()
                .take(MAX_CANDIDATE_CATEGORIES)

        val referenceRefined =
            frames.any {
                validatedByFrame.containsKey(it.id)
            }

        // --------------------------------
        // 7F PART 8: REFERENCE MEMORY
        // --------------------------------
        //
        // If a human has previously validated this
        // category (ideally on the same platform), the
        // item gets a "reference_supported" signal so
        // the observation can say it agrees with past
        // labels.

        val referenceSupported =
            category != null &&
                    validatedMemory.any { reference ->
                        reference.validatedLabel == category &&
                                (
                                        platform == null ||
                                                reference.platform == null ||
                                                reference.platform == platform
                                        )
                    }

        // --------------------------------
        // 7G PART 2/3: LOCAL RETRIEVAL + CONFIDENCE
        // --------------------------------
        //
        // Reference memory is searched with the cheap
        // local signals (platform, candidate categories,
        // topic, OCR text, fingerprint). Strong matches
        // - validated human labels - refine the numeric
        // confidence:
        //
        //   - support for the prediction  -> small boost
        //   - support for another category -> conflict,
        //     the item stays uncertain for human review
        //
        // The boost is transparent: how many supporting
        // references and which signals matched are
        // recorded in the classification reason.

        val matches =
            ReferenceMemory()
                .similarReferences(
                    references = validatedMemory,
                    categoryCandidates =
                        (candidates + listOfNotNull(category))
                            .toSet(),
                    platform = platform,
                    topic = topic,
                    visibleTexts =
                        frames.mapNotNull {
                            optString(it, "visibleText")
                        },
                    fingerprints =
                        frames.mapNotNull {
                            fingerprintOf(it)
                        }
                )

        val boost =
            ReferenceConfidence().apply(
                predicted = category,
                candidates = candidates,
                confidence = confidence,
                matches = matches
            )

        val boosted =
            boost.boosted

        val conflict =
            boost.conflict

        /*
         * A conflicting reference never overrides the
         * classifier - it surfaces the disagreement by
         * keeping the item in the review queue.
         *
         * Milestone 7K (Part 2): review eligibility is
         * decided centrally, so an item is only surfaced
         * when it is genuinely uncertain: low confidence,
         * reference conflict, a category tie, mixed
         * content, ambiguous topic/tone or a platform
         * conflict. The concrete reasons are kept for the
         * classification reason / "why the AI thinks this"
         * display.
         */
        val reviewEligibility =
            ReviewEligibility().decide(
                baseUncertain = uncertain,
                conflict = conflict,
                categories = categories,
                secondaryCategory =
                    mixedContentDecision
                        .secondaryCategories
                        .firstOrNull(),
                topics = topics,
                tones = tones,
                applications =
                    frames.map {
                        applicationOf(it)
                    },
                unknownCategory =
                    category == null,
                unknownTopic =
                    topics.isEmpty(),
                unknownTone =
                    tones.isEmpty()
            )

        val needsReview =
            reviewEligibility.needsReview

        val reviewReasons =
            reviewEligibility.reasons

        val finalConfidence =
            boost.confidence

        val finalUncertaintyLevel =
            ConfidenceLevel
                .from(finalConfidence)
                .name

        // --------------------------------
        // 7F PART 2: CONTENT TRANSITIONS
        // --------------------------------

        val contentTransitions =
            buildList {

                segment.states.forEach { state ->
                    when (state) {
                        FrameLifecycleState.START ->
                            add(
                                FeedItem
                                    .TRANSITION_CONTENT_STARTED
                            )

                        FrameLifecycleState.CONTINUATION ->
                            add(
                                FeedItem
                                    .TRANSITION_CONTENT_CONTINUED
                            )

                        FrameLifecycleState.END ->
                            add(
                                FeedItem
                                    .TRANSITION_CONTENT_ENDED
                            )

                        FrameLifecycleState.CONTENT_CHANGE ->
                            add(
                                FeedItem
                                    .TRANSITION_CONTENT_CHANGED
                            )
                    }
                }

                if (skipped) {
                    add(
                        FeedItem
                            .TRANSITION_CONTENT_SKIPPED
                    )
                }
            }.distinct()

        val interactionSignals =
            buildList {

                frames.forEach { frame ->

                    addAll(
                        interactionSignalsOf(frame)
                    )
                }

                if (skipped) {
                    add("skipped")
                }

                if (referenceRefined) {
                    add("reference_refined")
                }

                if (referenceSupported) {
                    add("reference_supported")
                }

                if (boosted) {
                    add("reference_boosted")
                }
            }.distinct()

        val interactionEvidence =
            frames
                .flatMap {
                    interactionEvidenceOf(it)
                }
                .distinct()

        /*
         * Milestone 7N. Behavioral interaction detection:
         * watched/skipped/paused/resumed/stopped derived
         * from timing + lifecycle, and liked/commented/
         * shared/saved/followed ONLY when the frame OCR
         * showed an explicit affordance (HIGH confidence).
         * When in doubt -> UNKNOWN (nothing is claimed).
         */
        val behavioralInteractions =
            InteractionClassifier().classify(
                durationSeconds = durationSeconds,
                skipped = skipped,
                pausedSeconds = pausedDurationSeconds,
                transitions = contentTransitions,
                uiEvidence = interactionEvidence
            )

        val behavioralSignals =
            behavioralInteractions.map {
                it.signal
            }

        val behavioralEvidence =
            behavioralInteractions.map {
                listOf(
                    it.signal,
                    it.confidence.name,
                    it.evidence
                ).joinToString(
                    InteractionSignal.EVIDENCE_SEPARATOR
                )
            }

        val interactionSignalsFinal =
            (interactionSignals + behavioralSignals)
                .distinct()

        val interactionEvidenceFinal =
            (interactionEvidence + behavioralEvidence)
                .distinct()

        return FeedItem(
            sessionId = sessionId,
            startTime = startTime,
            endTime = endTime,
            durationSeconds = durationSeconds,
            category = category,

            /*
             * Milestone 7V. Derive the top-level domain
             * from the effective category so roll-ups can
             * group by domain without knowing every leaf.
             */
            categoryDomain =
                CategoryCatalog.domainOf(category),

            confidence = finalConfidence,
            topic = topic,
            tone = tone,
            contentType = contentType,
            skipped = skipped,
            representativeFramePath =
                frames.first().filePath,
            frameCount = frames.size,
            interactionSignals =
                interactionSignalsFinal,
            modelVersion =
                modelVersions.firstOrNull(),
            frameFingerprint =
                fingerprintOf(
                    frames.first()
                ),
            needsReview = needsReview,
            candidateCategories =
                if (needsReview) {
                    candidates
                } else {
                    emptyList()
                },
            classificationReason =
                buildClassificationReason(
                    segment = segment,
                    referenceRefined =
                        referenceRefined,
                    referenceSupported =
                        referenceSupported,
                    boosted = boosted,
                    conflict = conflict,
                    supportingReferences =
                        boost.supportingReferences,
                    reviewReasons =
                        reviewReasons
                ),
            updatedAt = LocalDateTime.now(),
            contentTransitions =
                contentTransitions,
            interactionEvidence =
                interactionEvidenceFinal,
            secondaryCategory =
                secondaryCategory,
            secondaryCategories =
                mixedContentDecision
                    .secondaryCategories,

            /*
             * Milestone 7W. Scored multi-label confidence
             * aggregated from the item's frames.
             */
            categoryScores =
                categoryScores,

            mixedContent =
                mixedContentDecision
                    .mixedContent,
            pausedDurationSeconds =
                pausedDurationSeconds,
            activeWatchDurationSeconds =
                activeWatchDurationSeconds,
            uncertaintyLevel =
                finalUncertaintyLevel,
            appContext =
                appContextOfFrames(frames),
            chromeOnly =
                frames.all {
                    chromeFrameOf(it)
                }
        )
    }

    private fun buildClassificationReason(
        segment: ItemSegment,
        referenceRefined: Boolean,
        referenceSupported: Boolean,
        boosted: Boolean,
        conflict: Boolean,
        supportingReferences: Int,
        reviewReasons: List<String>
    ): String {

        val temporal =
            segment.states
                .joinToString(",") {
                    it.name
                }

        val startReason =
            segment.startReason?.let {
                " split:$it"
            } ?: ""

        val evidence =
            segment.frames
                .mapNotNull {
                    reasonOf(it)
                }
                .distinct()
                .joinToString(
                    separator = " | "
                )

        val refinement =
            buildList {

                if (referenceRefined) {
                    add("reference-refined")
                }

                if (referenceSupported) {
                    add("reference-memory")
                }

                /*
                 * 7G Part 10. Reference-layer provenance is
                 * recorded so the evaluation screen can show
                 * why the AI thinks what it thinks.
                 */
                if (boosted) {
                    add(
                        "reference-boost-v1:" +
                                supportingReferences
                    )
                }

                if (conflict) {
                    add("reference-conflict")
                }

                /*
                 * 7K Part 2. Concrete review reasons (why
                 * this item is in the queue) are appended to
                 * the provenance string.
                 */
                reviewReasons.forEach {
                    add(it)
                }
            }.joinToString(
                separator = ","
            )

        return buildString {

            append("temporal:$temporal")

            append(startReason)

            if (evidence.isNotEmpty()) {
                append("; evidence: ")
                append(evidence)
            }

            if (refinement.isNotEmpty()) {
                append(" $refinement")
            }
        }
    }

    // ========================================
    // AUTO OBSERVATION TEXT (7F PART 9)
    // ========================================
    //
    // Observations are written like a researcher would
    // describe them, and never overclaim:
    //
    // - A short glance becomes "Encountered ... and
    //   skipped it."
    // - An uncertain item becomes "Possible X/Y content
    //   ... uncertain classification." instead of a fake
    //   confidence percentage.
    // - Human-validated reference matches are called out
    //   as consistent with past labels.

    private fun autoObservationText(
        item: FeedItem,
        platform: String?
    ): String {

        val contentType =
            when (item.contentType) {
                FeedItem.CONTENT_SHORT_VIDEO ->
                    "short video"
                FeedItem.CONTENT_LONG_VIDEO ->
                    "long video"
                else ->
                    "content"
            }

        val category =
            item.category?.let {
                CategoryCatalog.displayName(it)
            } ?: "unclassified"

        val secondary =
            item.secondaryCategory
                ?.takeIf {
                    it != item.category
                }
                ?.let {
                    CategoryCatalog.displayName(it)
                }

        val categoryLabel =
            if (secondary != null) {
                "$category/$secondary"
            } else {
                category
            }

        val platformPart =
            platform
                ?.takeIf {
                    it.isNotEmpty()
                }
                ?.let {
                    " on $it"
                }
                ?: ""

        val durationPart =
            " for ${item.durationSeconds}s"

        val extras =
            buildList {

                if (item.skipped) {
                    add("skipped it")
                }

                addAll(
                    interactionLabelsOf(
                        item.interactionSignals
                    )
                )

                item.topic?.let {
                    if (it.isNotEmpty()) {
                        add("topic=$it")
                    }
                }

                item.tone?.let {
                    if (it.isNotEmpty()) {
                        add("tone=$it")
                    }
                }
            }

        val uncertain =
            item.needsReview

        return when {

            item.skipped && uncertain ->
                "AI: Encountered $categoryLabel $contentType" +
                        "$platformPart$durationPart and " +
                        "skipped it, uncertain classification."

            item.skipped ->
                "AI: Encountered $categoryLabel $contentType" +
                        "$platformPart$durationPart and " +
                        "skipped it."

            uncertain ->
                "AI: Possible $categoryLabel $contentType" +
                        "$platformPart$durationPart, uncertain " +
                        "classification."

            else -> {
                val tail =
                    if (extras.isEmpty()) {
                        ""
                    } else {
                        ", ${extras.joinToString(", ")}"
                    }

                "AI: Watched $categoryLabel $contentType" +
                        "$platformPart$durationPart$tail"
            }
        }
    }

    // ========================================
    // INTERACTION LABELS (7E)
    // ========================================
    //
    // Map stable internal signal ids to readable
    // research labels so observations read like a
    // human would say them ("liked", "commented").
    // Unknown signals stay as-is.

    private fun interactionLabelsOf(
        signals: List<String>
    ): List<String> {

        return signals.mapNotNull { signal ->
            when (signal) {
                InteractionDetector.SIGNAL_LIKE ->
                    "liked"
                InteractionDetector.SIGNAL_COMMENT ->
                    "commented"
                InteractionDetector.SIGNAL_SHARE ->
                    "shared"
                InteractionDetector.SIGNAL_SAVE ->
                    "saved"
                InteractionDetector.SIGNAL_FOLLOW ->
                    "followed"
                InteractionDetector.SIGNAL_PAUSE ->
                    "paused"
                InteractionDetector.SIGNAL_PLAYING ->
                    "playing"
                "reference_supported" ->
                    "consistent with past labels"
                "reference_boosted" ->
                    "reinforced by past labels"
                "human_validated" ->
                    "validated by human"
                "skipped", "uncertain" -> null
                else -> signal
            }
        }
    }

// ========================================
// FRAME RESULT EXTRACTION
// ========================================

    private fun categoryOf(
        frame: CapturedFrame
    ): String? {

        val result =
            frame.analysisResult
                ?: return null

        return try {

            val json =
                JSONObject(result)

            json
                .optString(
                    "contentCategory",
                    ""
                )
                .trim()
                .takeIf {
                    it.isNotEmpty()
                }

        } catch (_: Exception) {

            null
        }
    }

    private fun confidenceOf(
        frame: CapturedFrame
    ): Double? {

        val result =
            frame.analysisResult
                ?: return null

        return try {

            val json =
                JSONObject(result)

            if (
                json.has("confidence") &&
                !json.isNull("confidence")
            ) {

                json.getDouble(
                    "confidence"
                )

            } else {
                null
            }

        } catch (_: Exception) {

            null
        }
    }

    private fun modelVersionOf(
        frame: CapturedFrame
    ): String? {

        val result =
            frame.analysisResult
                ?: return null

        return try {

            val json =
                JSONObject(result)

            json
                .optString(
                    "modelVersion",
                    ""
                )
                .trim()
                .takeIf {
                    it.isNotEmpty()
                }

        } catch (_: Exception) {

            null
        }
    }

    // ========================================
    // 7E PLATFORM EXTRACTION
    // ========================================
    //
    // The platform (Instagram, YouTube, ...) comes from
    // the "application" field of the analysis result,
    // which LocalFrameAnalyzer populates from the OCR
    // text via PlatformDetector.

    private fun applicationOf(
        frame: CapturedFrame
    ): String? {

        return optString(
            frame,
            "application"
        )
    }

    private fun platformOf(
        frames: List<CapturedFrame>
    ): String? {

        return frames
            .mapNotNull {
                applicationOf(it)
            }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull {
                it.value
            }
            ?.key
    }

    // ========================================
    // APP CHROME EXTRACTION
    // ========================================
    //
    // Extracts app context and chrome flag from the
    // analysis result JSON produced by
    // AppChromeDetector via LocalFrameAnalyzer.

    private fun appContextOf(
        frame: CapturedFrame
    ): String? {

        return optString(
            frame,
            "appContext"
        )
    }

    private fun chromeFrameOf(
        frame: CapturedFrame
    ): Boolean {

        val result =
            frame.analysisResult
                ?: return false

        return try {

            val json =
                JSONObject(result)

            json.optBoolean(
                "isChromeFrame",
                false
            )

        } catch (_: Exception) {

            false
        }
    }

    private fun appContextOfFrames(
        frames: List<CapturedFrame>
    ): String? {

        return frames
            .mapNotNull {
                appContextOf(it)
            }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull {
                it.value
            }
            ?.key
    }

    // ========================================
    // 7D EXTRACTION
    // ========================================
    //
    // topic, tone, interaction signals and the
    // perceptual fingerprint are read from the same
    // analysis result JSON produced by the worker.

    private fun topicOf(
        frame: CapturedFrame
    ): String? {

        return optString(
            frame,
            "topic"
        )
    }

    private fun toneOf(
        frame: CapturedFrame
    ): String? {

        return optString(
            frame,
            "tone"
        )
    }

    private fun fingerprintOf(
        frame: CapturedFrame
    ): String? {

        return optString(
            frame,
            "frameFingerprint"
        )
    }

    private fun optString(
        frame: CapturedFrame,
        key: String
    ): String? {

        val result =
            frame.analysisResult
                ?: return null

        return try {

            val json =
                JSONObject(result)

            json
                .optString(
                    key,
                    ""
                )
                .trim()
                .takeIf {
                    it.isNotEmpty()
                }

        } catch (_: Exception) {

            null
        }
    }

    private fun interactionSignalsOf(
        frame: CapturedFrame
    ): List<String> {

        val result =
            frame.analysisResult
                ?: return emptyList()

        return try {

            val json =
                JSONObject(result)

            if (
                !json.has("interactionSignals") ||
                json.isNull("interactionSignals")
            ) {
                return emptyList()
            }

            val array =
                json.getJSONArray(
                    "interactionSignals"
                )

            buildList {

                for (index in 0 until array.length()) {

                    val signal =
                        array
                            .optString(index)
                            .trim()

                    if (
                        signal.isNotEmpty() &&
                        !contains(signal)
                    ) {
                        add(signal)
                    }
                }
            }

        } catch (_: Exception) {

            emptyList()
        }
    }

    // ========================================
    // 7D UNCERTAINTY EXTRACTION
    // ========================================
    //
    // Uncertain frames keep the item alive but flag it
    // for review. A frame is uncertain when the analysis
    // pipeline wrote uncertain=true or needsReview=true.

    private fun uncertainOf(
        frame: CapturedFrame
    ): Boolean {

        val result =
            frame.analysisResult
                ?: return false

        return try {

            val json =
                JSONObject(result)

            json.optBoolean(
                "uncertain",
                false
            ) || json.optBoolean(
                "needsReview",
                false
            )

        } catch (_: Exception) {

            false
        }
    }

    // ========================================
    // 7F EXTRACTION
    // ========================================
    //
    // secondaryCategories and interactionEvidence are
    // read from the same analysis result JSON written by
    // the worker.

    /*
     * Milestone 7W. Scored multi-label confidence from
     * the analysis result JSON ("category" -> score).
     * Missing or malformed data degrades to an empty map.
     */
    private fun categoryScoresOf(
        frame: CapturedFrame
    ): Map<String, Double> {

        val result =
            frame.analysisResult
                ?: return emptyMap()

        return try {

            val json =
                JSONObject(result)

            if (
                !json.has("categoryScores") ||
                json.isNull("categoryScores")
            ) {
                return emptyMap()
            }

            val scores =
                json.getJSONObject(
                    "categoryScores"
                )

            buildMap {
                scores.keys().forEach { key ->
                    put(
                        key,
                        scores.optDouble(
                            key,
                            0.0
                        )
                    )
                }
            }

        } catch (_: Exception) {

            emptyMap()
        }
    }

    private fun secondaryCategoriesOf(
        frame: CapturedFrame
    ): List<String> {

        val result =
            frame.analysisResult
                ?: return emptyList()

        return try {

            val json =
                JSONObject(result)

            if (
                !json.has("secondaryCategories") ||
                json.isNull("secondaryCategories")
            ) {
                return emptyList()
            }

            val array =
                json.getJSONArray(
                    "secondaryCategories"
                )

            buildList {

                for (index in 0 until array.length()) {

                    val candidate =
                        array
                            .optString(index)
                            .trim()

                    if (
                        candidate.isNotEmpty() &&
                        !contains(candidate)
                    ) {
                        add(candidate)
                    }
                }
            }

        } catch (_: Exception) {

            emptyList()
        }
    }

    private fun interactionEvidenceOf(
        frame: CapturedFrame
    ): List<String> {

        val result =
            frame.analysisResult
                ?: return emptyList()

        return try {

            val json =
                JSONObject(result)

            if (
                !json.has("interactionEvidence") ||
                json.isNull("interactionEvidence")
            ) {
                return emptyList()
            }

            val array =
                json.getJSONArray(
                    "interactionEvidence"
                )

            buildList {

                for (index in 0 until array.length()) {

                    val entry =
                        array
                            .optString(index)
                            .trim()

                    if (
                        entry.isNotEmpty() &&
                        !contains(entry)
                    ) {
                        add(entry)
                    }
                }
            }

        } catch (_: Exception) {

            emptyList()
        }
    }

    private fun candidateCategoriesOf(
        frame: CapturedFrame
    ): List<String> {

        val result =
            frame.analysisResult
                ?: return emptyList()

        return try {

            val json =
                JSONObject(result)

            buildList {

                json
                    .optString(
                        "contentCategory",
                        ""
                    )
                    .trim()
                    .takeIf {
                        it.isNotEmpty()
                    }
                    ?.let {
                        if (!contains(it)) {
                            add(it)
                        }
                    }

                if (
                    json.has("secondaryCategories") &&
                    !json.isNull("secondaryCategories")
                ) {

                    val array =
                        json.getJSONArray(
                            "secondaryCategories"
                        )

                    for (index in 0 until array.length()) {

                        val candidate =
                            array
                                .optString(index)
                                .trim()

                        if (
                            candidate.isNotEmpty() &&
                            !contains(candidate)
                        ) {
                            add(candidate)
                        }
                    }
                }
            }

        } catch (_: Exception) {

            emptyList()
        }
    }

    private fun reasonOf(
        frame: CapturedFrame
    ): String? {

        return optString(
            frame,
            "classificationReason"
        )
    }

    // --------------------------------
    // MANUAL OBSERVATIONS (Milestone 8)
    // --------------------------------

    suspend fun insertFeedItem(item: FeedItem) {
        feedItemDao.insert(item)
    }

    suspend fun updateFeedItem(item: FeedItem) {
        feedItemDao.update(item)
    }

    suspend fun computeSessionAnalytics(
        sessionId: String
    ): com.example.feedsense.viewmodel.SessionAnalytics {
        val dao = feedItemDao
        return com.example.feedsense.viewmodel.SessionAnalytics(
            totalItems = dao.getItemCount(sessionId),
            aiItems = dao.getAICountForSession(sessionId),
            manualItems = dao.getManualCountForSession(sessionId),
            totalWatchSeconds = dao.getTotalWatchTimeForSession(sessionId) ?: 0,
            activeWatchSeconds = dao.getActiveWatchTimeForSession(sessionId) ?: 0,
            needsReview = dao.getNeedsReviewCountForSession(sessionId),
            likedCount = dao.getLikedCountForSession(sessionId),
            commentedCount = dao.getCommentedCountForSession(sessionId),
            sharedCount = dao.getSharedCountForSession(sessionId),
            savedCount = dao.getSavedCountForSession(sessionId),
            skippedCount = dao.getSkippedCountForSession(sessionId),
            manualCorrections = dao.getManualCorrectionsForSession(sessionId),
            categoryBreakdown = dao.getCategoryBreakdownForSession(sessionId)
                .associate { it.category to it.count },
            platformBreakdown = dao.getPlatformBreakdownForSession(sessionId)
                .associate { it.platform to it.count },
            contentTypeBreakdown = dao.getContentTypeBreakdownForSession(sessionId)
                .associate { it.contentType to it.count },
            uncertaintyBreakdown = dao.getUncertaintyBreakdownForSession(sessionId)
                .associate { it.uncertaintyLevel to it.count }
        )
    }

    companion object {

        /*
         * Average confidence below this marks an item
         * uncertain even when no individual frame was
         * flagged. Mirrors ConfidenceGate.DEFAULT_ACCEPT_CONFIDENCE.
         */
        private const val ACCEPT_CONFIDENCE_THRESHOLD =
            0.8

        /*
         * Maximum candidate categories stored for an
         * uncertain item.
         */
        private const val MAX_CANDIDATE_CATEGORIES =
            3

        /*
         * Shorter than this is treated as a skip.
         */
        private const val SKIP_THRESHOLD_SECONDS =
            5

        /*
         * At or below this the content is short-form.
         */
        private const val LONG_VIDEO_THRESHOLD_SECONDS =
            90

        /*
         * 7F Part 8. How many validated references to
         * load as local reference memory per rebuild.
         * Bounded so rebuilds stay cheap.
         */
        private const val REFERENCE_MEMORY_LIMIT =
            500

        /*
         * 7F Part 4. Intra-item frame gaps above this are
         * treated as a pause (the screen did not change).
         */
        private const val PAUSE_GAP_THRESHOLD_MS =
            4_000L

        /*
         * 7F Part 4. Normal interval between saved frames
         * (ScreenCaptureService throttles to ~1 frame/s).
         */
        private const val NORMAL_FRAME_INTERVAL_MS =
            1_000L
    }
}
