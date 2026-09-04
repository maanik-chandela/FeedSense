package com.example.feedsense.analysis

import com.example.feedsense.model.CloudUsageRecord
import com.example.feedsense.repository.CloudUsageRepository
import java.io.File

// --------------------------------
// FRAME ANALYSIS PIPELINE
// --------------------------------
//
// Milestone 7A orchestrator.
//
// Flow:
//
//   file
//    -> localAnalyzer      (on-device, offline)
//    -> ConfidenceGate     (is the local result enough?)
//    -> CloudFrameAnalyzer (only when confidence is low
//                           or the content is ambiguous)
//
// If the cloud is unavailable the frame is marked
// for review instead of being forced into the cloud
// or silently failing.
//
// This class implements FrameAnalyzer so the worker
// and any future callers depend only on the same
// interface.
//

class FrameAnalysisPipeline(
    private val localAnalyzer: FrameAnalyzer,
    private val cloudAnalyzer: CloudFrameAnalyzer =
        UnconfiguredCloudAnalyzer(),
    private val confidenceGate: ConfidenceGate =
        ConfidenceGate(),
    /*
     * Milestone 7Q. When set, every cloud request is
     * gated and recorded by the budget manager. When
     * null (the default) the pipeline stays strictly
     * local-only and never consults the cloud at all.
     */
    private val cloudUsageManager: CloudUsageRepository? = null
) : FrameAnalyzer {

    override suspend fun analyze(
        file: File
    ): FrameAnalysisResult {

        // --------------------------------
        // STEP 1: LOCAL ANALYSIS
        // --------------------------------

        val localResult =
            localAnalyzer.analyze(
                file
            )

        // --------------------------------
        // STEP 2: CONFIDENCE GATE
        // --------------------------------

        val disposition =
            confidenceGate.evaluate(
                localResult
            )

        return when (disposition) {

            AnalysisDisposition.LOCAL_ACCEPTED ->
                localResult.copy(
                    disposition =
                        disposition.name,
                    needsReview = false
                )

            // --------------------------------
            // MEDIUM CONFIDENCE (7D)
            // --------------------------------
            //
            // The local result is not strong enough to
            // fully trust, but it is not so weak that it
            // must be held out of feed items. The content
            // is kept and flagged as uncertain; the feed
            // item builder queues it for review.
            //
            // No cloud call: medium confidence stays on
            // the local, budget-friendly path.
            //
            AnalysisDisposition.MEDIUM_CONFIDENCE ->
                localResult.copy(
                    disposition =
                        disposition.name,
                    needsReview = false,
                    uncertain = true
                )

            AnalysisDisposition.NEEDS_REVIEW ->
                localResult.copy(
                    status = STATUS_NEEDS_REVIEW,
                    disposition = disposition.name,
                    needsReview = true,
                    uncertain = true
                )

            // AMBIGUOUS and NEEDS_CLOUD both try
            // the cloud reference analyzer.
            AnalysisDisposition.AMBIGUOUS,
            AnalysisDisposition.NEEDS_CLOUD -> {

                /*
                 * Milestone 7Q. Every cloud request is
                 * gated BEFORE it is made. A blocked or
                 * unaffordable request behaves exactly
                 * like an unreachable cloud: the local
                 * result is kept and the item goes to the
                 * human review queue. The attempt itself
                 * is always recorded for the audit trail.
                 */
                val budgetDecision =
                    cloudUsageManager
                        ?.authorizeAndRecord(
                            sessionId = null,
                            frameId = null,
                            feedItemId = null,
                            filePath = file.absolutePath,
                            requestType = CloudUsageRecord
                                .REQUEST_TYPE_CLASSIFICATION,
                            modelVersion =
                                localResult.modelVersion
                        )

                val cloudAllowed =
                    budgetDecision?.allowed ?: false

                val cloudResult =
                    if (cloudAllowed) {
                        runCatching {
                            cloudAnalyzer.analyze(file)
                        }.getOrNull()
                    } else {
                        null
                    }

                if (cloudResult != null) {

                    cloudResult.copy(
                        disposition = disposition.name,
                        needsReview = false
                    )

                } else if (
                    disposition ==
                    AnalysisDisposition.AMBIGUOUS
                ) {

                    // --------------------------------
                    // AMBIGUOUS, NO CLOUD (7D)
                    // --------------------------------
                    //
                    // Ambiguous content must NOT be
                    // forced into a single category. Keep
                    // the local prediction as context,
                    // mark it uncertain, and let the
                    // reviewer decide with candidates.
                    //
                    localResult.copy(
                        disposition =
                            disposition.name,
                        needsReview = false,
                        uncertain = true
                    )

                } else {

                    // --------------------------------
                    // VERY LOW CONFIDENCE, NO CLOUD
                    // --------------------------------
                    //
                    // Keep the local prediction so the
                    // review UI has context, but mark
                    // the frame as needing review.
                    //
                    localResult.copy(
                        status = STATUS_NEEDS_REVIEW,
                        disposition =
                            AnalysisDisposition
                                .NEEDS_REVIEW
                                .name,
                        needsReview = true,
                        uncertain = true
                    )
                }
            }
        }
    }

    companion object {

        const val STATUS_NEEDS_REVIEW =
            "NEEDS_REVIEW"
    }
}