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
// - MEDIUM_CONFIDENCE : local result exists but is not
//                    strong enough to fully trust. The
//                    content is kept but flagged as
//                    uncertain for review.
// - AMBIGUOUS      : categories overlap; cloud/human
//                    reference recommended.
// - NEEDS_CLOUD    : local confidence is too low.
// - NEEDS_REVIEW   : no cloud available; queue for
//                    human review.
//

enum class AnalysisDisposition {

    LOCAL_ACCEPTED,

    MEDIUM_CONFIDENCE,

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

    /*
     * Milestone 7D. Human/audit-readable evidence for
     * the classification, e.g. "hits: sports=3, comedy=1".
     */
    val classificationReason: String? = null,

    /*
     * Milestone 7D. True when the local classification
     * is not strong enough to be trusted (medium or
     * ambiguous). The content is still kept, but the
     * FeedItem should be flagged for review.
     */
    val uncertain: Boolean = false,

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

    /*
     * Milestone 7V. Top-level domain of the predicted
     * category ("sports", "entertainment", "education",
     * ...). Set when the local hierarchy refinement runs;
     * null when the analyzer does not produce one.
     */
    val categoryDomain: String? = null,

    val secondaryCategories: List<String> = emptyList(),

    /*
     * Milestone 7W. Per-category multi-label confidence
     * scores (category -> score, e.g. comedy 0.8 and
     * sports 0.5 on a skit with sports cameos). Produced
     * by the multi-signal scorer so every secondary label
     * carries an auditable confidence, not just a name.
     */
    val categoryScores: Map<String, Double> = emptyMap(),

    val topic: String? = null,

    val tone: String? = null,

    val contentType: String? = null,

    val estimatedDurationSeconds: Int? = null,

    val interactionSignals: List<String> = emptyList(),

    /*
     * Milestone 7F (Part 3). Auditable interaction
     * evidence entries ("signal|confidence|evidence")
     * produced by InteractionDetector.detectWithEvidence.
     */
    val interactionEvidence: List<String> = emptyList(),

    val ambiguityScore: Double? = null,

    val modelVersion: String? = null,

    val source: String = AnalysisSource.LOCAL.name,

    val disposition: String =
        AnalysisDisposition.LOCAL_ACCEPTED.name,

    val needsReview: Boolean = false,

    /*
     * App Chrome Detection.
     *
     * When true the frame contains only app UI chrome
     * (status bar, nav bar, app header/footer) and
     * carries no research value. Chrome frames are
     * filtered out during feed item building.
     */
    val isChromeFrame: Boolean = false,

    /*
     * Which app was on screen, detected by
     * AppChromeDetector from OCR text. Stored as a
     * stable key ("youtube", "instagram", ...) so the
     * session can track app context over time.
     */
    val appContext: String? = null

)
