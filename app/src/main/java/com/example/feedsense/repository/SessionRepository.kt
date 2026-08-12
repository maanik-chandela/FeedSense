package com.example.feedsense.repository

import android.content.Context
import com.example.feedsense.dao.CaptureDao
import com.example.feedsense.dao.FeedItemDao
import com.example.feedsense.dao.ObservationDao
import com.example.feedsense.dao.SessionDao
import com.example.feedsense.model.CapturedFrame
import com.example.feedsense.model.FeedItem
import com.example.feedsense.model.ResearchObservation
import com.example.feedsense.model.ResearchSession
import com.example.feedsense.analysis.PerceptualHash
import com.example.feedsense.worker.FrameAnalysisScheduler
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
        result: String
    ) {
        captureDao.markAnalyzed(
            frameId = frameId,
            result = result,
            analyzedAt = LocalDateTime.now()
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
        result: String
    ) {
        captureDao.markNeedsReview(
            frameId = frameId,
            result = result,
            analyzedAt = LocalDateTime.now()
        )
    }

    suspend fun resetProcessingFrames() {
        captureDao.resetProcessingFrames()
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
     * ANALYZED frames.
     *
     * Idempotent: items and AUTO observations are
     * deleted and regenerated from the frames, so
     * running this repeatedly is safe.
     */
    suspend fun rebuildFeedItemsForSession(
        sessionId: String
    ) {

        val frames =
            captureDao.getAnalyzedFramesForSession(
                sessionId
            )

        if (frames.isEmpty()) {
            return
        }

        val segments =
            segmentFrames(
                frames
            )

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

            val item =
                buildFeedItem(
                    sessionId = sessionId,
                    frames = segment
                )

            feedItemDao.insert(item)

            observationDao.insert(
                ResearchObservation(
                    sessionId = sessionId,
                    text = autoObservationText(item),
                    source =
                        ResearchObservation.SOURCE_AUTO
                )
            )
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

// ========================================
// SEGMENTATION
// ========================================

    private fun segmentFrames(
        frames: List<CapturedFrame>
    ): List<List<CapturedFrame>> {

        val segments =
            mutableListOf<List<CapturedFrame>>()

        var current =
            mutableListOf<CapturedFrame>()

        for (frame in frames) {

            if (current.isEmpty()) {

                current.add(frame)
                continue
            }

            val previous =
                current.last()

            if (
                belongsToSameItem(
                    previous,
                    frame
                )
            ) {

                current.add(frame)

            } else {

                segments.add(current)
                current = mutableListOf(frame)
            }
        }

        if (current.isNotEmpty()) {
            segments.add(current)
        }

        return segments
    }

    /*
     * Two consecutive frames belong to the same
     * content piece when they were captured close
     * together AND their predicted category does
     * not change AND the screen is not showing
     * completely different content.
     *
     * Perceptual fingerprints (7D-C) split frames
     * that time + category rules would wrongly merge,
     * e.g. back-to-back Reels in the same category.
     *
     * Frames without a category are only merged
     * when the gap is very short, so a long run of
     * OCR-less frames does not collapse into one
     * giant item.
     */
    private fun belongsToSameItem(
        previous: CapturedFrame,
        current: CapturedFrame
    ): Boolean {

        val gapMs =
            abs(
                Duration
                    .between(
                        previous.capturedAt,
                        current.capturedAt
                    )
                    .toMillis()
            )

        if (gapMs > MAX_MERGE_GAP_MS) {
            return false
        }

        /*
         * 7D-C: strongly different visual content
         * always starts a new item.
         */
        if (
            fingerprintsIndicateDifferentContent(
                previous,
                current
            )
        ) {
            return false
        }

        val previousCategory =
            categoryOf(previous)

        val currentCategory =
            categoryOf(current)

        return when {

            previousCategory == null ||
                    currentCategory == null ->
                gapMs <= NULL_CATEGORY_GAP_MS

            previousCategory == currentCategory ->
                true

            else ->
                false
        }
    }

    /*
     * 7D-C perceptual split.
     *
     * When both frames carry a fingerprint and they
     * differ by enough bits, the screen shows a
     * different piece of content.
     */
    private fun fingerprintsIndicateDifferentContent(
        previous: CapturedFrame,
        current: CapturedFrame
    ): Boolean {

        val previousHash =
            fingerprintOf(previous)

        val currentHash =
            fingerprintOf(current)

        if (
            previousHash == null ||
            currentHash == null
        ) {
            return false
        }

        val distance =
            PerceptualHash()
                .hammingDistance(
                    previousHash,
                    currentHash
                ) ?: return false

        return distance >=
                HASH_SPLIT_DISTANCE
    }

    private fun buildFeedItem(
        sessionId: String,
        frames: List<CapturedFrame>
    ): FeedItem {

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

        val categories =
            frames.mapNotNull {
                categoryOf(it)
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

        val category =
            categories
                .groupingBy { it }
                .eachCount()
                .maxByOrNull {
                    it.value
                }
                ?.key

        val confidence =
            confidences
                .takeIf {
                    it.isNotEmpty()
                }
                ?.average()

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
            }.distinct()

        return FeedItem(
            sessionId = sessionId,
            startTime = startTime,
            endTime = endTime,
            durationSeconds = durationSeconds,
            category = category,
            confidence = confidence,
            topic = topic,
            tone = tone,
            contentType = contentType,
            skipped = skipped,
            representativeFramePath =
                frames.first().filePath,
            frameCount = frames.size,
            interactionSignals =
                interactionSignals,
            modelVersion =
                modelVersions.firstOrNull(),
            frameFingerprint =
                fingerprintOf(
                    frames.first()
                ),
            updatedAt = LocalDateTime.now()
        )
    }

    private fun autoObservationText(
        item: FeedItem
    ): String {

        val category =
            item.category
                ?: "unclassified"

        val skipped =
            if (item.skipped) {
                " (skipped)"
            } else {
                ""
            }

        val detail =
            buildString {

                item.topic?.let {
                    if (it.isNotEmpty()) {
                        append(" [$it]")
                    }
                }

                item.tone?.let {
                    if (it.isNotEmpty()) {
                        append(" tone=$it")
                    }
                }

                item.interactionSignals
                    .filterNot {
                        it == "skipped"
                    }
                    .takeIf {
                        it.isNotEmpty()
                    }
                    ?.let { signals ->
                        append(
                            " signals=" +
                                    signals.joinToString(",")
                        )
                    }
            }

        return "AI: Viewed $category content for " +
                "${item.durationSeconds}s$skipped$detail"
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

    companion object {

        /*
         * Frames further apart than this never belong
         * to the same content piece.
         */
        private const val MAX_MERGE_GAP_MS =
            10_000L

        /*
         * Maximum gap when the category is unknown.
         */
        private const val NULL_CATEGORY_GAP_MS =
            3_000L

        /*
         * Perceptual hashes differing by at least this
         * many bits (of 64) mean the screen shows a
         * different piece of content.
         */
        private const val HASH_SPLIT_DISTANCE =
            24

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
    }
}
