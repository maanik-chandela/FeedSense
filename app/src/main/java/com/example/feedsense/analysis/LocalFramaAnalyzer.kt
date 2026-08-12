package com.example.feedsense.analysis
import android.content.Context
import java.io.File

// --------------------------------
// LOCAL FRAME ANALYZER
// --------------------------------
//
// Milestone 7A local analysis chain.
//
// Composes:
//
//   1. OCR (ML Kit)   -> visible text
//   2. Heuristic      -> content category, confidence,
//                        ambiguity
//
// Runs fully on-device, offline, and free.
//
// The FrameAnalyzer interface is the swappable
// boundary: a real vision model can replace this
// implementation later without touching the pipeline
// or the worker.
//

class LocalFrameAnalyzer(
    context: Context
) : FrameAnalyzer {

    private val ocrAnalyzer =
        OcrFrameAnalyzer(
            context
        )

    private val classifier =
        TextHeuristicClassifier()

    private val interactionDetector =
        InteractionDetector()

    override suspend fun analyze(
        file: File
    ): FrameAnalysisResult {

        // --------------------------------
        // STEP 1: OCR
        // --------------------------------

        val ocrResult =
            ocrAnalyzer.analyze(
                file
            )

        // --------------------------------
        // STEP 2: HEURISTIC CLASSIFICATION
        // --------------------------------

        val classification =
            classifier.classify(
                ocrResult.visibleText
            )

        // --------------------------------
        // STEP 3: INTERACTION EVIDENCE
        // --------------------------------
        //
        // Milestone 7D-A. Only UI evidence found in
        // the OCR text becomes a signal.

        val interactionSignals =
            interactionDetector.detect(
                ocrResult.visibleText
            )

        // --------------------------------
        // STEP 4: MERGE INTO RESULT
        // --------------------------------

        return FrameAnalysisResult(
            status =
                "LOCAL_ANALYSIS_COMPLETE",

            fileName =
                ocrResult.fileName,

            width =
                ocrResult.width,

            height =
                ocrResult.height,

            fileSizeBytes =
                ocrResult.fileSizeBytes,

            message =
                if (classification.primaryCategory != null) {
                    "Local analysis complete. Category: " +
                            classification.primaryCategory
                } else {
                    "Local analysis complete. No category " +
                            "confidently detected."
                },

            screenType =
                ocrResult.screenType,

            application =
                ocrResult.application,

            activity =
                ocrResult.activity,

            visibleText =
                ocrResult.visibleText,

            confidence =
                classification.confidence,

            contentCategory =
                classification.primaryCategory,

            secondaryCategories =
                classification.secondaryCategories,

            topic =
                classification.topic,

            tone =
                classification.tone,

            ambiguityScore =
                classification.ambiguityScore,

            interactionSignals =
                interactionSignals,

            modelVersion =
                MODEL_VERSION
        )
    }

    companion object {

        /*
         * Version of the local heuristic model.
         *
         * Bumped whenever the keyword sets or scoring
         * logic change, so results are comparable.
         */
        const val MODEL_VERSION =
            "heuristic-v2"
    }
}