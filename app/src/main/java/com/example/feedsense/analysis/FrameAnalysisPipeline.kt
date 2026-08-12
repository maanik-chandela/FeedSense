package com.example.feedsense.analysis
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
        ConfidenceGate()
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

            AnalysisDisposition.NEEDS_REVIEW ->
                localResult.copy(
                    status = STATUS_NEEDS_REVIEW,
                    disposition = disposition.name,
                    needsReview = true
                )

            // AMBIGUOUS and NEEDS_CLOUD both try
            // the cloud reference analyzer.
            AnalysisDisposition.AMBIGUOUS,
            AnalysisDisposition.NEEDS_CLOUD -> {

                val cloudResult =
                    runCatching {
                        cloudAnalyzer.analyze(file)
                    }.getOrNull()

                if (cloudResult != null) {

                    cloudResult.copy(
                        disposition = disposition.name,
                        needsReview = false
                    )

                } else {

                    // No cloud available.
                    //
                    // Keep the local prediction so the
                    // review UI has context, but mark
                    // the frame as needing review.
                    localResult.copy(
                        status = STATUS_NEEDS_REVIEW,
                        disposition =
                            AnalysisDisposition
                                .NEEDS_REVIEW
                                .name,
                        needsReview = true
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