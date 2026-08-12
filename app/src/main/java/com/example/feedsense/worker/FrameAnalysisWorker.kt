package com.example.feedsense.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.feedsense.FeedSenseApplication
import com.example.feedsense.analysis.AnalysisSource
import com.example.feedsense.analysis.FrameAnalysisPipeline
import com.example.feedsense.analysis.FrameAnalysisResult
import com.example.feedsense.analysis.PerceptualHash
import com.example.feedsense.model.CapturedFrame
import com.example.feedsense.model.LabeledReference
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

class FrameAnalysisWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(
    appContext,
    workerParams
) {
    private val application =
        appContext.applicationContext
                as FeedSenseApplication

    private val sessionRepository =
        application.sessionRepository

    private val referenceRepository =
        application.referenceRepository

// --------------------------------
// HYBRID ANALYSIS PIPELINE
// --------------------------------
//
// Local analysis first.
// Cloud fallback only when the local result
// is not confident or is ambiguous.
//

    private val frameAnalyzer =
        application.frameAnalyzer

    companion object {

        const val WORK_NAME =
            "feedsense_frame_analysis"

        private const val BATCH_SIZE = 10
    }

    override suspend fun doWork(): Result {

        return try {

            // --------------------------------
            // RESET INTERRUPTED WORK
            // --------------------------------

            sessionRepository
                .resetProcessingFrames()

            // --------------------------------
            // LOAD PENDING FRAMES
            // --------------------------------

            val frames =
                sessionRepository
                    .getPendingFrames(
                        limit = BATCH_SIZE
                    )

            if (frames.isEmpty()) {
                return Result.success()
            }

            // --------------------------------
            // ANALYZE BATCH
            // --------------------------------

            for (frame in frames) {

                processFrame(frame)
            }

            // --------------------------------
            // REBUILD FEED ITEMS
            // --------------------------------
            //
            // Group the newly analyzed frames into
            // content pieces and generate AI
            // auto-observations.

            FeedItemScheduler.schedule(
                applicationContext
            )

            Result.success()

        } catch (exception: Exception) {

            exception.printStackTrace()

            Result.retry()
        }
    }

// ========================================
// PROCESS ONE FRAME
// ========================================

    private suspend fun processFrame(
        frame: CapturedFrame
    ) {

        try {

            // --------------------------------
            // MARK PROCESSING
            // --------------------------------

            sessionRepository
                .markFrameProcessing(
                    frame.id
                )

            // --------------------------------
            // VERIFY FILE
            // --------------------------------

            val file =
                File(
                    frame.filePath
                )

            if (
                !file.exists() ||
                !file.isFile
            ) {

                sessionRepository
                    .markFrameFailed(
                        frame.id
                    )

                return
            }

            // --------------------------------
            // RUN HYBRID PIPELINE
            // --------------------------------

            val analysis =
                frameAnalyzer.analyze(
                    file
                )

            // --------------------------------
            // PERCEPTUAL FINGERPRINT (7D-C)
            // --------------------------------
            //
            // Used later to separate back-to-back
            // Reels during feed item segmentation.

            val frameFingerprint =
                PerceptualHash()
                    .compute(file)

            val resultJson =
                buildResultJson(
                    analysis,
                    frameFingerprint
                )

            // --------------------------------
            // STORE RESULT
            // --------------------------------
            //
            // Frames that the local pipeline could
            // not confidently classify are queued
            // for review instead of being marked
            // analyzed.
            //
            // Cloud-sourced results are also stored
            // as PENDING references: they must be
            // validated by a human before they can
            // be used to improve the local model.

            val needsReview =
                analysis.needsReview ||
                        analysis.status ==
                        FrameAnalysisPipeline.STATUS_NEEDS_REVIEW

            if (needsReview) {

                sessionRepository
                    .markFrameNeedsReview(
                        frameId = frame.id,
                        result = resultJson.toString()
                    )

                recordReviewReference(
                    frame = frame,
                    analysis = analysis,
                    labelSource =
                        LabeledReference.LABEL_SOURCE_HUMAN
                )

            } else if (
                analysis.source ==
                AnalysisSource.CLOUD.name
            ) {

                sessionRepository
                    .markFrameAnalyzed(
                        frameId = frame.id,
                        result = resultJson.toString()
                    )

                recordReviewReference(
                    frame = frame,
                    analysis = analysis,
                    labelSource =
                        LabeledReference.LABEL_SOURCE_CLOUD
                )

            } else {

                sessionRepository
                    .markFrameAnalyzed(
                        frameId = frame.id,
                        result = resultJson.toString()
                    )
            }

        } catch (exception: Exception) {

            exception.printStackTrace()

            // --------------------------------
            // FRAME-LEVEL FAILURE
            // --------------------------------

            try {

                sessionRepository
                    .markFrameFailed(
                        frame.id
                    )

            } catch (statusException: Exception) {

                statusException.printStackTrace()
            }
        }
    }

// ========================================
// RECORD REVIEW REFERENCE
// ========================================

    private suspend fun recordReviewReference(
        frame: CapturedFrame,
        analysis: FrameAnalysisResult,
        labelSource: String
    ) {

        try {

            val candidates =
                buildList {

                    analysis.contentCategory?.let {
                        add(it)
                    }

                    analysis.secondaryCategories.forEach {
                        if (!contains(it)) {
                            add(it)
                        }
                    }
                }

            referenceRepository.addPendingReview(
                frameId = frame.id,
                sessionId = frame.sessionId,
                filePath = frame.filePath,
                aiCategory = analysis.contentCategory,
                aiConfidence = analysis.confidence,
                aiSource = analysis.source,
                modelVersion = analysis.modelVersion,
                candidateCategories = candidates,
                labelSource = labelSource
            )

        } catch (exception: Exception) {

            /*
             * A failed reference insert must not
             * fail the whole frame analysis.
             */
            exception.printStackTrace()
        }
    }

// ========================================
// STRUCTURED RESULT -> JSON
// ========================================

    private fun buildResultJson(
        result: FrameAnalysisResult,
        frameFingerprint: String?
    ): JSONObject {

        return JSONObject().apply {

            put(
                "status",
                result.status
            )

            put(
                "fileName",
                result.fileName
            )

            put(
                "width",
                result.width
            )

            put(
                "height",
                result.height
            )

            put(
                "fileSizeBytes",
                result.fileSizeBytes
            )

            put(
                "message",
                result.message
            )

            result.screenType?.let {
                put("screenType", it)
            }

            result.application?.let {
                put("application", it)
            }

            result.activity?.let {
                put("activity", it)
            }

            result.visibleText?.let {
                put("visibleText", it)
            }

            result.confidence?.let {
                put("confidence", it)
            }

            result.contentCategory?.let {
                put("contentCategory", it)
            }

            put(
                "secondaryCategories",
                JSONArray(
                    result.secondaryCategories
                )
            )

            result.topic?.let {
                put("topic", it)
            }

            result.tone?.let {
                put("tone", it)
            }

            result.contentType?.let {
                put("contentType", it)
            }

            result.estimatedDurationSeconds?.let {
                put("estimatedDurationSeconds", it)
            }

            put(
                "interactionSignals",
                JSONArray(
                    result.interactionSignals
                )
            )

            result.ambiguityScore?.let {
                put("ambiguityScore", it)
            }

            result.modelVersion?.let {
                put("modelVersion", it)
            }

            frameFingerprint?.let {
                put("frameFingerprint", it)
            }

            put(
                "source",
                result.source
            )

            put(
                "disposition",
                result.disposition
            )

            put(
                "needsReview",
                result.needsReview
            )
        }
    }
}
