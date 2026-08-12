package com.example.feedsense.analysis
import java.io.File
interface FrameAnalyzer {
    suspend fun analyze(
        file: File
    ): FrameAnalysisResult
}

// --------------------------------
// ANALYSIS SOURCE
// --------------------------------
//
// Where did the analysis result come from?
//

enum class AnalysisSource {

    LOCAL,

    CLOUD,

    HUMAN,

    UNKNOWN
}

// --------------------------------
// ANALYSIS DISPOSITION
// --------------------------------
//
// The pipeline decides what should happen
// with a frame after local analysis.
//
// - LOCAL_ACCEPTED : local result is trusted.
// - AMBIGUOUS      : categories overlap; cloud/human
//                    reference recommended.
// - NEEDS_CLOUD    : local confidence is too low.
// - NEEDS_REVIEW   : no cloud available; queue for
//                    human review.
//

enum class AnalysisDisposition {

    LOCAL_ACCEPTED,

    AMBIGUOUS,

    NEEDS_CLOUD,

    NEEDS_REVIEW
}

data class FrameAnalysisResult(

// --------------------------------
// EXISTING METADATA
// --------------------------------

    val status: String,

    val fileName: String,

    val width: Int,

    val height: Int,

    val fileSizeBytes: Long,

    val message: String,

// --------------------------------
// RESEARCH ANALYSIS
// --------------------------------

    val screenType: String? = null,

    val application: String? = null,

    val activity: String? = null,

    val visibleText: String? = null,

    val confidence: Double? = null,

// --------------------------------
// STRUCTURED CLASSIFICATION
// --------------------------------
//
// Added for Milestone 7A.
//
// These fields are defaulted so existing
// analyzers keep compiling unchanged.
//

    val contentCategory: String? = null,

    val secondaryCategories: List<String> = emptyList(),

    val topic: String? = null,

    val tone: String? = null,

    val contentType: String? = null,

    val estimatedDurationSeconds: Int? = null,

    val interactionSignals: List<String> = emptyList(),

    val ambiguityScore: Double? = null,

    val modelVersion: String? = null,

    val source: String = AnalysisSource.LOCAL.name,

    val disposition: String =
        AnalysisDisposition.LOCAL_ACCEPTED.name,

    val needsReview: Boolean = false

)
