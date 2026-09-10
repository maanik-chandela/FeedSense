package com.example.feedsense.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.feedsense.FeedSenseApplication
import com.example.feedsense.analysis.AnalysisSource
import com.example.feedsense.analysis.FrameAnalysisPipeline
import com.example.feedsense.analysis.FrameAnalysisResult
import com.example.feedsense.analysis.FrameDeduplicator
import com.example.feedsense.analysis.PerceptualHash
import com.example.feedsense.analysis.dedup.DeduplicationConfig
import com.example.feedsense.analysis.dedup.DeduplicationMetrics
import com.example.feedsense.analysis.dedup.FrameFilterEngine
import com.example.feedsense.analysis.dedup.PerceptualHashEngine
import com.example.feedsense.analysis.dedup.RetentionDecision
import com.example.feedsense.analysis.privacy.AndroidPrivacyRegionApplier
import com.example.feedsense.analysis.privacy.EvidenceSanitizer
import com.example.feedsense.analysis.privacy.PrivacyTextRedactor
import com.example.feedsense.analysis.privacy.SanitizedEvidence
import com.example.feedsense.analysis.privacy.defaultPrivacyPolicy
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

    // Milestone 7S. Pure dedup logic (unit-testable).
    private val frameDeduplicator =
        FrameDeduplicator()

    // Milestone 8B-3. Low-cost perceptual filtering.
    private val hashEngine =
        PerceptualHashEngine()

    private val filterEngine =
        FrameFilterEngine(
            hashEngine = hashEngine
        )

    private val dedupMetrics =
        DeduplicationMetrics()

    /*
     * Milestone 8B-10. Evidence sanitization pipeline.
     *
     * Wraps the 8B-4 rasterizer (via AndroidPrivacyRegionApplier)
     * and adds OCR text redaction, a privacy policy, and a
     * privacy-safe audit trail for every frame.
     *
     * The policy default is RESEARCH_MODE:
     *   - capture continues if sanitization fails
     *     (blockOnSanitizationFailure = false)
     *   - sanitized frames are written to app-private
     *     filesDir/sanitized so they persist for review.
     */
    private val privacyPolicy =
        defaultPrivacyPolicy()

    private val evidenceSanitizer =
        EvidenceSanitizer(
            policy = privacyPolicy,
            regionApplier = AndroidPrivacyRegionApplier(
                context = appContext,
                policy = privacyPolicy
            )
        )

    private val textRedactor =
        PrivacyTextRedactor()

    companion object {

        const val WORK_NAME =
            "feedsense_frame_analysis"

        private const val BATCH_SIZE = 10

        private const val TAG =
            "FrameAnalysisWorker"
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
            // MILESTONE 8B-3: PERCEPTUAL FILTER
            // --------------------------------
            //
            // Compare the new frame against the last
            // retained frame using low-cost perceptual
            // hashing. Near-duplicates are skipped to
            // avoid redundant expensive analysis.
            //
            // Failure policy: any error retains the
            // frame (fail-open).

            dedupMetrics.recordCaptured()

            val filterResult =
                filterEngine.evaluateFrame(
                    frameFile = file,
                    currentTimestampMs =
                        System.currentTimeMillis()
                )

            if (
                !filterResult.shouldRetain
            ) {

                dedupMetrics.recordDeduplicated()

                sessionRepository
                    .markFrameFiltered(
                        frameId = frame.id,
                        result =
                            buildFilterResultJson(
                                filterResult
                            ).toString(),
                        fingerprint =
                            filterResult.currentHash
                    )

                logFilterDecision(
                    filterResult
                )

                return
            }

            dedupMetrics.recordRetained()

            if (filterResult.forceKept) {
                dedupMetrics.recordForceKept()
            }

            if (
                filterResult.decision ==
                RetentionDecision.UNCERTAIN
            ) {
                dedupMetrics.recordUncertain()
            }

            logFilterDecision(filterResult)

            // --------------------------------
            // MILESTONE 8B-4 / 8B-10:
            // EVIDENCE SANITIZE
            // --------------------------------
            //
            // Sanitize the frame before sending to
            // AI/OCR. Protected system UI regions are
            // redacted to prevent unnecessary exposure
            // of sensitive information. The 8B-10
            // EvidenceSanitizer also redacts private
            // identifiers in OCR text produced by the
            // analysis step.
            //
            // Failure policy: sanitization failures do
            // NOT block capture (blockOnSanitizationFailure
            // defaults to false). The worker falls back to
            // the raw frame for analysis, but the failure
            // status is explicit and recorded in the frame's
            // analysis result JSON.
            //
            // Global logging rule: never log raw OCR text
            // or screenshot paths; only the privacy-safe
            // audit summary is logged.

            val sanitizedOutputDir =
                File(
                    applicationContext.filesDir,
                    "sanitized"
                )

            val evidence =
                evidenceSanitizer.sanitize(
                    rawFrame = file,
                    ocrText = null,
                    frameId = frame.id,
                    outputDir = sanitizedOutputDir
                )

            logPrivacySanitization(evidence)

            // Use the sanitized frame for downstream
            // analysis when it is safe and available;
            // otherwise fail-open to the raw frame.
            val analysisFile =
                if (
                    evidence.isSafeForResearchUse &&
                    evidence.sanitizedFrameFile.exists()
                ) {
                    evidence.sanitizedFrameFile
                } else {
                    file
                }

            // --------------------------------
            // PERCEPTUAL FINGERPRINT (7D-C / 7S)
            // --------------------------------
            //
            // Computed BEFORE the pipeline so the 7S
            // dedup can skip re-analyzing identical
            // content. Also used later to separate
            // back-to-back Reels during segmentation.

            val frameFingerprint =
                PerceptualHash()
                    .compute(file)

            // --------------------------------
            // MILESTONE 7S: DEDUP
            // --------------------------------
            //
            // Same content, same session -> analyze once.
            // When a prior frame with this fingerprint
            // already has a stored classification, reuse
            // it and skip the expensive pipeline. A null
            // fingerprint (unreadable frame) skips dedup
            // and falls through to the normal pipeline.

            if (
                frameFingerprint != null &&
                !frameFingerprint.isBlank()
            ) {

                val prior =
                    sessionRepository
                        .findAnalyzedFrameByFingerprint(
                            sessionId = frame.sessionId,
                            fingerprint = frameFingerprint
                        )

                if (
                    frameDeduplicator.isReusable(
                        prior = prior,
                        currentFrameId = frame.id
                    )
                ) {

                    val reused =
                        frameDeduplicator.reuseResultJson(
                            priorResult = prior!!.analysisResult!!,
                            fileName = file.name,
                            fileSizeBytes = file.length(),
                            fingerprint = frameFingerprint
                        )

                    if (reused != null) {

                        sessionRepository
                            .markFrameAnalyzed(
                                frameId = frame.id,
                                result = reused.toString(),
                                fingerprint = frameFingerprint
                            )

                        return
                    }
                }
            }

            // --------------------------------
            // RUN HYBRID PIPELINE
            // --------------------------------
            //
            // Use the sanitized frame for AI analysis
            // to minimize sensitive data exposure.

            val analysis =
                frameAnalyzer.analyze(
                    analysisFile
                )

            // --------------------------------
            // 8B-10: OCR TEXT REDACTION
            // --------------------------------
            //
            // The analysis may carry sensitive identifiers
            // in its OCR text. Those are redacted BEFORE
            // the text is persisted to the analysis result
            // JSON or any LabeledReference. Research content
            // (e.g. "IPL", "YouTube", "Netflix") passes
            // through untouched.

            val redaction =
                textRedactor.redact(
                    analysis.visibleText ?: "",
                    privacyPolicy
                )

            val resultJson =
                buildResultJson(
                    analysis,
                    frameFingerprint,
                    redactedVisibleText =
                        redaction.redacted,
                    privacyEvidence = evidence
                )

            // --------------------------------
            // STORE RESULT
            // --------------------------------
            //
            // Frames that the local pipeline could
            // not classify at all (or had no useful
            // evidence) are queued for review instead
            // of being marked analyzed.
            //
            // Uncertain frames (medium confidence or
            // ambiguous) are marked ANALYZED so they
            // still participate in FeedItems - a short
            // Reel must never disappear. The feed item
            // builder flags those items and creates the
            // review queue entries.
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
                        result = resultJson.toString(),
                        fingerprint = frameFingerprint
                    )

                recordReviewReference(
                    frame = frame,
                    analysis = analysis,
                    labelSource =
                        LabeledReference.LABEL_SOURCE_HUMAN,
                    frameFingerprint = frameFingerprint,
                    redactedVisibleText =
                        redaction.redacted,
                    evidenceFilePath = analysisFile
                )

            } else {

                sessionRepository
                    .markFrameAnalyzed(
                        frameId = frame.id,
                        result = resultJson.toString(),
                        fingerprint = frameFingerprint
                    )

                if (
                    analysis.source ==
                    AnalysisSource.CLOUD.name
                ) {

                    recordReviewReference(
                        frame = frame,
                        analysis = analysis,
                        labelSource =
                            LabeledReference.LABEL_SOURCE_CLOUD,
                        frameFingerprint = frameFingerprint,
                        redactedVisibleText =
                            redaction.redacted,
                        evidenceFilePath = analysisFile
                    )
                }
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
        labelSource: String,
        frameFingerprint: String?,
        redactedVisibleText: String?,
        evidenceFilePath: File
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
                /*
                 * 8B-10: store the SANITIZED frame path when
                 * a safe sanitized edition exists. The raw
                 * path is only used as a fail-open fallback.
                 */
                filePath = evidenceFilePath.absolutePath,
                aiCategory = analysis.contentCategory,
                aiConfidence = analysis.confidence,
                aiSource = analysis.source,
                modelVersion = analysis.modelVersion,
                candidateCategories = candidates,
                labelSource = labelSource,
                platform = analysis.application,
                topic = analysis.topic,
                tone = analysis.tone,
                /*
                 * 8B-10: persist only the REDACTED OCR text.
                 */
                visibleText = redactedVisibleText,
                aiReason = analysis.classificationReason,
                interactionSignals =
                    analysis.interactionSignals,
                frameFingerprint = frameFingerprint
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
        frameFingerprint: String?,
        redactedVisibleText: String?,
        privacyEvidence: SanitizedEvidence?
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

            /*
             * 8B-10: the OCR text stored here is the
             * REDACTED version. Raw OCR text is never
             * persisted.
             */
            redactedVisibleText?.let {
                put("visibleText", it)
            }

            result.confidence?.let {
                put("confidence", it)
            }

            result.classificationReason?.let {
                put("classificationReason", it)
            }

            result.contentCategory?.let {
                put("contentCategory", it)
            }

            result.categoryDomain?.let {
                put("categoryDomain", it)
            }

            put(
                "secondaryCategories",
                JSONArray(
                    result.secondaryCategories
                )
            )

            /*
             * Milestone 7W. Scored multi-label confidence
             * survives the analysis result so the item
             * builder can aggregate per-category scores.
             */
            if (result.categoryScores.isNotEmpty()) {
                put(
                    "categoryScores",
                    JSONObject(result.categoryScores)
                )
            }

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

            /*
             * Milestone 7F (Part 3): auditable evidence
             * ("signal|confidence|evidence") for each
             * detected interaction signal.
             */
            put(
                "interactionEvidence",
                JSONArray(
                    result.interactionEvidence
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

            put(
                "uncertain",
                result.uncertain
            )

            /*
             * 8B-10: privacy sanitization metadata for the
             * frame. Contains ONLY counts, statuses and
             * versions - NO raw content.
             */
            privacyEvidence?.let { evidence ->
                put(
                    "privacySanitization",
                    JSONObject().apply {
                        put(
                            "status",
                            evidence.status.label
                        )
                        put(
                            "policyVersion",
                            evidence.audit.policyVersion
                        )
                        put(
                            "sanitizationVersion",
                            evidence.audit.sanitizationVersion
                        )
                        put(
                            "regionsDetected",
                            evidence.audit.regionsDetected
                        )
                        put(
                            "regionTypes",
                            JSONArray(
                                evidence.audit.regionTypeLog.keys
                            )
                        )
                        put(
                            "redactedTextSegments",
                            evidence.audit.redactedTextSegments
                        )
                        put(
                            "evidenceLost",
                            evidence.audit
                                .evidenceLostDueToSanitization
                        )
                    }
                )
            }
        }
    }

// ========================================
// MILESTONE 8B-3: FILTER RESULT JSON
// ========================================

    private fun buildFilterResultJson(
        filterResult:
            com.example.feedsense.analysis.dedup
                .FrameFilterResult
    ): JSONObject {

        return JSONObject().apply {

            put(
                "status",
                "FILTERED"
            )

            put(
                "decision",
                filterResult.decision.name
            )

            put(
                "reason",
                filterResult.reason
            )

            put(
                "forceKept",
                filterResult.forceKept
            )

            filterResult.currentHash?.let {
                put("frameFingerprint", it)
            }

            filterResult.hammingDistance?.let {
                put("hammingDistance", it)
            }

            put(
                "message",
                "8B-3:frame-filtered:${filterResult.reason}"
            )
        }
    }

// ========================================
// MILESTONE 8B-3: STRUCTURED LOGGING
// ========================================

    private fun logFilterDecision(
        filterResult:
            com.example.feedsense.analysis.dedup
                .FrameFilterResult
    ) {

        Log.d(
            TAG,
            "FRAME_FILTER_DECISION " +
                    "distance=${filterResult.hammingDistance} " +
                    "decision=${filterResult.decision} " +
                    "shouldRetain=${filterResult.shouldRetain} " +
                    "reason=${filterResult.reason} " +
                    "forceKept=${filterResult.forceKept}"
        )
    }

// ========================================
// MILESTONE 8B-10: SANITIZATION LOGGING
// ========================================
//
// Logging rule: NEVER log raw OCR text, screenshots,
// or audit data containing raw content. Only the
// privacy-safe summary payload is logged.

private fun logPrivacySanitization(
    evidence: SanitizedEvidence
) {

    Log.d(
        TAG,
        "PRIVACY_SANITIZATION " +
                evidence.audit
                    .toPrivacyLogEntry()
                    .toLogLine()
    )
}
}
